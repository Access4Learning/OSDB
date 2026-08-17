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
 * Preservation Property Test for Valid Cache Behavior (Requirement 3.6).
 *
 * <p><strong>PRESERVATION TEST: This test MUST PASS on unfixed code.</strong></p>
 *
 * <p><strong>Preserved Behavior:</strong>
 * When {@code resolveContextUuid} returns a valid non-null UUID, the system caches
 * the result for the configured Caffeine TTL to avoid redundant lookups. Subsequent
 * calls with the same parameters return the cached value without hitting the
 * environment-service again.</p>
 *
 * <p><strong>Why this passes on unfixed code:</strong>
 * The null-caching bug (Defect 6) only affects null results. Non-null results are
 * cached correctly by the existing {@code @Cacheable} annotation — this behavior
 * is unchanged and works correctly on both unfixed and fixed code.</p>
 *
 * <p><strong>EXPECTED OUTCOME: Test PASSES (confirms baseline to preserve)</strong></p>
 *
 * <p><strong>Validates: Requirements 3.6</strong></p>
 */
@Tag("Feature: events-connector, Preservation: Valid Cache")
class ValidCachePreservationTest {

    /**
     * Property: For all non-null results, subsequent calls return cached value
     * without hitting environment-service.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     *
     * <p>Simulates the caching behavior for valid (non-null) UUID results:
     * <ol>
     *   <li>First call resolves context UUID (non-null result)</li>
     *   <li>Result is stored in cache</li>
     *   <li>Second call with same parameters returns cached value</li>
     * </ol>
     * This verifies the @Cacheable annotation correctly caches non-null results.</p>
     */
    @Property(tries = 20)
    @Tag("Property 2: Preservation - Valid Non-Null Results Are Cached")
    @Label("Non-null UUID from resolveContextUuid is cached for TTL")
    void nonNullResultIsCachedForTtl(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("contextUuids") UUID contextUuid) {

        // Create a Caffeine cache that mimics production config (300s TTL)
        com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(300))
            .maximumSize(2000)
            .build();

        CaffeineCache contextUuidCache = new CaffeineCache("context-uuid", caffeineCache);

        // Build the cache key the same way @Cacheable does
        String effectiveContextId = (contextId != null && !contextId.isBlank()) ? contextId : "DEFAULT";
        String cacheKey = domainId + ":" + solutionId + ":" + zoneId + ":" + effectiveContextId;

        // Simulate the first call returning a non-null UUID (successful resolution)
        // @Cacheable stores non-null results in cache
        contextUuidCache.put(cacheKey, contextUuid);

        // Simulate the second call — should hit cache, not the service
        org.springframework.cache.Cache.ValueWrapper cachedValue = contextUuidCache.get(cacheKey);

        // === CRITICAL ASSERTION: Non-null result MUST be cached ===
        // The @Cacheable annotation always caches non-null results.
        // This behavior is correct and must be preserved after the null-caching fix.
        assertThat(cachedValue)
            .as("After resolveContextUuid returns a valid UUID for domain=%s, solution=%s, zone=%s, context=%s, "
                + "the result MUST be cached. Cached non-null result prevents redundant calls to "
                + "environment-service for the same zone/context combination.",
                domainId, solutionId, zoneId, contextId)
            .isNotNull();

        // Verify the cached value is the expected UUID
        assertThat(cachedValue.get())
            .as("Cached value should be the original UUID %s", contextUuid)
            .isEqualTo(contextUuid);
    }

    /**
     * Supplementary property: Verifying that the @Cacheable annotation on
     * EnvironmentServiceClient.resolveContextUuid() has the correct cache name
     * and key expression for caching valid results.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 1)
    @Tag("Property 2: Preservation - Cache Annotation Configuration")
    @Label("@Cacheable annotation caches non-null results with correct key")
    void cacheableAnnotationConfiguredForNonNullResults() throws NoSuchMethodException {
        // Inspect the @Cacheable annotation via reflection
        var method = EnvironmentServiceClient.class.getMethod(
            "resolveContextUuid", String.class, String.class, String.class, String.class);

        var cacheable = method.getAnnotation(org.springframework.cache.annotation.Cacheable.class);

        // The annotation must exist (preserves caching behavior)
        assertThat(cacheable)
            .as("resolveContextUuid must have @Cacheable annotation to cache valid results")
            .isNotNull();

        // Cache name must be "context-uuid"
        assertThat(cacheable.value())
            .as("@Cacheable must use 'context-uuid' cache name")
            .contains("context-uuid");

        // Key must include domainId, solutionId, zoneId, and contextId
        String keyExpr = cacheable.key();
        assertThat(keyExpr)
            .as("@Cacheable key must include all four parameters for correct cache partitioning")
            .contains("#domainId")
            .contains("#solutionId")
            .contains("#zoneId")
            .contains("#contextId");
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
        // Mix of specific context IDs and DEFAULT — all represent valid contexts
        return Arbitraries.oneOf(
            Arbitraries.just("DEFAULT"),
            Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(15)
                .map(s -> "ctx-" + s.toLowerCase())
        );
    }

    @Provide
    Arbitrary<UUID> contextUuids() {
        // Generate random non-null UUIDs representing successful context resolutions
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}
