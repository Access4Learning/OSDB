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
import org.sifunity.broker.common.aspect.RedisSpanAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RedisSpanAspect}.
 *
 * <p>Tests Redis command inference for all method patterns, key sanitization
 * with known project key patterns, raw key value safety, and conditional loading.</p>
 *
 * <p>Validates: Requirements 2.2, 2.3, 2.4, 2.5, 2.7, 6.1, 6.2</p>
 */
class RedisSpanAspectTest {

    // ========== Mock infrastructure ==========

    private Tracer tracer;
    private Span span;
    private SpanBuilder spanBuilder;
    private RedisSpanAspect aspect;

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

        aspect = new RedisSpanAspect(tracer);
    }

    /**
     * Builds a mock ProceedingJoinPoint with given method name and arguments.
     */
    private ProceedingJoinPoint buildJoinPoint(String methodName, Object[] args, Object result)
            throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(result);

        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args != null ? args : new Object[]{});

        return joinPoint;
    }

    // ========== Redis Command Inference ==========

    @Nested
    @DisplayName("Redis command inference for all method patterns")
    class RedisCommandInference {

        // --- StreamMessageRepository methods ---

        @Test
        @DisplayName("enqueue → XADD")
        void enqueueMapsToXadd() {
            assertThat(RedisSpanAspect.inferRedisCommand("enqueue")).isEqualTo("XADD");
        }

        @Test
        @DisplayName("readNext → XREADGROUP")
        void readNextMapsToXreadgroup() {
            assertThat(RedisSpanAspect.inferRedisCommand("readNext")).isEqualTo("XREADGROUP");
        }

        @Test
        @DisplayName("readPending → XREADGROUP")
        void readPendingMapsToXreadgroup() {
            assertThat(RedisSpanAspect.inferRedisCommand("readPending")).isEqualTo("XREADGROUP");
        }

        @Test
        @DisplayName("acknowledgeAndDelete → XACK")
        void acknowledgeAndDeleteMapsToXack() {
            assertThat(RedisSpanAspect.inferRedisCommand("acknowledgeAndDelete")).isEqualTo("XACK");
        }

        @Test
        @DisplayName("deleteStream → DEL")
        void deleteStreamMapsToDel() {
            assertThat(RedisSpanAspect.inferRedisCommand("deleteStream")).isEqualTo("DEL");
        }

        @Test
        @DisplayName("messageCount → XLEN")
        void messageCountMapsToXlen() {
            assertThat(RedisSpanAspect.inferRedisCommand("messageCount")).isEqualTo("XLEN");
        }

        @Test
        @DisplayName("claimExpiredMessages → XCLAIM")
        void claimExpiredMessagesMapsToXclaim() {
            assertThat(RedisSpanAspect.inferRedisCommand("claimExpiredMessages")).isEqualTo("XCLAIM");
        }

        @Test
        @DisplayName("moveToDeadLetter → XADD")
        void moveToDeadLetterMapsToXadd() {
            assertThat(RedisSpanAspect.inferRedisCommand("moveToDeadLetter")).isEqualTo("XADD");
        }

        // --- ValueOperations methods ---

        @Test
        @DisplayName("get → GET")
        void getMapsToGet() {
            assertThat(RedisSpanAspect.inferRedisCommand("get")).isEqualTo("GET");
        }

        @Test
        @DisplayName("set → SET")
        void setMapsToSet() {
            assertThat(RedisSpanAspect.inferRedisCommand("set")).isEqualTo("SET");
        }

        // --- ZSetOperations methods ---

        @Test
        @DisplayName("add → ZADD")
        void addMapsToZadd() {
            assertThat(RedisSpanAspect.inferRedisCommand("add")).isEqualTo("ZADD");
        }

        @Test
        @DisplayName("removeRangeByScore → ZREMRANGEBYSCORE")
        void removeRangeByScoreMapsToZremrangebyscore() {
            assertThat(RedisSpanAspect.inferRedisCommand("removeRangeByScore")).isEqualTo("ZREMRANGEBYSCORE");
        }

        @Test
        @DisplayName("count → ZRANGEBYSCORE")
        void countMapsToZrangebyscore() {
            assertThat(RedisSpanAspect.inferRedisCommand("count")).isEqualTo("ZRANGEBYSCORE");
        }

        // --- HashOperations methods ---

        @Test
        @DisplayName("put → HSET")
        void putMapsToHset() {
            assertThat(RedisSpanAspect.inferRedisCommand("put")).isEqualTo("HSET");
        }

        @Test
        @DisplayName("entries → HGETALL")
        void entriesMapsToHgetall() {
            assertThat(RedisSpanAspect.inferRedisCommand("entries")).isEqualTo("HGETALL");
        }

        // --- Common template operations ---

        @Test
        @DisplayName("delete → DEL")
        void deleteMapsToDel() {
            assertThat(RedisSpanAspect.inferRedisCommand("delete")).isEqualTo("DEL");
        }

        // --- Default: uppercase method name ---

        @Test
        @DisplayName("Unknown method name uppercased as default")
        void unknownMethodUppercased() {
            assertThat(RedisSpanAspect.inferRedisCommand("someCustomMethod")).isEqualTo("SOMECUSTOMMETHOD");
        }

        @Test
        @DisplayName("null method name returns UNKNOWN")
        void nullMethodReturnsUnknown() {
            assertThat(RedisSpanAspect.inferRedisCommand(null)).isEqualTo("UNKNOWN");
        }
    }

    // ========== Key Sanitization ==========

    @Nested
    @DisplayName("Key sanitization with known project key patterns")
    class KeySanitization {

        @Test
        @DisplayName("UUID in key replaced with * — function:msg_queue pattern")
        void uuidReplacedInMsgQueueKey() {
            String raw = "function:msg_queue:domain:abc12345-1234-5678-9abc-def012345678:solution:xyz";
            String sanitized = RedisSpanAspect.sanitizeRedisKey(raw);

            assertThat(sanitized).isEqualTo("function:msg_queue:domain:*:solution:xyz");
        }

        @Test
        @DisplayName("Numeric segment replaced with * — rate_limit pattern")
        void numericSegmentReplacedInRateLimitKey() {
            String raw = "rate_limit:12345";
            String sanitized = RedisSpanAspect.sanitizeRedisKey(raw);

            assertThat(sanitized).isEqualTo("rate_limit:*");
        }

        @Test
        @DisplayName("No change for static-only key — provider pattern")
        void noChangeForStaticProviderKey() {
            String raw = "provider:DataExport:production:default";
            String sanitized = RedisSpanAspect.sanitizeRedisKey(raw);

            assertThat(sanitized).isEqualTo("provider:DataExport:production:default");
        }

        @Test
        @DisplayName("Multiple UUIDs replaced in complex key")
        void multipleUuidsReplaced() {
            String raw = "function:msg_queue:domain:abc12345-1234-5678-9abc-def012345678"
                    + ":solution:def67890-abcd-1234-5678-abcdef012345"
                    + ":environment:11111111-2222-3333-4444-555555555555"
                    + ":queue:aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
            String sanitized = RedisSpanAspect.sanitizeRedisKey(raw);

            assertThat(sanitized).isEqualTo(
                    "function:msg_queue:domain:*:solution:*:environment:*:queue:*");
        }

        @Test
        @DisplayName("Null key returns 'unknown'")
        void nullKeyReturnsUnknown() {
            assertThat(RedisSpanAspect.sanitizeRedisKey(null)).isEqualTo("unknown");
        }

        @Test
        @DisplayName("Mixed UUID and numeric segments both replaced")
        void mixedUuidAndNumericReplaced() {
            String raw = "rate_limit:abc12345-1234-5678-9abc-def012345678:counter:99999";
            String sanitized = RedisSpanAspect.sanitizeRedisKey(raw);

            assertThat(sanitized).isEqualTo("rate_limit:*:counter:*");
        }

        @Test
        @DisplayName("Numeric segment at end of key replaced")
        void numericAtEndReplaced() {
            String raw = "session:token:42";
            String sanitized = RedisSpanAspect.sanitizeRedisKey(raw);

            assertThat(sanitized).isEqualTo("session:token:*");
        }

        @Test
        @DisplayName("Alphabetic segments preserved between colons")
        void alphabeticSegmentsPreserved() {
            String raw = "cache:provider:lookup:result";
            String sanitized = RedisSpanAspect.sanitizeRedisKey(raw);

            assertThat(sanitized).isEqualTo("cache:provider:lookup:result");
        }
    }

    // ========== Raw Key Values Never in Span Attributes ==========

    @Nested
    @DisplayName("Raw key values never appear in span attributes")
    class RawKeyValueSafety {

        @Test
        @DisplayName("Span attribute redis.key_pattern contains sanitized key, not raw UUID key")
        void spanAttributeContainsSanitizedKey() throws Throwable {
            String rawKey = "function:msg_queue:domain:abc12345-1234-5678-9abc-def012345678:solution:xyz";
            ProceedingJoinPoint jp = buildJoinPoint("enqueue", new Object[]{rawKey}, null);

            aspect.traceRedisCall(jp);

            // Verify sanitized key is set
            verify(span).setAttribute("redis.key_pattern",
                    "function:msg_queue:domain:*:solution:xyz");
            // Verify raw key never set as any attribute value
            verify(span, never()).setAttribute(anyString(), eq(rawKey));
        }

        @Test
        @DisplayName("Span attribute redis.key_pattern contains sanitized key for rate_limit pattern")
        void spanAttributeSanitizedForRateLimitKey() throws Throwable {
            String rawKey = "rate_limit:12345";
            ProceedingJoinPoint jp = buildJoinPoint("get", new Object[]{rawKey}, "someValue");

            aspect.traceRedisCall(jp);

            verify(span).setAttribute("redis.key_pattern", "rate_limit:*");
            verify(span, never()).setAttribute(anyString(), eq(rawKey));
        }

        @Test
        @DisplayName("Static key passes through unchanged (no dynamic segments)")
        void staticKeyPassesThroughUnchanged() throws Throwable {
            String rawKey = "provider:DataExport:production:default";
            ProceedingJoinPoint jp = buildJoinPoint("get", new Object[]{rawKey}, "cached");

            aspect.traceRedisCall(jp);

            // Static key has no UUIDs or numerics — passes through as-is
            verify(span).setAttribute("redis.key_pattern",
                    "provider:DataExport:production:default");
        }

        @Test
        @DisplayName("No key argument results in 'unknown' key pattern")
        void noKeyArgumentResultsInUnknown() throws Throwable {
            ProceedingJoinPoint jp = buildJoinPoint("delete", new Object[]{}, null);

            aspect.traceRedisCall(jp);

            verify(span).setAttribute("redis.key_pattern", "unknown");
        }
    }

    // ========== Span Attributes for Known Scenarios ==========

    @Nested
    @DisplayName("Span attribute values for known Redis scenarios")
    class SpanAttributes {

        @Test
        @DisplayName("enqueue: db.system=redis, redis.operation=XADD, span name=redis.XADD")
        void enqueueSpanAttributes() throws Throwable {
            String key = "function:msg_queue:domain:abc12345-1234-5678-9abc-def012345678:solution:xyz";
            ProceedingJoinPoint jp = buildJoinPoint("enqueue", new Object[]{key}, null);

            aspect.traceRedisCall(jp);

            verify(tracer).spanBuilder("redis.XADD");
            verify(span).setAttribute("db.system", "redis");
            verify(span).setAttribute("redis.operation", "XADD");
            verify(span).setStatus(StatusCode.OK);
            verify(span).end();
        }

        @Test
        @DisplayName("get: db.system=redis, redis.operation=GET, span name=redis.GET")
        void getSpanAttributes() throws Throwable {
            ProceedingJoinPoint jp = buildJoinPoint("get",
                    new Object[]{"rate_limit:12345"}, "value");

            aspect.traceRedisCall(jp);

            verify(tracer).spanBuilder("redis.GET");
            verify(span).setAttribute("db.system", "redis");
            verify(span).setAttribute("redis.operation", "GET");
            verify(span).setStatus(StatusCode.OK);
        }

        @Test
        @DisplayName("Exception sets span status ERROR and re-throws")
        void exceptionSetsErrorStatus() throws Throwable {
            ProceedingJoinPoint jp = buildJoinPoint("readNext",
                    new Object[]{"stream:key"}, null);
            RuntimeException ex = new RuntimeException("Redis connection failed");
            when(jp.proceed()).thenThrow(ex);

            assertThatThrownBy(() -> aspect.traceRedisCall(jp))
                    .isSameAs(ex);

            verify(span).recordException(ex);
            verify(span).setStatus(StatusCode.ERROR, "Redis connection failed");
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
                    RedisSpanAspect.class.getAnnotation(ConditionalOnProperty.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.name()).containsExactly("observability.enabled");
            assertThat(annotation.havingValue()).isEqualTo("true");
        }
    }
}
