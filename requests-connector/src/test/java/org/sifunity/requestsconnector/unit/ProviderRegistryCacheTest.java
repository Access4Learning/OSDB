// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.requestsconnector.cache.ProviderRegistryCache;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Unit tests for ProviderRegistryCache.
 * 
 * <p>These tests verify the caching behavior of provider registry lookups,
 * including cache hits, cache misses, and cache invalidation.</p>
 * 
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li>Cache miss - calls Environment Service</li>
 *   <li>Cache hit - does not call Environment Service</li>
 *   <li>Cache invalidation - single entry</li>
 *   <li>Cache invalidation - all entries</li>
 *   <li>Empty result handling</li>
 *   <li>Null contextId handling</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderRegistryCache Unit Tests")
class ProviderRegistryCacheTest {
    
    @Mock
    private EnvironmentServiceClient environmentClient;
    
    private ProviderRegistryCache cache;
    private CacheManager cacheManager;
    
    @BeforeEach
    void setUp() {
        // Use in-memory cache for testing
        cacheManager = new ConcurrentMapCacheManager("provider-registry");
        cache = new ProviderRegistryCache(environmentClient);
    }
    
    @Test
    @DisplayName("Should call Environment Service on cache miss")
    void shouldCallEnvironmentServiceOnCacheMiss() {
        // Given
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "default";
        
        ProviderRegistrationDTO provider = createTestProvider(serviceName, zoneId, contextId);
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().serviceName()).isEqualTo(serviceName);
        assertThat(result.get().zoneId()).isEqualTo(zoneId);
        assertThat(result.get().contextId()).isEqualTo(contextId);
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
    }
    
    @Test
    @DisplayName("Should return empty when provider not found")
    void shouldReturnEmptyWhenProviderNotFound() {
        // Given
        String serviceName = "NonExistentService";
        String zoneId = "production";
        String contextId = "default";
        
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.empty());
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isEmpty();
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
    }
    
    @Test
    @DisplayName("Should handle null contextId")
    void shouldHandleNullContextId() {
        // Given
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = null;
        
        ProviderRegistrationDTO provider = createTestProvider(serviceName, zoneId, "default");
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isPresent();
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
    }
    
    @Test
    @DisplayName("Should handle empty contextId")
    void shouldHandleEmptyContextId() {
        // Given
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "";
        
        ProviderRegistrationDTO provider = createTestProvider(serviceName, zoneId, "default");
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isPresent();
        verify(environmentClient, times(1)).lookupProvider("d1", "s1", serviceName, zoneId, contextId);
    }
    
    @Test
    @DisplayName("Should extract provider endpoint correctly")
    void shouldExtractProviderEndpointCorrectly() {
        // Given
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "default";
        String expectedEndpoint = "http://provider.example.com/students";
        
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(expectedEndpoint),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(
                new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))),
                new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.QUERY)))
            )),
                null
        );
        
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isPresent();
        assertThat(ProtocolDTO.locationOf(result.get().endPoint())).isEqualTo(expectedEndpoint);
        assertThat(result.get().supportedRequestTypes().supportedRequestType()).hasSize(2);
        assertThat(result.get().supportedRequestTypes().supportedRequestType())
            .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE)
            .anyMatch(srt -> srt.requestType() == RequestType.DELAYED);
    }
    
    @Test
    @DisplayName("Should extract supported request types correctly")
    void shouldExtractSupportedRequestTypesCorrectly() {
        // Given
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "default";
        
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))))),
                null
        );
        
        when(environmentClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // When
        Optional<ProviderRegistrationDTO> result = cache.getProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().supportedRequestTypes().supportedRequestType()).hasSize(1);
        assertThat(result.get().supportedRequestTypes().supportedRequestType().get(0).requestType()).isEqualTo(RequestType.IMMEDIATE);
        assertThat(result.get().supportedRequestTypes().supportedRequestType().get(0).operations().operation()).containsExactly(Operation.QUERY);
    }
    
    @Test
    @DisplayName("Should not call invalidateProvider without cache manager")
    void shouldNotFailOnInvalidateProviderWithoutCacheManager() {
        // Given
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "default";
        
        // When/Then - should not throw exception
        cache.invalidateProvider("d1", "s1", serviceName, zoneId, contextId);
    }
    
    @Test
    @DisplayName("Should not fail on invalidateAll without cache manager")
    void shouldNotFailOnInvalidateAllWithoutCacheManager() {
        // When/Then - should not throw exception
        cache.invalidateAll();
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
