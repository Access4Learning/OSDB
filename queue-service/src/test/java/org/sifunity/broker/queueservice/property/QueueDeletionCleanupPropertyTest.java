// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.QueueService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 4: Queue deletion cleanup — deletion removes stream + DLQ + group.
 *
 * <p>Validates: Requirements 1.6, 8.2</p>
 */
class QueueDeletionCleanupPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * **Validates: Requirements 1.6, 8.2**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 4: Queue deletion cleanup")
    void deletionRemovesStreamDlqAndGroup(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId,
            @ForAll @IntRange(min = 0, max = 5) int messageCount) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        Queue queue = new Queue();
        queue.setId(queueId);
        queue.setName("delete-queue");
        queue.setOwnerId(environmentId);
        queue.setPolling("IMMEDIATE");
        queue.setMaxConcurrentConnections(1);
        queue.setMaxRetries(3);
        queue.setRetryDelayMs(5000L);
        queue.setCreated(Instant.now());

        String streamKey = "function:msg_queue:domain:d:solution:s:environment:" + environmentId + ":queue:" + queueId;
        String dlqKey = streamKey + ":dlq";
        String groupName = "group:" + queueId;

        when(qr.findById(queueId)).thenReturn(Optional.of(queue));
        when(smr.streamKey("d", "s", environmentId, queueId)).thenReturn(streamKey);
        when(smr.dlqStreamKey("d", "s", environmentId, queueId)).thenReturn(dlqKey);
        when(smr.groupName(queueId)).thenReturn(groupName);

        // Simulate storage keys in stream
        List<String> storageKeys = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            storageKeys.add("key-" + i);
        }
        when(smr.getAllStorageKeys(streamKey)).thenReturn(storageKeys);
        when(smr.getAllStorageKeys(dlqKey)).thenReturn(Collections.emptyList());
        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
        when(rcs.decrementAndGet(anyString())).thenReturn(0L);

        QueueService svc = new QueueService(qr, smr, rcs, sp, "");
        svc.deleteQueue(queueId, "d", "s", environmentId);

        // Verify both streams deleted
        verify(smr).deleteStream(streamKey);
        verify(smr).deleteStream(dlqKey);

        // Verify consumer groups destroyed
        verify(smr).destroyConsumerGroup(streamKey, groupName);
        verify(smr).destroyConsumerGroup(dlqKey, groupName);

        // Verify queue deleted from DB
        verify(qr).delete(queue);
    }
}
