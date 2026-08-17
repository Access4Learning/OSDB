// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import net.jqwik.api.*;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for trace ID generation.
 * 
 * <p>These tests verify that the OpenTelemetry SDK correctly generates
 * valid W3C Trace IDs for all HTTP requests and operations.
 * 
 * <p>This test uses a standalone OpenTelemetry SDK configuration with
 * a logging exporter to avoid dependencies on external services.
 */
class TraceIdGenerationProperties {

    private static final OpenTelemetry openTelemetry = createOpenTelemetry();
    private static final Tracer tracer = openTelemetry.getTracer("test-service");
    
    /**
     * Creates a standalone OpenTelemetry SDK for testing.
     */
    private static OpenTelemetry createOpenTelemetry() {
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "test-service",
                ResourceAttributes.SERVICE_VERSION, "1.0.0-test"
            )));
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
            .setSampler(Sampler.alwaysOn())
            .build();
        
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(
                W3CTraceContextPropagator.getInstance()
            ))
            .build();
    }

    // W3C Trace ID format: 32 hexadecimal characters
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");
    
    // W3C Span ID format: 16 hexadecimal characters
    private static final Pattern SPAN_ID_PATTERN = Pattern.compile("^[0-9a-f]{16}$");

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<String> operationNames() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('.', '_', '-')
            .ofMinLength(1)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> httpMethods() {
        return Arbitraries.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    }

    @Provide
    Arbitrary<String> httpPaths() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('/', '-', '_')
            .ofMinLength(1)
            .ofMaxLength(100)
            .map(s -> "/" + s);
    }

    // ========== Property 1: Trace ID Generation for HTTP Requests ==========
    // **Validates: Requirements 1.1**

    /**
     * Feature: observability-platform, Property 1: Trace ID Generation for HTTP Requests
     * 
     * <p>For any HTTP request received by any service, a valid W3C Trace ID 
     * (32 hexadecimal characters) should be present in the trace context.
     */
    @Property(tries = 100)
    @Tag("Feature-observability-platform-Property-1-Trace-ID-Generation-for-HTTP-Requests")
    void traceIdIsGeneratedForEveryRequest(
            @ForAll("operationNames") String operationName,
            @ForAll("httpMethods") String httpMethod,
            @ForAll("httpPaths") String httpPath) {
        
        // Execute: Create a span simulating an HTTP request
        Span span = tracer.spanBuilder(operationName)
            .setAttribute("http.method", httpMethod)
            .setAttribute("http.url", httpPath)
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Assert: Span has a valid trace context
            SpanContext spanContext = span.getSpanContext();
            
            assertThat(spanContext.isValid())
                .as("Span context should be valid")
                .isTrue();
            
            // Assert: Trace ID is present and valid W3C format
            String traceId = spanContext.getTraceId();
            assertThat(traceId)
                .as("Trace ID should not be null or empty")
                .isNotNull()
                .isNotEmpty();
            
            assertThat(traceId)
                .as("Trace ID should be 32 hexadecimal characters (W3C format)")
                .matches(TRACE_ID_PATTERN);
            
            // Assert: Span ID is present and valid W3C format
            String spanId = spanContext.getSpanId();
            assertThat(spanId)
                .as("Span ID should not be null or empty")
                .isNotNull()
                .isNotEmpty();
            
            assertThat(spanId)
                .as("Span ID should be 16 hexadecimal characters (W3C format)")
                .matches(SPAN_ID_PATTERN);
            
            // Assert: Trace ID is not all zeros (invalid trace ID)
            assertThat(traceId)
                .as("Trace ID should not be all zeros")
                .isNotEqualTo("00000000000000000000000000000000");
            
            // Assert: Span ID is not all zeros (invalid span ID)
            assertThat(spanId)
                .as("Span ID should not be all zeros")
                .isNotEqualTo("0000000000000000");
            
        } finally {
            span.end();
        }
    }

    /**
     * Feature: observability-platform, Property 1: Trace ID Generation for HTTP Requests
     * 
     * <p>Verifies that each new span gets a unique trace ID when no parent context exists.
     */
    @Property(tries = 100)
    @Tag("Feature-observability-platform-Property-1-Trace-ID-Generation-for-HTTP-Requests")
    void eachNewTraceHasUniqueTraceId(@ForAll("operationNames") String operationName) {
        
        // Execute: Create two independent spans (no parent context)
        Span span1 = tracer.spanBuilder(operationName + "-1").startSpan();
        String traceId1 = span1.getSpanContext().getTraceId();
        span1.end();
        
        Span span2 = tracer.spanBuilder(operationName + "-2").startSpan();
        String traceId2 = span2.getSpanContext().getTraceId();
        span2.end();
        
        // Assert: Each span has a different trace ID (they are independent traces)
        assertThat(traceId1)
            .as("Independent spans should have different trace IDs")
            .isNotEqualTo(traceId2);
    }

    /**
     * Feature: observability-platform, Property 1: Trace ID Generation for HTTP Requests
     * 
     * <p>Verifies that child spans inherit the parent's trace ID.
     */
    @Property(tries = 100)
    @Tag("Feature-observability-platform-Property-1-Trace-ID-Generation-for-HTTP-Requests")
    void childSpansInheritParentTraceId(@ForAll("operationNames") String operationName) {
        
        // Execute: Create a parent span and a child span
        Span parentSpan = tracer.spanBuilder(operationName + "-parent").startSpan();
        String parentTraceId = parentSpan.getSpanContext().getTraceId();
        
        try (Scope scope = parentSpan.makeCurrent()) {
            Span childSpan = tracer.spanBuilder(operationName + "-child").startSpan();
            String childTraceId = childSpan.getSpanContext().getTraceId();
            childSpan.end();
            
            // Assert: Child span has the same trace ID as parent
            assertThat(childTraceId)
                .as("Child span should inherit parent's trace ID")
                .isEqualTo(parentTraceId);
            
        } finally {
            parentSpan.end();
        }
    }

    /**
     * Feature: observability-platform, Property 1: Trace ID Generation for HTTP Requests
     * 
     * <p>Verifies that the tracer is properly initialized and not a no-op.
     */
    @Property(tries = 10)
    @Tag("Feature-observability-platform-Property-1-Trace-ID-Generation-for-HTTP-Requests")
    void tracerIsProperlyInitialized(@ForAll("operationNames") String operationName) {
        
        // Assert: Tracer is not null
        assertThat(tracer)
            .as("Tracer should be initialized")
            .isNotNull();
        
        // Assert: OpenTelemetry is not null
        assertThat(openTelemetry)
            .as("OpenTelemetry should be initialized")
            .isNotNull();
        
        // Execute: Create a span
        Span span = tracer.spanBuilder(operationName).startSpan();
        
        try {
            // Assert: Span is recording (not a no-op span)
            assertThat(span.isRecording())
                .as("Span should be recording (not a no-op)")
                .isTrue();
            
            // Assert: Span context is valid
            assertThat(span.getSpanContext().isValid())
                .as("Span context should be valid")
                .isTrue();
            
        } finally {
            span.end();
        }
    }
}
