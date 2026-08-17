// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for custom exception classes in the Events Connector.
 * <p>
 * Tests verify that exceptions are properly constructed with correct error codes,
 * HTTP status codes, messages, and causes.
 * </p>
 */
class CustomExceptionsTest {
    
    // ========== EventsConnectorException Tests ==========
    
    @Test
    void eventsConnectorException_shouldConstructWithErrorCodeAndMessage() {
        // Given
        String errorCode = "TEST_001";
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "Test error message";
        
        // When
        EventsConnectorException exception = new EventsConnectorException(errorCode, httpStatus, message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getHttpStatus()).isEqualTo(httpStatus);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void eventsConnectorException_shouldConstructWithCause() {
        // Given
        String errorCode = "TEST_001";
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "Test error message";
        Throwable cause = new RuntimeException("Root cause");
        
        // When
        EventsConnectorException exception = new EventsConnectorException(errorCode, httpStatus, message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getHttpStatus()).isEqualTo(httpStatus);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void eventsConnectorException_shouldConstructWithContextInformation() {
        // Given
        String errorCode = "TEST_001";
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "Test error message";
        String requestId = "req-123";
        String environmentId = "env-456";
        
        // When
        EventsConnectorException exception = new EventsConnectorException(
            errorCode, httpStatus, message, requestId, environmentId
        );
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getHttpStatus()).isEqualTo(httpStatus);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getRequestId()).isEqualTo(requestId);
        assertThat(exception.getEnvironmentId()).isEqualTo(environmentId);
    }
    
    // ========== EventValidationException Tests ==========
    
    @Test
    void eventValidationException_shouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Missing serviceName header";
        
        // When
        EventValidationException exception = new EventValidationException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void eventValidationException_shouldConstructWithCause() {
        // Given
        String message = "Invalid JSON payload";
        Throwable cause = new IllegalArgumentException("Malformed JSON");
        
        // When
        EventValidationException exception = new EventValidationException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void eventValidationException_shouldExtendEventsConnectorException() {
        // Given
        EventValidationException exception = new EventValidationException("Test");
        
        // Then
        assertThat(exception).isInstanceOf(EventsConnectorException.class);
    }
    
    // ========== SubscriptionServiceException Tests ==========
    
    @Test
    void subscriptionServiceException_shouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Subscription Service unavailable";
        
        // When
        SubscriptionServiceException exception = new SubscriptionServiceException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("SUBSCRIPTION_SERVICE_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void subscriptionServiceException_shouldConstructWithCause() {
        // Given
        String message = "Failed to query subscriptions";
        Throwable cause = new RuntimeException("Connection timeout");
        
        // When
        SubscriptionServiceException exception = new SubscriptionServiceException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("SUBSCRIPTION_SERVICE_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void subscriptionServiceException_shouldExtendEventsConnectorException() {
        // Given
        SubscriptionServiceException exception = new SubscriptionServiceException("Test");
        
        // Then
        assertThat(exception).isInstanceOf(EventsConnectorException.class);
    }
    
    // ========== QueueServiceException Tests ==========
    
    @Test
    void queueServiceException_shouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Queue Service unavailable";
        
        // When
        QueueServiceException exception = new QueueServiceException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("QUEUE_SERVICE_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void queueServiceException_shouldConstructWithCause() {
        // Given
        String message = "Failed to enqueue event";
        Throwable cause = new RuntimeException("Connection refused");
        
        // When
        QueueServiceException exception = new QueueServiceException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("QUEUE_SERVICE_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void queueServiceException_shouldExtendEventsConnectorException() {
        // Given
        QueueServiceException exception = new QueueServiceException("Test");
        
        // Then
        assertThat(exception).isInstanceOf(EventsConnectorException.class);
    }
    
    // ========== Exception Hierarchy Tests ==========
    
    @Test
    void allCustomExceptions_shouldExtendEventsConnectorException() {
        // Given & When
        EventValidationException validationException = new EventValidationException("Test");
        SubscriptionServiceException subscriptionException = new SubscriptionServiceException("Test");
        QueueServiceException queueException = new QueueServiceException("Test");
        
        // Then
        assertThat(validationException).isInstanceOf(EventsConnectorException.class);
        assertThat(subscriptionException).isInstanceOf(EventsConnectorException.class);
        assertThat(queueException).isInstanceOf(EventsConnectorException.class);
    }
    
    @Test
    void allCustomExceptions_shouldHaveUniqueErrorCodes() {
        // Given & When
        EventValidationException validationException = new EventValidationException("Test");
        SubscriptionServiceException subscriptionException = new SubscriptionServiceException("Test");
        QueueServiceException queueException = new QueueServiceException("Test");
        
        // Then
        assertThat(validationException.getErrorCode()).isEqualTo("VALIDATION_001");
        assertThat(subscriptionException.getErrorCode()).isEqualTo("SUBSCRIPTION_SERVICE_001");
        assertThat(queueException.getErrorCode()).isEqualTo("QUEUE_SERVICE_001");
        
        // Verify all error codes are unique
        assertThat(validationException.getErrorCode())
            .isNotEqualTo(subscriptionException.getErrorCode())
            .isNotEqualTo(queueException.getErrorCode());
    }
    
    @Test
    void serviceExceptions_shouldHaveServiceUnavailableStatus() {
        // Given & When
        SubscriptionServiceException subscriptionException = new SubscriptionServiceException("Test");
        QueueServiceException queueException = new QueueServiceException("Test");
        
        // Then
        assertThat(subscriptionException.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(queueException.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    @Test
    void validationException_shouldHaveBadRequestStatus() {
        // Given & When
        EventValidationException exception = new EventValidationException("Test");
        
        // Then
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
