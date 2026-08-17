// SPDX-License-Identifier: MIT
package org.sifunity.gateway.exception;

import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Instant;
import java.util.UUID;

/**
 * Global error handler for the gateway.
 * 
 * <p>This handler converts connection errors to 404 NOT_FOUND responses,
 * which is the expected behavior when a backend service is unavailable.</p>
 */
@Component
@Order(-2) // Higher priority than DefaultErrorWebExceptionHandler
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);
    
    public GlobalErrorWebExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties webProperties,
            ApplicationContext applicationContext,
            ServerCodecConfigurer serverCodecConfigurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        this.setMessageWriters(serverCodecConfigurer.getWriters());
    }
    
    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }
    
    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        // Check if response already has a 4xx status set by a filter (e.g., RateLimitFilter)
        var statusCode = request.exchange().getResponse().getStatusCode();
        if (statusCode != null && statusCode.is4xxClientError()) {
            log.debug("Response already has 4xx status: {}, preserving it", statusCode);
            
            // Preserve the existing status and headers set by the filter
            ServerResponse.BodyBuilder responseBuilder = ServerResponse
                .status(statusCode)
                .contentType(MediaType.APPLICATION_JSON);
            
            // Preserve headers from the response
            request.exchange().getResponse().getHeaders().forEach((name, values) -> {
                values.forEach(value -> responseBuilder.header(name, value));
            });
            
            HttpStatus existingStatus = HttpStatus.valueOf(statusCode.value());
            SifErrorResponse sifError = new SifErrorResponse(
                UUID.randomUUID().toString(),
                statusCode.value(),
                "GATEWAY_ERROR",
                existingStatus.getReasonPhrase(),
                null,
                "BROKER",
                Instant.now(),
                null,
                null
            );
            SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.of(sifError);
            return responseBuilder.body(BodyInserters.fromValue(envelope));
        }
        
        Throwable error = getError(request);
        HttpStatus status = determineHttpStatus(error);
        
        log.debug("Handling error: {} with status: {}", error.getClass().getSimpleName(), status);
        
        // Build response with preserved headers
        ServerResponse.BodyBuilder responseBuilder = ServerResponse
            .status(status)
            .contentType(MediaType.APPLICATION_JSON);
        
        // Preserve X-Correlation-Id header if present in request
        String correlationId = request.headers().firstHeader("X-Correlation-Id");
        if (correlationId != null) {
            responseBuilder.header("X-Correlation-Id", correlationId);
        }
        
        // Preserve environment headers if present in request (they were added by EnvironmentContextFilter)
        String environmentId = request.headers().firstHeader("X-Environment-Id");
        if (environmentId != null) {
            responseBuilder.header("X-Environment-Id", environmentId);
        }
        
        String environmentName = request.headers().firstHeader("X-Environment-Name");
        if (environmentName != null) {
            responseBuilder.header("X-Environment-Name", environmentName);
        }
        
        SifErrorResponse sifError = new SifErrorResponse(
            UUID.randomUUID().toString(),
            status.value(),
            "GATEWAY_ERROR",
            status.getReasonPhrase(),
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.of(sifError);
        return responseBuilder.body(BodyInserters.fromValue(envelope));
    }
    
    private HttpStatus determineHttpStatus(Throwable error) {
        // Check if this is a connection error (backend service unavailable)
        if (isConnectionError(error)) {
            return HttpStatus.NOT_FOUND;
        }
        
        // Default to 500 for other errors
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
    
    private boolean isConnectionError(Throwable error) {
        if (error == null) {
            return false;
        }
        
        // Check if this is a ConnectException or contains one in the cause chain
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException) {
                return true;
            }
            if (current.getClass().getName().contains("AnnotatedConnectException")) {
                return true;
            }
            current = current.getCause();
        }
        
        return false;
    }
}
