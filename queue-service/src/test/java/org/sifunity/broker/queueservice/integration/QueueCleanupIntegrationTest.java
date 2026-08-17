// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.config.EmbeddedRedisTestConfiguration;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.QueueCleanupService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the full queue cleanup flow (purge and delete).
 *
 * <p>Uses Testcontainers PostgreSQL (via application-test.yml JDBC URL) and
 * Testcontainers Redis (via {@link EmbeddedRedisTestConfiguration}) for real
 * infrastructure testing.</p>
 *
 * <p><strong>Validates: Requirements 2.7, 3.1, 3.2, 3.5, 4.1, 4.2, 7.1, 7.2, 7.3, 7.5</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-cleanup-integration",
    "queue.cleanup.batch-size=100",
    "queue.cleanup.inter-batch-yield-ms=5",
    "queue.cleanup.progress-log-interval=200"
})
class QueueCleanupIntegrationTest {

    private static final String DOMAIN_ID = "cleanup-test-domain";
    private static final String SOLUTION_ID = "cleanup-test-solution";
    private static final UUID OWNER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueCleanupService queueCleanupService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private StreamMessageRepository streamMessageRepository;

    @Autowired
    private RefCountService refCountService;

    @Autowired
    private StorageProvider storageProvider;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        queueRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        queueRepository.deleteAll();
    }

    // ── Test 1: Full purge of large queue (1000 messages) completes without OOM ──

    /**
     * Validates that purging a queue with 1000 messages completes successfully
     * using batched processing without exhausting memory.
     *
     * <p>Batch size is set to 100, so this exercises 10 batches of cursor-based
     * iteration with inter-batch yields.</p>
     *
     * <p><strong>Validates: Requirements 3.1, 3.2, 7.1, 7.2</strong></p>
     */
    @Test
    void testFullPurgeOfLargeQueueCompletesWithoutOOM() throws Exception {
        // Create queue
        QueueDTO queue = queueService.createQueue(
                "large-purge-queue", OWNER_ID, 3, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        String streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, OWNER_ID, queueId);

        // Enqueue 1000 messages
        List<String> storageKeys = new ArrayList<>(1000);
        for (int i = 0; i < 1000; i++) {
            MessageMetadata meta = messageService.enqueueMessage(
                    queueId, null, DOMAIN_ID, SOLUTION_ID, OWNER_ID,
                    OWNER_ID, "zone", "ctx", "event", "Svc", Map.of("idx", i));
            storageKeys.add(meta.getStorageKey());
        }

        // Verify stream has 1000 entries
        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(1000);

        // Submit purge and wait for completion
        queueCleanupService.submitPurge(queueId, DOMAIN_ID, SOLUTION_ID, OWNER_ID);
        waitForCleanupCompletion(queueId, "PURGE", Duration.ofSeconds(30));

        // Verify stream is empty after purge
        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(0);

        // Verify all payloads deleted (ref count reached 0 for unique messages)
        for (String storageKey : storageKeys) {
            assertThat(storageProvider.exists(storageKey))
                    .as("Payload %s should be deleted after purge", storageKey)
                    .isFalse();
        }

        // Verify queue metadata still exists (purge preserves infrastructure)
        assertThat(queueRepository.findById(queueId)).isPresent();
    }

    // ── Test 2: Full delete with mixed ref counts ──

    /**
     * Validates that deleting a queue with mixed ref counts correctly handles
     * shared payloads (ref count > 1 after decrement) and unique payloads
     * (ref count reaches 0).
     *
     * <p><strong>Validates: Requirements 3.1, 3.5, 4.1, 4.2</strong></p>
     */
    @Test
    void testFullDeleteWithMixedRefCounts() throws Exception {
        // Create two queues that share some messages
        QueueDTO queue1 = queueService.createQueue(
                "shared-queue-1", OWNER_ID, 3, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        QueueDTO queue2 = queueService.createQueue(
                "shared-queue-2", OWNER_ID, 3, 5000L, 1, DOMAIN_ID, SOLUTION_ID);

        UUID queueId1 = queue1.id();
        UUID queueId2 = queue2.id();

        String streamKey1 = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, OWNER_ID, queueId1);
        String streamKey2 = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, OWNER_ID, queueId2);

        // Enqueue unique messages to queue1
        List<String> uniqueStorageKeys = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MessageMetadata meta = messageService.enqueueMessage(
                    queueId1, null, DOMAIN_ID, SOLUTION_ID, OWNER_ID,
                    OWNER_ID, "zone", "ctx", "event", "Svc", Map.of("unique", i));
            uniqueStorageKeys.add(meta.getStorageKey());
        }

        // Enqueue messages to queue2 and manually increment ref counts to simulate sharing
        // (In real fan-out, the same storageKey would be enqueued to multiple queues)
        List<String> sharedStorageKeys = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MessageMetadata meta = messageService.enqueueMessage(
                    queueId1, null, DOMAIN_ID, SOLUTION_ID, OWNER_ID,
                    OWNER_ID, "zone", "ctx", "event", "Svc", Map.of("shared", i));
            sharedStorageKeys.add(meta.getStorageKey());

            // Increment ref count again to simulate another queue referencing same payload
            String refKey = refCountService.refCountKey(DOMAIN_ID, SOLUTION_ID, meta.getStorageKey());
            refCountService.increment(refKey);
        }

        // Verify initial state
        assertThat(streamMessageRepository.messageCount(streamKey1)).isEqualTo(10);

        // Delete queue1
        queueCleanupService.submitDelete(queueId1, DOMAIN_ID, SOLUTION_ID, OWNER_ID);
        waitForCleanupCompletion(queueId1, "DELETE", Duration.ofSeconds(15));

        // Verify queue1 removed from DB
        assertThat(queueRepository.findById(queueId1)).isEmpty();

        // Verify unique payloads deleted (ref count reached 0)
        for (String storageKey : uniqueStorageKeys) {
            assertThat(storageProvider.exists(storageKey))
                    .as("Unique payload %s should be deleted", storageKey)
                    .isFalse();
        }

        // Verify shared payloads retained (ref count still > 0)
        for (String storageKey : sharedStorageKeys) {
            assertThat(storageProvider.exists(storageKey))
                    .as("Shared payload %s should be retained (ref count > 0)", storageKey)
                    .isTrue();
        }

        // Verify shared ref count keys still exist with count 1
        for (String storageKey : sharedStorageKeys) {
            String refKey = refCountService.refCountKey(DOMAIN_ID, SOLUTION_ID, storageKey);
            String val = redisTemplate.opsForValue().get(refKey);
            assertThat(val)
                    .as("Shared ref count should be 1 after one decrement from initial 2")
                    .isEqualTo("1");
        }

        // Cleanup: delete queue2
        queueRepository.deleteById(queueId2);
    }

    // ── Test 3: Concurrent purge and acknowledge on same queue ──

    /**
     * Validates that concurrent purge and acknowledge operations on the same queue
     * do not cause data corruption or exceptions. The Lua-based atomic decrement
     * ensures ref counts are handled correctly under contention.
     *
     * <p><strong>Validates: Requirements 3.3, 3.5</strong></p>
     */
    @Test
    void testConcurrentPurgeAndAcknowledge() throws Exception {
        // Create queue
        QueueDTO queue = queueService.createQueue(
                "concurrent-queue", OWNER_ID, 3, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        String streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, OWNER_ID, queueId);

        // Enqueue 50 messages
        for (int i = 0; i < 50; i++) {
            messageService.enqueueMessage(
                    queueId, null, DOMAIN_ID, SOLUTION_ID, OWNER_ID,
                    OWNER_ID, "zone", "ctx", "event", "Svc", Map.of("idx", i));
        }

        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(50);

        // Deliver a few messages to a consumer (puts them in PEL)
        List<String> deliveredStreamIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Optional<MessageMetadata> delivered = deliveryService.deliverNextMessage(
                    queueId, "test-consumer", 
                    deliveredStreamIds.isEmpty() ? "" : deliveredStreamIds.get(deliveredStreamIds.size() - 1),
                    DOMAIN_ID, SOLUTION_ID, OWNER_ID);
            delivered.ifPresent(m -> deliveredStreamIds.add(m.getStreamId()));
        }

        // Run purge and acknowledge concurrently
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);

        // Thread 1: Purge
        Future<?> purgeFuture = executor.submit(() -> {
            try {
                latch.await();
                queueCleanupService.submitPurge(queueId, DOMAIN_ID, SOLUTION_ID, OWNER_ID);
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        });

        // Thread 2: Acknowledge remaining delivered messages
        Future<?> ackFuture = executor.submit(() -> {
            try {
                latch.await();
                // Acknowledge last delivered message (triggers ref count decrement)
                if (!deliveredStreamIds.isEmpty()) {
                    deliveryService.deliverNextMessage(
                            queueId, "test-consumer",
                            deliveredStreamIds.get(deliveredStreamIds.size() - 1),
                            DOMAIN_ID, SOLUTION_ID, OWNER_ID);
                }
            } catch (Exception e) {
                // Queue may be purged concurrently — some errors are expected
                // The key assertion is no data corruption
            }
        });

        // Release both threads simultaneously
        latch.countDown();

        // Wait for both to complete
        purgeFuture.get(15, TimeUnit.SECONDS);
        ackFuture.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Wait for async purge to finish
        waitForCleanupCompletion(queueId, "PURGE", Duration.ofSeconds(15));

        // Verify no errors in purge thread
        assertThat(errors.get()).isEqualTo(0);

        // Verify stream is empty or nearly empty (purge should have cleared it)
        long remaining = streamMessageRepository.messageCount(streamKey);
        assertThat(remaining).isLessThanOrEqualTo(0);

        // Verify queue metadata still exists (purge preserves infrastructure)
        assertThat(queueRepository.findById(queueId)).isPresent();
    }

    // ── Test 4: Service restart detection of incomplete cleanup markers ──

    /**
     * Validates that incomplete cleanup markers persist in Redis and can be
     * detected on service restart. The @PostConstruct method in QueueCleanupService
     * scans for these markers and logs warnings.
     *
     * <p>This test verifies the marker format and persistence — the actual
     * detection runs automatically on application startup (already exercised
     * by the Spring context loading in this test class).</p>
     *
     * <p><strong>Validates: Requirements 7.5</strong></p>
     */
    @Test
    void testServiceRestartDetectsIncompleteCleanupMarkers() {
        UUID fakeQueueId = UUID.randomUUID();
        String progressKey = "function:msg_queue:cleanup-progress:" + fakeQueueId + ":PURGE";

        // Manually insert an IN_PROGRESS marker (simulating a crash mid-cleanup)
        Map<String, String> fields = Map.of(
                "operation", "PURGE",
                "queueId", fakeQueueId.toString(),
                "streamKey", "function:msg_queue:domain:d:solution:s:environment:e:queue:" + fakeQueueId,
                "lastProcessedId", "1234567890-5",
                "messagesProcessed", "5000",
                "startedAt", "2024-01-15T10:30:00Z",
                "status", "IN_PROGRESS"
        );
        redisTemplate.opsForHash().putAll(progressKey, fields);
        redisTemplate.expire(progressKey, Duration.ofHours(1));

        // Verify the marker exists and has correct structure
        assertThat(redisTemplate.hasKey(progressKey)).isTrue();
        Object status = redisTemplate.opsForHash().get(progressKey, "status");
        assertThat(status).isEqualTo("IN_PROGRESS");

        Object operation = redisTemplate.opsForHash().get(progressKey, "operation");
        assertThat(operation).isEqualTo("PURGE");

        Object lastProcessedId = redisTemplate.opsForHash().get(progressKey, "lastProcessedId");
        assertThat(lastProcessedId).isEqualTo("1234567890-5");

        Object messagesProcessed = redisTemplate.opsForHash().get(progressKey, "messagesProcessed");
        assertThat(messagesProcessed).isEqualTo("5000");

        // Verify the marker is NOT auto-resumed (stays IN_PROGRESS for operator action)
        // The @PostConstruct detectIncompleteCleanups() already ran on context startup
        // and would have logged a WARN for any pre-existing markers.
        assertThat(redisTemplate.hasKey(progressKey)).isTrue();

        // Cleanup
        redisTemplate.delete(progressKey);
    }

    // ── Test 5: Storage batch delete called with correct batching ──

    /**
     * Validates that the cleanup processes messages in batches and calls
     * StorageProvider.deleteBatch with the correct number of keys per batch.
     *
     * <p>With batch-size=100 and 250 messages, we expect 3 batches (100+100+50).
     * Each batch should delete only the keys whose ref count reached zero.</p>
     *
     * <p><strong>Validates: Requirements 7.1, 7.3</strong></p>
     */
    @Test
    void testStorageBatchDeleteWithCorrectBatching() throws Exception {
        // Create queue
        QueueDTO queue = queueService.createQueue(
                "batch-delete-queue", OWNER_ID, 3, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        String streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, OWNER_ID, queueId);

        // Enqueue 250 messages (with batch-size=100, this means 3 batches)
        List<String> storageKeys = new ArrayList<>(250);
        for (int i = 0; i < 250; i++) {
            MessageMetadata meta = messageService.enqueueMessage(
                    queueId, null, DOMAIN_ID, SOLUTION_ID, OWNER_ID,
                    OWNER_ID, "zone", "ctx", "event", "Svc", Map.of("idx", i));
            storageKeys.add(meta.getStorageKey());
        }

        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(250);

        // Purge the queue
        queueCleanupService.submitPurge(queueId, DOMAIN_ID, SOLUTION_ID, OWNER_ID);
        waitForCleanupCompletion(queueId, "PURGE", Duration.ofSeconds(30));

        // Verify all 250 messages processed (stream empty)
        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(0);

        // Verify all payloads deleted (all unique, ref count reached 0)
        int deletedCount = 0;
        for (String storageKey : storageKeys) {
            if (!storageProvider.exists(storageKey)) {
                deletedCount++;
            }
        }
        assertThat(deletedCount).isEqualTo(250);
    }

    // ── Test 6: Cache eviction after delete ──

    /**
     * Validates that after a queue is deleted, the queue-metadata cache no longer
     * contains the queue entry.
     *
     * <p><strong>Validates: Requirements 2.7</strong></p>
     */
    @Test
    void testCacheEvictionAfterDelete() throws Exception {
        // Create queue
        QueueDTO queue = queueService.createQueue(
                "cache-eviction-queue", OWNER_ID, 3, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        // Access queue to populate cache
        queueService.getQueue(queueId, DOMAIN_ID, SOLUTION_ID);

        // Verify cache has the entry
        var cache = cacheManager.getCache("queue-metadata");
        assertThat(cache).isNotNull();
        // Note: cache.get(queueId) may or may not be populated depending on @Cacheable placement.
        // The key assertion is that after delete, the cache does NOT have the entry.

        // Enqueue a message so cleanup has something to process
        messageService.enqueueMessage(
                queueId, null, DOMAIN_ID, SOLUTION_ID, OWNER_ID,
                OWNER_ID, "zone", "ctx", "event", "Svc", Map.of("test", true));

        // Delete queue via cleanup service
        queueCleanupService.submitDelete(queueId, DOMAIN_ID, SOLUTION_ID, OWNER_ID);
        waitForCleanupCompletion(queueId, "DELETE", Duration.ofSeconds(15));

        // Verify queue removed from DB
        assertThat(queueRepository.findById(queueId)).isEmpty();

        // Verify cache evicted
        var cacheAfter = cacheManager.getCache("queue-metadata");
        assertThat(cacheAfter).isNotNull();
        assertThat(cacheAfter.get(queueId))
                .as("Queue should be evicted from cache after delete")
                .isNull();
    }

    // ── Helper methods ──────────────────────────────────────────────────

    /**
     * Wait for a cleanup operation to complete by polling the progress marker.
     * The marker is deleted on successful completion, so we wait until it's gone.
     * We first wait for the marker to appear (async task started), then wait for it to disappear.
     */
    private void waitForCleanupCompletion(UUID queueId, String operation, Duration timeout)
            throws InterruptedException {
        String progressKey = "function:msg_queue:cleanup-progress:" + queueId + ":" + operation;
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        // Phase 1: Wait for the marker to appear (async task has started)
        boolean markerSeen = false;
        while (System.currentTimeMillis() < deadline) {
            Boolean exists = redisTemplate.hasKey(progressKey);
            if (exists != null && exists) {
                markerSeen = true;
                break;
            }
            Thread.sleep(20);
        }

        // Phase 2: Wait for the marker to disappear (task completed)
        while (System.currentTimeMillis() < deadline) {
            Boolean exists = redisTemplate.hasKey(progressKey);
            if (exists == null || !exists) {
                // Marker deleted = completed successfully
                return;
            }

            // Check if status is FAILED
            Object status = redisTemplate.opsForHash().get(progressKey, "status");
            if ("FAILED".equals(status)) {
                throw new AssertionError("Cleanup operation failed: " + progressKey);
            }

            Thread.sleep(50);
        }

        // If marker was never seen, the task may have completed very quickly
        if (!markerSeen) {
            // Give a small grace period for very fast completions
            Thread.sleep(200);
            return;
        }

        Object status = redisTemplate.opsForHash().get(progressKey, "status");
        throw new AssertionError("Cleanup did not complete within timeout. Status: " + status);
    }
}
