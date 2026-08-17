// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import net.jqwik.api.*;
import org.sifunity.gateway.filter.TraceContextFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for trace context propagation in the API Gateway.
 * 
 * <p>These tests verify universal correctness properties for OpenTelemetry
 * distributed tracing across randomized inputs to ensure the Gateway correctly
 * propagates trace context in all scenarios.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Property 2: Trace Context Propagation</li>
 *   <li>Property 10: Correlation ID and Trace ID Association</li>
 * </ul>
 * 
 * <p>Each property test runs a minimum of 100 iterations with randomized test data.
 * 
 * @see TraceContextFilter
 */
class TraceContextPropagationProperties {
    
    /**
     * Property 2: Trace Context Propagation
     * 
     * **Validates: Requirements 1.2**
     * 
     * <p>For any outbound HTTP call made within a trace context, the W3C traceparent
     * header should be present and contain the current trace ID and span ID.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Trace context is extracted from incoming requests</li>
     *   <li>A new span is created for the gateway request</li>
     *   <li>The traceparent header is injected into the outbound request</li>
     *   <li>The trace ID is preserved across the gateway</li>
     *   <li>Response headers include X-Trace-ID</li>
     * </ul>
     * 
     * @param traceId randomly generated W3C trace ID (32 hex characters)
     * @param spanId randomly generated W3C span ID (16 hex characters)
     * @param path randomly generated request path
     * @param method randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: observability-platform, Property 2: Trace Context Propagation")
    void traceContextPropagation(
            @ForAll("traceIds") String traceId,
            @ForAll("spanIds") String spanId,
            @ForAll("requestPaths") String path,
            @ForAll("httpMethods") String method) {
        
        // Setup: Create in-memory span exporter to capture spans
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        Tracer tracer = openTelemetry.getTracer("gateway-test");
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
        
        // Create TraceContextFilter
        TraceContextFilter filter = new TraceContextFilter(tracer, propagator);
        
        // Arrange: Create incoming request with W3C traceparent header
        String traceparent = String.format("00-%s-%s-01", traceId, spanId);
        
        MockServerHttpRequest request = MockServerHttpRequest
                .method(org.springframework.http.HttpMethod.valueOf(method), path)
                .header("traceparent", traceparent)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Create mock filter chain that captures the mutated request
        final HttpHeaders[] capturedHeaders = new HttpHeaders[1];
        GatewayFilterChain chain = ex -> {
            capturedHeaders[0] = ex.getRequest().getHeaders();
            return Mono.empty();
        };
        
        // Act: Process request through filter
        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();
        
        // Assert: Verify trace context was propagated
        
        // 1. Verify a span was created
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
                .as("A span should be created for the gateway request")
                .hasSize(1);
        
        SpanData gatewaySpan = spans.get(0);
        
        // 2. Verify the span has the correct trace ID (inherited from parent)
        assertThat(gatewaySpan.getTraceId())
                .as("Gateway span should inherit trace ID from incoming request")
                .isEqualTo(traceId);
        
        // 3. Verify the span has the correct parent span ID
        assertThat(gatewaySpan.getParentSpanId())
                .as("Gateway span should have parent span ID from incoming request")
                .isEqualTo(spanId);
        
        // 4. Verify traceparent header was injected into outbound request
        assertThat(capturedHeaders[0])
                .as("Outbound request should have headers")
                .isNotNull();
        
        String outboundTraceparent = capturedHeaders[0].getFirst("traceparent");
        assertThat(outboundTraceparent)
                .as("Outbound request should have traceparent header")
                .isNotNull()
                .startsWith("00-" + traceId + "-");
        
        // 5. Verify X-Trace-ID header was added to response
        String responseTraceId = exchange.getResponse().getHeaders().getFirst("X-Trace-ID");
        assertThat(responseTraceId)
                .as("Response should include X-Trace-ID header")
                .isEqualTo(traceId);
        
        // 6. Verify span attributes include HTTP method and URL
        assertThat(gatewaySpan.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("http.method")))
                .as("Span should include http.method attribute")
                .isEqualTo(method);
        
        assertThat(gatewaySpan.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("http.target")))
                .as("Span should include http.target attribute")
                .isEqualTo(path);
        
        // Cleanup
        tracerProvider.close();
    }
    
    /**
     * Property 2 (variant): Trace context creation when no parent exists
     * 
     * **Validates: Requirements 1.1, 1.2**
     * 
     * <p>For any request without an existing trace context, the Gateway should
     * create a new trace with a valid W3C trace ID and propagate it downstream.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>A new trace is created when no traceparent header is present</li>
     *   <li>The generated trace ID is valid (32 hex characters)</li>
     *   <li>The traceparent header is injected into the outbound request</li>
     *   <li>Response headers include X-Trace-ID</li>
     * </ul>
     * 
     * @param path randomly generated request path
     * @param method randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: observability-platform, Property 2: Trace Context Propagation")
    void traceContextCreation(
            @ForAll("requestPaths") String path,
            @ForAll("httpMethods") String method) {
        
        // Setup: Create in-memory span exporter to capture spans
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        Tracer tracer = openTelemetry.getTracer("gateway-test");
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
        
        // Create TraceContextFilter
        TraceContextFilter filter = new TraceContextFilter(tracer, propagator);
        
        // Arrange: Create incoming request WITHOUT traceparent header
        MockServerHttpRequest request = MockServerHttpRequest
                .method(org.springframework.http.HttpMethod.valueOf(method), path)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Create mock filter chain that captures the mutated request
        final HttpHeaders[] capturedHeaders = new HttpHeaders[1];
        GatewayFilterChain chain = ex -> {
            capturedHeaders[0] = ex.getRequest().getHeaders();
            return Mono.empty();
        };
        
        // Act: Process request through filter
        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();
        
        // Assert: Verify new trace context was created
        
        // 1. Verify a span was created
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
                .as("A span should be created for the gateway request")
                .hasSize(1);
        
        SpanData gatewaySpan = spans.get(0);
        
        // 2. Verify the span has a valid trace ID (32 hex characters)
        String traceId = gatewaySpan.getTraceId();
        assertThat(traceId)
                .as("Gateway span should have a valid W3C trace ID")
                .matches("[0-9a-f]{32}");
        
        // 3. Verify the span has no parent (root span)
        assertThat(gatewaySpan.getParentSpanId())
                .as("Gateway span should be a root span with no parent")
                .isEqualTo("0000000000000000");
        
        // 4. Verify traceparent header was injected into outbound request
        assertThat(capturedHeaders[0])
                .as("Outbound request should have headers")
                .isNotNull();
        
        String outboundTraceparent = capturedHeaders[0].getFirst("traceparent");
        assertThat(outboundTraceparent)
                .as("Outbound request should have traceparent header")
                .isNotNull()
                .startsWith("00-" + traceId + "-");
        
        // 5. Verify X-Trace-ID header was added to response
        String responseTraceId = exchange.getResponse().getHeaders().getFirst("X-Trace-ID");
        assertThat(responseTraceId)
                .as("Response should include X-Trace-ID header")
                .isEqualTo(traceId);
        
        // Cleanup
        tracerProvider.close();
    }
    
    /**
     * Property 10: Correlation ID and Trace ID Association
     * 
     * **Validates: Requirements 4.1, 4.2**
     * 
     * <p>For any request with a Correlation ID header, both the Correlation ID and
     * Trace ID should be associated and accessible in the span attributes.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Correlation ID from X-Correlation-ID header is extracted</li>
     *   <li>Correlation ID is added as a span attribute</li>
     *   <li>Correlation ID is included in response headers</li>
     *   <li>Trace ID is included in response headers</li>
     *   <li>Both IDs are accessible for log correlation</li>
     * </ul>
     * 
     * @param correlationId randomly generated correlation ID (UUID format)
     * @param path randomly generated request path
     * @param method randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: observability-platform, Property 10: Correlation ID and Trace ID Association")
    void correlationIdAndTraceIdAssociation(
            @ForAll("correlationIds") String correlationId,
            @ForAll("requestPaths") String path,
            @ForAll("httpMethods") String method) {
        
        // Setup: Create in-memory span exporter to capture spans
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        Tracer tracer = openTelemetry.getTracer("gateway-test");
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
        
        // Create TraceContextFilter
        TraceContextFilter filter = new TraceContextFilter(tracer, propagator);
        
        // Arrange: Create incoming request with X-Correlation-ID header
        MockServerHttpRequest request = MockServerHttpRequest
                .method(org.springframework.http.HttpMethod.valueOf(method), path)
                .header("X-Correlation-ID", correlationId)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Create mock filter chain
        GatewayFilterChain chain = ex -> Mono.empty();
        
        // Act: Process request through filter
        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();
        
        // Assert: Verify correlation ID and trace ID association
        
        // 1. Verify a span was created
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
                .as("A span should be created for the gateway request")
                .hasSize(1);
        
        SpanData gatewaySpan = spans.get(0);
        
        // 2. Verify correlation ID is stored as span attribute
        String spanCorrelationId = gatewaySpan.getAttributes()
                .get(io.opentelemetry.api.common.AttributeKey.stringKey("correlation.id"));
        assertThat(spanCorrelationId)
                .as("Span should include correlation.id attribute")
                .isEqualTo(correlationId);
        
        // 3. Verify X-Correlation-ID header is in response
        String responseCorrelationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        assertThat(responseCorrelationId)
                .as("Response should include X-Correlation-ID header")
                .isEqualTo(correlationId);
        
        // 4. Verify X-Trace-ID header is in response
        String responseTraceId = exchange.getResponse().getHeaders().getFirst("X-Trace-ID");
        assertThat(responseTraceId)
                .as("Response should include X-Trace-ID header")
                .isNotNull()
                .matches("[0-9a-f]{32}");
        
        // 5. Verify both IDs are accessible (they should match the span's trace ID and correlation attribute)
        assertThat(responseTraceId)
                .as("Response trace ID should match span trace ID")
                .isEqualTo(gatewaySpan.getTraceId());
        
        // Cleanup
        tracerProvider.close();
    }
    
    /**
     * Property 10 (variant): Correlation ID generation when missing
     * 
     * **Validates: Requirements 4.1, 4.2**
     * 
     * <p>For any request without a Correlation ID header, the Gateway should
     * generate a new UUID correlation ID and associate it with the trace.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>A new correlation ID is generated when missing</li>
     *   <li>Generated correlation ID is a valid UUID</li>
     *   <li>Correlation ID is added as a span attribute</li>
     *   <li>Correlation ID is included in response headers</li>
     * </ul>
     * 
     * @param path randomly generated request path
     * @param method randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: observability-platform, Property 10: Correlation ID and Trace ID Association")
    void correlationIdGeneration(
            @ForAll("requestPaths") String path,
            @ForAll("httpMethods") String method) {
        
        // Setup: Create in-memory span exporter to capture spans
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        Tracer tracer = openTelemetry.getTracer("gateway-test");
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
        
        // Create TraceContextFilter
        TraceContextFilter filter = new TraceContextFilter(tracer, propagator);
        
        // Arrange: Create incoming request WITHOUT X-Correlation-ID header
        MockServerHttpRequest request = MockServerHttpRequest
                .method(org.springframework.http.HttpMethod.valueOf(method), path)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Create mock filter chain
        GatewayFilterChain chain = ex -> Mono.empty();
        
        // Act: Process request through filter
        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();
        
        // Assert: Verify correlation ID was generated
        
        // 1. Verify a span was created
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
                .as("A span should be created for the gateway request")
                .hasSize(1);
        
        SpanData gatewaySpan = spans.get(0);
        
        // 2. Verify correlation ID is stored as span attribute
        String spanCorrelationId = gatewaySpan.getAttributes()
                .get(io.opentelemetry.api.common.AttributeKey.stringKey("correlation.id"));
        assertThat(spanCorrelationId)
                .as("Span should include generated correlation.id attribute")
                .isNotNull()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"); // UUID format
        
        // 3. Verify X-Correlation-ID header is in response
        String responseCorrelationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        assertThat(responseCorrelationId)
                .as("Response should include generated X-Correlation-ID header")
                .isEqualTo(spanCorrelationId);
        
        // 4. Verify X-Trace-ID header is in response
        String responseTraceId = exchange.getResponse().getHeaders().getFirst("X-Trace-ID");
        assertThat(responseTraceId)
                .as("Response should include X-Trace-ID header")
                .isNotNull()
                .matches("[0-9a-f]{32}");
        
        // Cleanup
        tracerProvider.close();
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates random W3C trace IDs (32 hexadecimal characters).
     * Trace IDs must be lowercase hex as per W3C spec.
     * Excludes invalid trace ID (all zeros).
     */
    @Provide
    Arbitrary<String> traceIds() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .ofLength(32)
                .filter(id -> !id.equals("00000000000000000000000000000000")); // Exclude invalid trace ID
    }
    
    /**
     * Generates random W3C span IDs (16 hexadecimal characters).
     * Span IDs must be lowercase hex as per W3C spec.
     * Excludes invalid span ID (all zeros).
     */
    @Provide
    Arbitrary<String> spanIds() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .ofLength(16)
                .filter(id -> !id.equals("0000000000000000")); // Exclude invalid span ID
    }
    
    /**
     * Generates random request paths for testing.
     * Includes various SIF Unity Broker service paths.
     */
    @Provide
    Arbitrary<String> requestPaths() {
        return Arbitraries.of(
                "/requests/StudentPersonal",
                "/responses/SchoolInfo",
                "/events/StudentAttendance",
                "/services/StudentPersonal",
                "/queues",
                "/subscriptions",
                "/jobs",
                "/environments",
                "/acl-entries",
                "/providers"
        );
    }
    
    /**
     * Generates random HTTP methods for testing.
     */
    @Provide
    Arbitrary<String> httpMethods() {
        return Arbitraries.of("GET", "POST", "PUT", "DELETE", "PATCH");
    }
    
    /**
     * Generates random correlation IDs in UUID format.
     * Correlation IDs are typically UUIDs in the SIF Unity Broker.
     */
    @Provide
    Arbitrary<String> correlationIds() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .withChars('-')
                .ofLength(36)
                .map(s -> {
                    // Format as UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
                    if (s.length() < 36) {
                        s = s + "0".repeat(36 - s.length());
                    }
                    return s.substring(0, 8) + "-" +
                           s.substring(8, 12) + "-" +
                           s.substring(12, 16) + "-" +
                           s.substring(16, 20) + "-" +
                           s.substring(20, 32);
                });
    }
}
