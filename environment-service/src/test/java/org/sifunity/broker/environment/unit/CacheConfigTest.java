// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.unit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.environment.config.CacheConfig;
import org.sifunity.broker.environment.config.CacheProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for environment-service {@link CacheConfig}.
 *
 * <p>Verifies named caches exist, default TTL/max-size values match requirements,
 * expire-after-write semantics, and recordStats() enabled.</p>
 *
 * <p>Validates: Requirements 1.3, 1.4, 1.5, 1.6, 2.11, 2.12, 4.4, 4.5</p>
 */
@DisplayName("CacheConfig Unit Tests")
class CacheConfigTest {

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

        private static final List<String> EXPECTED_CACHES = List.of(
                "auth-environment",
                "auth-shared-secret",
                "auth-solution",
                "auth-domain",
                "acl-permissions",
                // Entitlement checks for the response destination gate. Registered separately from
                // acl-permissions because its key has no access-type component and must not collide.
                "acl-any-grant"
        );

        @Test
        @DisplayName("All 6 named caches are present in CacheManager")
        void allNamedCachesExist() {
            assertThat(cacheManager.getCacheNames())
                    .containsExactlyInAnyOrderElementsOf(EXPECTED_CACHES);
        }

        @Test
        @DisplayName("Each cache is a CaffeineCache instance")
        void eachCacheIsCaffeineInstance() {
            for (String name : EXPECTED_CACHES) {
                assertThat(cacheManager.getCache(name))
                        .isInstanceOf(CaffeineCache.class);
            }
        }
    }

    // ========== Default TTL and Max-Size (Req 1.4, 2.11, 2.12, 4.4, 4.5) ==========

    @Nested
    @DisplayName("Default TTL and max-size values")
    class DefaultTtlAndMaxSize {

        @Test
        @DisplayName("auth-environment: TTL=60s, max-size=10000")
        void authEnvironmentDefaults() {
            assertCacheSpec("auth-environment", 60, 10_000);
        }

        @Test
        @DisplayName("auth-shared-secret: TTL=60s, max-size=1000")
        void authSharedSecretDefaults() {
            assertCacheSpec("auth-shared-secret", 60, 1_000);
        }

        @Test
        @DisplayName("auth-solution: TTL=60s, max-size=5000")
        void authSolutionDefaults() {
            assertCacheSpec("auth-solution", 60, 5_000);
        }

        @Test
        @DisplayName("auth-domain: TTL=60s, max-size=5000")
        void authDomainDefaults() {
            assertCacheSpec("auth-domain", 60, 5_000);
        }

        @Test
        @DisplayName("acl-permissions: TTL=60s, max-size=5000")
        void aclPermissionsDefaults() {
            assertCacheSpec("acl-permissions", 60, 5_000);
        }

        private void assertCacheSpec(String cacheName, int expectedTtlSeconds, int expectedMaxSize) {
            Cache<Object, Object> nativeCache = getNativeCache(cacheName);
            Policy<Object, Object> policy = nativeCache.policy();

            assertThat(policy.expireAfterWrite())
                    .isPresent()
                    .hasValueSatisfying(expiration ->
                            assertThat(expiration.getExpiresAfter())
                                    .isEqualTo(Duration.ofSeconds(expectedTtlSeconds)));

            assertThat(policy.eviction())
                    .isPresent()
                    .hasValueSatisfying(eviction ->
                            assertThat(eviction.getMaximum())
                                    .isEqualTo(expectedMaxSize));
        }
    }

    // ========== Expire-After-Write Semantics (Req 1.5) ==========

    @Nested
    @DisplayName("Expire-after-write semantics")
    class ExpireAfterWriteSemantics {

        @Test
        @DisplayName("All caches use expireAfterWrite, not expireAfterAccess")
        void allCachesUseExpireAfterWrite() {
            for (String name : cacheManager.getCacheNames()) {
                Cache<Object, Object> nativeCache = getNativeCache(name);
                Policy<Object, Object> policy = nativeCache.policy();

                assertThat(policy.expireAfterWrite())
                        .as("Cache '%s' should use expireAfterWrite", name)
                        .isPresent();

                assertThat(policy.expireAfterAccess())
                        .as("Cache '%s' should NOT use expireAfterAccess", name)
                        .isEmpty();
            }
        }
    }

    // ========== RecordStats Enabled (Req 1.6) ==========

    @Nested
    @DisplayName("RecordStats enabled")
    class RecordStatsEnabled {

        @Test
        @DisplayName("All caches have recordStats() enabled")
        void allCachesHaveRecordStatsEnabled() {
            for (String name : cacheManager.getCacheNames()) {
                Cache<Object, Object> nativeCache = getNativeCache(name);

                // Put and get to generate stats, then verify stats are recording
                nativeCache.put("test-key", "test-value");
                nativeCache.getIfPresent("test-key");
                nativeCache.getIfPresent("missing-key");

                assertThat(nativeCache.stats().hitCount())
                        .as("Cache '%s' should record hit stats", name)
                        .isEqualTo(1);
                assertThat(nativeCache.stats().missCount())
                        .as("Cache '%s' should record miss stats", name)
                        .isEqualTo(1);
            }
        }
    }

    // ========== Helper ==========

    private Cache<Object, Object> getNativeCache(String cacheName) {
        CaffeineCache springCache = (CaffeineCache) cacheManager.getCache(cacheName);
        assertThat(springCache).as("Cache '%s' should exist", cacheName).isNotNull();
        return springCache.getNativeCache();
    }
}
