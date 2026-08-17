// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that populates the MDC (Mapped Diagnostic Context) with trace and correlation IDs
 * for log-trace correlation. This enables automatic inclusion of trace context in all log entries.
 * 
 * <p>The filter extracts:
 * <ul>
 *   <li>trace_id: The W3C trace ID from the current OpenTelemetry span</li>
 *   <li>span_id: The W3C span ID from the current OpenTelemetry span</li>
 *   <li>correlation_id: The business correlation ID from the X-Correlation-ID header</li>
 * </ul>
 * 
 * <p>These values are automatically included in structured log entries via the Logback
 * configuration, enabling correlation between traces in Jaeger and logs in Kibana.
 * 
 * <p>The MDC is cleared in a finally block to prevent memory leaks and context pollution
 * across requests in thread pools.
 * 
 * <p>Requirements: 5.3, 5.4, 6.1, 6.2 (Log-Trace Correlation)
 * 
 * @see org.slf4j.MDC
 * @see io.opentelemetry.api.trace.Span
 */
@Component
@Order(2) // Execute after CorrelationIdFilter (Order 1)
@Slf4j
public class MdcFilter implements Filter {
    
    private static final String TRACE_ID_KEY = "trace_id";
    private static final String SPAN_ID_KEY = "span_id";
    private static final String CORRELATION_ID_KEY = "correlation_id";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    
    /**
     * Populates MDC with trace and correlation IDs for the duration of the request.
     * 
     * @param request the servlet request
     * @param response the servlet response
     * @param chain the filter chain
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            // Extract trace ID and span ID from current OpenTelemetry span
            Span currentSpan = Span.current();
            if (currentSpan != null) {
                SpanContext spanContext = currentSpan.getSpanContext();
                if (spanContext.isValid()) {
                    String traceId = spanContext.getTraceId();
                    String spanId = spanContext.getSpanId();
                    
                    MDC.put(TRACE_ID_KEY, traceId);
                    MDC.put(SPAN_ID_KEY, spanId);
                    
                    log.debug("Populated MDC with trace_id: {}, span_id: {}", traceId, spanId);
                }
            }
            
            // Extract correlation ID from request header
            if (request instanceof HttpServletRequest) {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
                
                if (correlationId != null && !correlationId.isBlank()) {
                    MDC.put(CORRELATION_ID_KEY, correlationId);
                    log.debug("Populated MDC with correlation_id: {}", correlationId);
                }
            }
            
            // Continue filter chain with populated MDC
            chain.doFilter(request, response);
            
        } finally {
            // Clear MDC to prevent memory leaks and context pollution
            // This is critical in thread pool environments where threads are reused
            MDC.clear();
            log.trace("Cleared MDC after request completion");
        }
    }
}
