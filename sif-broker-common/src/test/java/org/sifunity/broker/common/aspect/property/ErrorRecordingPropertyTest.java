// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect.property;

import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Scope;
import net.jqwik.api.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.sifunity.broker.common.aspect.HttpClientSpanAspect;
import org.sifunity.broker.common.aspect.RedisSpanAspect;
import org.sifunity.broker.common.aspect.RepositorySpanAspect;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property 5: Error recording across all aspects.
 *
 * <p><em>For any</em> exception thrown by an intercepted method (repository, Redis, or
 * HTTP client), the enclosing span SHALL record the exception event and set span status
 * to {@code ERROR}. The original exception SHALL be re-thrown unchanged.</p>
 *
 * <p><strong>Validates: Requirements 1.7, 2.6, 3.6</strong></p>
 */
class ErrorRecordingPropertyTest {

    // ========== Exception types to generate ==========

    private static final List<Throwable> EXCEPTION_POOL = List.of(
            new RuntimeException("random runtime error"),
            new IllegalArgumentException("bad argument"),
            new IllegalStateException("invalid state"),
            new NullPointerException("null ref"),
            new UnsupportedOperationException("not supported"),
            new IOException("io failure"),
            new ArrayIndexOutOfBoundsException("index out of bounds"),
            new ClassCastException("bad cast"),
            new ArithmeticException("divide by zero"),
            new StackOverflowError("stack overflow")
    );

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Throwable> exceptions() {
        return Arbitraries.of(EXCEPTION_POOL);
    }

    // ========== Helpers ==========

    /**
     * Creates a mock Tracer that returns a mock Span via a mock SpanBuilder.
     * The mock Span's makeCurrent() returns a no-op Scope.
     */
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

    /**
     * Creates a mock ProceedingJoinPoint that throws the given exception on proceed().
     * The target is a plain Object — aspects will extract class info from it but that's
     * not what we're testing here (error recording is the focus).
     */
    private ProceedingJoinPoint buildThrowingJoinPoint(Throwable exception, String methodName)
            throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(exception);

        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);

        // Aspects call joinPoint.getTarget().getClass().getSimpleName() for class-based inference.
        // A plain Object is sufficient — entity type / service name extraction isn't under test here.
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getArgs()).thenReturn(new Object[]{"test-key:value"});

        return joinPoint;
    }

    // ========== Properties ==========

    /**
     * RepositorySpanAspect: exception is recorded on span, status set to ERROR,
     * and original exception re-thrown.
     *
     * <p><strong>Validates: Requirements 1.7</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 5: Error recording across all aspects")
    @Property(tries = 20)
    void repositoryAspectRecordsExceptionAndRethrows(@ForAll("exceptions") Throwable exception) throws Throwable {
        TracerAndSpan tas = buildMockTracerAndSpan();
        RepositorySpanAspect aspect = new RepositorySpanAspect(tas.tracer());

        ProceedingJoinPoint joinPoint = buildThrowingJoinPoint(exception, "findById");

        Throwable thrown = catchThrowable(() -> aspect.traceRepositoryCall(joinPoint));

        assertThat(thrown).isSameAs(exception);
        verify(tas.span()).recordException(exception);
        verify(tas.span()).setStatus(StatusCode.ERROR, exception.getMessage());
        verify(tas.span()).end();
    }

    /**
     * RedisSpanAspect: exception is recorded on span, status set to ERROR,
     * and original exception re-thrown.
     *
     * <p><strong>Validates: Requirements 2.6</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 5: Error recording across all aspects")
    @Property(tries = 20)
    void redisAspectRecordsExceptionAndRethrows(@ForAll("exceptions") Throwable exception) throws Throwable {
        TracerAndSpan tas = buildMockTracerAndSpan();
        RedisSpanAspect aspect = new RedisSpanAspect(tas.tracer());

        ProceedingJoinPoint joinPoint = buildThrowingJoinPoint(exception, "get");

        Throwable thrown = catchThrowable(() -> aspect.traceRedisCall(joinPoint));

        assertThat(thrown).isSameAs(exception);
        verify(tas.span()).recordException(exception);
        verify(tas.span()).setStatus(StatusCode.ERROR, exception.getMessage());
        verify(tas.span()).end();
    }

    /**
     * HttpClientSpanAspect: exception is recorded on span, status set to ERROR,
     * and original exception re-thrown.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 5: Error recording across all aspects")
    @Property(tries = 20)
    void httpClientAspectRecordsExceptionAndRethrows(@ForAll("exceptions") Throwable exception) throws Throwable {
        TracerAndSpan tas = buildMockTracerAndSpan();
        HttpClientSpanAspect aspect = new HttpClientSpanAspect(tas.tracer());

        ProceedingJoinPoint joinPoint = buildThrowingJoinPoint(exception, "getEnvironment");

        Throwable thrown = catchThrowable(() -> aspect.traceHttpClientCall(joinPoint));

        assertThat(thrown).isSameAs(exception);
        verify(tas.span()).recordException(exception);
        verify(tas.span()).setStatus(StatusCode.ERROR, exception.getMessage());
        verify(tas.span()).end();
    }
}
