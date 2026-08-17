// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.queueservice.metrics.QueueDepthGaugeUpdater;
import org.sifunity.broker.queueservice.metrics.QueueMetricsService;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository.QueueKeyProjection;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link QueueDepthGaugeUpdater}.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4
 */
@ExtendWith(MockitoExtension.class)
class QueueDepthGaugeUpdaterTest {

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private StreamMessageRepository streamMessageRepository;

    private QueueMetricsService queueMetricsService;
    private QueueDepthGaugeUpdater updater;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        queueMetricsService = new QueueMetricsService(meterRegistry);
        updater = new QueueDepthGaugeUpdater(queueRepository, streamMessageRepository, queueMetricsService);
    }

    /**
     * Validates: Requirement 4.1 - stream key uses queue's real domain and solution (not empty strings)
     * Validates: Requirement 4.2 - queue with N messages reports depth N
     * Validates: Requirement 4.4 - non-zero depth for a queue with known contents
     */
    @Test
    void updateAllQueueDepths_usesRealDomainAndSolution_reportsCorrectDepth() {
        String domainId = "test-district";
        String solutionId = "production";
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueKeyProjection projection = mockProjection(queueId, "events-queue", ownerId, domainId, solutionId);
        when(queueRepository.findAllKeyProjections()).thenReturn(List.of(projection));

        String streamKey = "domain:" + domainId + ":solution:" + solutionId
                + ":function:msg_queue:env:" + ownerId + ":queue:" + queueId;
        String dlqKey = streamKey + ":dlq";

        when(streamMessageRepository.resolveStreamKey(domainId, solutionId, ownerId, queueId))
                .thenReturn(streamKey);
        when(streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, ownerId, queueId))
                .thenReturn(dlqKey);
        when(streamMessageRepository.messageCount(streamKey)).thenReturn(7L);
        when(streamMessageRepository.messageCount(dlqKey)).thenReturn(2L);

        updater.updateAllQueueDepths();

        // Requirement 4.1: resolveStreamKey called with real domain/solution, NOT empty strings
        verify(streamMessageRepository).resolveStreamKey(domainId, solutionId, ownerId, queueId);
        verify(streamMessageRepository, never()).resolveStreamKey(eq(""), eq(""), any(), any());

        // Requirement 4.2 & 4.4: non-zero depth reported correctly
        assertThat(meterRegistry.get("sif.queue.depth")
                .tag("queue_name", "events-queue").gauge().value()).isEqualTo(7.0);
        assertThat(meterRegistry.get("sif.queue.dlq.depth")
                .tag("queue_name", "events-queue").gauge().value()).isEqualTo(2.0);
    }

    /**
     * Validates: Requirement 4.2 - DLQ depth reported separately
     */
    @Test
    void updateAllQueueDepths_reportsDlqDepthSeparately() {
        String domainId = "district-a";
        String solutionId = "staging";
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueKeyProjection projection = mockProjection(queueId, "dlq-test-queue", ownerId, domainId, solutionId);
        when(queueRepository.findAllKeyProjections()).thenReturn(List.of(projection));

        String streamKey = "stream-key";
        String dlqKey = "stream-key:dlq";

        when(streamMessageRepository.resolveStreamKey(domainId, solutionId, ownerId, queueId))
                .thenReturn(streamKey);
        when(streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, ownerId, queueId))
                .thenReturn(dlqKey);
        when(streamMessageRepository.messageCount(streamKey)).thenReturn(0L);
        when(streamMessageRepository.messageCount(dlqKey)).thenReturn(5L);

        updater.updateAllQueueDepths();

        assertThat(meterRegistry.get("sif.queue.depth")
                .tag("queue_name", "dlq-test-queue").gauge().value()).isEqualTo(0.0);
        assertThat(meterRegistry.get("sif.queue.dlq.depth")
                .tag("queue_name", "dlq-test-queue").gauge().value()).isEqualTo(5.0);
    }

    /**
     * Validates: Requirement 4.3 - uses lightweight projection (findAllKeyProjections) instead of findAll
     */
    @Test
    void updateAllQueueDepths_usesProjectionNotFindAll() {
        when(queueRepository.findAllKeyProjections()).thenReturn(List.of());

        updater.updateAllQueueDepths();

        verify(queueRepository).findAllKeyProjections();
        verify(queueRepository, never()).findAll();
    }

    @Test
    void updateAllQueueDepths_handlesEmptyQueueList() {
        when(queueRepository.findAllKeyProjections()).thenReturn(List.of());

        updater.updateAllQueueDepths();

        verify(streamMessageRepository, never()).messageCount(anyString());
    }

    @Test
    void updateAllQueueDepths_continuesOnIndividualQueueFailure() {
        String domainId = "district-b";
        String solutionId = "prod";
        UUID ownerId1 = UUID.randomUUID();
        UUID queueId1 = UUID.randomUUID();
        UUID ownerId2 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();

        QueueKeyProjection failing = mockProjection(queueId1, "failing-queue", ownerId1, domainId, solutionId);
        QueueKeyProjection good = mockProjection(queueId2, "good-queue", ownerId2, domainId, solutionId);
        when(queueRepository.findAllKeyProjections()).thenReturn(List.of(failing, good));

        // First queue fails
        when(streamMessageRepository.resolveStreamKey(domainId, solutionId, ownerId1, queueId1))
                .thenThrow(new RuntimeException("Redis connection failed"));

        // Second queue succeeds
        String streamKey2 = "stream2";
        String dlqKey2 = "stream2:dlq";
        when(streamMessageRepository.resolveStreamKey(domainId, solutionId, ownerId2, queueId2)).thenReturn(streamKey2);
        when(streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, ownerId2, queueId2)).thenReturn(dlqKey2);
        when(streamMessageRepository.messageCount(streamKey2)).thenReturn(5L);
        when(streamMessageRepository.messageCount(dlqKey2)).thenReturn(0L);

        updater.updateAllQueueDepths();

        // Second queue gauge should still be updated
        assertThat(meterRegistry.get("sif.queue.depth")
                .tag("queue_name", "good-queue").gauge().value()).isEqualTo(5.0);
    }

    /**
     * Validates: Requirement 4.1 - legacy queues (null domainId/solutionId) fall back to empty strings
     */
    @Test
    void updateAllQueueDepths_fallsBackToEmptyStringsWhenFieldsNull() {
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueKeyProjection projection = mockProjection(queueId, "legacy-queue", ownerId, null, null);
        when(queueRepository.findAllKeyProjections()).thenReturn(List.of(projection));

        String streamKey = "legacy-stream";
        String dlqKey = "legacy-stream:dlq";

        when(streamMessageRepository.resolveStreamKey("", "", ownerId, queueId)).thenReturn(streamKey);
        when(streamMessageRepository.resolveDlqStreamKey("", "", ownerId, queueId)).thenReturn(dlqKey);
        when(streamMessageRepository.messageCount(streamKey)).thenReturn(3L);
        when(streamMessageRepository.messageCount(dlqKey)).thenReturn(0L);

        updater.updateAllQueueDepths();

        verify(streamMessageRepository).resolveStreamKey("", "", ownerId, queueId);
        assertThat(meterRegistry.get("sif.queue.depth")
                .tag("queue_name", "legacy-queue").gauge().value()).isEqualTo(3.0);
    }

    @Test
    void updateAllQueueDepths_gaugesUpdateOnSubsequentCalls() {
        String domainId = "district-c";
        String solutionId = "test";
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueKeyProjection projection = mockProjection(queueId, "changing-queue", ownerId, domainId, solutionId);
        when(queueRepository.findAllKeyProjections()).thenReturn(List.of(projection));

        String streamKey = "stream";
        String dlqKey = "stream:dlq";
        when(streamMessageRepository.resolveStreamKey(domainId, solutionId, ownerId, queueId)).thenReturn(streamKey);
        when(streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, ownerId, queueId)).thenReturn(dlqKey);

        // First update: depth=10, dlq=0
        when(streamMessageRepository.messageCount(streamKey)).thenReturn(10L);
        when(streamMessageRepository.messageCount(dlqKey)).thenReturn(0L);
        updater.updateAllQueueDepths();

        assertThat(meterRegistry.get("sif.queue.depth")
                .tag("queue_name", "changing-queue").gauge().value()).isEqualTo(10.0);

        // Second update: depth=5, dlq=2
        when(streamMessageRepository.messageCount(streamKey)).thenReturn(5L);
        when(streamMessageRepository.messageCount(dlqKey)).thenReturn(2L);
        updater.updateAllQueueDepths();

        assertThat(meterRegistry.get("sif.queue.depth")
                .tag("queue_name", "changing-queue").gauge().value()).isEqualTo(5.0);
        assertThat(meterRegistry.get("sif.queue.dlq.depth")
                .tag("queue_name", "changing-queue").gauge().value()).isEqualTo(2.0);
    }

    private QueueKeyProjection mockProjection(UUID id, String name, UUID ownerId,
                                               String domainId, String solutionId) {
        QueueKeyProjection projection = mock(QueueKeyProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getName()).thenReturn(name);
        when(projection.getOwnerId()).thenReturn(ownerId);
        when(projection.getDomainId()).thenReturn(domainId);
        when(projection.getSolutionId()).thenReturn(solutionId);
        return projection;
    }
}
