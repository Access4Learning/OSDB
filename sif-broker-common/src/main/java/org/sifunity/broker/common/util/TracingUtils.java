// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Utility class for creating custom spans and tracing business operations.
 * 
 * <p>This class provides simple APIs for:
 * <ul>
 *   <li>Creating custom spans for synchronous operations</li>
 *   <li>Adding custom attributes to spans</li>
 *   <li>Automatic exception recording and error status</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Synchronous operation
 * String result = tracingUtils.traceOperation(
 *     "queue.process_message",
 *     Map.of("queue.name", "immediate-requests", "message.id", "msg-123"),
 *     () -> processMessage(message)
 * );
 * }</pre>
 * 
 * <p>This component is only available when observability is enabled.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "observability.enabled", havingValue = "true", matchIfMissing = false)
public class TracingUtils extends TracingUtilsBase {
    
    private final Tracer tracer;
    
    public TracingUtils(Tracer tracer) {
        this.tracer = tracer;
        log.info("TracingUtils initialized with REAL tracer (observability enabled). Spans WILL be exported.");
    }
    
    /**
     * Traces a synchronous operation with a custom span.
     * 
     * <p>This method:
     * <ul>
     *   <li>Creates a new span with the given operation name</li>
     *   <li>Adds MDC correlation attributes ({@code correlation_id}, {@code sif_service_name}, {@code zone}) when available</li>
     *   <li>Adds all provided attributes to the span</li>
     *   <li>Executes the operation within the span context</li>
     *   <li>Records exceptions and sets error status if operation fails</li>
     *   <li>Ends the span when operation completes</li>
     * </ul>
     * 
     * @param operationName the name of the operation (e.g., "queue.process_message")
     * @param attributes custom attributes to add to the span
     * @param operation the operation to execute
     * @param <T> the return type of the operation
     * @return the result of the operation
     * @throws RuntimeException if the operation throws an exception
     */
    @Override
    public <T> T traceOperation(String operationName, 
                                Map<String, String> attributes,
                                Supplier<T> operation) {
        Span span = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        
        // Add MDC correlation attributes when available
        addMdcAttribute(span, "correlation_id");
        addMdcAttribute(span, "sif_service_name");
        addMdcAttribute(span, "zone");
        
        // Add custom attributes
        if (attributes != null) {
            attributes.forEach(span::setAttribute);
        }
        
        try (Scope scope = span.makeCurrent()) {
            T result = operation.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            log.error("Operation {} failed with exception", operationName, e);
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Traces a synchronous operation with a custom span (no return value).
     * 
     * @param operationName the name of the operation
     * @param attributes custom attributes to add to the span
     * @param operation the operation to execute
     */
    @Override
    public void traceOperation(String operationName, 
                               Map<String, String> attributes,
                               Runnable operation) {
        traceOperation(operationName, attributes, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * Adds an MDC value as a span attribute if present and non-empty.
     * 
     * @param span the span to add the attribute to
     * @param key the MDC key (also used as the span attribute key)
     */
    private void addMdcAttribute(Span span, String key) {
        String value = MDC.get(key);
        if (value != null && !value.isEmpty()) {
            span.setAttribute(key, value);
        }
    }
    
    /**
     * Adds a custom attribute to the current span.
     * 
     * <p>This is useful for adding attributes dynamically during operation execution.
     * If no span is currently active, this method does nothing.
     * 
     * @param key the attribute key
     * @param value the attribute value
     */
    @Override
    public void addAttribute(String key, String value) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.setAttribute(key, value);
        }
    }
    
    /**
     * Adds a custom event to the current span.
     * 
     * <p>Events are timestamped annotations that can be used to mark significant
     * points in the span's lifetime.
     * 
     * @param eventName the name of the event
     */
    @Override
    public void addEvent(String eventName) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.addEvent(eventName);
        }
    }
    
    /**
     * Records an exception in the current span.
     * 
     * @param exception the exception to record
     */
    @Override
    public void recordException(Exception exception) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.recordException(exception);
            currentSpan.setStatus(StatusCode.ERROR, exception.getMessage());
        }
    }
}
