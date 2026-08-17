// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.service;

import jakarta.annotation.PostConstruct;
import org.sifunity.broker.queueservice.redis.StreamBatch;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated service for batched queue cleanup operations (purge and delete).
 *
 * <p>Extracted from {@link QueueService} to keep that class focused on CRUD.
 * Uses cursor-based stream iteration, pipelined Redis decrements, batch storage
 * deletes, and inter-batch yields to handle million-message queues without
 * exhausting memory or blocking Redis.</p>
 *
 * <p>Both {@code submitPurge} and {@code submitDelete} return immediately after
 * submitting work to the dedicated cleanup executor. A Redis progress marker
 * tracks state for crash resilience.</p>
 */
@Service
public class QueueCleanupService {

    private static final Logger log = LoggerFactory.getLogger(QueueCleanupService.class);

    private static final String PROGRESS_KEY_PREFIX = "function:msg_queue:cleanup-progress:";

    // Hash field names for progress marker
    private static final String FIELD_OPERATION = "operation";
    private static final String FIELD_QUEUE_ID = "queueId";
    private static final String FIELD_STREAM_KEY = "streamKey";
    private static final String FIELD_LAST_PROCESSED_ID = "lastProcessedId";
    private static final String FIELD_MESSAGES_PROCESSED = "messagesProcessed";
    private static final String FIELD_STARTED_AT = "startedAt";
    private static final String FIELD_STATUS = "status";

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private static final String OPERATION_PURGE = "PURGE";
    private static final String OPERATION_DELETE = "DELETE";

    private final StreamMessageRepository streamMessageRepository;
    private final RefCountService refCountService;
    private final StorageProvider storageProvider;
    private final StringRedisTemplate redisTemplate;
    private final QueueRepository queueRepository;
    private final CacheManager cacheManager;
    private final ThreadPoolTaskExecutor cleanupExecutor;

    @Value("${queue.cleanup.batch-size:1000}")
    private int batchSize;

    @Value("${queue.cleanup.progress-log-interval:10000}")
    private int progressLogInterval;

    @Value("${queue.cleanup.inter-batch-yield-ms:20}")
    private long interBatchYieldMs;

    public QueueCleanupService(StreamMessageRepository streamMessageRepository,
                               RefCountService refCountService,
                               StorageProvider storageProvider,
                               StringRedisTemplate redisTemplate,
                               QueueRepository queueRepository,
                               CacheManager cacheManager,
                               @Qualifier("cleanupExecutor") ThreadPoolTaskExecutor cleanupExecutor) {
        this.streamMessageRepository = streamMessageRepository;
        this.refCountService = refCountService;
        this.storageProvider = storageProvider;
        this.redisTemplate = redisTemplate;
        this.queueRepository = queueRepository;
        this.cacheManager = cacheManager;
        this.cleanupExecutor = cleanupExecutor;
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Submit an async purge operation. Returns immediately.
     * Clears both main and DLQ streams, preserves queue metadata and consumer group.
     */
    public void submitPurge(UUID queueId, String domainId, String solutionId, UUID environmentId) {
        log.info("Submitting purge: queueId={}", queueId);
        cleanupExecutor.submit(() -> executePurge(queueId, domainId, solutionId, environmentId));
    }

    /**
     * Submit an async delete operation. Returns immediately.
     * Clears streams, destroys consumer group, removes stream keys, removes DB row.
     */
    public void submitDelete(UUID queueId, String domainId, String solutionId, UUID environmentId) {
        log.info("Submitting delete: queueId={}", queueId);
        cleanupExecutor.submit(() -> executeDelete(queueId, domainId, solutionId, environmentId));
    }

    // ── Purge execution ─────────────────────────────────────────────────

    private void executePurge(UUID queueId, String domainId, String solutionId, UUID environmentId) {
        String streamKey = streamMessageRepository.resolveStreamKey(domainId, solutionId, environmentId, queueId);
        String dlqKey = streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, environmentId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);
        String progressKey = progressKey(queueId, OPERATION_PURGE);

        log.info("Starting purge: queueId={}, streamKey={}", queueId, streamKey);

        try {
            initProgressMarker(progressKey, OPERATION_PURGE, queueId, streamKey);

            // XACK all pending entries before XDEL to clear the PEL
            // This prevents stale PEL entries from causing 500 errors on subsequent reads
            acknowledgePendingEntries(streamKey, groupName);

            // Process main stream
            cleanupStream(streamKey, domainId, solutionId, progressKey);

            // Process DLQ stream
            updateProgressStreamKey(progressKey, dlqKey);
            cleanupStream(dlqKey, domainId, solutionId, progressKey);

            // Purge completion: preserve consumer group, delete progress marker
            completeProgressMarker(progressKey, STATUS_COMPLETED);
            log.info("Purge completed: queueId={}", queueId);
        } catch (Exception e) {
            log.error("Purge failed: queueId={}", queueId, e);
            completeProgressMarker(progressKey, STATUS_FAILED);
        }
    }

    // ── Delete execution ────────────────────────────────────────────────

    private void executeDelete(UUID queueId, String domainId, String solutionId, UUID environmentId) {
        String streamKey = streamMessageRepository.resolveStreamKey(domainId, solutionId, environmentId, queueId);
        String dlqKey = streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, environmentId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);
        String progressKey = progressKey(queueId, OPERATION_DELETE);

        log.info("Starting delete: queueId={}, streamKey={}", queueId, streamKey);

        try {
            initProgressMarker(progressKey, OPERATION_DELETE, queueId, streamKey);

            // Process main stream
            cleanupStream(streamKey, domainId, solutionId, progressKey);

            // Process DLQ stream
            updateProgressStreamKey(progressKey, dlqKey);
            cleanupStream(dlqKey, domainId, solutionId, progressKey);

            // Delete completion: destroy consumer groups, DEL stream keys, remove DB row
            streamMessageRepository.destroyConsumerGroup(streamKey, groupName);
            streamMessageRepository.destroyConsumerGroup(dlqKey, groupName);
            streamMessageRepository.deleteStream(streamKey);
            streamMessageRepository.deleteStream(dlqKey);

            // Remove queue metadata from PostgreSQL
            queueRepository.deleteById(queueId);

            // Evict queue from cache
            evictQueueFromCache(queueId);

            completeProgressMarker(progressKey, STATUS_COMPLETED);
            log.info("Delete completed: queueId={}", queueId);
        } catch (Exception e) {
            log.error("Delete failed: queueId={}", queueId, e);
            completeProgressMarker(progressKey, STATUS_FAILED);
        }
    }

    /**
     * Evict queue from the queue-metadata cache.
     */
    private void evictQueueFromCache(UUID queueId) {
        try {
            Cache cache = cacheManager.getCache("queue-metadata");
            if (cache != null) {
                cache.evict(queueId);
                log.debug("Evicted queue from cache: queueId={}", queueId);
            }
        } catch (Exception e) {
            log.debug("Could not evict queue from cache: queueId={}", queueId, e);
        }
    }

    // ── Core batched cleanup loop ───────────────────────────────────────

    /**
     * Core batched cleanup loop for a single stream.
     * Iterates with cursor-based pagination, pipelines decrements, batch-deletes payloads,
     * then XDEL's the processed entries. Sleeps between batches to avoid blocking Redis.
     */
    void cleanupStream(String streamKey, String domainId, String solutionId, String progressKey) {
        String cursor = "0";
        long totalProcessed = 0;
        boolean hasMore = true;

        while (hasMore) {
            StreamBatch batch = streamMessageRepository.getStorageKeysBatch(streamKey, cursor, batchSize);

            if (batch.entryIds().isEmpty()) {
                break;
            }

            // Build ref count keys for non-null storage keys
            List<String> refKeys = new ArrayList<>(batch.storageKeys().size());
            List<String> storageKeysForBatch = new ArrayList<>(batch.storageKeys().size());
            for (String storageKey : batch.storageKeys()) {
                if (storageKey != null) {
                    String refKey = refCountService.refCountKey(domainId, solutionId, storageKey);
                    refKeys.add(refKey);
                    storageKeysForBatch.add(storageKey);
                }
            }

            // Decrement ref counts in pipeline
            Map<String, Long> decrementResults = Map.of();
            if (!refKeys.isEmpty()) {
                try {
                    decrementResults = refCountService.decrementBatch(refKeys);
                } catch (Exception e) {
                    log.warn("Batch decrement failed for stream={}, cursor={}: {}",
                            streamKey, cursor, e.getMessage());
                    // Continue — we still XDEL the entries to make progress
                }
            }

            // Collect storage keys where ref count reached zero
            List<String> keysToDelete = new ArrayList<>();
            for (int i = 0; i < refKeys.size(); i++) {
                String refKey = refKeys.get(i);
                Long newCount = decrementResults.get(refKey);
                if (newCount != null && newCount <= 0) {
                    keysToDelete.add(storageKeysForBatch.get(i));
                }
            }

            // Batch delete payloads where ref count reached zero
            if (!keysToDelete.isEmpty()) {
                try {
                    storageProvider.deleteBatch(keysToDelete);
                } catch (Exception e) {
                    log.warn("Batch storage delete failed for stream={}: {}",
                            streamKey, e.getMessage());
                }
            }

            // XDEL processed entries (incremental stream shrink)
            streamMessageRepository.deleteEntries(streamKey, batch.entryIds());

            // Advance cursor
            cursor = batch.lastId();
            hasMore = batch.hasMore();
            totalProcessed += batch.entryIds().size();

            // Update progress marker
            updateProgressAfterBatch(progressKey, cursor, totalProcessed);

            // Log progress at configured interval
            if (totalProcessed % progressLogInterval < batchSize) {
                log.info("Cleanup progress: stream={}, messagesProcessed={}", streamKey, totalProcessed);
            }

            // Inter-batch yield to keep Redis responsive
            if (hasMore && interBatchYieldMs > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(interBatchYieldMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Cleanup interrupted: stream={}, processed={}", streamKey, totalProcessed);
                    break;
                }
            }
        }

        log.info("Stream cleanup finished: stream={}, totalProcessed={}", streamKey, totalProcessed);
    }

    // ── Redis progress marker logic ─────────────────────────────────────

    private String progressKey(UUID queueId, String operation) {
        return PROGRESS_KEY_PREFIX + queueId + ":" + operation;
    }

    /**
     * Acknowledge all pending entries in the consumer group's PEL.
     * This must be done before XDEL to prevent stale PEL entries from
     * causing errors on subsequent XREADGROUP/XCLAIM operations.
     */
    private void acknowledgePendingEntries(String streamKey, String groupName) {
        long totalAcknowledged = 0;

        while (true) {
            var pending = streamMessageRepository.getPendingMessagesForGroup(
                    streamKey, groupName, batchSize);
            if (pending == null || pending.size() == 0) {
                break;
            }

            for (var entry : pending) {
                // XACK returns 0 if the entry was already acknowledged (e.g. by a
                // concurrent consumer between our XPENDING read and this XACK call).
                // That is the desired end state — the entry is no longer pending — so
                // treat 0 the same as 1.  Only propagate actual Redis/IO exceptions.
                long acknowledged = streamMessageRepository.acknowledge(
                        streamKey, groupName, entry.getIdAsString());
                if (acknowledged == 1L) {
                    totalAcknowledged++;
                } else {
                    log.debug("Entry already acknowledged (concurrent consumer): streamKey={}, entryId={}",
                            streamKey, entry.getIdAsString());
                }
            }
        }

        if (totalAcknowledged > 0) {
            log.debug("Acknowledged {} pending entries before purge: streamKey={}",
                    totalAcknowledged, streamKey);
        }
    }

    private void initProgressMarker(String progressKey, String operation, UUID queueId, String streamKey) {
        Map<String, String> fields = Map.of(
                FIELD_OPERATION, operation,
                FIELD_QUEUE_ID, queueId.toString(),
                FIELD_STREAM_KEY, streamKey,
                FIELD_LAST_PROCESSED_ID, "0",
                FIELD_MESSAGES_PROCESSED, "0",
                FIELD_STARTED_AT, Instant.now().toString(),
                FIELD_STATUS, STATUS_IN_PROGRESS
        );
        redisTemplate.opsForHash().putAll(progressKey, fields);
        redisTemplate.expire(progressKey, Duration.ofHours(24));
    }

    private void updateProgressStreamKey(String progressKey, String newStreamKey) {
        redisTemplate.opsForHash().put(progressKey, FIELD_STREAM_KEY, newStreamKey);
    }

    private void updateProgressAfterBatch(String progressKey, String lastProcessedId, long messagesProcessed) {
        redisTemplate.opsForHash().put(progressKey, FIELD_LAST_PROCESSED_ID, lastProcessedId);
        redisTemplate.opsForHash().put(progressKey, FIELD_MESSAGES_PROCESSED, String.valueOf(messagesProcessed));
    }

    private void completeProgressMarker(String progressKey, String status) {
        redisTemplate.opsForHash().put(progressKey, FIELD_STATUS, status);
        if (STATUS_COMPLETED.equals(status)) {
            // Delete marker on successful completion
            redisTemplate.delete(progressKey);
        }
        // On failure, leave marker for operator inspection (TTL will expire it in 24h)
    }

    // ── Startup detection of incomplete cleanups ────────────────────────

    /**
     * On startup, scan for keys matching the cleanup progress pattern with status IN_PROGRESS.
     * Logs a WARN for each incomplete cleanup found. Does NOT auto-resume.
     */
    @PostConstruct
    void detectIncompleteCleanups() {
        try {
            String pattern = PROGRESS_KEY_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys == null || keys.isEmpty()) {
                return;
            }

            for (String key : keys) {
                Object status = redisTemplate.opsForHash().get(key, FIELD_STATUS);
                if (STATUS_IN_PROGRESS.equals(status)) {
                    Object queueId = redisTemplate.opsForHash().get(key, FIELD_QUEUE_ID);
                    Object operation = redisTemplate.opsForHash().get(key, FIELD_OPERATION);
                    Object lastProcessedId = redisTemplate.opsForHash().get(key, FIELD_LAST_PROCESSED_ID);
                    log.warn("Incomplete cleanup detected on startup: queueId={}, operation={}, lastProcessedId={}. "
                                    + "Manual intervention required.",
                            queueId, operation, lastProcessedId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan for incomplete cleanups on startup: {}", e.getMessage());
        }
    }
}
