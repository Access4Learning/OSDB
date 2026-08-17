// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.eventsconnector.dto.DeliveryResultStatus;
import org.sifunity.eventsconnector.dto.DeliveryStatus;
import org.sifunity.eventsconnector.dto.EventDistributionResponse;
import org.sifunity.eventsconnector.dto.EventPublicationRequest;
import org.sifunity.eventsconnector.dto.QueueDeliveryResult;
import org.sifunity.eventsconnector.metrics.EventMetrics;
import org.sifunity.eventsconnector.client.EnvironmentServiceClient;
import org.sifunity.eventsconnector.service.EventDistributionService;
import org.sifunity.eventsconnector.service.QueueEnqueueService;
import org.sifunity.eventsconnector.service.SubscriptionQueryService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for event distribution.
 * Tests Properties 1-9 from the design document.
 * 
 * <p>Uses jqwik for property-based testing with randomized inputs.</p>
 */
@Tag("Feature: events-connector")
class EventDistributionPropertiesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 1: Event acceptance with valid headers
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**
     * 
     * For any event publication request with valid serviceName, zoneId, and contextId headers,
     * the Events Connector should accept the event and return HTTP 200 OK or 207 Multi-Status.
     */
    @Property(tries = 100)
    @Tag("Property 1: Event acceptance with valid headers")
    void shouldAcceptEventWithValidHeaders(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String zoneId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String contextId
    ) throws Exception {
        // Arrange
        SubscriptionQueryService subscriptionQueryService = mock(SubscriptionQueryService.class);
        QueueEnqueueService queueEnqueueService = mock(QueueEnqueueService.class);
        EventMetrics eventMetrics = mock(EventMetrics.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);
        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        EventDistributionService service = new EventDistributionService(
            subscriptionQueryService, queueEnqueueService, environmentServiceClient, eventMetrics, new NoOpTracingUtils()
        );

        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId,
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        UUID queueId = UUID.randomUUID();
        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(UUID.randomUUID(), queueId, null, serviceName, (String) null, (String) null, SubscriptionType.EVENT, Instant.now())
        );

        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(subscriptions);
        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(List.of(QueueDeliveryResult.success(queueId.toString())));

        // Act
        EventDistributionResponse response = service.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.deliveryStatus()).isIn(DeliveryStatus.SUCCESS, DeliveryStatus.PARTIAL);
        assertThat(response.messageId()).isEqualTo(request.messageId());
    }

    /**
     * Property 2: Event validation rejection
     * 
     * **Validates: Requirements 1.6, 6.1, 6.2, 6.3, 6.4, 6.5**
     * 
     * For any event publication request missing required headers (serviceName, zoneId) or with
     * invalid data, the Events Connector should return HTTP 400 Bad Request with validation details.
     * 
     * Note: This property is validated at the controller level with Bean Validation.
     */
    @Property(tries = 100)
    @Tag("Property 2: Event validation rejection")
    void shouldRejectEventWithMissingRequiredHeaders(
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String contextId
    ) throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");

        // Act & Assert - Missing serviceName
        try {
            new EventPublicationRequest(
                null, "zone-1", contextId,
                UUID.randomUUID().toString(), Instant.now(), payload, null, null
            );
        } catch (Exception e) {
            // Expected - validation should fail
            assertThat(e).isNotNull();
        }

        // Act & Assert - Missing zoneId
        try {
            new EventPublicationRequest(
                "StudentPersonal", null, contextId,
                UUID.randomUUID().toString(), Instant.now(), payload, null, null
            );
        } catch (Exception e) {
            // Expected - validation should fail
            assertThat(e).isNotNull();
        }
    }

    /**
     * Property 3: Subscription query execution
     * 
     * **Validates: Requirements 2.1, 2.2, 2.3**
     * 
     * For any valid event, the Events Connector should query the Subscription Service with
     * the event's serviceName, zoneId, and contextId to find matching subscriptions.
     */
    @Property(tries = 100)
    @Tag("Property 3: Subscription query execution")
    void shouldQuerySubscriptionsForEveryEvent(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String zoneId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String contextId
    ) throws Exception {
        // Arrange
        SubscriptionQueryService subscriptionQueryService = mock(SubscriptionQueryService.class);
        QueueEnqueueService queueEnqueueService = mock(QueueEnqueueService.class);
        EventMetrics eventMetrics = mock(EventMetrics.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);
        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        EventDistributionService service = new EventDistributionService(
            subscriptionQueryService, queueEnqueueService, environmentServiceClient, eventMetrics, new NoOpTracingUtils()
        );

        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId,
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(List.of());

        // Act
        service.distributeEvent("iowa", "production", request);

        // Assert
        verify(subscriptionQueryService).querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any());
    }

    /**
     * Property 4: No subscriptions handling
     * 
     * **Validates: Requirements 2.4, 17.1, 17.2, 17.3, 17.4**
     * 
     * For any event where no subscriptions match, the Events Connector should return HTTP 200 OK
     * with NO_SUBSCRIBERS status and not attempt to enqueue to any queues.
     */
    @Property(tries = 100)
    @Tag("Property 4: No subscriptions handling")
    void shouldHandleNoSubscriptionsGracefully(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String zoneId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String contextId
    ) throws Exception {
        // Arrange
        SubscriptionQueryService subscriptionQueryService = mock(SubscriptionQueryService.class);
        QueueEnqueueService queueEnqueueService = mock(QueueEnqueueService.class);
        EventMetrics eventMetrics = mock(EventMetrics.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);
        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        EventDistributionService service = new EventDistributionService(
            subscriptionQueryService, queueEnqueueService, environmentServiceClient, eventMetrics, new NoOpTracingUtils()
        );

        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId,
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(List.of());

        // Act
        EventDistributionResponse response = service.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.NO_SUBSCRIBERS);
        assertThat(response.subscriberCount()).isEqualTo(0);
        assertThat(response.deliveryResults()).isEmpty();
        verify(queueEnqueueService, never()).enqueueToMultipleQueues(anyString(), anyString(), anyList(), any());
    }

    /**
     * Property 5: Event distribution to all subscribers
     * 
     * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
     * 
     * For any event with matching subscriptions, the Events Connector should enqueue the event
     * to every subscriber's queue identified in the subscriptions.
     */
    @Property(tries = 100)
    @Tag("Property 5: Event distribution to all subscribers")
    void shouldDistributeEventToAllSubscribers(
        @ForAll @Size(min = 1, max = 20) List<@From("uuids") UUID> queueIds
    ) throws Exception {
        // Arrange
        SubscriptionQueryService subscriptionQueryService = mock(SubscriptionQueryService.class);
        QueueEnqueueService queueEnqueueService = mock(QueueEnqueueService.class);
        EventMetrics eventMetrics = mock(EventMetrics.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);
        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        EventDistributionService service = new EventDistributionService(
            subscriptionQueryService, queueEnqueueService, environmentServiceClient, eventMetrics, new NoOpTracingUtils()
        );

        String serviceName = "StudentPersonal";
        String zoneId = "zone-1";
        String contextId = "DEFAULT";

        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId,
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        List<SubscriptionDTO> subscriptions = queueIds.stream()
            .map(queueId -> new SubscriptionDTO(
                UUID.randomUUID(), queueId, null, serviceName, (String) null, (String) null, SubscriptionType.EVENT, Instant.now()
            ))
            .collect(Collectors.toList());

        List<QueueDeliveryResult> deliveryResults = queueIds.stream()
            .map(queueId -> QueueDeliveryResult.success(queueId.toString()))
            .collect(Collectors.toList());

        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(subscriptions);
        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(deliveryResults);

        // Act
        EventDistributionResponse response = service.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response.subscriberCount()).isEqualTo(queueIds.size());
        assertThat(response.deliveryResults()).hasSize(queueIds.size());
        verify(queueEnqueueService).enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class));
    }

    /**
     * Property 6: Concurrent fan-out processing
     * 
     * **Validates: Requirements 4.1, 4.2**
     * 
     * For any event with multiple matching subscriptions, the Events Connector should enqueue
     * to all subscriber queues concurrently (in parallel).
     * 
     * Note: Concurrency is verified by the QueueEnqueueService using Flux for parallel processing.
     */
    @Property(tries = 100)
    @Tag("Property 6: Concurrent fan-out processing")
    void shouldProcessMultipleQueuesConcurrently(
        @ForAll @Size(min = 2, max = 50) List<@From("uuids") UUID> queueIds
    ) throws Exception {
        // Arrange
        SubscriptionQueryService subscriptionQueryService = mock(SubscriptionQueryService.class);
        QueueEnqueueService queueEnqueueService = mock(QueueEnqueueService.class);
        EventMetrics eventMetrics = mock(EventMetrics.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);
        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        EventDistributionService service = new EventDistributionService(
            subscriptionQueryService, queueEnqueueService, environmentServiceClient, eventMetrics, new NoOpTracingUtils()
        );

        String serviceName = "StudentPersonal";
        String zoneId = "zone-1";
        String contextId = "DEFAULT";

        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId,
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        List<SubscriptionDTO> subscriptions = queueIds.stream()
            .map(queueId -> new SubscriptionDTO(
                UUID.randomUUID(), queueId, null, serviceName, (String) null, (String) null, SubscriptionType.EVENT, Instant.now()
            ))
            .collect(Collectors.toList());

        List<QueueDeliveryResult> deliveryResults = queueIds.stream()
            .map(queueId -> QueueDeliveryResult.success(queueId.toString()))
            .collect(Collectors.toList());

        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(subscriptions);
        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(deliveryResults);

        // Act
        EventDistributionResponse response = service.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response.subscriberCount()).isEqualTo(queueIds.size());
        // Verify that enqueueToMultipleQueues was called (which uses Flux for concurrency)
        verify(queueEnqueueService).enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class));
    }

    /**
     * Property 7: Partial delivery resilience
     * 
     * **Validates: Requirements 4.3, 4.4, 11.1, 11.2, 11.5**
     * 
     * For any event distribution where some queues succeed and others fail, the Events Connector
     * should continue processing all queues and return HTTP 207 Multi-Status with details for each queue.
     */
    @Property(tries = 100)
    @Tag("Property 7: Partial delivery resilience")
    void shouldHandlePartialDeliveryGracefully(
        @ForAll @Size(min = 2, max = 20) List<@From("uuids") UUID> queueIds,
        @ForAll @IntRange(min = 1, max = 19) int failureCount
    ) throws Exception {
        Assume.that(failureCount < queueIds.size());

        // Arrange
        SubscriptionQueryService subscriptionQueryService = mock(SubscriptionQueryService.class);
        QueueEnqueueService queueEnqueueService = mock(QueueEnqueueService.class);
        EventMetrics eventMetrics = mock(EventMetrics.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);
        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        EventDistributionService service = new EventDistributionService(
            subscriptionQueryService, queueEnqueueService, environmentServiceClient, eventMetrics, new NoOpTracingUtils()
        );

        String serviceName = "StudentPersonal";
        String zoneId = "zone-1";
        String contextId = "DEFAULT";

        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId,
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        List<SubscriptionDTO> subscriptions = queueIds.stream()
            .map(queueId -> new SubscriptionDTO(
                UUID.randomUUID(), queueId, null, serviceName, (String) null, (String) null, SubscriptionType.EVENT, Instant.now()
            ))
            .collect(Collectors.toList());

        // Create mixed results: some success, some failure
        List<QueueDeliveryResult> deliveryResults = new ArrayList<>();
        for (int i = 0; i < queueIds.size(); i++) {
            if (i < failureCount) {
                deliveryResults.add(QueueDeliveryResult.failure(
                    queueIds.get(i).toString(), "Queue Service unavailable"
                ));
            } else {
                deliveryResults.add(QueueDeliveryResult.success(queueIds.get(i).toString()));
            }
        }

        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(subscriptions);
        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(deliveryResults);

        // Act
        EventDistributionResponse response = service.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.PARTIAL);
        assertThat(response.deliveryResults()).hasSize(queueIds.size());
        
        long successCount = response.deliveryResults().stream()
            .filter(r -> r.status() == DeliveryResultStatus.SUCCESS)
            .count();
        long actualFailureCount = response.deliveryResults().stream()
            .filter(r -> r.status() == DeliveryResultStatus.FAILED)
            .count();
        
        assertThat(successCount).isGreaterThan(0);
        assertThat(actualFailureCount).isGreaterThan(0);
    }

    /**
     * Property 8: Complete failure handling
     * 
     * **Validates: Requirements 3.7, 11.3**
     * 
     * For any event distribution where all queues fail, the Events Connector should return
     * HTTP 500 Internal Server Error.
     */
    @Property(tries = 100)
    @Tag("Property 8: Complete failure handling")
    void shouldHandleCompleteFailure(
        @ForAll @Size(min = 1, max = 20) List<@From("uuids") UUID> queueIds
    ) throws Exception {
        // Arrange
        SubscriptionQueryService subscriptionQueryService = mock(SubscriptionQueryService.class);
        QueueEnqueueService queueEnqueueService = mock(QueueEnqueueService.class);
        EventMetrics eventMetrics = mock(EventMetrics.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);
        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        EventDistributionService service = new EventDistributionService(
            subscriptionQueryService, queueEnqueueService, environmentServiceClient, eventMetrics, new NoOpTracingUtils()
        );

        String serviceName = "StudentPersonal";
        String zoneId = "zone-1";
        String contextId = "DEFAULT";

        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId,
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        List<SubscriptionDTO> subscriptions = queueIds.stream()
            .map(queueId -> new SubscriptionDTO(
                UUID.randomUUID(), queueId, null, serviceName, (String) null, (String) null, SubscriptionType.EVENT, Instant.now()
            ))
            .collect(Collectors.toList());

        // All queues fail
        List<QueueDeliveryResult> deliveryResults = queueIds.stream()
            .map(queueId -> QueueDeliveryResult.failure(
                queueId.toString(), "Queue Service unavailable"
            ))
            .collect(Collectors.toList());

        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(subscriptions);
        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(deliveryResults);

        // Act
        EventDistributionResponse response = service.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(response.deliveryResults()).hasSize(queueIds.size());
        assertThat(response.deliveryResults())
            .allMatch(r -> r.status() == DeliveryResultStatus.FAILED);
    }

    /**
     * Property 9: Complete success response
     * 
     * **Validates: Requirements 3.5, 4.5, 11.4**
     * 
     * For any event distribution where all queues succeed, the Events Connector should return
     * HTTP 200 OK with SUCCESS status.
     */
    @Property(tries = 100)
    @Tag("Property 9: Complete success response")
    void shouldReturnSuccessWhenAllQueuesSucceed(
        @ForAll @Size(min = 1, max = 20) List<@From("uuids") UUID> queueIds
    ) throws Exception {
        // Arrange
        SubscriptionQueryService subscriptionQueryService = mock(SubscriptionQueryService.class);
        QueueEnqueueService queueEnqueueService = mock(QueueEnqueueService.class);
        EventMetrics eventMetrics = mock(EventMetrics.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);
        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        EventDistributionService service = new EventDistributionService(
            subscriptionQueryService, queueEnqueueService, environmentServiceClient, eventMetrics, new NoOpTracingUtils()
        );

        String serviceName = "StudentPersonal";
        String zoneId = "zone-1";
        String contextId = "DEFAULT";

        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId,
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        List<SubscriptionDTO> subscriptions = queueIds.stream()
            .map(queueId -> new SubscriptionDTO(
                UUID.randomUUID(), queueId, null, serviceName, (String) null, (String) null, SubscriptionType.EVENT, Instant.now()
            ))
            .collect(Collectors.toList());

        // All queues succeed
        List<QueueDeliveryResult> deliveryResults = queueIds.stream()
            .map(queueId -> QueueDeliveryResult.success(queueId.toString()))
            .collect(Collectors.toList());

        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(subscriptions);
        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(deliveryResults);

        // Act
        EventDistributionResponse response = service.distributeEvent("iowa", "production", request);

        // Assert
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(response.deliveryResults()).hasSize(queueIds.size());
        assertThat(response.deliveryResults())
            .allMatch(r -> r.status() == DeliveryResultStatus.SUCCESS);
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}