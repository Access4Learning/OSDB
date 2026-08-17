// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.servicesconnector.cache.ProviderRegistryCache;
import org.sifunity.servicesconnector.client.EnvironmentServiceClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Unit tests for ProviderRegistryCache.
 * 
 * Tests Requirements:
 * - 7.5: Cache provider registry lookups to reduce Environment Service load
 * - 13.1: Use cached provider entry when available (cache hit)
 * - 13.2: Call Environment Service on cache miss
 * - 13.3: Support cache invalidation
 * - 13.4: Use configurable TTL (default 5 minutes)
 */
@ExtendWith(MockitoExtension.class)
class ProviderRegistryCacheTest {
    
    @Mock
    private EnvironmentServiceClient environmentClient;
    
    @Mock
    private RedisTemplate<String, ProviderRegistrationDTO> redisTemplate;
    
    @Mock
    private ValueOperations<String, ProviderRegistrationDTO> valueOperations;
    
    private ProviderRegistryCache cache;
    
    private static final long DEFAULT_TTL_SECONDS = 300; // 5 minutes
    
    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new ProviderRegistryCache(environmentClient, redisTemplate, DEFAULT_TTL_SECONDS);
    }
    
    // ========== Cache Hit Tests (Requirement 13.1) ==========
    
    @Test
    void getProvider_whenCacheHit_shouldReturnCachedProvider() {
        // Given
        String serviceName = "DataExport";
        String zoneId = "production";
        String contextId = "context1";
        String cacheKey = "provider:d1:s1:DataExport:production:context1";
        
        ProviderRegistrationDTO cachedProvider = createTestProvider(serviceName, zoneId, contextId);
        when(valueOperations.get(cacheKey)).thenReturn(cachedProvider);
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(cachedProvider);
        
        // Verify cache was checked
        verify(valueOperations).get(cacheKey);
        
        // Verify Environment Service was NOT called (cache hit)
        verifyNoInteractions(environmentClient);
    }
    
    @Test
    void getProvider_whenCacheHitWithNullContext_shouldReturnCachedProvider() {
        // Given
        String serviceName = "DataExport";
        String zoneId = "production";
        String cacheKey = "provider:d1:s1:DataExport:production:default";
        
        ProviderRegistrationDTO cachedProvider = createTestProvider(serviceName, zoneId, null);
        when(valueOperations.get(cacheKey)).thenReturn(cachedProvider);
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, null);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(cachedProvider);
        
        // Verify cache was checked with "default" context
        verify(valueOperations).get(cacheKey);
        
        // Verify Environment Service was NOT called
        verifyNoInteractions(environmentClient);
    }
    
    // ========== Cache Miss Tests (Requirement 13.2) ==========
    
    @Test
    void getProvider_whenCacheMiss_shouldCallEnvironmentServiceAndCacheResult() {
        // Given
        String serviceName = "DataExport";
        String zoneId = "production";
        String contextId = "context1";
        String cacheKey = "provider:d1:s1:DataExport:production:context1";
        
        ProviderRegistrationDTO provider = createTestProvider(serviceName, zoneId, contextId);
        
        // Cache miss
        when(valueOperations.get(cacheKey)).thenReturn(null);
        
        // Environment Service returns provider
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(provider);
        
        // Verify cache was checked
        verify(valueOperations).get(cacheKey);
        
        // Verify Environment Service was called
        verify(environmentClient).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Verify result was cached with TTL
        verify(valueOperations).set(cacheKey, provider, Duration.ofSeconds(DEFAULT_TTL_SECONDS));
    }
    
    @Test
    void getProvider_whenCacheMissAndProviderNotFound_shouldNotCacheResult() {
        // Given
        String serviceName = "NonExistentService";
        String zoneId = "production";
        String contextId = "context1";
        String cacheKey = "provider:d1:s1:NonExistentService:production:context1";
        
        // Cache miss
        when(valueOperations.get(cacheKey)).thenReturn(null);
        
        // Environment Service returns empty
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.empty());
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isEmpty();
        
        // Verify cache was checked
        verify(valueOperations).get(cacheKey);
        
        // Verify Environment Service was called
        verify(environmentClient).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Verify result was NOT cached (only cache successful lookups)
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }
    
    @Test
    void getProvider_whenCacheMissWithNullContext_shouldUseDefaultInCacheKey() {
        // Given
        String serviceName = "DataExport";
        String zoneId = "production";
        String cacheKey = "provider:d1:s1:DataExport:production:default";
        
        ProviderRegistrationDTO provider = createTestProvider(serviceName, zoneId, null);
        
        // Cache miss
        when(valueOperations.get(cacheKey)).thenReturn(null);
        
        // Environment Service returns provider
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, null))
            .thenReturn(Optional.of(provider));
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, null);
        
        // Then
        assertThat(result).isPresent();
        
        // Verify cache key uses "default" for null context
        verify(valueOperations).get(cacheKey);
        verify(valueOperations).set(cacheKey, provider, Duration.ofSeconds(DEFAULT_TTL_SECONDS));
    }
    
    // ========== Cache Invalidation Tests (Requirement 13.3) ==========
    
    @Test
    void invalidate_whenEntryExists_shouldDeleteFromCache() {
        // Given
        String serviceName = "DataExport";
        String zoneId = "production";
        String contextId = "context1";
        String cacheKey = "provider:d1:s1:DataExport:production:context1";
        
        when(redisTemplate.delete(cacheKey)).thenReturn(true);
        
        // When
        cache.invalidate("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        verify(redisTemplate).delete(cacheKey);
    }
    
    @Test
    void invalidate_whenEntryDoesNotExist_shouldHandleGracefully() {
        // Given
        String serviceName = "NonExistentService";
        String zoneId = "production";
        String contextId = "context1";
        String cacheKey = "provider:d1:s1:NonExistentService:production:context1";
        
        when(redisTemplate.delete(cacheKey)).thenReturn(false);
        
        // When
        cache.invalidate("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        verify(redisTemplate).delete(cacheKey);
        // Should not throw exception
    }
    
    @Test
    void invalidate_withNullContext_shouldUseDefaultInCacheKey() {
        // Given
        String serviceName = "DataExport";
        String zoneId = "production";
        String cacheKey = "provider:d1:s1:DataExport:production:default";
        
        when(redisTemplate.delete(cacheKey)).thenReturn(true);
        
        // When
        cache.invalidate("d1", "s1", serviceName, zoneId, null);
        
        // Then
        verify(redisTemplate).delete(cacheKey);
    }
    
    @Test
    void invalidate_afterInvalidation_nextGetShouldCallEnvironmentService() {
        // Given
        String serviceName = "DataExport";
        String zoneId = "production";
        String contextId = "context1";
        String cacheKey = "provider:d1:s1:DataExport:production:context1";
        
        ProviderRegistrationDTO provider = createTestProvider(serviceName, zoneId, contextId);
        
        // First get - cache hit
        when(valueOperations.get(cacheKey)).thenReturn(provider);
        cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Invalidate
        when(redisTemplate.delete(cacheKey)).thenReturn(true);
        cache.invalidate("d1", "s1", serviceName, zoneId, contextId);
        
        // Second get - cache miss after invalidation
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When
        cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        verify(environmentClient).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
    }
    
    // ========== TTL Configuration Tests (Requirement 13.4) ==========
    
    @Test
    void constructor_withCustomTtl_shouldUseThatTtl() {
        // Given
        long customTtl = 600; // 10 minutes
        ProviderRegistryCache customCache = new ProviderRegistryCache(
            environmentClient, redisTemplate, customTtl);
        
        String serviceName = "DataExport";
        String zoneId = "production";
        String contextId = "context1";
        String cacheKey = "provider:d1:s1:DataExport:production:context1";
        
        ProviderRegistrationDTO provider = createTestProvider(serviceName, zoneId, contextId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When
        customCache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        verify(valueOperations).set(cacheKey, provider, Duration.ofSeconds(customTtl));
    }
    
    // ========== Cache Key Format Tests ==========
    
    @Test
    void getProvider_shouldUseCacheKeyFormat() {
        // Given
        String serviceName = "MyService";
        String zoneId = "zone123";
        String contextId = "ctx456";
        String expectedCacheKey = "provider:d1:s1:MyService:zone123:ctx456";
        
        when(valueOperations.get(expectedCacheKey)).thenReturn(null);
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.empty());
        
        // When
        cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        verify(valueOperations).get(expectedCacheKey);
    }
    
    // ========== Helper Methods ==========
    
    private ProviderRegistrationDTO createTestProvider(String serviceName, String zoneId, String contextId) {
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(
                new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))),
                new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.QUERY)))
            )),
                Instant.now()
        );
    }
}
