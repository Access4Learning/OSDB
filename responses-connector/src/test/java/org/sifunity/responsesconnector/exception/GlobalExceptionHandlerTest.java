// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
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
 * 
 * <p>Tests that each exception type returns the correct HTTP status code and
 * error response format compliant with SIF 3.7 specification.</p>
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>QueueNotFoundException returns HTTP 404 Not Found</li>
 *   <li>QueueServiceUnavailableException returns HTTP 503 Service Unavailable</li>
 *   <li>MethodArgumentNotValidException returns HTTP 400 Bad Request with field-level validation details</li>
 *   <li>ResponseRoutingException returns HTTP 500 Internal Server Error</li>
 *   <li>Generic Exception returns HTTP 500 Internal Server Error</li>
 *   <li>All error responses conform to SIF 3.7 error format</li>
 * </ul>
 * 
 * <p><b>Validates Requirements:</b> 10.1, 10.2, 10.3, 10.4, 10.5, 10.6</p>
 */
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler exceptionHandler;
    
    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }
    
    /**
     * Test: QueueNotFoundException returns HTTP 404 Not Found.
     * 
     * <p>Verifies that when a queue is not found, the handler returns:
     * <ul>
     *   <li>HTTP 404 Not Found status</li>
     *   <li>Error code "QUEUE_NOT_FOUND"</li>
     *   <li>Appropriate error message</li>
     *   <li>SIF-compliant error format</li>
     * </ul>
     * 
     * <p><b>Validates Requirements:</b> 3.7, 9.3, 10.4</p>
     */
    @Test
    void testHandleQueueNotFoundException_Returns404() {
        // Given: QueueNotFoundException with specific queue ID
        String queueId = "550e8400-e29b-41d4-a716-446655440000";
        QueueNotFoundException exception = new QueueNotFoundException(
            "Queue not found: " + queueId
        );
        
        // When: Handling the exception
        ResponseEntity<SifErrorResponseEnvelope> response = 
            exceptionHandler.handleQueueNotFoundException(exception);
        
        // Then: Returns HTTP 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        
        // And: Response body contains correct error details
        SifErrorResponse error = response.getBody().error();
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isEqualTo("QUEUE_NOT_FOUND");
        assertThat(error.message()).contains("Queue not found");
        assertThat(error.message()).contains(queueId);
        assertThat(error.description()).isEqualTo(
            "The specified queue does not exist. Verify the queueId is correct."
        );
        assertThat(error.scope()).isEqualTo("BROKER");
        assertThat(error.timestamp()).isNotNull();
    }
    
    /**
     * Test: QueueServiceUnavailableException returns HTTP 503 Service Unavailable.
     * 
     * <p>Verifies that when the Queue Service is unavailable, the handler returns:
     * <ul>
     *   <li>HTTP 503 Service Unavailable status</li>
     *   <li>Error code "SERVICE_UNAVAILABLE"</li>
     *   <li>Appropriate error message</li>
     *   <li>SIF-compliant error format</li>
     * </ul>
     * 
     * <p><b>Validates Requirements:</b> 3.6, 9.4, 10.1, 12.4</p>
     */
    @Test
    void testHandleQueueServiceUnavailableException_Returns503() {
        // Given: QueueServiceUnavailableException
        QueueServiceUnavailableException exception = new QueueServiceUnavailableException(
            "Queue Service is currently unavailable"
        );
        
        // When: Handling the exception
        ResponseEntity<SifErrorResponseEnvelope> response = 
            exceptionHandler.handleQueueServiceUnavailableException(exception);
        
        // Then: Returns HTTP 503 Service Unavailable
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        
        // And: Response body contains correct error details
        SifErrorResponse error = response.getBody().error();
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(error.message()).contains("Queue Service is currently unavailable");
        assertThat(error.description()).isEqualTo(
            "The Queue Service is currently unavailable. Please retry the request later."
        );
        assertThat(error.scope()).isEqualTo("BROKER");
        assertThat(error.timestamp()).isNotNull();
    }
    
    /**
     * Test: QueueServiceUnavailableException with cause returns HTTP 503.
     * 
     * <p>Verifies that when the Queue Service is unavailable due to an underlying
     * cause (e.g., network error), the handler properly handles the exception chain.</p>
     * 
     * <p><b>Validates Requirements:</b> 3.6, 9.4, 10.1</p>
     */
    @Test
    void testHandleQueueServiceUnavailableException_WithCause_Returns503() {
        // Given: QueueServiceUnavailableException with underlying cause
        RuntimeException cause = new RuntimeException("Connection refused");
        QueueServiceUnavailableException exception = new QueueServiceUnavailableException(
            "Failed to connect to Queue Service",
            cause
        );
        
        // When: Handling the exception
        ResponseEntity<SifErrorResponseEnvelope> response = 
            exceptionHandler.handleQueueServiceUnavailableException(exception);
        
        // Then: Returns HTTP 503 Service Unavailable
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        
        // And: Response body contains correct error details
        SifErrorResponse error = response.getBody().error();
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(error.message()).contains("Failed to connect to Queue Service");
        assertThat(error.scope()).isEqualTo("BROKER");
    }
    
    /**
     * Test: MethodArgumentNotValidException returns HTTP 400 Bad Request with field-level validation details.
     * 
     * <p>Verifies that when request validation fails, the handler returns:
     * <ul>
     *   <li>HTTP 400 Bad Request status</li>
     *   <li>Error code "VALIDATION_ERROR"</li>
     *   <li>Field-level validation error details</li>
     *   <li>SIF-compliant error format</li>
     * </ul>
     * 
     * <p><b>Validates Requirements:</b> 1.5, 7.4, 10.4</p>
     */
    @Test
    void testHandleMethodArgumentNotValidException_Returns400WithFieldErrors() {
        // Given: MethodArgumentNotValidException with multiple field errors
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        FieldError fieldError1 = new FieldError("responseDTO", "serviceName", "Service name is required");
        FieldError fieldError2 = new FieldError("responseDTO", "serviceType", "Service type is required");
        FieldError fieldError3 = new FieldError("responseDTO", "payload", "Payload is required");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2, fieldError3));
        
        // When: Handling the exception
        ResponseEntity<SifErrorResponseEnvelope> response = 
            exceptionHandler.handleMethodArgumentNotValidException(exception);
        
        // Then: Returns HTTP 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        // And: Response body contains correct error details
        SifErrorResponse error = response.getBody().error();
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.message()).isEqualTo("Request validation failed");
        
        // And: Description contains all field-level validation errors
        assertThat(error.description()).contains("serviceName: Service name is required");
        assertThat(error.description()).contains("serviceType: Service type is required");
        assertThat(error.description()).contains("payload: Payload is required");
        assertThat(error.scope()).isEqualTo("BROKER");
        assertThat(error.timestamp()).isNotNull();
    }
    
    /**
     * Test: MethodArgumentNotValidException with single field error returns HTTP 400.
     * 
     * <p>Verifies that validation errors with a single field are properly formatted.</p>
     * 
     * <p><b>Validates Requirements:</b> 1.5, 7.4, 10.4</p>
     */
    @Test
    void testHandleMethodArgumentNotValidException_SingleFieldError_Returns400() {
        // Given: MethodArgumentNotValidException with single field error
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        FieldError fieldError = new FieldError("responseDTO", "serviceName", "Service name is required");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        
        // When: Handling the exception
        ResponseEntity<SifErrorResponseEnvelope> response = 
            exceptionHandler.handleMethodArgumentNotValidException(exception);
        
        // Then: Returns HTTP 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        // And: Response body contains correct error details
        SifErrorResponse error = response.getBody().error();
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.description()).isEqualTo("serviceName: Service name is required");
    }
    
    /**
     * Test: ResponseRoutingException returns HTTP 500 Internal Server Error.
     * 
     * <p>Verifies that when a response routing error occurs, the handler returns:
     * <ul>
     *   <li>HTTP 500 Internal Server Error status</li>
     *   <li>Error code "ROUTING_ERROR"</li>
     *   <li>Appropriate error message</li>
     *   <li>SIF-compliant error format</li>
     * </ul>
     * 
     * <p><b>Validates Requirements:</b> 10.3, 10.5</p>
     */
    @Test
    void testHandleResponseRoutingException_Returns500() {
        // Given: ResponseRoutingException
        ResponseRoutingException exception = new ResponseRoutingException(
            "Failed to route response to consumer queue"
        );
        
        // When: Handling the exception
        ResponseEntity<SifErrorResponseEnvelope> response = 
            exceptionHandler.handleResponseRoutingException(exception);
        
        // Then: Returns HTTP 500 Internal Server Error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // And: Response body contains correct error details
        SifErrorResponse error = response.getBody().error();
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isEqualTo("ROUTING_ERROR");
        assertThat(error.message()).contains("Failed to route response to consumer queue");
        assertThat(error.description()).isEqualTo(
            "An error occurred while routing the response. Please contact support if the problem persists."
        );
        assertThat(error.scope()).isEqualTo("BROKER");
        assertThat(error.timestamp()).isNotNull();
    }
    
    /**
     * Test: ResponseRoutingException with cause returns HTTP 500.
     * 
     * <p>Verifies that routing exceptions with underlying causes are properly handled.</p>
     * 
     * <p><b>Validates Requirements:</b> 10.3, 10.5</p>
     */
    @Test
    void testHandleResponseRoutingException_WithCause_Returns500() {
        // Given: ResponseRoutingException with underlying cause
        RuntimeException cause = new RuntimeException("Serialization failed");
        ResponseRoutingException exception = new ResponseRoutingException(
            "Failed to construct message envelope",
            cause
        );
        
        // When: Handling the exception
        ResponseEntity<SifErrorResponseEnvelope> response = 
            exceptionHandler.handleResponseRoutingException(exception);
        
        // Then: Returns HTTP 500 Internal Server Error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // And: Response body contains correct error details
        SifErrorResponse error = response.getBody().error();
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isEqualTo("ROUTING_ERROR");
        assertThat(error.message()).contains("Failed to construct message envelope");
        assertThat(error.scope()).isEqualTo("BROKER");
    }
    
    /**
     * Test: Generic Exception returns HTTP 500 Internal Server Error.
     * 
     * <p>Verifies that unexpected exceptions are caught and handled with:
     * <ul>
     *   <li>HTTP 500 Internal Server Error status</li>
     *   <li>Error code "INTERNAL_ERROR"</li>
     *   <li>Generic error message (no sensitive details exposed)</li>
     *   <li>SIF-compliant error format</li>
     * </ul>
     * 
     * <p><b>Validates Requirements:</b> 10.3, 10.5, 10.6</p>
     */
    @Test
    void testHandleGenericException_Returns500() {
        // Given: Generic RuntimeException
        RuntimeException exception = new RuntimeException("Unexpected error occurred");
        
        // When: Handling the exception
        ResponseEntity<SifErrorResponseEnvelope> response = 
            exceptionHandler.handleGenericException(exception);
        
        // Then: Returns HTTP 500 Internal Server Error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // And: Response body contains correct error details
        SifErrorResponse error = response.getBody().error();
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.message()).isEqualTo("An unexpected error occurred");
        assertThat(error.description()).isEqualTo(
            "An internal server error occurred. Please contact support if the problem persists."
        );
        assertThat(error.scope()).isEqualTo("BROKER");
        assertThat(error.timestamp()).isNotNull();
    }
    
    /**
     * Test: Generic Exception with NullPointerException returns HTTP 500.
     * 
     * <p>Verifies that even null pointer exceptions are properly handled.</p>
     * 
     * <p><b>Validates Requirements:</b> 10.3, 10.5, 10.6</p>
     */
    @Test
    void testHandleGenericException_NullPointerException_Returns500() {
        // Given: NullPointerException
        NullPointerException exception = new NullPointerException("Null value encountered");
        
        // When: Handling the exception
        ResponseEntity<SifErrorResponseEnvelope> response = 
            exceptionHandler.handleGenericException(exception);
        
        // Then: Returns HTTP 500 Internal Server Error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // And: Response body contains correct error details
        SifErrorResponse error = response.getBody().error();
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.scope()).isEqualTo("BROKER");
    }
    
    /**
     * Test: All error responses conform to SIF 3.7 error format.
     * 
     * <p>Verifies that all exception handlers return error responses that conform
     * to the SIF 3.7 specification with required fields:
     * <ul>
     *   <li>code: Error code identifier</li>
     *   <li>message: Human-readable error message</li>
     *   <li>scope: Error scope (always "BROKER" for this service)</li>
     *   <li>timestamp: Error occurrence timestamp</li>
     * </ul>
     * 
     * <p><b>Validates Requirements:</b> 10.5, 10.6</p>
     */
    @Test
    void testAllErrorResponses_ConformToSifFormat() {
        // Test QueueNotFoundException
        ResponseEntity<SifErrorResponseEnvelope> response1 = 
            exceptionHandler.handleQueueNotFoundException(
                new QueueNotFoundException("Queue not found")
            );
        verifySifErrorFormat(response1.getBody().error());
        
        // Test QueueServiceUnavailableException
        ResponseEntity<SifErrorResponseEnvelope> response2 = 
            exceptionHandler.handleQueueServiceUnavailableException(
                new QueueServiceUnavailableException("Service unavailable")
            );
        verifySifErrorFormat(response2.getBody().error());
        
        // Test MethodArgumentNotValidException
        MethodArgumentNotValidException validationException = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(validationException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
            List.of(new FieldError("obj", "field", "error"))
        );
        ResponseEntity<SifErrorResponseEnvelope> response3 = 
            exceptionHandler.handleMethodArgumentNotValidException(validationException);
        verifySifErrorFormat(response3.getBody().error());
        
        // Test ResponseRoutingException
        ResponseEntity<SifErrorResponseEnvelope> response4 = 
            exceptionHandler.handleResponseRoutingException(
                new ResponseRoutingException("Routing error")
            );
        verifySifErrorFormat(response4.getBody().error());
        
        // Test Generic Exception
        ResponseEntity<SifErrorResponseEnvelope> response5 = 
            exceptionHandler.handleGenericException(
                new RuntimeException("Generic error")
            );
        verifySifErrorFormat(response5.getBody().error());
    }
    
    /**
     * Test: All exception handlers return non-null response body.
     * 
     * <p>Verifies that no exception handler returns a null response body,
     * ensuring clients always receive a valid error response.</p>
     * 
     * <p><b>Validates Requirements:</b> 10.1, 10.2, 10.3, 10.4, 10.5, 10.6</p>
     */
    @Test
    void testAllExceptionHandlers_ReturnNonNullResponseBody() {
        // Test QueueNotFoundException
        ResponseEntity<SifErrorResponseEnvelope> response1 = 
            exceptionHandler.handleQueueNotFoundException(
                new QueueNotFoundException("test")
            );
        assertThat(response1.getBody()).isNotNull();
        
        // Test QueueServiceUnavailableException
        ResponseEntity<SifErrorResponseEnvelope> response2 = 
            exceptionHandler.handleQueueServiceUnavailableException(
                new QueueServiceUnavailableException("test")
            );
        assertThat(response2.getBody()).isNotNull();
        
        // Test MethodArgumentNotValidException
        MethodArgumentNotValidException validationException = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(validationException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
            List.of(new FieldError("obj", "field", "error"))
        );
        ResponseEntity<SifErrorResponseEnvelope> response3 = 
            exceptionHandler.handleMethodArgumentNotValidException(validationException);
        assertThat(response3.getBody()).isNotNull();
        
        // Test ResponseRoutingException
        ResponseEntity<SifErrorResponseEnvelope> response4 = 
            exceptionHandler.handleResponseRoutingException(
                new ResponseRoutingException("test")
            );
        assertThat(response4.getBody()).isNotNull();
        
        // Test Generic Exception
        ResponseEntity<SifErrorResponseEnvelope> response5 = 
            exceptionHandler.handleGenericException(
                new RuntimeException("test")
            );
        assertThat(response5.getBody()).isNotNull();
    }
    
    /**
     * Test: Error responses have correct HTTP status codes.
     * 
     * <p>Verifies that each exception type maps to the correct HTTP status code.</p>
     * 
     * <p><b>Validates Requirements:</b> 10.1, 10.2, 10.3, 10.4</p>
     */
    @Test
    void testErrorResponses_HaveCorrectHttpStatusCodes() {
        // QueueNotFoundException -> 404 Not Found
        ResponseEntity<SifErrorResponseEnvelope> response1 = 
            exceptionHandler.handleQueueNotFoundException(
                new QueueNotFoundException("test")
            );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        
        // QueueServiceUnavailableException -> 503 Service Unavailable
        ResponseEntity<SifErrorResponseEnvelope> response2 = 
            exceptionHandler.handleQueueServiceUnavailableException(
                new QueueServiceUnavailableException("test")
            );
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        
        // MethodArgumentNotValidException -> 400 Bad Request
        MethodArgumentNotValidException validationException = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(validationException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
            List.of(new FieldError("obj", "field", "error"))
        );
        ResponseEntity<SifErrorResponseEnvelope> response3 = 
            exceptionHandler.handleMethodArgumentNotValidException(validationException);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        // ResponseRoutingException -> 500 Internal Server Error
        ResponseEntity<SifErrorResponseEnvelope> response4 = 
            exceptionHandler.handleResponseRoutingException(
                new ResponseRoutingException("test")
            );
        assertThat(response4.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // Generic Exception -> 500 Internal Server Error
        ResponseEntity<SifErrorResponseEnvelope> response5 = 
            exceptionHandler.handleGenericException(
                new RuntimeException("test")
            );
        assertThat(response5.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * Test: Error scope is always "BROKER".
     * 
     * <p>Verifies that all error responses have scope set to "BROKER" as required
     * by the SIF specification for broker-originated errors.</p>
     * 
     * <p><b>Validates Requirements:</b> 10.5, 10.6</p>
     */
    @Test
    void testAllErrorResponses_HaveBrokerScope() {
        // Test all exception handlers
        ResponseEntity<SifErrorResponseEnvelope> response1 = 
            exceptionHandler.handleQueueNotFoundException(
                new QueueNotFoundException("test")
            );
        assertThat(response1.getBody().error().scope()).isEqualTo("BROKER");
        
        ResponseEntity<SifErrorResponseEnvelope> response2 = 
            exceptionHandler.handleQueueServiceUnavailableException(
                new QueueServiceUnavailableException("test")
            );
        assertThat(response2.getBody().error().scope()).isEqualTo("BROKER");
        
        MethodArgumentNotValidException validationException = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(validationException.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
            List.of(new FieldError("obj", "field", "error"))
        );
        ResponseEntity<SifErrorResponseEnvelope> response3 = 
            exceptionHandler.handleMethodArgumentNotValidException(validationException);
        assertThat(response3.getBody().error().scope()).isEqualTo("BROKER");
        
        ResponseEntity<SifErrorResponseEnvelope> response4 = 
            exceptionHandler.handleResponseRoutingException(
                new ResponseRoutingException("test")
            );
        assertThat(response4.getBody().error().scope()).isEqualTo("BROKER");
        
        ResponseEntity<SifErrorResponseEnvelope> response5 = 
            exceptionHandler.handleGenericException(
                new RuntimeException("test")
            );
        assertThat(response5.getBody().error().scope()).isEqualTo("BROKER");
    }
    
    /**
     * Helper method to verify SIF error format compliance.
     * 
     * @param error the error response to verify
     */
    private void verifySifErrorFormat(SifErrorResponse error) {
        assertThat(error).isNotNull();
        assertThat(error.subCode()).isNotNull().isNotBlank();
        assertThat(error.message()).isNotNull().isNotBlank();
        assertThat(error.scope()).isEqualTo("BROKER");
        assertThat(error.timestamp()).isNotNull();
    }
}
