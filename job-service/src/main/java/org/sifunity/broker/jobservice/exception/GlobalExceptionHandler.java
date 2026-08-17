// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Job Service.
 * Converts exceptions to standardized SifErrorResponse format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handles SifBrokerException and its subclasses.
     *
     * @param ex the exception
     * @return error response with appropriate HTTP status
     */
    @ExceptionHandler(SifBrokerException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleSifBrokerException(SifBrokerException ex) {
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(ex);
        return ResponseEntity.status(ex.getHttpStatus()).body(envelope);
    }
    
    /**
     * Handles validation errors from @Valid annotations.
     *
     * @param ex the validation exception
     * @return error response with HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleValidationException(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "VAL_001",
            "Validation failed: " + message,
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.badRequest().body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles optimistic locking failures (concurrent modifications).
     *
     * @param ex the optimistic locking exception
     * @return error response with HTTP 409 Conflict
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleOptimisticLockException(
            OptimisticLockingFailureException ex) {
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.CONFLICT.value(),
            "JOB_006",
            "Concurrent modification detected. Please retry.",
            "The job was modified by another request. Please retrieve the latest version and try again.",
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles data integrity violations (e.g., unique constraint violations).
     *
     * @param ex the data integrity violation exception
     * @return error response with HTTP 409 Conflict
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.CONFLICT.value(),
            "JOB_007",
            "Data integrity violation: a conflicting resource already exists",
            ex.getMostSpecificCause().getMessage(),
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles all other unexpected exceptions.
     *
     * @param ex the exception
     * @return error response with HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleGenericException(Exception ex) {
        // Log the exception for debugging
        System.err.println("Unexpected error: " + ex.getMessage());
        ex.printStackTrace();
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
}
