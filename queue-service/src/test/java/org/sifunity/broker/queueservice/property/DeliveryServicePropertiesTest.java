// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.TracingUtils;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.MessageStatus;
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
 * Property-based tests for DeliveryService with Redis Streams.
 *
 * <p>Note: These are legacy property tests updated for compilation with the new
 * StreamMessageRepository API. Task 14 will create comprehensive new property tests.</p>
 */
class DeliveryServicePropertiesTest {

    private TracingUtils createMockTracingUtils() {
        TracingUtils tracingUtils = mock(TracingUtils.class);
        when(tracingUtils.traceOperation(anyString(), anyMap(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
        return tracingUtils;
    }

    @Provide
    Arbitrary<UUID> queueIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<UUID> messageIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * Property: Queue not found throws exception.
     */
    @Property(tries = 50)
    @Label("deliverNextMessage throws QueueNotFoundException for missing queue")
    void deliverNextMessageThrowsForMissingQueue(@ForAll("queueIds") UUID queueId) {
        QueueRepository queueRepository = mock(QueueRepository.class);
        StreamMessageRepository streamMessageRepository = mock(StreamMessageRepository.class);
        RefCountService refCountService = mock(RefCountService.class);
        StorageProvider storageProvider = mock(StorageProvider.class);

        when(queueRepository.findById(queueId)).thenReturn(Optional.empty());

        DeliveryService service = new DeliveryService(
                queueRepository, streamMessageRepository, refCountService,
                storageProvider, createMockTracingUtils(), new com.fasterxml.jackson.databind.ObjectMapper(), 30,
                mock(org.sifunity.broker.queueservice.metrics.QueueMetricsService.class));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.sifunity.broker.common.exception.QueueNotFoundException.class,
                () -> service.deliverNextMessage(queueId, "consumer", "", "domain", "solution", UUID.randomUUID()));
    }

    /**
     * Property: Empty queue returns empty Optional.
     */
    @Property(tries = 50)
    @Label("deliverNextMessage returns empty for empty queue")
    void deliverNextMessageReturnsEmptyForEmptyQueue(@ForAll("queueIds") UUID queueId) {
        QueueRepository queueRepository = mock(QueueRepository.class);
        StreamMessageRepository streamMessageRepository = mock(StreamMessageRepository.class);
        RefCountService refCountService = mock(RefCountService.class);
        StorageProvider storageProvider = mock(StorageProvider.class);

        Queue queue = new Queue();
        queue.setId(queueId);
        queue.setName("test");
        queue.setOwnerId(UUID.randomUUID());
        queue.setMaxRetries(3);
        queue.setRetryDelayMs(5000L);

        when(queueRepository.findById(queueId)).thenReturn(Optional.of(queue));
        when(streamMessageRepository.streamKey(anyString(), anyString(), any(), any())).thenReturn("key");
        when(streamMessageRepository.dlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("key:dlq");
        when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
        when(streamMessageRepository.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(streamMessageRepository.readPending(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(streamMessageRepository.readNext(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        DeliveryService service = new DeliveryService(
                queueRepository, streamMessageRepository, refCountService,
                storageProvider, createMockTracingUtils(), new com.fasterxml.jackson.databind.ObjectMapper(), 30,
                mock(org.sifunity.broker.queueservice.metrics.QueueMetricsService.class));

        Optional<MessageMetadata> result = service.deliverNextMessage(
                queueId, "consumer", "", "domain", "solution", UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}
