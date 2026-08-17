// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TracingUtils.
 * 
 * <p>These tests verify specific examples, edge cases, and error conditions
 * for the TracingUtils utility class.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Span creation with operation names</li>
 *   <li>Exception handling and error status</li>
 *   <li>Edge cases (null attributes, empty operation names, etc.)</li>
 * </ul>
 */
class TracingUtilsTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private OpenTelemetry openTelemetry;
    private Tracer tracer;
    private TracingUtils tracingUtils;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        
        tracer = openTelemetry.getTracer("test-service");
        tracingUtils = new TracingUtils(tracer);
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
        MDC.clear();
    }

    // ========== Synchronous Operation Tests ==========

    @Test
    void traceOperation_createsSpanWithOperationName() {
        // Given
        String operationName = "test.operation";
        Map<String, String> attributes = Map.of("key1", "value1");
        
        // When
        String result = tracingUtils.traceOperation(
            operationName,
            attributes,
            () -> "success"
        );
        
        // Then
        assertThat(result).isEqualTo("success");
        
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo(operationName);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("key1")))
            .isEqualTo("value1");
    }

    @Test
    void traceOperation_setsOkStatusOnSuccess() {
        // When
        tracingUtils.traceOperation(
            "test.operation",
            Map.of(),
            () -> "success"
        );
        
        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @Test
    void traceOperation_recordsExceptionAndSetsErrorStatus() {
        // Given
        RuntimeException exception = new RuntimeException("Test error");
        
        // When/Then
        assertThatThrownBy(() -> 
            tracingUtils.traceOperation(
                "test.operation",
                Map.of(),
                () -> {
                    throw exception;
                }
            )
        ).isEqualTo(exception);
        
        // Verify span recorded the exception
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEqualTo("Test error");
        assertThat(span.getEvents()).isNotEmpty();
        assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");
    }

    @Test
    void traceOperation_handlesNullAttributes() {
        // When
        String result = tracingUtils.traceOperation(
            "test.operation",
            null,  // null attributes
            () -> "success"
        );
        
        // Then
        assertThat(result).isEqualTo("success");
        
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getName()).isEqualTo("test.operation");
    }

    @Test
    void traceOperation_handlesEmptyAttributes() {
        // When
        String result = tracingUtils.traceOperation(
            "test.operation",
            Map.of(),  // empty attributes
            () -> "success"
        );
        
        // Then
        assertThat(result).isEqualTo("success");
        
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getName()).isEqualTo("test.operation");
    }

    @Test
    void traceOperation_handlesMultipleAttributes() {
        // Given
        Map<String, String> attributes = Map.of(
            "key1", "value1",
            "key2", "value2",
            "key3", "value3"
        );
        
        // When
        tracingUtils.traceOperation(
            "test.operation",
            attributes,
            () -> "success"
        );
        
        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        SpanData span = spans.get(0);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("key1")))
            .isEqualTo("value1");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("key2")))
            .isEqualTo("value2");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("key3")))
            .isEqualTo("value3");
    }

    @Test
    void traceOperation_voidOperation_completesSuccessfully() {
        // Given
        final boolean[] executed = {false};
        
        // When
        tracingUtils.traceOperation(
            "test.operation",
            Map.of(),
            () -> {
                executed[0] = true;
            }
        );
        
        // Then
        assertThat(executed[0]).isTrue();
        
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    // ========== Helper Method Tests ==========

    @Test
    void addAttribute_addsAttributeToCurrentSpan() {
        // Given
        Span span = tracer.spanBuilder("test.operation").startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // When
            tracingUtils.addAttribute("test.key", "test.value");
        } finally {
            span.end();
        }
        
        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        SpanData spanData = spans.get(0);
        assertThat(spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("test.key")))
            .isEqualTo("test.value");
    }

    @Test
    void addAttribute_doesNothingWhenNoCurrentSpan() {
        // When - no current span
        tracingUtils.addAttribute("test.key", "test.value");
        
        // Then - no exception thrown, no spans created
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).isEmpty();
    }

    @Test
    void addEvent_addsEventToCurrentSpan() {
        // Given
        Span span = tracer.spanBuilder("test.operation").startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // When
            tracingUtils.addEvent("test.event");
        } finally {
            span.end();
        }
        
        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        SpanData spanData = spans.get(0);
        assertThat(spanData.getEvents()).hasSize(1);
        assertThat(spanData.getEvents().get(0).getName()).isEqualTo("test.event");
    }

    @Test
    void addEvent_doesNothingWhenNoCurrentSpan() {
        // When - no current span
        tracingUtils.addEvent("test.event");
        
        // Then - no exception thrown, no spans created
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).isEmpty();
    }

    @Test
    void recordException_recordsExceptionInCurrentSpan() {
        // Given
        Span span = tracer.spanBuilder("test.operation").startSpan();
        RuntimeException exception = new RuntimeException("Test error");
        
        try (Scope scope = span.makeCurrent()) {
            // When
            tracingUtils.recordException(exception);
        } finally {
            span.end();
        }
        
        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        SpanData spanData = spans.get(0);
        assertThat(spanData.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(spanData.getStatus().getDescription()).isEqualTo("Test error");
        assertThat(spanData.getEvents()).isNotEmpty();
        assertThat(spanData.getEvents().get(0).getName()).isEqualTo("exception");
    }

    @Test
    void recordException_doesNothingWhenNoCurrentSpan() {
        // When - no current span
        RuntimeException exception = new RuntimeException("Test error");
        tracingUtils.recordException(exception);
        
        // Then - no exception thrown, no spans created
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).isEmpty();
    }

    // ========== Edge Case Tests ==========

    @Test
    void traceOperation_handlesEmptyOperationName() {
        // When
        String result = tracingUtils.traceOperation(
            "",  // empty operation name
            Map.of(),
            () -> "success"
        );
        
        // Then
        assertThat(result).isEqualTo("success");
        
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        // OpenTelemetry replaces empty operation names with "<unspecified span name>"
        assertThat(spans.get(0).getName()).isEqualTo("<unspecified span name>");
    }

    @Test
    void traceOperation_handlesSpecialCharactersInOperationName() {
        // Given
        String operationName = "test.operation-with_special.chars/123";
        
        // When
        tracingUtils.traceOperation(
            operationName,
            Map.of(),
            () -> "success"
        );
        
        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getName()).isEqualTo(operationName);
    }

    @Test
    void traceOperation_handlesNullReturnValue() {
        // When
        String result = tracingUtils.traceOperation(
            "test.operation",
            Map.of(),
            () -> null
        );
        
        // Then
        assertThat(result).isNull();
        
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @Test
    void traceOperation_handlesLongRunningOperation() throws InterruptedException {
        // When
        long startTime = System.currentTimeMillis();
        tracingUtils.traceOperation(
            "test.long.operation",
            Map.of(),
            () -> {
                try {
                    Thread.sleep(100);  // Simulate long-running operation
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "success";
            }
        );
        long endTime = System.currentTimeMillis();
        
        // Then
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(100);
        
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        
        SpanData span = spans.get(0);
        long spanDuration = span.getEndEpochNanos() - span.getStartEpochNanos();
        assertThat(spanDuration).isGreaterThan(0);
    }

    // ========== MDC Correlation Attribute Tests ==========

    @Test
    void traceOperation_addsCorrelationIdFromMdc() {
        // Given
        MDC.put("correlation_id", "corr-123-abc");

        // When
        tracingUtils.traceOperation("test.operation", Map.of(), () -> "ok");

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("correlation_id")))
            .isEqualTo("corr-123-abc");
    }

    @Test
    void traceOperation_addsSifServiceNameFromMdc() {
        // Given
        MDC.put("sif_service_name", "SchoolInfos");

        // When
        tracingUtils.traceOperation("test.operation", Map.of(), () -> "ok");

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("sif_service_name")))
            .isEqualTo("SchoolInfos");
    }

    @Test
    void traceOperation_addsZoneFromMdc() {
        // Given
        MDC.put("zone", "district-42");

        // When
        tracingUtils.traceOperation("test.operation", Map.of(), () -> "ok");

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("zone")))
            .isEqualTo("district-42");
    }

    @Test
    void traceOperation_addsAllMdcCorrelationAttributes() {
        // Given
        MDC.put("correlation_id", "corr-456");
        MDC.put("sif_service_name", "StudentPersonals");
        MDC.put("zone", "zone-A");

        // When
        tracingUtils.traceOperation("test.operation", Map.of(), () -> "ok");

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        SpanData span = spans.get(0);
        assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("correlation_id")))
            .isEqualTo("corr-456");
        assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("sif_service_name")))
            .isEqualTo("StudentPersonals");
        assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("zone")))
            .isEqualTo("zone-A");
    }

    @Test
    void traceOperation_skipsNullMdcValues() {
        // Given - MDC is empty, no values set

        // When
        tracingUtils.traceOperation("test.operation", Map.of(), () -> "ok");

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        SpanData span = spans.get(0);
        assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("correlation_id")))
            .isNull();
        assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("sif_service_name")))
            .isNull();
        assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("zone")))
            .isNull();
    }

    @Test
    void traceOperation_skipsEmptyMdcValues() {
        // Given
        MDC.put("correlation_id", "");
        MDC.put("sif_service_name", "");
        MDC.put("zone", "");

        // When
        tracingUtils.traceOperation("test.operation", Map.of(), () -> "ok");

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        SpanData span = spans.get(0);
        assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("correlation_id")))
            .isNull();
        assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("sif_service_name")))
            .isNull();
        assertThat(span.getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("zone")))
            .isNull();
    }

    @Test
    void traceOperation_mdcAttributesDoNotOverrideExplicitAttributes() {
        // Given - MDC has correlation_id, but explicit attributes also set it
        MDC.put("correlation_id", "mdc-value");

        // When - explicit attribute should win (applied after MDC)
        tracingUtils.traceOperation("test.operation",
            Map.of("correlation_id", "explicit-value"), () -> "ok");

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getAttributes()
            .get(io.opentelemetry.api.common.AttributeKey.stringKey("correlation_id")))
            .isEqualTo("explicit-value");
    }
}
