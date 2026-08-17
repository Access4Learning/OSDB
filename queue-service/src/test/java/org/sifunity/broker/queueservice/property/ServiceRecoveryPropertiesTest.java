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
 * Property-based tests for service restart recovery with Redis Streams.
 *
 * <p>With Redis Streams, recovery is inherent: unacknowledged messages remain in the PEL
 * and are available via XREADGROUP or XCLAIM after restart. These tests verify the
 * basic contract still holds.</p>
 */
class ServiceRecoveryPropertiesTest {

    private TracingUtils createMockTracingUtils() {
        TracingUtils tu = mock(TracingUtils.class);
        when(tu.traceOperation(anyString(), anyMap(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
        return tu;
    }

    @Provide
    Arbitrary<UUID> queueIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * Property: After restart, empty queue still returns empty.
     */
    @Property(tries = 50)
    @Label("After restart, empty queue returns empty")
    void afterRestartEmptyQueueReturnsEmpty(@ForAll("queueIds") UUID queueId) {
        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        Queue queue = new Queue();
        queue.setId(queueId);
        queue.setName("test");
        queue.setOwnerId(UUID.randomUUID());
        queue.setMaxRetries(3);
        queue.setRetryDelayMs(5000L);

        when(qr.findById(queueId)).thenReturn(Optional.of(queue));
        when(smr.streamKey(anyString(), anyString(), any(), any())).thenReturn("key");
        when(smr.dlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("key:dlq");
        when(smr.groupName(any())).thenReturn("group:" + queueId);
        when(smr.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(smr.readNext(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Simulate restart by creating a new service instance
        DeliveryService svc = new DeliveryService(qr, smr, rcs, sp, createMockTracingUtils(), new com.fasterxml.jackson.databind.ObjectMapper(), 30, mock(org.sifunity.broker.queueservice.metrics.QueueMetricsService.class));

        var result = svc.deliverNextMessage(queueId, "consumer", "", "d", "s", UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}
