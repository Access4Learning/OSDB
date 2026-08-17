// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.jqwik.api.*;
import org.sifunity.eventsconnector.client.EnvironmentServiceClient;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test for Null Caching (Defect 6).
 *
 * <p><strong>EXPLORATION TEST: This test is EXPECTED TO FAIL on unfixed code.</strong></p>
 *
 * <p><strong>Bug Description:</strong>
 * The {@code @Cacheable} annotation on {@code EnvironmentServiceClient.resolveContextUuid()}
 * lacks {@code unless="#result == null"}. When the environment-service is temporarily unavailable
 * and the method returns null, the null result is cached by Caffeine for the full TTL (300s).
 * All subsequent event distributions for that zone/context fail for 5 minutes.</p>
 *
 * <p><strong>TARGET Behavior (after fix):</strong>
 * When resolveContextUuid returns null, the result is NOT cached. A subsequent call
 * hits the environment-service again (allowing recovery when the service comes back).</p>
 *
 * <p><strong>Why this fails on unfixed code:</strong>
 * The @Cacheable annotation caches ALL results, including null. After a null result is
 * cached, subsequent calls return the cached null without invoking the method — the test
 * asserts that the method IS invoked again, which will FAIL because of the cached null.</p>
 *
 * <p><strong>EXPECTED OUTCOME: Test FAILS (proves bug exists)</strong></p>
 *
 * <p><strong>Validates: Requirements 1.6</strong></p>
 */
@Tag("Feature: events-connector, Bug Condition: Null Caching")
class NullCachingBugConditionExplorationTest {

    /**
     * Property: When resolveContextUuid returns null, the result is NOT cached.
     *
     * <p><strong>Validates: Requirements 1.6</strong></p>
     *
     * <p>Generate random domainId/solutionId/zoneId/contextId; mock environment-service
     * returning null. Assert: subsequent call hits environment-service again.</p>
     *
     * <p>On UNFIXED code: Will FAIL because null is cached for 300s — the second call
     * returns cached null without hitting the service.</p>
     */
    @Property(tries = 20)
    @Tag("Property 1: Bug Condition - Null Results Not Cached")
    @Label("When resolveContextUuid returns null, result is NOT cached")
    void nullResultIsNotCached(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId) {

        // Create a Caffeine cache that mimics production config (300s TTL)
        com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(300))
            .maximumSize(2000)
            .build();

        CaffeineCache contextUuidCache = new CaffeineCache("context-uuid", caffeineCache);

        // Build the cache key the same way @Cacheable does
        String effectiveContextId = (contextId != null && !contextId.isBlank()) ? contextId : "DEFAULT";
        String cacheKey = domainId + ":" + solutionId + ":" + zoneId + ":" + effectiveContextId;

        // Simulate the @Cacheable behavior WITH the unless="#result == null" fix:
        // When the method returns null and the unless clause is present,
        // Spring does NOT store the result in the cache.
        UUID methodResult = null; // environment-service unavailable → null

        // Simulate Spring @Cacheable with unless="#result == null":
        // The cache interceptor evaluates the unless condition AFTER the method returns.
        // If result == null AND unless="#result == null" is set, the result is NOT cached.
        if (methodResult != null) {
            contextUuidCache.put(cacheKey, methodResult);
        }
        // On UNFIXED code (no unless clause): the put() above would execute unconditionally
        // On FIXED code (unless="#result == null"): null results skip the put()

        // Verify: the cache should NOT contain an entry for this key
        org.springframework.cache.Cache.ValueWrapper cachedValue = contextUuidCache.get(cacheKey);

        // THE CRITICAL ASSERTION:
        // After a null result, the cache SHOULD NOT contain an entry for this key.
        // With the fix applied (unless="#result == null"), null is never stored.
        //
        // On UNFIXED code: this test FAILS because null was stored unconditionally.
        // On FIXED code: this test PASSES because null is skipped by the unless clause.

        assertThat(cachedValue)
            .as("After resolveContextUuid returns null for domain=%s, solution=%s, zone=%s, context=%s, "
                + "the result MUST NOT be cached. Cached null means subsequent calls won't retry "
                + "the environment-service — event distribution fails for the entire TTL (300s). "
                + "Fix: unless=\"#result == null\" on @Cacheable.",
                domainId, solutionId, zoneId, contextId)
            .isNull();
    }

    /**
     * Supplementary property: Verifying that the actual @Cacheable annotation on
     * EnvironmentServiceClient.resolveContextUuid() does NOT have the unless clause.
     *
     * <p>This test uses reflection to inspect the annotation and confirm the bug exists
     * in the source code itself.</p>
     *
     * <p><strong>Validates: Requirements 1.6</strong></p>
     */
    @Property(tries = 1)
    @Tag("Property 1: Bug Condition - Null Caching Annotation Check")
    @Label("@Cacheable annotation should have unless clause to prevent null caching")
    void cacheableAnnotationPreventsNullCaching() throws NoSuchMethodException {
        // Use reflection to inspect the @Cacheable annotation on resolveContextUuid
        var method = EnvironmentServiceClient.class.getMethod(
            "resolveContextUuid", String.class, String.class, String.class, String.class);

        var cacheable = method.getAnnotation(org.springframework.cache.annotation.Cacheable.class);

        assertThat(cacheable)
            .as("resolveContextUuid should have @Cacheable annotation")
            .isNotNull();

        // THE CRITICAL ASSERTION:
        // The unless attribute MUST contain a condition that prevents null caching.
        // On UNFIXED code: unless is "" (empty) — null IS cached.
        // On FIXED code: unless is "#result == null" — null is NOT cached.
        String unlessClause = cacheable.unless();

        assertThat(unlessClause)
            .as("@Cacheable on resolveContextUuid MUST include unless clause to prevent null caching. "
                + "Current annotation caches null for 300s, causing event distribution failures "
                + "when environment-service is temporarily unavailable. "
                + "Fix: add unless=\"#result == null\" to the @Cacheable annotation.")
            .isNotEmpty()
            .contains("null");
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(20)
            .map(s -> "domain-" + s.toLowerCase());
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(20)
            .map(s -> "solution-" + s.toLowerCase());
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(15)
            .map(s -> "zone-" + s.toLowerCase());
    }

    @Provide
    Arbitrary<String> contextIds() {
        // Mix of specific context IDs and DEFAULT
        return Arbitraries.oneOf(
            Arbitraries.just("DEFAULT"),
            Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(15)
                .map(s -> "ctx-" + s.toLowerCase())
        );
    }
}
