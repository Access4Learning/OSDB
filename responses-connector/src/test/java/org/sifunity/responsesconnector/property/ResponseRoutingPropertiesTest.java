// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.responsesconnector.client.QueueServiceClient;
import org.sifunity.responsesconnector.dto.InternalErrorDetail;
import org.sifunity.responsesconnector.dto.InternalErrorRequest;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.responsesconnector.exception.QueueNotFoundException;
import org.sifunity.responsesconnector.exception.QueueServiceUnavailableException;
import org.sifunity.responsesconnector.service.LegacyTagLookupService;
import org.sifunity.responsesconnector.service.ResponseDestinationValidator;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.sifunity.responsesconnector.util.MessageBuilder;
import org.sifunity.broker.common.util.NoOpTracingUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for Response Routing.
 * <p>
 * This test class validates the correctness properties for response routing
 * as defined in the design document. It uses jqwik for property-based testing
 * to verify that the properties hold across a wide range of randomized inputs.
 * </p>
 * 
 * <p><b>Properties Tested:</b></p>
 * <ul>
 *   <li>Property 4: Response enqueuing to consumer queue</li>
 *   <li>Property 5: Queue not found error handling</li>
 *   <li>Property 6: Queue Service unavailability handling</li>
 * </ul>
 * 
 * <p><b>Test Configuration:</b></p>
 * <ul>
 *   <li>Each property runs 100+ iterations with randomized inputs</li>
 *   <li>Uses mocked QueueServiceClient to simulate Queue Service behavior</li>
 *   <li>Validates response routing logic and error handling</li>
 * </ul>
 * 
 * @see org.sifunity.responsesconnector.service.ResponseRoutingService
 * @see org.sifunity.responsesconnector.client.QueueServiceClient
 */
class ResponseRoutingPropertiesTest {
    
    /**
     * Property 4: Response enqueuing to consumer queue
     * <p>
     * For any valid response, the Responses Connector should enqueue the response
     * to the consumer's queue via Queue Service API using the provided queueId.
     * </p>
     * 
     * <p><b>Validates: Requirements 3.1, 3.2, 3.4</b></p>
     * 
     * <p><b>Test Strategy:</b></p>
     * <ul>
     *   <li>Generate random valid UUIDs for queueId, requestId, environmentId</li>
     *   <li>Generate random valid response payloads</li>
     *   <li>Mock QueueServiceClient to return success</li>
     *   <li>Call routeResponse on ResponseRoutingService</li>
     *   <li>Verify QueueServiceClient.enqueueMessage was called with correct queueId</li>
     *   <li>Verify response contains messageId, queueId, status="DELIVERED", timestamp</li>
     * </ul>
     */
    @Property(tries = 20)
    @Label("Property 4: Response enqueuing to consumer queue")
    void shouldEnqueueResponseToConsumerQueue(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create mocks
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        MessageBuilder messageBuilder = new MessageBuilder();
        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class))).thenReturn(UUID.randomUUID());
        when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class))).thenReturn(false);
        ResponseRoutingService routingService = new ResponseRoutingService(queueClient, messageBuilder, new NoOpTracingUtils(), legacyTagLookupService, mock(ResponseDestinationValidator.class));
        
        UUID messageId = UUID.randomUUID();
        when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class)))
                .thenReturn(messageId);
        
        // Act: Route the response
        ResponseResultDTO result = routingService.routeResponse(
                "test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal");
        
        // Assert: Verify response enqueuing
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.getQueueId()).isEqualTo(queueId);
        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getTimestamp()).isNotNull();
        
        // Verify QueueServiceClient was called with correct queueId
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        
        // Verify the envelope was constructed correctly
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), argThat(envelope -> 
            envelope.getRequestId().equals(requestId) &&
            envelope.getSourceId().equals(environmentId) &&
            envelope.getServiceName().equals(response.getServiceName()) &&
            envelope.getServiceType().name().equals(response.getServiceType())
        ));
    }
    
    /**
     * Property 4: Internal response enqueuing to consumer queue
     * <p>
     * For any valid internal response, the Responses Connector should enqueue the response
     * to the consumer's queue via Queue Service API using the provided queueId.
     * </p>
     * 
     * <p><b>Validates: Requirements 3.1, 3.2, 2.5</b></p>
     */
    @Property(tries = 20)
    @Label("Property 4: Internal response enqueuing to consumer queue")
    void shouldEnqueueInternalResponseToConsumerQueue(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create mocks
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        MessageBuilder messageBuilder = new MessageBuilder();
        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class))).thenReturn(UUID.randomUUID());
        when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class))).thenReturn(false);
        ResponseRoutingService routingService = new ResponseRoutingService(queueClient, messageBuilder, new NoOpTracingUtils(), legacyTagLookupService, mock(ResponseDestinationValidator.class));
        
        UUID messageId = UUID.randomUUID();
        when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class)))
                .thenReturn(messageId);
        
        // Act: Route the internal response
        ResponseResultDTO result = routingService.routeInternalResponse(
                "test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap());
        
        // Assert: Verify internal response enqueuing
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getTimestamp()).isNotNull();
        
        // Verify QueueServiceClient was called with correct queueId
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        
        // Verify the envelope was constructed correctly
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), argThat(envelope -> 
            envelope.getRequestId().equals(requestId) &&
            envelope.getServiceName().equals(response.getServiceName()) &&
            envelope.getServiceType().name().equals(response.getServiceType())
        ));
    }
    
    /**
     * Property 5: Queue not found error handling
     * <p>
     * For any response with a queueId that does not exist, the Responses Connector
     * should throw QueueNotFoundException which will be mapped to HTTP 404 Not Found
     * with message "Queue not found".
     * </p>
     * 
     * <p><b>Validates: Requirements 3.7</b></p>
     * 
     * <p><b>Test Strategy:</b></p>
     * <ul>
     *   <li>Generate random valid UUIDs for queueId, requestId, environmentId</li>
     *   <li>Generate random valid response payloads</li>
     *   <li>Mock QueueServiceClient to throw QueueNotFoundException</li>
     *   <li>Call routeResponse on ResponseRoutingService</li>
     *   <li>Verify QueueNotFoundException is thrown</li>
     *   <li>Verify exception message contains the queueId</li>
     * </ul>
     */
    @Property(tries = 20)
    @Label("Property 5: Queue not found error handling")
    void shouldThrowQueueNotFoundExceptionWhenQueueDoesNotExist(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create mocks
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        MessageBuilder messageBuilder = new MessageBuilder();
        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class))).thenReturn(UUID.randomUUID());
        when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class))).thenReturn(false);
        ResponseRoutingService routingService = new ResponseRoutingService(queueClient, messageBuilder, new NoOpTracingUtils(), legacyTagLookupService, mock(ResponseDestinationValidator.class));
        
        // Mock QueueServiceClient to throw QueueNotFoundException
        when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class)))
                .thenThrow(new QueueNotFoundException("Queue not found: " + queueId));
        
        // Act & Assert: Verify QueueNotFoundException is thrown
        assertThatThrownBy(() -> routingService.routeResponse(
                "test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal"))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessageContaining("Queue not found")
                .hasMessageContaining(queueId.toString());
        
        // Verify QueueServiceClient was called
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
    }
    
    /**
     * Property 5: Queue not found error handling for internal responses
     * <p>
     * For any internal response with a queueId that does not exist, the Responses Connector
     * should throw QueueNotFoundException.
     * </p>
     * 
     * <p><b>Validates: Requirements 3.7</b></p>
     */
    @Property(tries = 20)
    @Label("Property 5: Queue not found error handling for internal responses")
    void shouldThrowQueueNotFoundExceptionForInternalResponseWhenQueueDoesNotExist(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create mocks
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        MessageBuilder messageBuilder = new MessageBuilder();
        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class))).thenReturn(UUID.randomUUID());
        when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class))).thenReturn(false);
        ResponseRoutingService routingService = new ResponseRoutingService(queueClient, messageBuilder, new NoOpTracingUtils(), legacyTagLookupService, mock(ResponseDestinationValidator.class));
        
        // Mock QueueServiceClient to throw QueueNotFoundException
        when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class)))
                .thenThrow(new QueueNotFoundException("Queue not found: " + queueId));
        
        // Act & Assert: Verify QueueNotFoundException is thrown
        assertThatThrownBy(() -> routingService.routeInternalResponse(
                "test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap()))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessageContaining("Queue not found")
                .hasMessageContaining(queueId.toString());
        
        // Verify QueueServiceClient was called
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
    }
    
    /**
     * Property 6: Queue Service unavailability handling
     * <p>
     * For any response when Queue Service is unavailable, the Responses Connector
     * should throw QueueServiceUnavailableException which will be mapped to
     * HTTP 503 Service Unavailable.
     * </p>
     * 
     * <p><b>Validates: Requirements 3.6, 10.1</b></p>
     * 
     * <p><b>Test Strategy:</b></p>
     * <ul>
     *   <li>Generate random valid UUIDs for queueId, requestId, environmentId</li>
     *   <li>Generate random valid response payloads</li>
     *   <li>Mock QueueServiceClient to throw QueueServiceUnavailableException</li>
     *   <li>Call routeResponse on ResponseRoutingService</li>
     *   <li>Verify QueueServiceUnavailableException is thrown</li>
     *   <li>Verify exception message indicates service unavailability</li>
     * </ul>
     */
    @Property(tries = 20)
    @Label("Property 6: Queue Service unavailability handling")
    void shouldThrowQueueServiceUnavailableExceptionWhenQueueServiceIsUnavailable(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create mocks
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        MessageBuilder messageBuilder = new MessageBuilder();
        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class))).thenReturn(UUID.randomUUID());
        when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class))).thenReturn(false);
        ResponseRoutingService routingService = new ResponseRoutingService(queueClient, messageBuilder, new NoOpTracingUtils(), legacyTagLookupService, mock(ResponseDestinationValidator.class));
        
        // Mock QueueServiceClient to throw QueueServiceUnavailableException
        when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class)))
                .thenThrow(new QueueServiceUnavailableException("Queue Service unavailable"));
        
        // Act & Assert: Verify QueueServiceUnavailableException is thrown
        assertThatThrownBy(() -> routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal"))
                .isInstanceOf(QueueServiceUnavailableException.class)
                .hasMessageContaining("Queue Service unavailable");
        
        // Verify QueueServiceClient was called
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
    }
    
    /**
     * Property 6: Queue Service unavailability handling for internal responses
     * <p>
     * For any internal response when Queue Service is unavailable, the Responses Connector
     * should throw QueueServiceUnavailableException.
     * </p>
     * 
     * <p><b>Validates: Requirements 3.6, 10.1</b></p>
     */
    @Property(tries = 20)
    @Label("Property 6: Queue Service unavailability handling for internal responses")
    void shouldThrowQueueServiceUnavailableExceptionForInternalResponseWhenQueueServiceIsUnavailable(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create mocks
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        MessageBuilder messageBuilder = new MessageBuilder();
        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class))).thenReturn(UUID.randomUUID());
        when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class))).thenReturn(false);
        ResponseRoutingService routingService = new ResponseRoutingService(queueClient, messageBuilder, new NoOpTracingUtils(), legacyTagLookupService, mock(ResponseDestinationValidator.class));
        
        // Mock QueueServiceClient to throw QueueServiceUnavailableException
        when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class)))
                .thenThrow(new QueueServiceUnavailableException("Queue Service unavailable"));
        
        // Act & Assert: Verify QueueServiceUnavailableException is thrown
        assertThatThrownBy(() -> routingService.routeInternalResponse("test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap()))
                .isInstanceOf(QueueServiceUnavailableException.class)
                .hasMessageContaining("Queue Service unavailable");
        
        // Verify QueueServiceClient was called
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
    }
    
    /**
     * Property 6: Queue Service unavailability handling for internal error responses
     * <p>
     * For any internal error response when Queue Service is unavailable, the Responses Connector
     * should throw QueueServiceUnavailableException.
     * </p>
     * 
     * <p><b>Validates: Requirements 3.6, 10.1</b></p>
     */
    @Property(tries = 20)
    @Label("Property 6: Queue Service unavailability handling for internal error responses")
    void shouldThrowQueueServiceUnavailableExceptionForInternalErrorResponseWhenQueueServiceIsUnavailable(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validErrorMessage") String errorMessage) {
        
        // Arrange: Create mocks
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        MessageBuilder messageBuilder = new MessageBuilder();
        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class))).thenReturn(UUID.randomUUID());
        when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class))).thenReturn(false);
        ResponseRoutingService routingService = new ResponseRoutingService(queueClient, messageBuilder, new NoOpTracingUtils(), legacyTagLookupService, mock(ResponseDestinationValidator.class));
        
        // Mock QueueServiceClient to throw QueueServiceUnavailableException
        when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class)))
                .thenThrow(new QueueServiceUnavailableException("Queue Service unavailable"));
        
        // Act & Assert: Verify QueueServiceUnavailableException is thrown
        assertThatThrownBy(() -> routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage(errorMessage), Collections.emptyMap()))
                .isInstanceOf(QueueServiceUnavailableException.class)
                .hasMessageContaining("Queue Service unavailable");
        
        // Verify QueueServiceClient was called
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
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
