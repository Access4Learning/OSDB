// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler.
 * <p>
 * Tests exception handling for all exception types and verifies that
 * error responses conform to SIF 3.7 format.
 * </p>
 * <p>
 * <strong>Requirements:</strong> 10
 * </p>
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }
    
    @Nested
    @DisplayName("EventValidationException Handling")
    class EventValidationExceptionHandling {
        
        @Test
        @DisplayName("Should handle EventValidationException with 400 status")
        void shouldHandleEventValidationException() {
            // Given
            EventValidationException ex = new EventValidationException(
                "serviceName header is required"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleEventValidationException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("VALIDATION_001");
            assertThat(response.getBody().error().message()).isEqualTo("serviceName header is required");
            assertThat(response.getBody().error().description()).isEqualTo("Invalid event data provided");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle EventValidationException for missing zoneId")
        void shouldHandleEventValidationExceptionForMissingZoneId() {
            // Given
            EventValidationException ex = new EventValidationException(
                "zoneId header is required"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleEventValidationException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("VALIDATION_001");
            assertThat(response.getBody().error().message()).isEqualTo("zoneId header is required");
            assertThat(response.getBody().error().description()).isEqualTo("Invalid event data provided");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
        
        @Test
        @DisplayName("Should handle EventValidationException for invalid JSON payload")
        void shouldHandleEventValidationExceptionForInvalidJson() {
            // Given
            EventValidationException ex = new EventValidationException(
                "Event payload must be valid JSON"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleEventValidationException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("VALIDATION_001");
            assertThat(response.getBody().error().message()).isEqualTo("Event payload must be valid JSON");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
        
        @Test
        @DisplayName("Should handle EventValidationException with cause")
        void shouldHandleEventValidationExceptionWithCause() {
            // Given
            Throwable cause = new IllegalArgumentException("Invalid format");
            EventValidationException ex = new EventValidationException(
                "Validation failed",
                cause
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleEventValidationException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("VALIDATION_001");
            assertThat(response.getBody().error().message()).isEqualTo("Validation failed");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
    }
    
    @Nested
    @DisplayName("SubscriptionServiceException Handling")
    class SubscriptionServiceExceptionHandling {
        
        @Test
        @DisplayName("Should handle SubscriptionServiceException with 503 status")
        void shouldHandleSubscriptionServiceException() {
            // Given
            SubscriptionServiceException ex = new SubscriptionServiceException(
                "Subscription Service is unavailable"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleSubscriptionServiceException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("SUBSCRIPTION_SERVICE_001");
            assertThat(response.getBody().error().message()).isEqualTo("Subscription Service is unavailable");
            assertThat(response.getBody().error().description())
                .isEqualTo("Unable to query subscriptions for event distribution");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle SubscriptionServiceException with timeout cause")
        void shouldHandleSubscriptionServiceExceptionWithTimeout() {
            // Given
            Throwable cause = new java.util.concurrent.TimeoutException("Request timed out");
            SubscriptionServiceException ex = new SubscriptionServiceException(
                "Failed to query subscriptions: timeout",
                cause
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleSubscriptionServiceException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("SUBSCRIPTION_SERVICE_001");
            assertThat(response.getBody().error().message())
                .isEqualTo("Failed to query subscriptions: timeout");
            assertThat(response.getBody().error().description())
                .isEqualTo("Unable to query subscriptions for event distribution");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
        
        @Test
        @DisplayName("Should handle SubscriptionServiceException with connection error")
        void shouldHandleSubscriptionServiceExceptionWithConnectionError() {
            // Given
            Throwable cause = new java.net.ConnectException("Connection refused");
            SubscriptionServiceException ex = new SubscriptionServiceException(
                "Failed to connect to Subscription Service",
                cause
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleSubscriptionServiceException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("SUBSCRIPTION_SERVICE_001");
            assertThat(response.getBody().error().message())
                .isEqualTo("Failed to connect to Subscription Service");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
    }
    
    @Nested
    @DisplayName("QueueServiceException Handling")
    class QueueServiceExceptionHandling {
        
        @Test
        @DisplayName("Should handle QueueServiceException with 503 status")
        void shouldHandleQueueServiceException() {
            // Given
            QueueServiceException ex = new QueueServiceException(
                "Queue Service is unavailable"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleQueueServiceException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("QUEUE_SERVICE_001");
            assertThat(response.getBody().error().message()).isEqualTo("Queue Service is unavailable");
            assertThat(response.getBody().error().description())
                .isEqualTo("Unable to enqueue event to subscriber queues");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle QueueServiceException with timeout cause")
        void shouldHandleQueueServiceExceptionWithTimeout() {
            // Given
            Throwable cause = new java.util.concurrent.TimeoutException("Request timed out");
            QueueServiceException ex = new QueueServiceException(
                "Failed to enqueue event: timeout",
                cause
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleQueueServiceException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("QUEUE_SERVICE_001");
            assertThat(response.getBody().error().message()).isEqualTo("Failed to enqueue event: timeout");
            assertThat(response.getBody().error().description())
                .isEqualTo("Unable to enqueue event to subscriber queues");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
        
        @Test
        @DisplayName("Should handle QueueServiceException for all queues failing")
        void shouldHandleQueueServiceExceptionForAllQueuesFailing() {
            // Given
            QueueServiceException ex = new QueueServiceException(
                "All queue enqueue operations failed"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleQueueServiceException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("QUEUE_SERVICE_001");
            assertThat(response.getBody().error().message())
                .isEqualTo("All queue enqueue operations failed");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
        
        @Test
        @DisplayName("Should handle QueueServiceException with connection error")
        void shouldHandleQueueServiceExceptionWithConnectionError() {
            // Given
            Throwable cause = new java.net.ConnectException("Connection refused");
            QueueServiceException ex = new QueueServiceException(
                "Failed to connect to Queue Service",
                cause
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleQueueServiceException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("QUEUE_SERVICE_001");
            assertThat(response.getBody().error().message())
                .isEqualTo("Failed to connect to Queue Service");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
    }
    
    @Nested
    @DisplayName("Generic Exception Handling")
    class GenericExceptionHandling {
        
        @Test
        @DisplayName("Should handle unexpected Exception with 500 status")
        void shouldHandleUnexpectedException() {
            // Given
            Exception ex = new RuntimeException("Unexpected error occurred");
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleGenericException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("INTERNAL_ERROR_001");
            assertThat(response.getBody().error().message()).isEqualTo("Internal server error");
            assertThat(response.getBody().error().description())
                .contains("An unexpected error occurred");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle NullPointerException with 500 status")
        void shouldHandleNullPointerException() {
            // Given
            Exception ex = new NullPointerException("Null value encountered");
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleGenericException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("INTERNAL_ERROR_001");
            assertThat(response.getBody().error().message()).isEqualTo("Internal server error");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
        
        @Test
        @DisplayName("Should handle IllegalStateException with 500 status")
        void shouldHandleIllegalStateException() {
            // Given
            Exception ex = new IllegalStateException("Invalid state");
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleGenericException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("INTERNAL_ERROR_001");
            assertThat(response.getBody().error().message()).isEqualTo("Internal server error");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
        
        @Test
        @DisplayName("Should handle generic Exception with nested cause")
        void shouldHandleExceptionWithNestedCause() {
            // Given
            Throwable rootCause = new IllegalArgumentException("Root cause");
            Throwable cause = new RuntimeException("Intermediate cause", rootCause);
            Exception ex = new Exception("Top level error", cause);
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleGenericException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("INTERNAL_ERROR_001");
            assertThat(response.getBody().error().message()).isEqualTo("Internal server error");
            assertThat(response.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
    }
    
    @Nested
    @DisplayName("Error Response Format Validation")
    class ErrorResponseFormatValidation {
        
        @Test
        @DisplayName("All error responses should conform to SIF 3.7 format")
        void allErrorResponsesShouldConformToSifFormat() {
            // Test various exception types and verify SIF format
            
            // 1. EventValidationException
            EventValidationException validationEx = new EventValidationException(
                "Validation error"
            );
            ResponseEntity<SifErrorResponseEnvelope> response1 = 
                handler.handleEventValidationException(validationEx);
            verifySifFormat(response1.getBody().error());
            
            // 2. SubscriptionServiceException
            SubscriptionServiceException subscriptionEx = new SubscriptionServiceException(
                "Subscription Service error"
            );
            ResponseEntity<SifErrorResponseEnvelope> response2 = 
                handler.handleSubscriptionServiceException(subscriptionEx);
            verifySifFormat(response2.getBody().error());
            
            // 3. QueueServiceException
            QueueServiceException queueEx = new QueueServiceException(
                "Queue Service error"
            );
            ResponseEntity<SifErrorResponseEnvelope> response3 = 
                handler.handleQueueServiceException(queueEx);
            verifySifFormat(response3.getBody().error());
            
            // 4. Generic Exception
            Exception genericEx = new RuntimeException("Unexpected");
            ResponseEntity<SifErrorResponseEnvelope> response4 = 
                handler.handleGenericException(genericEx);
            verifySifFormat(response4.getBody().error());
        }
        
        @Test
        @DisplayName("Error responses should have correct scope")
        void errorResponsesShouldHaveCorrectScope() {
            // All errors from Events Connector should have scope "EVENTS_CONNECTOR"
            
            EventValidationException validationEx = new EventValidationException("Error");
            ResponseEntity<SifErrorResponseEnvelope> response1 = 
                handler.handleEventValidationException(validationEx);
            assertThat(response1.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
            
            SubscriptionServiceException subscriptionEx = new SubscriptionServiceException("Error");
            ResponseEntity<SifErrorResponseEnvelope> response2 = 
                handler.handleSubscriptionServiceException(subscriptionEx);
            assertThat(response2.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
            
            QueueServiceException queueEx = new QueueServiceException("Error");
            ResponseEntity<SifErrorResponseEnvelope> response3 = 
                handler.handleQueueServiceException(queueEx);
            assertThat(response3.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
            
            Exception genericEx = new RuntimeException("Error");
            ResponseEntity<SifErrorResponseEnvelope> response4 = 
                handler.handleGenericException(genericEx);
            assertThat(response4.getBody().error().scope()).isEqualTo("EVENTS_CONNECTOR");
        }
        
        @Test
        @DisplayName("Error responses should have correct HTTP status codes")
        void errorResponsesShouldHaveCorrectStatusCodes() {
            // Verify HTTP status codes match exception types
            
            EventValidationException validationEx = new EventValidationException("Error");
            ResponseEntity<SifErrorResponseEnvelope> response1 = 
                handler.handleEventValidationException(validationEx);
            assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            
            SubscriptionServiceException subscriptionEx = new SubscriptionServiceException("Error");
            ResponseEntity<SifErrorResponseEnvelope> response2 = 
                handler.handleSubscriptionServiceException(subscriptionEx);
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            
            QueueServiceException queueEx = new QueueServiceException("Error");
            ResponseEntity<SifErrorResponseEnvelope> response3 = 
                handler.handleQueueServiceException(queueEx);
            assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            
            Exception genericEx = new RuntimeException("Error");
            ResponseEntity<SifErrorResponseEnvelope> response4 = 
                handler.handleGenericException(genericEx);
            assertThat(response4.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        
        private void verifySifFormat(SifErrorResponse error) {
            assertThat(error).isNotNull();
            assertThat(error.subCode()).isNotNull().isNotBlank();
            assertThat(error.message()).isNotNull().isNotBlank();
            assertThat(error.scope()).isEqualTo("EVENTS_CONNECTOR");
            assertThat(error.timestamp()).isNotNull();
        }
    }
}
