// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.MessageService;
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
 * Property 13: Timestamp updates — lastModified updated on enqueue, lastAccessed updated on delivery.
 *
 * <p>Validates: Requirements 6.5, 6.6</p>
 */
class TimestampUpdatePropertyTest {

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
     * **Validates: Requirements 6.6**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 13: Timestamp updates — lastModified on enqueue")
    void lastModifiedUpdatedOnEnqueue(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        Queue queue = new Queue();
        queue.setId(queueId);
        queue.setName("ts-queue");
        queue.setOwnerId(environmentId);
        queue.setPolling("IMMEDIATE");
        queue.setMaxConcurrentConnections(1);
        queue.setMaxRetries(3);
        queue.setRetryDelayMs(5000L);
        queue.setCreated(Instant.now());

        Instant beforeEnqueue = Instant.now();

        when(qr.findById(queueId)).thenReturn(Optional.of(queue));
        when(qr.save(any(Queue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sp.store(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(smr.streamKey(anyString(), anyString(), any(), any())).thenReturn("key");
        when(smr.groupName(any())).thenReturn("group:" + queueId);
        when(smr.enqueue(anyString(), anyString(), any(), anyString(), any(), any())).thenReturn("1-0");
        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
        when(rcs.increment(anyString())).thenReturn(1L);

        MessageService svc = new MessageService(qr, smr, rcs, sp, createMockTracingUtils(), new com.fasterxml.jackson.databind.ObjectMapper(), "", mock(org.sifunity.broker.queueservice.metrics.QueueMetricsService.class));
        svc.enqueueMessage(queueId, UUID.randomUUID(), "d", "s", environmentId,
                environmentId, "z", "c", "event", "Svc", "{}");

        // lastModified should be updated
        assertThat(queue.getLastModified()).isNotNull();
        assertThat(queue.getLastModified()).isAfterOrEqualTo(beforeEnqueue);
    }

    /**
     * **Validates: Requirements 6.5**
     */
    @SuppressWarnings("unchecked")
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 13b: Timestamp updates — lastAccessed on delivery")
    void lastAccessedUpdatedOnDelivery(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        Queue queue = new Queue();
        queue.setId(queueId);
        queue.setName("ts-queue");
        queue.setOwnerId(environmentId);
        queue.setPolling("IMMEDIATE");
        queue.setMaxConcurrentConnections(1);
        queue.setMaxRetries(3);
        queue.setRetryDelayMs(5000L);
        queue.setCreated(Instant.now());

        Instant beforeDelivery = Instant.now();

        when(qr.findById(queueId)).thenReturn(Optional.of(queue));
        when(qr.save(any(Queue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(smr.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
        when(smr.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("dlq-key");
        when(smr.groupName(any())).thenReturn("group:" + queueId);
        when(smr.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        // Mock a message to deliver
        UUID msgId = UUID.randomUUID();
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        RecordId recordId = RecordId.of("1000-0");
        when(record.getId()).thenReturn(recordId);
        Map<Object, Object> fields = new HashMap<>();
        fields.put(StreamMessageRepository.FIELD_MESSAGE_ID, msgId.toString());
        fields.put(StreamMessageRepository.FIELD_STORAGE_KEY, "storage-key");
        fields.put(StreamMessageRepository.FIELD_ENQUEUED_AT, Instant.now().toString());
        when(record.getValue()).thenReturn(fields);
        when(smr.readNext(anyString(), anyString(), anyString())).thenReturn(Optional.of(record));
        when(sp.retrieve("storage-key")).thenReturn(Optional.of("{\"data\":\"test\"}"));

        TracingUtilsBase tu = createMockTracingUtils();
        DeliveryService ds = new DeliveryService(qr, smr, rcs, sp, tu, new com.fasterxml.jackson.databind.ObjectMapper(), 30, mock(org.sifunity.broker.queueservice.metrics.QueueMetricsService.class));

        Optional<MessageMetadata> result = ds.deliverNextMessage(
                queueId, "consumer", "", "d", "s", environmentId);

        assertThat(result).isPresent();
        assertThat(queue.getLastAccessed()).isNotNull();
        assertThat(queue.getLastAccessed()).isAfterOrEqualTo(beforeDelivery);
    }
}
