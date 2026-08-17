// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.TracingUtils;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for retry delay enforcement with Redis Streams PEL.
 *
 * <p>Note: These are legacy property tests updated for compilation with the new
 * StreamMessageRepository API. Task 14 will create comprehensive new property tests
 * covering PEL-based redelivery (Property 7).</p>
 */
class RetryDelayPropertiesTest {

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

    /**
     * Property: Empty queue returns empty result regardless of retry delay.
     */
    @Property(tries = 50)
    @Label("Empty queue returns empty regardless of retry delay")
    void emptyQueueReturnsEmpty(@ForAll("queueIds") UUID queueId) {
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

        var result = service.deliverNextMessage(
                queueId, "consumer", "", "domain", "solution", UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}
