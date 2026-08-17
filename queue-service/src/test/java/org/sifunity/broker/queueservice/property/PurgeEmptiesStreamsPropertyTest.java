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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 1: Purge empties both streams.
 *
 * <p>For any queue containing messages in its main stream and/or DLQ stream,
 * after a purge operation completes, both streams SHALL have zero entries
 * (XLEN == 0).</p>
 *
 * <p><b>Validates: Requirements 1.1, 1.2</b></p>
 */
@Tag("Feature: queue-purge-cascade-delete, Property 1: Purge empties both streams")
class PurgeEmptiesStreamsPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * **Validates: Requirements 1.1, 1.2**
     */
    @Property(tries = 100)
    @Label("Feature: queue-purge-cascade-delete, Property 1: Purge empties both streams")
    void purgeRemovesAllEntriesFromBothStreams(
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
        String streamKey = "function:msg_queue:domain:" + domainId + ":solution:" + solutionId
                + ":environment:" + environmentId + ":queue:" + queueId;
        String dlqKey = streamKey + ":dlq";

        when(smr.streamKey(domainId, solutionId, environmentId, queueId)).thenReturn(streamKey);
        when(smr.dlqStreamKey(domainId, solutionId, environmentId, queueId)).thenReturn(dlqKey);

        // Setup batched reads for main stream
        setupBatchedStream(smr, streamKey, mainMessageCount);
        // Setup batched reads for DLQ stream
        setupBatchedStream(smr, dlqKey, dlqMessageCount);

        // RefCountService returns 0 for all decrements (all payloads should be deleted)
        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenAnswer(inv ->
                "ref:" + inv.getArgument(2));
        when(rcs.decrementBatch(anyList())).thenAnswer(inv -> {
            List<String> keys = inv.getArgument(0);
            Map<String, Long> result = new LinkedHashMap<>();
            for (String key : keys) {
                result.put(key, 0L);
            }
            return result;
        });

        // Track XDEL calls to verify all entries are deleted
        List<String> mainDeletedEntries = Collections.synchronizedList(new ArrayList<>());
        List<String> dlqDeletedEntries = Collections.synchronizedList(new ArrayList<>());

        when(smr.deleteEntries(eq(streamKey), anyList())).thenAnswer(inv -> {
            List<String> ids = inv.getArgument(1);
            mainDeletedEntries.addAll(ids);
            return (long) ids.size();
        });
        when(smr.deleteEntries(eq(dlqKey), anyList())).thenAnswer(inv -> {
            List<String> ids = inv.getArgument(1);
            dlqDeletedEntries.addAll(ids);
            return (long) ids.size();
        });

        // Create service and invoke cleanupStream via reflection (package-private)
        QueueCleanupService service = new QueueCleanupService(
                smr, rcs, sp, redisTemplate, qr, cacheManager, executor);
        CleanupTestHelper.setConfigFields(service);

        String progressKey = "function:msg_queue:cleanup-progress:" + queueId + ":PURGE";
        CleanupTestHelper.invokeCleanupStream(service, streamKey, domainId, solutionId, progressKey);
        CleanupTestHelper.invokeCleanupStream(service, dlqKey, domainId, solutionId, progressKey);

        // Verify: all entries from main stream were deleted via XDEL
        assertThat(mainDeletedEntries).hasSize(mainMessageCount);
        // Verify: all entries from DLQ stream were deleted via XDEL
        assertThat(dlqDeletedEntries).hasSize(dlqMessageCount);
    }

    /**
     * Setup mock to return batched stream data simulating a stream with the given message count.
     */
    private void setupBatchedStream(StreamMessageRepository smr, String streamKey, int messageCount) {
        int batchSize = 1000; // matches default config
        int fullBatches = messageCount / batchSize;
        int remainder = messageCount % batchSize;

        // Track call count per stream key
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
