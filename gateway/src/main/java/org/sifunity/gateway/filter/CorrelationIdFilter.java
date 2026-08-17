// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filter that generates or preserves correlation IDs for request tracing.
 * 
 * <p>This filter runs first in the filter chain (Order 1) to establish a correlation ID
 * that can be used by all subsequent filters and backend services for distributed tracing.</p>
 * 
 * <p>Behavior:</p>
 * <ul>
 *   <li>If the request already contains an X-Correlation-Id header, it is preserved</li>
 *   <li>If no correlation ID is present, a new UUID is generated</li>
 *   <li>The correlation ID is injected into the request headers for backend services</li>
 *   <li>The correlation ID is added to the MDC for logging context</li>
 * </ul>
 * 
 * <p>Requirements: 4.3, 4.4, 9.1, 9.2</p>
 * 
 * @see SifHeaders#X_CORRELATION_ID
 * @see LoggingUtils#setCorrelationId(String)
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Check if correlation ID already exists
        String correlationId = request.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
        
        if (correlationId == null || correlationId.isBlank()) {
            // Generate new correlation ID
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("Preserving existing correlation ID: {}", correlationId);
        }
        
        // Add to MDC for logging
        LoggingUtils.setCorrelationId(correlationId);
        
        // Inject into request headers for backend services
        ServerHttpRequest mutatedRequest = request.mutate()
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
            .doFinally(signalType -> {
                // Clear MDC after request processing to prevent context leakage
                LoggingUtils.clearContext();
            });
    }
    
    @Override
    public int getOrder() {
        return 1; // First filter in the chain
    }
}
