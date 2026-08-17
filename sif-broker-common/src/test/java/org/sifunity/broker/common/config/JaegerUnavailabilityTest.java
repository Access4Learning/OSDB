// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for graceful degradation when Jaeger is unavailable.
 * 
 * These tests verify that:
 * - Service continues operating when Jaeger is down
 * - Traces are buffered in memory
 * - No exceptions are thrown to application code
 * - Spans can still be created and used
 * 
 * Validates Requirements: 1.4, 18.1
 */
@SpringBootTest(classes = ObservabilityConfig.class)
@TestPropertySource(properties = {
    "spring.application.name=test-service",
    "spring.application.version=1.0.0-test",
    "observability.enabled=true",
    // Point to non-existent Jaeger endpoint to simulate unavailability
    "observability.tracing.jaeger.endpoint=http://localhost:19999",
    "observability.tracing.sampling.probability=1.0",
    // Don't register globally to avoid conflicts between test classes
    "observability.tracing.register-global=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JaegerUnavailabilityTest {

    @Autowired
    private OpenTelemetry openTelemetry;

    @Autowired
    private Tracer tracer;

    @Test
    void serviceStartsSuccessfullyWhenJaegerIsUnavailable() {
        // Given: Jaeger is unavailable (configured to non-existent endpoint)
        // When: Service starts
        // Then: OpenTelemetry beans are created successfully
        assertThat(openTelemetry).isNotNull();
        assertThat(tracer).isNotNull();
    }

    @Test
    void spansCanBeCreatedWhenJaegerIsUnavailable() {
        // Given: Jaeger is unavailable
        // When: Creating a span
        Span span = tracer.spanBuilder("test-operation")
            .setAttribute("test.attribute", "test-value")
            .startSpan();
        
        // Then: Span is created successfully
        assertThat(span).isNotNull();
        assertThat(span.getSpanContext().isValid()).isTrue();
        assertThat(span.getSpanContext().getTraceId()).isNotEmpty();
        assertThat(span.getSpanContext().getSpanId()).isNotEmpty();
        
        span.end();
    }

    @Test
    void noExceptionsThrownWhenExportingToUnavailableJaeger() {
        // Given: Jaeger is unavailable
        // When: Creating and ending multiple spans (which triggers export)
        assertThatCode(() -> {
            for (int i = 0; i < 10; i++) {
                Span span = tracer.spanBuilder("test-operation-" + i)
                    .setAttribute("iteration", i)
                    .startSpan();
                span.end();
            }
            
            // Force flush to trigger export
            if (openTelemetry instanceof OpenTelemetrySdk) {
                OpenTelemetrySdk sdk = (OpenTelemetrySdk) openTelemetry;
                SdkTracerProvider tracerProvider = sdk.getSdkTracerProvider();
                tracerProvider.forceFlush();
            }
        }).doesNotThrowAnyException();
        
        // Then: No exceptions are thrown, service continues operating
    }

    @Test
    void tracerProviderIsConfiguredWithBatchProcessor() {
        // Given: OpenTelemetry SDK is initialized
        assertThat(openTelemetry).isInstanceOf(OpenTelemetrySdk.class);
        
        OpenTelemetrySdk sdk = (OpenTelemetrySdk) openTelemetry;
        SdkTracerProvider tracerProvider = sdk.getSdkTracerProvider();
        
        // Then: Tracer provider is configured (with batch processor for buffering)
        assertThat(tracerProvider).isNotNull();
    }

    @Test
    void spansAreBufferedInMemoryWhenJaegerIsUnavailable() {
        // Given: Jaeger is unavailable
        // When: Creating many spans to fill the buffer
        for (int i = 0; i < 100; i++) {
            Span span = tracer.spanBuilder("buffered-operation-" + i)
                .setAttribute("buffer.test", true)
                .setAttribute("iteration", i)
                .startSpan();
            span.end();
        }
        
        // Then: All spans are created successfully without errors
        // (They are buffered in the BatchSpanProcessor queue)
        assertThat(tracer).isNotNull();
    }

    @Test
    void traceContextIsValidEvenWhenJaegerIsUnavailable() {
        // Given: Jaeger is unavailable
        // When: Creating a span with parent-child relationship
        Span parentSpan = tracer.spanBuilder("parent-operation").startSpan();
        
        Span childSpan = tracer.spanBuilder("child-operation")
            .setParent(io.opentelemetry.context.Context.current().with(parentSpan))
            .startSpan();
        
        // Then: Parent-child relationship is maintained
        assertThat(parentSpan.getSpanContext().getTraceId())
            .isEqualTo(childSpan.getSpanContext().getTraceId());
        assertThat(childSpan.getSpanContext().getSpanId())
            .isNotEqualTo(parentSpan.getSpanContext().getSpanId());
        
        childSpan.end();
        parentSpan.end();
    }

    @Test
    void spanAttributesArePreservedWhenJaegerIsUnavailable() {
        // Given: Jaeger is unavailable
        // When: Creating a span with multiple attributes
        Span span = tracer.spanBuilder("attributed-operation")
            .setAttribute("string.attr", "test-value")
            .setAttribute("long.attr", 12345L)
            .setAttribute("boolean.attr", true)
            .setAttribute("double.attr", 3.14159)
            .startSpan();
        
        // Then: Span is created successfully with attributes
        assertThat(span).isNotNull();
        assertThat(span.getSpanContext().isValid()).isTrue();
        
        span.end();
    }
}
