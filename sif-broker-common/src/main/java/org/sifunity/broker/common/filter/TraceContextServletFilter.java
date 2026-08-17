// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;

/**
 * Servlet filter that extracts W3C trace context (traceparent header) from incoming
 * HTTP requests and creates a server span for the request duration.
 *
 * <p>This filter enables distributed tracing across services: the gateway injects
 * the {@code traceparent} header into downstream requests, and this filter extracts
 * it to establish parent-child span relationships. All spans created by AOP aspects
 * (repository, Redis, HTTP client) within the request become children of this span.</p>
 *
 * <p>Runs at Order 0 (before CorrelationIdFilter and MdcFilter) so the trace context
 * is available for MDC population and all downstream processing.</p>
 *
 * <p>Only active for servlet-based services (not the reactive gateway).</p>
 */
@Component
@Order(0) // Run before CorrelationIdFilter (1) and MdcFilter (2)
@ConditionalOnProperty(name = "observability.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Slf4j
public class TraceContextServletFilter implements Filter {

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    public TraceContextServletFilter(Tracer tracer, TextMapPropagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
        log.info(">>>>>> TraceContextServletFilter initialized — incoming requests will produce server spans");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        // Extract W3C trace context from incoming request headers
        Context extractedContext = propagator.extract(
                Context.current(),
                httpRequest,
                HttpServletRequestGetter.INSTANCE
        );

        // Create a server span as child of the extracted context
        String spanName = httpRequest.getMethod() + " " + httpRequest.getRequestURI();
        Span span = tracer.spanBuilder(spanName)
                .setParent(extractedContext)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.method", httpRequest.getMethod())
                .setAttribute("http.url", httpRequest.getRequestURL().toString())
                .setAttribute("http.target", httpRequest.getRequestURI())
                .startSpan();

        // Make this span the current span so child spans (from AOP aspects) attach to it
        try (Scope scope = span.makeCurrent()) {
            chain.doFilter(request, response);

            if (response instanceof HttpServletResponse httpResponse) {
                int statusCode = httpResponse.getStatus();
                span.setAttribute("http.status_code", statusCode);
                if (statusCode >= 400) {
                    span.setStatus(StatusCode.ERROR, "HTTP " + statusCode);
                } else {
                    span.setStatus(StatusCode.OK);
                }
            }
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * TextMapGetter for extracting trace context from HttpServletRequest headers.
     */
    private static class HttpServletRequestGetter implements TextMapGetter<HttpServletRequest> {

        static final HttpServletRequestGetter INSTANCE = new HttpServletRequestGetter();

        @Override
        public Iterable<String> keys(HttpServletRequest request) {
            return Collections.list(request.getHeaderNames());
        }

        @Nullable
        @Override
        public String get(@Nullable HttpServletRequest request, String key) {
            if (request == null) return null;
            return request.getHeader(key);
        }
    }
}
