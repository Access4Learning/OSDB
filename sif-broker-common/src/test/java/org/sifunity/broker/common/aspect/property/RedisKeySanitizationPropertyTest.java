// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.aspect.RedisSpanAspect;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Redis key sanitization replaces dynamic segments.
 *
 * <p>For any Redis key string containing UUID patterns (8-4-4-4-12 hex format)
 * or pure numeric segments between colons, {@code sanitizeRedisKey} SHALL replace
 * all UUID patterns and numeric segments with {@code *}, and the sanitized result
 * SHALL NOT contain any UUID or pure numeric segment. Static segments (alphabetic
 * words, known prefixes) SHALL be preserved unchanged.</p>
 *
 * <p><strong>Validates: Requirements 2.4, 2.5, 5.5</strong></p>
 */
class RedisKeySanitizationPropertyTest {

    /** Matches a full UUID pattern (8-4-4-4-12 hex). */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Matches a pure numeric segment between colons (or at end of string after colon). */
    private static final Pattern NUMERIC_SEGMENT_PATTERN = Pattern.compile("(?<=:)\\d+(?=:|$)");

    private static final List<String> STATIC_SEGMENTS =
            List.of("function", "msg_queue", "domain", "solution", "environment",
                    "queue", "rate_limit", "provider", "session", "cache", "lock");

    // ========== Arbitraries ==========

    /** Generates a random UUID string in 8-4-4-4-12 hex format. */
    @Provide
    Arbitrary<String> uuids() {
        return Arbitraries.strings().withCharRange('0', '9').withCharRange('a', 'f')
                .ofLength(32)
                .map(hex -> hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                        + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-"
                        + hex.substring(20, 32));
    }

    /** Generates a pure numeric string (1–10 digits). */
    @Provide
    Arbitrary<String> numericSegments() {
        return Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(10);
    }

    /** Generates a static segment name from known prefixes. */
    @Provide
    Arbitrary<String> staticSegments() {
        return Arbitraries.of(STATIC_SEGMENTS);
    }

    /**
     * Generates Redis keys with embedded UUIDs between static segments.
     * Pattern: static:uuid:static:uuid...
     */
    @Provide
    Arbitrary<String> keysWithUuids() {
        return Arbitraries.of(STATIC_SEGMENTS)
                .list().ofMinSize(2).ofMaxSize(5)
                .flatMap(segments -> uuids().list().ofSize(segments.size() - 1)
                        .map(ids -> {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < segments.size(); i++) {
                                if (i > 0) sb.append(":");
                                sb.append(segments.get(i));
                                if (i < ids.size()) {
                                    sb.append(":").append(ids.get(i));
                                }
                            }
                            return sb.toString();
                        }));
    }

    /**
     * Generates Redis keys with numeric segments between colons.
     * Pattern: static:123:static:456...
     */
    @Provide
    Arbitrary<String> keysWithNumericSegments() {
        return Arbitraries.of(STATIC_SEGMENTS)
                .list().ofMinSize(2).ofMaxSize(5)
                .flatMap(segments -> numericSegments().list().ofSize(segments.size() - 1)
                        .map(nums -> {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < segments.size(); i++) {
                                if (i > 0) sb.append(":");
                                sb.append(segments.get(i));
                                if (i < nums.size()) {
                                    sb.append(":").append(nums.get(i));
                                }
                            }
                            return sb.toString();
                        }));
    }

    /**
     * Generates Redis keys with only static (alphabetic) segments.
     * Pattern: static:static:static...
     */
    @Provide
    Arbitrary<String> pureStaticKeys() {
        return Arbitraries.of(STATIC_SEGMENTS)
                .list().ofMinSize(1).ofMaxSize(5)
                .map(segments -> String.join(":", segments));
    }

    // ========== Properties ==========

    /**
     * Keys with embedded UUIDs: all UUID patterns replaced with {@code *}.
     *
     * <p><strong>Validates: Requirements 2.4, 2.5, 5.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 2: Redis key sanitization replaces dynamic segments")
    @Property(tries = 20)
    void uuidSegmentsReplacedWithWildcard(@ForAll("keysWithUuids") String key) {
        String sanitized = RedisSpanAspect.sanitizeRedisKey(key);

        // No UUID pattern should remain in sanitized output
        assertThat(UUID_PATTERN.matcher(sanitized).find())
                .as("Sanitized key '%s' (from '%s') should not contain UUID patterns", sanitized, key)
                .isFalse();
    }

    /**
     * Keys with numeric segments between colons: all numeric segments replaced with {@code *}.
     *
     * <p><strong>Validates: Requirements 2.4, 2.5, 5.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 2: Redis key sanitization replaces dynamic segments")
    @Property(tries = 20)
    void numericSegmentsReplacedWithWildcard(@ForAll("keysWithNumericSegments") String key) {
        String sanitized = RedisSpanAspect.sanitizeRedisKey(key);

        // No pure numeric segment between colons should remain
        assertThat(NUMERIC_SEGMENT_PATTERN.matcher(sanitized).find())
                .as("Sanitized key '%s' (from '%s') should not contain numeric segments", sanitized, key)
                .isFalse();
    }

    /**
     * Keys with only static segments: output equals input (no replacement needed).
     *
     * <p><strong>Validates: Requirements 2.4, 2.5, 5.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 2: Redis key sanitization replaces dynamic segments")
    @Property(tries = 20)
    void staticSegmentsPreservedUnchanged(@ForAll("pureStaticKeys") String key) {
        String sanitized = RedisSpanAspect.sanitizeRedisKey(key);

        assertThat(sanitized)
                .as("Pure static key '%s' should be preserved unchanged", key)
                .isEqualTo(key);
    }

    /**
     * Null input returns "unknown".
     *
     * <p><strong>Validates: Requirements 2.4, 2.5, 5.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 2: Redis key sanitization replaces dynamic segments")
    @Property(tries = 1)
    void nullInputReturnsUnknown() {
        assertThat(RedisSpanAspect.sanitizeRedisKey(null)).isEqualTo("unknown");
    }

    /**
     * Static segments within keys containing UUIDs are preserved.
     *
     * <p><strong>Validates: Requirements 2.4, 2.5, 5.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 2: Redis key sanitization replaces dynamic segments")
    @Property(tries = 20)
    void staticSegmentsPreservedInMixedKeys(@ForAll("keysWithUuids") String key) {
        String sanitized = RedisSpanAspect.sanitizeRedisKey(key);

        // Every static segment from the original key should still appear in sanitized output
        for (String segment : STATIC_SEGMENTS) {
            if (key.contains(segment)) {
                assertThat(sanitized)
                        .as("Static segment '%s' should be preserved in sanitized key", segment)
                        .contains(segment);
            }
        }
    }
}
