// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.sifunity.broker.common.exception.SubscriptionNotFoundException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler.
 * Tests that each exception type returns correct status code and error response format.
 * 
 * **Validates: Requirements 1.7, 2.2, 5.2, 7.3, 10.2, 10.4, 12.1, 12.2, 12.3, 12.4, 12.5**
 */
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler exceptionHandler;
    
    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }
    
    /**
     * Test: SubscriptionNotFoundException returns 404 Not Found
     * 
     * **Validates: Requirements 5.2, 7.3, 12.2**
     */
    @Test
    void testHandleSubscriptionNotFoundException_Returns404() {
        // Given: SubscriptionNotFoundException
        SubscriptionNotFoundException exception = new SubscriptionNotFoundException("Subscription not found");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleSubscriptionNotFound(exception);
        
        // Then: Returns 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).isEqualTo("Subscription not found");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        assertThat(response.getBody().error().timestamp()).isNotNull();
    }
    
    /**
     * Test: QueueNotFoundException returns 404 Not Found
     * 
     * **Validates: Requirements 10.2, 12.2**
     */
    @Test
    void testHandleQueueNotFoundException_Returns404() {
        // Given: QueueNotFoundException
        QueueNotFoundException exception = new QueueNotFoundException("Queue not found");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleQueueNotFound(exception);
        
        // Then: Returns 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).isEqualTo("Queue not found");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        assertThat(response.getBody().error().timestamp()).isNotNull();
    }
    
    /**
     * Test: DuplicateSubscriptionException returns 409 Conflict
     * 
     * **Validates: Requirements 2.2, 12.4**
     */
    @Test
    void testHandleDuplicateSubscriptionException_Returns409() {
        // Given: DuplicateSubscriptionException
        DuplicateSubscriptionException exception = new DuplicateSubscriptionException(
            "Subscription already exists for this queue and topic"
        );
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleDuplicateSubscription(exception);
        
        // Then: Returns 409 Conflict
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).contains("already exists");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        assertThat(response.getBody().error().timestamp()).isNotNull();
    }
    
    /**
     * Test: MethodArgumentNotValidException returns 400 Bad Request
     * 
     * **Validates: Requirements 1.7, 12.1**
     */
    @Test
    void testHandleValidationErrors_Returns400() {
        // Given: MethodArgumentNotValidException with field errors
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        FieldError fieldError1 = new FieldError("request", "queueId", "Queue ID is required");
        FieldError fieldError2 = new FieldError("request", "serviceName", "Service name is required");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.toString()).thenReturn("queueId: Queue ID is required, serviceName: Service name is required");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleValidationErrors(exception);
        
        // Then: Returns 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("VAL_001");
        assertThat(response.getBody().error().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().error().description()).contains("queueId");
        assertThat(response.getBody().error().description()).contains("serviceName");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
    }
    
    /**
     * Test: MissingServletRequestParameterException returns 400 Bad Request
     * 
     * **Validates: Requirements 1.7, 12.1**
     */
    @Test
    void testHandleMissingParameter_Returns400() {
        // Given: MissingServletRequestParameterException
        MissingServletRequestParameterException exception = 
            new MissingServletRequestParameterException("serviceName", "String");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleMissingParameter(exception);
        
        // Then: Returns 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("VAL_001");
        assertThat(response.getBody().error().message()).isEqualTo("Missing required parameter");
        assertThat(response.getBody().error().description()).contains("serviceName");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        assertThat(response.getBody().error().field()).isEqualTo("serviceName");
    }
    
    /**
     * Test: ServiceUnavailableException returns 503 Service Unavailable
     * 
     * **Validates: Requirements 10.4, 12.3**
     */
    @Test
    void testHandleServiceUnavailableException_Returns503() {
        // Given: ServiceUnavailableException
        ServiceUnavailableException exception = new ServiceUnavailableException(
            "Queue Service", 
            "Failed to validate queue existence"
        );
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleServiceUnavailable(exception);
        
        // Then: Returns 503 Service Unavailable
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).contains("Queue Service");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        assertThat(response.getBody().error().timestamp()).isNotNull();
    }
    
    /**
     * Test: Generic Exception returns 500 Internal Server Error
     * 
     * **Validates: Requirements 12.5**
     */
    @Test
    void testHandleUnexpectedError_Returns500() {
        // Given: Generic Exception
        Exception exception = new RuntimeException("Unexpected database error");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleUnexpectedError(exception);
        
        // Then: Returns 500 Internal Server Error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().error().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().error().description()).contains("Unexpected database error");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
    }
    
    /**
     * Test: Error response format matches SIF specification
     * 
     * **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5**
     */
    @Test
    void testErrorResponseFormat_MatchesSifSpecification() {
        // Given: SubscriptionNotFoundException
        SubscriptionNotFoundException exception = new SubscriptionNotFoundException("Subscription not found");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleSubscriptionNotFound(exception);
        
        // Then: Response format matches SIF specification
        SifErrorResponse error = response.getBody().error();
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isNotNull();
        assertThat(error.message()).isNotNull();
        assertThat(error.scope()).isEqualTo("BROKER");
        assertThat(error.timestamp()).isNotNull();
    }
    
    /**
     * Test: All exception handlers return non-null response body
     * 
     * **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5**
     */
    @Test
    void testAllExceptionHandlers_ReturnNonNullResponseBody() {
        // Test SubscriptionNotFoundException
        ResponseEntity<SifErrorResponseEnvelope> response1 = exceptionHandler.handleSubscriptionNotFound(
            new SubscriptionNotFoundException("test")
        );
        assertThat(response1.getBody()).isNotNull();
        
        // Test QueueNotFoundException
        ResponseEntity<SifErrorResponseEnvelope> response2 = exceptionHandler.handleQueueNotFound(
            new QueueNotFoundException("test")
        );
        assertThat(response2.getBody()).isNotNull();
        
        // Test DuplicateSubscriptionException
        ResponseEntity<SifErrorResponseEnvelope> response3 = exceptionHandler.handleDuplicateSubscription(
            new DuplicateSubscriptionException("test")
        );
        assertThat(response3.getBody()).isNotNull();
        
        // Test ServiceUnavailableException
        ResponseEntity<SifErrorResponseEnvelope> response4 = exceptionHandler.handleServiceUnavailable(
            new ServiceUnavailableException("TestService", "Service is down")
        );
        assertThat(response4.getBody()).isNotNull();
        
        // Test generic Exception
        ResponseEntity<SifErrorResponseEnvelope> response5 = exceptionHandler.handleUnexpectedError(
            new RuntimeException("test")
        );
        assertThat(response5.getBody()).isNotNull();
    }
    
    /**
     * Test: SubscriptionNotFoundException with UUID in message
     * 
     * **Validates: Requirements 5.2, 12.2**
     */
    @Test
    void testHandleSubscriptionNotFoundException_WithUUID() {
        // Given: SubscriptionNotFoundException with UUID
        String subscriptionId = "550e8400-e29b-41d4-a716-446655440000";
        SubscriptionNotFoundException exception = new SubscriptionNotFoundException(
            "Subscription not found: " + subscriptionId
        );
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleSubscriptionNotFound(exception);
        
        // Then: Returns 404 with UUID in message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).contains(subscriptionId);
    }
    
    /**
     * Test: QueueNotFoundException with descriptive message
     * 
     * **Validates: Requirements 10.2, 12.2**
     */
    @Test
    void testHandleQueueNotFoundException_WithDescriptiveMessage() {
        // Given: QueueNotFoundException with descriptive message
        String queueId = "550e8400-e29b-41d4-a716-446655440001";
        QueueNotFoundException exception = new QueueNotFoundException("Queue not found: " + queueId);
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleQueueNotFound(exception);
        
        // Then: Returns 404 with descriptive message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).contains("Queue not found");
        assertThat(response.getBody().error().message()).contains(queueId);
    }
    
    /**
     * Test: DuplicateSubscriptionException with existing subscription details
     * 
     * **Validates: Requirements 2.2, 12.4**
     */
    @Test
    void testHandleDuplicateSubscriptionException_WithDetails() {
        // Given: DuplicateSubscriptionException with details
        DuplicateSubscriptionException exception = new DuplicateSubscriptionException(
            "Subscription already exists for queue 550e8400-e29b-41d4-a716-446655440001 " +
            "and service StudentPersonal in zone district-123"
        );
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleDuplicateSubscription(exception);
        
        // Then: Returns 409 with details
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).contains("already exists");
        assertThat(response.getBody().error().message()).contains("StudentPersonal");
    }
    
    /**
     * Test: ServiceUnavailableException for database unavailability
     * 
     * **Validates: Requirements 12.3**
     */
    @Test
    void testHandleServiceUnavailableException_DatabaseUnavailable() {
        // Given: ServiceUnavailableException for database
        ServiceUnavailableException exception = new ServiceUnavailableException(
            "PostgreSQL", 
            "Database connection timeout"
        );
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleServiceUnavailable(exception);
        
        // Then: Returns 503 for database unavailability
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).contains("PostgreSQL");
    }
    
    /**
     * Test: ServiceUnavailableException for Queue Service unavailability
     * 
     * **Validates: Requirements 10.4, 12.3**
     */
    @Test
    void testHandleServiceUnavailableException_QueueServiceUnavailable() {
        // Given: ServiceUnavailableException for Queue Service
        ServiceUnavailableException exception = new ServiceUnavailableException(
            "Queue Service", 
            "Connection refused"
        );
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleServiceUnavailable(exception);
        
        // Then: Returns 503 for Queue Service unavailability
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).contains("Queue Service");
    }
    
    /**
     * Test: Validation error with multiple field errors
     * 
     * **Validates: Requirements 1.7, 12.1**
     */
    @Test
    void testHandleValidationErrors_MultipleFields() {
        // Given: MethodArgumentNotValidException with multiple field errors
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.toString()).thenReturn(
            "queueId: must not be null, " +
            "serviceName: must not be blank, " +
            "zoneId: must not be blank, " +
            "type: must not be null"
        );
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleValidationErrors(exception);
        
        // Then: Returns 400 with all field errors
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("VAL_001");
        assertThat(response.getBody().error().description()).contains("queueId");
        assertThat(response.getBody().error().description()).contains("serviceName");
        assertThat(response.getBody().error().description()).contains("zoneId");
        assertThat(response.getBody().error().description()).contains("type");
    }
    
    /**
     * Test: Unexpected error with null message
     * 
     * **Validates: Requirements 12.5**
     */
    @Test
    void testHandleUnexpectedError_WithNullMessage() {
        // Given: Exception with null message
        Exception exception = new NullPointerException();
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleUnexpectedError(exception);
        
        // Then: Returns 500 with generic message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().error().message()).isEqualTo("An unexpected error occurred");
    }
    
    /**
     * Test: All error responses include timestamp
     * 
     * **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5**
     */
    @Test
    void testAllErrorResponses_IncludeTimestamp() {
        // Test various exceptions
        ResponseEntity<SifErrorResponseEnvelope> response1 = exceptionHandler.handleSubscriptionNotFound(
            new SubscriptionNotFoundException("test")
        );
        assertThat(response1.getBody().error().timestamp()).isNotNull();
        
        ResponseEntity<SifErrorResponseEnvelope> response2 = exceptionHandler.handleQueueNotFound(
            new QueueNotFoundException("test")
        );
        assertThat(response2.getBody().error().timestamp()).isNotNull();
        
        ResponseEntity<SifErrorResponseEnvelope> response3 = exceptionHandler.handleDuplicateSubscription(
            new DuplicateSubscriptionException("test")
        );
        assertThat(response3.getBody().error().timestamp()).isNotNull();
        
        ResponseEntity<SifErrorResponseEnvelope> response4 = exceptionHandler.handleServiceUnavailable(
            new ServiceUnavailableException("TestService", "test")
        );
        assertThat(response4.getBody().error().timestamp()).isNotNull();
        
        ResponseEntity<SifErrorResponseEnvelope> response5 = exceptionHandler.handleUnexpectedError(
            new RuntimeException("test")
        );
        assertThat(response5.getBody().error().timestamp()).isNotNull();
    }
    
    /**
     * Test: All error responses include BROKER scope
     * 
     * **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5**
     */
    @Test
    void testAllErrorResponses_IncludeBrokerScope() {
        // Test various exceptions
        ResponseEntity<SifErrorResponseEnvelope> response1 = exceptionHandler.handleSubscriptionNotFound(
            new SubscriptionNotFoundException("test")
        );
        assertThat(response1.getBody().error().scope()).isEqualTo("BROKER");
        
        ResponseEntity<SifErrorResponseEnvelope> response2 = exceptionHandler.handleQueueNotFound(
            new QueueNotFoundException("test")
        );
        assertThat(response2.getBody().error().scope()).isEqualTo("BROKER");
        
        ResponseEntity<SifErrorResponseEnvelope> response3 = exceptionHandler.handleDuplicateSubscription(
            new DuplicateSubscriptionException("test")
        );
        assertThat(response3.getBody().error().scope()).isEqualTo("BROKER");
        
        MethodArgumentNotValidException validationException = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(validationException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.toString()).thenReturn("test");
        ResponseEntity<SifErrorResponseEnvelope> response4 = exceptionHandler.handleValidationErrors(validationException);
        assertThat(response4.getBody().error().scope()).isEqualTo("BROKER");
        
        ResponseEntity<SifErrorResponseEnvelope> response5 = exceptionHandler.handleServiceUnavailable(
            new ServiceUnavailableException("TestService", "test")
        );
        assertThat(response5.getBody().error().scope()).isEqualTo("BROKER");
        
        ResponseEntity<SifErrorResponseEnvelope> response6 = exceptionHandler.handleUnexpectedError(
            new RuntimeException("test")
        );
        assertThat(response6.getBody().error().scope()).isEqualTo("BROKER");
    }
}
