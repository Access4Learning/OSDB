// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.exception.*;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.queueservice.exception.GlobalExceptionHandler;
import org.sifunity.broker.queueservice.storage.StorageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler.
 * Tests that each exception type returns correct status code and error response format.
 * 
 * **Validates: Requirements 13.1, 13.2, 13.3, 13.4, 13.5**
 */
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler exceptionHandler;
    
    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }
    
    /**
     * Test: QueueNotFoundException returns 404 Not Found
     * 
     * **Validates: Requirements 13.2**
     */
    @Test
    void testHandleQueueNotFoundException_Returns404() {
        // Given: QueueNotFoundException
        QueueNotFoundException exception = new QueueNotFoundException("Queue not found");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleQueueNotFoundException(exception);
        
        // Then: Returns 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).isEqualTo("Queue not found");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        assertThat(response.getBody().error().timestamp()).isNotNull();
    }

    /**
     * Test: MethodArgumentNotValidException returns 400 Bad Request
     * 
     * **Validates: Requirements 13.1**
     */
    @Test
    void testHandleValidationException_Returns400() {
        // Given: MethodArgumentNotValidException with field errors
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        FieldError fieldError1 = new FieldError("request", "name", "Name is required");
        FieldError fieldError2 = new FieldError("request", "ownerId", "Owner ID is required");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleValidationException(exception);
        
        // Then: Returns 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("VAL_001");
        assertThat(response.getBody().error().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().error().description()).contains("name: Name is required");
        assertThat(response.getBody().error().description()).contains("ownerId: Owner ID is required");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
    }
    
    /**
     * Test: InvalidRequestException returns 400 Bad Request
     * 
     * **Validates: Requirements 13.1**
     */
    @Test
    void testHandleInvalidRequestException_Returns400() {
        // Given: InvalidRequestException
        InvalidRequestException exception = new InvalidRequestException("Invalid request parameters");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleInvalidRequestException(exception);
        
        // Then: Returns 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).isEqualTo("Invalid request parameters");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
    }
    
    /**
     * Test: StorageException returns 500 Internal Server Error
     * 
     * **Validates: Requirements 13.4**
     */
    @Test
    void testHandleStorageException_Returns500() {
        // Given: StorageException
        StorageException exception = new StorageException("Failed to store message");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleStorageException(exception);
        
        // Then: Returns 500 Internal Server Error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("STORAGE_001");
        assertThat(response.getBody().error().message()).isEqualTo("Storage operation failed");
        assertThat(response.getBody().error().description()).isEqualTo("Failed to store message");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
    }

    /**
     * Test: ServiceUnavailableException returns 503 Service Unavailable
     * 
     * **Validates: Requirements 13.3, 13.4**
     */
    @Test
    void testHandleServiceUnavailableException_Returns503() {
        // Given: ServiceUnavailableException
        ServiceUnavailableException exception = new ServiceUnavailableException("Redis", "Connection failed");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleServiceUnavailableException(exception);
        
        // Then: Returns 503 Service Unavailable
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).contains("Redis");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
    }
    
    /**
     * Test: IllegalArgumentException returns 400 Bad Request
     * 
     * **Validates: Requirements 13.1**
     */
    @Test
    void testHandleIllegalArgumentException_Returns400() {
        // Given: IllegalArgumentException
        IllegalArgumentException exception = new IllegalArgumentException("Invalid argument provided");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleIllegalArgumentException(exception);
        
        // Then: Returns 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("VAL_002");
        assertThat(response.getBody().error().message()).isEqualTo("Invalid argument");
        assertThat(response.getBody().error().description()).isEqualTo("Invalid argument provided");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
    }
    
    /**
     * Test: Generic Exception returns 500 Internal Server Error
     * 
     * **Validates: Requirements 13.5**
     */
    @Test
    void testHandleGenericException_Returns500() {
        // Given: Generic Exception
        Exception exception = new RuntimeException("Unexpected error occurred");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleGenericException(exception);
        
        // Then: Returns 500 Internal Server Error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("SYS_001");
        assertThat(response.getBody().error().message()).isEqualTo("Internal server error");
        assertThat(response.getBody().error().description()).contains("unexpected error occurred");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
    }
    
    /**
     * Test: Error response format matches SIF specification
     * 
     * **Validates: Requirements 13.1, 13.2, 13.3, 13.4, 13.5**
     */
    @Test
    void testErrorResponseFormat_MatchesSifSpecification() {
        // Given: QueueNotFoundException
        QueueNotFoundException exception = new QueueNotFoundException("Queue not found");
        
        // When: Handling exception
        ResponseEntity<SifErrorResponseEnvelope> response = exceptionHandler.handleQueueNotFoundException(exception);
        
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
     * **Validates: Requirements 13.1, 13.2, 13.3, 13.4, 13.5**
     */
    @Test
    void testAllExceptionHandlers_ReturnNonNullResponseBody() {
        // Test QueueNotFoundException
        ResponseEntity<SifErrorResponseEnvelope> response1 = exceptionHandler.handleQueueNotFoundException(
            new QueueNotFoundException("test")
        );
        assertThat(response1.getBody()).isNotNull();
        
        // Test InvalidRequestException
        ResponseEntity<SifErrorResponseEnvelope> response2 = exceptionHandler.handleInvalidRequestException(
            new InvalidRequestException("test")
        );
        assertThat(response2.getBody()).isNotNull();
        
        // Test StorageException
        ResponseEntity<SifErrorResponseEnvelope> response3 = exceptionHandler.handleStorageException(
            new StorageException("test")
        );
        assertThat(response3.getBody()).isNotNull();
        
        // Test ServiceUnavailableException
        ResponseEntity<SifErrorResponseEnvelope> response4 = exceptionHandler.handleServiceUnavailableException(
            new ServiceUnavailableException("TestService", "Service is down")
        );
        assertThat(response4.getBody()).isNotNull();
        
        // Test IllegalArgumentException
        ResponseEntity<SifErrorResponseEnvelope> response5 = exceptionHandler.handleIllegalArgumentException(
            new IllegalArgumentException("test")
        );
        assertThat(response5.getBody()).isNotNull();
        
        // Test generic Exception
        ResponseEntity<SifErrorResponseEnvelope> response6 = exceptionHandler.handleGenericException(
            new RuntimeException("test")
        );
        assertThat(response6.getBody()).isNotNull();
    }
}
