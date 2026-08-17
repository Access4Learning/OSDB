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
 * Property 19: Queue deletion ref counts — queue deletion decrements all ref counts.
 *
 * <p>Validates: Requirements 12.5</p>
 */
class QueueDeletionRefCountPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * **Validates: Requirements 12.5**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 19: Queue deletion ref counts")
    void queueDeletionDecrementsAllRefCounts(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId,
            @ForAll @IntRange(min = 1, max = 8) int messageCount) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        Queue queue = new Queue();
        queue.setId(queueId);
        queue.setName("del-queue");
        queue.setOwnerId(environmentId);
        queue.setPolling("IMMEDIATE");
        queue.setMaxConcurrentConnections(1);
        queue.setMaxRetries(3);
        queue.setRetryDelayMs(5000L);
        queue.setCreated(Instant.now());

        String streamKey = "stream-key";
        String dlqKey = "dlq-key";
        String groupName = "group:" + queueId;

        when(qr.findById(queueId)).thenReturn(Optional.of(queue));
        when(smr.streamKey("d", "s", environmentId, queueId)).thenReturn(streamKey);
        when(smr.dlqStreamKey("d", "s", environmentId, queueId)).thenReturn(dlqKey);
        when(smr.groupName(queueId)).thenReturn(groupName);

        // Create storage keys for messages in the stream
        List<String> storageKeys = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            storageKeys.add("storage-key-" + i);
        }
        when(smr.getAllStorageKeys(streamKey)).thenReturn(storageKeys);
        when(smr.getAllStorageKeys(dlqKey)).thenReturn(Collections.emptyList());

        // All ref counts reach zero → payload should be deleted
        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenAnswer(inv -> "ref:" + inv.getArgument(2));
        when(rcs.decrementAndGet(anyString())).thenReturn(0L);

        QueueService svc = new QueueService(qr, smr, rcs, sp, "");
        svc.deleteQueue(queueId, "d", "s", environmentId);

        // Verify ref count decremented for each message
        verify(rcs, times(messageCount)).decrementAndGet(anyString());

        // Verify payload deleted for each message (since count reached 0)
        verify(sp, times(messageCount)).delete(anyString());
    }
}
