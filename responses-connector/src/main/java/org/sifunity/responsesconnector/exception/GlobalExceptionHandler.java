// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.exception;

import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Responses Connector.
 * 
 * <p>Handles all exceptions thrown by the application and converts them
 * to appropriate HTTP responses with SIF 3.7 compliant error format.</p>
 * 
 * <p>This handler provides centralized exception handling for:
 * <ul>
 *   <li>Queue not found errors (404)</li>
 *   <li>Queue Service unavailability (503)</li>
 *   <li>Request validation errors (400)</li>
 *   <li>Generic internal errors (500)</li>
 * </ul>
 * 
 * <p>All error responses conform to the SIF 3.7 error format using
 * {@link SifErrorResponse} from the sif-broker-common library.</p>
 * 
 * <p><b>Requirements:</b> 10.1, 10.2, 10.3, 10.4, 10.5, 10.6</p>
 * 
 * @see SifErrorResponse
 * @see QueueNotFoundException
 * @see QueueServiceUnavailableException
 * @see ResponseRoutingException
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Handle QueueNotFoundException - queue does not exist.
     * Returns HTTP 404 Not Found.
     * 
     * <p>This exception is thrown when attempting to enqueue a response to a
     * consumer's queue that does not exist in the Queue Service.</p>
     * 
     * @param ex the exception
     * @return error response with HTTP 404 status
     * 
     * <p><b>Requirements:</b> 3.7, 9.3, 10.4</p>
     */
    /**
     * Handle ResponseDestinationDeniedException — the destination queue is not a valid target.
     * Returns HTTP 404 Not Found.
     *
     * <p>Raised by the destination-ACL gate when the owner of a caller-supplied {@code queueId}
     * cannot be resolved, or holds no approved ACL grant for the object being delivered.</p>
     *
     * <p>404 rather than 403, and a single generic message for every denial reason, so the response
     * cannot be used to determine whether a given queue exists or who owns it. The real reason is
     * logged at WARN by the gate itself.</p>
     */
    @ExceptionHandler(ResponseDestinationDeniedException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleResponseDestinationDenied(
            ResponseDestinationDeniedException ex) {
        // Logged at WARN, not ERROR: a denial is an expected outcome of an authorization gate,
        // not a broker malfunction. The gate has already logged the discriminating detail.
        log.warn("Response destination denied: {}", ex.getMessage());

        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.NOT_FOUND.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                "The specified queue is not a valid destination for this response.",
                "BROKER",
                Instant.now(),
                null,
                null
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SifErrorResponseEnvelope.of(error));
    }

    @ExceptionHandler(QueueNotFoundException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleQueueNotFoundException(QueueNotFoundException ex) {
        log.error("Queue not found: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.NOT_FOUND.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                "The specified queue does not exist. Verify the queueId is correct.",
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle QueueServiceUnavailableException - Queue Service is unavailable.
     * Returns HTTP 503 Service Unavailable.
     * 
     * <p>This exception is thrown when the Queue Service is unreachable or
     * returning server errors after all retry attempts have been exhausted.</p>
     * 
     * @param ex the exception
     * @return error response with HTTP 503 status
     * 
     * <p><b>Requirements:</b> 3.6, 9.4, 10.1, 12.4</p>
     */
    @ExceptionHandler(QueueServiceUnavailableException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleQueueServiceUnavailableException(
            QueueServiceUnavailableException ex) {
        log.error("Queue Service unavailable: {}", ex.getMessage(), ex);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                "The Queue Service is currently unavailable. Please retry the request later.",
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle MethodArgumentNotValidException - request validation failed.
     * Returns HTTP 400 Bad Request with field-level validation details.
     * 
     * <p>This exception is thrown when request body validation fails due to
     * missing required fields, invalid formats, or constraint violations.</p>
     * 
     * @param ex the exception
     * @return error response with HTTP 400 status
     * 
     * <p><b>Requirements:</b> 1.5, 7.4, 10.4</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex) {
        log.error("Validation error: {}", ex.getMessage());
        
        String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Request validation failed",
                fieldErrors,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle MissingRequestHeaderException - required header is missing.
     * Returns HTTP 400 Bad Request.
     *
     * @param ex the exception
     * @return error response with HTTP 400 status
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleMissingRequestHeaderException(
            MissingRequestHeaderException ex) {
        log.error("Missing required header: {}", ex.getHeaderName());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Missing required header: " + ex.getHeaderName(),
                ex.getMessage(),
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle IllegalArgumentException - invalid argument provided.
     * Returns HTTP 400 Bad Request.
     *
     * <p>This exception is thrown when a required parameter is missing or invalid,
     * such as when zoneId is absent and no default is available.</p>
     *
     * @param ex the exception
     * @return error response with HTTP 400 status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        log.error("Invalid argument: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                ex.getMessage(),
                "The request is missing a required parameter or contains an invalid value.",
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle ResponseRoutingException - error during response routing.
     * Returns HTTP 500 Internal Server Error.
     * 
     * <p>This exception is thrown when an unexpected error occurs during the
     * response routing process that is not covered by more specific exceptions.</p>
     * 
     * @param ex the exception
     * @return error response with HTTP 500 status
     * 
     * <p><b>Requirements:</b> 10.3, 10.5</p>
     */
    @ExceptionHandler(ResponseRoutingException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleResponseRoutingException(ResponseRoutingException ex) {
        log.error("Response routing error: {}", ex.getMessage(), ex);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                "An error occurred while routing the response. Please contact support if the problem persists.",
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle all other unexpected exceptions.
     * Returns HTTP 500 Internal Server Error.
     * 
     * <p>This is a catch-all handler for any unexpected exceptions that are not
     * handled by more specific exception handlers. It logs the full stack trace
     * for debugging purposes.</p>
     * 
     * @param ex the exception
     * @return error response with HTTP 500 status
     * 
     * <p><b>Requirements:</b> 10.3, 10.5, 10.6</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                "An internal server error occurred. Please contact support if the problem persists.",
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
}
