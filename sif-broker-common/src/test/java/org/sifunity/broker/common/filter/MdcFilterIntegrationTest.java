// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.filter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration tests for MdcFilter with real OpenTelemetry spans.
 * 
 * <p>These tests verify the complete integration between MdcFilter and OpenTelemetry,
 * ensuring that trace IDs and span IDs are correctly extracted from active spans
 * and populated into the MDC.
 * 
 * <p>Requirements: 5.3, 5.4, 6.1, 6.2
 */
@ExtendWith(MockitoExtension.class)
class MdcFilterIntegrationTest {
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;
    
    private MdcFilter mdcFilter;
    private OpenTelemetry openTelemetry;
    private Tracer tracer;
    private InMemorySpanExporter spanExporter;
    
    @BeforeEach
    void setUp() {
        mdcFilter = new MdcFilter();
        MDC.clear();
        
        // Set up OpenTelemetry with in-memory exporter for testing
        // Don't use buildAndRegisterGlobal() to avoid conflicts between tests
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
        
        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
        
        tracer = openTelemetry.getTracer("test-tracer");
    }
    
    @AfterEach
    void tearDown() {
        MDC.clear();
        spanExporter.reset();
    }
    
    /**
     * Test that MDC is populated with trace ID and span ID from an active span.
     * This is the primary integration test for log-trace correlation.
     * Requirements: 5.3, 6.1, 6.2
     */
    @Test
    void shouldPopulateMdcWithTraceAndSpanIdsFromActiveSpan() throws IOException, ServletException {
        // Given: An active OpenTelemetry span
        Span span = tracer.spanBuilder("test-operation").startSpan();
        String expectedTraceId = span.getSpanContext().getTraceId();
        String expectedSpanId = span.getSpanContext().getSpanId();
        
        // And: A request with correlation ID
        String expectedCorrelationId = "test-correlation-id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(expectedCorrelationId);
        
        // Capture MDC state during filter execution
        doAnswer(invocation -> {
            // Verify MDC contains all expected values
            String actualTraceId = MDC.get("trace_id");
            String actualSpanId = MDC.get("span_id");
            String actualCorrelationId = MDC.get("correlation_id");
            
            assertThat(actualTraceId).isEqualTo(expectedTraceId);
            assertThat(actualSpanId).isEqualTo(expectedSpanId);
            assertThat(actualCorrelationId).isEqualTo(expectedCorrelationId);
            
            return null;
        }).when(filterChain).doFilter(request, response);
        
        // When: Filter is executed within span scope
        try (Scope scope = span.makeCurrent()) {
            mdcFilter.doFilter(request, response, filterChain);
        } finally {
            span.end();
        }
        
        // Then: Filter chain was called
        verify(filterChain).doFilter(request, response);
        
        // And: MDC is cleared after request
        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("span_id")).isNull();
        assertThat(MDC.get("correlation_id")).isNull();
    }
    
    /**
     * Test that MDC is populated correctly with nested spans.
     * This verifies that the filter always uses the current (innermost) span.
     * Requirements: 5.3, 6.1, 6.2
     */
    @Test
    void shouldPopulateMdcWithCurrentSpanInNestedSpans() throws IOException, ServletException {
        // Given: A parent span
        Span parentSpan = tracer.spanBuilder("parent-operation").startSpan();
        
        // And: A child span
        Span childSpan;
        try (Scope parentScope = parentSpan.makeCurrent()) {
            childSpan = tracer.spanBuilder("child-operation").startSpan();
        }
        
        String expectedTraceId = childSpan.getSpanContext().getTraceId();
        String expectedSpanId = childSpan.getSpanContext().getSpanId();
        
        // Verify trace IDs match (same trace)
        assertThat(expectedTraceId).isEqualTo(parentSpan.getSpanContext().getTraceId());
        // Verify span IDs differ (different spans)
        assertThat(expectedSpanId).isNotEqualTo(parentSpan.getSpanContext().getSpanId());
        
        // Capture MDC state during filter execution
        doAnswer(invocation -> {
            // Verify MDC contains child span IDs (current span)
            String actualTraceId = MDC.get("trace_id");
            String actualSpanId = MDC.get("span_id");
            
            assertThat(actualTraceId).isEqualTo(expectedTraceId);
            assertThat(actualSpanId).isEqualTo(expectedSpanId);
            
            return null;
        }).when(filterChain).doFilter(request, response);
        
        // When: Filter is executed within child span scope
        try (Scope childScope = childSpan.makeCurrent()) {
            mdcFilter.doFilter(request, response, filterChain);
        } finally {
            childSpan.end();
            parentSpan.end();
        }
        
        // Then: Filter chain was called
        verify(filterChain).doFilter(request, response);
    }
    
    /**
     * Test that MDC handles the case when no span is active.
     * The filter should not fail and should still process correlation ID.
     * Requirements: 5.4, 6.1, 6.2
     */
    @Test
    void shouldHandleNoActiveSpanGracefully() throws IOException, ServletException {
        // Given: No active span (Span.current() will return invalid span)
        // And: A request with correlation ID
        String expectedCorrelationId = "test-correlation-id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(expectedCorrelationId);
        
        // Capture MDC state during filter execution
        doAnswer(invocation -> {
            // Verify MDC contains correlation ID but no trace/span IDs
            String actualCorrelationId = MDC.get("correlation_id");
            String actualTraceId = MDC.get("trace_id");
            String actualSpanId = MDC.get("span_id");
            
            assertThat(actualCorrelationId).isEqualTo(expectedCorrelationId);
            // Trace and span IDs should be null when no active span
            assertThat(actualTraceId).isNull();
            assertThat(actualSpanId).isNull();
            
            return null;
        }).when(filterChain).doFilter(request, response);
        
        // When: Filter is executed without active span
        mdcFilter.doFilter(request, response, filterChain);
        
        // Then: Filter chain was called without error
        verify(filterChain).doFilter(request, response);
    }
    
    /**
     * Test that MDC is correctly populated across multiple sequential requests.
     * This simulates real-world usage where the same filter instance handles multiple requests.
     * Requirements: 5.3, 5.4, 6.1, 6.2
     */
    @Test
    void shouldHandleMultipleSequentialRequests() throws IOException, ServletException {
        // First request
        Span firstSpan = tracer.spanBuilder("first-operation").startSpan();
        String firstTraceId = firstSpan.getSpanContext().getTraceId();
        String firstSpanId = firstSpan.getSpanContext().getSpanId();
        String firstCorrelationId = "first-correlation-id";
        
        when(request.getHeader("X-Correlation-ID")).thenReturn(firstCorrelationId);
        
        doAnswer(invocation -> {
            assertThat(MDC.get("trace_id")).isEqualTo(firstTraceId);
            assertThat(MDC.get("span_id")).isEqualTo(firstSpanId);
            assertThat(MDC.get("correlation_id")).isEqualTo(firstCorrelationId);
            return null;
        }).when(filterChain).doFilter(request, response);
        
        try (Scope scope = firstSpan.makeCurrent()) {
            mdcFilter.doFilter(request, response, filterChain);
        } finally {
            firstSpan.end();
        }
        
        // Verify MDC is cleared
        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("span_id")).isNull();
        assertThat(MDC.get("correlation_id")).isNull();
        
        // Second request with different span and correlation ID
        Span secondSpan = tracer.spanBuilder("second-operation").startSpan();
        String secondTraceId = secondSpan.getSpanContext().getTraceId();
        String secondSpanId = secondSpan.getSpanContext().getSpanId();
        String secondCorrelationId = "second-correlation-id";
        
        HttpServletRequest secondRequest = mock(HttpServletRequest.class);
        HttpServletResponse secondResponse = mock(HttpServletResponse.class);
        when(secondRequest.getHeader("X-Correlation-ID")).thenReturn(secondCorrelationId);
        
        doAnswer(invocation -> {
            // Verify second request has its own IDs, not first request's
            assertThat(MDC.get("trace_id")).isEqualTo(secondTraceId);
            assertThat(MDC.get("span_id")).isEqualTo(secondSpanId);
            assertThat(MDC.get("correlation_id")).isEqualTo(secondCorrelationId);
            
            assertThat(MDC.get("trace_id")).isNotEqualTo(firstTraceId);
            assertThat(MDC.get("span_id")).isNotEqualTo(firstSpanId);
            assertThat(MDC.get("correlation_id")).isNotEqualTo(firstCorrelationId);
            
            return null;
        }).when(filterChain).doFilter(secondRequest, secondResponse);
        
        try (Scope scope = secondSpan.makeCurrent()) {
            mdcFilter.doFilter(secondRequest, secondResponse, filterChain);
        } finally {
            secondSpan.end();
        }
        
        // Verify MDC is cleared again
        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("span_id")).isNull();
        assertThat(MDC.get("correlation_id")).isNull();
    }
}
