// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.exception;

import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

/**
 * Global exception handler for the Events Connector.
 * <p>
 * Handles all exceptions thrown by controllers and converts them to
 * standardized SIF error responses conforming to SIF 3.7 format.
 * </p>
 * <p>
 * This handler ensures that all errors are properly logged and returned
 * to clients in a consistent format with appropriate HTTP status codes.
 * </p>
 * <p>
 * <strong>Requirements:</strong> 10
 * </p>
 * <p>
 * Exception Handling:
 * </p>
 * <ul>
 *   <li>EventValidationException → 400 Bad Request</li>
 *   <li>SubscriptionServiceException → 503 Service Unavailable</li>
 *   <li>QueueServiceException → 503 Service Unavailable</li>
 *   <li>Generic Exception → 500 Internal Server Error</li>
 * </ul>
 *
 * @see EventValidationException
 * @see SubscriptionServiceException
 * @see QueueServiceException
 * @see SifErrorResponse
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handles EventValidationException (HTTP 400 Bad Request).
     * <p>
     * This exception is thrown when event validation fails, such as missing
     * required headers (serviceName, zoneId) or invalid event payload.
     * </p>
     *
     * @param ex the EventValidationException
     * @return error response with HTTP 400 status
     * 
     * <p><strong>Requirements:</strong> 10.2, 10.5</p>
     */
    @ExceptionHandler(EventValidationException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleEventValidationException(
            EventValidationException ex) {
        log.error("Event validation error: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_REQUEST.value(),
            ex.getErrorCode(),
            ex.getMessage(),
            "Invalid event data provided",
            "EVENTS_CONNECTOR",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles SubscriptionServiceException (HTTP 503 Service Unavailable).
     * <p>
     * This exception is thrown when the Events Connector is unable to query
     * subscriptions from the Subscription Service due to service unavailability,
     * timeout, or other communication errors.
     * </p>
     *
     * @param ex the SubscriptionServiceException
     * @return error response with HTTP 503 status
     * 
     * <p><strong>Requirements:</strong> 10.1, 10.5, 10.6</p>
     */
    @ExceptionHandler(SubscriptionServiceException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleSubscriptionServiceException(
            SubscriptionServiceException ex) {
        log.error("Subscription Service error: {}", ex.getMessage(), ex);
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            ex.getErrorCode(),
            ex.getMessage(),
            "Unable to query subscriptions for event distribution",
            "EVENTS_CONNECTOR",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles QueueServiceException (HTTP 503 Service Unavailable).
     * <p>
     * This exception is thrown when the Events Connector is unable to enqueue
     * events to the Queue Service due to service unavailability, timeout, or
     * other communication errors.
     * </p>
     * <p>
     * Note: This exception is typically thrown when all queue operations fail.
     * Partial failures (some queues succeed, some fail) are handled differently
     * and result in a 207 Multi-Status response rather than this exception.
     * </p>
     *
     * @param ex the QueueServiceException
     * @return error response with HTTP 503 status
     * 
     * <p><strong>Requirements:</strong> 10.1, 10.5, 10.6</p>
     */
    @ExceptionHandler(QueueServiceException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleQueueServiceException(
            QueueServiceException ex) {
        log.error("Queue Service error: {}", ex.getMessage(), ex);
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            ex.getErrorCode(),
            ex.getMessage(),
            "Unable to enqueue event to subscriber queues",
            "EVENTS_CONNECTOR",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles all other unexpected exceptions (HTTP 500 Internal Server Error).
     * <p>
     * This is a catch-all handler for any exceptions not explicitly handled by
     * other exception handlers. It logs the full error context including stack
     * trace for debugging and returns a generic error message to the client.
     * </p>
     *
     * @param ex the exception
     * @return error response with HTTP 500 status
     * 
     * <p><strong>Requirements:</strong> 10.3, 10.5, 10.6</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR_001",
            "Internal server error",
            "An unexpected error occurred. Please contact support if the problem persists.",
            "EVENTS_CONNECTOR",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
}
