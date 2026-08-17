// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.requestsconnector.cache.ProviderRegistryCache;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Integration tests for ProviderRegistryCache with Redis.
 * 
 * <p>These tests verify the caching behavior with an actual Redis instance
 * using Testcontainers. They test cache hits, cache misses, TTL, and invalidation.</p>
 * 
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li>Cache miss - first lookup calls Environment Service</li>
 *   <li>Cache hit - second lookup uses cached value</li>
 *   <li>Cache invalidation - single entry eviction</li>
 *   <li>Cache invalidation - all entries eviction</li>
 *   <li>Different cache keys for different parameters</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@DisplayName("ProviderRegistryCache Integration Tests")
class ProviderRegistryCacheIntegrationTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private ProviderRegistryCache cache;
    
    @Autowired
    private CacheManager cacheManager;
    
    @MockBean
    private EnvironmentServiceClient environmentClient;
    
    @BeforeEach
    void setUp() {
        // Clear cache before each test
        cacheManager.getCache("provider-registry").clear();
        reset(environmentClient);
    }
    
    @Test
    @DisplayName("Should cache provider on first lookup and use cache on second lookup")
    void shouldCacheProviderAndReuseOnSecondLookup() {
        // Given
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "default";
        
        ProviderRegistrationDTO provider = createTestProvider(serviceName, zoneId, contextId);
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When - First lookup (cache miss)
        Optional<ProviderRegistrationDTO> result1 = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then - Should call Environment Service
        assertThat(result1).isPresent();
        assertThat(result1.get().serviceName()).isEqualTo(serviceName);
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // When - Second lookup (cache hit)
        Optional<ProviderRegistrationDTO> result2 = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then - Should NOT call Environment Service again
        assertThat(result2).isPresent();
        assertThat(result2.get().serviceName()).isEqualTo(serviceName);
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName, zoneId, contextId); // Still only 1 call
    }
    
    @Test
    @DisplayName("Should use different cache keys for different parameters")
    void shouldUseDifferentCacheKeysForDifferentParameters() {
        // Given
        String serviceName1 = "StudentPersonal";
        String serviceName2 = "SchoolInfo";
        String zoneId = "production";
        String contextId = "default";
        
        ProviderRegistrationDTO provider1 = createTestProvider(serviceName1, zoneId, contextId);
        ProviderRegistrationDTO provider2 = createTestProvider(serviceName2, zoneId, contextId);
        
        when(environmentClient.lookupProvider("d1", "s1", serviceName1, zoneId, contextId))
            .thenReturn(Optional.of(provider1));
        when(environmentClient.lookupProvider("d1", "s1", serviceName2, zoneId, contextId))
            .thenReturn(Optional.of(provider2));
        
        // When
        Optional<ProviderRegistrationDTO> result1 = cache.getProvider("d1", "s1", serviceName1, zoneId, contextId);
        Optional<ProviderRegistrationDTO> result2 = cache.getProvider("d1", "s1", serviceName2, zoneId, contextId);
        
        // Then - Both should call Environment Service (different cache keys)
        assertThat(result1).isPresent();
        assertThat(result1.get().serviceName()).isEqualTo(serviceName1);
        assertThat(result2).isPresent();
        assertThat(result2.get().serviceName()).isEqualTo(serviceName2);
        
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName1, zoneId, contextId);
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName2, zoneId, contextId);
    }
    
    @Test
    @DisplayName("Should invalidate specific cache entry")
    void shouldInvalidateSpecificCacheEntry() {
        // Given
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "default";
        
        ProviderRegistrationDTO provider = createTestProvider(serviceName, zoneId, contextId);
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When - First lookup (cache miss)
        cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // When - Invalidate cache
        cache.invalidateProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // When - Second lookup (cache miss again after invalidation)
        cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then - Should call Environment Service again
        verify(environmentClient, times(2)).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
    }
    
    @Test
    @DisplayName("Should invalidate all cache entries")
    void shouldInvalidateAllCacheEntries() {
        // Given
        String serviceName1 = "StudentPersonal";
        String serviceName2 = "SchoolInfo";
        String zoneId = "production";
        String contextId = "default";
        
        ProviderRegistrationDTO provider1 = createTestProvider(serviceName1, zoneId, contextId);
        ProviderRegistrationDTO provider2 = createTestProvider(serviceName2, zoneId, contextId);
        
        when(environmentClient.lookupProvider("d1", "s1", serviceName1, zoneId, contextId))
            .thenReturn(Optional.of(provider1));
        when(environmentClient.lookupProvider("d1", "s1", serviceName2, zoneId, contextId))
            .thenReturn(Optional.of(provider2));
        
        // When - Cache both providers
        cache.getProvider("d1", "s1", serviceName1, zoneId, contextId);
        cache.getProvider("d1", "s1", serviceName2, zoneId, contextId);
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName1, zoneId, contextId);
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName2, zoneId, contextId);
        
        // When - Invalidate all
        cache.invalidateAll();
        
        // When - Lookup both again
        cache.getProvider("d1", "s1", serviceName1, zoneId, contextId);
        cache.getProvider("d1", "s1", serviceName2, zoneId, contextId);
        
        // Then - Should call Environment Service again for both
        verify(environmentClient, times(2)).lookupProvider("d1", "s1", serviceName1, zoneId, contextId);
        verify(environmentClient, times(2)).lookupProvider("d1", "s1", serviceName2, zoneId, contextId);
    }
    
    @Test
    @DisplayName("Should handle null contextId consistently")
    void shouldHandleNullContextIdConsistently() {
        // Given
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = null;
        
        ProviderRegistrationDTO provider = createTestProvider(serviceName, zoneId, "default");
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When - First lookup with null contextId
        cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // When - Second lookup with null contextId (should hit cache)
        cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then - Should only call Environment Service once
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
    }
    
    @Test
    @DisplayName("Should not cache empty results")
    void shouldNotCacheEmptyResults() {
        // Given
        String serviceName = "NonExistentService";
        String zoneId = "production";
        String contextId = "default";
        
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.empty());
        
        // When - First lookup (returns empty)
        Optional<ProviderRegistrationDTO> result1 = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // When - Second lookup (should call Environment Service again since empty not cached)
        Optional<ProviderRegistrationDTO> result2 = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then - Should call Environment Service twice (empty results not cached)
        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();
        verify(environmentClient, times(2)).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
    }
    
    /**
     * Helper method to create a test provider registration.
     */
    private ProviderRegistrationDTO createTestProvider(String serviceName, String zoneId, String contextId) {
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/" + serviceName.toLowerCase()),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(
                new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))),
                new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.QUERY)))
            )),
                null
        );
    }
}
