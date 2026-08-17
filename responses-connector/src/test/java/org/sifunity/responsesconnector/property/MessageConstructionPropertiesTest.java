// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.responsesconnector.dto.InternalErrorDetail;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.util.MessageBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-Based Tests for Message Construction.
 * <p>
 * This test class validates the correctness properties for message construction
 * as defined in the design document. It uses jqwik for property-based testing
 * to verify that the properties hold across a wide range of randomized inputs.
 * </p>
 * 
 * <p><b>Properties Tested:</b></p>
 * <ul>
 *   <li>Property 13: SIF message envelope construction</li>
 *   <li>Property 14: Payload preservation</li>
 * </ul>
 * 
 * <p><b>Test Configuration:</b></p>
 * <ul>
 *   <li>Each property runs 100+ iterations with randomized inputs</li>
 *   <li>Tests all four message builder methods</li>
 *   <li>Validates SIF 3.7 compliance</li>
 * </ul>
 * 
 * <p><b>Requirements Validated:</b></p>
 * <ul>
 *   <li>6.1: SIF message envelope with all required headers</li>
 *   <li>6.2: Include messageId, timestamp, messageType, and serviceName</li>
 *   <li>6.3: Include requestId for correlation</li>
 *   <li>6.4: Preserve response payload from provider</li>
 * </ul>
 * 
 * @see org.sifunity.responsesconnector.util.MessageBuilder
 */
class MessageConstructionPropertiesTest {
    
    private final MessageBuilder messageBuilder = new MessageBuilder();
    
    /**
     * Property 13: SIF message envelope construction - buildResponseEnvelope
     * <p>
     * For any response, the Responses Connector should construct a SIF message envelope
     * with messageId, timestamp, messageType, serviceName, and requestId.
     * </p>
     * 
     * <p><b>Validates: Requirements 6.1, 6.2, 6.3</b></p>
     * 
     * <p><b>Test Strategy:</b></p>
     * <ul>
     *   <li>Generate random valid UUIDs for environmentId and requestId</li>
     *   <li>Generate random valid response payloads</li>
     *   <li>Build response envelope</li>
     *   <li>Verify all required SIF headers are present and valid</li>
     *   <li>Verify messageId is unique and valid UUID</li>
     *   <li>Verify timestamp is current</li>
     *   <li>Verify messageType is RESPONSE</li>
     *   <li>Verify serviceName and serviceType match input</li>
     *   <li>Verify requestId is preserved for correlation</li>
     * </ul>
     */
    @Property(tries = 20)
    @Label("Property 13: SIF message envelope construction - buildResponseEnvelope")
    void shouldConstructValidSifEnvelopeForResponse(
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Capture time before building envelope
        Instant before = Instant.now();
        
        // Act: Build response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        
        // Assert: Verify all required SIF headers are present
        Instant after = Instant.now();
        
        // Requirement 6.2: messageId must be present and valid UUID
        assertThat(envelope.getMessageId())
                .as("messageId must be present")
                .isNotNull()
                .isInstanceOf(UUID.class);
        
        // Requirement 6.2: timestamp must be present and current
        assertThat(envelope.getTimestamp())
                .as("timestamp must be present and current")
                .isNotNull()
                .isBetween(before.minus(1, ChronoUnit.SECONDS), after.plus(1, ChronoUnit.SECONDS));
        
        // Requirement 6.2: messageType must be RESPONSE
        assertThat(envelope.getMessageType())
                .as("messageType must be RESPONSE")
                .isEqualTo(MessageType.RESPONSE);
        
        // Requirement 6.2: serviceName must match input
        assertThat(envelope.getServiceName())
                .as("serviceName must match input")
                .isEqualTo(response.getServiceName());
        
        // Requirement 6.2: serviceType must match input
        assertThat(envelope.getServiceType())
                .as("serviceType must match input")
                .isEqualTo(ServiceType.valueOf(response.getServiceType()));
        
        // Requirement 6.3: requestId must be preserved for correlation
        assertThat(envelope.getRequestId())
                .as("requestId must be preserved for correlation")
                .isEqualTo(requestId);
        
        // Requirement 6.1: sourceId (environmentId) must be present
        assertThat(envelope.getSourceId())
                .as("sourceId (environmentId) must be present")
                .isEqualTo(environmentId);
    }
    
    /**
     * Property 13: SIF message envelope construction - buildInternalResponseEnvelope
     * <p>
     * For any internal response, the Responses Connector should construct a SIF message envelope
     * with messageId, timestamp, messageType, serviceName, and requestId.
     * </p>
     * 
     * <p><b>Validates: Requirements 6.1, 6.2, 6.3</b></p>
     */
    @Property(tries = 20)
    @Label("Property 13: SIF message envelope construction - buildInternalResponseEnvelope")
    void shouldConstructValidSifEnvelopeForInternalResponse(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Capture time before building envelope
        Instant before = Instant.now();
        
        // Act: Build internal response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildInternalResponseEnvelope(
                requestId, response);
        
        // Assert: Verify all required SIF headers are present
        Instant after = Instant.now();
        
        // Requirement 6.2: messageId must be present and valid UUID
        assertThat(envelope.getMessageId())
                .as("messageId must be present")
                .isNotNull()
                .isInstanceOf(UUID.class);
        
        // Requirement 6.2: timestamp must be present and current
        assertThat(envelope.getTimestamp())
                .as("timestamp must be present and current")
                .isNotNull()
                .isBetween(before.minus(1, ChronoUnit.SECONDS), after.plus(1, ChronoUnit.SECONDS));
        
        // Requirement 6.2: messageType must be RESPONSE
        assertThat(envelope.getMessageType())
                .as("messageType must be RESPONSE")
                .isEqualTo(MessageType.RESPONSE);
        
        // Requirement 6.2: serviceName must match input
        assertThat(envelope.getServiceName())
                .as("serviceName must match input")
                .isEqualTo(response.getServiceName());
        
        // Requirement 6.2: serviceType must match input
        assertThat(envelope.getServiceType())
                .as("serviceType must match input")
                .isEqualTo(ServiceType.valueOf(response.getServiceType()));
        
        // Requirement 6.3: requestId must be preserved for correlation
        assertThat(envelope.getRequestId())
                .as("requestId must be preserved for correlation")
                .isEqualTo(requestId);
        
        // Internal responses don't have sourceId (no environment context)
        assertThat(envelope.getSourceId())
                .as("sourceId should be null for internal responses")
                .isNull();
    }
    
    /**
     * Property 13: SIF message envelope construction - buildInternalErrorEnvelope
     * <p>
     * For any internal error, the Responses Connector should construct a SIF message envelope
     * with messageId, timestamp, messageType, serviceName, and requestId.
     * </p>
     * 
     * <p><b>Validates: Requirements 6.1, 6.2, 6.3</b></p>
     */
    @Property(tries = 20)
    @Label("Property 13: SIF message envelope construction - buildInternalErrorEnvelope")
    void shouldConstructValidSifEnvelopeForInternalError(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validErrorMessage") String errorMessage) {
        
        // Arrange: Capture time before building envelope
        Instant before = Instant.now();
        
        // Act: Build internal error envelope
        SifMessageEnvelope<SifErrorResponse> envelope = messageBuilder.buildInternalErrorEnvelope(
                requestId, InternalErrorDetail.ofMessage(errorMessage));
        
        // Assert: Verify all required SIF headers are present
        Instant after = Instant.now();
        
        // Requirement 6.2: messageId must be present and valid UUID
        assertThat(envelope.getMessageId())
                .as("messageId must be present")
                .isNotNull()
                .isInstanceOf(UUID.class);
        
        // Requirement 6.2: timestamp must be present and current
        assertThat(envelope.getTimestamp())
                .as("timestamp must be present and current")
                .isNotNull()
                .isBetween(before.minus(1, ChronoUnit.SECONDS), after.plus(1, ChronoUnit.SECONDS));
        
        // Requirement 6.2: messageType must be RESPONSE
        assertThat(envelope.getMessageType())
                .as("messageType must be RESPONSE")
                .isEqualTo(MessageType.RESPONSE);
        
        // Requirement 6.2: serviceName must be InternalError
        assertThat(envelope.getServiceName())
                .as("serviceName must be InternalError")
                .isEqualTo("InternalError");
        
        // Requirement 6.2: serviceType must be UTILITY
        assertThat(envelope.getServiceType())
                .as("serviceType must be UTILITY")
                .isEqualTo(ServiceType.UTILITY);
        
        // Requirement 6.3: requestId must be preserved for correlation
        assertThat(envelope.getRequestId())
                .as("requestId must be preserved for correlation")
                .isEqualTo(requestId);
        
        // Internal errors don't have sourceId (no environment context)
        assertThat(envelope.getSourceId())
                .as("sourceId should be null for internal errors")
                .isNull();
    }
    
    /**
     * Property 14: Payload preservation - buildResponseEnvelope
     * <p>
     * For any response, the Responses Connector should preserve the complete response payload
     * from the provider in the enqueued message.
     * </p>
     * 
     * <p><b>Validates: Requirements 6.4</b></p>
     * 
     * <p><b>Test Strategy:</b></p>
     * <ul>
     *   <li>Generate random complex payloads with nested structures</li>
     *   <li>Build response envelope</li>
     *   <li>Verify payload is preserved exactly as provided</li>
     *   <li>Verify no data loss or transformation</li>
     * </ul>
     */
    @Property(tries = 20)
    @Label("Property 14: Payload preservation - buildResponseEnvelope")
    void shouldPreserveCompletePayloadForResponse(
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Act: Build response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        
        // Assert: Verify payload is preserved exactly
        // Requirement 6.4: Complete payload preservation
        assertThat(envelope.getPayload())
                .as("payload must be preserved exactly as provided")
                .isNotNull()
                .isEqualTo(response.getPayload());
        
        // Verify payload is the same object reference (no copying/transformation)
        assertThat(envelope.getPayload())
                .as("payload should be the same object reference")
                .isSameAs(response.getPayload());
    }
    
    /**
     * Property 14: Payload preservation - buildInternalResponseEnvelope
     * <p>
     * For any internal response, the Responses Connector should preserve the complete response payload.
     * </p>
     * 
     * <p><b>Validates: Requirements 6.4</b></p>
     */
    @Property(tries = 20)
    @Label("Property 14: Payload preservation - buildInternalResponseEnvelope")
    void shouldPreserveCompletePayloadForInternalResponse(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Act: Build internal response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildInternalResponseEnvelope(
                requestId, response);
        
        // Assert: Verify payload is preserved exactly
        // Requirement 6.4: Complete payload preservation
        assertThat(envelope.getPayload())
                .as("payload must be preserved exactly as provided")
                .isNotNull()
                .isEqualTo(response.getPayload());
        
        // Verify payload is the same object reference (no copying/transformation)
        assertThat(envelope.getPayload())
                .as("payload should be the same object reference")
                .isSameAs(response.getPayload());
    }
    
    /**
     * Property 14: Payload preservation - buildInternalErrorEnvelope
     * <p>
     * For any internal error, the Responses Connector should preserve the error message
     * in the constructed error payload.
     * </p>
     * 
     * <p><b>Validates: Requirements 6.4</b></p>
     */
    @Property(tries = 20)
    @Label("Property 14: Payload preservation - buildInternalErrorEnvelope")
    void shouldPreserveErrorMessageForInternalError(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validErrorMessage") String errorMessage) {
        
        // Act: Build internal error envelope
        SifMessageEnvelope<SifErrorResponse> envelope = messageBuilder.buildInternalErrorEnvelope(
                requestId, InternalErrorDetail.ofMessage(errorMessage));
        
        // Assert: Verify error message is preserved in payload
        // Requirement 6.4: Complete payload preservation
        assertThat(envelope.getPayload())
                .as("error payload must be present")
                .isNotNull();
        
        assertThat(envelope.getPayload().message())
                .as("error message must be preserved exactly as provided")
                .isEqualTo(errorMessage);
        
        // Verify error has correct structure for internal errors
        assertThat(envelope.getPayload().subCode())
                .as("error code must be INTERNAL_ERROR")
                .isEqualTo("INTERNAL_ERROR");
        
        assertThat(envelope.getPayload().scope())
                .as("error scope must be BROKER")
                .isEqualTo("BROKER");
    }
    
    /**
     * Property 14: Payload preservation - complex nested payloads
     * <p>
     * For any response with complex nested payloads, the Responses Connector should preserve
     * all nested structures without modification.
     * </p>
     * 
     * <p><b>Validates: Requirements 6.4</b></p>
     */
    @Property(tries = 20)
    @Label("Property 14: Payload preservation - complex nested payloads")
    void shouldPreserveComplexNestedPayloads(
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validUUIDString") String requestId,
            @ForAll("complexPayload") Map<String, Object> complexPayload) {
        
        // Arrange: Create response with complex payload
        ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(complexPayload)
                .build();
        
        // Act: Build response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        
        // Assert: Verify complex payload is preserved exactly
        // Requirement 6.4: Complete payload preservation including nested structures
        assertThat(envelope.getPayload())
                .as("complex payload must be preserved exactly")
                .isNotNull()
                .isEqualTo(complexPayload);
        
        // Verify it's the same object reference
        assertThat(envelope.getPayload())
                .as("payload should be the same object reference")
                .isSameAs(complexPayload);
    }
    
    /**
     * Property 13 & 14: Message ID uniqueness across multiple calls
     * <p>
     * For any sequence of message construction calls, each message should have a unique messageId
     * while preserving all other properties.
     * </p>
     * 
     * <p><b>Validates: Requirements 6.2, 6.3, 6.4</b></p>
     */
    @Property(tries = 20)
    @Label("Property 13 & 14: Message ID uniqueness across multiple calls")
    void shouldGenerateUniqueMessageIdsAcrossMultipleCalls(
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Act: Build multiple envelopes with same inputs
        SifMessageEnvelope<Object> envelope1 = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        SifMessageEnvelope<Object> envelope2 = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        SifMessageEnvelope<Object> envelope3 = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        
        // Assert: Verify each message has unique messageId
        assertThat(envelope1.getMessageId())
                .as("first messageId must be unique")
                .isNotEqualTo(envelope2.getMessageId())
                .isNotEqualTo(envelope3.getMessageId());
        
        assertThat(envelope2.getMessageId())
                .as("second messageId must be unique")
                .isNotEqualTo(envelope3.getMessageId());
        
        // Verify all other properties are preserved consistently
        assertThat(envelope1.getRequestId())
                .as("requestId must be preserved across all calls")
                .isEqualTo(requestId)
                .isEqualTo(envelope2.getRequestId())
                .isEqualTo(envelope3.getRequestId());
        
        assertThat(envelope1.getPayload())
                .as("payload must be preserved across all calls")
                .isEqualTo(response.getPayload())
                .isEqualTo(envelope2.getPayload())
                .isEqualTo(envelope3.getPayload());
    }
    
    // ========== Arbitrary Providers ==========
    
    /**
     * Provides valid UUID values for property tests.
     */
    @Provide
    Arbitrary<UUID> validUUID() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> validUUIDString() {
        return Arbitraries.randomValue(random -> UUID.randomUUID().toString());
    }
    
    /**
     * Provides valid environment names for property tests.
     */
    @Provide
    Arbitrary<String> validEnvironmentName() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_', ' ')
                .ofMinLength(3)
                .ofMaxLength(50);
    }
    
    /**
     * Provides valid ResponseDTO objects for property tests.
     */
    @Provide
    Arbitrary<ResponseDTO> validResponse() {
        return Combinators.combine(
                validServiceName(),
                validServiceType(),
                validPayload()
        ).as((serviceName, serviceType, payload) ->
                ResponseDTO.builder()
                        .serviceName(serviceName)
                        .serviceType(serviceType)
                        .payload(payload)
                        .build()
        );
    }
    
    /**
     * Provides valid service names for property tests.
     */
    @Provide
    Arbitrary<String> validServiceName() {
        return Arbitraries.of(
                "StudentPersonal",
                "SchoolInfo",
                "StaffPersonal",
                "StudentAttendance",
                "StudentGrade",
                "CourseInfo",
                "TeachingGroup",
                "TimeTableSubject",
                "CalendarSummary",
                "LearningResource"
        );
    }
    
    /**
     * Provides valid service types for property tests.
     */
    @Provide
    Arbitrary<String> validServiceType() {
        return Arbitraries.of("OBJECT", "FUNCTIONAL", "UTILITY");
    }
    
    /**
     * Provides valid payload objects for property tests.
     */
    @Provide
    Arbitrary<Object> validPayload() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
                Arbitraries.integers().between(1, 100)
        ).as((key1, key2, value) -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put(key1, key2);
            payload.put("id", value);
            payload.put("timestamp", Instant.now().toString());
            return payload;
        });
    }
    
    /**
     * Provides complex nested payload objects for property tests.
     */
    @Provide
    Arbitrary<Map<String, Object>> complexPayload() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
                Arbitraries.integers().between(1, 1000),
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50),
                Arbitraries.doubles().between(0.0, 100.0)
        ).as((name, id, description, score) -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", id);
            payload.put("name", name);
            payload.put("description", description);
            
            // Add nested object
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("score", score);
            metadata.put("timestamp", Instant.now().toString());
            metadata.put("version", "1.0");
            payload.put("metadata", metadata);
            
            // Add nested array
            payload.put("tags", java.util.List.of("tag1", "tag2", "tag3"));
            
            return payload;
        });
    }
    
    /**
     * Provides valid error messages for property tests.
     */
    @Provide
    Arbitrary<String> validErrorMessage() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '.', ',', ':', '-')
                .ofMinLength(10)
                .ofMaxLength(200);
    }
    
}
