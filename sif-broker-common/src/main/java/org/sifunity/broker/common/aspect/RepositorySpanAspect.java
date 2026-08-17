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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AOP aspect that creates OpenTelemetry child spans around JPA repository method calls.
 *
 * <p>Intercepts all methods on beans annotated with {@code @Repository} or implementing
 * {@link JpaRepository}, creating spans named {@code db.jpa.{EntityType}.{methodName}}
 * with operation type inference, entity type extraction, and result metadata.
 *
 * <p>This aspect follows the same conditional loading pattern as {@code TracingUtils}:
 * it is only registered when {@code observability.enabled=true}. When disabled, no AOP
 * proxying occurs on repository beans.
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
public class RepositorySpanAspect {

    private final Tracer tracer;
    private final Map<Class<?>, String> entityTypeCache = new ConcurrentHashMap<>();

    public RepositorySpanAspect(Tracer tracer) {
        this.tracer = tracer;
        log.info(">>>>>> RepositorySpanAspect initialized — JPA repository calls will produce trace spans (pointcut: bean(*Repository))");
    }

    /**
     * Pointcut matching repository method calls in our application packages.
     *
     * <p>Combines {@code bean(*Repository)} to match Spring Data repository beans
     * with an {@code execution()} filter to only intercept methods declared in our
     * packages. This prevents matching Spring infrastructure beans like
     * {@code JpaRepositoryFactoryBean}. Excludes redis packages (handled by
     * {@code RedisSpanAspect}).</p>
     */
    @Pointcut("bean(*Repository) && execution(* org.sifunity.broker..*.*(..)) && " +
              "!execution(* org.sifunity.broker..redis..*(..))")
    public void repositoryMethods() {}

    /**
     * Around advice that wraps repository method calls with an OpenTelemetry span.
     *
     * @param joinPoint the intercepted method call
     * @return the result of the original method
     * @throws Throwable if the original method throws
     */
    @Around("repositoryMethods()")
    public Object traceRepositoryCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String entityType = extractEntityType(joinPoint.getTarget().getClass());
        String spanName = buildSpanName(entityType, methodName);
        log.debug(">>>>>> Intercepted repository call: {} on {}", spanName, joinPoint.getTarget().getClass().getSimpleName());

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
            span.setAttribute("db.system", "postgresql");
            span.setAttribute("db.operation", inferOperationType(methodName));
            span.setAttribute("db.entity_type", entityType);

            // MDC correlation attributes
            addMdcAttribute(span, "correlation_id");
            addMdcAttribute(span, "sif_service_name");
            addMdcAttribute(span, "zone");

            Object result = joinPoint.proceed();

            span.setStatus(StatusCode.OK);
            addResultMetadata(span, result);

            return result;
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * Infers the database operation type from the repository method name.
     *
     * @param methodName the repository method name
     * @return one of SELECT, DELETE, INSERT, UPDATE, or UNKNOWN
     */
    public static String inferOperationType(String methodName) {
        String lower = methodName.toLowerCase();
        if (lower.startsWith("find") || lower.startsWith("get") || lower.startsWith("exists")
                || lower.startsWith("count") || lower.startsWith("query") || lower.startsWith("read")
                || lower.startsWith("search") || lower.startsWith("lookup")) {
            return "SELECT";
        }
        if (lower.startsWith("delete") || lower.startsWith("remove")) {
            return "DELETE";
        }
        if (lower.startsWith("save") || lower.startsWith("insert") || lower.startsWith("create")
                || lower.startsWith("persist")) {
            return "INSERT";
        }
        if (lower.startsWith("update") || lower.startsWith("merge") || lower.startsWith("modify")) {
            return "UPDATE";
        }
        return "UNKNOWN";
    }

    /**
     * Builds the span name following the convention {@code db.jpa.{entityType}.{methodName}}.
     *
     * @param entityType the entity type simple name
     * @param methodName the repository method name
     * @return the formatted span name
     */
    public static String buildSpanName(String entityType, String methodName) {
        return "db.jpa." + entityType + "." + methodName;
    }

    /**
     * Extracts the JPA entity type from the repository's generic type parameter.
     * Results are cached per repository class for performance.
     *
     * @param repositoryClass the runtime class of the repository bean
     * @return the entity type simple name, or "Unknown" if extraction fails
     */
    String extractEntityType(Class<?> repositoryClass) {
        return entityTypeCache.computeIfAbsent(repositoryClass, this::resolveEntityType);
    }

    private String resolveEntityType(Class<?> repositoryClass) {
        try {
            // Walk all interfaces looking for JpaRepository<EntityType, ID>
            for (Type iface : repositoryClass.getGenericInterfaces()) {
                String result = extractFromType(iface);
                if (result != null) return result;
            }
            // Also check interfaces of interfaces (proxy classes)
            for (Class<?> iface : repositoryClass.getInterfaces()) {
                for (Type superIface : iface.getGenericInterfaces()) {
                    String result = extractFromType(superIface);
                    if (result != null) return result;
                }
            }
        } catch (Throwable e) {
            log.debug("Failed to extract entity type from {}: {}", repositoryClass.getName(), e.getMessage());
        }
        return "Unknown";
    }

    private String extractFromType(Type type) {
        if (type instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType instanceof Class<?> rawClass && JpaRepository.class.isAssignableFrom(rawClass)) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> entityClass) {
                    return entityClass.getSimpleName();
                }
            }
        }
        return null;
    }

    /**
     * Adds result metadata to the span based on the return type.
     * <ul>
     *   <li>{@link Collection}: sets {@code db.result_count}</li>
     *   <li>{@link Page}/{@link Slice}: sets {@code db.result_count} via {@code getNumberOfElements()}</li>
     *   <li>{@link Optional}: sets {@code db.result_found}</li>
     * </ul>
     *
     * <p>Public static for property-based testing.</p>
     */
    public static void addResultMetadata(Span span, Object result) {
        if (result == null) return;

        try {
            if (result instanceof Page<?> page) {
                span.setAttribute("db.result_count", (long) page.getNumberOfElements());
            } else if (result instanceof Slice<?> slice) {
                span.setAttribute("db.result_count", (long) slice.getNumberOfElements());
            } else if (result instanceof Collection<?> collection) {
                span.setAttribute("db.result_count", (long) collection.size());
            } else if (result instanceof Optional<?> optional) {
                span.setAttribute("db.result_found", optional.isPresent());
            }
        } catch (Exception e) {
            log.debug("Failed to add result metadata: {}", e.getMessage());
        }
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
