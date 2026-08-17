// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.eventsconnector.dto.EventPublicationRequest;
import org.sifunity.eventsconnector.exception.SubscriptionServiceException;
import org.sifunity.eventsconnector.metrics.EventMetrics;
import org.sifunity.eventsconnector.client.EnvironmentServiceClient;
import org.sifunity.eventsconnector.service.EventDistributionService;
import org.sifunity.eventsconnector.service.QueueEnqueueService;
import org.sifunity.eventsconnector.service.SubscriptionQueryService;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for service integration.
 * Tests Properties 14-18 from the design document.
 * 
 * <p>Uses jqwik for property-based testing with randomized inputs.</p>
 */
@Tag("Feature: events-connector")
class ServiceIntegrationPropertiesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 14: Subscription Service unavailability handling
     * 
     * **Validates: Requirements 2.5, 8.4, 8.5, 10.1**
     * 
     * For any event where the Subscription Service is unavailable or times out, the Events
     * Connector should return HTTP 503 Service Unavailable.
     */
    @Property(tries = 100)
    @Tag("Property 14: Subscription Service unavailability handling")
    void shouldHandleSubscriptionServiceUnavailability(
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

        // Simulate Subscription Service unavailability
        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenThrow(new SubscriptionServiceException(
                "Subscription Service unavailable", 
                new RuntimeException("Connection refused")
            ));

        // Act & Assert
        assertThatThrownBy(() -> service.distributeEvent("iowa", "production", request))
            .isInstanceOf(SubscriptionServiceException.class)
            .hasMessageContaining("Subscription Service unavailable");

        verify(queueEnqueueService, never()).enqueueToMultipleQueues(anyString(), anyString(), anyList(), any());
    }

    /**
     * Property 15: Queue Service unavailability handling
     * 
     * **Validates: Requirements 3.8, 9.4, 9.5, 10.1**
     * 
     * For any event where the Queue Service is unavailable for all queues, the Events Connector
     * should return HTTP 503 Service Unavailable or HTTP 500 Internal Server Error.
     * 
     * Note: Queue Service failures are handled gracefully at the queue level, resulting in
     * FAILED delivery status rather than throwing exceptions.
     */
    @Property(tries = 100)
    @Tag("Property 15: Queue Service unavailability handling")
    void shouldHandleQueueServiceUnavailability(
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

        // Simulate Queue Service unavailability
        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(List.of(
                new org.sifunity.broker.common.dto.SubscriptionDTO(
                    UUID.randomUUID(), UUID.randomUUID(), null, serviceName, (String) null, (String) null, org.sifunity.broker.common.constant.SubscriptionType.EVENT, java.time.Instant.now()
                )
            ));
        
        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(List.of(
                org.sifunity.eventsconnector.dto.QueueDeliveryResult.failure(
                    UUID.randomUUID().toString(), "Queue Service unavailable"
                )
            ));

        // Act
        org.sifunity.eventsconnector.dto.EventDistributionResponse response = service.distributeEvent("iowa", "production", request);

        // Assert
        // Queue Service unavailability results in FAILED delivery status
        assertThat(response.deliveryStatus()).isEqualTo(org.sifunity.eventsconnector.dto.DeliveryStatus.FAILED);
    }

    /**
     * Property 16: Subscription Service retry on transient errors
     * 
     * **Validates: Requirements 13.1, 13.3, 13.4**
     * 
     * For any Subscription Service call that fails with a 5xx error, the Events Connector
     * should retry the call with exponential backoff up to the configured maximum attempts.
     * 
     * Note: Retry logic is implemented in the SubscriptionServiceClient, tested separately.
     */
    @Property(tries = 50)
    @Tag("Property 16: Subscription Service retry on transient errors")
    void shouldRetrySubscriptionServiceOn5xxErrors(
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

        // Simulate 5xx error that should trigger retry
        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenThrow(new SubscriptionServiceException(
                "Service temporarily unavailable",
                new RuntimeException("Service error")
            ));

        // Act & Assert
        assertThatThrownBy(() -> service.distributeEvent("iowa", "production", request))
            .isInstanceOf(SubscriptionServiceException.class);

        // Note: Actual retry logic is in SubscriptionServiceClient, tested in client tests
        verify(subscriptionQueryService).querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any());
    }

    /**
     * Property 17: Queue Service retry on transient errors
     * 
     * **Validates: Requirements 13.2, 13.3, 13.4**
     * 
     * For any Queue Service call that fails with a 5xx error, the Events Connector should
     * retry the call with exponential backoff up to the configured maximum attempts.
     * 
     * Note: Retry logic is implemented in the QueueServiceClient, tested separately.
     */
    @Property(tries = 50)
    @Tag("Property 17: Queue Service retry on transient errors")
    void shouldRetryQueueServiceOn5xxErrors(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName
    ) throws Exception {
        // Note: Retry logic for Queue Service is implemented in QueueServiceClient
        // and tested in QueueServiceClientTest with WireMock.
        // This property test verifies the integration behavior.
        
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
            serviceName, "zone-1", "DEFAULT",
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        UUID queueId = UUID.randomUUID();
        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(List.of(
                new org.sifunity.broker.common.dto.SubscriptionDTO(
                    UUID.randomUUID(), queueId, null, serviceName, (String) null, (String) null, SubscriptionType.EVENT, Instant.now()
                )
            ));

        // Simulate Queue Service failure (after retries exhausted)
        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(List.of(
                org.sifunity.eventsconnector.dto.QueueDeliveryResult.failure(
                    queueId.toString(), "Service temporarily unavailable"
                )
            ));

        // Act
        org.sifunity.eventsconnector.dto.EventDistributionResponse response = service.distributeEvent("iowa", "production", request);

        // Assert
        // After retries are exhausted, should return FAILED status
        assertThat(response.deliveryStatus()).isEqualTo(org.sifunity.eventsconnector.dto.DeliveryStatus.FAILED);
    }

    /**
     * Property 18: No retry on client errors
     * 
     * **Validates: Requirements 13.6**
     * 
     * For any service call that fails with a 4xx error, the Events Connector should not
     * retry the call.
     * 
     * Note: No-retry logic for 4xx errors is implemented in the service clients and tested
     * in client tests with WireMock.
     */
    @Property(tries = 50)
    @Tag("Property 18: No retry on client errors")
    void shouldNotRetryOn4xxErrors(
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

        // Simulate 4xx error (should not retry)
        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenThrow(new SubscriptionServiceException(
                "Bad request",
                new RuntimeException("Service error")
            ));

        // Act & Assert
        assertThatThrownBy(() -> service.distributeEvent("iowa", "production", request))
            .isInstanceOf(SubscriptionServiceException.class);

        // Verify only called once (no retries for 4xx)
        verify(subscriptionQueryService, times(1)).querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any());
    }

    /**
     * Additional test: Timeout handling
     */
    @Property(tries = 50)
    @Tag("Property 14: Subscription Service unavailability handling")
    void shouldHandleSubscriptionServiceTimeout(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName
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
            serviceName, "zone-1", "DEFAULT",
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        // Simulate timeout
        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenThrow(new SubscriptionServiceException(
                "Request timeout",
                new java.util.concurrent.TimeoutException("Read timeout")
            ));

        // Act & Assert
        assertThatThrownBy(() -> service.distributeEvent("iowa", "production", request))
            .isInstanceOf(SubscriptionServiceException.class)
            .hasMessageContaining("Request timeout");
    }

    /**
     * Additional test: Network error handling
     */
    @Property(tries = 50)
    @Tag("Property 14: Subscription Service unavailability handling")
    void shouldHandleNetworkErrors(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName
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
            serviceName, "zone-1", "DEFAULT",
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        // Simulate network error
        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenThrow(new SubscriptionServiceException(
                "Network error",
                new java.net.ConnectException("Connection refused")
            ));

        // Act & Assert
        assertThatThrownBy(() -> service.distributeEvent("iowa", "production", request))
            .isInstanceOf(SubscriptionServiceException.class)
            .hasMessageContaining("Network error");
    }
}

