// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect.unit;

import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.aspect.HttpClientSpanAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HttpClientSpanAspect}.
 *
 * <p>Tests target service name inference from client class names, HTTP method
 * inference from method names, span attribute values for known HTTP client
 * scenarios, and conditional loading.</p>
 *
 * <p>Validates: Requirements 3.2, 3.3, 3.4, 3.7, 6.1, 6.2</p>
 */
class HttpClientSpanAspectTest {

    // ========== Mock infrastructure ==========

    private Tracer tracer;
    private Span span;
    private SpanBuilder spanBuilder;
    private HttpClientSpanAspect aspect;

    @BeforeEach
    void setUp() {
        span = mock(Span.class);
        when(span.setAttribute(anyString(), anyString())).thenReturn(span);
        when(span.setAttribute(anyString(), anyLong())).thenReturn(span);
        when(span.setAttribute(anyString(), anyBoolean())).thenReturn(span);
        when(span.setStatus(any(StatusCode.class))).thenReturn(span);
        when(span.setStatus(any(StatusCode.class), anyString())).thenReturn(span);

        Scope scope = mock(Scope.class);
        when(span.makeCurrent()).thenReturn(scope);

        spanBuilder = mock(SpanBuilder.class);
        when(spanBuilder.setSpanKind(any(SpanKind.class))).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);

        tracer = mock(Tracer.class);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);

        aspect = new HttpClientSpanAspect(tracer);
    }

    /**
     * Builds a mock ProceedingJoinPoint with a target whose simple class name
     * is the given className and whose method name is the given methodName.
     */
    private ProceedingJoinPoint buildJoinPoint(String className, String methodName, Object result)
            throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(result);

        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);

        // Create a target mock whose getClass().getSimpleName() returns className
        Object target = mock(Object.class);
        // We can't mock getClass(), so we use a named inner class approach via spy
        // Instead, we'll directly test the static methods and use a real joinPoint for integration
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        return joinPoint;
    }

    // ========== Target Service Name Inference ==========

    @Nested
    @DisplayName("Target service name inference from client class names")
    class TargetServiceInference {

        @Test
        @DisplayName("QueueServiceClient → queue-service")
        void queueServiceClient() {
            assertThat(HttpClientSpanAspect.inferTargetService("QueueServiceClient"))
                    .isEqualTo("queue-service");
        }

        @Test
        @DisplayName("EnvironmentServiceClient → environment-service")
        void environmentServiceClient() {
            assertThat(HttpClientSpanAspect.inferTargetService("EnvironmentServiceClient"))
                    .isEqualTo("environment-service");
        }

        @Test
        @DisplayName("SubscriptionServiceClient → subscription-service")
        void subscriptionServiceClient() {
            assertThat(HttpClientSpanAspect.inferTargetService("SubscriptionServiceClient"))
                    .isEqualTo("subscription-service");
        }

        @Test
        @DisplayName("ResponsesConnectorClient → responses-connector")
        void responsesConnectorClient() {
            assertThat(HttpClientSpanAspect.inferTargetService("ResponsesConnectorClient"))
                    .isEqualTo("responses-connector");
        }

        @Test
        @DisplayName("ProviderHttpClient → provider")
        void providerHttpClient() {
            assertThat(HttpClientSpanAspect.inferTargetService("ProviderHttpClient"))
                    .isEqualTo("provider");
        }

        @Test
        @DisplayName("null class name → unknown")
        void nullClassName() {
            assertThat(HttpClientSpanAspect.inferTargetService(null))
                    .isEqualTo("unknown");
        }

        @Test
        @DisplayName("empty class name → unknown")
        void emptyClassName() {
            assertThat(HttpClientSpanAspect.inferTargetService(""))
                    .isEqualTo("unknown");
        }

        @Test
        @DisplayName("bare 'Client' suffix → unknown (nothing left after stripping)")
        void bareClientSuffix() {
            assertThat(HttpClientSpanAspect.inferTargetService("Client"))
                    .isEqualTo("unknown");
        }
    }

    // ========== HTTP Method Inference ==========

    @Nested
    @DisplayName("HTTP method inference from method names")
    class HttpMethodInference {

        // --- GET methods ---

        @Test
        @DisplayName("getEnvironment → GET")
        void getEnvironment() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("getEnvironment")).isEqualTo("GET");
        }

        @Test
        @DisplayName("lookupProvider → GET")
        void lookupProvider() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("lookupProvider")).isEqualTo("GET");
        }

        @Test
        @DisplayName("listSubscriptions → GET")
        void listSubscriptions() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("listSubscriptions")).isEqualTo("GET");
        }

        @Test
        @DisplayName("findByDomainId → GET")
        void findByDomainId() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("findByDomainId")).isEqualTo("GET");
        }

        @Test
        @DisplayName("ping → GET")
        void ping() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("ping")).isEqualTo("GET");
        }

        // --- POST methods ---

        @Test
        @DisplayName("createSubscription → POST")
        void createSubscription() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("createSubscription")).isEqualTo("POST");
        }

        @Test
        @DisplayName("enqueueMessage → POST")
        void enqueueMessage() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("enqueueMessage")).isEqualTo("POST");
        }

        @Test
        @DisplayName("validateToken → POST")
        void validateToken() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("validateToken")).isEqualTo("POST");
        }

        @Test
        @DisplayName("checkAccess → POST")
        void checkAccess() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("checkAccess")).isEqualTo("POST");
        }

        @Test
        @DisplayName("sendNotification → POST")
        void sendNotification() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("sendNotification")).isEqualTo("POST");
        }

        // --- PUT methods ---

        @Test
        @DisplayName("updateEnvironment → PUT")
        void updateEnvironment() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("updateEnvironment")).isEqualTo("PUT");
        }

        @Test
        @DisplayName("modifySubscription → PUT")
        void modifySubscription() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("modifySubscription")).isEqualTo("PUT");
        }

        // --- DELETE methods ---

        @Test
        @DisplayName("deleteSubscription → DELETE")
        void deleteSubscription() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("deleteSubscription")).isEqualTo("DELETE");
        }

        @Test
        @DisplayName("removeEntry → DELETE")
        void removeEntry() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("removeEntry")).isEqualTo("DELETE");
        }

        // --- UNKNOWN ---

        @Test
        @DisplayName("processMessage → UNKNOWN")
        void unknownMethod() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("processMessage")).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("null method name → UNKNOWN")
        void nullMethodName() {
            assertThat(HttpClientSpanAspect.inferHttpMethod(null)).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("empty method name → UNKNOWN")
        void emptyMethodName() {
            assertThat(HttpClientSpanAspect.inferHttpMethod("")).isEqualTo("UNKNOWN");
        }
    }

    // ========== Span Attribute Values for Known HTTP Client Scenarios ==========

    @Nested
    @DisplayName("Span attribute values for known HTTP client scenarios")
    class SpanAttributes {

        @Test
        @DisplayName("Successful call: span status OK, http.status_code=200, span ends")
        void successfulCallSetsOkStatusAndEnds() throws Throwable {
            ProceedingJoinPoint jp = buildJoinPoint("QueueServiceClient", "enqueueMessage", "ok");

            aspect.traceHttpClientCall(jp);

            verify(span).setStatus(StatusCode.OK);
            verify(span).setAttribute("http.status_code", 200L);
            verify(span).end();
        }

        @Test
        @DisplayName("Span kind is CLIENT")
        void spanKindIsClient() throws Throwable {
            ProceedingJoinPoint jp = buildJoinPoint("QueueServiceClient", "enqueueMessage", "ok");

            aspect.traceHttpClientCall(jp);

            verify(spanBuilder).setSpanKind(SpanKind.CLIENT);
        }

        @Test
        @DisplayName("Exception sets span status ERROR, records exception, and re-throws")
        void exceptionSetsErrorStatusAndRethrows() throws Throwable {
            ProceedingJoinPoint jp = buildJoinPoint("QueueServiceClient", "enqueueMessage", null);
            RuntimeException ex = new RuntimeException("Connection refused");
            when(jp.proceed()).thenThrow(ex);

            assertThatThrownBy(() -> aspect.traceHttpClientCall(jp))
                    .isSameAs(ex);

            verify(span).recordException(ex);
            verify(span).setStatus(StatusCode.ERROR, "Connection refused");
            verify(span).end();
        }
    }

    // ========== Conditional Loading ==========

    @Nested
    @DisplayName("Conditional loading: aspect not created when observability.enabled=false")
    class ConditionalLoading {

        @Test
        @DisplayName("@ConditionalOnProperty(name='observability.enabled', havingValue='true') is present")
        void hasConditionalOnPropertyAnnotation() {
            ConditionalOnProperty annotation =
                    HttpClientSpanAspect.class.getAnnotation(ConditionalOnProperty.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.name()).containsExactly("observability.enabled");
            assertThat(annotation.havingValue()).isEqualTo("true");
        }
    }
}
