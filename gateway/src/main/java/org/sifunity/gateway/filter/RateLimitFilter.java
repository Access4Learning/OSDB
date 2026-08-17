// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.util.LoggingUtils;
import org.sifunity.gateway.config.RateLimitConfiguration;
import org.sifunity.gateway.metrics.GatewayMetricsService;
import org.sifunity.gateway.ratelimit.RateLimitResponse;
import org.sifunity.gateway.ratelimit.RedisRateLimiter;
import org.sifunity.gateway.util.GatewayErrorResponseUtils;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Filter that enforces request rate limits per environment.
 * 
 * <p>This filter runs eighth in the filter chain (Order 8) after the EnvironmentContextFilter.
 * It enforces rate limits based on environment type using Redis-backed sliding window
 * rate limiting.</p>
 * 
 * <p>Behavior:</p>
 * <ul>
 *   <li>Exempt paths (no environment context) skip rate limiting</li>
 *   <li>Extracts environment ID and type from exchange attributes</li>
 *   <li>Gets rate limit for environment type from configuration</li>
 *   <li>Checks rate limit using RedisRateLimiter</li>
 *   <li>Returns 429 Too Many Requests with Retry-After header if exceeded</li>
 *   <li>Allows request to proceed if within rate limit</li>
 * </ul>
 * 
 * <p>Rate Limits by Environment Type:</p>
 * <ul>
 *   <li>CONSUMER: 100 requests per second (default)</li>
 *   <li>PROVIDER: 100 requests per second (default)</li>
 *   <li>BROKER: 200 requests per second (default)</li>
 * </ul>
 * 
 * <p>Requirements: 8.1, 8.2, 8.4, 8.5</p>
 * 
 * @see RedisRateLimiter
 * @see RateLimitConfiguration
 */
@Component
@ConditionalOnProperty(name = "sif.broker.gateway.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    
    private final RedisRateLimiter rateLimiter;
    private final RateLimitConfiguration rateLimitConfig;
    private final ObjectMapper objectMapper;
    private final GatewayMetricsService metricsService;
    private final Tracer tracer;
    
    public RateLimitFilter(
            RedisRateLimiter rateLimiter,
            RateLimitConfiguration rateLimitConfig,
            ObjectMapper objectMapper,
            GatewayMetricsService metricsService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Tracer tracer) {
        this.rateLimiter = rateLimiter;
        this.rateLimitConfig = rateLimitConfig;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.tracer = tracer;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();
        
        // Extract environment ID from exchange attributes (set by AuthenticationFilter)
        UUID environmentId = exchange.getAttribute("environmentId");
        
        // Skip rate limiting for exempt paths (no environment context)
        if (environmentId == null) {
            log.debug("Skipping rate limiting for path {} (no environment context)", path);
            return chain.filter(exchange);
        }
        
        // Skip rate limiting for exempt paths
        if (isExemptPath(path, method)) {
            log.debug("Skipping rate limiting for exempt path: {}", path);
            return chain.filter(exchange);
        }
        
        // Extract environment type from exchange attributes
        EnvironmentType envType = exchange.getAttribute("environmentType");
        final EnvironmentType environmentType = (envType != null) ? envType : EnvironmentType.BROKERED;
        
        if (envType == null) {
            log.warn("Environment type not found for environment {}, using default rate limit", environmentId);
        }
        
        // Get rate limit for environment type
        int requestsPerSecond = rateLimitConfig.getRequestsPerSecond(environmentType);
        
        log.debug("Checking rate limit for environment {} (type: {}, limit: {} req/s)",
            environmentId, environmentType, requestsPerSecond);
        
        // Parse URL to extract domain/solution context
        UrlPrefixParser.ParsedPath parsed = UrlPrefixParser.parse(path);
        String domainId = parsed.domainId();
        String solutionId = parsed.solutionId();

        // Check rate limit using domain/solution-scoped key if available, otherwise legacy key
        Mono<RateLimitResponse> rateLimitCheck;
        if (domainId != null && solutionId != null) {
            rateLimitCheck = rateLimiter.isAllowed(domainId, solutionId, environmentId.toString(), requestsPerSecond);
        } else {
            rateLimitCheck = rateLimiter.isAllowed(environmentId.toString(), requestsPerSecond);
        }

        // Wrap with span if tracing is enabled
        if (tracer != null) {
            final String traceDomainId = domainId;
            final String traceSolutionId = solutionId;
            rateLimitCheck = Mono.deferContextual(ctx -> {
                Span parentSpan = ctx.hasKey(Span.class) ? ctx.get(Span.class) : Span.current();
                Span span = tracer.spanBuilder("gateway.rate_limit")
                        .setParent(Context.current().with(parentSpan))
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute("environment.id", environmentId.toString())
                        .setAttribute("environment.type", environmentType.name())
                        .setAttribute("rate_limit.requests_per_second", requestsPerSecond)
                        .startSpan();

                Mono<RateLimitResponse> tracedCheck;
                if (traceDomainId != null && traceSolutionId != null) {
                    tracedCheck = rateLimiter.isAllowed(traceDomainId, traceSolutionId, environmentId.toString(), requestsPerSecond);
                } else {
                    tracedCheck = rateLimiter.isAllowed(environmentId.toString(), requestsPerSecond);
                }

                return Mono.using(
                        span::makeCurrent,
                        scope -> tracedCheck
                                .doOnSuccess(r -> { span.setStatus(StatusCode.OK); span.end(); })
                                .doOnError(e -> { span.recordException(e); span.setStatus(StatusCode.ERROR, e.getMessage()); span.end(); }),
                        Scope::close
                );
            });
        }

        return rateLimitCheck
            .flatMap(response -> {
                if (response.isAllowed()) {
                    log.debug("Rate limit check passed for environment {}", environmentId);
                    return chain.filter(exchange);
                } else {
                    log.warn("Rate limit exceeded for environment {} (type: {}, limit: {} req/s)",
                        environmentId, environmentType, requestsPerSecond);
                    return handleRateLimitExceeded(exchange, response.getRetryAfterSeconds());
                }
            });
    }
    
    /**
     * Checks if a path is exempt from rate limiting.
     * 
     * <p>Exempt paths:</p>
     * <ul>
     *   <li>POST /environments - Environment registration endpoint</li>
     *   <li>/actuator/health - Health check endpoint</li>
     * </ul>
     * 
     * @param path the request path
     * @param method the HTTP method
     * @return true if the path is exempt from rate limiting, false otherwise
     */
    private boolean isExemptPath(String path, HttpMethod method) {
        // POST /environments is exempt (registration)
        if (path.equals("/environments") && method == HttpMethod.POST) {
            return true;
        }
        // Health checks are exempt
        if (path.startsWith("/actuator/health")) {
            return true;
        }
        return false;
    }
    
    /**
     * Handles rate limit exceeded by creating a 429 error response.
     * 
     * <p>This method logs the rate limit violation with correlation ID context
     * and returns an HTTP 429 Too Many Requests response with:</p>
     * <ul>
     *   <li>SIF-compliant error response body</li>
     *   <li>Retry-After header indicating when to retry</li>
     *   <li>X-Correlation-Id header for request tracing</li>
     * </ul>
     * 
     * @param exchange the server web exchange
     * @param retryAfterSeconds the number of seconds to wait before retrying
     * @return a Mono that completes when the error response is written
     */
    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange, long retryAfterSeconds) {
        String correlationId = LoggingUtils.getCorrelationId();
        
        // Fallback: Get correlation ID from request headers if not in MDC
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = exchange.getRequest().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
        }
        
        UUID environmentId = exchange.getAttribute("environmentId");
        
        log.error("Rate limit exceeded [correlationId={}, environmentId={}]", 
            correlationId, environmentId);
        
        // Record rate limit exceeded metric
        if (environmentId != null) {
            metricsService.recordRateLimitExceeded(environmentId);
        }
        
        // Create SIF-compliant error response
        SifErrorResponse errorResponse = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.TOO_MANY_REQUESTS.value(),
            "RATE_001",
            "Rate limit exceeded",
            "Too many requests. Please retry after " + retryAfterSeconds + " seconds.",
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        // Set Retry-After header and MDC correlation ID BEFORE calling utility
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set("Retry-After", String.valueOf(retryAfterSeconds));
        if (correlationId != null && !correlationId.isBlank()) {
            response.getHeaders().set(SifHeaders.X_CORRELATION_ID, correlationId);
        }
        
        return GatewayErrorResponseUtils.writeErrorResponse(
                exchange, objectMapper, HttpStatus.TOO_MANY_REQUESTS, errorResponse);
    }
    
    @Override
    public int getOrder() {
        return 8; // Eighth filter in the chain (after EnvironmentContextFilter)
    }
}
