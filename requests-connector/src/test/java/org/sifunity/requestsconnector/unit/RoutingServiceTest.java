// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

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
import org.sifunity.broker.common.util.TracingUtils;
import org.sifunity.requestsconnector.cache.ProviderRegistryCache;
import org.sifunity.requestsconnector.service.ConsumerQueueOwnershipValidator;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.exception.NoProviderException;
import org.sifunity.requestsconnector.exception.UnsupportedPatternException;
import org.sifunity.requestsconnector.service.DelayedRequestHandler;
import org.sifunity.requestsconnector.service.ImmediateRequestHandler;
import org.sifunity.requestsconnector.service.RoutingService;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Unit tests for RoutingService.
 * 
 * <p>These tests validate specific routing scenarios and edge cases using Mockito
 * to mock dependencies. They complement the property-based tests by testing concrete
 * examples and error conditions.</p>
 */
@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {
    
    @Mock
    private ProviderRegistryCache providerCache;
    
    @Mock
    private ImmediateRequestHandler immediateHandler;
    
    @Mock
    private DelayedRequestHandler delayedHandler;
    
    @Mock
    private TracingUtils tracingUtils;
    
    @Mock
    private ConsumerQueueOwnershipValidator queueOwnershipValidator;
    
    private RoutingService routingService;
    
    private UUID environmentId;
    private String environmentName;
    private String serviceName;
    private String zoneId;
    private String contextId;
    
    @BeforeEach
    void setUp() {
        routingService = new RoutingService(providerCache, immediateHandler, delayedHandler, tracingUtils,
                queueOwnershipValidator);
        
        // Configure TracingUtils mock to execute the operation directly
        when(tracingUtils.traceOperation(anyString(), anyMap(), any(java.util.function.Supplier.class)))
            .thenAnswer(invocation -> {
                java.util.function.Supplier<?> operation = invocation.getArgument(2);
                return operation.get();
            });
        
        environmentId = UUID.randomUUID();
        environmentName = "Test Environment";
        serviceName = "StudentPersonal";
        zoneId = "production";
        contextId = "default";
    }
    
    private final String domainId = "test-domain";
    private final String solutionId = "test-solution";
    
    /**
     * Test IMMEDIATE routing decision with successful provider lookup and routing.
     * 
     * <p>Validates that when a provider is found and supports IMMEDIATE pattern,
     * the request is correctly routed to the ImmediateRequestHandler.</p>
     * 
     * <p><strong>Requirements:</strong> 1.1, 1.5, 2.1, 2.2</p>
     */
    @Test
    void testImmediateRoutingDecision_Success() {
        // Given: Provider that supports IMMEDIATE pattern
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                environmentId,
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/api"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );
        
        when(providerCache.getProvider(domainId, solutionId, serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        RequestResponseDTO expectedResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .payload(new Object())
            .build();
        
        when(immediateHandler.handleRequest(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedResponse);
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        // When: Routing IMMEDIATE request
        RequestResponseDTO response = routingService.routeImmediateRequest(
            environmentId, environmentName, domainId, solutionId, zoneId, contextId, request, Collections.emptyMap());
        
        // Then: Provider cache should be queried
        verify(providerCache).getProvider(domainId, solutionId, serviceName, zoneId, contextId);
        
        // And: ImmediateRequestHandler should be called with correct parameters
        verify(immediateHandler).handleRequest(
            eq(environmentId),
            eq(environmentName),
            eq(domainId),
            eq(solutionId),
            eq(provider),
            eq(request),
            eq(Collections.emptyMap())
        );
        
        // And: Response should be returned
        assertThat(response).isEqualTo(expectedResponse);
    }
    
    /**
     * Test DELAYED to DELAYED routing decision.
     * 
     * <p>Validates that when a provider supports DELAYED pattern, the DELAYED_to_DELAYED
     * sub-pattern is used and the request is routed to DelayedRequestHandler.</p>
     * 
     * <p><strong>Requirements:</strong> 3.1, 3.2, 3.3, 3.5</p>
     */
    @Test
    void testDelayedToDelayedRoutingDecision() {
        // Given: Provider that supports DELAYED pattern
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                environmentId,
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/api"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY))))),
                Instant.now()
        );
        
        when(providerCache.getProvider(domainId, solutionId, serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        UUID queueId = UUID.randomUUID();
        RequestResponseDTO expectedResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(queueId)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();
        
        when(delayedHandler.handleDelayedToDelayed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedResponse);
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        // When: Routing DELAYED request
        RequestResponseDTO response = routingService.routeDelayedRequest(
            environmentId, environmentName, domainId, solutionId, zoneId, contextId, queueId, request, Collections.emptyMap(), "POST", "/StudentPersonal", null);
        
        // Then: Provider cache should be queried
        verify(providerCache).getProvider(domainId, solutionId, serviceName, zoneId, contextId);
        
        // And: DelayedRequestHandler.handleDelayedToDelayed should be called
        verify(delayedHandler).handleDelayedToDelayed(
            eq(environmentId),
            eq(environmentName),
            eq(domainId),
            eq(solutionId),
            eq(provider),
            eq(queueId),
            eq(request),
            eq(Collections.emptyMap()),
            eq("/StudentPersonal"),
            any()
        );
        
        // And: handleDelayedToImmediate should NOT be called
        verify(delayedHandler, never()).handleDelayedToImmediate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        
        // And: Response should be returned
        assertThat(response).isEqualTo(expectedResponse);
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
    }
    
    /**
     * Test DELAYED to IMMEDIATE routing decision.
     * 
     * <p>Validates that when a provider supports only IMMEDIATE pattern, the DELAYED_to_IMMEDIATE
     * sub-pattern is used and the request is routed to DelayedRequestHandler.</p>
     * 
     * <p><strong>Requirements:</strong> 3.1, 3.2, 3.3, 3.5</p>
     */
    @Test
    void testDelayedToImmediateRoutingDecision() {
        // Given: Provider that supports only IMMEDIATE pattern
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                environmentId,
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/api"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );
        
        when(providerCache.getProvider(domainId, solutionId, serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        UUID queueId = UUID.randomUUID();
        RequestResponseDTO expectedResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(queueId)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();
        
        when(delayedHandler.handleDelayedToImmediate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedResponse);
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        // When: Routing DELAYED request
        RequestResponseDTO response = routingService.routeDelayedRequest(
            environmentId, environmentName, domainId, solutionId, zoneId, contextId, queueId, request, Collections.emptyMap(), "POST", "/StudentPersonal", null);
        
        // Then: Provider cache should be queried
        verify(providerCache).getProvider(domainId, solutionId, serviceName, zoneId, contextId);
        
        // And: DelayedRequestHandler.handleDelayedToImmediate should be called
        verify(delayedHandler).handleDelayedToImmediate(
            eq(environmentId),
            eq(environmentName),
            eq(domainId),
            eq(solutionId),
            eq(provider),
            eq(queueId),
            eq(request),
            eq(Collections.emptyMap()),
            eq("POST"),
            eq("/StudentPersonal"),
            any()
        );
        
        // And: handleDelayedToDelayed should NOT be called
        verify(delayedHandler, never()).handleDelayedToDelayed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        
        // And: Response should be returned
        assertThat(response).isEqualTo(expectedResponse);
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
    }
    
    /**
     * Test no provider error for IMMEDIATE request.
     * 
     * <p>Validates that when no provider is found in the registry, a NoProviderException
     * is thrown with the correct error message.</p>
     * 
     * <p><strong>Requirements:</strong> 1.5, 2.3</p>
     */
    @Test
    void testNoProviderError_ImmediateRequest() {
        // Given: No provider registered
        when(providerCache.getProvider(domainId, solutionId, serviceName, zoneId, contextId))
            .thenReturn(Optional.empty());
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        // When/Then: Routing should throw NoProviderException
        assertThatThrownBy(() -> routingService.routeImmediateRequest(
                environmentId, environmentName, domainId, solutionId, zoneId, contextId, request, Collections.emptyMap()))
            .isInstanceOf(NoProviderException.class)
            .hasMessageContaining("No provider registered")
            .hasMessageContaining(serviceName)
            .hasMessageContaining(zoneId);
        
        // And: Provider cache should be queried
        verify(providerCache).getProvider(domainId, solutionId, serviceName, zoneId, contextId);
        
        // And: Handler should not be called
        verifyNoInteractions(immediateHandler);
    }
    
    /**
     * Test no provider error for DELAYED request.
     * 
     * <p>Validates that when no provider is found in the registry for a DELAYED request,
     * a NoProviderException is thrown.</p>
     * 
     * <p><strong>Requirements:</strong> 1.5, 2.3</p>
     */
    @Test
    void testNoProviderError_DelayedRequest() {
        // Given: No provider registered
        when(providerCache.getProvider(domainId, solutionId, serviceName, zoneId, contextId))
            .thenReturn(Optional.empty());
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        UUID queueId = UUID.randomUUID();
        
        // When/Then: Routing should throw NoProviderException
        assertThatThrownBy(() -> routingService.routeDelayedRequest(
                environmentId, environmentName, domainId, solutionId, zoneId, contextId, queueId, request, Collections.emptyMap(), "POST", "/StudentPersonal", null))
            .isInstanceOf(NoProviderException.class)
            .hasMessageContaining("No provider registered")
            .hasMessageContaining(serviceName)
            .hasMessageContaining(zoneId);
        
        // And: Provider cache should be queried
        verify(providerCache).getProvider(domainId, solutionId, serviceName, zoneId, contextId);
        
        // And: Handler should not be called
        verifyNoInteractions(delayedHandler);
    }
    
    /**
     * Test unsupported pattern error for IMMEDIATE request.
     * 
     * <p>Validates that when a provider doesn't support IMMEDIATE pattern,
     * an UnsupportedPatternException is thrown.</p>
     * 
     * <p><strong>Requirements:</strong> 3.4</p>
     */
    @Test
    void testUnsupportedPatternError_ImmediateRequest() {
        // Given: Provider that doesn't support IMMEDIATE pattern
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                environmentId,
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/api"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of()),
                // Empty - no IMMEDIATE or DELAYED support
            Instant.now()
        );
        
        when(providerCache.getProvider(domainId, solutionId, serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        // When/Then: Routing should throw UnsupportedPatternException
        assertThatThrownBy(() -> routingService.routeImmediateRequest(
                environmentId, environmentName, domainId, solutionId, zoneId, contextId, request, Collections.emptyMap()))
            .isInstanceOf(UnsupportedPatternException.class)
            .hasMessageContaining("Provider does not support IMMEDIATE pattern");
        
        // And: Provider cache should be queried
        verify(providerCache).getProvider(domainId, solutionId, serviceName, zoneId, contextId);
        
        // And: Handler should not be called
        verifyNoInteractions(immediateHandler);
    }
    
    /**
     * Test unsupported pattern error for DELAYED request.
     * 
     * <p>Validates that when a provider supports neither IMMEDIATE nor DELAYED patterns,
     * an UnsupportedPatternException is thrown.</p>
     * 
     * <p><strong>Requirements:</strong> 3.4</p>
     */
    @Test
    void testUnsupportedPatternError_DelayedRequest() {
        // Given: Provider that supports neither IMMEDIATE nor DELAYED
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                environmentId,
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/api"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of()),
                // Empty - no IMMEDIATE or DELAYED support
            Instant.now()
        );
        
        when(providerCache.getProvider(domainId, solutionId, serviceName, zoneId, contextId))
            .thenReturn(Optional.of(provider));
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        UUID queueId = UUID.randomUUID();
        
        // When/Then: Routing should throw UnsupportedPatternException
        assertThatThrownBy(() -> routingService.routeDelayedRequest(
                environmentId, environmentName, domainId, solutionId, zoneId, contextId, queueId, request, Collections.emptyMap(), "POST", "/StudentPersonal", null))
            .isInstanceOf(UnsupportedPatternException.class)
            .hasMessageContaining("Provider does not support request patterns");
        
        // And: Provider cache should be queried
        verify(providerCache).getProvider(domainId, solutionId, serviceName, zoneId, contextId);
        
        // And: Handler should not be called
        verifyNoInteractions(delayedHandler);
    }
    
    /**
     * Test routing with null contextId.
     * 
     * <p>Validates that routing works correctly when contextId is null.</p>
     */
    @Test
    void testRoutingWithNullContextId() {
        // Given: Provider with null contextId
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                environmentId,
                serviceName,
                zoneId,
                null,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/api"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );
        
        when(providerCache.getProvider(domainId, solutionId, serviceName, zoneId, null))
            .thenReturn(Optional.of(provider));
        
        RequestResponseDTO expectedResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .build();
        
        when(immediateHandler.handleRequest(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedResponse);
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(new Object());
        
        // When: Routing with null contextId
        RequestResponseDTO response = routingService.routeImmediateRequest(
            environmentId, environmentName, domainId, solutionId, zoneId, null, request, Collections.emptyMap());
        
        // Then: Provider cache should be queried with null contextId
        verify(providerCache).getProvider(domainId, solutionId, serviceName, zoneId, null);
        
        // And: Handler should be called
        verify(immediateHandler).handleRequest(any(), any(), any(), any(), eq(provider), eq(request), any());
        
        // And: Response should be returned
        assertThat(response).isEqualTo(expectedResponse);
    }
}
