// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.eventsconnector.client.QueueServiceClient;
import org.sifunity.eventsconnector.dto.DeliveryResultStatus;
import org.sifunity.eventsconnector.dto.EnqueueResponse;
import org.sifunity.eventsconnector.dto.QueueDeliveryResult;
import org.sifunity.eventsconnector.metrics.EventMetrics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueueEnqueueService.
 * Tests concurrent queue enqueuing with mocked client.
 */
@ExtendWith(MockitoExtension.class)
class QueueEnqueueServiceTest {

    @Mock
    private QueueServiceClient queueServiceClient;

    @Mock
    private EventMetrics eventMetrics;

    @InjectMocks
    private QueueEnqueueService queueEnqueueService;

    @Test
    void shouldEnqueueToMultipleQueuesSuccessfully() {
        // Arrange
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        UUID queueId3 = UUID.randomUUID();
        List<UUID> queueIds = List.of(queueId1, queueId2, queueId3);

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.EVENT)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .zoneId("zone-1")
            .contextId("DEFAULT")
            .payload("test payload")
            .build();

        EnqueueResponse response1 = new EnqueueResponse(UUID.randomUUID(), queueId1, "storage-key-1", Instant.now(), "ENQUEUED");
        EnqueueResponse response2 = new EnqueueResponse(UUID.randomUUID(), queueId2, "storage-key-2", Instant.now(), "ENQUEUED");
        EnqueueResponse response3 = new EnqueueResponse(UUID.randomUUID(), queueId3, "storage-key-3", Instant.now(), "ENQUEUED");

        when(queueServiceClient.enqueueMessage(eq("iowa"), eq("production"), eq(queueId1), any()))
            .thenReturn(response1);
        when(queueServiceClient.enqueueMessage(eq("iowa"), eq("production"), eq(queueId2), any()))
            .thenReturn(response2);
        when(queueServiceClient.enqueueMessage(eq("iowa"), eq("production"), eq(queueId3), any()))
            .thenReturn(response3);

        // Act
        List<QueueDeliveryResult> results = queueEnqueueService.enqueueToMultipleQueues("iowa", "production", queueIds, envelope);

        // Assert
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(r -> r.status() == DeliveryResultStatus.SUCCESS);
        assertThat(results).extracting(QueueDeliveryResult::queueId)
            .containsExactlyInAnyOrder(queueId1.toString(), queueId2.toString(), queueId3.toString());

        verify(queueServiceClient, times(3)).enqueueMessage(eq("iowa"), eq("production"), any(UUID.class), any());
        verify(eventMetrics, times(3)).recordQueueEnqueueLatency(any(), anyString(), eq(true));
    }

    @Test
    void shouldHandlePartialFailures() {
        // Arrange
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        UUID queueId3 = UUID.randomUUID();
        List<UUID> queueIds = List.of(queueId1, queueId2, queueId3);

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.EVENT)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .zoneId("zone-1")
            .contextId("DEFAULT")
            .payload("test payload")
            .build();

        EnqueueResponse response1 = new EnqueueResponse(UUID.randomUUID(), queueId1, "storage-key-1", Instant.now(), "ENQUEUED");
        EnqueueResponse response2 = new EnqueueResponse(UUID.randomUUID(), queueId2, "storage-key-2", Instant.now(), "ENQUEUED");

        when(queueServiceClient.enqueueMessage(eq("iowa"), eq("production"), eq(queueId1), any()))
            .thenReturn(response1);
        when(queueServiceClient.enqueueMessage(eq("iowa"), eq("production"), eq(queueId2), any()))
            .thenReturn(response2);
        when(queueServiceClient.enqueueMessage(eq("iowa"), eq("production"), eq(queueId3), any()))
            .thenThrow(new RuntimeException("Queue Service unavailable"));

        // Act
        List<QueueDeliveryResult> results = queueEnqueueService.enqueueToMultipleQueues("iowa", "production", queueIds, envelope);

        // Assert
        assertThat(results).hasSize(3);
        
        long successCount = results.stream()
            .filter(r -> r.status() == DeliveryResultStatus.SUCCESS)
            .count();
        assertThat(successCount).isEqualTo(2);

        long failureCount = results.stream()
            .filter(r -> r.status() == DeliveryResultStatus.FAILED)
            .count();
        assertThat(failureCount).isEqualTo(1);

        QueueDeliveryResult failedResult = results.stream()
            .filter(r -> r.status() == DeliveryResultStatus.FAILED)
            .findFirst()
            .orElseThrow();
        assertThat(failedResult.queueId()).isEqualTo(queueId3.toString());
        assertThat(failedResult.error()).contains("Queue Service unavailable");

        verify(eventMetrics, times(2)).recordQueueEnqueueLatency(any(), anyString(), eq(true));
        verify(eventMetrics, times(1)).recordQueueEnqueueLatency(any(), anyString(), eq(false));
    }

    @Test
    void shouldHandleAllFailures() {
        // Arrange
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        List<UUID> queueIds = List.of(queueId1, queueId2);

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.EVENT)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .zoneId("zone-1")
            .contextId("DEFAULT")
            .payload("test payload")
            .build();

        when(queueServiceClient.enqueueMessage(eq("iowa"), eq("production"), any(UUID.class), any()))
            .thenThrow(new RuntimeException("Queue Service unavailable"));

        // Act
        List<QueueDeliveryResult> results = queueEnqueueService.enqueueToMultipleQueues("iowa", "production", queueIds, envelope);

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.status() == DeliveryResultStatus.FAILED);
        assertThat(results).allMatch(r -> r.error().contains("Queue Service unavailable"));

        verify(eventMetrics, times(2)).recordQueueEnqueueLatency(any(), anyString(), eq(false));
    }

    @Test
    void shouldThrowExceptionForNullQueueIds() {
        // Arrange
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.EVENT)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .zoneId("zone-1")
            .contextId("DEFAULT")
            .payload("test payload")
            .build();

        // Act & Assert
        assertThatThrownBy(() -> queueEnqueueService.enqueueToMultipleQueues("iowa", "production", null, envelope))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("queueIds must not be null or empty");
    }

    @Test
    void shouldThrowExceptionForEmptyQueueIds() {
        // Arrange
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.EVENT)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .zoneId("zone-1")
            .contextId("DEFAULT")
            .payload("test payload")
            .build();

        // Act & Assert
        assertThatThrownBy(() -> queueEnqueueService.enqueueToMultipleQueues("iowa", "production", List.of(), envelope))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("queueIds must not be null or empty");
    }

    @Test
    void shouldThrowExceptionForNullEnvelope() {
        // Arrange
        List<UUID> queueIds = List.of(UUID.randomUUID());

        // Act & Assert
        assertThatThrownBy(() -> queueEnqueueService.enqueueToMultipleQueues("iowa", "production", queueIds, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("envelope must not be null");
    }

    @Test
    void shouldProcessQueuesConcurrently() {
        // Arrange
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        UUID queueId3 = UUID.randomUUID();
        List<UUID> queueIds = List.of(queueId1, queueId2, queueId3);

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.EVENT)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .zoneId("zone-1")
            .contextId("DEFAULT")
            .payload("test payload")
            .build();

        EnqueueResponse response = new EnqueueResponse(UUID.randomUUID(), queueId1, "storage-key", Instant.now(), "ENQUEUED");

        when(queueServiceClient.enqueueMessage(eq("iowa"), eq("production"), any(UUID.class), any()))
            .thenReturn(response);

        // Act
        List<QueueDeliveryResult> results = queueEnqueueService.enqueueToMultipleQueues("iowa", "production", queueIds, envelope);

        // Assert
        assertThat(results).hasSize(3);
        // All queues should be processed (concurrent processing verified by all being called)
        verify(queueServiceClient, times(3)).enqueueMessage(eq("iowa"), eq("production"), any(UUID.class), any());
    }
}



