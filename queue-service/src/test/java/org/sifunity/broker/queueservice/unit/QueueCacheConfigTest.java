// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.queueservice.config.CacheConfig;
import org.sifunity.broker.queueservice.config.CacheProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for queue-service {@link CacheConfig}.
 *
 * <p>Verifies named cache exists, default TTL/max-size values match requirements,
 * expire-after-write semantics, and recordStats() enabled.</p>
 *
 * <p>Validates: Requirements 1.3, 1.4, 1.5, 1.6, 3.8, 3.9</p>
 */
@DisplayName("QueueCacheConfig Unit Tests")
class QueueCacheConfigTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        CacheProperties props = new CacheProperties();
        CacheConfig config = new CacheConfig();
        cacheManager = config.cacheManager(props);
        // SimpleCacheManager requires afterPropertiesSet() to initialize
        ((org.springframework.cache.support.SimpleCacheManager) cacheManager).afterPropertiesSet();
    }

    // ========== Named Caches Exist (Req 1.3) ==========

    @Nested
    @DisplayName("Named caches exist")
    class NamedCachesExist {

        private static final List<String> EXPECTED_CACHES = List.of("queue-metadata");

        @Test
        @DisplayName("queue-metadata cache is present in CacheManager")
        void queueMetadataCacheExists() {
            assertThat(cacheManager.getCacheNames())
                    .containsExactlyInAnyOrderElementsOf(EXPECTED_CACHES);
        }

        @Test
        @DisplayName("queue-metadata cache is a CaffeineCache instance")
        void cacheIsCaffeineInstance() {
            assertThat(cacheManager.getCache("queue-metadata"))
                    .isInstanceOf(CaffeineCache.class);
        }
    }

    // ========== Default TTL and Max-Size (Req 1.4, 3.8, 3.9) ==========

    @Nested
    @DisplayName("Default TTL and max-size values")
    class DefaultTtlAndMaxSize {

        @Test
        @DisplayName("queue-metadata: TTL=30s, max-size=5000")
        void queueMetadataDefaults() {
            Cache<Object, Object> nativeCache = getNativeCache("queue-metadata");
            Policy<Object, Object> policy = nativeCache.policy();

            assertThat(policy.expireAfterWrite())
                    .isPresent()
                    .hasValueSatisfying(expiration ->
                            assertThat(expiration.getExpiresAfter())
                                    .isEqualTo(Duration.ofSeconds(30)));

            assertThat(policy.eviction())
                    .isPresent()
                    .hasValueSatisfying(eviction ->
                            assertThat(eviction.getMaximum())
                                    .isEqualTo(5_000));
        }
    }

    // ========== Expire-After-Write Semantics (Req 1.5) ==========

    @Nested
    @DisplayName("Expire-after-write semantics")
    class ExpireAfterWriteSemantics {

        @Test
        @DisplayName("queue-metadata uses expireAfterWrite, not expireAfterAccess")
        void usesExpireAfterWrite() {
            Cache<Object, Object> nativeCache = getNativeCache("queue-metadata");
            Policy<Object, Object> policy = nativeCache.policy();

            assertThat(policy.expireAfterWrite())
                    .as("Cache should use expireAfterWrite")
                    .isPresent();

            assertThat(policy.expireAfterAccess())
                    .as("Cache should NOT use expireAfterAccess")
                    .isEmpty();
        }
    }

    // ========== RecordStats Enabled (Req 1.6) ==========

    @Nested
    @DisplayName("RecordStats enabled")
    class RecordStatsEnabled {

        @Test
        @DisplayName("queue-metadata has recordStats() enabled")
        void recordStatsEnabled() {
            Cache<Object, Object> nativeCache = getNativeCache("queue-metadata");

            // Put and get to generate stats, then verify stats are recording
            nativeCache.put("test-key", "test-value");
            nativeCache.getIfPresent("test-key");
            nativeCache.getIfPresent("missing-key");

            assertThat(nativeCache.stats().hitCount())
                    .as("Cache should record hit stats")
                    .isEqualTo(1);
            assertThat(nativeCache.stats().missCount())
                    .as("Cache should record miss stats")
                    .isEqualTo(1);
        }
    }

    // ========== Helper ==========

    private Cache<Object, Object> getNativeCache(String cacheName) {
        CaffeineCache springCache = (CaffeineCache) cacheManager.getCache(cacheName);
        assertThat(springCache).as("Cache '%s' should exist", cacheName).isNotNull();
        return springCache.getNativeCache();
    }
}
