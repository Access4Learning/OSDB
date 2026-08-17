// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.redis.StreamBatch;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.QueueCleanupService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 4: Exactly one ref count decrement per message.
 *
 * <p>For any queue with N messages across its main stream and DLQ stream,
 * a purge or delete operation SHALL decrement exactly N ref count keys total —
 * one per message, no double-decrements, no skipped messages.</p>
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.5, 5.1</b></p>
 */
@Tag("Feature: queue-purge-cascade-delete, Property 4: Exactly one decrement per message")
class ExactDecrementCountPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * **Validates: Requirements 3.1, 3.2, 3.5, 5.1**
     */
    @Property(tries = 100)
    @Label("Feature: queue-purge-cascade-delete, Property 4: Exactly one decrement per message")
    void decrementCountEqualsMessageCount(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId,
            @ForAll @IntRange(min = 0, max = 500) int mainMessageCount,
            @ForAll @IntRange(min = 0, max = 500) int dlqMessageCount) {

        // Setup mocks
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        QueueRepository qr = mock(QueueRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);

        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);

        String domainId = "test-domain";
        String solutionId = "test-solution";
        String streamKey = "stream:" + queueId;
        String dlqKey = streamKey + ":dlq";

        when(smr.streamKey(domainId, solutionId, environmentId, queueId)).thenReturn(streamKey);
        when(smr.dlqStreamKey(domainId, solutionId, environmentId, queueId)).thenReturn(dlqKey);

        // Setup batched reads
        setupBatchedStream(smr, streamKey, mainMessageCount);
        setupBatchedStream(smr, dlqKey, dlqMessageCount);

        // Track total decrements
        AtomicInteger totalDecrements = new AtomicInteger(0);

        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenAnswer(inv ->
                "ref:" + inv.getArgument(2));
        when(rcs.decrementBatch(anyList())).thenAnswer(inv -> {
            List<String> keys = inv.getArgument(0);
            totalDecrements.addAndGet(keys.size());
            Map<String, Long> result = new LinkedHashMap<>();
            for (String key : keys) {
                result.put(key, 1L); // non-zero so no storage deletes
            }
            return result;
        });
        when(smr.deleteEntries(anyString(), anyList())).thenAnswer(inv ->
                (long) ((List<?>) inv.getArgument(1)).size());

        // Create service and run cleanup on both streams
        QueueCleanupService service = new QueueCleanupService(
                smr, rcs, sp, redisTemplate, qr, cacheManager, executor);
        CleanupTestHelper.setConfigFields(service);

        String progressKey = "function:msg_queue:cleanup-progress:" + queueId + ":PURGE";
        CleanupTestHelper.invokeCleanupStream(service, streamKey, domainId, solutionId, progressKey);
        CleanupTestHelper.invokeCleanupStream(service, dlqKey, domainId, solutionId, progressKey);

        // Verify: total decrements == total message count
        int expectedTotal = mainMessageCount + dlqMessageCount;
        assertThat(totalDecrements.get()).isEqualTo(expectedTotal);
    }

    private void setupBatchedStream(StreamMessageRepository smr, String streamKey, int messageCount) {
        int batchSize = 1000;
        final int[] callCount = {0};

        when(smr.getStorageKeysBatch(eq(streamKey), anyString(), anyInt())).thenAnswer(inv -> {
            int currentCall = callCount[0]++;
            int startIdx = currentCall * batchSize;

            if (startIdx >= messageCount) {
                return new StreamBatch(List.of(), List.of(), "0", false);
            }

            int count = Math.min(batchSize, messageCount - startIdx);
            List<String> storageKeys = new ArrayList<>(count);
            List<String> entryIds = new ArrayList<>(count);

            for (int i = startIdx; i < startIdx + count; i++) {
                storageKeys.add("storage-key-" + i);
                entryIds.add((1000000 + i) + "-0");
            }

            String lastId = entryIds.get(entryIds.size() - 1);
            boolean hasMore = (startIdx + count) < messageCount;

            return new StreamBatch(storageKeys, entryIds, lastId, hasMore);
        });
    }
}
