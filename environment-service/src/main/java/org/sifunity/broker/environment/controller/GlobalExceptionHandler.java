// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.environment.secrets.BackendUnavailableException;
import org.sifunity.broker.environment.secrets.ConfigurationException;
import org.sifunity.broker.environment.secrets.EncryptionException;
import org.sifunity.broker.environment.secrets.SecretNotFoundException;
import org.sifunity.broker.environment.secrets.SecretsBackendException;
import org.sifunity.broker.environment.secrets.util.SecretRedactor;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Environment Service.
 * 
 * <p>This handler catches exceptions thrown by controllers and converts them
 * to appropriate HTTP responses with SIF-compliant error format.</p>
 * 
 * <p>Handles the following exception types:
 * <ul>
 *   <li>SifBrokerException - Custom business exceptions</li>
 *   <li>SecretsBackendException - Secrets backend operation failures</li>
 *   <li>SecretNotFoundException - Secret not found errors</li>
 *   <li>BackendUnavailableException - Backend service unavailable</li>
 *   <li>EncryptionException - Encryption/decryption failures</li>
 *   <li>ConfigurationException - Configuration errors</li>
 *   <li>MethodArgumentNotValidException - Bean validation errors</li>
 *   <li>IllegalArgumentException - Invalid parameter errors</li>
 *   <li>Exception - Generic server errors</li>
 * </ul>
 * 
 * <p>All error responses are processed through SecretRedactor to ensure
 * sensitive data is never exposed in error messages.</p>
 * 
 * @see SifBrokerException
 * @see SifErrorResponse
 * @see SecretRedactor
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handles SifBrokerException and its subclasses.
     * 
     * <p>These are business logic exceptions with predefined HTTP status codes
     * and error messages. The exception is converted to a SifErrorResponse.</p>
     * 
     * @param ex the SifBrokerException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and appropriate HTTP status
     */
    @ExceptionHandler(SifBrokerException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleSifBrokerException(SifBrokerException ex) {
        log.warn("Business exception occurred: {}", ex.getMessage());
        
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(ex);
        
        return ResponseEntity.status(ex.getHttpStatus()).body(envelope);
    }
    
    /**
     * Handles Bean Validation exceptions.
     * 
     * <p>These occur when @Valid annotation validation fails on request DTOs.
     * The field errors are collected and returned as a validation error.</p>
     * 
     * @param ex the MethodArgumentNotValidException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleValidationException(MethodArgumentNotValidException ex) {
        String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        log.warn("Validation error: {}", fieldErrors);
        
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
     * Handles IllegalArgumentException.
     * 
     * <p>These occur when method parameters are invalid (e.g., negative page numbers).
     * Converted to HTTP 400 Bad Request.</p>
     * 
     * @param ex the IllegalArgumentException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "ARG_001",
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
     * Handles MethodArgumentTypeMismatchException.
     * 
     * <p>These occur when path variables or request parameters cannot be converted
     * to the required type (e.g., invalid UUID format). Converted to HTTP 400 Bad Request.</p>
     * 
     * @param ex the MethodArgumentTypeMismatchException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        
        String message = String.format("Invalid value for parameter '%s': %s", 
                ex.getName(), ex.getValue());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "ARG_002",
                "Invalid parameter format",
                message,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles MissingServletRequestParameterException.
     * 
     * <p>These occur when required request parameters are missing.
     * Converted to HTTP 400 Bad Request.</p>
     * 
     * @param ex the MissingServletRequestParameterException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 400 Bad Request
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        log.warn("Missing required parameter '{}': {}", ex.getParameterName(), ex.getMessage());
        
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "ARG_003",
                "Missing required parameter",
                message,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles SecretNotFoundException.
     * 
     * <p>These occur when a secret is not found for an environment.
     * Converted to HTTP 404 Not Found. Error messages are redacted to prevent
     * exposure of sensitive data.</p>
     * 
     * @param ex the SecretNotFoundException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 404 Not Found
     */
    @ExceptionHandler(SecretNotFoundException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleSecretNotFoundException(SecretNotFoundException ex) {
        String redactedMessage = SecretRedactor.redactExceptionMessage(ex);
        log.warn("Secret not found: {}", redactedMessage);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.NOT_FOUND.value(),
                "SECRET_001",
                "Secret not found",
                redactedMessage,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles BackendUnavailableException.
     * 
     * <p>These occur when the secrets backend service is unavailable (e.g., Vault is down).
     * Converted to HTTP 503 Service Unavailable. Error messages are redacted to prevent
     * exposure of sensitive data.</p>
     * 
     * @param ex the BackendUnavailableException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 503 Service Unavailable
     */
    @ExceptionHandler(BackendUnavailableException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleBackendUnavailableException(BackendUnavailableException ex) {
        String redactedMessage = SecretRedactor.redactExceptionMessage(ex);
        log.error("Secrets backend unavailable: {}", redactedMessage);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "SECRET_002",
                "Secrets backend unavailable",
                redactedMessage,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles EncryptionException.
     * 
     * <p>These occur when encryption or decryption operations fail.
     * Converted to HTTP 500 Internal Server Error. Error messages are redacted to prevent
     * exposure of sensitive data like keys or encrypted values.</p>
     * 
     * @param ex the EncryptionException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 500 Internal Server Error
     */
    @ExceptionHandler(EncryptionException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleEncryptionException(EncryptionException ex) {
        String redactedMessage = SecretRedactor.redactExceptionMessage(ex);
        log.error("Encryption operation failed: {}", redactedMessage);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "SECRET_003",
                "Encryption operation failed",
                redactedMessage,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles ConfigurationException.
     * 
     * <p>These occur when secrets backend configuration is invalid.
     * Converted to HTTP 500 Internal Server Error. Error messages are redacted to prevent
     * exposure of sensitive configuration data like keys or tokens.</p>
     * 
     * @param ex the ConfigurationException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 500 Internal Server Error
     */
    @ExceptionHandler(ConfigurationException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleConfigurationException(ConfigurationException ex) {
        String redactedMessage = SecretRedactor.redactExceptionMessage(ex);
        log.error("Secrets backend configuration error: {}", redactedMessage);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "SECRET_004",
                "Secrets backend configuration error",
                redactedMessage,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles generic SecretsBackendException.
     * 
     * <p>These are base exceptions for secrets backend operations that don't fit
     * into more specific categories. Converted to HTTP 500 Internal Server Error.
     * Error messages are redacted to prevent exposure of sensitive data.</p>
     * 
     * @param ex the SecretsBackendException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 500 Internal Server Error
     */
    @ExceptionHandler(SecretsBackendException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleSecretsBackendException(SecretsBackendException ex) {
        String redactedMessage = SecretRedactor.redactExceptionMessage(ex);
        log.error("Secrets backend error: {}", redactedMessage);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "SECRET_005",
                "Secrets backend error",
                redactedMessage,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles JSON parse and message conversion errors.
     * 
     * <p>These occur when the request body cannot be deserialized — invalid JSON syntax,
     * unrecognized fields, invalid enum values, type mismatches, etc.
     * Converted to HTTP 400 Bad Request with a descriptive message.</p>
     * 
     * @param ex the HttpMessageNotReadableException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex) {
        String description = extractReadableMessage(ex);
        log.warn("Request body parse error: {}", description);
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "VAL_002",
                "Invalid request body",
                description,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Extracts a human-readable message from a JSON parse exception,
     * stripping internal class names and stack details.
     */
    private String extractReadableMessage(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause == null) {
            return "Malformed JSON request body";
        }
        
        String msg = cause.getMessage();
        if (msg == null) {
            return "Malformed JSON request body";
        }
        
        // Strip everything after the first line break or "at [Source:" to remove internal details
        int sourceIdx = msg.indexOf(" at [Source:");
        if (sourceIdx > 0) {
            msg = msg.substring(0, sourceIdx);
        }
        int lineBreak = msg.indexOf('\n');
        if (lineBreak > 0) {
            msg = msg.substring(0, lineBreak);
        }
        
        // Strip fully-qualified class names like "org.sifunity.broker.common.constant.EnvironmentType"
        // Replace with just the simple class name
        msg = msg.replaceAll("`[a-z][a-zA-Z0-9.]*\\.([A-Z][a-zA-Z0-9]*)`", "`$1`");
        
        return msg.trim();
    }
    
    /**
     * Handles NoResourceFoundException.
     * 
     * <p>These occur when Spring MVC cannot find a handler for the requested path
     * (unmapped routes). Converted to HTTP 404 Not Found instead of falling through
     * to the catch-all 500 handler.</p>
     * 
     * @param ex the NoResourceFoundException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 404 Not Found
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND_001",
                "Resource not found",
                ex.getMessage(),
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles HttpRequestMethodNotSupportedException.
     *
     * <p>These occur when a request targets a valid path with an HTTP method that has no
     * mapping (e.g., a {@code POST} to a collection path that only exposes {@code GET}, such as
     * the removed bare-collection zone/context create routes). Converted to HTTP 405 Method Not
     * Allowed with an {@code Allow} header listing the supported methods, instead of falling
     * through to the catch-all 500 handler.</p>
     *
     * @param ex the HttpRequestMethodNotSupportedException to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 405 Method Not Allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());

        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "METHOD_001",
                "Method not allowed",
                ex.getMessage(),
                "BROKER",
                Instant.now(),
                null,
                null
        );

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            builder.allow(ex.getSupportedHttpMethods().toArray(new HttpMethod[0]));
        }
        return builder.body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Handles generic exceptions.
     * 
     * <p>These are unexpected server errors. The exception is logged with full
     * stack trace and a generic error response is returned. Error messages are
     * redacted to prevent exposure of sensitive data.</p>
     * 
     * @param ex the Exception to handle
     * @return ResponseEntity with SifErrorResponseEnvelope and HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleGenericException(Exception ex) {
        String redactedMessage = SecretRedactor.redactExceptionMessage(ex);
        log.error("Unexpected error occurred: {}", redactedMessage);
        
        // Description is null for generic exceptions as a security measure —
        // we never expose internal error details to the caller.
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_001",
                "Internal server error",
                null,
                "BROKER",
                Instant.now(),
                null,
                null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
    }
}
