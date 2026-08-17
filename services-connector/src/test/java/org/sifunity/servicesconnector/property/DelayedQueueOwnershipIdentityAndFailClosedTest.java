// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
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

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Identity-uniformity (Property 3) and fail-closed (Property 4) tests for the
 * services-connector, mirroring the requests-connector suite.
 *
 * <p>Requirement 2.8 requires identical semantics in both connectors, so the same
 * properties are asserted here against {@link FunctionalServiceRoutingService}.</p>
 *
 * <p><strong>Validates: Requirements 2.3, 2.6, 2.7, 2.8, 2.10, 3.9</strong></p>
 */
@Tag("delayed-queue-ownership-fix")
class DelayedQueueOwnershipIdentityAndFailClosedTest {

    private record Fixture(
            FunctionalServiceRoutingService routingService,
            ProviderRegistryCache providerCache,
            DelayedRequestHandler delayedHandler,
            QueueOwnerCache queueOwnerCache
    ) {}

    @SuppressWarnings("unchecked")
    private static Fixture fixture(String serviceName) {
        ProviderRegistryCache providerCache = mock(ProviderRegistryCache.class);
        ProviderHttpClient providerHttpClient = mock(ProviderHttpClient.class);
        TracingUtilsBase tracingUtils = mock(TracingUtilsBase.class);
        DelayedRequestHandler delayedHandler = mock(DelayedRequestHandler.class);
        EnvironmentServiceClient envClient = mock(EnvironmentServiceClient.class);
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        QueueOwnerCache queueOwnerCache = mock(QueueOwnerCache.class);

        lenient().when(tracingUtils.traceOperation(anyString(), any(Map.class), any(java.util.function.Supplier.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                "test-zone",
                "DEFAULT",
                "ProviderApp",
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/" + serviceName),
                ServiceType.FUNCTIONAL,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of())))),
                Instant.now()
        );

        lenient().when(providerCache.getProvider(anyString(), anyString(), eq(serviceName), anyString(), anyString()))
                .thenReturn(Optional.of(provider));

        lenient().when(delayedHandler.handleDelayedToDelayed(
                        any(), any(), any(), any(), any(), any(), any(), any(),
                        any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("status", "ACCEPTED", "messageId", UUID.randomUUID().toString()));

        ConsumerQueueOwnershipValidator validator = new ConsumerQueueOwnershipValidator(queueOwnerCache);

        FunctionalServiceRoutingService routingService = new FunctionalServiceRoutingService(
                providerCache, providerHttpClient, tracingUtils, new MessageBuilder(),
                delayedHandler, envClient, queueClient, validator);

        return new Fixture(routingService, providerCache, delayedHandler, queueOwnerCache);
    }

    /**
     * Requirement 3.9: proxied-as-A naming A's OWN queue still succeeds.
     */
    @Property(tries = 20)
    @Label("Identity uniformity: proxied-as-A naming A's OWN queue succeeds")
    void proxiedAsOwnerNamingOwnQueueSucceeds(
            @ForAll("uuids") UUID envA,
            @ForAll("uuids") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        Fixture f = fixture(serviceName);
        when(f.queueOwnerCache().getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.of(envA));

        Object result = f.routingService().routeDelayedRequest(
                "POST", serviceName, null, null,
                envA, "admin-proxied-as-A",
                "domain1", "solution1", "test-zone", "DEFAULT",
                queueId, null, null, Map.of());

        assertThat(result).isNotNull();
        assertThat(result.toString()).contains("ACCEPTED");
    }

    /**
     * Requirement 2.6: proxied-as-A naming another environment's queue is rejected.
     */
    @Property(tries = 20)
    @Label("Identity uniformity: proxied-as-A naming another env's queue is rejected")
    void proxiedAsOwnerNamingForeignQueueIsRejected(
            @ForAll("uuids") UUID envA,
            @ForAll("uuids") UUID envB,
            @ForAll("uuids") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        org.junit.jupiter.api.Assumptions.assumeTrue(!envA.equals(envB));

        Fixture f = fixture(serviceName);
        when(f.queueOwnerCache().getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.of(envB));

        assertThatThrownBy(() -> f.routingService().routeDelayedRequest(
                "POST", serviceName, null, null,
                envA, "admin-proxied-as-A",
                "domain1", "solution1", "test-zone", "DEFAULT",
                queueId, null, null, Map.of()))
                .isInstanceOf(UnknownQueueException.class);

        verifyNoInteractions(f.delayedHandler());
    }

    /**
     * Fail-closed: unresolvable owner rejects and never enqueues.
     */
    @Property(tries = 20)
    @Label("Fail-closed: unresolvable owner rejects and never enqueues")
    void unresolvableOwnerRejects(
            @ForAll("uuids") UUID caller,
            @ForAll("uuids") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        Fixture f = fixture(serviceName);
        when(f.queueOwnerCache().getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> f.routingService().routeDelayedRequest(
                "GET", serviceName, "job-1", "phase-1",
                caller, "test-env",
                "domain1", "solution1", "test-zone", "DEFAULT",
                queueId, null, null, Map.of()))
                .isInstanceOf(UnknownQueueException.class);

        verifyNoInteractions(f.delayedHandler());
        verifyNoInteractions(f.providerCache());
    }

    /**
     * Requirement 2.10: the validator's own null-caller branch rejects.
     */
    @Property(tries = 20)
    @Label("Fail-closed: null caller environment is rejected by the validator itself")
    void nullCallerIsRejectedByValidator(@ForAll("uuids") UUID queueId) {

        QueueOwnerCache queueOwnerCache = mock(QueueOwnerCache.class);
        ConsumerQueueOwnershipValidator validator = new ConsumerQueueOwnershipValidator(queueOwnerCache);

        assertThatThrownBy(() -> validator.requireOwnership("domain1", "solution1", queueId, null))
                .isInstanceOf(UnknownQueueException.class);

        verifyNoInteractions(queueOwnerCache);
    }

    /**
     * Bypass regression guard — no fingerprint/token parameter exists on the decision path.
     */
    @Example
    @Label("Bypass regression guard: the validator cannot consult fingerprint or service token")
    void validatorHasNoBypassParameters() throws Exception {
        Method m = ConsumerQueueOwnershipValidator.class
                .getMethod("requireOwnership", String.class, String.class, UUID.class, UUID.class);

        assertThat(m.getParameterCount()).isEqualTo(4);

        assertThat(ConsumerQueueOwnershipValidator.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("requireOwnership"))
                .hasSize(1);
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of("StudentTranscript", "AggregateCharacteristics",
                "LearnerRecordTransfer", "ReportGeneration");
    }
}
