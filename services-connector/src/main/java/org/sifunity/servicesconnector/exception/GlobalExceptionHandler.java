// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.common.web.ProviderResponseErrorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Services Connector.
 * 
 * <p>Handles all exceptions thrown by controllers and converts them to
 * standardized SIF error responses conforming to SIF 3.7 format.</p>
 * 
 * <p><strong>Requirements:</strong></p>
 * <ul>
 *   <li>2.4: Forward Job Service 400 errors</li>
 *   <li>2.5: Forward Job Service 404 errors</li>
 *   <li>3.6: Forward Job Service 409 errors</li>
 *   <li>6.4: Forward Job Service conflict errors</li>
 *   <li>11.1: Return 503 when Job Service unavailable</li>
 *   <li>11.2: Return 503 when Environment Service unavailable</li>
 *   <li>11.4: Return 400 for invalid requests</li>
 *   <li>11.5: Return 500 for unexpected errors and log full context</li>
 *   <li>11.6: All error responses conform to SIF 3.7 format</li>
 *   <li>15.4: Log all exceptions with full context</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle ProviderTimeoutException - provider call timed out.
     * Returns HTTP 504 Gateway Timeout.
     *
     * @param ex the ProviderTimeoutException
     * @return error response with HTTP 504 status
     */
    @ExceptionHandler(ProviderTimeoutException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleProviderTimeout(ProviderTimeoutException ex) {
        log.error("Provider timeout: {}", ex.getMessage());

        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.GATEWAY_TIMEOUT.value(),
            "PROVIDER_TIMEOUT",
            "Provider request timed out",
            ex.getMessage(),
            "BROKER",
            Instant.now(),
            null,
            null
        );

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Handles SifBrokerException and its subclasses.
     * 
     * <p>Converts the exception to a SifErrorResponse and returns the appropriate
     * HTTP status code from the exception.</p>
     * 
     * @param ex the SifBrokerException
     * @return error response with appropriate HTTP status
     * 
     * <p><strong>Requirements:</strong> 11.1, 11.2, 11.4, 11.6</p>
     */
    @ExceptionHandler(SifBrokerException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleSifBrokerException(SifBrokerException ex) {
        log.error("SIF Broker exception: code={}, message={}, status={}", 
            ex.getErrorCode(), ex.getMessage(), ex.getHttpStatus(), ex);
        
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(ex);
        return ResponseEntity.status(ex.getHttpStatus()).body(envelope);
    }
    
    /**
     * Handles NoProviderException (no provider registered for service/zone/context).
     * 
     * <p>Returns HTTP 404 Not Found with a descriptive error message.</p>
     * 
     * @param ex the NoProviderException
     * @return error response with HTTP 404 status
     * 
     * <p><strong>Requirements:</strong> 1.5, 7.3, 11.6</p>
     */
    @ExceptionHandler(NoProviderException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleNoProviderException(NoProviderException ex) {
        log.warn("No provider found: service={}, zone={}, context={}", 
            ex.getServiceName(), ex.getZoneId(), ex.getContextId());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.NOT_FOUND.value(),
            "ROUTE_001",
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
     * Handle UnknownQueueException - queue ownership cannot be confirmed.
     * Returns HTTP 404 Not Found (never 403, to prevent queue existence enumeration).
     */
    @ExceptionHandler(UnknownQueueException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleUnknownQueue(UnknownQueueException ex) {
        log.error("Unknown queue: {}", ex.getMessage());

        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.NOT_FOUND.value(),
            "QUEUE_NOT_FOUND",
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
     * Handles NoSubscriptionException (provider has no request subscription for DELAYED routing).
     * 
     * <p>Returns HTTP 404 Not Found with a descriptive error message.</p>
     * 
     * @param ex the NoSubscriptionException
     * @return error response with HTTP 404 status
     */
    @ExceptionHandler(NoSubscriptionException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleNoSubscriptionException(NoSubscriptionException ex) {
        log.warn("No subscription found: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.NOT_FOUND.value(),
            "ROUTE_002",
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
     * Handles WebClientResponseException (errors from downstream services).
     * 
     * <p>Forwards the HTTP status code and error details from the downstream service
     * (Job Service or Environment Service) to the client. This ensures that errors
     * like 404 Not Found, 400 Bad Request, 409 Conflict, and 503 Service Unavailable
     * are properly propagated.</p>
     * 
     * @param ex the WebClientResponseException
     * @return error response with the same HTTP status as the downstream service
     * 
     * <p><strong>Requirements:</strong> 2.4, 2.5, 3.6, 6.4, 11.1, 11.2, 11.6</p>
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleWebClientResponseException(
            WebClientResponseException ex) {
        // A non-error status (2xx/3xx) means the downstream responded OK but the
        // body could not be read/decoded (e.g. exceeded the in-memory buffer
        // limit). Surface as 502 Bad Gateway with a descriptive message rather
        // than forwarding a misleading success status. (Shared logic in
        // ProviderResponseErrorMapper.)
        if (ProviderResponseErrorMapper.isNonErrorStatus(ex.getStatusCode().value())) {
            String detail = ProviderResponseErrorMapper.messageForProcessingFailure(ex);
            log.error("Downstream response could not be processed (status {}): {}",
                ex.getStatusCode().value(), detail, ex);

            SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_GATEWAY.value(),
                ProviderResponseErrorMapper.codeForProcessingFailure(ex),
                detail,
                null,
                "BROKER",
                Instant.now(),
                null,
                null
            );
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(SifErrorResponseEnvelope.of(error));
        }

        log.error("Downstream service error: status={}, message={}", 
            ex.getStatusCode(), ex.getMessage(), ex);
        
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        
        // Try to parse the downstream error response, or create a generic one
        SifErrorResponse error;
        try {
            // Attempt to extract error details from response body
            String responseBody = ex.getResponseBodyAsString();
            if (responseBody != null && !responseBody.isEmpty()) {
                // If downstream already returned a SifErrorResponse, we could parse it
                // For now, create a generic error with the downstream message
                error = new SifErrorResponse(
                    UUID.randomUUID().toString(),
                    status.value(),
                    "DOWNSTREAM_ERROR",
                    "Downstream service returned error: " + ex.getStatusText(),
                    responseBody,
                    "BROKER",
                    Instant.now(),
                    null,
                    null
                );
            } else {
                error = new SifErrorResponse(
                    UUID.randomUUID().toString(),
                    status.value(),
                    "DOWNSTREAM_ERROR",
                    "Downstream service returned error: " + ex.getStatusText(),
                    null,
                    "BROKER",
                    Instant.now(),
                    null,
                    null
                );
            }
        } catch (Exception e) {
            // If parsing fails, create a generic error
            error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                status.value(),
                "DOWNSTREAM_ERROR",
                "Downstream service returned error: " + ex.getStatusText(),
                null,
                "BROKER",
                Instant.now(),
                null,
                null
            );
        }
        
        return ResponseEntity.status(ex.getStatusCode()).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles validation errors from @Valid annotations.
     * 
     * <p>Returns HTTP 400 Bad Request with field-level validation details.</p>
     * 
     * @param ex the validation exception
     * @return error response with HTTP 400 status
     * 
     * <p><strong>Requirements:</strong> 9.5, 11.4, 11.6</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleValidationException(
            MethodArgumentNotValidException ex) {
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
     * Handles MissingRequestHeaderException (missing required headers).
     * 
     * <p>Returns HTTP 400 Bad Request when required headers like X-Environment-Id
     * or X-Environment-Name are missing from the request.</p>
     * 
     * @param ex the MissingRequestHeaderException
     * @return error response with HTTP 400 status
     * 
     * <p><strong>Requirements:</strong> 10.3, 11.4, 11.6</p>
     */
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleMissingRequestHeaderException(
            org.springframework.web.bind.MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "VAL_003",
            "Missing required header",
            ex.getMessage(),
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handles IllegalArgumentException (invalid arguments).
     * 
     * <p>Returns HTTP 400 Bad Request with error details.</p>
     * 
     * @param ex the IllegalArgumentException
     * @return error response with HTTP 400 status
     * 
     * <p><strong>Requirements:</strong> 11.4, 11.6</p>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleIllegalArgumentException(
            IllegalArgumentException ex) {
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
     * Handles DataBufferLimitException - the downstream response body exceeded the
     * configured in-memory buffer limit and could not be read. Returns HTTP 502
     * Bad Gateway with a descriptive, actionable message.
     */
    @ExceptionHandler(org.springframework.core.io.buffer.DataBufferLimitException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleDataBufferLimit(
            org.springframework.core.io.buffer.DataBufferLimitException ex) {
        log.error("Downstream response exceeded the in-memory buffer limit: {}", ex.getMessage());

        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_GATEWAY.value(),
            ProviderResponseErrorMapper.codeForProcessingFailure(ex),
            ProviderResponseErrorMapper.messageForProcessingFailure(ex),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Handles all other unexpected exceptions.
     * 
     * <p>Returns HTTP 500 Internal Server Error and logs the full error context
     * including stack trace for debugging.</p>
     * 
     * @param ex the exception
     * @return error response with HTTP 500 status
     * 
     * <p><strong>Requirements:</strong> 11.5, 15.4</p>
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
