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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 2: Purge preserves queue infrastructure.
 *
 * <p>For any queue with metadata and an active consumer group, after a purge
 * operation completes, the queue metadata in PostgreSQL SHALL be unchanged
 * and the consumer group SHALL still exist.</p>
 *
 * <p><b>Validates: Requirements 1.3, 1.4</b></p>
 */
@Tag("Feature: queue-purge-cascade-delete, Property 2: Purge preserves queue infrastructure")
class PurgePreservesInfraPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> queueNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30);
    }

    /**
     * **Validates: Requirements 1.3, 1.4**
     */
    @Property(tries = 100)
    @Label("Feature: queue-purge-cascade-delete, Property 2: Purge preserves queue infrastructure")
    void purgePreservesMetadataAndConsumerGroup(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("queueNames") String queueName,
            @ForAll @IntRange(min = 0, max = 200) int messageCount,
            @ForAll @IntRange(min = 1, max = 10) int maxRetries,
            @ForAll @IntRange(min = 1000, max = 60000) int retryDelayMs) {

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
        when(redisTemplate.delete(anyString())).thenReturn(true);

        String domainId = "test-domain";
        String solutionId = "test-solution";
        String streamKey = "function:msg_queue:domain:" + domainId + ":solution:" + solutionId
                + ":environment:" + environmentId + ":queue:" + queueId;
        String dlqKey = streamKey + ":dlq";
        String groupName = "group:" + queueId;

        when(smr.streamKey(domainId, solutionId, environmentId, queueId)).thenReturn(streamKey);
        when(smr.dlqStreamKey(domainId, solutionId, environmentId, queueId)).thenReturn(dlqKey);
        when(smr.groupName(queueId)).thenReturn(groupName);

        // Setup empty streams (messages already processed or simple case)
        when(smr.getStorageKeysBatch(eq(streamKey), anyString(), anyInt())).thenReturn(
                new StreamBatch(List.of(), List.of(), "0", false));
        when(smr.getStorageKeysBatch(eq(dlqKey), anyString(), anyInt())).thenReturn(
                new StreamBatch(List.of(), List.of(), "0", false));

        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
        when(rcs.decrementBatch(anyList())).thenReturn(Map.of());

        // Create service and invoke cleanup (simulating purge)
        QueueCleanupService service = new QueueCleanupService(
                smr, rcs, sp, redisTemplate, qr, cacheManager, executor);
        CleanupTestHelper.setConfigFields(service);

        String progressKey = "function:msg_queue:cleanup-progress:" + queueId + ":PURGE";
        CleanupTestHelper.invokeCleanupStream(service, streamKey, domainId, solutionId, progressKey);
        CleanupTestHelper.invokeCleanupStream(service, dlqKey, domainId, solutionId, progressKey);

        // Verify: consumer group is NEVER destroyed during purge
        verify(smr, never()).destroyConsumerGroup(anyString(), anyString());

        // Verify: stream keys are NEVER deleted during purge (only entries via XDEL)
        verify(smr, never()).deleteStream(anyString());

        // Verify: queue metadata is NEVER deleted from PostgreSQL during purge
        verify(qr, never()).deleteById(any());
        verify(qr, never()).delete(any());
    }
}
