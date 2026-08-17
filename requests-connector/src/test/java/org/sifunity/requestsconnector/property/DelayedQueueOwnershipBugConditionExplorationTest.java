// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.requestsconnector.cache.ProviderRegistryCache;
import org.sifunity.requestsconnector.cache.QueueOwnerCache;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.exception.UnknownQueueException;
import org.sifunity.requestsconnector.service.ConsumerQueueOwnershipValidator;
import org.sifunity.requestsconnector.service.DelayedRequestHandler;
import org.sifunity.requestsconnector.service.ImmediateRequestHandler;
import org.sifunity.requestsconnector.service.RoutingService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug Condition Exploration Test: Cross-Tenant Queue Ownership Hijack (requests-connector).
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>After the fix, the {@link ConsumerQueueOwnershipValidator} throws
 * {@link UnknownQueueException} when the queue owner doesn't match the caller.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.4, 2.6</strong></p>
 */
@Tag("delayed-queue-ownership-fix")
@Tag("bug-condition-exploration")
class DelayedQueueOwnershipBugConditionExplorationTest {

    /**
     * Property 1: A DELAYED request naming a queue owned by a DIFFERENT environment
     * must be rejected with UnknownQueueException.
     */
    @Property(tries = 20)
    @Label("Bug Condition: DELAYED request with foreign queueId throws UnknownQueueException")
    void delayedRequestWithForeignQueueMustBeRejected(
            @ForAll("callerEnvironmentIds") UUID callerEnvironmentId,
            @ForAll("queueIds") UUID queueId,
            @ForAll("queueOwnerIds") UUID queueOwnerId,
            @ForAll("serviceNames") String serviceName) {

        // Precondition: owner differs from caller
        org.junit.jupiter.api.Assumptions.assumeTrue(!queueOwnerId.equals(callerEnvironmentId));

        // Mocks
        ProviderRegistryCache providerCache = mock(ProviderRegistryCache.class);
        ImmediateRequestHandler immediateHandler = mock(ImmediateRequestHandler.class);
        DelayedRequestHandler delayedHandler = mock(DelayedRequestHandler.class);
        TracingUtilsBase tracingUtils = mock(TracingUtilsBase.class);
        QueueOwnerCache queueOwnerCache = mock(QueueOwnerCache.class);

        // TracingUtils pass-through
        when(tracingUtils.traceOperation(anyString(), any(Map.class), any(java.util.function.Supplier.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        // Queue owner lookup returns the OTHER environment (not the caller)
        when(queueOwnerCache.getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.of(queueOwnerId));

        ConsumerQueueOwnershipValidator validator = new ConsumerQueueOwnershipValidator(queueOwnerCache);

        RoutingService routingService = new RoutingService(
                providerCache, immediateHandler, delayedHandler, tracingUtils, validator);

        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");

        // ACT + ASSERT: must throw UnknownQueueException (mapped to 404)
        assertThatThrownBy(() -> routingService.routeDelayedRequest(
                callerEnvironmentId, "test-env",
                "domain1", "solution1",
                "test-zone", "DEFAULT",
                queueId, request,
                Map.of(), "GET", "/" + serviceName, null))
                .isInstanceOf(UnknownQueueException.class)
                .hasMessageContaining("does not exist");

        // Verify: provider lookup and delayed handler are NEVER reached
        verifyNoInteractions(providerCache);
        verifyNoInteractions(delayedHandler);
    }

    /**
     * Property 1b: Queue not found (owner lookup returns empty) → rejected.
     */
    @Property(tries = 20)
    @Label("Bug Condition: DELAYED request with non-existent queue throws UnknownQueueException")
    void delayedRequestWithNonExistentQueueMustBeRejected(
            @ForAll("callerEnvironmentIds") UUID callerEnvironmentId,
            @ForAll("queueIds") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        ProviderRegistryCache providerCache = mock(ProviderRegistryCache.class);
        ImmediateRequestHandler immediateHandler = mock(ImmediateRequestHandler.class);
        DelayedRequestHandler delayedHandler = mock(DelayedRequestHandler.class);
        TracingUtilsBase tracingUtils = mock(TracingUtilsBase.class);
        QueueOwnerCache queueOwnerCache = mock(QueueOwnerCache.class);

        when(tracingUtils.traceOperation(anyString(), any(Map.class), any(java.util.function.Supplier.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        // Queue does not exist — lookup returns empty
        when(queueOwnerCache.getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.empty());

        ConsumerQueueOwnershipValidator validator = new ConsumerQueueOwnershipValidator(queueOwnerCache);

        RoutingService routingService = new RoutingService(
                providerCache, immediateHandler, delayedHandler, tracingUtils, validator);

        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");

        assertThatThrownBy(() -> routingService.routeDelayedRequest(
                callerEnvironmentId, "test-env",
                "domain1", "solution1",
                "test-zone", "DEFAULT",
                queueId, request,
                Map.of(), "GET", "/" + serviceName, null))
                .isInstanceOf(UnknownQueueException.class);

        verifyNoInteractions(providerCache);
        verifyNoInteractions(delayedHandler);
    }

    /**
     * Property 1c: Admin-proxy case — even with broker-services fingerprint,
     * the ownership check is enforced. No bypass.
     */
    @Property(tries = 20)
    @Label("Bug Condition: Admin-proxied DELAYED request with foreign queueId is rejected (no bypass)")
    void adminProxiedWithForeignQueueIsRejected(
            @ForAll("callerEnvironmentIds") UUID proxiedAsEnv,
            @ForAll("queueIds") UUID queueId,
            @ForAll("queueOwnerIds") UUID actualOwner,
            @ForAll("serviceNames") String serviceName) {

        org.junit.jupiter.api.Assumptions.assumeTrue(!actualOwner.equals(proxiedAsEnv));

        ProviderRegistryCache providerCache = mock(ProviderRegistryCache.class);
        ImmediateRequestHandler immediateHandler = mock(ImmediateRequestHandler.class);
        DelayedRequestHandler delayedHandler = mock(DelayedRequestHandler.class);
        TracingUtilsBase tracingUtils = mock(TracingUtilsBase.class);
        QueueOwnerCache queueOwnerCache = mock(QueueOwnerCache.class);

        when(tracingUtils.traceOperation(anyString(), any(Map.class), any(java.util.function.Supplier.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        when(queueOwnerCache.getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.of(actualOwner));

        ConsumerQueueOwnershipValidator validator = new ConsumerQueueOwnershipValidator(queueOwnerCache);

        RoutingService routingService = new RoutingService(
                providerCache, immediateHandler, delayedHandler, tracingUtils, validator);

        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");

        // proxiedAsEnv is X-Environment-Id as set by the gateway (admin run-as resolved)
        assertThatThrownBy(() -> routingService.routeDelayedRequest(
                proxiedAsEnv, "admin-proxy-env",
                "domain1", "solution1",
                "test-zone", "DEFAULT",
                queueId, request,
                Map.of(), "POST", "/" + serviceName, null))
                .isInstanceOf(UnknownQueueException.class);

        verifyNoInteractions(providerCache);
    }

    // ── Arbitraries ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<UUID> callerEnvironmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<UUID> queueIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<UUID> queueOwnerIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
                "StudentPersonal", "SchoolInfo", "StudentSchoolEnrollment",
                "StaffPersonal", "TeachingGroup");
    }
}
