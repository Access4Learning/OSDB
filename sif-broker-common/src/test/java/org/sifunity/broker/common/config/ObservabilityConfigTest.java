// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ObservabilityConfig.
 * 
 * Tests verify:
 * - OpenTelemetry SDK initialization
 * - Tracer bean creation
 * - TextMapPropagator configuration
 * - Error handling when Jaeger is unavailable
 * - Configuration from properties
 */
@SpringBootTest(classes = ObservabilityConfig.class)
@TestPropertySource(properties = {
    "spring.application.name=test-service",
    "spring.application.version=1.0.0-test",
    "observability.enabled=true",
    "observability.tracing.jaeger.endpoint=http://localhost:14250",
    "observability.tracing.sampling.probability=1.0",
    // Don't register globally to avoid conflicts between test classes
    "observability.tracing.register-global=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ObservabilityConfigTest {

    @Autowired(required = false)
    private OpenTelemetry openTelemetry;

    @Autowired(required = false)
    private Tracer tracer;

    @Autowired(required = false)
    private TextMapPropagator textMapPropagator;

    @Test
    void openTelemetryBeanIsCreated() {
        assertThat(openTelemetry).isNotNull();
    }

    @Test
    void tracerBeanIsCreated() {
        assertThat(tracer).isNotNull();
    }

    @Test
    void textMapPropagatorBeanIsCreated() {
        assertThat(textMapPropagator).isNotNull();
    }

    @Test
    void openTelemetryIsConfiguredWithTracerProvider() {
        assertThat(openTelemetry).isInstanceOf(OpenTelemetrySdk.class);
        
        OpenTelemetrySdk sdk = (OpenTelemetrySdk) openTelemetry;
        assertThat(sdk.getSdkTracerProvider()).isNotNull();
        assertThat(sdk.getSdkTracerProvider()).isInstanceOf(SdkTracerProvider.class);
    }

    @Test
    void tracerIsCreatedFromOpenTelemetry() {
        assertThat(tracer).isNotNull();
        
        // Verify tracer can create spans
        var span = tracer.spanBuilder("test-operation").startSpan();
        assertThat(span).isNotNull();
        assertThat(span.getSpanContext().isValid()).isTrue();
        span.end();
    }

    @Test
    void textMapPropagatorIsW3CTraceContext() {
        assertThat(textMapPropagator).isNotNull();
        assertThat(textMapPropagator.fields()).contains("traceparent");
    }
}
