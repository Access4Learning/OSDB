// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
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
 * Cache configuration for the queue-service.
 *
 * <p>Creates a {@link SimpleCacheManager} backed by Caffeine with one named cache
 * for queue metadata routing lookups. The cache is configured with per-cache TTL
 * and maximum size read from {@link CacheProperties}.</p>
 *
 * <p>The cache uses expire-after-write semantics and has {@code recordStats()} enabled
 * so that Micrometer can expose hit/miss/eviction metrics via the actuator endpoints.</p>
 *
 * @see CacheProperties
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    /**
     * Creates the {@link CacheManager} bean with the queue-metadata Caffeine cache.
     *
     * @param props per-cache TTL and max-size configuration
     * @return a {@link SimpleCacheManager} containing the queue-service cache
     */
    @Bean
    public CacheManager cacheManager(CacheProperties props) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache("queue-metadata", props.getQueueMetadata())
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
