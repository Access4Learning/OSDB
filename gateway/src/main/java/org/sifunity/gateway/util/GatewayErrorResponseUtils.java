// SPDX-License-Identifier: MIT
package org.sifunity.gateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public final class GatewayErrorResponseUtils {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorResponseUtils.class);

    private GatewayErrorResponseUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Mono<Void> writeErrorResponse(
            ServerWebExchange exchange,
            ObjectMapper objectMapper,
            HttpStatus status,
            String errorCode,
            String message,
            String description) {

        SifErrorResponse errorResponse = new SifErrorResponse(
                UUID.randomUUID().toString(), status.value(), errorCode, message, description, "BROKER", Instant.now(), null, null);
        return writeErrorResponse(exchange, objectMapper, status, errorResponse);
    }

    public static Mono<Void> writeErrorResponse(
            ServerWebExchange exchange,
            ObjectMapper objectMapper,
            HttpStatus status,
            SifErrorResponse errorResponse) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(SifHeaders.X_CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            response.getHeaders().set(SifHeaders.X_CORRELATION_ID, correlationId);
        }

        try {
            SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.of(errorResponse);
            byte[] bytes = objectMapper.writeValueAsBytes(envelope);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            return response.setComplete();
        }
    }
}
