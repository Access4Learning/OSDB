// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Filter that blocks all requests to internal API endpoints (/internal/*).
 * Runs with Order -1, before all other filters, to ensure internal endpoints
 * are blocked immediately without any processing.
 */
@Component
public class InternalApiBlockingFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;

    public InternalApiBlockingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        if (path.startsWith("/internal")) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try {
                SifErrorResponse error = new SifErrorResponse(
                        UUID.randomUUID().toString(), HttpStatus.FORBIDDEN.value(),
                        "ACCESS_001", "Access denied",
                        "Internal API endpoints are not accessible externally",
                        "BROKER", Instant.now(), null, null);
                byte[] bytes = objectMapper.writeValueAsBytes(SifErrorResponseEnvelope.of(error));
                DataBuffer buffer = response.bufferFactory().wrap(bytes);
                return response.writeWith(Mono.just(buffer));
            } catch (Exception e) {
                return response.setComplete();
            }
        }
        
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
