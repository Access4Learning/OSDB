// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.exception;

import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.sifunity.broker.common.exception.SubscriptionNotFoundException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

/**
 * Global exception handler for the Subscription Service.
 * 
 * <p>This handler catches exceptions thrown by controllers and converts them
 * into appropriate HTTP responses with SIF-compliant error messages.</p>
 * 
 * <p>Handles the following exception types:</p>
 * <ul>
 *   <li>SubscriptionNotFoundException - 404 Not Found</li>
 *   <li>QueueNotFoundException - 404 Not Found</li>
 *   <li>AclEntryNotFoundException - 404 Not Found</li>
 *   <li>DuplicateSubscriptionException - 409 Conflict</li>
 *   <li>AccessTypeMismatchException - 403 Forbidden</li>
 *   <li>MethodArgumentNotValidException - 400 Bad Request</li>
 *   <li>ServiceUnavailableException - 503 Service Unavailable</li>
 *   <li>Exception - 500 Internal Server Error</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handles SubscriptionNotFoundException.
     * 
     * @param ex the exception
     * @return 404 Not Found response with error details
     */
    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleSubscriptionNotFound(
            SubscriptionNotFoundException ex) {
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(envelope);
    }
    
    /**
     * Handles QueueNotFoundException.
     * 
     * @param ex the exception
     * @return 404 Not Found response with error details
     */
    @ExceptionHandler(QueueNotFoundException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleQueueNotFound(
            QueueNotFoundException ex) {
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(envelope);
    }
    
    /**
     * Handles DuplicateSubscriptionException.
     * 
     * @param ex the exception
     * @return 409 Conflict response with error details
     */
    @ExceptionHandler(DuplicateSubscriptionException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleDuplicateSubscription(
            DuplicateSubscriptionException ex) {
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(envelope);
    }
    
    /**
     * Handles AclEntryNotFoundException.
     * 
     * @param ex the exception
     * @return 404 Not Found response with error details
     */
    @ExceptionHandler(AclEntryNotFoundException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleAclEntryNotFound(
            AclEntryNotFoundException ex) {
        // D7 (2.16): 400, not 404. POST /subscriptions/subscription declares no 404, and a
        // caller-supplied aclEntryId that does not resolve is a malformed request rather than a
        // missing resource. The declared 404s on GET/DELETE /subscriptions/{id} are unchanged.
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(envelope);
    }

    /**
     * Handles AclEntryNotHeldException — the caller holds no matching approved grant.
     *
     * <p>D7 (2.16): an authorization outcome, so 403 (declared) rather than 404 (undeclared). Kept
     * distinct from {@link AclEntryNotFoundException} so a caller probing ACL ids learns "you do not
     * hold this" rather than "this does not exist".</p>
     *
     * @param ex the exception
     * @return 403 Forbidden response with error details
     */
    @ExceptionHandler(AclEntryNotHeldException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleAclEntryNotHeld(
            AclEntryNotHeldException ex) {
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(envelope);
    }
    
    /**
     * Handles AccessTypeMismatchException.
     * 
     * @param ex the exception
     * @return 403 Forbidden response with error details (authorization failure)
     */
    @ExceptionHandler(AccessTypeMismatchException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleAccessTypeMismatch(
            AccessTypeMismatchException ex) {
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(envelope);
    }
    
    /**
     * Handles validation errors from @Valid annotation.
     * 
     * @param ex the exception
     * @return 400 Bad Request response with validation error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "VAL_001",
            "Validation failed",
            ex.getBindingResult().toString(),
            "BROKER",
            Instant.now(),
            null,
            null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles missing required request headers.
     * 
     * @param ex the exception
     * @return 400 Bad Request response with error details
     */
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleMissingHeader(
            org.springframework.web.bind.MissingRequestHeaderException ex) {
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "VAL_001",
            "Missing required header",
            ex.getMessage(),
            "BROKER",
            Instant.now(),
            ex.getHeaderName(),
            null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Handles missing required request parameters.
     * 
     * @param ex the exception
     * @return 400 Bad Request response with error details
     */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleMissingParameter(
            org.springframework.web.bind.MissingServletRequestParameterException ex) {
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "VAL_001",
            "Missing required parameter",
            ex.getMessage(),
            "BROKER",
            Instant.now(),
            ex.getParameterName(),
            null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles ServiceUnavailableException.
     * 
     * @param ex the exception
     * @return 503 Service Unavailable response with error details
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleServiceUnavailable(
            ServiceUnavailableException ex) {
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(envelope);
    }
    
    /**
     * Handles unexpected errors.
     * 
     * @param ex the exception
     * @return 500 Internal Server Error response with error details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleUnexpectedError(Exception ex) {
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            ex.getMessage(),
            "BROKER",
            Instant.now(),
            null,
            null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
}
