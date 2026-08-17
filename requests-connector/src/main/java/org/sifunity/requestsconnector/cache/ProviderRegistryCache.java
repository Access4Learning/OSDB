// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.cache;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Cache for provider registry lookups using Spring Cache abstraction.
 * 
 * <p>This component provides caching for provider registry entries to reduce load on the
 * Environment Service. It uses Spring Cache abstraction with Redis as the backing store
 * to support horizontal scaling of the Requests Connector.</p>
 * 
 * <p><strong>Cache Configuration:</strong></p>
 * <ul>
 *   <li>Cache Name: {@code provider-registry}</li>
 *   <li>Cache Key Format: {@code provider:{serviceName}:{zoneId}:{contextId}}</li>
 *   <li>TTL: 5 minutes (default, configured in RedisConfig)</li>
 *   <li>Backing Store: Redis</li>
 * </ul>
 * 
 * <p><strong>Cache Behavior:</strong></p>
 * <ul>
 *   <li>Cache Hit: Returns cached provider without calling Environment Service</li>
 *   <li>Cache Miss: Calls Environment Service, caches result with TTL</li>
 *   <li>Invalidation: Supports single entry and full cache invalidation</li>
 * </ul>
 * 
 * <p><strong>Requirements:</strong></p>
 * <ul>
 *   <li>2.5: Cache provider registry lookups to reduce Environment Service load</li>
 *   <li>12.1: Cache results with configurable TTL</li>
 *   <li>12.2: Use cached entry when available</li>
 *   <li>12.3: Support cache invalidation</li>
 * </ul>
 * 
 * @see ProviderRegistrationDTO
 * @see EnvironmentServiceClient
 */
@Component
@Slf4j
public class ProviderRegistryCache {
    
    private final EnvironmentServiceClient environmentClient;
    
    /**
     * Constructs a ProviderRegistryCache with the specified Environment Service client.
     * 
     * @param environmentClient client for calling Environment Service
     */
    public ProviderRegistryCache(EnvironmentServiceClient environmentClient) {
        this.environmentClient = environmentClient;
        log.info("ProviderRegistryCache initialized with Spring Cache abstraction");
    }
    
    /**
     * Gets a provider registration from cache or Environment Service.
     * Backward-compatible 3-param method that defaults serviceType to OBJECT.
     * 
     * <p><strong>Requirements:</strong></p>
     * <ul>
     *   <li>2.5: Cache provider registry lookups</li>
     *   <li>12.1: Cache with configurable TTL (5 minutes default)</li>
     *   <li>12.2: Use cached entry when available</li>
     * </ul>
     * 
     * @param serviceName the name of the service (e.g., "StudentPersonal")
     * @param zoneId the zone ID (e.g., "production")
     * @param contextId the context ID (optional, can be null or empty)
     * @return Optional containing the provider registration if found, empty otherwise
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException
     *         if Environment Service returns an error
     */
    @Cacheable(
        value = "provider-registry",
        key = "'provider:' + #domainId + ':' + #solutionId + ':' + #serviceName + ':' + #zoneId + ':' + (#contextId != null && !#contextId.isEmpty() ? #contextId : 'default') + ':OBJECT'",
        unless = "#result == null"
    )
    public Optional<ProviderRegistrationDTO> getProvider(
            String domainId, String solutionId, String serviceName, String zoneId, String contextId) {

        log.debug("Provider cache miss - querying Environment Service: domainId={}, solutionId={}, serviceName={}, zoneId={}, contextId={}, serviceType=OBJECT",
            domainId, solutionId, serviceName, zoneId, contextId);

        Optional<ProviderRegistrationDTO> provider =
            environmentClient.lookupProvider(domainId, solutionId, serviceName, zoneId, contextId);
        
        if (provider.isPresent()) {
            log.info("Provider found and will be cached: serviceName={}, zoneId={}, contextId={}, serviceType=OBJECT, endpoint={}", 
                serviceName, zoneId, contextId, ProtocolDTO.locationOf(provider.get().endPoint()));
        } else {
            log.info("No provider found: serviceName={}, zoneId={}, contextId={}, serviceType=OBJECT", 
                serviceName, zoneId, contextId);
        }
        
        return provider;
    }
    
    /**
     * Gets a provider registration from cache or Environment Service, filtered by service type.
     * 
     * <p>This method is annotated with {@code @Cacheable} to enable automatic caching:</p>
     * <ol>
     *   <li>Spring checks the cache for an entry matching the cache key</li>
     *   <li>If found (cache hit), returns cached provider without calling the method</li>
     *   <li>If not found (cache miss), executes the method to query Environment Service</li>
     *   <li>If provider found, Spring caches it with configured TTL</li>
     *   <li>Returns the provider (or empty if not found)</li>
     * </ol>
     * 
     * <p><strong>Cache Key:</strong> Constructed from serviceName, zoneId, contextId, and serviceType
     * using SpEL expression. The key format is:
     * {@code provider:{serviceName}:{zoneId}:{contextId}:{serviceType}}</p>
     * 
     * <p>Including serviceType in the cache key ensures that OBJECT and XQUERYTEMPLATE lookups
     * for the same serviceName are cached separately.</p>
     * 
     * <p><strong>Requirements:</strong></p>
     * <ul>
     *   <li>2.5: Cache provider registry lookups</li>
     *   <li>3.1: Support serviceType in provider lookup</li>
     *   <li>3.5: Use serviceType to disambiguate provider registrations</li>
     *   <li>3.6: Default to OBJECT when serviceType is absent</li>
     *   <li>12.1: Cache with configurable TTL (5 minutes default)</li>
     *   <li>12.2: Use cached entry when available</li>
     * </ul>
     * 
     * @param serviceName the name of the service (e.g., "StudentPersonal")
     * @param zoneId the zone ID (e.g., "production")
     * @param contextId the context ID (optional, can be null or empty)
     * @param serviceType the service type (e.g., "OBJECT", "XQUERYTEMPLATE"); null defaults to "OBJECT"
     * @return Optional containing the provider registration if found, empty otherwise
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException
     *         if Environment Service returns an error
     */
    @Cacheable(
        value = "provider-registry",
        key = "'provider:' + #domainId + ':' + #solutionId + ':' + #serviceName + ':' + #zoneId + ':' + (#contextId != null && !#contextId.isEmpty() ? #contextId : 'default') + ':' + (#serviceType != null && !#serviceType.isEmpty() ? #serviceType : 'OBJECT')",
        unless = "#result == null"
    )
    public Optional<ProviderRegistrationDTO> getProvider(
            String domainId, String solutionId, String serviceName, String zoneId, String contextId, String serviceType) {

        String effectiveServiceType = (serviceType != null && !serviceType.isEmpty()) ? serviceType : "OBJECT";

        log.debug("Provider cache miss - querying Environment Service: domainId={}, solutionId={}, serviceName={}, zoneId={}, contextId={}, serviceType={}",
            domainId, solutionId, serviceName, zoneId, contextId, effectiveServiceType);

        Optional<ProviderRegistrationDTO> provider =
            environmentClient.lookupProvider(domainId, solutionId, serviceName, zoneId, contextId, serviceType);
        
        if (provider.isPresent()) {
            log.info("Provider found and will be cached: serviceName={}, zoneId={}, contextId={}, serviceType={}, endpoint={}", 
                serviceName, zoneId, contextId, effectiveServiceType, ProtocolDTO.locationOf(provider.get().endPoint()));
        } else {
            log.info("No provider found: serviceName={}, zoneId={}, contextId={}, serviceType={}", 
                serviceName, zoneId, contextId, effectiveServiceType);
        }
        
        return provider;
    }
    
    /**
     * Invalidates a cached provider registry entry (backward compatible, uses OBJECT as serviceType).
     * 
     * <p>This method removes a provider entry from the cache. The next lookup for this
     * provider will query the Environment Service. This is useful when provider
     * registrations are updated or removed.</p>
     * 
     * <p><strong>Requirements:</strong> 12.3</p>
     * 
     * @param serviceName the name of the service
     * @param zoneId the zone ID
     * @param contextId the context ID (optional, can be null or empty)
     */
    @CacheEvict(
        value = "provider-registry",
        key = "'provider:' + #domainId + ':' + #solutionId + ':' + #serviceName + ':' + #zoneId + ':' + (#contextId != null && !#contextId.isEmpty() ? #contextId : 'default') + ':OBJECT'"
    )
    public void invalidateProvider(String domainId, String solutionId, String serviceName, String zoneId, String contextId) {
        log.info("Invalidated provider cache entry: domainId={}, solutionId={}, serviceName={}, zoneId={}, contextId={}, serviceType=OBJECT",
            domainId, solutionId, serviceName, zoneId, contextId);
    }
    
    /**
     * Invalidates a cached provider registry entry for a specific service type.
     * 
     * <p>This method removes a provider entry from the cache for the given serviceType.
     * The next lookup for this provider + serviceType will query the Environment Service.</p>
     * 
     * <p><strong>Requirements:</strong> 12.3</p>
     * 
     * @param serviceName the name of the service
     * @param zoneId the zone ID
     * @param contextId the context ID (optional, can be null or empty)
     * @param serviceType the service type (e.g., "OBJECT", "XQUERYTEMPLATE"); null defaults to "OBJECT"
     */
    @CacheEvict(
        value = "provider-registry",
        key = "'provider:' + #domainId + ':' + #solutionId + ':' + #serviceName + ':' + #zoneId + ':' + (#contextId != null && !#contextId.isEmpty() ? #contextId : 'default') + ':' + (#serviceType != null && !#serviceType.isEmpty() ? #serviceType : 'OBJECT')"
    )
    public void invalidateProvider(String domainId, String solutionId, String serviceName, String zoneId, String contextId, String serviceType) {
        String effectiveServiceType = (serviceType != null && !serviceType.isEmpty()) ? serviceType : "OBJECT";
        log.info("Invalidated provider cache entry: domainId={}, solutionId={}, serviceName={}, zoneId={}, contextId={}, serviceType={}",
            domainId, solutionId, serviceName, zoneId, contextId, effectiveServiceType);
    }
    
    /**
     * Invalidates all cached provider registry entries.
     * 
     * <p>This method clears all provider cache entries. This is useful for administrative
     * operations or when the provider registry undergoes significant changes.</p>
     * 
     * <p><strong>Warning:</strong> This operation clears all cached providers, which will
     * cause increased load on the Environment Service until the cache is repopulated.</p>
     * 
     * <p><strong>Requirements:</strong> 12.3</p>
     */
    @CacheEvict(value = "provider-registry", allEntries = true)
    public void invalidateAll() {
        log.info("Invalidated all provider cache entries");
    }
}
