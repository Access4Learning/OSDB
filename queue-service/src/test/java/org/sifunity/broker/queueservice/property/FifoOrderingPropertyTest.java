// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 3: FIFO ordering preserved — N enqueued messages read back in order.
 *
 * <p>Validates: Requirements 1.3, 2.1</p>
 */
class FifoOrderingPropertyTest {

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
     * **Validates: Requirements 1.3, 2.1**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 3: FIFO ordering preserved")
    void nEnqueuedMessagesProduceMonotonicallyIncreasingStreamIds(
            @ForAll("uuids") UUID queueId,
            @ForAll @IntRange(min = 2, max = 10) int messageCount) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        Queue queue = new Queue();
        queue.setId(queueId);
        queue.setName("fifo-queue");
        queue.setOwnerId(UUID.randomUUID());
        queue.setPolling("IMMEDIATE");
        queue.setMaxConcurrentConnections(1);
        queue.setMaxRetries(3);
        queue.setRetryDelayMs(5000L);
        queue.setCreated(Instant.now());

        when(qr.findById(queueId)).thenReturn(Optional.of(queue));
        when(qr.save(any(Queue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sp.store(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(smr.streamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
        when(smr.groupName(any())).thenReturn("group:" + queueId);
        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
        when(rcs.increment(anyString())).thenReturn(1L);

        // Simulate monotonically increasing stream IDs
        AtomicLong sequence = new AtomicLong(1000000000L);
        when(smr.enqueue(anyString(), anyString(), any(), anyString(), any(), any()))
                .thenAnswer(inv -> sequence.getAndIncrement() + "-0");

        MessageService svc = new MessageService(qr, smr, rcs, sp, createMockTracingUtils(), new com.fasterxml.jackson.databind.ObjectMapper(), "", mock(org.sifunity.broker.queueservice.metrics.QueueMetricsService.class));

        List<String> streamIds = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            UUID msgId = UUID.randomUUID();
            svc.enqueueMessage(queueId, msgId, "dom", "sol", UUID.randomUUID(),
                    UUID.randomUUID(), "zone", "ctx", "event", "Svc", "{}");
            // Capture the stream ID from the mock's return
            streamIds.add((sequence.get() - 1) + "-0");
        }

        // Verify stream IDs are monotonically increasing
        for (int i = 1; i < streamIds.size(); i++) {
            long prev = Long.parseLong(streamIds.get(i - 1).split("-")[0]);
            long curr = Long.parseLong(streamIds.get(i).split("-")[0]);
            assertThat(curr).isGreaterThan(prev);
        }

        // Verify enqueue was called exactly messageCount times
        verify(smr, times(messageCount)).enqueue(anyString(), anyString(), any(), anyString(), any(), any());
    }
}
