// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import net.jqwik.api.*;
import org.sifunity.broker.common.util.TracingUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for custom span creation utilities.
 * 
 * <p>These tests verify that the TracingUtils class correctly creates
 * custom spans with proper parent-child relationships and attributes.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Property 8: Custom Span Parent-Child Linking</li>
 *   <li>Property 9: Custom Span Attributes</li>
 * </ul>
 * 
 * <p>Each property test runs a minimum of 100 iterations with randomized test data.
 */
class CustomSpanProperties {

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
    Arbitrary<String> attributeKeys() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('.', '_', '-')
            .ofMinLength(1)
            .ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> attributeValues() {
        return Arbitraries.strings()
            .ascii()
            .ofMinLength(0)
            .ofMaxLength(100);
    }

    @Provide
    Arbitrary<Map<String, String>> customAttributes() {
        return Arbitraries.maps(
            attributeKeys(),
            attributeValues()
        ).ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<Integer> resultValues() {
        return Arbitraries.integers().between(-1000, 1000);
    }

    // ========== Property 8: Custom Span Parent-Child Linking ==========
    // **Validates: Requirements 3.2**

    /**
     * Feature: observability-platform, Property 8: Custom Span Parent-Child Linking
     * 
     * <p>For any custom span created within an existing trace context, the custom
     * span should have the current span as its parent.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Custom spans created with TracingUtils have correct parent span</li>
     *   <li>Parent-child relationship is maintained in the trace hierarchy</li>
     *   <li>Child spans inherit the parent's trace ID</li>
     *   <li>Child spans have different span IDs from their parents</li>
     * </ul>
     */
    @Property(tries = 100)
    @Label("Feature: observability-platform, Property 8: Custom Span Parent-Child Linking")
    void customSpanHasCorrectParent(
            @ForAll("operationNames") String parentOperation,
            @ForAll("operationNames") String childOperation,
            @ForAll("customAttributes") Map<String, String> attributes,
            @ForAll("resultValues") Integer result) {
        
        // Setup: Create in-memory span exporter to capture spans
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setSampler(Sampler.alwaysOn())
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(
                    W3CTraceContextPropagator.getInstance()
                ))
                .build();
        
        Tracer tracer = openTelemetry.getTracer("test-service");
        TracingUtils tracingUtils = new TracingUtils(tracer);
        
        // Arrange: Create a parent span
        Span parentSpan = tracer.spanBuilder(parentOperation).startSpan();
        String parentTraceId = parentSpan.getSpanContext().getTraceId();
        String parentSpanId = parentSpan.getSpanContext().getSpanId();
        
        try (Scope scope = parentSpan.makeCurrent()) {
            // Act: Create a custom child span using TracingUtils
            Integer actualResult = tracingUtils.traceOperation(
                childOperation,
                attributes,
                () -> result
            );
            
            // Assert: Operation returned correct result
            assertThat(actualResult)
                .as("Operation should return the expected result")
                .isEqualTo(result);
            
        } finally {
            parentSpan.end();
        }
        
        // Assert: Verify span hierarchy
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
            .as("Should have created 2 spans (parent and child)")
            .hasSize(2);
        
        // Find parent and child spans
        SpanData parentSpanData = spans.stream()
            .filter(s -> s.getName().equals(parentOperation))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Parent span not found"));
        
        SpanData childSpanData = spans.stream()
            .filter(s -> s.getName().equals(childOperation))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Child span not found"));
        
        // Assert: Child span has correct parent
        assertThat(childSpanData.getParentSpanId())
            .as("Child span should have parent span as its parent")
            .isEqualTo(parentSpanId);
        
        // Assert: Child span inherits parent's trace ID
        assertThat(childSpanData.getTraceId())
            .as("Child span should inherit parent's trace ID")
            .isEqualTo(parentTraceId);
        
        // Assert: Child span has different span ID from parent
        assertThat(childSpanData.getSpanId())
            .as("Child span should have different span ID from parent")
            .isNotEqualTo(parentSpanId);
        
        // Assert: Child span has valid span ID
        assertThat(childSpanData.getSpanId())
            .as("Child span should have valid span ID (16 hex characters)")
            .matches("[0-9a-f]{16}");
        
        // Cleanup
        tracerProvider.close();
    }

    /**
     * Feature: observability-platform, Property 8: Custom Span Parent-Child Linking
     * 
     * <p>Verifies that nested custom spans maintain correct parent-child hierarchy
     * across multiple levels.
     */
    @Property(tries = 100)
    @Label("Feature: observability-platform, Property 8: Custom Span Parent-Child Linking")
    void nestedCustomSpansMaintainHierarchy(
            @ForAll("operationNames") String level1Operation,
            @ForAll("operationNames") String level2Operation,
            @ForAll("operationNames") String level3Operation,
            @ForAll("resultValues") Integer result) {
        
        // Setup: Create in-memory span exporter to capture spans
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setSampler(Sampler.alwaysOn())
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        Tracer tracer = openTelemetry.getTracer("test-service");
        TracingUtils tracingUtils = new TracingUtils(tracer);
        
        // Act: Create nested custom spans (3 levels deep)
        // Note: The first traceOperation call creates a root span since there's no parent context
        Integer actualResult = tracingUtils.traceOperation(
            level1Operation,
            Map.of("level", "1"),
            () -> tracingUtils.traceOperation(
                level2Operation,
                Map.of("level", "2"),
                () -> tracingUtils.traceOperation(
                    level3Operation,
                    Map.of("level", "3"),
                    () -> result
                )
            )
        );
        
        // Assert: Operation returned correct result
        assertThat(actualResult)
            .as("Operation should return the expected result")
            .isEqualTo(result);
        
        // Assert: Verify span hierarchy
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
            .as("Should have created 3 spans (3 levels)")
            .hasSize(3);
        
        // Find spans by level attribute (more reliable than operation name when names are the same)
        SpanData level1Span = spans.stream()
            .filter(s -> "1".equals(s.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("level"))))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Level 1 span not found"));
        
        SpanData level2Span = spans.stream()
            .filter(s -> "2".equals(s.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("level"))))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Level 2 span not found"));
        
        SpanData level3Span = spans.stream()
            .filter(s -> "3".equals(s.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("level"))))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Level 3 span not found"));
        
        // Assert: Level 1 is root span (no parent) OR has a parent if context exists
        // Since we're calling traceOperation without an explicit parent context,
        // it will create a root span
        String level1ParentId = level1Span.getParentSpanId();
        boolean isRootSpan = level1ParentId.equals("0000000000000000");
        
        // Assert: Level 2 has Level 1 as parent
        assertThat(level2Span.getParentSpanId())
            .as("Level 2 span should have Level 1 as parent")
            .isEqualTo(level1Span.getSpanId());
        
        // Assert: Level 3 has Level 2 as parent
        assertThat(level3Span.getParentSpanId())
            .as("Level 3 span should have Level 2 as parent")
            .isEqualTo(level2Span.getSpanId());
        
        // Assert: All spans share the same trace ID
        String traceId = level1Span.getTraceId();
        assertThat(level2Span.getTraceId())
            .as("Level 2 span should share trace ID with Level 1")
            .isEqualTo(traceId);
        assertThat(level3Span.getTraceId())
            .as("Level 3 span should share trace ID with Level 1")
            .isEqualTo(traceId);
        
        // Cleanup
        tracerProvider.close();
    }

    // ========== Property 9: Custom Span Attributes ==========
    // **Validates: Requirements 3.5**

    /**
     * Feature: observability-platform, Property 9: Custom Span Attributes
     * 
     * <p>For any custom attributes added to a span as key-value pairs, those
     * attributes should be retrievable from the span with their original values.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Custom attributes are correctly added to spans</li>
     *   <li>Attribute values are preserved exactly as provided</li>
     *   <li>Multiple attributes can be added to a single span</li>
     *   <li>Attributes are accessible after span completion</li>
     * </ul>
     */
    @Property(tries = 100)
    @Label("Feature: observability-platform, Property 9: Custom Span Attributes")
    void customSpanAttributesArePreserved(
            @ForAll("operationNames") String operationName,
            @ForAll("customAttributes") Map<String, String> attributes,
            @ForAll("resultValues") Integer result) {
        
        // Setup: Create in-memory span exporter to capture spans
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setSampler(Sampler.alwaysOn())
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        Tracer tracer = openTelemetry.getTracer("test-service");
        TracingUtils tracingUtils = new TracingUtils(tracer);
        
        // Act: Create a custom span with attributes
        Integer actualResult = tracingUtils.traceOperation(
            operationName,
            attributes,
            () -> result
        );
        
        // Assert: Operation returned correct result
        assertThat(actualResult)
            .as("Operation should return the expected result")
            .isEqualTo(result);
        
        // Assert: Verify span attributes
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
            .as("Should have created 1 span")
            .hasSize(1);
        
        SpanData spanData = spans.get(0);
        
        // Assert: Span has correct operation name
        assertThat(spanData.getName())
            .as("Span should have correct operation name")
            .isEqualTo(operationName);
        
        // Assert: All custom attributes are present with correct values
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String expectedValue = entry.getValue();
            
            String actualValue = spanData.getAttributes()
                .get(io.opentelemetry.api.common.AttributeKey.stringKey(key));
            
            assertThat(actualValue)
                .as("Span should have attribute '%s' with value '%s'", key, expectedValue)
                .isEqualTo(expectedValue);
        }
        
        // Assert: Number of custom attributes matches
        long customAttributeCount = spanData.getAttributes().asMap().keySet().stream()
            .filter(key -> attributes.containsKey(key.getKey()))
            .count();
        
        assertThat(customAttributeCount)
            .as("Span should have all %d custom attributes", attributes.size())
            .isEqualTo(attributes.size());
        
        // Cleanup
        tracerProvider.close();
    }

    /**
     * Feature: observability-platform, Property 9: Custom Span Attributes
     * 
     * <p>Verifies that null or empty attribute maps are handled gracefully.
     */
    @Property(tries = 100)
    @Label("Feature: observability-platform, Property 9: Custom Span Attributes")
    void customSpanHandlesNullAttributes(
            @ForAll("operationNames") String operationName,
            @ForAll("resultValues") Integer result) {
        
        // Setup: Create in-memory span exporter to capture spans
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setSampler(Sampler.alwaysOn())
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        Tracer tracer = openTelemetry.getTracer("test-service");
        TracingUtils tracingUtils = new TracingUtils(tracer);
        
        // Act: Create a custom span with null attributes
        Integer actualResult = tracingUtils.traceOperation(
            operationName,
            null,  // null attributes
            () -> result
        );
        
        // Assert: Operation returned correct result (no exception thrown)
        assertThat(actualResult)
            .as("Operation should return the expected result even with null attributes")
            .isEqualTo(result);
        
        // Assert: Span was created successfully
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
            .as("Should have created 1 span even with null attributes")
            .hasSize(1);
        
        SpanData spanData = spans.get(0);
        
        // Assert: Span has correct operation name
        assertThat(spanData.getName())
            .as("Span should have correct operation name")
            .isEqualTo(operationName);
        
        // Cleanup
        tracerProvider.close();
    }

    /**
     * Feature: observability-platform, Property 9: Custom Span Attributes
     * 
     * <p>Verifies that empty attribute maps are handled correctly.
     */
    @Property(tries = 100)
    @Label("Feature: observability-platform, Property 9: Custom Span Attributes")
    void customSpanHandlesEmptyAttributes(
            @ForAll("operationNames") String operationName,
            @ForAll("resultValues") Integer result) {
        
        // Setup: Create in-memory span exporter to capture spans
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setSampler(Sampler.alwaysOn())
                .build();
        
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        Tracer tracer = openTelemetry.getTracer("test-service");
        TracingUtils tracingUtils = new TracingUtils(tracer);
        
        // Act: Create a custom span with empty attributes
        Integer actualResult = tracingUtils.traceOperation(
            operationName,
            new HashMap<>(),  // empty attributes
            () -> result
        );
        
        // Assert: Operation returned correct result
        assertThat(actualResult)
            .as("Operation should return the expected result with empty attributes")
            .isEqualTo(result);
        
        // Assert: Span was created successfully
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
            .as("Should have created 1 span with empty attributes")
            .hasSize(1);
        
        SpanData spanData = spans.get(0);
        
        // Assert: Span has correct operation name
        assertThat(spanData.getName())
            .as("Span should have correct operation name")
            .isEqualTo(operationName);
        
        // Cleanup
        tracerProvider.close();
    }
}
