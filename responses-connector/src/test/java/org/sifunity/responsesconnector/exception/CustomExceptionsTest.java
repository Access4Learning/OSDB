// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for custom exception classes in the Responses Connector.
 * <p>
 * Tests verify that exceptions are properly constructed with correct error codes,
 * HTTP status codes, messages, and causes. These exceptions are used for error
 * handling during response routing operations.
 * </p>
 * <p>
 * Requirements: 10.1, 10.2, 10.3, 10.4
 * </p>
 */
class CustomExceptionsTest {
    
    // ========== QueueNotFoundException Tests ==========
    
    @Test
    void queueNotFoundException_shouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Queue not found: 12345678-1234-1234-1234-123456789012";
        
        // When
        QueueNotFoundException exception = new QueueNotFoundException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("QUEUE_NOT_FOUND");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void queueNotFoundException_shouldConstructWithCause() {
        // Given
        String message = "Queue not found: 12345678-1234-1234-1234-123456789012";
        Throwable cause = new RuntimeException("WebClient 404 error");
        
        // When
        QueueNotFoundException exception = new QueueNotFoundException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("QUEUE_NOT_FOUND");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void queueNotFoundException_shouldExtendSifBrokerException() {
        // Given
        QueueNotFoundException exception = new QueueNotFoundException("Test");
        
        // Then
        assertThat(exception).isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
    }
    
    @Test
    void queueNotFoundException_shouldBeRuntimeException() {
        // Given
        QueueNotFoundException exception = new QueueNotFoundException("Test");
        
        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    // ========== QueueServiceUnavailableException Tests ==========
    
    @Test
    void queueServiceUnavailableException_shouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Queue Service is currently unavailable";
        
        // When
        QueueServiceUnavailableException exception = new QueueServiceUnavailableException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void queueServiceUnavailableException_shouldConstructWithCause() {
        // Given
        String message = "Queue Service unavailable after 3 retries";
        Throwable cause = new RuntimeException("Connection timeout");
        
        // When
        QueueServiceUnavailableException exception = new QueueServiceUnavailableException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void queueServiceUnavailableException_shouldExtendSifBrokerException() {
        // Given
        QueueServiceUnavailableException exception = new QueueServiceUnavailableException("Test");
        
        // Then
        assertThat(exception).isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
    }
    
    @Test
    void queueServiceUnavailableException_shouldBeRuntimeException() {
        // Given
        QueueServiceUnavailableException exception = new QueueServiceUnavailableException("Test");
        
        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    // ========== ResponseRoutingException Tests ==========
    
    @Test
    void responseRoutingException_shouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Failed to route response to consumer queue";
        
        // When
        ResponseRoutingException exception = new ResponseRoutingException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("ROUTING_ERROR");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void responseRoutingException_shouldConstructWithCause() {
        // Given
        String message = "Unexpected error during message envelope construction";
        Throwable cause = new IllegalArgumentException("Invalid payload format");
        
        // When
        ResponseRoutingException exception = new ResponseRoutingException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("ROUTING_ERROR");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void responseRoutingException_shouldExtendSifBrokerException() {
        // Given
        ResponseRoutingException exception = new ResponseRoutingException("Test");
        
        // Then
        assertThat(exception).isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
    }
    
    @Test
    void responseRoutingException_shouldBeRuntimeException() {
        // Given
        ResponseRoutingException exception = new ResponseRoutingException("Test");
        
        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    // ========== Exception Hierarchy Tests ==========
    
    @Test
    void allCustomExceptions_shouldExtendSifBrokerException() {
        // Given & When
        QueueNotFoundException queueNotFoundException = new QueueNotFoundException("Test");
        QueueServiceUnavailableException serviceException = new QueueServiceUnavailableException("Test");
        ResponseRoutingException routingException = new ResponseRoutingException("Test");
        
        // Then
        assertThat(queueNotFoundException).isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
        assertThat(serviceException).isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
        assertThat(routingException).isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
    }
    
    @Test
    void allCustomExceptions_shouldBeRuntimeExceptions() {
        // Given & When
        QueueNotFoundException queueNotFoundException = new QueueNotFoundException("Test");
        QueueServiceUnavailableException serviceException = new QueueServiceUnavailableException("Test");
        ResponseRoutingException routingException = new ResponseRoutingException("Test");
        
        // Then
        assertThat(queueNotFoundException).isInstanceOf(RuntimeException.class);
        assertThat(serviceException).isInstanceOf(RuntimeException.class);
        assertThat(routingException).isInstanceOf(RuntimeException.class);
    }
    
    @Test
    void allCustomExceptions_shouldHaveUniqueErrorCodes() {
        // Given & When
        QueueNotFoundException queueNotFoundException = new QueueNotFoundException("Test");
        QueueServiceUnavailableException serviceException = new QueueServiceUnavailableException("Test");
        ResponseRoutingException routingException = new ResponseRoutingException("Test");
        
        // Then
        assertThat(queueNotFoundException.getErrorCode()).isEqualTo("QUEUE_NOT_FOUND");
        assertThat(serviceException.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(routingException.getErrorCode()).isEqualTo("ROUTING_ERROR");
        
        // Verify all error codes are unique
        assertThat(queueNotFoundException.getErrorCode())
            .isNotEqualTo(serviceException.getErrorCode())
            .isNotEqualTo(routingException.getErrorCode());
    }
    
    @Test
    void allCustomExceptions_shouldHaveAppropriateHttpStatuses() {
        // Given & When
        QueueNotFoundException queueNotFoundException = new QueueNotFoundException("Test");
        QueueServiceUnavailableException serviceException = new QueueServiceUnavailableException("Test");
        ResponseRoutingException routingException = new ResponseRoutingException("Test");
        
        // Then
        assertThat(queueNotFoundException.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(serviceException.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(routingException.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    @Test
    void queueNotFoundException_shouldHaveNotFoundStatus() {
        // Given & When
        QueueNotFoundException exception = new QueueNotFoundException("Test");
        
        // Then
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getHttpStatus().value()).isEqualTo(404);
    }
    
    @Test
    void queueServiceUnavailableException_shouldHaveServiceUnavailableStatus() {
        // Given & When
        QueueServiceUnavailableException exception = new QueueServiceUnavailableException("Test");
        
        // Then
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getHttpStatus().value()).isEqualTo(503);
    }
    
    @Test
    void responseRoutingException_shouldHaveInternalServerErrorStatus() {
        // Given & When
        ResponseRoutingException exception = new ResponseRoutingException("Test");
        
        // Then
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exception.getHttpStatus().value()).isEqualTo(500);
    }
    
    // ========== Message Preservation Tests ==========
    
    @Test
    void exceptions_shouldPreserveDetailedMessages() {
        // Given
        String queueMessage = "Queue not found: 12345678-1234-1234-1234-123456789012";
        String serviceMessage = "Queue Service unavailable after 3 retry attempts";
        String routingMessage = "Failed to construct message envelope for requestId: abc-123";
        
        // When
        QueueNotFoundException queueException = new QueueNotFoundException(queueMessage);
        QueueServiceUnavailableException serviceException = new QueueServiceUnavailableException(serviceMessage);
        ResponseRoutingException routingException = new ResponseRoutingException(routingMessage);
        
        // Then
        assertThat(queueException.getMessage()).isEqualTo(queueMessage);
        assertThat(serviceException.getMessage()).isEqualTo(serviceMessage);
        assertThat(routingException.getMessage()).isEqualTo(routingMessage);
    }
    
    @Test
    void exceptions_shouldPreserveCauseChain() {
        // Given
        RuntimeException rootCause = new RuntimeException("Root cause");
        IllegalArgumentException intermediateCause = new IllegalArgumentException("Intermediate", rootCause);
        
        // When
        QueueNotFoundException exception = new QueueNotFoundException("Test", intermediateCause);
        
        // Then
        assertThat(exception.getCause()).isEqualTo(intermediateCause);
        assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }
    
    // ========== Use Case Scenario Tests ==========
    
    @Test
    void queueNotFoundException_shouldRepresentQueueNotFoundScenario() {
        // Given - Simulating Queue Service returning 404
        String queueId = "12345678-1234-1234-1234-123456789012";
        String message = "Queue not found: " + queueId;
        
        // When
        QueueNotFoundException exception = new QueueNotFoundException(message);
        
        // Then
        assertThat(exception.getMessage()).contains(queueId);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getErrorCode()).isEqualTo("QUEUE_NOT_FOUND");
    }
    
    @Test
    void queueServiceUnavailableException_shouldRepresentServiceDownScenario() {
        // Given - Simulating Queue Service being down after retries
        String message = "Queue Service unavailable after 3 retry attempts";
        Throwable cause = new RuntimeException("Connection refused");
        
        // When
        QueueServiceUnavailableException exception = new QueueServiceUnavailableException(message, cause);
        
        // Then
        assertThat(exception.getMessage()).contains("unavailable");
        assertThat(exception.getMessage()).contains("retry");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getCause()).isNotNull();
    }
    
    @Test
    void responseRoutingException_shouldRepresentInternalErrorScenario() {
        // Given - Simulating unexpected error during routing
        String message = "Unexpected error during message envelope construction";
        Throwable cause = new IllegalArgumentException("Invalid payload format");
        
        // When
        ResponseRoutingException exception = new ResponseRoutingException(message, cause);
        
        // Then
        assertThat(exception.getMessage()).contains("Unexpected error");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
    }
}
