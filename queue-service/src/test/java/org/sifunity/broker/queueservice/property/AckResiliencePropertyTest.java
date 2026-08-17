// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 16: ACK resilience — ACK succeeds even when payload delete throws.
 *
 * <p>Validates: Requirements 11.4</p>
 */
class AckResiliencePropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    private TracingUtilsBase createMockTracingUtils() {
        TracingUtilsBase tu = mock(TracingUtilsBase.class);
        when(tu.traceOperation(anyString(), anyMap(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
        return tu;
    }

    /**
     * **Validates: Requirements 11.4**
     */
    @SuppressWarnings("unchecked")
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 16: ACK resilience")
    void ackSucceedsEvenWhenPayloadDeleteThrows(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        Queue queue = new Queue();
        queue.setId(queueId);
        queue.setName("ack-queue");
        queue.setOwnerId(environmentId);
        queue.setPolling("IMMEDIATE");
        queue.setMaxConcurrentConnections(1);
        queue.setMaxRetries(3);
        queue.setRetryDelayMs(5000L);
        queue.setCreated(Instant.now());

        String streamKey = "stream-key";
        String dlqKey = "dlq-key";
        String groupName = "group:" + queueId;
        String deleteStreamId = "1000-0";
        String storageKey = "storage-key";

        when(qr.findById(queueId)).thenReturn(Optional.of(queue));
        when(qr.save(any(Queue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(smr.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn(streamKey);
        when(smr.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn(dlqKey);
        when(smr.groupName(any())).thenReturn(groupName);
        when(smr.acknowledgeAndDelete(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(smr.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        // readRange returns the entry with storageKey
        MapRecord<String, Object, Object> rangeRecord = mock(MapRecord.class);
        Map<Object, Object> rangeFields = new HashMap<>();
        rangeFields.put(StreamMessageRepository.FIELD_STORAGE_KEY, storageKey);
        when(rangeRecord.getValue()).thenReturn(rangeFields);
        when(smr.readRange(anyString(), anyString(), anyString()))
                .thenReturn(List.of(rangeRecord));

        // Ref count reaches zero → should trigger delete
        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
        when(rcs.decrementAndGet(anyString())).thenReturn(0L);

        // Payload delete THROWS an exception
        doThrow(new RuntimeException("S3 delete failed")).when(sp).delete(storageKey);

        // Mock next message to deliver
        UUID nextMsgId = UUID.randomUUID();
        MapRecord<String, Object, Object> nextRecord = mock(MapRecord.class);
        RecordId nextRecordId = RecordId.of("2000-0");
        when(nextRecord.getId()).thenReturn(nextRecordId);
        Map<Object, Object> nextFields = new HashMap<>();
        nextFields.put(StreamMessageRepository.FIELD_MESSAGE_ID, nextMsgId.toString());
        nextFields.put(StreamMessageRepository.FIELD_STORAGE_KEY, "next-storage-key");
        nextFields.put(StreamMessageRepository.FIELD_ENQUEUED_AT, Instant.now().toString());
        when(nextRecord.getValue()).thenReturn(nextFields);
        when(smr.readNext(anyString(), anyString(), anyString())).thenReturn(Optional.of(nextRecord));
        when(sp.retrieve("next-storage-key")).thenReturn(Optional.of("{\"data\":\"next\"}"));

        DeliveryService ds = new DeliveryService(qr, smr, rcs, sp, createMockTracingUtils(), new com.fasterxml.jackson.databind.ObjectMapper(), 30, mock(org.sifunity.broker.queueservice.metrics.QueueMetricsService.class));

        // ACK should succeed despite payload delete failure
        Optional<MessageMetadata> result = ds.deliverNextMessage(
                queueId, "consumer", deleteStreamId, "d", "s", environmentId);

        // The delivery should still succeed
        assertThat(result).isPresent();

        // XACK + XDEL should have been called
        verify(smr).acknowledgeAndDelete(streamKey, groupName, "consumer", deleteStreamId);
    }
}
