// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.sifunity.broker.common.constant.SifHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filter that manages distributed tracing context using OpenTelemetry.
 * 
 * <p>This filter runs very early in the filter chain (Order 0) to establish
 * trace context before other filters execute. It works in conjunction with
 * CorrelationIdFilter to provide comprehensive request tracking.</p>
 * 
 * <p>Behavior:</p>
 * <ul>
 *   <li>Extracts W3C trace context from incoming request headers (traceparent)</li>
 *   <li>Creates a new trace if no context is present</li>
 *   <li>Extracts or generates correlation ID from X-Correlation-ID header</li>
 *   <li>Associates correlation ID with trace as a span attribute</li>
 *   <li>Adds X-Trace-ID and X-Correlation-ID to response headers</li>
 *   <li>Propagates trace context to downstream services</li>
 *   <li>Records span status based on HTTP response code</li>
 * </ul>
 * 
 * <p>Requirements: 1.1, 1.2, 4.1, 4.2</p>
 * 
 * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
 * @see CorrelationIdFilter
 */
@Component
@ConditionalOnProperty(name = "observability.enabled", havingValue = "true", matchIfMissing = true)
public class TraceContextFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(TraceContextFilter.class);
    
    private final Tracer tracer;
    private final TextMapPropagator propagator;
    
    /**
     * Constructs a TraceContextFilter with the specified tracer and propagator.
     * 
     * @param tracer the OpenTelemetry tracer for creating spans
     * @param propagator the W3C trace context propagator for extracting/injecting context
     */
    public TraceContextFilter(Tracer tracer, TextMapPropagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Extract trace context from incoming request headers.
        // Use Context.root() (not Context.current()) to prevent trace leakage
        // between requests on the same Netty event loop thread.
        Context extractedContext = propagator.extract(
            Context.root(),
            request.getHeaders(),
            HttpHeadersGetter.INSTANCE
        );
        
        // Get or generate correlation ID
        String correlationId = request.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("Using existing correlation ID: {}", correlationId);
        }
        
        // Start span with extracted context as parent
        Span span = tracer.spanBuilder("gateway.request")
            .setParent(extractedContext)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("http.method", request.getMethod() != null ? request.getMethod().name() : "UNKNOWN")
            .setAttribute("http.url", request.getURI().toString())
            .setAttribute("http.target", request.getPath().value())
            .setAttribute("correlation.id", correlationId)
            .startSpan();
        
        String traceId = span.getSpanContext().getTraceId();
        log.debug("Processing request with trace ID: {}, correlation ID: {}", traceId, correlationId);
        
        // Add trace ID and correlation ID to response headers
        exchange.getResponse().getHeaders().add("X-Trace-ID", traceId);
        exchange.getResponse().getHeaders().add(SifHeaders.X_CORRELATION_ID, correlationId);
        
        // Inject trace context into request headers for downstream services
        ServerHttpRequest mutatedRequest = request.mutate()
            .headers(headers -> propagator.inject(
                Context.current().with(span),
                headers,
                HttpHeadersSetter.INSTANCE
            ))
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        
        // Continue filter chain with span in context.
        // The timeout ensures the span always ends within a bounded duration,
        // preventing 10h+ phantom traces when Docker suspends or connections drop.
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
            .timeout(java.time.Duration.ofMinutes(15))
            .doOnSuccess(v -> {
                // Record successful completion
                int statusCode = exchange.getResponse().getStatusCode() != null ?
                    exchange.getResponse().getStatusCode().value() : 200;
                span.setAttribute("http.status_code", statusCode);
                
                if (statusCode >= 400) {
                    span.setStatus(StatusCode.ERROR, "HTTP " + statusCode);
                } else {
                    span.setStatus(StatusCode.OK);
                }
                
                log.debug("Request completed successfully: trace ID={}, status={}", traceId, statusCode);
            })
            .doOnError(error -> {
                // Record error (includes TimeoutException from the .timeout() above)
                span.recordException(error);
                span.setStatus(StatusCode.ERROR, error.getMessage());
                if (error instanceof java.util.concurrent.TimeoutException) {
                    log.warn("Request timed out: trace ID={}", traceId);
                } else {
                    log.error("Request failed: trace ID={}, error={}", traceId, error.getMessage());
                }
            })
            .doFinally(signalType -> {
                // Always end the span
                span.end();
            })
            .contextWrite(ctx -> ctx.put(Span.class, span));
    }
    
    @Override
    public int getOrder() {
        return 0; // Run before CorrelationIdFilter (order 1)
    }
    
    /**
     * TextMapGetter implementation for extracting trace context from HTTP headers.
     */
    private static class HttpHeadersGetter implements TextMapGetter<HttpHeaders> {
        
        static final HttpHeadersGetter INSTANCE = new HttpHeadersGetter();
        
        @Override
        public Iterable<String> keys(HttpHeaders headers) {
            return headers.keySet();
        }
        
        @Nullable
        @Override
        public String get(@Nullable HttpHeaders headers, String key) {
            if (headers == null) {
                return null;
            }
            return headers.getFirst(key);
        }
    }
    
    /**
     * TextMapSetter implementation for injecting trace context into HTTP headers.
     */
    private static class HttpHeadersSetter implements io.opentelemetry.context.propagation.TextMapSetter<HttpHeaders> {
        
        static final HttpHeadersSetter INSTANCE = new HttpHeadersSetter();
        
        @Override
        public void set(@Nullable HttpHeaders headers, String key, String value) {
            if (headers != null) {
                headers.set(key, value);
            }
        }
    }
}
