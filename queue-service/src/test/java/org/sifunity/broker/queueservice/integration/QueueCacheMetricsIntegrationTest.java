// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.integration;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.queueservice.config.EmbeddedRedisTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying Micrometer cache metrics are registered for the
 * queue-service Caffeine cache.
 *
 * <p>Spring Boot's {@code CacheMetricsAutoConfiguration} automatically binds
 * Caffeine cache statistics to the {@link MeterRegistry} when
 * {@code recordStats()} is enabled. This test verifies that binding works
 * correctly for the {@code queue-metadata} cache managed by
 * {@code SimpleCacheManager}.</p>
 *
 * <p><strong>Validates: Requirements 6.1, 6.2, 6.3, 6.4</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
        "message.storage.type=filesystem",
        "message.storage.filesystem.path=./target/test-messages-cache-metrics"
})
class QueueCacheMetricsIntegrationTest {

    private static final String CACHE_NAME = "queue-metadata";

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private CacheManager cacheManager;

    /**
     * Verify the queue-metadata cache exists in the CacheManager.
     */
    @Test
    void queueMetadataCacheExistsInCacheManager() {
        assertThat(cacheManager.getCache(CACHE_NAME))
                .as("Cache '%s' should exist in CacheManager", CACHE_NAME)
                .isNotNull();
    }

    /**
     * Verify {@code cache.gets} metric is registered for queue-metadata cache
     * with {@code result=hit} and {@code result=miss} tags.
     *
     * <p><strong>Validates: Requirements 6.1, 6.4</strong></p>
     */
    @Test
    void cacheGetsMetricExistsWithHitAndMissTags() {
        var cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();

        // Trigger a miss
        cache.get("metrics-test-key-miss");

        // Trigger a hit
        cache.put("metrics-test-key-hit", "value");
        cache.get("metrics-test-key-hit");

        // Verify cache.gets with result=miss tag
        var missMeter = meterRegistry.find("cache.gets")
                .tag("cache", CACHE_NAME)
                .tag("result", "miss")
                .functionCounter();
        assertThat(missMeter)
                .as("cache.gets metric with result=miss should exist for cache '%s'", CACHE_NAME)
                .isNotNull();

        // Verify cache.gets with result=hit tag
        var hitMeter = meterRegistry.find("cache.gets")
                .tag("cache", CACHE_NAME)
                .tag("result", "hit")
                .functionCounter();
        assertThat(hitMeter)
                .as("cache.gets metric with result=hit should exist for cache '%s'", CACHE_NAME)
                .isNotNull();

        // Clean up
        cache.evict("metrics-test-key-miss");
        cache.evict("metrics-test-key-hit");
    }

    /**
     * Verify {@code cache.evictions} metric is registered for queue-metadata cache.
     *
     * <p><strong>Validates: Requirements 6.1, 6.4</strong></p>
     */
    @Test
    void cacheEvictionsMetricExists() {
        var evictionMeter = meterRegistry.find("cache.evictions")
                .tag("cache", CACHE_NAME)
                .functionCounter();
        assertThat(evictionMeter)
                .as("cache.evictions metric should exist for cache '%s'", CACHE_NAME)
                .isNotNull();
    }

    /**
     * Verify {@code cache.size} metric is registered for queue-metadata cache.
     *
     * <p><strong>Validates: Requirements 6.1, 6.4</strong></p>
     */
    @Test
    void cacheSizeMetricExists() {
        var sizeMeter = meterRegistry.find("cache.size")
                .tag("cache", CACHE_NAME)
                .gauge();
        assertThat(sizeMeter)
                .as("cache.size metric should exist for cache '%s'", CACHE_NAME)
                .isNotNull();
    }

    /**
     * Verify metrics are tagged with the cache name so operators can
     * monitor each cache independently.
     *
     * <p><strong>Validates: Requirements 6.4</strong></p>
     */
    @Test
    void metricsAreTaggedWithCacheName() {
        // Trigger at least one operation so meters are populated
        var cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        cache.get("tag-test-key");

        // Verify the cache tag value matches the expected cache name
        var meters = meterRegistry.find("cache.gets")
                .tag("cache", CACHE_NAME)
                .meters();
        assertThat(meters)
                .as("Meters for cache '%s' should have cache name tag", CACHE_NAME)
                .isNotEmpty();

        // Clean up
        cache.evict("tag-test-key");
    }
}
