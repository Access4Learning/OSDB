// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.exception;

import org.sifunity.broker.common.exception.*;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.queueservice.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Queue Service.
 * 
 * <p>Handles all exceptions thrown by controllers and converts them to
 * standardized SIF error responses.</p>
 * 
 * <p>Requirements: 13.1, 13.2, 13.3, 13.4, 13.5</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle QueueNotFoundException (HTTP 404).
     * 
     * @param ex the exception
     * @return error response
     * 
     * <p>Requirements: 13.2</p>
     */
    @ExceptionHandler(QueueNotFoundException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleQueueNotFoundException(QueueNotFoundException ex) {
        log.warn("Queue not found: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.NOT_FOUND.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                null,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle MessageNotFoundException (HTTP 404).
     * 
     * @param ex the exception
     * @return error response
     * 
     * <p>Requirements: 13.2</p>
     */
    @ExceptionHandler(MessageNotFoundException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleMessageNotFoundException(MessageNotFoundException ex) {
        log.warn("Message not found: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.NOT_FOUND.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                null,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle validation errors (HTTP 400).
     * 
     * @param ex the exception
     * @return error response
     * 
     * <p>Requirements: 13.1</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        
        String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "VAL_001",
                "Validation failed",
                fieldErrors,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle InvalidRequestException (HTTP 400).
     * 
     * @param ex the exception
     * @return error response
     * 
     * <p>Requirements: 13.1</p>
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleInvalidRequestException(InvalidRequestException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                null,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle StorageException (HTTP 500).
     * 
     * @param ex the exception
     * @return error response
     * 
     * <p>Requirements: 13.4</p>
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleStorageException(StorageException ex) {
        log.error("Storage error: {}", ex.getMessage(), ex);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "STORAGE_001",
                "Storage operation failed",
                ex.getMessage(),
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle ServiceUnavailableException (HTTP 503).
     * 
     * @param ex the exception
     * @return error response
     * 
     * <p>Requirements: 13.3, 13.4</p>
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleServiceUnavailableException(ServiceUnavailableException ex) {
        log.error("Service unavailable: {}", ex.getMessage(), ex);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                null,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle IllegalArgumentException (HTTP 400).
     * 
     * @param ex the exception
     * @return error response
     * 
     * <p>Requirements: 13.1</p>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "VAL_002",
                "Invalid argument",
                ex.getMessage(),
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle data integrity violations (HTTP 409).
     * 
     * <p>Catches duplicate key and other constraint violations that slip past
     * service-level checks. Returns a descriptive 409 Conflict response.</p>
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        String detail = ex.getMostSpecificCause().getMessage();
        // Extract a user-friendly message from the constraint violation
        String message = "A resource with the same unique attributes already exists";
        if (detail != null && detail.contains("idx_queues_owner_name")) {
            message = "This environment already has a queue with that name";
        }

        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.CONFLICT.value(),
                "CONFLICT_001",
                message,
                detail,
                "BROKER",
                Instant.now(),
                null,
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Handle all other exceptions (HTTP 500).
     * 
     * @param ex the exception
     * @return error response
     * 
     * <p>Requirements: 13.5</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "SYS_001",
                "Internal server error",
                "An unexpected error occurred. Please contact support if the problem persists.",
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
}
