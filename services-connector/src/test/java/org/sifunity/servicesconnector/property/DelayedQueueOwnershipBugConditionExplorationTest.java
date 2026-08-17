// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.servicesconnector.cache.ProviderRegistryCache;
import org.sifunity.servicesconnector.cache.QueueOwnerCache;
import org.sifunity.servicesconnector.client.EnvironmentServiceClient;
import org.sifunity.servicesconnector.client.ProviderHttpClient;
import org.sifunity.servicesconnector.client.QueueServiceClient;
import org.sifunity.servicesconnector.exception.UnknownQueueException;
import org.sifunity.servicesconnector.service.ConsumerQueueOwnershipValidator;
import org.sifunity.servicesconnector.service.DelayedRequestHandler;
import org.sifunity.servicesconnector.service.FunctionalServiceRoutingService;
import org.sifunity.servicesconnector.util.MessageBuilder;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug Condition Exploration Test: Cross-Tenant Queue Ownership Hijack (services-connector).
 *
 * <p><strong>CRITICAL: After the fix, these tests PASS — the gate rejects unauthorized queueIds.</strong></p>
 *
 * <p><strong>Validates: Requirements 1.6, 2.1, 2.8, 2.9</strong></p>
 */
@Tag("delayed-queue-ownership-fix")
@Tag("bug-condition-exploration")
class DelayedQueueOwnershipBugConditionExplorationTest {

    /**
     * Property 1: DELAYED request with foreign queueId throws UnknownQueueException.
     */
    @Property(tries = 20)
    @Label("Bug Condition: DELAYED functional service request with foreign queueId is rejected")
    void delayedWithForeignQueueIsRejected(
            @ForAll("callerEnvironmentIds") UUID callerEnvId,
            @ForAll("queueIds") UUID queueId,
            @ForAll("queueOwnerIds") UUID queueOwnerId,
            @ForAll("serviceNames") String serviceName) {

        org.junit.jupiter.api.Assumptions.assumeTrue(!queueOwnerId.equals(callerEnvId));

        ProviderRegistryCache providerCache = mock(ProviderRegistryCache.class);
        ProviderHttpClient providerHttpClient = mock(ProviderHttpClient.class);
        TracingUtilsBase tracingUtils = mock(TracingUtilsBase.class);
        DelayedRequestHandler delayedHandler = mock(DelayedRequestHandler.class);
        EnvironmentServiceClient envClient = mock(EnvironmentServiceClient.class);
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        QueueOwnerCache queueOwnerCache = mock(QueueOwnerCache.class);

        when(tracingUtils.traceOperation(anyString(), any(Map.class), any(java.util.function.Supplier.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        when(queueOwnerCache.getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.of(queueOwnerId));

        ConsumerQueueOwnershipValidator validator = new ConsumerQueueOwnershipValidator(queueOwnerCache);

        FunctionalServiceRoutingService routingService = new FunctionalServiceRoutingService(
                providerCache, providerHttpClient, tracingUtils, new MessageBuilder(),
                delayedHandler, envClient, queueClient, validator);

        assertThatThrownBy(() -> routingService.routeDelayedRequest(
                "POST", serviceName, null, null,
                callerEnvId, "test-env",
                "domain1", "solution1",
                "test-zone", "DEFAULT",
                queueId, null,
                null, Map.of()))
                .isInstanceOf(UnknownQueueException.class)
                .hasMessageContaining("does not exist");

        verifyNoInteractions(providerCache);
        verifyNoInteractions(delayedHandler);
    }

    /**
     * Property 2: DELAYED with non-existent queue (empty lookup) → rejected.
     */
    @Property(tries = 20)
    @Label("Bug Condition: DELAYED with non-existent queue (empty lookup) is rejected")
    void delayedWithNonExistentQueueIsRejected(
            @ForAll("callerEnvironmentIds") UUID callerEnvId,
            @ForAll("queueIds") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        ProviderRegistryCache providerCache = mock(ProviderRegistryCache.class);
        ProviderHttpClient providerHttpClient = mock(ProviderHttpClient.class);
        TracingUtilsBase tracingUtils = mock(TracingUtilsBase.class);
        DelayedRequestHandler delayedHandler = mock(DelayedRequestHandler.class);
        EnvironmentServiceClient envClient = mock(EnvironmentServiceClient.class);
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        QueueOwnerCache queueOwnerCache = mock(QueueOwnerCache.class);

        when(tracingUtils.traceOperation(anyString(), any(Map.class), any(java.util.function.Supplier.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        when(queueOwnerCache.getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.empty());

        ConsumerQueueOwnershipValidator validator = new ConsumerQueueOwnershipValidator(queueOwnerCache);

        FunctionalServiceRoutingService routingService = new FunctionalServiceRoutingService(
                providerCache, providerHttpClient, tracingUtils, new MessageBuilder(),
                delayedHandler, envClient, queueClient, validator);

        assertThatThrownBy(() -> routingService.routeDelayedRequest(
                "GET", serviceName, "job-1", "phase-1",
                callerEnvId, "test-env",
                "domain1", "solution1",
                "test-zone", "DEFAULT",
                queueId, null,
                null, Map.of()))
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
        return Arbitraries.of("StudentTranscript", "AggregateCharacteristics",
                "LearnerRecordTransfer", "ReportGeneration");
    }
}
