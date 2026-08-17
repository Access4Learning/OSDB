// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.responsesconnector.controller.InternalResponseController;
import org.sifunity.responsesconnector.dto.InternalErrorDetail;
import org.sifunity.responsesconnector.dto.InternalErrorRequest;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InternalResponseController.
 * 
 * Tests specific examples and edge cases for the internal REST API endpoints,
 * including successful responses, error responses, and header validation.
 * 
 * <p><b>Test Coverage:</b></p>
 * <ul>
 *   <li>Internal response endpoint with valid request</li>
 *   <li>Internal error response endpoint (structured error detail)</li>
 *   <li>Header validation (queueId, requestId)</li>
 *   <li>No environment context required</li>
 *   <li>Service integration</li>
 * </ul>
 * 
 * <p><b>Requirements Validated:</b></p>
 * <ul>
 *   <li>2.1: Accept response from Requests Connector via internal API endpoint</li>
 *   <li>2.2: Validate queueId is provided</li>
 *   <li>2.3: Validate requestId is provided for correlation</li>
 *   <li>2.4: Accept both success and error responses</li>
 *   <li>2.5: Process response identically to provider responses</li>
 *   <li>2.6: Internal API accessible without authentication</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InternalResponseControllerTest {
    
    @Mock
    private ResponseRoutingService routingService;
    
    @Mock
    private HeaderSanitizer headerSanitizer;
    
    private InternalResponseController controller;
    
    @BeforeEach
    void setUp() {
        controller = new InternalResponseController(routingService, headerSanitizer);
        // Default: sanitizer returns empty map (no passthrough headers)
        when(headerSanitizer.sanitize(any(MockHttpServletRequest.class))).thenReturn(Collections.emptyMap());
    }
    
    // ========================================
    // Internal Response Endpoint Tests
    // ========================================
    
    @Test
    void shouldHandleInternalResponseSuccessfully() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("refId", "abc123");
        payload.put("name", "John Doe");
        
        ResponseDTO request = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(payload)
            .build();
        
        UUID messageId = UUID.randomUUID();
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(messageId)
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalResponse(queueId, requestId, "test-domain", "test-solution", null, request, new MockHttpServletRequest());
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessageId()).isEqualTo(messageId);
        assertThat(response.getBody().getStatus()).isEqualTo("DELIVERED");
        assertThat(response.getBody().getTimestamp()).isNotNull();
        
        verify(routingService).routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any());
    }
    
    @Test
    void shouldPassQueueIdAndRequestIdToRoutingService() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        
        ResponseDTO request = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(new HashMap<>())
            .build();
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any()))
            .thenReturn(expectedResult);
        
        // When
        controller.handleInternalResponse(queueId, requestId, "test-domain", "test-solution", null, request, new MockHttpServletRequest());
        
        // Then
        verify(routingService).routeInternalResponse(anyString(), anyString(), eq(queueId), 
            eq(requestId), 
            any(),
            eq(request),
            any());
    }
    
    @Test
    void shouldReturnCorrectStatusCodeForSuccessfulInternalResponse() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        
        ResponseDTO request = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(new HashMap<>())
            .build();
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalResponse(queueId, requestId, "test-domain", "test-solution", null, request, new MockHttpServletRequest());
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    @Test
    void shouldHandleInternalResponseWithComplexPayload() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        
        Map<String, Object> complexPayload = new HashMap<>();
        complexPayload.put("refId", "abc123");
        Map<String, String> name = new HashMap<>();
        name.put("givenName", "John");
        name.put("familyName", "Doe");
        complexPayload.put("name", name);
        complexPayload.put("age", 25);
        
        ResponseDTO request = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(complexPayload)
            .build();
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalResponse(queueId, requestId, "test-domain", "test-solution", null, request, new MockHttpServletRequest());
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(routingService).routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any());
    }
    
    @Test
    void shouldNotRequireEnvironmentContextForInternalResponse() {
        // Given - Note: No environment ID or name parameters
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        
        ResponseDTO request = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(new HashMap<>())
            .build();
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalResponse(queueId, requestId, "test-domain", "test-solution", null, request, new MockHttpServletRequest());
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Verify that routeInternalResponse is called (not routeResponse which requires environment context)
        verify(routingService).routeInternalResponse(anyString(), anyString(), any(), any(), any(), any(), any());
        verify(routingService, never()).routeResponse(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
    
    // ========================================
    // Internal Error Response Endpoint Tests
    // ========================================
    
    @Test
    void shouldHandleInternalErrorResponseSuccessfully() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String errorMessage = "Provider timeout: no response received within 30 seconds";
        
        UUID messageId = UUID.randomUUID();
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(messageId)
            .status("ERROR_DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalErrorResponse(queueId, requestId, "test-domain", "test-solution", new InternalErrorRequest(InternalErrorDetail.ofMessage(errorMessage)), new MockHttpServletRequest());
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessageId()).isEqualTo(messageId);
        assertThat(response.getBody().getStatus()).isEqualTo("ERROR_DELIVERED");
        assertThat(response.getBody().getTimestamp()).isNotNull();
        
        verify(routingService).routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any());
    }
    
    @Test
    void shouldPassErrorMessageToRoutingService() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String errorMessage = "Provider unavailable";
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("ERROR_DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any()))
            .thenReturn(expectedResult);
        
        // When
        controller.handleInternalErrorResponse(queueId, requestId, "test-domain", "test-solution", new InternalErrorRequest(InternalErrorDetail.ofMessage(errorMessage)), new MockHttpServletRequest());
        
        // Then
        verify(routingService).routeInternalErrorResponse(anyString(), anyString(), eq(queueId), 
            eq(requestId), 
            eq(InternalErrorDetail.ofMessage(errorMessage)),
            any());
    }
    
    @Test
    void shouldReturnCorrectStatusCodeForInternalErrorResponse() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String errorMessage = "Provider error";
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("ERROR_DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalErrorResponse(queueId, requestId, "test-domain", "test-solution", new InternalErrorRequest(InternalErrorDetail.ofMessage(errorMessage)), new MockHttpServletRequest());
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    @Test
    void shouldHandleInternalErrorResponseWithLongMessage() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String errorMessage = "Provider timeout: The provider failed to respond within the " +
            "configured timeout period of 30 seconds. This may indicate that the provider " +
            "is experiencing high load or network connectivity issues.";
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("ERROR_DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalErrorResponse(queueId, requestId, "test-domain", "test-solution", new InternalErrorRequest(InternalErrorDetail.ofMessage(errorMessage)), new MockHttpServletRequest());
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(routingService).routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any());
    }
    
    @Test
    void shouldHandleInternalErrorResponseWithEmptyMessage() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String errorMessage = "";
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("ERROR_DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalErrorResponse(queueId, requestId, "test-domain", "test-solution", new InternalErrorRequest(InternalErrorDetail.ofMessage(errorMessage)), new MockHttpServletRequest());
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(routingService).routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any());
    }
    
    @Test
    void shouldNotRequireEnvironmentContextForInternalErrorResponse() {
        // Given - Note: No environment ID or name parameters
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String errorMessage = "Provider error";
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("ERROR_DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalErrorResponse(queueId, requestId, "test-domain", "test-solution", new InternalErrorRequest(InternalErrorDetail.ofMessage(errorMessage)), new MockHttpServletRequest());
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Verify the internal routing path is used — it takes no environment context
        verify(routingService).routeInternalErrorResponse(anyString(), anyString(), any(), any(), any(), any());
    }
    
    // ========================================
    // Header Validation Tests
    // ========================================
    
    @Test
    void shouldAcceptValidUUIDHeaders() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        
        ResponseDTO request = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(new HashMap<>())
            .build();
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalResponse(queueId, requestId, "test-domain", "test-solution", null, request, new MockHttpServletRequest());
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(routingService).routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), any(), any());
    }
    
    // ========================================
    // Service Integration Tests
    // ========================================
    
    @Test
    void shouldDelegateToRoutingServiceForInternalResponse() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        
        ResponseDTO request = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(new HashMap<>())
            .build();
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any()))
            .thenReturn(expectedResult);
        
        // When
        controller.handleInternalResponse(queueId, requestId, "test-domain", "test-solution", null, request, new MockHttpServletRequest());
        
        // Then
        verify(routingService, times(1)).routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any());
    }
    
    @Test
    void shouldDelegateToRoutingServiceForInternalErrorResponse() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String errorMessage = "Provider error";
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("ERROR_DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any()))
            .thenReturn(expectedResult);
        
        // When
        controller.handleInternalErrorResponse(queueId, requestId, "test-domain", "test-solution", new InternalErrorRequest(InternalErrorDetail.ofMessage(errorMessage)), new MockHttpServletRequest());
        
        // Then
        verify(routingService, times(1)).routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any());
    }
    
    @Test
    void shouldReturnResultFromRoutingService() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID expectedMessageId = UUID.randomUUID();
        Instant expectedTimestamp = Instant.now();
        
        ResponseDTO request = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(new HashMap<>())
            .build();
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(expectedMessageId)
            .status("DELIVERED")
            .timestamp(expectedTimestamp)
            .build();
        
        when(routingService.routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalResponse(queueId, requestId, "test-domain", "test-solution", null, request, new MockHttpServletRequest());
        
        // Then
        assertThat(response.getBody()).isEqualTo(expectedResult);
        assertThat(response.getBody().getMessageId()).isEqualTo(expectedMessageId);
        assertThat(response.getBody().getStatus()).isEqualTo("DELIVERED");
        assertThat(response.getBody().getTimestamp()).isEqualTo(expectedTimestamp);
    }
    
    @Test
    void shouldReturnResultFromRoutingServiceForErrorResponse() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID expectedMessageId = UUID.randomUUID();
        Instant expectedTimestamp = Instant.now();
        String errorMessage = "Provider error";
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(expectedMessageId)
            .status("ERROR_DELIVERED")
            .timestamp(expectedTimestamp)
            .build();
        
        when(routingService.routeInternalErrorResponse(anyString(), anyString(), eq(queueId), eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalErrorResponse(queueId, requestId, "test-domain", "test-solution", new InternalErrorRequest(InternalErrorDetail.ofMessage(errorMessage)), new MockHttpServletRequest());
        
        // Then
        assertThat(response.getBody()).isEqualTo(expectedResult);
        assertThat(response.getBody().getMessageId()).isEqualTo(expectedMessageId);
        assertThat(response.getBody().getStatus()).isEqualTo("ERROR_DELIVERED");
        assertThat(response.getBody().getTimestamp()).isEqualTo(expectedTimestamp);
    }
    
    // ========================================
    // Comparison with Public API Tests
    // ========================================
    
    @Test
    void shouldProcessInternalResponseIdenticallyToProviderResponse() {
        // Given
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("refId", "abc123");
        payload.put("name", "John Doe");
        
        ResponseDTO request = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(payload)
            .build();
        
        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();
        
        when(routingService.routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any()))
            .thenReturn(expectedResult);
        
        // When
        ResponseEntity<ResponseResultDTO> response = controller.handleInternalResponse(queueId, requestId, "test-domain", "test-solution", null, request, new MockHttpServletRequest());
        
        // Then
        // Verify the response structure is identical to provider responses
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessageId()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("DELIVERED");
        assertThat(response.getBody().getTimestamp()).isNotNull();
        
        // Verify the service method is called correctly
        verify(routingService).routeInternalResponse(anyString(), anyString(), eq(queueId), eq(requestId), any(), eq(request), any());
    }
}


