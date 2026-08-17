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
 * Property 5: Payload deleted if and only if ref count reaches zero.
 *
 * <p>For any message whose ref count reaches zero (or below) after decrement,
 * the corresponding payload SHALL be deleted from the StorageProvider.
 * For any message whose ref count remains above zero, the payload SHALL NOT be deleted.</p>
 *
 * <p><b>Validates: Requirements 4.1, 4.2, 6.1</b></p>
 */
@Tag("Feature: queue-purge-cascade-delete, Property 5: Payload deleted if and only if ref count reaches zero")
class ConditionalPayloadDeletionPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * Generate a list of ref counts where some are 0 (should delete) and some are >0 (should retain).
     */
    @Provide
    Arbitrary<List<Long>> mixedRefCounts() {
        return Arbitraries.longs().between(0, 5)
                .list().ofMinSize(1).ofMaxSize(100);
    }

    /**
     * **Validates: Requirements 4.1, 4.2, 6.1**
     */
    @Property(tries = 100)
    @Label("Feature: queue-purge-cascade-delete, Property 5: Payload deleted if and only if ref count reaches zero")
    void payloadDeletedIffRefCountReachesZero(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("mixedRefCounts") List<Long> refCountsAfterDecrement) {

        int messageCount = refCountsAfterDecrement.size();

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

        // Build storage keys and entry IDs
        List<String> storageKeys = new ArrayList<>(messageCount);
        List<String> entryIds = new ArrayList<>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            storageKeys.add("storage-key-" + i);
            entryIds.add((1000000 + i) + "-0");
        }

        // Single batch (all messages fit in one batch since max is 100)
        String lastId = entryIds.get(entryIds.size() - 1);
        StreamBatch batch = new StreamBatch(storageKeys, entryIds, lastId, false);

        when(smr.getStorageKeysBatch(eq(streamKey), anyString(), anyInt()))
                .thenReturn(batch)
                .thenReturn(new StreamBatch(List.of(), List.of(), "0", false));

        // Setup ref count key mapping
        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenAnswer(inv ->
                "ref:" + inv.getArgument(2));

        // Return the pre-determined ref counts from decrementBatch
        when(rcs.decrementBatch(anyList())).thenAnswer(inv -> {
            List<String> keys = inv.getArgument(0);
            Map<String, Long> result = new LinkedHashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                result.put(keys.get(i), refCountsAfterDecrement.get(i));
            }
            return result;
        });

        when(smr.deleteEntries(anyString(), anyList())).thenReturn((long) messageCount);

        // Track which storage keys are passed to deleteBatch
        List<String> actuallyDeleted = new ArrayList<>();
        doAnswer(inv -> {
            List<String> keys = inv.getArgument(0);
            actuallyDeleted.addAll(keys);
            return null;
        }).when(sp).deleteBatch(anyList());

        // Create service and run cleanup
        QueueCleanupService service = new QueueCleanupService(
                smr, rcs, sp, redisTemplate, qr, cacheManager, executor);
        CleanupTestHelper.setConfigFields(service);

        String progressKey = "function:msg_queue:cleanup-progress:" + queueId + ":PURGE";
        CleanupTestHelper.invokeCleanupStream(service, streamKey, domainId, solutionId, progressKey);

        // Determine expected deletions: keys where ref count <= 0
        List<String> expectedDeleted = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            if (refCountsAfterDecrement.get(i) <= 0) {
                expectedDeleted.add(storageKeys.get(i));
            }
        }

        // Verify: exactly the keys with ref count <= 0 were deleted
        assertThat(actuallyDeleted).containsExactlyInAnyOrderElementsOf(expectedDeleted);

        // Verify: keys with ref count > 0 were NOT deleted
        for (int i = 0; i < messageCount; i++) {
            if (refCountsAfterDecrement.get(i) > 0) {
                assertThat(actuallyDeleted).doesNotContain(storageKeys.get(i));
            }
        }
    }
}
