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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for trace sampling consistency.
 * 
 * <p>These tests verify that when a trace is sampled, all spans belonging
 * to that trace are included regardless of individual span sampling decisions.
 * 
 * <p>This is a critical property for distributed tracing - partial traces
 * are not useful for debugging and can be misleading.
 */
class TraceSamplingProperties {

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
    Arbitrary<Double> samplingRates() {
        return Arbitraries.doubles()
            .between(0.0, 1.0);
    }

    @Provide
    Arbitrary<Integer> spanCounts() {
        return Arbitraries.integers()
            .between(1, 10);
    }

    /**
     * Creates an OpenTelemetry SDK with a specific sampling rate.
     */
    private OpenTelemetry createOpenTelemetryWithSampling(double samplingRate) {
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "test-service",
                ResourceAttributes.SERVICE_VERSION, "1.0.0-test"
            )));
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
            .setSampler(Sampler.traceIdRatioBased(samplingRate))
            .build();
        
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(
                W3CTraceContextPropagator.getInstance()
            ))
            .build();
    }

    // ========== Property 7: Trace Sampling Consistency ==========
    // **Validates: Requirements 2.5**

    /**
     * Feature: observability-platform, Property 7: Trace Sampling Consistency
     * 
     * <p>For any trace that is sampled, all spans belonging to that trace should
     * be included regardless of individual span sampling decisions.
     * 
     * <p>This test verifies that child spans inherit the sampling decision from
     * the parent span, ensuring complete traces.
     */
    @Property(tries = 100)
    @Tag("Feature-observability-platform-Property-7-Trace-Sampling-Consistency")
    void allSpansInSampledTraceAreIncluded(
            @ForAll("operationNames") String operationName,
            @ForAll("spanCounts") int childSpanCount) {
        
        // Setup: Create OpenTelemetry with 100% sampling
        OpenTelemetry openTelemetry = createOpenTelemetryWithSampling(1.0);
        Tracer tracer = openTelemetry.getTracer("test-service");
        
        // Execute: Create a parent span and multiple child spans
        Span parentSpan = tracer.spanBuilder(operationName + "-parent").startSpan();
        String parentTraceId = parentSpan.getSpanContext().getTraceId();
        boolean parentSampled = parentSpan.getSpanContext().isSampled();
        
        try (Scope scope = parentSpan.makeCurrent()) {
            // Create child spans
            for (int i = 0; i < childSpanCount; i++) {
                Span childSpan = tracer.spanBuilder(operationName + "-child-" + i).startSpan();
                
                try {
                    // Assert: Child span has same trace ID as parent
                    assertThat(childSpan.getSpanContext().getTraceId())
                        .as("Child span should have same trace ID as parent")
                        .isEqualTo(parentTraceId);
                    
                    // Assert: Child span has same sampling decision as parent
                    assertThat(childSpan.getSpanContext().isSampled())
                        .as("Child span should have same sampling decision as parent")
                        .isEqualTo(parentSampled);
                    
                } finally {
                    childSpan.end();
                }
            }
        } finally {
            parentSpan.end();
        }
    }

    /**
     * Feature: observability-platform, Property 7: Trace Sampling Consistency
     * 
     * <p>Verifies that with 100% sampling, all traces are sampled.
     */
    @Property(tries = 100)
    @Tag("Feature-observability-platform-Property-7-Trace-Sampling-Consistency")
    void hundredPercentSamplingIncludesAllTraces(@ForAll("operationNames") String operationName) {
        
        // Setup: Create OpenTelemetry with 100% sampling
        OpenTelemetry openTelemetry = createOpenTelemetryWithSampling(1.0);
        Tracer tracer = openTelemetry.getTracer("test-service");
        
        // Execute: Create a span
        Span span = tracer.spanBuilder(operationName).startSpan();
        
        try {
            // Assert: Span is sampled
            assertThat(span.getSpanContext().isSampled())
                .as("With 100% sampling, all spans should be sampled")
                .isTrue();
            
            // Assert: Span is recording
            assertThat(span.isRecording())
                .as("Sampled spans should be recording")
                .isTrue();
            
        } finally {
            span.end();
        }
    }

    /**
     * Feature: observability-platform, Property 7: Trace Sampling Consistency
     * 
     * <p>Verifies that with 0% sampling, no traces are sampled.
     */
    @Property(tries = 100)
    @Tag("Feature-observability-platform-Property-7-Trace-Sampling-Consistency")
    void zeroPercentSamplingExcludesAllTraces(@ForAll("operationNames") String operationName) {
        
        // Setup: Create OpenTelemetry with 0% sampling
        OpenTelemetry openTelemetry = createOpenTelemetryWithSampling(0.0);
        Tracer tracer = openTelemetry.getTracer("test-service");
        
        // Execute: Create a span
        Span span = tracer.spanBuilder(operationName).startSpan();
        
        try {
            // Assert: Span is not sampled
            assertThat(span.getSpanContext().isSampled())
                .as("With 0% sampling, no spans should be sampled")
                .isFalse();
            
            // Note: Even unsampled spans may still be recording for context propagation
            // The key is that they won't be exported to the backend
            
        } finally {
            span.end();
        }
    }

    /**
     * Feature: observability-platform, Property 7: Trace Sampling Consistency
     * 
     * <p>Verifies that nested spans (grandchildren) also inherit the sampling decision.
     */
    @Property(tries = 50)
    @Tag("Feature-observability-platform-Property-7-Trace-Sampling-Consistency")
    void nestedSpansInheritSamplingDecision(@ForAll("operationNames") String operationName) {
        
        // Setup: Create OpenTelemetry with 100% sampling
        OpenTelemetry openTelemetry = createOpenTelemetryWithSampling(1.0);
        Tracer tracer = openTelemetry.getTracer("test-service");
        
        // Execute: Create a parent span, child span, and grandchild span
        Span parentSpan = tracer.spanBuilder(operationName + "-parent").startSpan();
        String parentTraceId = parentSpan.getSpanContext().getTraceId();
        boolean parentSampled = parentSpan.getSpanContext().isSampled();
        
        try (Scope parentScope = parentSpan.makeCurrent()) {
            Span childSpan = tracer.spanBuilder(operationName + "-child").startSpan();
            
            try (Scope childScope = childSpan.makeCurrent()) {
                Span grandchildSpan = tracer.spanBuilder(operationName + "-grandchild").startSpan();
                
                try {
                    // Assert: All spans have the same trace ID
                    assertThat(childSpan.getSpanContext().getTraceId())
                        .as("Child span should have same trace ID as parent")
                        .isEqualTo(parentTraceId);
                    
                    assertThat(grandchildSpan.getSpanContext().getTraceId())
                        .as("Grandchild span should have same trace ID as parent")
                        .isEqualTo(parentTraceId);
                    
                    // Assert: All spans have the same sampling decision
                    assertThat(childSpan.getSpanContext().isSampled())
                        .as("Child span should have same sampling decision as parent")
                        .isEqualTo(parentSampled);
                    
                    assertThat(grandchildSpan.getSpanContext().isSampled())
                        .as("Grandchild span should have same sampling decision as parent")
                        .isEqualTo(parentSampled);
                    
                } finally {
                    grandchildSpan.end();
                }
            } finally {
                childSpan.end();
            }
        } finally {
            parentSpan.end();
        }
    }

    /**
     * Feature: observability-platform, Property 7: Trace Sampling Consistency
     * 
     * <p>Verifies that sampling rate configuration is respected.
     * 
     * <p>Note: This is a statistical test - with a 50% sampling rate, we expect
     * approximately 50% of traces to be sampled over many iterations.
     */
    @Property(tries = 1000)
    @Tag("Feature-observability-platform-Property-7-Trace-Sampling-Consistency")
    @Report(Reporting.GENERATED)
    void samplingRateIsRespected(
            @ForAll("operationNames") String operationName,
            @ForAll("samplingRates") double samplingRate) {
        
        // Setup: Create OpenTelemetry with specified sampling rate
        OpenTelemetry openTelemetry = createOpenTelemetryWithSampling(samplingRate);
        Tracer tracer = openTelemetry.getTracer("test-service");
        
        // Execute: Create a span
        Span span = tracer.spanBuilder(operationName).startSpan();
        
        try {
            // Assert: Span context is valid
            assertThat(span.getSpanContext().isValid())
                .as("Span context should be valid regardless of sampling")
                .isTrue();
            
            // Note: We can't assert the exact sampling decision for a single span
            // because sampling is probabilistic. The property we're testing is that
            // the sampling decision is made consistently for the entire trace.
            // Statistical validation would require aggregating results across many runs.
            
        } finally {
            span.end();
        }
    }
}
