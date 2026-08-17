// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.eventsconnector.dto.QueueDeliveryResult;
import org.sifunity.eventsconnector.dto.EventPublicationRequest;
import org.sifunity.eventsconnector.metrics.EventMetrics;
import org.sifunity.eventsconnector.client.EnvironmentServiceClient;
import org.sifunity.eventsconnector.service.EventDistributionService;
import org.sifunity.eventsconnector.service.QueueEnqueueService;
import org.sifunity.eventsconnector.service.SubscriptionQueryService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for inter-service domain/solution propagation.
 *
 * <p>Property 7: Inter-Service Domain/Solution Propagation</p>
 * <p>For any incoming request to the events-connector with domainId and solutionId path variables,
 * all outgoing HTTP calls to queue-service and subscription-service shall include the same
 * domainId and solutionId in their URL paths.</p>
 *
 * <p><strong>Validates: Requirements 8.1, 8.2, 8.4</strong></p>
 */
@Tag("Feature: domain-solution-url-prefix")
@Tag("Property 7: Inter-Service Domain/Solution Propagation")
class InterServicePropagationPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 7a: domainId and solutionId are propagated to SubscriptionQueryService.
     *
     * <p><strong>Validates: Requirements 8.2, 8.4</strong></p>
     *
     * For any domainId and solutionId, when EventDistributionService.distributeEvent is called,
     * the SubscriptionQueryService receives the exact same domainId and solutionId.
     */
    @Property(tries = 100)
    @Tag("Property 7a: Propagation to SubscriptionQueryService")
    void domainAndSolutionPropagatedToSubscriptionQuery(
        @ForAll @AlphaChars @StringLength(min = 1, max = 30) String domainId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 30) String solutionId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 30) String serviceName,
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

        when(subscriptionQueryService.querySubscriptions(anyString(), anyString(), anyString(), any()))
            .thenReturn(List.of());

        // Act
        service.distributeEvent(domainId, solutionId, request);

        // Assert — verify the exact domainId and solutionId were passed
        verify(subscriptionQueryService).querySubscriptions(
            eq(domainId), eq(solutionId), eq(serviceName), any()
        );
    }

    /**
     * Property 7b: domainId and solutionId are propagated to QueueEnqueueService.
     *
     * <p><strong>Validates: Requirements 8.1, 8.4</strong></p>
     *
     * For any domainId and solutionId, when EventDistributionService distributes an event
     * with matching subscriptions, the QueueEnqueueService receives the exact same
     * domainId and solutionId.
     */
    @Property(tries = 100)
    @Tag("Property 7b: Propagation to QueueEnqueueService")
    void domainAndSolutionPropagatedToQueueEnqueue(
        @ForAll @AlphaChars @StringLength(min = 1, max = 30) String domainId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 30) String solutionId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 30) String serviceName,
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

        when(subscriptionQueryService.querySubscriptions(anyString(), anyString(), anyString(), any()))
            .thenReturn(subscriptions);
        when(queueEnqueueService.enqueueToMultipleQueues(anyString(), anyString(), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(List.of(QueueDeliveryResult.success(queueId.toString())));

        // Act
        service.distributeEvent(domainId, solutionId, request);

        // Assert — verify the exact domainId and solutionId were passed to enqueue
        verify(queueEnqueueService).enqueueToMultipleQueues(
            eq(domainId), eq(solutionId), anyList(), any(SifMessageEnvelope.class)
        );
    }

    /**
     * Property 7c: domainId and solutionId are consistently propagated to both services.
     *
     * <p><strong>Validates: Requirements 8.1, 8.2, 8.4</strong></p>
     *
     * For any domainId and solutionId, when EventDistributionService distributes an event
     * with matching subscriptions, both the SubscriptionQueryService and QueueEnqueueService
     * receive the identical domainId and solutionId values.
     */
    @Property(tries = 100)
    @Tag("Property 7c: Consistent propagation to both services")
    void domainAndSolutionConsistentlyPropagatedToBothServices(
        @ForAll @AlphaChars @StringLength(min = 1, max = 30) String domainId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 30) String solutionId
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
        String zoneId = "zone1";
        String contextId = "DEFAULT";

        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"UPDATE\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId,
            UUID.randomUUID().toString(), Instant.now(), payload, null, null
        );

        UUID queueId = UUID.randomUUID();
        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(UUID.randomUUID(), queueId, null, serviceName, (String) null, (String) null, SubscriptionType.EVENT, Instant.now())
        );

        when(subscriptionQueryService.querySubscriptions(anyString(), anyString(), anyString(), any()))
            .thenReturn(subscriptions);
        when(queueEnqueueService.enqueueToMultipleQueues(anyString(), anyString(), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(List.of(QueueDeliveryResult.success(queueId.toString())));

        // Act
        service.distributeEvent(domainId, solutionId, request);

        // Assert — both services received the same domainId and solutionId
        verify(subscriptionQueryService).querySubscriptions(
            eq(domainId), eq(solutionId), eq(serviceName), any()
        );
        verify(queueEnqueueService).enqueueToMultipleQueues(
            eq(domainId), eq(solutionId), anyList(), any(SifMessageEnvelope.class)
        );
    }
}
