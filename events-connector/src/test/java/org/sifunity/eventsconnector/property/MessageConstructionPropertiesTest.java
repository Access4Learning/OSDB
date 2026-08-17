// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.time.api.Dates;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.eventsconnector.dto.EventPublicationRequest;
import org.sifunity.eventsconnector.dto.QueueDeliveryResult;
import org.sifunity.eventsconnector.metrics.EventMetrics;
import org.sifunity.eventsconnector.client.EnvironmentServiceClient;
import org.sifunity.eventsconnector.service.EventDistributionService;
import org.sifunity.eventsconnector.service.QueueEnqueueService;
import org.sifunity.eventsconnector.service.SubscriptionQueryService;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for message construction.
 * Tests Properties 10-13 from the design document.
 * 
 * <p>Uses jqwik for property-based testing with randomized inputs.</p>
 */
@Tag("Feature: events-connector")
class MessageConstructionPropertiesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 10: SIF message envelope construction
     * 
     * **Validates: Requirements 5.1, 5.2, 5.5**
     * 
     * For any event, the Events Connector should construct a SIF message envelope with
     * messageId, timestamp, messageType (EVENT), serviceName, zoneId, contextId, and payload.
     */
    @Property(tries = 100)
    @Tag("Property 10: SIF message envelope construction")
    void shouldConstructSifMessageEnvelopeWithAllRequiredFields(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String zoneId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String contextId,
        @ForAll @From("validInstants") Instant timestamp
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

        String messageId = UUID.randomUUID().toString();
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\",\"data\":\"test\"}");
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId, messageId, timestamp, payload, null, null);

        UUID queueId = UUID.randomUUID();
        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(UUID.randomUUID(), queueId, null, serviceName, (String) null, (String) null, SubscriptionType.EVENT, Instant.now())
        );

        when(subscriptionQueryService.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), any()))
            .thenReturn(subscriptions);
        when(queueEnqueueService.enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), any(SifMessageEnvelope.class)))
            .thenReturn(List.of(QueueDeliveryResult.success(queueId.toString())));

        ArgumentCaptor<SifMessageEnvelope> envelopeCaptor = ArgumentCaptor.forClass(SifMessageEnvelope.class);

        // Act
        service.distributeEvent("iowa", "production", request);

        // Assert
        verify(queueEnqueueService).enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), envelopeCaptor.capture());
        
        SifMessageEnvelope<?> envelope = envelopeCaptor.getValue();
        assertThat(envelope).isNotNull();
        assertThat(envelope.getMessageId().toString()).isEqualTo(messageId);
        assertThat(envelope.getTimestamp()).isEqualTo(timestamp);
        assertThat(envelope.getMessageType()).isEqualTo(MessageType.EVENT);
        assertThat(envelope.getServiceName()).isEqualTo(serviceName);
        assertThat(envelope.getZoneId()).isEqualTo(zoneId);
        assertThat(envelope.getContextId()).isEqualTo(contextId);
        assertThat(envelope.getPayload()).isEqualTo(payload);
    }

    /**
     * Property 11: MessageId generation
     * 
     * **Validates: Requirements 5.3**
     * 
     * For any event without a messageId, the Events Connector should generate a unique messageId.
     */
    @Property(tries = 100)
    @Tag("Property 11: MessageId generation")
    void shouldGenerateUniqueMessageIdWhenNotProvided(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String zoneId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String contextId
    ) throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        
        // Create request without messageId (null)
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId, null, Instant.now(), payload, null, null
        );

        // Assert
        // The EventPublicationRequest constructor should generate a messageId
        assertThat(request.messageId()).isNotNull();
        assertThat(request.messageId()).isNotBlank();
        
        // Verify it's a valid UUID
        UUID.fromString(request.messageId()); // Should not throw exception
    }

    /**
     * Property 12: Timestamp generation
     * 
     * **Validates: Requirements 5.4**
     * 
     * For any event without a timestamp, the Events Connector should set the timestamp
     * to the current time.
     */
    @Property(tries = 100)
    @Tag("Property 12: Timestamp generation")
    void shouldGenerateTimestampWhenNotProvided(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String zoneId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String contextId
    ) throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        Instant before = Instant.now();
        
        // Create request without timestamp (null)
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId, UUID.randomUUID().toString(), null, payload, null, null
        );
        
        Instant after = Instant.now();

        // Assert
        // The EventPublicationRequest constructor should generate a timestamp
        assertThat(request.timestamp()).isNotNull();
        assertThat(request.timestamp()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    /**
     * Property 13: Payload preservation
     * 
     * **Validates: Requirements 3.3, 5.5**
     * 
     * For any event, the Events Connector should preserve the complete event payload
     * when constructing the message envelope.
     */
    @Property(tries = 100)
    @Tag("Property 13: Payload preservation")
    void shouldPreserveCompleteEventPayload(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String zoneId,
        @ForAll @AlphaChars @StringLength(min = 1, max = 20) String contextId,
        @ForAll @AlphaChars @StringLength(min = 10, max = 100) String payloadData
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

        JsonNode payload = objectMapper.readTree(
            "{\"eventAction\":\"CREATE\",\"data\":\"" + payloadData + "\",\"nested\":{\"field\":\"value\"}}"
        );
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

        ArgumentCaptor<SifMessageEnvelope> envelopeCaptor = ArgumentCaptor.forClass(SifMessageEnvelope.class);

        // Act
        service.distributeEvent("iowa", "production", request);

        // Assert
        verify(queueEnqueueService).enqueueToMultipleQueues(eq("iowa"), eq("production"), anyList(), envelopeCaptor.capture());
        
        SifMessageEnvelope<?> envelope = envelopeCaptor.getValue();
        assertThat(envelope.getPayload()).isEqualTo(payload);
        
        // Verify payload structure is preserved
        JsonNode envelopePayload = (JsonNode) envelope.getPayload();
        assertThat(envelopePayload.has("eventAction")).isTrue();
        assertThat(envelopePayload.has("data")).isTrue();
        assertThat(envelopePayload.has("nested")).isTrue();
        assertThat(envelopePayload.get("data").asText()).isEqualTo(payloadData);
    }

    /**
     * Additional test: MessageId uniqueness across multiple events
     */
    @Property(tries = 100)
    @Tag("Property 11: MessageId generation")
    void shouldGenerateUniqueMessageIdsForMultipleEvents(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName
    ) throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        
        // Create multiple requests without messageId
        EventPublicationRequest request1 = new EventPublicationRequest(
            serviceName, "zone-1", "DEFAULT", null, Instant.now(), payload, null, null
        );
        EventPublicationRequest request2 = new EventPublicationRequest(
            serviceName, "zone-1", "DEFAULT", null, Instant.now(), payload, null, null
        );
        EventPublicationRequest request3 = new EventPublicationRequest(
            serviceName, "zone-1", "DEFAULT", null, Instant.now(), payload, null, null
        );

        // Assert
        // All generated messageIds should be unique
        assertThat(request1.messageId()).isNotEqualTo(request2.messageId());
        assertThat(request1.messageId()).isNotEqualTo(request3.messageId());
        assertThat(request2.messageId()).isNotEqualTo(request3.messageId());
    }

    /**
     * Additional test: Provided messageId should be preserved
     */
    @Property(tries = 100)
    @Tag("Property 10: SIF message envelope construction")
    void shouldPreserveProvidedMessageId(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
        @ForAll @From("uuids") UUID providedMessageId
    ) throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        
        // Create request with provided messageId
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, "zone-1", "DEFAULT", providedMessageId.toString(), Instant.now(), payload, null, null
        );

        // Assert
        assertThat(request.messageId()).isEqualTo(providedMessageId.toString());
    }

    /**
     * Additional test: Provided timestamp should be preserved
     */
    @Property(tries = 100)
    @Tag("Property 10: SIF message envelope construction")
    void shouldPreserveProvidedTimestamp(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
        @ForAll @From("validInstants") Instant providedTimestamp
    ) throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");
        
        // Create request with provided timestamp
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, "zone-1", "DEFAULT", UUID.randomUUID().toString(), providedTimestamp, payload, null, null
        );

        // Assert
        assertThat(request.timestamp()).isEqualTo(providedTimestamp);
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<Instant> validInstants() {
        return Dates.dates()
            .between(LocalDate.of(2020, 1, 1), LocalDate.of(2030, 12, 31))
            .map(date -> date.atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}