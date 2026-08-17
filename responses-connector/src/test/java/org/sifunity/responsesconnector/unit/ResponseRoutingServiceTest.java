// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.TracingUtils;
import org.sifunity.responsesconnector.client.QueueServiceClient;
import org.sifunity.responsesconnector.dto.InternalErrorDetail;
import org.sifunity.responsesconnector.dto.InternalErrorRequest;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.responsesconnector.exception.QueueNotFoundException;
import org.sifunity.responsesconnector.exception.QueueServiceUnavailableException;
import org.sifunity.responsesconnector.service.LegacyTagLookupService;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.sifunity.responsesconnector.util.MessageBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ResponseRoutingService.
 * 
 * Tests cover:
 * - Successful response routing (routeResponse)
 * - Internal response routing (routeInternalResponse)
 * - Internal error response routing (routeInternalErrorResponse)
 * - Queue Service integration (mocked)
 * - Error handling (queue not found, service unavailable)
 * - ResponseResultDTO construction
 * - Logging verification
 * 
 * Requirements: 3.1, 3.2, 3.6, 3.7, 5.1, 5.2, 5.3
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ResponseRoutingService Unit Tests")
class ResponseRoutingServiceTest {
    
    @Mock
    private QueueServiceClient queueClient;
    
    @Mock
    private MessageBuilder messageBuilder;
    
    @Mock
    private TracingUtils tracingUtils;

    @Mock
    private org.sifunity.responsesconnector.service.ResponseDestinationValidator destinationValidator;
    
    @Mock
    private LegacyTagLookupService legacyTagLookupService;
    
    private ResponseRoutingService routingService;
    
    private UUID environmentId;
    private String environmentName;
    private UUID queueId;
    private String requestId;
    private UUID messageId;
    
    @BeforeEach
    void setUp() {
        routingService = new ResponseRoutingService(queueClient, messageBuilder, tracingUtils, legacyTagLookupService, destinationValidator);
        
        // Configure TracingUtils mock to execute the operation directly
        lenient().when(tracingUtils.traceOperation(any(), any(), any(java.util.function.Supplier.class)))
            .thenAnswer(invocation -> {
                java.util.function.Supplier<?> operation = invocation.getArgument(2);
                return operation.get();
            });
        
        // Default: queue owner lookup returns a UUID, legacy check returns false
        lenient().when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class)))
            .thenReturn(UUID.randomUUID());
        lenient().when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class)))
            .thenReturn(false);
        
        environmentId = UUID.randomUUID();
        environmentName = "TestEnvironment";
        queueId = UUID.randomUUID();
        requestId = UUID.randomUUID().toString();
        messageId = UUID.randomUUID();
    }

    // ========================================
    // routeResponse Tests
    // ========================================
    
    @Nested
    @DisplayName("routeResponse Tests")
    class RouteResponseTests {
        
        @Test
        @DisplayName("Should successfully route response to consumer queue")
        void shouldSuccessfullyRouteResponse() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345", "name", "John Doe"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .timestamp(Instant.now())
                .messageType(MessageType.RESPONSE)
                .serviceName("StudentPersonal")
                .serviceType(ServiceType.OBJECT)
                .requestId(requestId)
                .sourceId(environmentId)
                .payload(response.getPayload())
                .build();
            
            when(messageBuilder.buildResponseEnvelope(environmentId, environmentName, requestId, response, Collections.emptyMap()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class)))
                .thenReturn(messageId);
            
            // Act
            ResponseResultDTO result = routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal");
            
            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo(messageId);
            assertThat(result.getQueueId()).isEqualTo(queueId);
            assertThat(result.getStatus()).isEqualTo("DELIVERED");
            assertThat(result.getTimestamp()).isNotNull();
            
            // Verify interactions
            verify(messageBuilder).buildResponseEnvelope(environmentId, environmentName, requestId, response, Collections.emptyMap());
            verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        }
        
        @Test
        @DisplayName("Should call MessageBuilder with correct parameters")
        void shouldCallMessageBuilderWithCorrectParameters() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);
            
            // Act
            routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal");
            
            // Assert
            verify(messageBuilder).buildResponseEnvelope(
                eq(environmentId),
                eq(environmentName),
                eq(requestId),
                eq(response),
                eq(Collections.emptyMap())
            );
        }
        
        @Test
        @DisplayName("Should call QueueServiceClient with correct parameters")
        void shouldCallQueueServiceClientWithCorrectParameters() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);
            
            // Act
            routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal");
            
            // Assert
            verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        }
        
        @Test
        @DisplayName("Should throw QueueNotFoundException when queue does not exist")
        void shouldThrowQueueNotFoundExceptionWhenQueueDoesNotExist() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenThrow(new QueueNotFoundException("Queue not found: " + queueId));
            
            // Act & Assert
            assertThatThrownBy(() -> routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal"))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessageContaining("Queue not found");
            
            verify(messageBuilder).buildResponseEnvelope(environmentId, environmentName, requestId, response, Collections.emptyMap());
            verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        }
        
        @Test
        @DisplayName("Should throw QueueServiceUnavailableException when Queue Service is unavailable")
        void shouldThrowQueueServiceUnavailableExceptionWhenServiceUnavailable() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenThrow(new QueueServiceUnavailableException("Queue Service unavailable"));
            
            // Act & Assert
            assertThatThrownBy(() -> routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal"))
                .isInstanceOf(QueueServiceUnavailableException.class)
                .hasMessageContaining("Queue Service unavailable");
            
            verify(messageBuilder).buildResponseEnvelope(environmentId, environmentName, requestId, response, Collections.emptyMap());
            verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        }
        
        @Test
        @DisplayName("Should handle complex payload in response")
        void shouldHandleComplexPayloadInResponse() {
            // Arrange
            Map<String, Object> complexPayload = Map.of(
                "student", Map.of(
                    "id", "12345",
                    "name", "John Doe",
                    "grades", Map.of("math", 95, "science", 88)
                )
            );
            
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(complexPayload)
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .payload(complexPayload)
                .build();
            
            when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);
            
            // Act
            ResponseResultDTO result = routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal");
            
            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo(messageId);
            assertThat(result.getStatus()).isEqualTo("DELIVERED");
        }
        
        @Test
        @DisplayName("Should handle FUNCTIONAL service type")
        void shouldHandleFunctionalServiceType() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("GetStudentGrades")
                .serviceType("FUNCTIONAL")
                .payload(Map.of("result", "success"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .serviceName("GetStudentGrades")
                .serviceType(ServiceType.FUNCTIONAL)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);
            
            // Act
            ResponseResultDTO result = routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal");
            
            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("DELIVERED");
        }
    }

    // ========================================
    // routeInternalResponse Tests
    // ========================================
    
    @Nested
    @DisplayName("routeInternalResponse Tests")
    class RouteInternalResponseTests {
        
        @Test
        @DisplayName("Should successfully route internal response to consumer queue")
        void shouldSuccessfullyRouteInternalResponse() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345", "name", "John Doe"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .timestamp(Instant.now())
                .messageType(MessageType.RESPONSE)
                .serviceName("StudentPersonal")
                .serviceType(ServiceType.OBJECT)
                .requestId(requestId)
                .payload(response.getPayload())
                .build();
            
            when(messageBuilder.buildInternalResponseEnvelope(requestId, response))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class)))
                .thenReturn(messageId);
            
            // Act
            ResponseResultDTO result = routingService.routeInternalResponse("test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap());
            
            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo(messageId);
            assertThat(result.getStatus()).isEqualTo("DELIVERED");
            assertThat(result.getTimestamp()).isNotNull();
            // Note: queueId is not included in internal response result
            
            // Verify interactions
            verify(messageBuilder).buildInternalResponseEnvelope(requestId, response);
            verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        }
        
        @Test
        @DisplayName("Should call MessageBuilder without environment context for internal response")
        void shouldCallMessageBuilderWithoutEnvironmentContext() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildInternalResponseEnvelope(any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);
            
            // Act
            routingService.routeInternalResponse("test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap());
            
            // Assert
            verify(messageBuilder).buildInternalResponseEnvelope(eq(requestId), eq(response));
            // Verify that buildResponseEnvelope (with environment context) was NOT called
            verify(messageBuilder, never()).buildResponseEnvelope(any(), any(), any(), any(), any());
        }
        
        @Test
        @DisplayName("Should throw QueueNotFoundException when queue does not exist for internal response")
        void shouldThrowQueueNotFoundExceptionForInternalResponse() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildInternalResponseEnvelope(any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenThrow(new QueueNotFoundException("Queue not found: " + queueId));
            
            // Act & Assert
            assertThatThrownBy(() -> routingService.routeInternalResponse("test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap()))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessageContaining("Queue not found");
            
            verify(messageBuilder).buildInternalResponseEnvelope(requestId, response);
            verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        }
        
        @Test
        @DisplayName("Should throw QueueServiceUnavailableException when Queue Service is unavailable for internal response")
        void shouldThrowQueueServiceUnavailableExceptionForInternalResponse() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildInternalResponseEnvelope(any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenThrow(new QueueServiceUnavailableException("Queue Service unavailable"));
            
            // Act & Assert
            assertThatThrownBy(() -> routingService.routeInternalResponse("test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap()))
                .isInstanceOf(QueueServiceUnavailableException.class)
                .hasMessageContaining("Queue Service unavailable");
        }
        
        @Test
        @DisplayName("Should handle internal response from Requests Connector")
        void shouldHandleInternalResponseFromRequestsConnector() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345", "name", "Jane Smith"))
                .build();
            
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .payload(response.getPayload())
                .build();
            
            when(messageBuilder.buildInternalResponseEnvelope(any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);
            
            // Act
            ResponseResultDTO result = routingService.routeInternalResponse("test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap());
            
            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo(messageId);
            assertThat(result.getStatus()).isEqualTo("DELIVERED");
        }
    }


    // ========================================
    // routeInternalErrorResponse Tests
    // ========================================
    
    @Nested
    @DisplayName("routeInternalErrorResponse Tests")
    class RouteInternalErrorResponseTests {
        
        @Test
        @DisplayName("Should successfully route internal error response to consumer queue")
        void shouldSuccessfullyRouteInternalErrorResponse() {
            // Arrange
            String errorMessage = "Provider timeout";
            
            SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                500,
                "INTERNAL_ERROR",
                errorMessage,
                null,
                "BROKER",
                Instant.now(),
                null,
                null
            );
            
            SifMessageEnvelope<SifErrorResponse> envelope = SifMessageEnvelope.<SifErrorResponse>builder()
                .messageId(UUID.randomUUID())
                .timestamp(Instant.now())
                .messageType(MessageType.RESPONSE)
                .serviceName("InternalError")
                .serviceType(ServiceType.UTILITY)
                .requestId(requestId)
                .payload(error)
                .build();
            
            when(messageBuilder.buildInternalErrorEnvelope(requestId, InternalErrorDetail.ofMessage(errorMessage)))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class)))
                .thenReturn(messageId);
            
            // Act
            ResponseResultDTO result = routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage(errorMessage), Collections.emptyMap());
            
            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo(messageId);
            assertThat(result.getStatus()).isEqualTo("ERROR_DELIVERED");
            assertThat(result.getTimestamp()).isNotNull();
            
            // Verify interactions
            verify(messageBuilder).buildInternalErrorEnvelope(requestId, InternalErrorDetail.ofMessage(errorMessage));
            verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        }
        
        @Test
        @DisplayName("Should call MessageBuilder with correct parameters for internal error")
        void shouldCallMessageBuilderWithCorrectParametersForInternalError() {
            // Arrange
            String errorMessage = "Provider unavailable";
            
            SifMessageEnvelope<SifErrorResponse> envelope = SifMessageEnvelope.<SifErrorResponse>builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildInternalErrorEnvelope(any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);
            
            // Act
            routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage(errorMessage), Collections.emptyMap());
            
            // Assert
            verify(messageBuilder).buildInternalErrorEnvelope(eq(requestId), eq(InternalErrorDetail.ofMessage(errorMessage)));
        }
        
        @Test
        @DisplayName("Should throw QueueNotFoundException when queue does not exist for internal error")
        void shouldThrowQueueNotFoundExceptionForInternalError() {
            // Arrange
            String errorMessage = "Provider timeout";
            
            SifMessageEnvelope<SifErrorResponse> envelope = SifMessageEnvelope.<SifErrorResponse>builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildInternalErrorEnvelope(any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenThrow(new QueueNotFoundException("Queue not found: " + queueId));
            
            // Act & Assert
            assertThatThrownBy(() -> routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage(errorMessage), Collections.emptyMap()))
                .isInstanceOf(QueueNotFoundException.class)
                .hasMessageContaining("Queue not found");
            
            verify(messageBuilder).buildInternalErrorEnvelope(requestId, InternalErrorDetail.ofMessage(errorMessage));
            verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        }
        
        @Test
        @DisplayName("Should throw QueueServiceUnavailableException when Queue Service is unavailable for internal error")
        void shouldThrowQueueServiceUnavailableExceptionForInternalError() {
            // Arrange
            String errorMessage = "Provider timeout";
            
            SifMessageEnvelope<SifErrorResponse> envelope = SifMessageEnvelope.<SifErrorResponse>builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildInternalErrorEnvelope(any(), any()))
                .thenReturn(envelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenThrow(new QueueServiceUnavailableException("Queue Service unavailable"));
            
            // Act & Assert
            assertThatThrownBy(() -> routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage(errorMessage), Collections.emptyMap()))
                .isInstanceOf(QueueServiceUnavailableException.class)
                .hasMessageContaining("Queue Service unavailable");
        }
        
        @Test
        @DisplayName("Should handle different error messages")
        void shouldHandleDifferentErrorMessages() {
            // Arrange
            String errorMessage1 = "Provider timeout";
            String errorMessage2 = "Provider unavailable";
            
            SifMessageEnvelope<SifErrorResponse> envelope1 = SifMessageEnvelope.<SifErrorResponse>builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            SifMessageEnvelope<SifErrorResponse> envelope2 = SifMessageEnvelope.<SifErrorResponse>builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();
            
            when(messageBuilder.buildInternalErrorEnvelope(requestId, InternalErrorDetail.ofMessage(errorMessage1)))
                .thenReturn(envelope1);
            when(messageBuilder.buildInternalErrorEnvelope(requestId, InternalErrorDetail.ofMessage(errorMessage2)))
                .thenReturn(envelope2);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);
            
            // Act
            ResponseResultDTO result1 = routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage(errorMessage1), Collections.emptyMap());
            ResponseResultDTO result2 = routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage(errorMessage2), Collections.emptyMap());
            
            // Assert
            assertThat(result1.getStatus()).isEqualTo("ERROR_DELIVERED");
            assertThat(result2.getStatus()).isEqualTo("ERROR_DELIVERED");
            
            verify(messageBuilder).buildInternalErrorEnvelope(requestId, InternalErrorDetail.ofMessage(errorMessage1));
            verify(messageBuilder).buildInternalErrorEnvelope(requestId, InternalErrorDetail.ofMessage(errorMessage2));
        }
    }


    // ========================================
    // Cross-Method Tests
    // ========================================
    
    @Nested
    @DisplayName("Cross-Method Tests")
    class CrossMethodTests {
        
        @Test
        @DisplayName("All routing methods should preserve requestId for correlation")
        void allRoutingMethodsShouldPreserveRequestId() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> responseEnvelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();

            SifMessageEnvelope<SifErrorResponse> errorEnvelope = SifMessageEnvelope.<SifErrorResponse>builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();

            when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(responseEnvelope);
            when(messageBuilder.buildInternalResponseEnvelope(any(), any()))
                .thenReturn(responseEnvelope);
            when(messageBuilder.buildInternalErrorEnvelope(any(), any()))
                .thenReturn(errorEnvelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);

            // Act
            routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal");
            routingService.routeInternalResponse("test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap());
            routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage("Error message"), Collections.emptyMap());

            // Assert - Verify all methods were called with the same requestId
            verify(messageBuilder).buildResponseEnvelope(environmentId, environmentName, requestId, response, Collections.emptyMap());
            verify(messageBuilder).buildInternalResponseEnvelope(requestId, response);
            verify(messageBuilder).buildInternalErrorEnvelope(requestId, InternalErrorDetail.ofMessage("Error message"));
        }
        
        @Test
        @DisplayName("All routing methods should call QueueServiceClient to enqueue messages")
        void allRoutingMethodsShouldCallQueueServiceClient() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> responseEnvelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();

            SifMessageEnvelope<SifErrorResponse> errorEnvelope = SifMessageEnvelope.<SifErrorResponse>builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();

            when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(responseEnvelope);
            when(messageBuilder.buildInternalResponseEnvelope(any(), any()))
                .thenReturn(responseEnvelope);
            when(messageBuilder.buildInternalErrorEnvelope(any(), any()))
                .thenReturn(errorEnvelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);

            // Act
            routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal");
            routingService.routeInternalResponse("test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap());
            routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage("Error message"), Collections.emptyMap());

            // Assert - Verify QueueServiceClient was called once per routing method
            verify(queueClient, times(3)).enqueueMessage(anyString(), anyString(), eq(queueId), any());
        }
        
        @Test
        @DisplayName("All routing methods should return ResponseResultDTO with messageId")
        void allRoutingMethodsShouldReturnResponseResultDTOWithMessageId() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> responseEnvelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();

            SifMessageEnvelope<SifErrorResponse> errorEnvelope = SifMessageEnvelope.<SifErrorResponse>builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();

            when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(responseEnvelope);
            when(messageBuilder.buildInternalResponseEnvelope(any(), any()))
                .thenReturn(responseEnvelope);
            when(messageBuilder.buildInternalErrorEnvelope(any(), any()))
                .thenReturn(errorEnvelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);

            // Act
            ResponseResultDTO result1 = routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal");
            ResponseResultDTO result3 = routingService.routeInternalResponse("test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap());
            ResponseResultDTO result4 = routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage("Error message"), Collections.emptyMap());

            // Assert
            assertThat(result1.getMessageId()).isEqualTo(messageId);
            assertThat(result3.getMessageId()).isEqualTo(messageId);
            assertThat(result4.getMessageId()).isEqualTo(messageId);
        }
        
        @Test
        @DisplayName("Success methods should return DELIVERED status, error methods should return ERROR_DELIVERED")
        void shouldReturnCorrectStatusForEachMethod() {
            // Arrange
            ResponseDTO response = ResponseDTO.builder()
                .serviceName("StudentPersonal")
                .serviceType("OBJECT")
                .payload(Map.of("id", "12345"))
                .build();
            
            SifMessageEnvelope<Object> responseEnvelope = SifMessageEnvelope.builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();

            SifMessageEnvelope<SifErrorResponse> errorEnvelope = SifMessageEnvelope.<SifErrorResponse>builder()
                .messageId(UUID.randomUUID())
                .messageType(MessageType.RESPONSE)
                .requestId(requestId)
                .build();

            when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(responseEnvelope);
            when(messageBuilder.buildInternalResponseEnvelope(any(), any()))
                .thenReturn(responseEnvelope);
            when(messageBuilder.buildInternalErrorEnvelope(any(), any()))
                .thenReturn(errorEnvelope);
            when(queueClient.enqueueMessage(anyString(), anyString(), any(), any()))
                .thenReturn(messageId);

            // Act
            ResponseResultDTO result1 = routingService.routeResponse("test-domain", "test-solution", environmentId, environmentName, queueId, requestId, response, Collections.emptyMap(), "test-zone", "DEFAULT", "/StudentPersonal");
            ResponseResultDTO result3 = routingService.routeInternalResponse("test-domain", "test-solution", queueId, requestId, null, response, Collections.emptyMap());
            ResponseResultDTO result4 = routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage("Error message"), Collections.emptyMap());

            // Assert
            assertThat(result1.getStatus()).isEqualTo("DELIVERED");
            assertThat(result3.getStatus()).isEqualTo("DELIVERED");
            assertThat(result4.getStatus()).isEqualTo("ERROR_DELIVERED");
        }
    }
}

