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
import org.springframework.stereotype.Component;

/**
 * AOP aspect that creates OpenTelemetry child spans around Redis operations.
 *
 * <p>Intercepts method calls on {@code StringRedisTemplate}, {@code ReactiveRedisTemplate},
 * and {@code StreamMessageRepository}, creating spans named {@code redis.{operationType}}
 * with Redis command inference, key sanitization, and MDC correlation attributes.
 *
 * <p>This aspect follows the same conditional loading pattern as {@code RepositorySpanAspect}:
 * it is only registered when {@code observability.enabled=true}. When disabled, no AOP
 * proxying occurs on Redis beans.
 *
 * <p>Safety: raw key values, message payloads, and session tokens are never logged or
 * set as span attributes. Only the sanitized {@code redis.key_pattern} is recorded.
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
public class RedisSpanAspect {

    private final Tracer tracer;

    public RedisSpanAspect(Tracer tracer) {
        this.tracer = tracer;
        log.info(">>>>>> RedisSpanAspect initialized — Redis operations will produce trace spans");
    }

    /**
     * Pointcut matching methods on the StreamMessageRepository bean and any
     * Redis-related beans in redis packages.
     *
     * <p>Uses {@code bean()} pointcut for StreamMessageRepository (reliable with
     * Spring proxies) combined with package-based execution pattern for other
     * Redis classes.</p>
     */
    @Pointcut("bean(streamMessageRepository) || execution(* org.sifunity.broker..redis..*(..))")
    public void redisOperations() {}

    /**
     * Around advice that wraps Redis operations with an OpenTelemetry span.
     *
     * @param joinPoint the intercepted method call
     * @return the result of the original method
     * @throws Throwable if the original method throws
     */
    @Around("redisOperations()")
    public Object traceRedisCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String redisCommand = inferRedisCommand(methodName);
        String spanName = "redis." + redisCommand;

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
            span.setAttribute("db.system", "redis");
            span.setAttribute("redis.operation", redisCommand);

            // Extract and sanitize Redis key from arguments
            String keyPattern = sanitizeRedisKey(extractRedisKey(joinPoint.getArgs()));
            span.setAttribute("redis.key_pattern", keyPattern);

            // MDC correlation attributes
            addMdcAttribute(span, "correlation_id");
            addMdcAttribute(span, "sif_service_name");
            addMdcAttribute(span, "zone");

            Object result = joinPoint.proceed();

            span.setStatus(StatusCode.OK);
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
     * Infers the Redis command from the method name.
     *
     * <p>Mapping covers StreamMessageRepository business methods and
     * common StringRedisTemplate / ReactiveRedisTemplate operations.
     *
     * @param methodName the intercepted method name
     * @return the Redis command string (e.g. XADD, GET, HSET)
     */
    public static String inferRedisCommand(String methodName) {
        if (methodName == null) {
            return "UNKNOWN";
        }

        // StreamMessageRepository methods
        return switch (methodName) {
            case "enqueue" -> "XADD";
            case "readNext", "readPending" -> "XREADGROUP";
            case "acknowledgeAndDelete" -> "XACK";
            case "deleteStream" -> "DEL";
            case "messageCount" -> "XLEN";
            case "claimExpiredMessages" -> "XCLAIM";
            case "moveToDeadLetter" -> "XADD";

            // ValueOperations — StringRedisTemplate.opsForValue()
            case "get" -> "GET";
            case "set" -> "SET";

            // ZSetOperations — StringRedisTemplate.opsForZSet()
            case "add" -> "ZADD";
            case "removeRangeByScore" -> "ZREMRANGEBYSCORE";
            case "count" -> "ZRANGEBYSCORE";

            // HashOperations — StringRedisTemplate.opsForHash()
            case "put" -> "HSET";
            case "entries" -> "HGETALL";

            // Common template operations
            case "delete" -> "DEL";

            // Default: uppercase method name
            default -> methodName.toUpperCase();
        };
    }

    /**
     * Sanitizes a Redis key by replacing dynamic segments with {@code *}.
     *
     * <ul>
     *   <li>UUID patterns (8-4-4-4-12 hex) are replaced with {@code *}</li>
     *   <li>Pure numeric segments between colons are replaced with {@code *}</li>
     *   <li>Null keys return {@code "unknown"}</li>
     * </ul>
     *
     * <p>Examples:
     * <pre>
     *   "function:msg_queue:domain:abc12345-1234-5678-9abc-def012345678:solution:xyz"
     *     → "function:msg_queue:domain:*:solution:xyz"
     *   "rate_limit:12345" → "rate_limit:*"
     *   null → "unknown"
     * </pre>
     *
     * @param key the raw Redis key
     * @return the sanitized key pattern
     */
    public static String sanitizeRedisKey(String key) {
        if (key == null) {
            return "unknown";
        }
        // Replace UUID patterns (8-4-4-4-12 hex)
        String sanitized = key.replaceAll(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "*");
        // Replace pure numeric segments between colons
        sanitized = sanitized.replaceAll("(?<=:)\\d+(?=:|$)", "*");
        return sanitized;
    }

    /**
     * Extracts a Redis key from method arguments.
     *
     * <p>Looks for the first String argument that looks like a Redis key
     * (contains {@code :} separator or is a simple key name). If no key
     * can be extracted, returns {@code null} (which sanitizeRedisKey maps to "unknown").
     *
     * @param args the method arguments
     * @return the extracted key, or null if none found
     */
    static String extractRedisKey(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof String str && !str.isEmpty()) {
                // A Redis key typically contains ':' or is a simple identifier
                if (str.contains(":") || !str.contains(" ")) {
                    return str;
                }
            }
        }
        return null;
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
