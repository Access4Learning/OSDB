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
 * Property 6: Operation continues despite individual failures.
 *
 * <p>For any queue where some ref count decrements or storage deletes fail,
 * the cleanup operation SHALL continue processing all remaining messages.
 * The number of attempted decrements SHALL still equal the total message count.</p>
 *
 * <p><b>Validates: Requirements 4.3, 5.3</b></p>
 */
@Tag("Feature: queue-purge-cascade-delete, Property 6: Operation continues despite failures")
class CleanupResiliencePropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * Generate a set of indices where failures should be injected.
     */
    @Provide
    Arbitrary<Set<Integer>> failureIndices() {
        return Arbitraries.integers().between(0, 499)
                .set().ofMinSize(0).ofMaxSize(20);
    }

    /**
     * **Validates: Requirements 4.3, 5.3**
     */
    @Property(tries = 100)
    @Label("Feature: queue-purge-cascade-delete, Property 6: Operation continues despite failures")
    void operationContinuesDespiteFailures(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId,
            @ForAll @IntRange(min = 1, max = 200) int messageCount,
            @ForAll("failureIndices") Set<Integer> storageFailureIndices) {

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

        // Setup batched reads
        setupBatchedStream(smr, streamKey, messageCount);

        // Track total decrements attempted
        AtomicInteger totalDecrementsAttempted = new AtomicInteger(0);

        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenAnswer(inv ->
                "ref:" + inv.getArgument(2));

        // decrementBatch succeeds but returns 0 for all (triggering storage deletes)
        when(rcs.decrementBatch(anyList())).thenAnswer(inv -> {
            List<String> keys = inv.getArgument(0);
            totalDecrementsAttempted.addAndGet(keys.size());
            Map<String, Long> result = new LinkedHashMap<>();
            for (String key : keys) {
                result.put(key, 0L);
            }
            return result;
        });

        // Storage deleteBatch throws for batches containing failure indices
        doAnswer(inv -> {
            List<String> keys = inv.getArgument(0);
            // Simulate partial failure by throwing — the service should catch and continue
            for (String key : keys) {
                int idx = Integer.parseInt(key.replace("storage-key-", ""));
                if (storageFailureIndices.contains(idx)) {
                    throw new RuntimeException("Simulated storage failure for key: " + key);
                }
            }
            return null;
        }).when(sp).deleteBatch(anyList());

        when(smr.deleteEntries(anyString(), anyList())).thenAnswer(inv ->
                (long) ((List<?>) inv.getArgument(1)).size());

        // Track total entries XDEL'd
        AtomicInteger totalEntriesDeleted = new AtomicInteger(0);
        when(smr.deleteEntries(eq(streamKey), anyList())).thenAnswer(inv -> {
            List<String> ids = inv.getArgument(1);
            totalEntriesDeleted.addAndGet(ids.size());
            return (long) ids.size();
        });

        // Create service and run cleanup
        QueueCleanupService service = new QueueCleanupService(
                smr, rcs, sp, redisTemplate, qr, cacheManager, executor);
        CleanupTestHelper.setConfigFields(service);

        String progressKey = "function:msg_queue:cleanup-progress:" + queueId + ":PURGE";
        CleanupTestHelper.invokeCleanupStream(service, streamKey, domainId, solutionId, progressKey);

        // Verify: all messages were attempted for decrement regardless of storage failures
        assertThat(totalDecrementsAttempted.get()).isEqualTo(messageCount);

        // Verify: all entries were XDEL'd (operation continued past failures)
        assertThat(totalEntriesDeleted.get()).isEqualTo(messageCount);
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
