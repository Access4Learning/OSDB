// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying Micrometer cache metrics are registered for all
 * environment-service Caffeine caches.
 *
 * <p>Spring Boot's {@code CacheMetricsAutoConfiguration} automatically binds
 * Caffeine cache statistics to the {@link MeterRegistry} when
 * {@code recordStats()} is enabled. This test verifies that binding works
 * correctly for every named cache managed by {@code SimpleCacheManager}.</p>
 *
 * <p><strong>Validates: Requirements 6.1, 6.2, 6.3, 6.4</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CacheMetricsIntegrationTest {

    private static final List<String> EXPECTED_CACHES = List.of(
            "auth-environment",
            "auth-shared-secret",
            "auth-solution",
            "auth-domain",
            "acl-permissions",
            "acl-any-grant"
    );

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private CacheManager cacheManager;

    /**
     * Verify all expected named caches exist in the CacheManager.
     */
    @Test
    void allNamedCachesExistInCacheManager() {
        for (String cacheName : EXPECTED_CACHES) {
            assertThat(cacheManager.getCache(cacheName))
                    .as("Cache '%s' should exist in CacheManager", cacheName)
                    .isNotNull();
        }
    }

    /**
     * Verify {@code cache.gets} metric is registered for each named cache
     * after triggering a cache miss (get on empty cache) and a cache hit
     * (get after put).
     *
     * <p><strong>Validates: Requirements 6.1, 6.4</strong></p>
     */
    @Test
    void cacheGetsMetricExistsWithHitAndMissTags() {
        for (String cacheName : EXPECTED_CACHES) {
            var cache = cacheManager.getCache(cacheName);
            assertThat(cache).isNotNull();

            // Trigger a miss
            cache.get("metrics-test-key-miss");

            // Trigger a hit
            cache.put("metrics-test-key-hit", "value");
            cache.get("metrics-test-key-hit");

            // Verify cache.gets with result=miss tag
            var missMeter = meterRegistry.find("cache.gets")
                    .tag("cache", cacheName)
                    .tag("result", "miss")
                    .functionCounter();
            assertThat(missMeter)
                    .as("cache.gets metric with result=miss should exist for cache '%s'", cacheName)
                    .isNotNull();

            // Verify cache.gets with result=hit tag
            var hitMeter = meterRegistry.find("cache.gets")
                    .tag("cache", cacheName)
                    .tag("result", "hit")
                    .functionCounter();
            assertThat(hitMeter)
                    .as("cache.gets metric with result=hit should exist for cache '%s'", cacheName)
                    .isNotNull();

            // Clean up test entries
            cache.evict("metrics-test-key-miss");
            cache.evict("metrics-test-key-hit");
        }
    }

    /**
     * Verify {@code cache.evictions} metric is registered for each named cache.
     *
     * <p><strong>Validates: Requirements 6.1, 6.4</strong></p>
     */
    @Test
    void cacheEvictionsMetricExists() {
        for (String cacheName : EXPECTED_CACHES) {
            var evictionMeter = meterRegistry.find("cache.evictions")
                    .tag("cache", cacheName)
                    .functionCounter();
            assertThat(evictionMeter)
                    .as("cache.evictions metric should exist for cache '%s'", cacheName)
                    .isNotNull();
        }
    }

    /**
     * Verify {@code cache.size} metric is registered for each named cache.
     *
     * <p><strong>Validates: Requirements 6.1, 6.4</strong></p>
     */
    @Test
    void cacheSizeMetricExists() {
        for (String cacheName : EXPECTED_CACHES) {
            var sizeMeter = meterRegistry.find("cache.size")
                    .tag("cache", cacheName)
                    .gauge();
            assertThat(sizeMeter)
                    .as("cache.size metric should exist for cache '%s'", cacheName)
                    .isNotNull();
        }
    }

    /**
     * Verify metrics are tagged with the cache name so operators can
     * monitor each cache independently.
     *
     * <p><strong>Validates: Requirements 6.4</strong></p>
     */
    @Test
    void metricsAreTaggedWithCacheName() {
        for (String cacheName : EXPECTED_CACHES) {
            // Trigger at least one operation so meters are populated
            var cache = cacheManager.getCache(cacheName);
            assertThat(cache).isNotNull();
            cache.get("tag-test-key");

            // Verify the cache tag value matches the expected cache name
            var meters = meterRegistry.find("cache.gets")
                    .tag("cache", cacheName)
                    .meters();
            assertThat(meters)
                    .as("Meters for cache '%s' should have cache name tag", cacheName)
                    .isNotEmpty();

            // Clean up
            cache.evict("tag-test-key");
        }
    }
}
