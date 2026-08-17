// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.common.util.TracingUtils;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.MessageStatus;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for MessageService with stream-based enqueue.
 */
class MessageServicePropertiesTest {

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

    @Property(tries = 50)
    @Label("Enqueue to non-existent queue fails")
    void enqueueToNonExistentQueueFails(@ForAll("queueIds") UUID queueId) {
        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);
        when(qr.findById(queueId)).thenReturn(Optional.empty());

        MessageService svc = new MessageService(qr, smr, rcs, sp, createMockTracingUtils(), new com.fasterxml.jackson.databind.ObjectMapper(), "", mock(org.sifunity.broker.queueservice.metrics.QueueMetricsService.class));

        assertThatThrownBy(() -> svc.enqueueMessage(
                queueId, null, "d", "s", UUID.randomUUID(),
                UUID.randomUUID(), "z", "c", "event", "Svc", "{}"))
                .isInstanceOf(QueueNotFoundException.class);
    }
}
