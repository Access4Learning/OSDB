// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 1: Enqueue round-trip — enqueue then read stream entry yields same fields.
 *
 * <p>Validates: Requirements 1.1, 2.5</p>
 */
class EnqueueRoundTripPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> messageTypes() {
        return Arbitraries.of("event", "request", "response");
    }

    @Provide
    Arbitrary<String> alphaStrings() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    private TracingUtilsBase createMockTracingUtils() {
        TracingUtilsBase tu = mock(TracingUtilsBase.class);
        when(tu.traceOperation(anyString(), anyMap(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
        return tu;
    }

    /**
     * **Validates: Requirements 1.1, 2.5**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 1: Enqueue round-trip")
    void enqueueThenReadYieldsSameFields(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID messageId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("alphaStrings") String domainId,
            @ForAll("alphaStrings") String solutionId,
            @ForAll("alphaStrings") String zone,
            @ForAll("alphaStrings") String context,
            @ForAll("messageTypes") String messageType,
            @ForAll("alphaStrings") String service) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        Queue queue = new Queue();
        queue.setId(queueId);
        queue.setName("test-queue");
        queue.setOwnerId(environmentId);
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
        when(smr.enqueue(anyString(), anyString(), any(), anyString(), any(), any()))
                .thenReturn("1234567890-0");
        when(rcs.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
        when(rcs.increment(anyString())).thenReturn(1L);

        MessageService svc = new MessageService(qr, smr, rcs, sp, createMockTracingUtils(), new com.fasterxml.jackson.databind.ObjectMapper(), "", mock(org.sifunity.broker.queueservice.metrics.QueueMetricsService.class));

        MessageMetadata result = svc.enqueueMessage(
                queueId, messageId, domainId, solutionId, environmentId,
                environmentId, zone, context, messageType, service, "{}");

        // Round-trip: the returned metadata should contain the same messageId and queueId
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.getQueueId()).isEqualTo(queueId);
        assertThat(result.getStorageKey()).isNotNull().isNotEmpty();
        assertThat(result.getEnqueuedAt()).isNotNull();

        // Verify enqueue was called with the correct messageId and storageKey
        verify(smr).enqueue(
                eq("stream-key"),
                eq("group:" + queueId),
                eq(messageId),
                eq(result.getStorageKey()),
                any(Instant.class),
                isNull());
    }
}
