// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import org.sifunity.gateway.metrics.GatewayMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filter that records request metrics (count and duration per route).
 * 
 * <p>This filter runs last in the filter chain (Order 100) to capture
 * the complete request processing time and final response status.</p>
 * 
 * <p>Metrics Recorded:</p>
 * <ul>
 *   <li>gateway.requests - Counter for total requests per route</li>
 *   <li>gateway.request.duration - Timer for request duration per route</li>
 * </ul>
 * 
 * <p>Requirements: 10.3</p>
 * 
 * @see GatewayMetricsService
 */
@Component
public class MetricsFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(MetricsFilter.class);
    
    private final GatewayMetricsService metricsService;
    
    /**
     * Constructs a MetricsFilter with the specified metrics service.
     * 
     * @param metricsService the metrics service for recording request metrics
     */
    public MetricsFilter(GatewayMetricsService metricsService) {
        this.metricsService = metricsService;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        
        // Extract route prefix (e.g., "/requests", "/responses", "/environments")
        String route = extractRoutePrefix(path);
        
        // Record start time
        long startTime = System.currentTimeMillis();
        
        // Continue filter chain and record metrics after completion
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // Calculate duration
                    long duration = System.currentTimeMillis() - startTime;
                    
                    // Get response status code
                    ServerHttpResponse response = exchange.getResponse();
                    int statusCode = response.getStatusCode() != null ? 
                            response.getStatusCode().value() : 0;
                    
                    // Record metrics
                    metricsService.recordRequest(route, method, statusCode);
                    metricsService.recordRequestDuration(route, method, duration);
                    
                    log.debug("Request metrics recorded: route={}, method={}, status={}, duration={}ms",
                            route, method, statusCode, duration);
                });
    }
    
    /**
     * Extracts the route prefix from a request path.
     * 
     * <p>Examples:</p>
     * <ul>
     *   <li>/requests/StudentPersonal → /requests</li>
     *   <li>/responses/123 → /responses</li>
     *   <li>/environments → /environments</li>
     *   <li>/actuator/health → /actuator</li>
     * </ul>
     * 
     * @param path the request path
     * @return the route prefix
     */
    private String extractRoutePrefix(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "/";
        }
        
        // Remove leading slash and split by slash
        String[] parts = path.substring(1).split("/");
        
        // Return first segment as route prefix
        return "/" + (parts.length > 0 ? parts[0] : "");
    }
    
    @Override
    public int getOrder() {
        return 100; // Run last to capture complete request processing
    }
}
