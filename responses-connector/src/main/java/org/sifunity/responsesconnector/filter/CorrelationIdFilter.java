// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds a correlation ID to the MDC (Mapped Diagnostic Context) for request tracing.
 * The correlation ID is either extracted from the X-Correlation-Id header or generated if not present.
 * This enables tracking of requests across multiple services and log entries.
 * 
 * Requirements: 13.1, 13.6 (Observability and Logging)
 */
@Component
@Order(1)
@Slf4j
public class CorrelationIdFilter implements Filter {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Extract or generate correlation ID
        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        // Add to MDC for logging
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        
        // Add to response header for client tracking
        httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
        
        try {
            log.debug("Processing request with correlation ID: {}", correlationId);
            chain.doFilter(request, response);
        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.clear();
        }
    }
}
