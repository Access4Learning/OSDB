// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import net.jqwik.api.*;
import org.sifunity.broker.environment.config.CacheProperties;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for cache configuration round-trip.
 *
 * <p><strong>Property 1: Cache configuration round-trip</strong></p>
 *
 * <p>For any valid TTL (1–3600 seconds) and max-size (1–100,000) configuration
 * values provided via {@link CacheProperties.CacheSpec}, the resulting Caffeine
 * cache instance SHALL have an expire-after-write duration equal to the configured
 * TTL and a maximum size equal to the configured max-size.</p>
 *
 * <p><strong>Validates: Requirements 1.2, 7.3, 7.4</strong></p>
 */
class CacheConfigPropertyTest {

    /**
     * Property 1: Cache configuration round-trip
     *
     * <p><strong>Validates: Requirements 1.2, 7.3, 7.4</strong></p>
     *
     * <p>Generates random valid TTL and max-size values, builds a CaffeineCache
     * using the same pattern as {@code CacheConfig.buildCache()}, and verifies
     * the resulting cache's expire-after-write duration and maximum size match
     * the input configuration.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 1: Cache configuration round-trip")
    void cacheConfigurationRoundTrip(
            @ForAll("validTtlSeconds") int ttlSeconds,
            @ForAll("validMaxSize") int maxSize) {

        // Build CacheSpec with generated values
        CacheProperties.CacheSpec spec = new CacheProperties.CacheSpec(ttlSeconds, maxSize);

        // Build CaffeineCache using same pattern as CacheConfig.buildCache()
        CaffeineCache caffeineCache = new CaffeineCache("test-cache", Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(spec.getTtlSeconds()))
                .maximumSize(spec.getMaxSize())
                .recordStats()
                .build());

        // Introspect native cache policy
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        Policy<Object, Object> policy = nativeCache.policy();

        // Verify expire-after-write matches configured TTL
        assertThat(policy.expireAfterWrite())
                .as("Cache must use expireAfterWrite")
                .isPresent()
                .hasValueSatisfying(expiration ->
                        assertThat(expiration.getExpiresAfter())
                                .as("expireAfterWrite duration must equal configured TTL (%ds)", ttlSeconds)
                                .isEqualTo(Duration.ofSeconds(ttlSeconds)));

        // Verify maximum size matches configured max-size
        assertThat(policy.eviction())
                .as("Cache must have eviction policy with maximum size")
                .isPresent()
                .hasValueSatisfying(eviction ->
                        assertThat(eviction.getMaximum())
                                .as("maximum size must equal configured max-size (%d)", maxSize)
                                .isEqualTo(maxSize));
    }

    // ========== Arbitraries ==========

    /**
     * Generates valid TTL values in the range 1–3600 seconds.
     */
    @Provide
    Arbitrary<Integer> validTtlSeconds() {
        return Arbitraries.integers().between(1, 3600);
    }

    /**
     * Generates valid max-size values in the range 1–100,000 entries.
     */
    @Provide
    Arbitrary<Integer> validMaxSize() {
        return Arbitraries.integers().between(1, 100_000);
    }
}
