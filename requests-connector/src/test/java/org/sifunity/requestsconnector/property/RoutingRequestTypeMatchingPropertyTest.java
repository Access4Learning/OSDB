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
import org.sifunity.requestsconnector.exception.UnsupportedPatternException;
import org.sifunity.requestsconnector.service.ConsumerQueueOwnershipValidator;
import org.sifunity.requestsconnector.service.DelayedRequestHandler;
import org.sifunity.requestsconnector.service.ImmediateRequestHandler;
import org.sifunity.requestsconnector.service.RoutingService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Property-based test for Property 2: Routing accepts request iff provider supports matching request type.
 *
 * <p><strong>Property:</strong> For any provider registration with a random valid
 * supportedRequestTypes structure and for any incoming request type (IMMEDIATE or DELAYED),
 * the routing service SHALL accept the request if the provider's supportedRequestTypes
 * contains an entry with a matching requestType (with DELAYED requests falling back to
 * IMMEDIATE when no DELAYED entry exists), and SHALL throw UnsupportedPatternException
 * if no matching entry (and no fallback) is available.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.4</strong></p>
 */
public class RoutingRequestTypeMatchingPropertyTest {

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
        return new RoutingService(providerCache, immediateHandler, delayedHandler,
                new org.sifunity.broker.common.util.NoOpTracingUtils(),
                Mockito.mock(ConsumerQueueOwnershipValidator.class));
    }

    /**
     * Property 2: Routing accepts request if and only if provider supports matching request type
     *
     * <p><strong>Validates: Requirements 4.1, 4.2, 4.4</strong></p>
     */
    @Property(tries = 10)
    void routingAcceptsRequestIffProviderSupportsMatchingRequestType(
            @ForAll("validSupportedRequestTypes") List<SupportedRequestType> supportedRequestTypes,
            @ForAll("requestTypes") RequestType incomingRequestType) {

        ProviderRegistryCache providerCache = createProviderCache();
        ImmediateRequestHandler immediateHandler = createImmediateHandler();
        DelayedRequestHandler delayedHandler = createDelayedHandler();
        RoutingService routingService = createRoutingService(providerCache, immediateHandler, delayedHandler);

        String serviceName = "TestService";
        String zoneId = "test-zone";
        String contextId = "test-context";
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnv";

        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/api"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(supportedRequestTypes),
                Instant.now()
        );

        when(providerCache.getProvider(anyString(), anyString(), eq(serviceName), eq(zoneId), eq(contextId)))
                .thenReturn(Optional.of(provider));

        // Mock handler responses
        RequestResponseDTO mockResponse = RequestResponseDTO.builder()
                .messageId(UUID.randomUUID())
                .timestamp(Instant.now())
                .status("ACCEPTED")
                .build();

        when(immediateHandler.handleRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockResponse);
        when(delayedHandler.handleDelayedToDelayed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockResponse);
        when(delayedHandler.handleDelayedToImmediate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockResponse);

        boolean supportsImmediate = supportedRequestTypes.stream()
                .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE);
        boolean supportsDelayed = supportedRequestTypes.stream()
                .anyMatch(srt -> srt.requestType() == RequestType.DELAYED);

        if (incomingRequestType == RequestType.IMMEDIATE) {
            // IMMEDIATE request: accepted iff provider supports IMMEDIATE
            ImmediateRequestDTO request = new ImmediateRequestDTO();
            request.setServiceName(serviceName);
            request.setServiceType("OBJECT");
            request.setMessageType("REQUEST");
            request.setPayload(new Object());

            if (supportsImmediate) {
                RequestResponseDTO response = routingService.routeImmediateRequest(
                        environmentId, environmentName, TEST_DOMAIN_ID, TEST_SOLUTION_ID, zoneId, contextId, request, Collections.emptyMap());
                assertThat(response).isNotNull();
                verify(immediateHandler).handleRequest(any(), any(), any(), any(), any(), any(), any());
            } else {
                assertThatThrownBy(() -> routingService.routeImmediateRequest(
                        environmentId, environmentName, TEST_DOMAIN_ID, TEST_SOLUTION_ID, zoneId, contextId, request, Collections.emptyMap()))
                        .isInstanceOf(UnsupportedPatternException.class);
            }
        } else {
            // DELAYED request: accepted if provider supports DELAYED or IMMEDIATE (fallback)
            DelayedRequestDTO request = new DelayedRequestDTO();
            request.setServiceName(serviceName);
            request.setServiceType("OBJECT");
            request.setMessageType("REQUEST");
            request.setPayload(new Object());
            UUID queueId = UUID.randomUUID();

            if (supportsDelayed) {
                // Should use DELAYED_to_DELAYED sub-pattern
                RequestResponseDTO response = routingService.routeDelayedRequest(
                        environmentId, environmentName, "dom", "sol", zoneId, contextId,
                        queueId, request, Collections.emptyMap(), "POST", "/TestService", null);
                assertThat(response).isNotNull();
                verify(delayedHandler).handleDelayedToDelayed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            } else if (supportsImmediate) {
                // Should fall back to DELAYED_to_IMMEDIATE sub-pattern
                RequestResponseDTO response = routingService.routeDelayedRequest(
                        environmentId, environmentName, "dom", "sol", zoneId, contextId,
                        queueId, request, Collections.emptyMap(), "POST", "/TestService", null);
                assertThat(response).isNotNull();
                verify(delayedHandler).handleDelayedToImmediate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            } else {
                // No match and no fallback — should throw
                assertThatThrownBy(() -> routingService.routeDelayedRequest(
                        environmentId, environmentName, "dom", "sol", zoneId, contextId,
                        queueId, request, Collections.emptyMap(), "POST", "/TestService", null))
                        .isInstanceOf(UnsupportedPatternException.class);
            }
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<RequestType> requestTypes() {
        return Arbitraries.of(RequestType.values());
    }

    @Provide
    Arbitrary<List<SupportedRequestType>> validSupportedRequestTypes() {
        Arbitrary<Set<Operation>> ops = Arbitraries.of(Operation.values())
                .set()
                .ofMinSize(1)
                .ofMaxSize(4);

        Arbitrary<SupportedRequestType> immediateEntry = ops
                .map(o -> new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(o)));
        Arbitrary<SupportedRequestType> delayedEntry = ops
                .map(o -> new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(o)));

        // Generate: IMMEDIATE only, DELAYED only, both, or empty (no support)
        return Arbitraries.oneOf(
                immediateEntry.map(List::of),
                delayedEntry.map(List::of),
                Combinators.combine(immediateEntry, delayedEntry).as(List::of),
                Arbitraries.just(List.of())
        );
    }
}
