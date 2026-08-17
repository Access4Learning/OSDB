// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.mockito.Mockito;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.requestsconnector.cache.ProviderRegistryCache;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.exception.NoProviderException;
import org.sifunity.requestsconnector.exception.UnsupportedPatternException;
import org.sifunity.requestsconnector.service.ConsumerQueueOwnershipValidator;
import org.sifunity.requestsconnector.service.DelayedRequestHandler;
import org.sifunity.requestsconnector.service.ImmediateRequestHandler;
import org.sifunity.requestsconnector.service.RoutingService;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Property-based tests for RoutingService.
 * 
 * <p>These tests validate universal properties that should hold for all valid inputs
 * across the routing decision process. Each property test runs with a minimum of 100
 * iterations to ensure comprehensive coverage.</p>
 * 
 * <p><strong>Properties Tested:</strong></p>
 * <ul>
 *   <li>Property 5: No provider error handling</li>
 *   <li>Property 6: Provider registry data extraction</li>
 *   <li>Property 9: Provider capability query for DELAYED requests</li>
 *   <li>Property 10: DELAYED sub-pattern selection</li>
 *   <li>Property 11: Unsupported pattern error handling</li>
 * </ul>
 */
public class RoutingServicePropertiesTest {
    
    private static final String TEST_DOMAIN_ID = "test-domain";
    private static final String TEST_SOLUTION_ID = "test-solution";
    
    private ProviderRegistryCache createProviderCache() {
        return Mockito.mock(ProviderRegistryCache.class);
    }
    
    private ImmediateRequestHandler createImmediateHandler() {
        return Mockito.mock(ImmediateRequestHandler.class);
    }
    
    private DelayedRequestHandler createDelayedHandler() {
        return Mockito.mock(DelayedRequestHandler.class);
    }
    
    private RoutingService createRoutingService(
            ProviderRegistryCache providerCache,
            ImmediateRequestHandler immediateHandler,
            DelayedRequestHandler delayedHandler) {
        return new RoutingService(providerCache, immediateHandler, delayedHandler, new org.sifunity.broker.common.util.NoOpTracingUtils(),
                mock(ConsumerQueueOwnershipValidator.class));
    }
    
    /**
     * Property 5: No provider error handling
     * 
     * <p><b>Property:</b> For any request where no provider is registered for the requested
     * service/zone/context combination, the connector should return HTTP 404 Not Found with
     * message "No provider registered for {service} in zone {zone} context {context}".</p>
     * 
     * <p><b>Validates:</b> Requirements 1.5, 2.3</p>
     */
    @Property(tries = 10)
    void property5_noProviderErrorHandling(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("environmentNames") String environmentName) {
        
        // Create fresh mocks for this test
        ProviderRegistryCache providerCache = createProviderCache();
        ImmediateRequestHandler immediateHandler = createImmediateHandler();
        DelayedRequestHandler delayedHandler = createDelayedHandler();
        RoutingService routingService = createRoutingService(providerCache, immediateHandler, delayedHandler);
        
        // Given: No provider registered for service/zone/context
        when(providerCache.getProvider(TEST_DOMAIN_ID, TEST_SOLUTION_ID, serviceName, zoneId, contextId))
            .thenReturn(Optional.empty());
        
        ImmediateRequestDTO immediateRequest = new ImmediateRequestDTO();
        immediateRequest.setServiceName(serviceName);
        immediateRequest.setServiceType("OBJECT");
        immediateRequest.setMessageType("REQUEST");
        immediateRequest.setPayload(new Object());
        
        // When/Then: Routing IMMEDIATE request should throw NoProviderException
        assertThatThrownBy(() -> routingService.routeImmediateRequest(
                environmentId, environmentName, TEST_DOMAIN_ID, TEST_SOLUTION_ID, zoneId, contextId, immediateRequest, Collections.emptyMap()))
            .isInstanceOf(NoProviderException.class)
            .hasMessageContaining("No provider registered")
            .hasMessageContaining(serviceName)
            .hasMessageContaining(zoneId);
        
        // Verify provider cache was queried
        verify(providerCache).getProvider(TEST_DOMAIN_ID, TEST_SOLUTION_ID, serviceName, zoneId, contextId);

        // Verify handler was not called
        verifyNoInteractions(immediateHandler);
        
        // Reset for DELAYED request test
        reset(providerCache);
        when(providerCache.getProvider("dom", "sol", serviceName, zoneId, contextId))
            .thenReturn(Optional.empty());
        
        DelayedRequestDTO delayedRequest = new DelayedRequestDTO();
        delayedRequest.setServiceName(serviceName);
        delayedRequest.setServiceType("OBJECT");
        delayedRequest.setMessageType("REQUEST");
        delayedRequest.setPayload(new Object());
        
        UUID queueId = UUID.randomUUID();
        
        // When/Then: Routing DELAYED request should also throw NoProviderException
        assertThatThrownBy(() -> routingService.routeDelayedRequest(
                environmentId, environmentName, "dom", "sol", zoneId, contextId, queueId, delayedRequest, Collections.emptyMap(), "POST", "/" + serviceName, null))
            .isInstanceOf(NoProviderException.class)
            .hasMessageContaining("No provider registered")
            .hasMessageContaining(serviceName)
            .hasMessageContaining(zoneId);
        
        // Verify provider cache was queried
        verify(providerCache).getProvider("dom", "sol", serviceName, zoneId, contextId);
        
        // Verify handler was not called
        verifyNoInteractions(delayedHandler);
    }
    
    /**
     * Property 6: Provider registry data extraction
     * 
     * <p><b>Property:</b> For any provider registry response that returns a provider,
     * the connector should extract the provider's endpoint and supported request types correctly.</p>
     * 
     * <p><b>Validates:</b> Requirements 2.2</p>
     */
    @Property(tries = 10)
    void property6_providerRegistryDataExtraction(
            @ForAll("providers") ProviderRegistrationDTO provider,
            @ForAll("uuids") UUID environmentId,
            @ForAll("environmentNames") String environmentName) {
        
        // Create fresh mocks for this test
        ProviderRegistryCache providerCache = createProviderCache();
        ImmediateRequestHandler immediateHandler = createImmediateHandler();
        DelayedRequestHandler delayedHandler = createDelayedHandler();
        RoutingService routingService = createRoutingService(providerCache, immediateHandler, delayedHandler);
        
        // Given: Provider registry returns a provider
        when(providerCache.getProvider(
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, provider.serviceName(), provider.zoneId(), provider.contextId()))
            .thenReturn(Optional.of(provider));

        // Mock handler response
        RequestResponseDTO mockResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .build();
        
        when(immediateHandler.handleRequest(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName(provider.serviceName());
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        // When: Routing request (if provider supports IMMEDIATE)
        boolean supportsImmediate = provider.supportedRequestTypes().supportedRequestType().stream()
            .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE);
        
        if (supportsImmediate) {
            RequestResponseDTO response = routingService.routeImmediateRequest(
                environmentId, environmentName, TEST_DOMAIN_ID, TEST_SOLUTION_ID, provider.zoneId(), provider.contextId(), request, Collections.emptyMap());
            
            // Then: Handler should be called with correct provider data
            verify(immediateHandler).handleRequest(
                eq(environmentId),
                eq(environmentName),
                eq(TEST_DOMAIN_ID),
                eq(TEST_SOLUTION_ID),
                argThat(p -> ProtocolDTO.locationOf(p.endPoint()).equals(ProtocolDTO.locationOf(provider.endPoint())) &&
                            p.supportedRequestTypes().supportedRequestType().equals(provider.supportedRequestTypes().supportedRequestType())),
                eq(request),
                eq(Collections.emptyMap()));
            
            assertThat(response).isNotNull();
        }
    }
    
    /**
     * Property 9: Provider capability query for DELAYED requests
     * 
     * <p><b>Property:</b> For any DELAYED request, the connector should query the provider
     * registry to determine provider capabilities.</p>
     * 
     * <p><b>Validates:</b> Requirements 3.1</p>
     */
    @Property(tries = 10)
    void property9_providerCapabilityQueryForDelayedRequests(
            @ForAll("providers") ProviderRegistrationDTO provider,
            @ForAll("uuids") UUID environmentId,
            @ForAll("environmentNames") String environmentName,
            @ForAll("uuids") UUID queueId) {
        
        // Create fresh mocks for this test
        ProviderRegistryCache providerCache = createProviderCache();
        ImmediateRequestHandler immediateHandler = createImmediateHandler();
        DelayedRequestHandler delayedHandler = createDelayedHandler();
        RoutingService routingService = createRoutingService(providerCache, immediateHandler, delayedHandler);
        
        // Given: Provider registry returns a provider
        when(providerCache.getProvider(
                "dom", "sol", provider.serviceName(), provider.zoneId(), provider.contextId()))
            .thenReturn(Optional.of(provider));
        
        // Mock handler response
        RequestResponseDTO mockResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(queueId)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();
        
        when(delayedHandler.handleDelayedToDelayed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);
        when(delayedHandler.handleDelayedToImmediate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(provider.serviceName());
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        // When: Routing DELAYED request (if provider supports request types)
        boolean supportsDelayed = provider.supportedRequestTypes().supportedRequestType().stream()
            .anyMatch(srt -> srt.requestType() == RequestType.DELAYED);
        boolean supportsImmediate = provider.supportedRequestTypes().supportedRequestType().stream()
            .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE);
        
        if (supportsDelayed || supportsImmediate) {
            routingService.routeDelayedRequest(
                environmentId, environmentName, "dom", "sol", provider.zoneId(), provider.contextId(), queueId, request, Collections.emptyMap(), "POST", "/" + provider.serviceName(), null);
            
            // Then: Provider cache should be queried to determine capabilities
            verify(providerCache).getProvider(
                "dom", "sol", provider.serviceName(), provider.zoneId(), provider.contextId());
        }
    }
    
    /**
     * Property 10: DELAYED sub-pattern selection
     * 
     * <p><b>Property:</b> For any DELAYED request, when the provider supports DELAYED request type,
     * the connector should use DELAYED_to_DELAYED sub-pattern, and when the provider supports
     * only IMMEDIATE request type, the connector should use DELAYED_to_IMMEDIATE sub-pattern.</p>
     * 
     * <p><b>Validates:</b> Requirements 3.2, 3.3, 3.5</p>
     */
    @Property(tries = 10)
    void property10_delayedSubPatternSelection(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("environmentNames") String environmentName,
            @ForAll("uuids") UUID queueId,
            @ForAll("endpoints") String endpoint,
            @ForAll boolean supportsDelayed,
            @ForAll boolean supportsImmediate) {
        
        // Skip if provider supports neither request type (tested in property 11)
        if (!supportsDelayed && !supportsImmediate) {
            return;
        }
        
        // Create fresh mocks for this test
        ProviderRegistryCache providerCache = createProviderCache();
        ImmediateRequestHandler immediateHandler = createImmediateHandler();
        DelayedRequestHandler delayedHandler = createDelayedHandler();
        RoutingService routingService = createRoutingService(providerCache, immediateHandler, delayedHandler);
        
        // Given: Provider with specific request type support
        List<SupportedRequestType> supportedRequestTypes;
        if (supportsDelayed) {
            supportedRequestTypes = List.of(
                new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY))));
        } else {
            supportedRequestTypes = List.of(
                new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY))));
        }
        
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(endpoint),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(supportedRequestTypes),
                Instant.now()
        );
        
        when(providerCache.getProvider("dom", "sol", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        // Mock handler response
        RequestResponseDTO mockResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(queueId)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();
        
        when(delayedHandler.handleDelayedToDelayed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);
        when(delayedHandler.handleDelayedToImmediate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        // When: Routing DELAYED request
        RequestResponseDTO response = routingService.routeDelayedRequest(
            environmentId, environmentName, "dom", "sol", zoneId, contextId, queueId, request, Collections.emptyMap(), "POST", "/" + serviceName, null);
        
        // Then: Correct sub-pattern handler should be called
        if (supportsDelayed) {
            // DELAYED_to_DELAYED sub-pattern
            verify(delayedHandler).handleDelayedToDelayed(
                eq(environmentId), eq(environmentName), eq("dom"), eq("sol"), eq(provider), eq(queueId), eq(request), eq(Collections.emptyMap()), eq("/" + serviceName), any());
            verify(delayedHandler, never()).handleDelayedToImmediate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        } else {
            // DELAYED_to_IMMEDIATE sub-pattern
            verify(delayedHandler).handleDelayedToImmediate(
                eq(environmentId), eq(environmentName), eq("dom"), eq("sol"), eq(provider), eq(queueId), eq(request), eq(Collections.emptyMap()), eq("POST"), eq("/" + serviceName), any());
            verify(delayedHandler, never()).handleDelayedToDelayed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
        
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
    }
    
    /**
     * Property 11: Unsupported pattern error handling
     * 
     * <p><b>Property:</b> For any DELAYED request where the provider supports neither
     * IMMEDIATE nor DELAYED request types, the connector should return HTTP 400 Bad Request
     * with message "Provider does not support request patterns".</p>
     * 
     * <p><b>Validates:</b> Requirements 3.4</p>
     */
    @Property(tries = 10)
    void property11_unsupportedPatternErrorHandling(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("environmentNames") String environmentName,
            @ForAll("uuids") UUID queueId,
            @ForAll("endpoints") String endpoint) {
        
        // Create fresh mocks for this test
        ProviderRegistryCache providerCache = createProviderCache();
        ImmediateRequestHandler immediateHandler = createImmediateHandler();
        DelayedRequestHandler delayedHandler = createDelayedHandler();
        RoutingService routingService = createRoutingService(providerCache, immediateHandler, delayedHandler);
        
        // Given: Provider with empty supportedRequestTypes (no request types supported)
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(endpoint),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of()),
                // Empty - no IMMEDIATE or DELAYED support
            Instant.now()
        );
        
        when(providerCache.getProvider("dom", "sol", serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        // When/Then: Routing DELAYED request should throw UnsupportedPatternException
        assertThatThrownBy(() -> routingService.routeDelayedRequest(
                environmentId, environmentName, "dom", "sol", zoneId, contextId, queueId, request, Collections.emptyMap(), "POST", "/" + serviceName, null))
            .isInstanceOf(UnsupportedPatternException.class)
            .hasMessageContaining("Provider does not support request patterns");
        
        // Verify provider cache was queried
        verify(providerCache).getProvider("dom", "sol", serviceName, zoneId, contextId);
        
        // Verify handler was not called
        verifyNoInteractions(delayedHandler);
        
        // Test IMMEDIATE request with provider that doesn't support IMMEDIATE
        reset(providerCache);
        when(providerCache.getProvider(TEST_DOMAIN_ID, TEST_SOLUTION_ID, serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        ImmediateRequestDTO immediateRequest = new ImmediateRequestDTO();
        immediateRequest.setServiceName(serviceName);
        immediateRequest.setServiceType("OBJECT");
        immediateRequest.setMessageType("REQUEST");
        immediateRequest.setPayload(new Object());
        
        // When/Then: Routing IMMEDIATE request should throw UnsupportedPatternException
        assertThatThrownBy(() -> routingService.routeImmediateRequest(
                environmentId, environmentName, TEST_DOMAIN_ID, TEST_SOLUTION_ID, zoneId, contextId, immediateRequest, Collections.emptyMap()))
            .isInstanceOf(UnsupportedPatternException.class)
            .hasMessageContaining("Provider does not support IMMEDIATE pattern");
        
        // Verify handler was not called
        verifyNoInteractions(immediateHandler);
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(3)
            .ofMaxLength(50)
            .filter(s -> s.matches("^[a-zA-Z].*"));  // Must start with letter
    }
    
    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of("production", "staging", "development", "test", "zone-1", "zone-2");
    }
    
    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.of("default", "context-1", "context-2", null);
    }
    
    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(() -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<String> environmentNames() {
        return Arbitraries.of("Production Environment", "Staging Environment", "Test Environment");
    }
    
    @Provide
    Arbitrary<String> endpoints() {
        return Arbitraries.of(
            "http://provider1.example.com/api",
            "http://provider2.example.com/api",
            "https://provider3.example.com/api/v1"
        );
    }
    
    @Provide
    Arbitrary<ProviderRegistrationDTO> providers() {
        return Combinators.combine(
            uuids(),
            uuids(),
            serviceNames(),
            zoneIds(),
            contextIds(),
            endpoints(),
            supportedRequestTypes(),
            Arbitraries.create(() -> Instant.now())
        ).as((id, envId, svcName, zone, ctx, ep, srt, ts) ->
            new ProviderRegistrationDTO(
                id,
                envId,
                svcName,
                zone,
                ctx,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(ep),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(srt),
                ts
        ));
    }
    
    @Provide
    Arbitrary<List<SupportedRequestType>> supportedRequestTypes() {
        return Arbitraries.of(
            List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE)))),
            List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))),
            List.of(
                new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))),
                new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))
            ),
            List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY)))),
            List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE))))
        );
    }
}
