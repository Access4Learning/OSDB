// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.TracingUtils;
import org.sifunity.eventsconnector.client.EnvironmentServiceClient;
import org.sifunity.eventsconnector.dto.DeliveryResultStatus;
import org.sifunity.eventsconnector.dto.DeliveryStatus;
import org.sifunity.eventsconnector.dto.EventDistributionResponse;
import org.sifunity.eventsconnector.dto.EventPublicationRequest;
import org.sifunity.eventsconnector.dto.QueueDeliveryResult;
import org.sifunity.eventsconnector.metrics.EventMetrics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventDistributionService.
 * Tests core event distribution logic with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class EventDistributionServiceTest {

    @Mock
    private SubscriptionQueryService subscriptionQueryService;

    @Mock
    private QueueEnqueueService queueEnqueueService;

    @Mock
    private EnvironmentServiceClient environmentServiceClient;

    @Mock
    private EventMetrics eventMetrics;

    @Mock
    private TracingUtils tracingUtils;

    private EventDistributionService distributionService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        distributionService = new EventDistributionService(
            subscriptionQueryService, queueEnqueueService, environmentServiceClient, eventMetrics, tracingUtils);
        
        // Configure TracingUtils mock to execute the operation directly
        when(tracingUtils.traceOperation(any(), any(), any(java.util.function.Supplier.class)))
            .thenAnswer(invocation -> {
                java.util.function.Supplier<?> operation = invocation.getArgument(2);
                return operation.get();
            });
        
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldDistributeEventSuccessfully() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal",
            "zone-1",
            "DEFAULT",
            UUID.randomUUID().toString(),
            Instant.now(),
            payload, null, null
        );

        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        UUID queueId3 = UUID.randomUUID();

        // DEFAULT context resolves to null contextUuid (wildcard)
        when(environmentServiceClient.resolveContextUuid("iowa", "production", "zone-1", "DEFAULT"))
            .thenReturn(null);

        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(UUID.randomUUID(), queueId1, null, "StudentPersonal", null, null, SubscriptionType.EVENT, Instant.now()),
            new SubscriptionDTO(UUID.randomUUID(), queueId2, null, "StudentPersonal", null, null, SubscriptionType.EVENT, Instant.now()),
            new SubscriptionDTO(UUID.randomUUID(), queueId3, null, "StudentPersonal", null, null, SubscriptionType.EVENT, Instant.now())
        );

        List<QueueDeliveryResult> deliveryResults = List.of(
            QueueDeliveryResult.success(queueId1.toString()),
            QueueDeliveryResult.success(queueId2.toString()),
            QueueDeliveryResult.success(queueId3.toString())
        );

        when(subscriptionQueryService.querySubscriptions(
            eq("iowa"), eq("production"), eq("StudentPersonal"), isNull()
        )).thenReturn(subscriptions);

        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(deliveryResults);

        // Act
        EventDistributionResponse response = distributionService.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.messageId()).isEqualTo(request.messageId());
        assertThat(response.subscriberCount()).isEqualTo(3);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(response.deliveryResults()).hasSize(3);

        verify(environmentServiceClient).resolveContextUuid("iowa", "production", "zone-1", "DEFAULT");
        verify(subscriptionQueryService).querySubscriptions("iowa", "production", "StudentPersonal", null);
        verify(queueEnqueueService).enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class));
        verify(eventMetrics).recordEventReceived("StudentPersonal", "zone-1");
        verify(eventMetrics).recordEventDistributed(DeliveryStatus.SUCCESS, 3);
    }

    @Test
    void shouldDistributeEventWithResolvedContextUuid() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal",
            "zone-1",
            "ctx-1",
            UUID.randomUUID().toString(),
            Instant.now(),
            payload, null, null
        );

        UUID contextUuid = UUID.randomUUID();
        UUID queueId1 = UUID.randomUUID();

        when(environmentServiceClient.resolveContextUuid("iowa", "production", "zone-1", "ctx-1"))
            .thenReturn(contextUuid);

        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(UUID.randomUUID(), queueId1, null, "StudentPersonal", "zone-1", "ctx-1", SubscriptionType.EVENT, Instant.now())
        );

        List<QueueDeliveryResult> deliveryResults = List.of(
            QueueDeliveryResult.success(queueId1.toString())
        );

        when(subscriptionQueryService.querySubscriptions(
            eq("iowa"), eq("production"), eq("StudentPersonal"), eq(contextUuid)
        )).thenReturn(subscriptions);

        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(deliveryResults);

        // Act
        EventDistributionResponse response = distributionService.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SUCCESS);

        verify(environmentServiceClient).resolveContextUuid("iowa", "production", "zone-1", "ctx-1");
        verify(subscriptionQueryService).querySubscriptions("iowa", "production", "StudentPersonal", contextUuid);
    }

    @Test
    void shouldHandleNoSubscriptions() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal",
            "zone-1",
            "DEFAULT",
            UUID.randomUUID().toString(),
            Instant.now(),
            payload, null, null
        );

        when(environmentServiceClient.resolveContextUuid("iowa", "production", "zone-1", "DEFAULT"))
            .thenReturn(null);

        when(subscriptionQueryService.querySubscriptions(
            eq("iowa"), eq("production"), eq("StudentPersonal"), isNull()
        )).thenReturn(List.of());

        // Act
        EventDistributionResponse response = distributionService.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.messageId()).isEqualTo(request.messageId());
        assertThat(response.subscriberCount()).isEqualTo(0);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.NO_SUBSCRIBERS);
        assertThat(response.deliveryResults()).isEmpty();

        verify(subscriptionQueryService).querySubscriptions("iowa", "production", "StudentPersonal", null);
        verify(queueEnqueueService, never()).enqueueToMultipleQueues(anyString(), anyString(), anyList(), any());
        verify(eventMetrics).recordNoSubscribers();
        verify(eventMetrics).recordEventDistributed(DeliveryStatus.NO_SUBSCRIBERS, 0);
    }

    @Test
    void shouldHandlePartialDeliveryFailure() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal",
            "zone-1",
            "DEFAULT",
            UUID.randomUUID().toString(),
            Instant.now(),
            payload, null, null
        );

        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        UUID queueId3 = UUID.randomUUID();

        when(environmentServiceClient.resolveContextUuid("iowa", "production", "zone-1", "DEFAULT"))
            .thenReturn(null);

        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(UUID.randomUUID(), queueId1, null, "StudentPersonal", null, null, SubscriptionType.EVENT, Instant.now()),
            new SubscriptionDTO(UUID.randomUUID(), queueId2, null, "StudentPersonal", null, null, SubscriptionType.EVENT, Instant.now()),
            new SubscriptionDTO(UUID.randomUUID(), queueId3, null, "StudentPersonal", null, null, SubscriptionType.EVENT, Instant.now())
        );

        List<QueueDeliveryResult> deliveryResults = List.of(
            QueueDeliveryResult.success(queueId1.toString()),
            QueueDeliveryResult.success(queueId2.toString()),
            QueueDeliveryResult.failure(queueId3.toString(), "Queue Service unavailable")
        );

        when(subscriptionQueryService.querySubscriptions(
            eq("iowa"), eq("production"), eq("StudentPersonal"), isNull()
        )).thenReturn(subscriptions);

        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(deliveryResults);

        // Act
        EventDistributionResponse response = distributionService.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.messageId()).isEqualTo(request.messageId());
        assertThat(response.subscriberCount()).isEqualTo(3);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.PARTIAL);
        assertThat(response.deliveryResults()).hasSize(3);

        long successCount = response.deliveryResults().stream()
            .filter(r -> r.status() == DeliveryResultStatus.SUCCESS)
            .count();
        assertThat(successCount).isEqualTo(2);

        verify(eventMetrics).recordEventDistributed(DeliveryStatus.PARTIAL, 3);
        verify(eventMetrics).recordPartialDelivery();
    }

    @Test
    void shouldHandleAllFailures() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal",
            "zone-1",
            "DEFAULT",
            UUID.randomUUID().toString(),
            Instant.now(),
            payload, null, null
        );

        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();

        when(environmentServiceClient.resolveContextUuid("iowa", "production", "zone-1", "DEFAULT"))
            .thenReturn(null);

        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(UUID.randomUUID(), queueId1, null, "StudentPersonal", null, null, SubscriptionType.EVENT, Instant.now()),
            new SubscriptionDTO(UUID.randomUUID(), queueId2, null, "StudentPersonal", null, null, SubscriptionType.EVENT, Instant.now())
        );

        List<QueueDeliveryResult> deliveryResults = List.of(
            QueueDeliveryResult.failure(queueId1.toString(), "Queue Service unavailable"),
            QueueDeliveryResult.failure(queueId2.toString(), "Queue Service unavailable")
        );

        when(subscriptionQueryService.querySubscriptions(
            eq("iowa"), eq("production"), eq("StudentPersonal"), isNull()
        )).thenReturn(subscriptions);

        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(deliveryResults);

        // Act
        EventDistributionResponse response = distributionService.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.messageId()).isEqualTo(request.messageId());
        assertThat(response.subscriberCount()).isEqualTo(2);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(response.deliveryResults()).hasSize(2);

        long failureCount = response.deliveryResults().stream()
            .filter(r -> r.status() == DeliveryResultStatus.FAILED)
            .count();
        assertThat(failureCount).isEqualTo(2);

        verify(eventMetrics).recordEventDistributed(DeliveryStatus.FAILED, 2);
        verify(eventMetrics).recordFailedDelivery();
    }

    @Test
    void shouldGenerateCorrelationId() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal",
            "zone-1",
            "DEFAULT",
            UUID.randomUUID().toString(),
            Instant.now(),
            payload, null, null
        );

        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(null);

        when(subscriptionQueryService.querySubscriptions(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of());

        // Act
        EventDistributionResponse response = distributionService.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response).isNotNull();
        verify(subscriptionQueryService).querySubscriptions(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldRecordMetricsForSubscriptionQuery() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal",
            "zone-1",
            "DEFAULT",
            UUID.randomUUID().toString(),
            Instant.now(),
            payload, null, null
        );

        UUID queueId = UUID.randomUUID();

        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(null);

        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(UUID.randomUUID(), queueId, null, "StudentPersonal", null, null, SubscriptionType.EVENT, Instant.now())
        );

        List<QueueDeliveryResult> deliveryResults = List.of(
            QueueDeliveryResult.success(queueId.toString())
        );

        when(subscriptionQueryService.querySubscriptions(anyString(), anyString(), anyString(), any()))
            .thenReturn(subscriptions);

        when(queueEnqueueService.enqueueToMultipleQueues(anyString(), anyString(), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(deliveryResults);

        // Act
        distributionService.distributeEvent("iowa", "production", request);

        // Assert
        verify(eventMetrics).recordSubscriptionQueryLatency(any());
    }
}
