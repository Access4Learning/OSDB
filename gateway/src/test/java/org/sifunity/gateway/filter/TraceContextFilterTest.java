// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TraceContextFilter.
 * 
 * <p>These tests verify specific examples and edge cases for trace context
 * propagation in the API Gateway. Property-based tests provide comprehensive
 * coverage across randomized inputs.
 * 
 * @see TraceContextFilter
 * @see org.sifunity.gateway.property.TraceContextPropagationProperties
 */
class TraceContextFilterTest {
    
    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private Tracer tracer;
    private TextMapPropagator propagator;
    private TraceContextFilter filter;
    
    @BeforeEach
    void setUp() {
        // Create in-memory span exporter to capture spans
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        tracer = openTelemetry.getTracer("gateway-test");
        propagator = W3CTraceContextPropagator.getInstance();
        
        filter = new TraceContextFilter(tracer, propagator);
    }
    
    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }
    
    /**
     * Test: Correlation ID generation when missing
     * 
     * <p>Verifies that when a request arrives without an X-Correlation-ID header,
     * the filter generates a new UUID correlation ID.
     * 
     * <p>Requirements: 4.1, 4.2
     */
    @Test
    void shouldGenerateCorrelationIdWhenMissing() {
        // Arrange: Create request without X-Correlation-ID header
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/requests/StudentPersonal")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = ex -> Mono.empty();
        
        // Act: Process request through filter
        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();
        
        // Assert: Verify correlation ID was generated
        String responseCorrelationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        assertThat(responseCorrelationId)
                .as("Response should include generated X-Correlation-ID header")
                .isNotNull()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        
        // Verify span includes correlation ID
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        String spanCorrelationId = spans.get(0).getAttributes()
                .get(io.opentelemetry.api.common.AttributeKey.stringKey("correlation.id"));
        assertThat(spanCorrelationId).isEqualTo(responseCorrelationId);
    }
    
    /**
     * Test: Correlation ID extraction when present
     * 
     * <p>Verifies that when a request arrives with an X-Correlation-ID header,
     * the filter preserves and uses that correlation ID.
     * 
     * <p>Requirements: 4.1, 4.2
     */
    @Test
    void shouldExtractCorrelationIdWhenPresent() {
        // Arrange: Create request with X-Correlation-ID header
        String correlationId = "550e8400-e29b-41d4-a716-446655440000";
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/requests/StudentPersonal")
                .header("X-Correlation-ID", correlationId)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = ex -> Mono.empty();
        
        // Act: Process request through filter
        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();
        
        // Assert: Verify correlation ID was preserved
        String responseCorrelationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        assertThat(responseCorrelationId)
                .as("Response should include the same X-Correlation-ID from request")
                .isEqualTo(correlationId);
        
        // Verify span includes correlation ID
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        String spanCorrelationId = spans.get(0).getAttributes()
                .get(io.opentelemetry.api.common.AttributeKey.stringKey("correlation.id"));
        assertThat(spanCorrelationId).isEqualTo(correlationId);
    }
    
    /**
     * Test: Response header population
     * 
     * <p>Verifies that the filter adds both X-Trace-ID and X-Correlation-ID
     * headers to the response.
     * 
     * <p>Requirements: 1.2, 4.1, 4.2
     */
    @Test
    void shouldPopulateResponseHeaders() {
        // Arrange: Create request
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/requests/StudentPersonal")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = ex -> Mono.empty();
        
        // Act: Process request through filter
        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();
        
        // Assert: Verify both headers are present in response
        String traceId = exchange.getResponse().getHeaders().getFirst("X-Trace-ID");
        assertThat(traceId)
                .as("Response should include X-Trace-ID header")
                .isNotNull()
                .matches("[0-9a-f]{32}");
        
        String correlationId = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        assertThat(correlationId)
                .as("Response should include X-Correlation-ID header")
                .isNotNull();
    }
    
    /**
     * Test: Span attributes include HTTP method and URL
     * 
     * <p>Verifies that the created span includes HTTP method and URL attributes
     * for observability and debugging.
     * 
     * <p>Requirements: 1.1, 3.5
     */
    @Test
    void shouldIncludeHttpAttributesInSpan() {
        // Arrange: Create request with specific method and path
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.PUT, "/requests/StudentPersonal/123")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = ex -> Mono.empty();
        
        // Act: Process request through filter
        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();
        
        // Assert: Verify span includes HTTP attributes
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        SpanData span = spans.get(0);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("http.method")))
                .as("Span should include http.method attribute")
                .isEqualTo("PUT");
        
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("http.target")))
                .as("Span should include http.target attribute")
                .isEqualTo("/requests/StudentPersonal/123");
    }
    
    /**
     * Test: Span status reflects HTTP response code
     * 
     * <p>Verifies that the span status is set based on the HTTP response code:
     * - 2xx/3xx: OK status
     * - 4xx/5xx: ERROR status
     * 
     * <p>Requirements: 1.1
     */
    @Test
    void shouldSetSpanStatusBasedOnResponseCode() {
        // Arrange: Create request
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/requests/StudentPersonal")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set response status to 200 OK
        exchange.getResponse().setRawStatusCode(200);
        
        GatewayFilterChain chain = ex -> Mono.empty();
        
        // Act: Process request through filter
        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();
        
        // Assert: Verify span status is OK
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode())
                .as("Span status should be OK for 2xx response")
                .isEqualTo(io.opentelemetry.api.trace.StatusCode.OK);
    }
    
    /**
     * Test: Filter order ensures early execution
     * 
     * <p>Verifies that the filter has order 0 to ensure it runs before
     * CorrelationIdFilter (order 1) and other filters.
     * 
     * <p>Requirements: 1.2
     */
    @Test
    void shouldHaveCorrectFilterOrder() {
        // Assert: Verify filter order
        assertThat(filter.getOrder())
                .as("TraceContextFilter should run before CorrelationIdFilter (order 1)")
                .isEqualTo(0);
    }
}
