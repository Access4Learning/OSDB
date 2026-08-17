// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that creates OpenTelemetry child spans around inter-service HTTP client calls.
 *
 * <p>Intercepts method calls on {@code @Component} classes in {@code client} packages
 * whose class name ends with {@code Client}, creating spans named
 * {@code http.client.{targetService}.{methodName}} with HTTP method inference,
 * target service name inference, and status code extraction.
 *
 * <p>This aspect follows the same conditional loading pattern as {@code RepositorySpanAspect}
 * and {@code RedisSpanAspect}: it is only registered when {@code observability.enabled=true}.
 * When disabled, no AOP proxying occurs on HTTP client beans.
 *
 * <p>Graceful degradation: if the Tracer fails during span creation, the aspect logs
 * a warning and proceeds without instrumentation.
 *
 * @see io.opentelemetry.api.trace.Tracer
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(name = "observability.enabled", havingValue = "true", matchIfMissing = true)
public class HttpClientSpanAspect {

    private final Tracer tracer;

    public HttpClientSpanAspect(Tracer tracer) {
        this.tracer = tracer;
        log.info(">>>>>> HttpClientSpanAspect initialized — HTTP client calls will produce trace spans");
    }

    /**
     * Pointcut matching HTTP client method calls in our application packages.
     *
     * <p>Combines {@code bean(*Client)} to match our HTTP client beans with an
     * {@code execution()} filter to only intercept methods declared in our client
     * packages. This prevents matching Spring infrastructure beans like
     * {@code jdbcClient} (DefaultJdbcClient is final and can't be proxied).</p>
     */
    @Pointcut("bean(*Client) && execution(* org.sifunity..client.*.*(..))")
    public void httpClientMethods() {}

    /**
     * Around advice that wraps HTTP client method calls with an OpenTelemetry span.
     *
     * @param joinPoint the intercepted method call
     * @return the result of the original method
     * @throws Throwable if the original method throws
     */
    @Around("httpClientMethods()")
    public Object traceHttpClientCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String targetService = inferTargetService(className);
        String httpMethod = inferHttpMethod(methodName);
        String spanName = "http.client." + targetService + "." + methodName;

        Span span;
        try {
            span = tracer.spanBuilder(spanName)
                    .setSpanKind(SpanKind.CLIENT)
                    .startSpan();
        } catch (Exception e) {
            log.warn("Failed to create span for {}: {}", spanName, e.getMessage());
            return joinPoint.proceed();
        }

        try (Scope scope = span.makeCurrent()) {
            // Static attributes
            span.setAttribute("http.target_service", targetService);
            span.setAttribute("http.method", httpMethod);

            // MDC correlation attributes
            addMdcAttribute(span, "correlation_id");
            addMdcAttribute(span, "sif_service_name");
            addMdcAttribute(span, "zone");

            Object result = joinPoint.proceed();

            span.setStatus(StatusCode.OK);

            // Extract status code from result
            int statusCode = extractStatusCode(result);
            span.setAttribute("http.status_code", (long) statusCode);

            return result;
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());

            // Try to extract status code from exception
            int statusCode = extractStatusCodeFromException(t);
            if (statusCode > 0) {
                span.setAttribute("http.status_code", (long) statusCode);
            }

            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * Infers the target service name from the HTTP client class name.
     *
     * <p>Strips known suffixes ({@code ServiceClient}, {@code HttpClient}, {@code Client})
     * and converts CamelCase to kebab-case.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code QueueServiceClient} → {@code queue-service}</li>
     *   <li>{@code EnvironmentServiceClient} → {@code environment-service}</li>
     *   <li>{@code SubscriptionServiceClient} → {@code subscription-service}</li>
     *   <li>{@code ResponsesConnectorClient} → {@code responses-connector}</li>
     *   <li>{@code ProviderHttpClient} → {@code provider}</li>
     * </ul>
     *
     * @param className the simple class name of the HTTP client
     * @return the inferred target service name in kebab-case
     */
    public static String inferTargetService(String className) {
        if (className == null || className.isEmpty()) {
            return "unknown";
        }

        // Strip known suffixes in order of specificity
        String stripped = className;
        if (stripped.endsWith("ServiceClient")) {
            stripped = stripped.substring(0, stripped.length() - "ServiceClient".length());
        } else if (stripped.endsWith("HttpClient")) {
            stripped = stripped.substring(0, stripped.length() - "HttpClient".length());
        } else if (stripped.endsWith("ConnectorClient")) {
            stripped = stripped.substring(0, stripped.length() - "ConnectorClient".length());
        } else if (stripped.endsWith("Client")) {
            stripped = stripped.substring(0, stripped.length() - "Client".length());
        }

        if (stripped.isEmpty()) {
            return "unknown";
        }

        // Convert CamelCase to kebab-case
        String kebab = camelToKebab(stripped);

        // Re-append the suffix type for ServiceClient and ConnectorClient
        if (className.endsWith("ServiceClient")) {
            return kebab + "-service";
        } else if (className.endsWith("ConnectorClient")) {
            return kebab + "-connector";
        }

        return kebab;
    }

    /**
     * Infers the HTTP method from the client method name.
     *
     * <p>Mapping:
     * <ul>
     *   <li>{@code get*}, {@code lookup*}, {@code list*}, {@code find*}, {@code ping} → GET</li>
     *   <li>{@code create*}, {@code enqueue*}, {@code validate*}, {@code check*}, {@code send*} → POST</li>
     *   <li>{@code update*}, {@code modify*} → PUT</li>
     *   <li>{@code delete*}, {@code remove*} → DELETE</li>
     *   <li>Default → UNKNOWN</li>
     * </ul>
     *
     * @param methodName the client method name
     * @return the inferred HTTP method string
     */
    public static String inferHttpMethod(String methodName) {
        if (methodName == null || methodName.isEmpty()) {
            return "UNKNOWN";
        }

        String lower = methodName.toLowerCase();

        // GET methods
        if (lower.startsWith("get") || lower.startsWith("lookup") || lower.startsWith("list")
                || lower.startsWith("find") || lower.equals("ping")) {
            return "GET";
        }

        // POST methods
        if (lower.startsWith("create") || lower.startsWith("enqueue") || lower.startsWith("validate")
                || lower.startsWith("check") || lower.startsWith("send")) {
            return "POST";
        }

        // PUT methods
        if (lower.startsWith("update") || lower.startsWith("modify")) {
            return "PUT";
        }

        // DELETE methods
        if (lower.startsWith("delete") || lower.startsWith("remove")) {
            return "DELETE";
        }

        return "UNKNOWN";
    }

    /**
     * Converts a CamelCase string to kebab-case.
     *
     * <p>Example: {@code EnvironmentService} → {@code environment-service}
     *
     * @param camelCase the CamelCase input
     * @return the kebab-case output
     */
    static String camelToKebab(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return "";
        }
        // Insert hyphen before each uppercase letter (except the first), then lowercase
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /**
     * Extracts HTTP status code from a successful result.
     * Checks for {@link ResponseEntity} and defaults to 200 on success.
     *
     * @param result the method return value
     * @return the HTTP status code
     */
    private int extractStatusCode(Object result) {
        if (result instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }
        // Default to 200 for successful calls
        return 200;
    }

    /**
     * Extracts HTTP status code from an exception.
     * Handles {@code WebClientResponseException} via reflection to avoid hard dependency
     * on spring-webflux, and also handles {@code HttpStatusCodeException} from spring-web.
     *
     * @param throwable the exception thrown
     * @return the HTTP status code, or 0 if not extractable
     */
    private int extractStatusCodeFromException(Throwable throwable) {
        // Check for WebClientResponseException via reflection (avoids webflux compile dependency)
        try {
            Class<?> webClientExClass = Class.forName(
                    "org.springframework.web.reactive.function.client.WebClientResponseException");
            if (webClientExClass.isInstance(throwable)) {
                var method = webClientExClass.getMethod("getStatusCode");
                Object statusCode = method.invoke(throwable);
                if (statusCode != null) {
                    var valueMethod = statusCode.getClass().getMethod("value");
                    return (int) valueMethod.invoke(statusCode);
                }
            }
        } catch (Exception ignored) {
            // WebClientResponseException not on classpath or reflection failed — skip
        }

        // Check for HttpStatusCodeException (spring-web, available on classpath)
        if (throwable instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
            return httpEx.getStatusCode().value();
        }

        return 0;
    }

    /**
     * Adds an MDC value as a span attribute if present and non-empty.
     */
    private void addMdcAttribute(Span span, String key) {
        String value = MDC.get(key);
        if (value != null && !value.isEmpty()) {
            span.setAttribute(key, value);
        }
    }
}
