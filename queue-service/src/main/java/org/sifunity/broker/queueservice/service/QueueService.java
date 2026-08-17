// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.service;

import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing message queues.
 *
 * <p>Handles queue creation, retrieval, deletion, and statistics.
 * Queue metadata is stored in PostgreSQL, while message counts are
 * computed from Redis Streams via XLEN and XPENDING.</p>
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final QueueRepository queueRepository;
    private final StreamMessageRepository streamMessageRepository;
    private final RefCountService refCountService;
    private final StorageProvider storageProvider;
    private final String baseUrl;

    public QueueService(QueueRepository queueRepository,
                        StreamMessageRepository streamMessageRepository,
                        RefCountService refCountService,
                        StorageProvider storageProvider,
                        @Value("${queue.base-url:}") String baseUrl) {
        this.queueRepository = queueRepository;
        this.streamMessageRepository = streamMessageRepository;
        this.refCountService = refCountService;
        this.storageProvider = storageProvider;
        this.baseUrl = baseUrl;
    }

    /**
     * Create a new queue with the specified configuration.
     *
     * <p>Sets {@code polling = IMMEDIATE}, {@code maxConcurrentConnections} to the
     * requested value (or default 1), and generates the {@code queueUri}.</p>
     *
     * @param name                     the queue name
     * @param ownerId                  the environment ID that owns the queue
     * @param maxRetries               maximum retry attempts (defaults to 3 if null)
     * @param retryDelayMs             delay between retries in milliseconds (defaults to 5000 if null)
     * @param maxConcurrentConnections max concurrent connections (defaults to 1 if null)
     * @param domainId                 human-readable domain identifier
     * @param solutionId               human-readable solution identifier
     * @param solutionUuid             UUID of the solution this queue belongs to
     * @return the created queue DTO
     */
    @Transactional
    public QueueDTO createQueue(String name, UUID ownerId, Integer maxRetries, Long retryDelayMs,
                                Integer maxConcurrentConnections,
                                String domainId, String solutionId, UUID solutionUuid) {
        log.info("Creating queue: name={}, ownerId={}, solutionUuid={}", name, ownerId, solutionUuid);

        // Check for duplicate queue name per owner
        if (queueRepository.findByOwnerIdAndName(ownerId, name).isPresent()) {
            throw new IllegalArgumentException(
                    "Environment " + ownerId + " already has a queue named '" + name + "'");
        }

        Queue queue = new Queue();
        queue.setName(name);
        queue.setOwnerId(ownerId);
        queue.setSolutionUuid(solutionUuid);
        queue.setDomainId(domainId);
        queue.setSolutionId(solutionId);
        queue.setPolling("IMMEDIATE");
        queue.setMaxConcurrentConnections(maxConcurrentConnections != null ? maxConcurrentConnections : 1);
        queue.setMaxRetries(maxRetries != null ? maxRetries : 3);
        queue.setRetryDelayMs(retryDelayMs != null ? retryDelayMs : 5000L);

        Queue saved = queueRepository.save(queue);

        // Generate queueUri after save (need the ID)
        String queueUri = generateQueueUri(domainId, solutionId, saved.getId());
        saved.setQueueUri(queueUri);
        saved = queueRepository.save(saved);

        log.debug("Queue created: id={}, name={}, queueUri={}", saved.getId(), saved.getName(), queueUri);
        return toDTO(saved, domainId, solutionId);
    }

    /**
     * Backward-compatible createQueue without solutionUuid.
     */
    @Transactional
    public QueueDTO createQueue(String name, UUID ownerId, Integer maxRetries, Long retryDelayMs,
                                Integer maxConcurrentConnections,
                                String domainId, String solutionId) {
        return createQueue(name, ownerId, maxRetries, retryDelayMs, maxConcurrentConnections,
                domainId, solutionId, null);
    }

    /**
     * Backward-compatible createQueue without maxConcurrentConnections and domain/solution.
     */
    @Transactional
    public QueueDTO createQueue(String name, UUID ownerId, Integer maxRetries, Long retryDelayMs) {
        return createQueue(name, ownerId, maxRetries, retryDelayMs, null, "", "");
    }

    /**
     * Get queue by ID.
     */
    public QueueDTO getQueue(UUID queueId) {
        log.debug("Getting queue: queueId={}", queueId);
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + queueId));
        return toDTO(queue, null, null);
    }

    /**
     * Get queue by ID with domain/solution context for accurate message count.
     */
    public QueueDTO getQueue(UUID queueId, String domainId, String solutionId) {
        log.debug("Getting queue: queueId={}, domainId={}, solutionId={}", queueId, domainId, solutionId);
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + queueId));
        return toDTO(queue, domainId, solutionId);
    }

    /**
     * Get queue metadata for routing (cached).
     *
     * <p>Used on the hot path (enqueue/deliver) where only ownership verification
     * is needed. Returns a cached {@link QueueDTO} to avoid a database round-trip
     * on every message operation.</p>
     *
     * @param queueId the queue ID
     * @return the queue DTO
     * @throws QueueNotFoundException if the queue does not exist
     */
    @Cacheable(value = "queue-metadata", key = "#queueId", unless = "#result == null")
    public QueueDTO getQueueForRouting(UUID queueId) {
        log.debug("Cache miss for queue-metadata: queueId={}", queueId);
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + queueId));
        return toDTO(queue, null, null);
    }

    /**
     * Get queue details with fresh database stats (uncached).
     *
     * <p>Always queries the database for up-to-date {@code lastAccessed},
     * {@code messageCount}, and {@code lastModified} values. Use this for
     * REST API responses where fresh stats are needed.</p>
     *
     * @param queueId the queue ID
     * @return the queue DTO with fresh stats
     * @throws QueueNotFoundException if the queue does not exist
     */
    public QueueDTO getQueueDetails(UUID queueId) {
        return getQueue(queueId);
    }

    /**
     * Get all queues.
     */
    public List<QueueDTO> getAllQueues() {
        log.debug("Getting all queues");
        return queueRepository.findAll().stream()
                .map(q -> toDTO(q, null, null))
                .toList();
    }

    /**
     * Get all queues with message counts (requires domain/solution for stream key lookup).
     * Filters by solutionUuid for data isolation.
     */
    public List<QueueDTO> getAllQueues(String domainId, String solutionId, UUID solutionUuid) {
        log.debug("Getting all queues for solution: domainId={}, solutionId={}, solutionUuid={}",
                domainId, solutionId, solutionUuid);
        if (solutionUuid != null) {
            return queueRepository.findBySolutionUuid(solutionUuid).stream()
                    .map(q -> toDTO(q, domainId, solutionId))
                    .toList();
        }
        // Fallback: no solutionUuid available (should not happen in normal flow)
        log.warn("getAllQueues called without solutionUuid — returning empty list for safety");
        return List.of();
    }

    /**
     * Get all queues with message counts (backward-compatible, no solution scoping).
     */
    public List<QueueDTO> getAllQueues(String domainId, String solutionId) {
        log.debug("Getting all queues with message counts: domainId={}, solutionId={}", domainId, solutionId);
        return queueRepository.findAll().stream()
                .map(q -> toDTO(q, domainId, solutionId))
                .toList();
    }

    /**
     * Get all queues owned by an environment.
     */
    public List<QueueDTO> getQueuesByOwner(UUID ownerId) {
        log.debug("Getting queues by owner: ownerId={}", ownerId);
        return queueRepository.findByOwnerId(ownerId).stream()
                .map(q -> toDTO(q, null, null))
                .toList();
    }

    /**
     * Get all queues owned by an environment with message counts.
     */
    public List<QueueDTO> getQueuesByOwner(UUID ownerId, String domainId, String solutionId) {
        log.debug("Getting queues by owner with message counts: ownerId={}, domainId={}, solutionId={}", ownerId, domainId, solutionId);
        return queueRepository.findByOwnerId(ownerId).stream()
                .map(q -> toDTO(q, domainId, solutionId))
                .toList();
    }

    /**
     * Delete a queue and all its Redis Streams resources.
     *
     * <p>Deletes the main stream + DLQ stream + consumer groups, decrements ref counts
     * for all messages, and removes the queue from PostgreSQL.</p>
     */
    @CacheEvict(value = "queue-metadata", key = "#queueId")
    @Transactional
    public void deleteQueue(UUID queueId, String domainId, String solutionId, UUID environmentId) {
        log.info("Deleting queue: queueId={}", queueId);

        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + queueId));

        String streamKey = streamMessageRepository.streamKey(domainId, solutionId, environmentId, queueId);
        String dlqKey = streamMessageRepository.dlqStreamKey(domainId, solutionId, environmentId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);

        // Decrement ref counts for all messages in main stream
        decrementRefCountsForStream(streamKey, domainId, solutionId);

        // Decrement ref counts for all messages in DLQ stream
        decrementRefCountsForStream(dlqKey, domainId, solutionId);

        // Destroy consumer groups and delete streams
        streamMessageRepository.destroyConsumerGroup(streamKey, groupName);
        streamMessageRepository.deleteStream(streamKey);
        streamMessageRepository.destroyConsumerGroup(dlqKey, groupName);
        streamMessageRepository.deleteStream(dlqKey);

        // Delete queue from PostgreSQL
        queueRepository.delete(queue);
        log.info("Queue deleted: queueId={}", queueId);
    }

    /**
     * Backward-compatible deleteQueue without domain/solution/environment context.
     * Uses empty strings for domain/solution and the queue's ownerId as environmentId.
     */
    @CacheEvict(value = "queue-metadata", key = "#queueId")
    @Transactional
    public void deleteQueue(UUID queueId) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + queueId));
        deleteQueue(queueId, "", "", queue.getOwnerId());
    }

    /**
     * Get queue statistics using XLEN and XPENDING.
     */
    public QueueStatistics getQueueStatistics(UUID queueId, String domainId, String solutionId,
                                               UUID environmentId) {
        log.debug("Getting queue statistics: queueId={}", queueId);

        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + queueId));

        String streamKey = streamMessageRepository.resolveStreamKey(domainId, solutionId, environmentId, queueId);
        String dlqKey = streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, environmentId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);

        long totalUnprocessed = streamMessageRepository.messageCount(streamKey);
        long deliveredUnacked = streamMessageRepository.pendingCount(streamKey, groupName);
        long deadLetterCount = streamMessageRepository.messageCount(dlqKey);

        log.debug("Queue stats: queueId={}, unprocessed={}, pending={}, dlq={}",
                queueId, totalUnprocessed, deliveredUnacked, deadLetterCount);

        return new QueueStatistics(queueId, totalUnprocessed, deliveredUnacked, deadLetterCount,
                totalUnprocessed + deadLetterCount);
    }

    /**
     * Backward-compatible getQueueStatistics without domain/solution/environment.
     */
    public QueueStatistics getQueueStatistics(UUID queueId) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + queueId));
        return getQueueStatistics(queueId, "", "", queue.getOwnerId());
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private QueueDTO toDTO(Queue queue, String domainId, String solutionId) {
        // Compute messageCount via XLEN if we have domain/solution context
        long messageCount = 0;
        if (domainId != null && solutionId != null) {
            String streamKey = streamMessageRepository.resolveStreamKey(
                    domainId, solutionId, queue.getOwnerId(), queue.getId());
            messageCount = streamMessageRepository.messageCount(streamKey);
        } else {
            // Without context, try with empty strings (best-effort)
            String streamKey = streamMessageRepository.resolveStreamKey(
                    "", "", queue.getOwnerId(), queue.getId());
            messageCount = streamMessageRepository.messageCount(streamKey);
        }

        return new QueueDTO(
                queue.getId(),
                queue.getName(),
                queue.getOwnerId(),
                queue.getPolling(),
                queue.getQueueUri(),
                queue.getMaxConcurrentConnections(),
                queue.getCreated(),
                queue.getLastAccessed(),
                queue.getLastModified(),
                queue.getMaxRetries(),      // suppressed from JSON by @JsonIgnore on accessor
                queue.getRetryDelayMs(),    // suppressed from JSON by @JsonIgnore on accessor
                messageCount
        );
    }

    private String generateQueueUri(String domainId, String solutionId, UUID queueId) {
        String base = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "";
        if (base.isEmpty()) {
            return domainId + "/" + solutionId + "/queues/" + queueId + "/messages";
        }
        return base + "/" + domainId + "/" + solutionId + "/queues/" + queueId + "/messages";
    }

    private void decrementRefCountsForStream(String streamKey, String domainId, String solutionId) {
        List<String> storageKeys = streamMessageRepository.getAllStorageKeys(streamKey);
        for (String storageKey : storageKeys) {
            try {
                String refKey = refCountService.refCountKey(domainId, solutionId, storageKey);
                long newCount = refCountService.decrementAndGet(refKey);
                if (newCount <= 0) {
                    try {
                        storageProvider.delete(storageKey);
                    } catch (Exception e) {
                        log.warn("Failed to delete payload during queue cleanup: storageKey={}", storageKey, e);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to decrement ref count during queue cleanup: storageKey={}", storageKey, e);
            }
        }
    }

    /**
     * Queue statistics record.
     */
    public record QueueStatistics(
            UUID queueId,
            long pendingCount,
            long deliveredCount,
            long deadLetterCount,
            long totalProcessed
    ) {}
}
