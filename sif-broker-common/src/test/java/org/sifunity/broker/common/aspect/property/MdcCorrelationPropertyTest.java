// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect.property;

import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Scope;
import net.jqwik.api.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.sifunity.broker.common.aspect.RepositorySpanAspect;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property 6: MDC correlation attribute propagation.
 *
 * <p><em>For any</em> span created by any of the three aspects, if MDC contains a non-empty
 * value for {@code correlation_id}, {@code sif_service_name}, or {@code zone}, then the span
 * SHALL include that value as an attribute with the same key.</p>
 *
 * <p>Tested with {@link RepositorySpanAspect} — the MDC logic is identical across all
 * three aspects.</p>
 *
 * <p><strong>Validates: Requirements 5.4</strong></p>
 */
class MdcCorrelationPropertyTest {

    private static final List<String> MDC_KEYS = List.of("correlation_id", "sif_service_name", "zone");

    // ========== Arbitraries ==========

    /**
     * Generates a random MDC state for a single key: null, empty string, or a non-empty value.
     */
    @Provide
    Arbitrary<String> mdcValues() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30)
        );
    }

    // ========== Helpers ==========

    private record TracerAndSpan(Tracer tracer, Span span) {}

    private TracerAndSpan buildMockTracerAndSpan() {
        Span span = mock(Span.class);
        when(span.setAttribute(anyString(), anyString())).thenReturn(span);
        when(span.setAttribute(anyString(), anyLong())).thenReturn(span);
        when(span.setAttribute(anyString(), anyBoolean())).thenReturn(span);
        when(span.setStatus(any(StatusCode.class))).thenReturn(span);
        when(span.setStatus(any(StatusCode.class), anyString())).thenReturn(span);

        Scope scope = mock(Scope.class);
        when(span.makeCurrent()).thenReturn(scope);

        SpanBuilder spanBuilder = mock(SpanBuilder.class);
        when(spanBuilder.setSpanKind(any(SpanKind.class))).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);

        Tracer tracer = mock(Tracer.class);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);

        return new TracerAndSpan(tracer, span);
    }

    private ProceedingJoinPoint buildSuccessJoinPoint(Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(result);

        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("findById");
        when(joinPoint.getSignature()).thenReturn(signature);

        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        return joinPoint;
    }

    // ========== Properties ==========

    /**
     * For random MDC states (each of correlation_id, sif_service_name, zone independently
     * null, empty, or non-empty), verify that the span includes the attribute when the MDC
     * value is non-empty, and does NOT include the attribute when the MDC value is null or empty.
     *
     * <p><strong>Validates: Requirements 5.4</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 6: MDC correlation attribute propagation")
    @Property(tries = 20)
    void mdcAttributesPropagatedWhenNonEmpty(
            @ForAll("mdcValues") String correlationId,
            @ForAll("mdcValues") String serviceName,
            @ForAll("mdcValues") String zone) throws Throwable {

        // Build MDC state map
        Map<String, String> mdcState = Map.of(
                "correlation_id", correlationId == null ? "\0NULL" : correlationId,
                "sif_service_name", serviceName == null ? "\0NULL" : serviceName,
                "zone", zone == null ? "\0NULL" : zone
        );

        // Clear MDC and set values
        MDC.clear();
        try {
            if (correlationId != null) MDC.put("correlation_id", correlationId);
            if (serviceName != null) MDC.put("sif_service_name", serviceName);
            if (zone != null) MDC.put("zone", zone);

            TracerAndSpan tas = buildMockTracerAndSpan();
            RepositorySpanAspect aspect = new RepositorySpanAspect(tas.tracer());
            ProceedingJoinPoint joinPoint = buildSuccessJoinPoint("result");

            aspect.traceRepositoryCall(joinPoint);

            // Verify each MDC key
            verifyMdcAttribute(tas.span(), "correlation_id", correlationId);
            verifyMdcAttribute(tas.span(), "sif_service_name", serviceName);
            verifyMdcAttribute(tas.span(), "zone", zone);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Verifies that span.setAttribute(key, value) was called when value is non-empty,
     * and was NOT called when value is null or empty.
     */
    private void verifyMdcAttribute(Span span, String key, String value) {
        if (value != null && !value.isEmpty()) {
            verify(span).setAttribute(eq(key), eq(value));
        } else {
            verify(span, never()).setAttribute(eq(key), anyString());
        }
    }
}
