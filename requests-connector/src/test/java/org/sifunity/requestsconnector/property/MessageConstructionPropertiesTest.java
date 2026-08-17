// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.util.MessageBuilder;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for message construction.
 * 
 * <p>These tests verify that the MessageBuilder correctly constructs SIF message
 * envelopes with all required headers for both IMMEDIATE and DELAYED request patterns.</p>
 * 
 * <p>Tests Properties 19, 20, 21 from the design document.</p>
 */
@Tag("Feature: requests-connector")
class MessageConstructionPropertiesTest {
    
    private final MessageBuilder messageBuilder = new MessageBuilder();
    
    /**
     * Property 19: SIF message envelope construction
     * 
     * <p>For any request message being constructed, the connector should create a SIF message
     * envelope with all required headers (messageId, timestamp, messageType, serviceName,
     * serviceType) and include zoneId, contextId, and requestId from the original request.</p>
     * 
     * <p>Validates: Requirements 6.1, 6.2, 6.3</p>
     */
    @Property(tries = 10)
    @Tag("Property 19: SIF message envelope construction")
    void immediateRequestEnvelopeContainsAllRequiredHeaders(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentNames") String environmentName,
            @ForAll("immediateRequests") ImmediateRequestDTO request) {
        
        // When: Building a request envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, request, Collections.emptyMap());
        
        // Then: All required headers should be present
        assertThat(envelope.getMessageId())
            .as("messageId should be generated")
            .isNotNull();
        
        assertThat(envelope.getTimestamp())
            .as("timestamp should be generated")
            .isNotNull();
        
        assertThat(envelope.getMessageType())
            .as("messageType should match request")
            .isEqualTo(MessageType.valueOf(request.getMessageType()));
        
        assertThat(envelope.getServiceName())
            .as("serviceName should match request")
            .isEqualTo(request.getServiceName());
        
        assertThat(envelope.getServiceType())
            .as("serviceType should match request")
            .isEqualTo(ServiceType.valueOf(request.getServiceType()));
        
        assertThat(envelope.getSourceId())
            .as("sourceId should be set to environmentId")
            .isEqualTo(environmentId);
    }
    
    /**
     * Property 19 (continued): SIF message envelope construction for DELAYED requests
     * 
     * <p>Validates: Requirements 6.1, 6.2, 6.3</p>
     */
    @Property(tries = 10)
    @Tag("Property 19: SIF message envelope construction")
    void delayedRequestEnvelopeContainsAllRequiredHeaders(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentNames") String environmentName,
            @ForAll("queueIds") UUID consumerQueueId,
            @ForAll("delayedRequests") DelayedRequestDTO request) {
        
        // When: Building a delayed request envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
            environmentId, environmentName, consumerQueueId, request, Collections.emptyMap(), "default", "default", null);
        
        // Then: All required headers should be present
        assertThat(envelope.getMessageId())
            .as("messageId should be generated")
            .isNotNull();
        
        assertThat(envelope.getTimestamp())
            .as("timestamp should be generated")
            .isNotNull();
        
        assertThat(envelope.getMessageType())
            .as("messageType should match request")
            .isEqualTo(MessageType.valueOf(request.getMessageType()));
        
        assertThat(envelope.getServiceName())
            .as("serviceName should match request")
            .isEqualTo(request.getServiceName());
        
        assertThat(envelope.getServiceType())
            .as("serviceType should match request")
            .isEqualTo(ServiceType.valueOf(request.getServiceType()));
        
        assertThat(envelope.getSourceId())
            .as("sourceId should be set to environmentId")
            .isEqualTo(environmentId);
    }
    
    /**
     * Property 20: DELAYED request queueId inclusion
     * 
     * <p>For any DELAYED request message being constructed, the connector should include
     * the consumer's queueId for response routing.</p>
     * 
     * <p>Validates: Requirements 6.4</p>
     */
    @Property(tries = 10)
    @Tag("Property 20: DELAYED request queueId inclusion")
    void delayedRequestEnvelopeIncludesConsumerQueueId(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentNames") String environmentName,
            @ForAll("queueIds") UUID consumerQueueId,
            @ForAll("delayedRequests") DelayedRequestDTO request) {
        
        // When: Building a delayed request envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
            environmentId, environmentName, consumerQueueId, request, Collections.emptyMap(), "default", "default", null);
        
        // Then: The consumer's queueId should be included
        assertThat(envelope.getQueueId())
            .as("queueId should be set to consumer's queue ID")
            .isEqualTo(consumerQueueId);
    }
    
    /**
     * Property 21: Request payload preservation
     * 
     * <p>For any request message being constructed, the connector should preserve
     * the original request payload unchanged.</p>
     * 
     * <p>Validates: Requirements 6.5</p>
     */
    @Property(tries = 10)
    @Tag("Property 21: Request payload preservation")
    void immediateRequestEnvelopePreservesPayload(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentNames") String environmentName,
            @ForAll("immediateRequests") ImmediateRequestDTO request) {
        
        // When: Building a request envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, request, Collections.emptyMap());
        
        // Then: The payload should be preserved unchanged
        assertThat(envelope.getPayload())
            .as("payload should be preserved unchanged")
            .isEqualTo(request.getPayload());
    }
    
    /**
     * Property 21 (continued): Request payload preservation for DELAYED requests
     * 
     * <p>Validates: Requirements 6.5</p>
     */
    @Property(tries = 10)
    @Tag("Property 21: Request payload preservation")
    void delayedRequestEnvelopePreservesPayload(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentNames") String environmentName,
            @ForAll("queueIds") UUID consumerQueueId,
            @ForAll("delayedRequests") DelayedRequestDTO request) {
        
        // When: Building a delayed request envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
            environmentId, environmentName, consumerQueueId, request, Collections.emptyMap(), "default", "default", null);
        
        // Then: The payload should be preserved unchanged (null defaults to empty map)
        Object expectedPayload = request.getPayload() != null ? request.getPayload() : Collections.emptyMap();
        assertThat(envelope.getPayload())
            .as("payload should be preserved unchanged")
            .isEqualTo(expectedPayload);
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<UUID> queueIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<String> environmentNames() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(1)
            .flatMap(first -> 
                Arbitraries.strings()
                    .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-")
                    .ofMinLength(0)
                    .ofMaxLength(30)
                    .map(rest -> first + rest)
            );
    }
    
    @Provide
    Arbitrary<MessageType> messageTypes() {
        return Arbitraries.of(MessageType.class);
    }
    
    @Provide
    Arbitrary<ServiceType> serviceTypes() {
        return Arbitraries.of(ServiceType.class);
    }
    
    @Provide
    Arbitrary<Object> payloads() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),
            Arbitraries.strings().ofMaxLength(100),
            Arbitraries.integers(),
            Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().ofMaxLength(50)
            ).ofMaxSize(5)
        );
    }
    
    @Provide
    Arbitrary<ImmediateRequestDTO> immediateRequests() {
        return Combinators.combine(
            serviceNames(),
            serviceTypes(),
            messageTypes(),
            payloads()
        ).as((serviceName, serviceType, messageType, payload) -> {
            ImmediateRequestDTO dto = new ImmediateRequestDTO();
            dto.setServiceName(serviceName);
            dto.setServiceType(serviceType.name());
            dto.setMessageType(messageType.name());
            dto.setPayload(payload);
            return dto;
        });
    }
    
    @Provide
    Arbitrary<DelayedRequestDTO> delayedRequests() {
        return Combinators.combine(
            serviceNames(),
            serviceTypes(),
            messageTypes(),
            payloads()
        ).as((serviceName, serviceType, messageType, payload) -> {
            DelayedRequestDTO dto = new DelayedRequestDTO();
            dto.setServiceName(serviceName);
            dto.setServiceType(serviceType.name());
            dto.setMessageType(messageType.name());
            dto.setPayload(payload);
            return dto;
        });
    }
}
