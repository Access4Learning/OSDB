// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import net.jqwik.api.*;
import org.mockito.Mockito;
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
 * Property-based tests for ProviderRegistryCache.
 * 
 * This test class uses jqwik to verify cache behavior with randomly generated inputs.
 * 
 * **Validates: Requirements 7.5, 13.1, 13.2, 13.3**
 * 
 * Property 12: Provider Registry Cache Hit Avoids Environment Service Call
 * *For any* job creation request where the provider registry entry is cached,
 * the Services Connector should use the cached entry without calling the Environment Service.
 * 
 * Property 13: Provider Registry Cache Miss Calls Environment Service
 * *For any* job creation request where the provider registry entry is not cached,
 * the Services Connector should call the Environment Service and cache the result.
 * 
 * Property 14: Cache Invalidation Removes Entry
 * *For any* provider registry entry, when invalidation is requested,
 * the Services Connector should remove the entry from cache.
 */
@Tag("Feature-services-connector")
class ProviderRegistryCachePropertiesTest {
    
    /**
     * Helper class to encapsulate test context with mocks.
     * This ensures proper lifecycle management for each property test.
     */
    private static class TestContext implements AutoCloseable {
        final EnvironmentServiceClient environmentClient;
        final RedisTemplate<String, ProviderRegistrationDTO> redisTemplate;
        final ValueOperations<String, ProviderRegistrationDTO> valueOperations;
        final ProviderRegistryCache cache;
        
        @SuppressWarnings("unchecked")
        TestContext(long ttlSeconds) {
            environmentClient = mock(EnvironmentServiceClient.class);
            redisTemplate = mock(RedisTemplate.class);
            valueOperations = mock(ValueOperations.class);
            
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            
            cache = new ProviderRegistryCache(environmentClient, redisTemplate, ttlSeconds);
        }
        
        @Override
        public void close() {
            // Reset mocks to avoid state leakage between tests
            Mockito.reset(environmentClient, redisTemplate, valueOperations);
        }
    }
    
    // ========== Property 12: Cache Hit Avoids Environment Service Call ==========
    
    @Property(tries = 10)
    @Tag("Property-12-Provider-Registry-Cache-Hit-Avoids-Environment-Service-Call")
    @Label("Property 12: Cache hit should return cached provider without calling Environment Service")
    void cacheHit_shouldNotCallEnvironmentService(
            @ForAll("validServiceNames") String serviceName,
            @ForAll("validZoneIds") String zoneId,
            @ForAll("optionalContextIds") String contextId,
            @ForAll("uuids") UUID providerId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("validEndpoints") String endpoint) {
        
        try (TestContext ctx = new TestContext(300)) {
            // Given: Provider is cached
            String cacheKey = buildCacheKey(serviceName, zoneId, contextId);
            ProviderRegistrationDTO cachedProvider = createProvider(
                providerId, environmentId, serviceName, zoneId, contextId, endpoint);
            
            when(ctx.valueOperations.get(cacheKey)).thenReturn(cachedProvider);
            
            // When: Getting provider
            Optional<ProviderRegistrationDTO> result = ctx.cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
            
            // Then: Cached provider should be returned
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(cachedProvider);
            
            // And: Cache should be checked
            verify(ctx.valueOperations).get(cacheKey);
            
            // And: Environment Service should NOT be called (cache hit)
            verifyNoInteractions(ctx.environmentClient);
        }
    }
    
    @Property(tries = 10)
    @Tag("Property-12-Provider-Registry-Cache-Hit-Avoids-Environment-Service-Call")
    @Label("Property 12: Multiple cache hits should never call Environment Service")
    void multipleCacheHits_shouldNeverCallEnvironmentService(
            @ForAll("validServiceNames") String serviceName,
            @ForAll("validZoneIds") String zoneId,
            @ForAll("optionalContextIds") String contextId,
            @ForAll("numberOfCalls") int numberOfCalls) {
        
        try (TestContext ctx = new TestContext(300)) {
            // Given: Provider is cached
            String cacheKey = buildCacheKey(serviceName, zoneId, contextId);
            ProviderRegistrationDTO cachedProvider = createProvider(
                UUID.randomUUID(), UUID.randomUUID(), serviceName, zoneId, contextId, 
                "http://provider.example.com");
            
            when(ctx.valueOperations.get(cacheKey)).thenReturn(cachedProvider);
            
            // When: Getting provider multiple times
            for (int i = 0; i < numberOfCalls; i++) {
                Optional<ProviderRegistrationDTO> result = ctx.cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
                assertThat(result).isPresent();
            }
            
            // Then: Cache should be checked multiple times
            verify(ctx.valueOperations, times(numberOfCalls)).get(cacheKey);
            
            // And: Environment Service should NEVER be called
            verifyNoInteractions(ctx.environmentClient);
        }
    }
    
    // ========== Property 13: Cache Miss Calls Environment Service ==========
    
    @Property(tries = 10)
    @Tag("Property-13-Provider-Registry-Cache-Miss-Calls-Environment-Service")
    @Label("Property 13: Cache miss should call Environment Service and cache result")
    void cacheMiss_shouldCallEnvironmentServiceAndCacheResult(
            @ForAll("validServiceNames") String serviceName,
            @ForAll("validZoneIds") String zoneId,
            @ForAll("optionalContextIds") String contextId,
            @ForAll("uuids") UUID providerId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("validEndpoints") String endpoint,
            @ForAll("ttlSeconds") long ttlSeconds) {
        
        try (TestContext ctx = new TestContext(ttlSeconds)) {
            // Given: Cache miss
            String cacheKey = buildCacheKey(serviceName, zoneId, contextId);
            when(ctx.valueOperations.get(cacheKey)).thenReturn(null);
            
            // And: Environment Service returns provider
            ProviderRegistrationDTO provider = createProvider(
                providerId, environmentId, serviceName, zoneId, contextId, endpoint);
            when(ctx.environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
                .thenReturn(Optional.of(provider));
            
            // When: Getting provider
            Optional<ProviderRegistrationDTO> result = ctx.cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
            
            // Then: Provider should be returned
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(provider);
            
            // And: Cache should be checked
            verify(ctx.valueOperations).get(cacheKey);
            
            // And: Environment Service should be called (cache miss)
            verify(ctx.environmentClient).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
            
            // And: Result should be cached with TTL
            verify(ctx.valueOperations).set(cacheKey, provider, Duration.ofSeconds(ttlSeconds));
        }
    }
    
    @Property(tries = 10)
    @Tag("Property-13-Provider-Registry-Cache-Miss-Calls-Environment-Service")
    @Label("Property 13: Cache miss with provider not found should not cache result")
    void cacheMiss_whenProviderNotFound_shouldNotCacheResult(
            @ForAll("validServiceNames") String serviceName,
            @ForAll("validZoneIds") String zoneId,
            @ForAll("optionalContextIds") String contextId) {
        
        try (TestContext ctx = new TestContext(300)) {
            // Given: Cache miss
            String cacheKey = buildCacheKey(serviceName, zoneId, contextId);
            when(ctx.valueOperations.get(cacheKey)).thenReturn(null);
            
            // And: Environment Service returns empty (provider not found)
            when(ctx.environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
                .thenReturn(Optional.empty());
            
            // When: Getting provider
            Optional<ProviderRegistrationDTO> result = ctx.cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
            
            // Then: Empty result should be returned
            assertThat(result).isEmpty();
            
            // And: Cache should be checked
            verify(ctx.valueOperations).get(cacheKey);
            
            // And: Environment Service should be called
            verify(ctx.environmentClient).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
            
            // And: Result should NOT be cached (only cache successful lookups)
            verify(ctx.valueOperations, never()).set(anyString(), any(), any(Duration.class));
        }
    }
    
    // ========== Property 14: Cache Invalidation Removes Entry ==========
    
    @Property(tries = 10)
    @Tag("Property-14-Cache-Invalidation-Removes-Entry")
    @Label("Property 14: Invalidation should remove entry from cache")
    void invalidate_shouldRemoveEntryFromCache(
            @ForAll("validServiceNames") String serviceName,
            @ForAll("validZoneIds") String zoneId,
            @ForAll("optionalContextIds") String contextId) {
        
        try (TestContext ctx = new TestContext(300)) {
            // Given: Cache key
            String cacheKey = buildCacheKey(serviceName, zoneId, contextId);
            when(ctx.redisTemplate.delete(cacheKey)).thenReturn(true);
            
            // When: Invalidating cache
            ctx.cache.invalidate("d1", "s1", serviceName, zoneId, contextId);
            
            // Then: Cache entry should be deleted
            verify(ctx.redisTemplate).delete(cacheKey);
        }
    }
    
    @Property(tries = 10)
    @Tag("Property-14-Cache-Invalidation-Removes-Entry")
    @Label("Property 14: After invalidation, next get should call Environment Service")
    void afterInvalidation_nextGetShouldCallEnvironmentService(
            @ForAll("validServiceNames") String serviceName,
            @ForAll("validZoneIds") String zoneId,
            @ForAll("optionalContextIds") String contextId,
            @ForAll("uuids") UUID providerId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("validEndpoints") String endpoint) {
        
        try (TestContext ctx = new TestContext(300)) {
            // Given: Provider is initially cached
            String cacheKey = buildCacheKey(serviceName, zoneId, contextId);
            ProviderRegistrationDTO provider = createProvider(
                providerId, environmentId, serviceName, zoneId, contextId, endpoint);
            
            when(ctx.valueOperations.get(cacheKey)).thenReturn(provider);
            
            // When: First get (cache hit)
            ctx.cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
            
            // Then: Environment Service should not be called
            verifyNoInteractions(ctx.environmentClient);
            
            // When: Invalidate cache
            when(ctx.redisTemplate.delete(cacheKey)).thenReturn(true);
            ctx.cache.invalidate("d1", "s1", serviceName, zoneId, contextId);
            
            // And: Second get (cache miss after invalidation)
            when(ctx.valueOperations.get(cacheKey)).thenReturn(null);
            when(ctx.environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
                .thenReturn(Optional.of(provider));
            
            ctx.cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
            
            // Then: Environment Service should be called after invalidation
            verify(ctx.environmentClient).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        }
    }
    
    // ========== Test Data Generators ==========
    
    @Provide
    Arbitrary<String> validServiceNames() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> validZoneIds() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> optionalContextIds() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(50)
            .injectNull(0.3); // 30% chance of null
    }
    
    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(() -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<String> validEndpoints() {
        return Arbitraries.of(
            "http://provider1.example.com",
            "http://provider2.example.com",
            "https://provider.example.com:8080",
            "http://localhost:8080",
            "https://api.provider.com/v1"
        );
    }
    
    @Provide
    Arbitrary<Integer> numberOfCalls() {
        return Arbitraries.integers().between(2, 10);
    }
    
    @Provide
    Arbitrary<Long> ttlSeconds() {
        return Arbitraries.longs().between(60, 3600);
    }
    
    // ========== Helper Methods ==========
    
    private String buildCacheKey(String serviceName, String zoneId, String contextId) {
        // Mirrors ProviderRegistryCache's tenant-isolated key format with fixed test domain/solution.
        return String.format("provider:d1:s1:%s:%s:%s",
            serviceName,
            zoneId,
            contextId != null ? contextId : "default");
    }
    
    private ProviderRegistrationDTO createProvider(
            UUID providerId, UUID environmentId, String serviceName, 
            String zoneId, String contextId, String endpoint) {
        return new ProviderRegistrationDTO(
                providerId,
                environmentId,
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(endpoint),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(
                new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))),
                new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.QUERY)))
            )),
                Instant.now()
        );
    }
}
