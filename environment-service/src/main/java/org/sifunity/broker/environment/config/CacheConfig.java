// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.config;

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
 * Cache configuration for the environment-service.
 *
 * <p>Creates a {@link SimpleCacheManager} backed by Caffeine with five named caches
 * for auth token validation entity lookups and ACL permission checks. Each cache
 * is configured with per-cache TTL and maximum size read from {@link CacheProperties}.</p>
 *
 * <p>All caches use expire-after-write semantics and have {@code recordStats()} enabled
 * so that Micrometer can expose hit/miss/eviction metrics via the actuator endpoints.</p>
 *
 * @see CacheProperties
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    /**
     * Creates the {@link CacheManager} bean with the named Caffeine caches.
     *
     * <p>{@link SimpleCacheManager} does <strong>not</strong> create caches on demand, so every
     * cache referenced by a {@code @Cacheable} or {@code @CacheEvict} annotation must be listed
     * here. Omitting one fails at runtime with
     * {@code IllegalArgumentException: Cannot find cache named '...'} on the first call, not at
     * startup — so add the name here in the same change that introduces the annotation.</p>
     *
     * @param props per-cache TTL and max-size configuration
     * @return a {@link SimpleCacheManager} containing all environment-service caches
     */
    @Bean
    public CacheManager cacheManager(CacheProperties props) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache("auth-environment", props.getAuthEnvironment()),
                buildCache("auth-shared-secret", props.getAuthSharedSecret()),
                buildCache("auth-solution", props.getAuthSolution()),
                buildCache("auth-domain", props.getAuthDomain()),
                buildCache("acl-permissions", props.getAclPermissions()),
                buildCache("acl-any-grant", props.getAclAnyGrant())
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
