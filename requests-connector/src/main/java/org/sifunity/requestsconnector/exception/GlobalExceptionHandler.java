// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.common.web.ProviderResponseErrorMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;

/**
 * Global exception handler for the Requests Connector.
 * 
 * Handles all exceptions thrown by the application and converts them
 * to appropriate HTTP responses with SIF-compliant error format.
 * 
 * Validates Requirements 10.1, 10.2, 10.3, 10.4, 10.5, 10.6
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Handle NoProviderException - no provider registered for service/zone/context.
     * Returns HTTP 404 Not Found.
     * 
     * Validates Requirements 1.5, 2.3, 10.1
     */
    @ExceptionHandler(NoProviderException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleNoProvider(NoProviderException e) {
        log.error("No provider found: {}", e.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.NOT_FOUND.value(),
            "NO_PROVIDER",
            e.getMessage(),
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
    public ResponseEntity<SifErrorResponseEnvelope> handleUnknownQueue(UnknownQueueException e) {
        log.error("Unknown queue: {}", e.getMessage());

        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.NOT_FOUND.value(),
            "QUEUE_NOT_FOUND",
            e.getMessage(),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle NoSubscriptionException - provider has no request subscription.
     * Returns HTTP 404 Not Found.
     * 
     * Validates Requirements 4.3, 10.1
     */
    @ExceptionHandler(NoSubscriptionException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleNoSubscription(NoSubscriptionException e) {
        log.error("No subscription found: {}", e.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.NOT_FOUND.value(),
            "NO_SUBSCRIPTION",
            e.getMessage(),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle MissingHeaderException - required header missing.
     * Returns HTTP 400 Bad Request.
     * 
     * Validates Requirements 8.3, 10.4
     */
    @ExceptionHandler(MissingHeaderException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleMissingHeader(MissingHeaderException e) {
        log.error("Missing header: {}", e.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "MISSING_HEADER",
            e.getMessage(),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle IllegalArgumentException - invalid request parameters (e.g., missing queueId for DELAYED).
     * Returns HTTP 400 Bad Request.
     * 
     * Validates Requirements 8.3, 10.4
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "INVALID_ARGUMENT",
            e.getMessage(),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle UnsupportedPatternException - provider doesn't support required pattern.
     * Returns HTTP 400 Bad Request.
     * 
     * Validates Requirements 3.4, 10.4
     */
    @ExceptionHandler(UnsupportedPatternException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleUnsupportedPattern(UnsupportedPatternException e) {
        log.error("Unsupported pattern: {}", e.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "UNSUPPORTED_PATTERN",
            e.getMessage(),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle MethodArgumentNotValidException - request validation failed.
     * Returns HTTP 400 Bad Request with field-level validation details.
     * 
     * Validates Requirements 7.4, 10.4
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleValidation(MethodArgumentNotValidException e) {
        log.error("Validation failed: {}", e.getMessage());
        
        StringBuilder details = new StringBuilder();
        e.getBindingResult().getFieldErrors().forEach(error -> {
            details.append(error.getField())
                   .append(": ")
                   .append(error.getDefaultMessage())
                   .append("; ");
        });
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_ERROR",
            "Request validation failed: " + details.toString(),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle ProviderTimeoutException - provider call timed out.
     * Returns HTTP 504 Gateway Timeout.
     * 
     * Validates Requirements 11.3
     */
    @ExceptionHandler(ProviderTimeoutException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleProviderTimeout(ProviderTimeoutException e) {
        log.error("Provider timeout: {}", e.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.GATEWAY_TIMEOUT.value(),
            "PROVIDER_TIMEOUT",
            e.getMessage(),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle WebClientResponseException - service unavailable or provider error.
     * Returns HTTP 503 Service Unavailable for 5xx errors, forwards other errors.
     * 
     * Validates Requirements 1.4, 2.4, 4.6, 10.2, 10.3
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleWebClientResponse(WebClientResponseException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        
        // A non-error status (2xx/3xx) on a WebClientResponseException means the
        // provider returned an OK response but the connector failed while reading
        // or decoding the body (e.g. the body exceeded the codec in-memory buffer
        // limit). This is an upstream-processing failure, not a provider error
        // status, so surface it as 502 Bad Gateway with a descriptive message
        // (shared logic in ProviderResponseErrorMapper) rather than echoing back
        // a misleading 200 with an empty body.
        if (ProviderResponseErrorMapper.isNonErrorStatus(e.getStatusCode().value())) {
            String code = ProviderResponseErrorMapper.codeForProcessingFailure(e);
            String detail = ProviderResponseErrorMapper.messageForProcessingFailure(e);
            log.error("Provider response could not be processed (upstream status {}): {}",
                status.value(), detail, e);

            SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.BAD_GATEWAY.value(),
                code,
                detail,
                null,
                "BROKER",
                Instant.now(),
                null,
                null
            );
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(SifErrorResponseEnvelope.of(error));
        }
        
        // If it's a 5xx error, treat as service unavailable
        if (status.is5xxServerError()) {
            log.error("Service unavailable: {}", e.getMessage());
            
            SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "SERVICE_UNAVAILABLE",
                "A required service is currently unavailable",
                null,
                "BROKER",
                Instant.now(),
                null,
                null
            );
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SifErrorResponseEnvelope.of(error));
        }
        
        // For other errors (4xx), forward the error response
        log.error("Provider error: {} - {}", status, e.getMessage());
        
        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            status.value(),
            "PROVIDER_ERROR",
            e.getResponseBodyAsString(),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return ResponseEntity.status(status).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Handle DataBufferLimitException - the provider response body exceeded the
     * configured in-memory buffer limit and could not be read. Returns HTTP 502
     * Bad Gateway with a descriptive, actionable message.
     */
    @ExceptionHandler(org.springframework.core.io.buffer.DataBufferLimitException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleDataBufferLimit(
            org.springframework.core.io.buffer.DataBufferLimitException e) {
        log.error("Provider response exceeded the in-memory buffer limit: {}", e.getMessage());

        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_GATEWAY.value(),
            ProviderResponseErrorMapper.codeForProcessingFailure(e),
            ProviderResponseErrorMapper.messageForProcessingFailure(e),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Handle WebClientRequestException - the provider endpoint could not be reached
     * (connection refused, DNS failure, connection reset, etc.).
     *
     * <p>This is distinct from {@link WebClientResponseException} (provider returned an
     * HTTP error status) and {@link ProviderTimeoutException} (provider accepted the
     * connection but did not respond in time). A request-level failure means the broker
     * acted as a proxy but the upstream provider was unreachable, which maps to
     * HTTP 502 Bad Gateway.</p>
     *
     * Validates Requirements 1.4, 2.4, 10.2, 10.3
     */
    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleWebClientRequest(WebClientRequestException e) {
        log.error("Provider unreachable: {}", e.getMessage());

        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.BAD_GATEWAY.value(),
            "PROVIDER_UNAVAILABLE",
            "The provider endpoint could not be reached",
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Handle ServiceUnavailableException - a required core broker service
     * (environment-service, queue-service, subscription-service) could not be reached.
     * Returns HTTP 503 Service Unavailable, naming the unreachable service.
     *
     * <p>This is distinct from {@link WebClientRequestException} (provider endpoint
     * unreachable → 502 Bad Gateway): a core internal dependency being down is a 503.</p>
     *
     * Validates Requirements 10.2, 10.3
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleServiceUnavailable(ServiceUnavailableException e) {
        log.error("Core service unavailable: {}", e.getMessage(), e);

        SifErrorResponse error = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "SERVICE_UNAVAILABLE",
            e.getMessage(),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Handle unexpected exceptions.
     * Returns HTTP 500 Internal Server Error.
     * 
     * Validates Requirements 10.5
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SifErrorResponseEnvelope> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        
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
