// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Cache configuration for the events-connector.
 *
 * <p>Creates a {@link SimpleCacheManager} backed by Caffeine with named caches
 * for context UUID resolution and subscription query results. Each cache is configured
 * with per-cache TTL and maximum size read from {@link CacheProperties}.</p>
 *
 * <p>The caches use expire-after-write semantics and have {@code recordStats()} enabled
 * so that Micrometer can expose hit/miss/eviction metrics via the actuator endpoints.</p>
 *
 * @see CacheProperties
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    /**
     * Creates the {@link CacheManager} bean with the events-connector Caffeine caches.
     * Skipped when spring.cache.type=none (used in integration tests to disable caching).
     *
     * @param props per-cache TTL and max-size configuration
     * @return a {@link SimpleCacheManager} containing the events-connector caches
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager cacheManager(CacheProperties props) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache("context-uuid", props.getContextUuid()),
                buildCache("subscription-query", props.getSubscriptionQuery())
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, CacheProperties.CacheSpec spec) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(spec.getTtlSeconds()))
                .maximumSize(spec.getMaxSize())
                .recordStats()
                .build());
    }
}
