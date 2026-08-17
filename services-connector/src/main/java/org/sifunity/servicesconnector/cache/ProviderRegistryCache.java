// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.cache;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.servicesconnector.client.EnvironmentServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache for provider registry lookups.
 * 
 * <p>This component provides caching for provider registry entries to reduce load on the
 * Environment Service. It uses Redis for distributed caching to support horizontal scaling
 * of the Services Connector.</p>
 * 
 * <p><strong>Cache Key Format:</strong> {@code provider:{domainId}:{solutionId}:{serviceName}:{zoneId}:{contextId}}</p>
 * 
 * <p><strong>Cache Behavior:</strong></p>
 * <ul>
 *   <li>Cache Hit: Returns cached provider without calling Environment Service</li>
 *   <li>Cache Miss: Calls Environment Service, caches result with TTL</li>
 *   <li>TTL: Configurable via {@code provider.cache.ttl} property (default: 5 minutes)</li>
 * </ul>
 * 
 * <p><strong>Requirements:</strong></p>
 * <ul>
 *   <li>7.5: Cache provider registry lookups to reduce Environment Service load</li>
 *   <li>13.1: Use cached provider entry when available</li>
 *   <li>13.2: Call Environment Service on cache miss</li>
 *   <li>13.3: Support cache invalidation</li>
 *   <li>13.4: Use configurable TTL (default 5 minutes)</li>
 * </ul>
 * 
 * @see ProviderRegistrationDTO
 * @see EnvironmentServiceClient
 */
@Component
@Slf4j
public class ProviderRegistryCache {
    
    private final EnvironmentServiceClient environmentClient;
    private final RedisTemplate<String, ProviderRegistrationDTO> redisTemplate;
    private final Duration cacheTtl;
    
    /**
     * Constructs a ProviderRegistryCache with the specified dependencies.
     * 
     * @param environmentClient client for calling Environment Service
     * @param redisTemplate Redis template for cache operations
     * @param cacheTtlSeconds cache TTL in seconds (from {@code provider.cache.ttl} property)
     */
    public ProviderRegistryCache(
            EnvironmentServiceClient environmentClient,
            RedisTemplate<String, ProviderRegistrationDTO> redisTemplate,
            @Value("${provider.cache.ttl:300}") long cacheTtlSeconds) {
        this.environmentClient = environmentClient;
        this.redisTemplate = redisTemplate;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
        log.info("ProviderRegistryCache initialized with TTL: {} seconds", cacheTtlSeconds);
    }
    
    /**
     * Gets a provider registration from cache or Environment Service.
     * 
     * <p>This method implements a cache-aside pattern:</p>
     * <ol>
     *   <li>Check Redis cache for existing entry</li>
     *   <li>If found (cache hit), return cached provider</li>
     *   <li>If not found (cache miss), query Environment Service</li>
     *   <li>If provider found in Environment Service, cache it with TTL</li>
     *   <li>Return the provider (or empty if not found)</li>
     * </ol>
     * 
     * <p><strong>Requirements:</strong></p>
     * <ul>
     *   <li>7.5: Cache provider registry lookups</li>
     *   <li>13.1: Use cached entry when available</li>
     *   <li>13.2: Call Environment Service on cache miss</li>
     * </ul>
     * 
     * @param domainId the domain ID for tenant isolation (required)
     * @param solutionId the solution ID for tenant isolation (required)
     * @param serviceName the name of the service (e.g., "DataExport")
     * @param zoneId the zone ID (e.g., "production")
     * @param contextId the context ID (optional, can be null)
     * @return Optional containing the provider registration if found, empty otherwise
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException
     *         if Environment Service returns an error
     */
    public Optional<ProviderRegistrationDTO> getProvider(
            String domainId, String solutionId, String serviceName, String zoneId, String contextId) {

        String cacheKey = buildCacheKey(domainId, solutionId, serviceName, zoneId, contextId);

        // Check cache first
        ProviderRegistrationDTO cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Provider cache hit: {}", cacheKey);
            return Optional.of(cached);
        }

        // Cache miss - query Environment Service
        log.debug("Provider cache miss: {}", cacheKey);
        Optional<ProviderRegistrationDTO> provider =
            environmentClient.lookupProvider(domainId, solutionId, serviceName, zoneId, contextId);
        
        // Cache result if found
        provider.ifPresent(p -> {
            redisTemplate.opsForValue().set(cacheKey, p, cacheTtl);
            log.debug("Cached provider: {} with TTL: {} seconds", cacheKey, cacheTtl.getSeconds());
        });
        
        return provider;
    }
    
    /**
     * Invalidates a cached provider registry entry.
     * 
     * <p>This method removes a provider entry from the cache, forcing the next lookup
     * to query the Environment Service. This is useful when provider registrations are
     * updated or removed.</p>
     * 
     * <p><strong>Requirements:</strong> 13.3</p>
     * 
     * @param domainId the domain ID for tenant isolation (required)
     * @param solutionId the solution ID for tenant isolation (required)
     * @param serviceName the name of the service
     * @param zoneId the zone ID
     * @param contextId the context ID (optional, can be null)
     */
    public void invalidate(String domainId, String solutionId, String serviceName, String zoneId, String contextId) {
        String cacheKey = buildCacheKey(domainId, solutionId, serviceName, zoneId, contextId);
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Invalidated provider cache: {}", cacheKey);
        } else {
            log.debug("Cache key not found for invalidation: {}", cacheKey);
        }
    }
    
    /**
     * Builds a cache key from domain ID, solution ID, service name, zone ID, and context ID.
     *
     * <p>Cache key format: {@code provider:{domainId}:{solutionId}:{serviceName}:{zoneId}:{contextId}}</p>
     * <p>Prepending domainId and solutionId ensures tenants cannot collide in the shared Redis cache,
     * since solutionId/zoneId are not globally unique.</p>
     * <p>If contextId is null, uses "default" as the context.</p>
     *
     * @param domainId the domain ID
     * @param solutionId the solution ID
     * @param serviceName the service name
     * @param zoneId the zone ID
     * @param contextId the context ID (optional, can be null)
     * @return the cache key string
     */
    private String buildCacheKey(String domainId, String solutionId, String serviceName, String zoneId, String contextId) {
        return String.format("provider:%s:%s:%s:%s:%s",
            domainId,
            solutionId,
            serviceName,
            zoneId,
            contextId != null ? contextId : "default");
    }
}
