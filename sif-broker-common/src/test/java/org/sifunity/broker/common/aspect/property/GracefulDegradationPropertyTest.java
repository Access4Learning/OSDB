// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect.property;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import net.jqwik.api.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.sifunity.broker.common.aspect.HttpClientSpanAspect;
import org.sifunity.broker.common.aspect.RedisSpanAspect;
import org.sifunity.broker.common.aspect.RepositorySpanAspect;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property 7: Graceful degradation on tracer failure.
 *
 * <p><em>For any</em> intercepted method call, if the {@code Tracer} bean throws an exception
 * during span creation, the aspect SHALL catch the exception, log a warning, and execute
 * the original method, returning its result (or re-throwing its exception) as if no aspect
 * were present.</p>
 *
 * <p><strong>Validates: Requirements 6.5</strong></p>
 */
class GracefulDegradationPropertyTest {

    // ========== Tracer failure types ==========

    private static final List<RuntimeException> TRACER_FAILURES = List.of(
            new RuntimeException("tracer unavailable"),
            new NullPointerException("null tracer state"),
            new IllegalStateException("tracer not initialized"),
            new UnsupportedOperationException("span creation not supported"),
            new ClassCastException("tracer type mismatch"),
            new ArithmeticException("unexpected tracer error"),
            new ArrayIndexOutOfBoundsException("tracer internal error"),
            new SecurityException("tracer access denied")
    );

    // ========== Result values for successful method execution ==========

    private static final List<Object> RESULT_VALUES = List.of(
            "string-result",
            42,
            true,
            List.of("a", "b", "c"),
            new Object()
    );

    // ========== Exceptions the intercepted method might throw ==========

    private static final List<Throwable> METHOD_EXCEPTIONS = List.of(
            new RuntimeException("method failed"),
            new IllegalArgumentException("bad input"),
            new NullPointerException("null value"),
            new IllegalStateException("invalid state")
    );

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<RuntimeException> tracerFailures() {
        return Arbitraries.of(TRACER_FAILURES);
    }

    @Provide
    Arbitrary<Object> resultValues() {
        return Arbitraries.of(RESULT_VALUES);
    }

    @Provide
    Arbitrary<Throwable> methodExceptions() {
        return Arbitraries.of(METHOD_EXCEPTIONS);
    }

    // ========== Helpers ==========

    /**
     * Creates a mock Tracer whose {@code spanBuilder(...)} throws the given exception,
     * simulating a tracer failure during span creation.
     */
    private Tracer buildFailingTracer(RuntimeException failure) {
        Tracer tracer = mock(Tracer.class);
        SpanBuilder spanBuilder = mock(SpanBuilder.class);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.setSpanKind(any(SpanKind.class))).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenThrow(failure);
        return tracer;
    }

    /**
     * Creates a mock ProceedingJoinPoint that returns the given result on proceed().
     */
    private ProceedingJoinPoint buildSuccessJoinPoint(Object result, String methodName) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(result);

        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);

        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getArgs()).thenReturn(new Object[]{"test-key:value"});

        return joinPoint;
    }

    /**
     * Creates a mock ProceedingJoinPoint that throws the given exception on proceed().
     */
    private ProceedingJoinPoint buildThrowingJoinPoint(Throwable exception, String methodName) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(exception);

        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);

        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getArgs()).thenReturn(new Object[]{"test-key:value"});

        return joinPoint;
    }

    // ========== Properties: Successful method execution despite tracer failure ==========

    /**
     * RepositorySpanAspect: when tracer fails, original method result is returned.
     *
     * <p><strong>Validates: Requirements 6.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 7: Graceful degradation on tracer failure")
    @Property(tries = 20)
    void repositoryAspectReturnsResultWhenTracerFails(
            @ForAll("tracerFailures") RuntimeException tracerFailure,
            @ForAll("resultValues") Object expectedResult) throws Throwable {

        Tracer failingTracer = buildFailingTracer(tracerFailure);
        RepositorySpanAspect aspect = new RepositorySpanAspect(failingTracer);
        ProceedingJoinPoint joinPoint = buildSuccessJoinPoint(expectedResult, "findById");

        Object actualResult = aspect.traceRepositoryCall(joinPoint);

        assertThat(actualResult).isSameAs(expectedResult);
        verify(joinPoint).proceed();
    }

    /**
     * RedisSpanAspect: when tracer fails, original method result is returned.
     *
     * <p><strong>Validates: Requirements 6.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 7: Graceful degradation on tracer failure")
    @Property(tries = 20)
    void redisAspectReturnsResultWhenTracerFails(
            @ForAll("tracerFailures") RuntimeException tracerFailure,
            @ForAll("resultValues") Object expectedResult) throws Throwable {

        Tracer failingTracer = buildFailingTracer(tracerFailure);
        RedisSpanAspect aspect = new RedisSpanAspect(failingTracer);
        ProceedingJoinPoint joinPoint = buildSuccessJoinPoint(expectedResult, "get");

        Object actualResult = aspect.traceRedisCall(joinPoint);

        assertThat(actualResult).isSameAs(expectedResult);
        verify(joinPoint).proceed();
    }

    /**
     * HttpClientSpanAspect: when tracer fails, original method result is returned.
     *
     * <p><strong>Validates: Requirements 6.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 7: Graceful degradation on tracer failure")
    @Property(tries = 20)
    void httpClientAspectReturnsResultWhenTracerFails(
            @ForAll("tracerFailures") RuntimeException tracerFailure,
            @ForAll("resultValues") Object expectedResult) throws Throwable {

        Tracer failingTracer = buildFailingTracer(tracerFailure);
        HttpClientSpanAspect aspect = new HttpClientSpanAspect(failingTracer);
        ProceedingJoinPoint joinPoint = buildSuccessJoinPoint(expectedResult, "getEnvironment");

        Object actualResult = aspect.traceHttpClientCall(joinPoint);

        assertThat(actualResult).isSameAs(expectedResult);
        verify(joinPoint).proceed();
    }

    // ========== Properties: Method exception re-thrown despite tracer failure ==========

    /**
     * RepositorySpanAspect: when tracer fails AND method throws, original exception is re-thrown.
     *
     * <p><strong>Validates: Requirements 6.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 7: Graceful degradation on tracer failure")
    @Property(tries = 20)
    void repositoryAspectRethrowsExceptionWhenTracerFails(
            @ForAll("tracerFailures") RuntimeException tracerFailure,
            @ForAll("methodExceptions") Throwable methodException) throws Throwable {

        Tracer failingTracer = buildFailingTracer(tracerFailure);
        RepositorySpanAspect aspect = new RepositorySpanAspect(failingTracer);
        ProceedingJoinPoint joinPoint = buildThrowingJoinPoint(methodException, "findById");

        Throwable thrown = catchThrowable(() -> aspect.traceRepositoryCall(joinPoint));

        assertThat(thrown).isSameAs(methodException);
        verify(joinPoint).proceed();
    }

    /**
     * RedisSpanAspect: when tracer fails AND method throws, original exception is re-thrown.
     *
     * <p><strong>Validates: Requirements 6.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 7: Graceful degradation on tracer failure")
    @Property(tries = 20)
    void redisAspectRethrowsExceptionWhenTracerFails(
            @ForAll("tracerFailures") RuntimeException tracerFailure,
            @ForAll("methodExceptions") Throwable methodException) throws Throwable {

        Tracer failingTracer = buildFailingTracer(tracerFailure);
        RedisSpanAspect aspect = new RedisSpanAspect(failingTracer);
        ProceedingJoinPoint joinPoint = buildThrowingJoinPoint(methodException, "get");

        Throwable thrown = catchThrowable(() -> aspect.traceRedisCall(joinPoint));

        assertThat(thrown).isSameAs(methodException);
        verify(joinPoint).proceed();
    }

    /**
     * HttpClientSpanAspect: when tracer fails AND method throws, original exception is re-thrown.
     *
     * <p><strong>Validates: Requirements 6.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 7: Graceful degradation on tracer failure")
    @Property(tries = 20)
    void httpClientAspectRethrowsExceptionWhenTracerFails(
            @ForAll("tracerFailures") RuntimeException tracerFailure,
            @ForAll("methodExceptions") Throwable methodException) throws Throwable {

        Tracer failingTracer = buildFailingTracer(tracerFailure);
        HttpClientSpanAspect aspect = new HttpClientSpanAspect(failingTracer);
        ProceedingJoinPoint joinPoint = buildThrowingJoinPoint(methodException, "getEnvironment");

        Throwable thrown = catchThrowable(() -> aspect.traceHttpClientCall(joinPoint));

        assertThat(thrown).isSameAs(methodException);
        verify(joinPoint).proceed();
    }
}
