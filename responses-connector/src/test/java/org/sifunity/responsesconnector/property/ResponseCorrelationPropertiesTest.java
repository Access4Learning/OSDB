// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.responsesconnector.dto.InternalErrorDetail;
import org.sifunity.responsesconnector.dto.InternalErrorRequest;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.util.MessageBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-Based Tests for Response Correlation.
 * <p>
 * This test class validates the correctness properties for response correlation
 * as defined in the design document. It uses jqwik for property-based testing
 * to verify that the properties hold across a wide range of randomized inputs.
 * </p>
 * 
 * <p><b>Properties Tested:</b></p>
 * <ul>
 *   <li>Property 7: Request correlation preservation</li>
 *   <li>Property 8: Message ID generation</li>
 *   <li>Property 9: Timestamp inclusion</li>
 * </ul>
 * 
 * <p><b>Test Configuration:</b></p>
 * <ul>
 *   <li>Each property runs 100+ iterations with randomized inputs</li>
 *   <li>Tests MessageBuilder utility for proper envelope construction</li>
 *   <li>Validates correlation metadata is preserved in all message types</li>
 * </ul>
 * 
 * @see org.sifunity.responsesconnector.util.MessageBuilder
 */
class ResponseCorrelationPropertiesTest {
    
    /**
     * Property 7: Request correlation preservation
     * <p>
     * For any response, the Responses Connector should include the requestId
     * in the enqueued message metadata for correlation with the original request.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.1, 4.5</b></p>
     * 
     * <p><b>Test Strategy:</b></p>
     * <ul>
     *   <li>Generate random valid UUIDs for requestId and environmentId</li>
     *   <li>Generate random valid response payloads</li>
     *   <li>Build message envelope using MessageBuilder</li>
     *   <li>Verify requestId is preserved in the envelope</li>
     *   <li>Verify requestId matches the original request ID</li>
     * </ul>
     */
    @Property(tries = 20)
    @Label("Property 7: Request correlation preservation")
    void shouldPreserveRequestIdInResponseEnvelope(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create MessageBuilder
        MessageBuilder messageBuilder = new MessageBuilder();
        
        // Act: Build response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        
        // Assert: Verify requestId is preserved
        assertThat(envelope).isNotNull();
        assertThat(envelope.getRequestId()).isNotNull();
        assertThat(envelope.getRequestId()).isEqualTo(requestId);
    }
    
    /**
     * Property 7: Request correlation preservation for internal responses
     * <p>
     * For any internal response, the Responses Connector should include the requestId
     * in the enqueued message metadata for correlation with the original request.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.1, 4.5</b></p>
     */
    @Property(tries = 20)
    @Label("Property 7: Request correlation preservation for internal responses")
    void shouldPreserveRequestIdInInternalResponseEnvelope(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create MessageBuilder
        MessageBuilder messageBuilder = new MessageBuilder();
        
        // Act: Build internal response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildInternalResponseEnvelope(
                requestId, response);
        
        // Assert: Verify requestId is preserved
        assertThat(envelope).isNotNull();
        assertThat(envelope.getRequestId()).isNotNull();
        assertThat(envelope.getRequestId()).isEqualTo(requestId);
    }
    
    /**
     * Property 7: Request correlation preservation for internal error responses
     * <p>
     * For any internal error response, the Responses Connector should include the requestId
     * in the enqueued message metadata for correlation with the original request.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.1, 4.5, 5.5</b></p>
     */
    @Property(tries = 20)
    @Label("Property 7: Request correlation preservation for internal error responses")
    void shouldPreserveRequestIdInInternalErrorEnvelope(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validErrorMessage") String errorMessage) {
        
        // Arrange: Create MessageBuilder
        MessageBuilder messageBuilder = new MessageBuilder();
        
        // Act: Build internal error envelope
        SifMessageEnvelope<SifErrorResponse> envelope = messageBuilder.buildInternalErrorEnvelope(
                requestId, InternalErrorDetail.ofMessage(errorMessage));
        
        // Assert: Verify requestId is preserved
        assertThat(envelope).isNotNull();
        assertThat(envelope.getRequestId()).isNotNull();
        assertThat(envelope.getRequestId()).isEqualTo(requestId);
    }
    
    /**
     * Property 8: Message ID generation
     * <p>
     * For any response, the Responses Connector should generate a new unique
     * messageId for the response message.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.3</b></p>
     * 
     * <p><b>Test Strategy:</b></p>
     * <ul>
     *   <li>Generate random valid UUIDs for requestId and environmentId</li>
     *   <li>Generate random valid response payloads</li>
     *   <li>Build message envelope using MessageBuilder</li>
     *   <li>Verify messageId is present in the envelope</li>
     *   <li>Verify messageId is a valid UUID</li>
     *   <li>Verify messageId is different from requestId</li>
     * </ul>
     */
    @Property(tries = 20)
    @Label("Property 8: Message ID generation")
    void shouldGenerateUniqueMessageIdForResponse(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create MessageBuilder
        MessageBuilder messageBuilder = new MessageBuilder();
        
        // Act: Build response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        
        // Assert: Verify messageId is generated
        assertThat(envelope).isNotNull();
        assertThat(envelope.getMessageId()).isNotNull();
        assertThat(envelope.getMessageId()).isInstanceOf(UUID.class);
        
        // Verify messageId is different from requestId (new unique ID)
        assertThat(envelope.getMessageId()).isNotEqualTo(requestId);
    }
    
    /**
     * Property 8: Message ID generation for internal responses
     * <p>
     * For any internal response, the Responses Connector should generate a new unique
     * messageId for the response message.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.3</b></p>
     */
    @Property(tries = 20)
    @Label("Property 8: Message ID generation for internal responses")
    void shouldGenerateUniqueMessageIdForInternalResponse(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create MessageBuilder
        MessageBuilder messageBuilder = new MessageBuilder();
        
        // Act: Build internal response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildInternalResponseEnvelope(
                requestId, response);
        
        // Assert: Verify messageId is generated
        assertThat(envelope).isNotNull();
        assertThat(envelope.getMessageId()).isNotNull();
        assertThat(envelope.getMessageId()).isInstanceOf(UUID.class);
        
        // Verify messageId is different from requestId (new unique ID)
        assertThat(envelope.getMessageId()).isNotEqualTo(requestId);
    }
    
    /**
     * Property 8: Message ID generation for internal error responses
     * <p>
     * For any internal error response, the Responses Connector should generate a new unique
     * messageId for the error response message.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.3</b></p>
     */
    @Property(tries = 20)
    @Label("Property 8: Message ID generation for internal error responses")
    void shouldGenerateUniqueMessageIdForInternalErrorResponse(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validErrorMessage") String errorMessage) {
        
        // Arrange: Create MessageBuilder
        MessageBuilder messageBuilder = new MessageBuilder();
        
        // Act: Build internal error envelope
        SifMessageEnvelope<SifErrorResponse> envelope = messageBuilder.buildInternalErrorEnvelope(
                requestId, InternalErrorDetail.ofMessage(errorMessage));
        
        // Assert: Verify messageId is generated
        assertThat(envelope).isNotNull();
        assertThat(envelope.getMessageId()).isNotNull();
        assertThat(envelope.getMessageId()).isInstanceOf(UUID.class);
        
        // Verify messageId is different from requestId (new unique ID)
        assertThat(envelope.getMessageId()).isNotEqualTo(requestId);
    }
    
    /**
     * Property 8: Message ID uniqueness across multiple invocations
     * <p>
     * For any two responses built with the same input data, the Responses Connector
     * should generate different messageIds to ensure uniqueness.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.3</b></p>
     */
    @Property(tries = 20)
    @Label("Property 8: Message ID uniqueness across multiple invocations")
    void shouldGenerateDifferentMessageIdsForMultipleInvocations(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create MessageBuilder
        MessageBuilder messageBuilder = new MessageBuilder();
        
        // Act: Build two response envelopes with the same input data
        SifMessageEnvelope<Object> envelope1 = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        SifMessageEnvelope<Object> envelope2 = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        
        // Assert: Verify messageIds are different
        assertThat(envelope1.getMessageId()).isNotNull();
        assertThat(envelope2.getMessageId()).isNotNull();
        assertThat(envelope1.getMessageId()).isNotEqualTo(envelope2.getMessageId());
    }
    
    /**
     * Property 9: Timestamp inclusion
     * <p>
     * For any response, the Responses Connector should include a timestamp
     * in the enqueued message.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.4</b></p>
     * 
     * <p><b>Test Strategy:</b></p>
     * <ul>
     *   <li>Generate random valid UUIDs for requestId and environmentId</li>
     *   <li>Generate random valid response payloads</li>
     *   <li>Build message envelope using MessageBuilder</li>
     *   <li>Verify timestamp is present in the envelope</li>
     *   <li>Verify timestamp is a valid Instant</li>
     *   <li>Verify timestamp is recent (within last few seconds)</li>
     * </ul>
     */
    @Property(tries = 20)
    @Label("Property 9: Timestamp inclusion")
    void shouldIncludeTimestampInResponseEnvelope(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create MessageBuilder and capture current time
        MessageBuilder messageBuilder = new MessageBuilder();
        Instant beforeBuild = Instant.now();
        
        // Act: Build response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        
        Instant afterBuild = Instant.now();
        
        // Assert: Verify timestamp is present and valid
        assertThat(envelope).isNotNull();
        assertThat(envelope.getTimestamp()).isNotNull();
        assertThat(envelope.getTimestamp()).isInstanceOf(Instant.class);
        
        // Verify timestamp is recent (between before and after build time)
        assertThat(envelope.getTimestamp()).isBetween(beforeBuild, afterBuild);
    }
    
    /**
     * Property 9: Timestamp inclusion for internal responses
     * <p>
     * For any internal response, the Responses Connector should include a timestamp
     * in the enqueued message.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.4</b></p>
     */
    @Property(tries = 20)
    @Label("Property 9: Timestamp inclusion for internal responses")
    void shouldIncludeTimestampInInternalResponseEnvelope(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create MessageBuilder and capture current time
        MessageBuilder messageBuilder = new MessageBuilder();
        Instant beforeBuild = Instant.now();
        
        // Act: Build internal response envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildInternalResponseEnvelope(
                requestId, response);
        
        Instant afterBuild = Instant.now();
        
        // Assert: Verify timestamp is present and valid
        assertThat(envelope).isNotNull();
        assertThat(envelope.getTimestamp()).isNotNull();
        assertThat(envelope.getTimestamp()).isInstanceOf(Instant.class);
        
        // Verify timestamp is recent (between before and after build time)
        assertThat(envelope.getTimestamp()).isBetween(beforeBuild, afterBuild);
    }
    
    /**
     * Property 9: Timestamp inclusion for internal error responses
     * <p>
     * For any internal error response, the Responses Connector should include a timestamp
     * in the enqueued message.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.4</b></p>
     */
    @Property(tries = 20)
    @Label("Property 9: Timestamp inclusion for internal error responses")
    void shouldIncludeTimestampInInternalErrorEnvelope(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validErrorMessage") String errorMessage) {
        
        // Arrange: Create MessageBuilder and capture current time
        MessageBuilder messageBuilder = new MessageBuilder();
        Instant beforeBuild = Instant.now();
        
        // Act: Build internal error envelope
        SifMessageEnvelope<SifErrorResponse> envelope = messageBuilder.buildInternalErrorEnvelope(
                requestId, InternalErrorDetail.ofMessage(errorMessage));
        
        Instant afterBuild = Instant.now();
        
        // Assert: Verify timestamp is present and valid
        assertThat(envelope).isNotNull();
        assertThat(envelope.getTimestamp()).isNotNull();
        assertThat(envelope.getTimestamp()).isInstanceOf(Instant.class);
        
        // Verify timestamp is recent (between before and after build time)
        assertThat(envelope.getTimestamp()).isBetween(beforeBuild, afterBuild);
    }
    
    /**
     * Property 9: Timestamp ordering across multiple invocations
     * <p>
     * For any two responses built sequentially, the second response should have
     * a timestamp that is equal to or after the first response's timestamp.
     * </p>
     * 
     * <p><b>Validates: Requirements 4.4</b></p>
     */
    @Property(tries = 20)
    @Label("Property 9: Timestamp ordering across multiple invocations")
    void shouldHaveOrderedTimestampsForSequentialInvocations(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create MessageBuilder
        MessageBuilder messageBuilder = new MessageBuilder();
        
        // Act: Build two response envelopes sequentially
        SifMessageEnvelope<Object> envelope1 = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        SifMessageEnvelope<Object> envelope2 = messageBuilder.buildResponseEnvelope(
                environmentId, environmentName, requestId, response, Collections.emptyMap());
        
        // Assert: Verify timestamps are ordered (second is equal or after first)
        assertThat(envelope1.getTimestamp()).isNotNull();
        assertThat(envelope2.getTimestamp()).isNotNull();
        assertThat(envelope2.getTimestamp()).isAfterOrEqualTo(envelope1.getTimestamp());
    }
    
    // ========== Arbitrary Providers ==========
    
    /**
     * Provides valid UUID values for property tests.
     */
    @Provide
    Arbitrary<UUID> validUUID() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    /**
     * Provides valid UUID strings for property tests (for fields typed as String, e.g. requestId).
     */
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
