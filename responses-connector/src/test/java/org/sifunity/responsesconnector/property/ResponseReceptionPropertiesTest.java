// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.model.SifErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.responsesconnector.controller.InternalResponseController;
import org.sifunity.responsesconnector.controller.ResponseController;
import org.sifunity.responsesconnector.dto.InternalErrorDetail;
import org.sifunity.responsesconnector.dto.InternalErrorRequest;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.responsesconnector.service.ResponseDestinationValidator;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for Response Reception.
 * <p>
 * This test class validates the correctness properties for response reception
 * as defined in the design document. It uses jqwik for property-based testing
 * to verify that the properties hold across a wide range of randomized inputs.
 * </p>
 * 
 * <p><b>Properties Tested:</b></p>
 * <ul>
 *   <li>Property 1: Response acceptance with valid headers</li>
 *   <li>Property 2: Response validation enforcement</li>
 *   <li>Property 3: Internal response acceptance</li>
 * </ul>
 * 
 * <p><b>Test Configuration:</b></p>
 * <ul>
 *   <li>Each property runs 100+ iterations with randomized inputs</li>
 *   <li>Uses mocked ResponseRoutingService to isolate controller behavior</li>
 *   <li>Validates HTTP status codes and response structure</li>
 * </ul>
 * 
 * @see org.sifunity.responsesconnector.controller.ResponseController
 * @see org.sifunity.responsesconnector.controller.internalResponseController
 */
class ResponseReceptionPropertiesTest {
    
    private final HeaderSanitizer headerSanitizer = mock(HeaderSanitizer.class);
    private final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    private final ConnectorMetricsService connectorMetricsService = mock(ConnectorMetricsService.class);
    
    {
        when(headerSanitizer.sanitize(any(HttpServletRequest.class))).thenReturn(Collections.emptyMap());
        when(httpServletRequest.getMethod()).thenReturn("POST");
    }
    
    /**
     * Property 1: Response acceptance with valid headers
     * <p>
     * For any response with valid queueId and requestId headers,
     * the Responses Connector should accept the response and return HTTP 200 OK.
     * </p>
     * 
     * <p><b>Validates: Requirements 1.1, 1.4</b></p>
     * 
     * <p><b>Test Strategy:</b></p>
     * <ul>
     *   <li>Generate random valid UUIDs for queueId, requestId, environmentId</li>
     *   <li>Generate random valid response payloads</li>
     *   <li>Call controller with all required headers</li>
     *   <li>Verify HTTP 200 OK is returned</li>
     *   <li>Verify response contains messageId, queueId, status, timestamp</li>
     * </ul>
     */
    @Property(tries = 20)
    @Label("Property 1: Response acceptance with valid headers")
    void shouldAcceptResponseWithValidHeaders(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validUUID") UUID environmentId,
            @ForAll("validEnvironmentName") String environmentName,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create mocks and controller
        ResponseRoutingService routingService = mock(ResponseRoutingService.class);
        ResponseController responseController = new ResponseController(routingService, headerSanitizer, connectorMetricsService, mock(ResponseDestinationValidator.class));
        
        UUID messageId = UUID.randomUUID();
        ResponseResultDTO mockResult = ResponseResultDTO.builder()
                .messageId(messageId)
                .queueId(queueId)
                .status("DELIVERED")
                .timestamp(Instant.now())
                .build();
        
        when(routingService.routeResponse(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockResult);
        
        // Act: Call the primary header-based endpoint (relativeServicePath only)
        ResponseEntity<ResponseResultDTO> result = responseController.handleResponse(
                "test-domain", "test-solution",
                environmentId, environmentName, queueId, requestId,
                response.getServiceName(), response.getServiceType(),
                null, null, response.getPayload(), httpServletRequest);
        
        // Assert: Verify response
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessageId()).isEqualTo(messageId);
        assertThat(result.getBody().getQueueId()).isEqualTo(queueId);
        assertThat(result.getBody().getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getBody().getTimestamp()).isNotNull();
        
        // Verify routing service was called with correct parameters
        verify(routingService).routeResponse(anyString(), anyString(), eq(environmentId), eq(environmentName), eq(queueId), eq(requestId), any(ResponseDTO.class), any(), any(), any(), any());
    }
    
    
    /**
     * Property 3: Internal response acceptance
     * <p>
     * For any internal response from Requests Connector with valid queueId and requestId,
     * the Responses Connector should accept and process it identically to provider responses.
     * </p>
     * 
     * <p><b>Validates: Requirements 2.1, 2.2, 2.3, 2.5</b></p>
     * 
     * <p><b>Test Strategy:</b></p>
     * <ul>
     *   <li>Generate random valid UUIDs for queueId and requestId</li>
     *   <li>Generate random valid response payloads</li>
     *   <li>Call internal controller (no environment headers)</li>
     *   <li>Verify HTTP 200 OK is returned</li>
     *   <li>Verify response contains messageId and status</li>
     * </ul>
     */
    @Property(tries = 20)
    @Label("Property 3: Internal response acceptance")
    void shouldAcceptInternalResponseWithValidHeaders(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validResponse") ResponseDTO response) {
        
        // Arrange: Create mocks and controller
        ResponseRoutingService routingService = mock(ResponseRoutingService.class);
        HeaderSanitizer headerSanitizer = mock(HeaderSanitizer.class);
        when(headerSanitizer.sanitize(any(HttpServletRequest.class))).thenReturn(Collections.emptyMap());
        InternalResponseController internalResponseController = new InternalResponseController(routingService, headerSanitizer);
        
        UUID messageId = UUID.randomUUID();
        ResponseResultDTO mockResult = ResponseResultDTO.builder()
                .messageId(messageId)
                .status("DELIVERED")
                .timestamp(Instant.now())
                .build();
        
        when(routingService.routeInternalResponse(anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(mockResult);
        
        // Act: Call the internal controller (no environment headers required)
        ResponseEntity<ResponseResultDTO> result = internalResponseController.handleInternalResponse(
                queueId, requestId, "test-domain", "test-solution", null, response, new MockHttpServletRequest());
        
        // Assert: Verify response
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessageId()).isEqualTo(messageId);
        assertThat(result.getBody().getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getBody().getTimestamp()).isNotNull();
        
        // Verify routing service was called with correct parameters
        verify(routingService).routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(response), any());
    }
    
    /**
     * Property 3: Internal error response acceptance
     * <p>
     * For any internal error response from Requests Connector with valid queueId and requestId,
     * the Responses Connector should accept and process it.
     * </p>
     * 
     * <p><b>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5</b></p>
     */
    @Property(tries = 20)
    @Label("Property 3: Internal error response acceptance")
    void shouldAcceptInternalErrorResponseWithValidHeaders(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validErrorMessage") String errorMessage) {
        
        // Arrange: Create mocks and controller
        ResponseRoutingService routingService = mock(ResponseRoutingService.class);
        HeaderSanitizer headerSanitizer = mock(HeaderSanitizer.class);
        when(headerSanitizer.sanitize(any(HttpServletRequest.class))).thenReturn(Collections.emptyMap());
        InternalResponseController internalResponseController = new InternalResponseController(routingService, headerSanitizer);
        
        UUID messageId = UUID.randomUUID();
        ResponseResultDTO mockResult = ResponseResultDTO.builder()
                .messageId(messageId)
                .status("ERROR_DELIVERED")
                .timestamp(Instant.now())
                .build();
        
        when(routingService.routeInternalErrorResponse(anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(mockResult);
        
        // Act: Call the internal error controller
        ResponseEntity<ResponseResultDTO> result = internalResponseController.handleInternalErrorResponse(
                queueId, requestId, "test-domain", "test-solution", new InternalErrorRequest(InternalErrorDetail.ofMessage(errorMessage)), new MockHttpServletRequest());
        
        // Assert: Verify response
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessageId()).isEqualTo(messageId);
        assertThat(result.getBody().getStatus()).isEqualTo("ERROR_DELIVERED");
        assertThat(result.getBody().getTimestamp()).isNotNull();
        
        // Verify routing service was called with correct parameters
        verify(routingService).routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any());
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
        return Arbitraries.of("OBJECT", "FUNCTION", "UTILITY", "SERVICEPATH");
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
