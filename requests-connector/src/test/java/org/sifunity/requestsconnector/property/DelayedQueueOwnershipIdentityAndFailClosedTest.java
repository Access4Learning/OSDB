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
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.exception.UnknownQueueException;
import org.sifunity.requestsconnector.service.ConsumerQueueOwnershipValidator;
import org.sifunity.requestsconnector.service.DelayedRequestHandler;
import org.sifunity.requestsconnector.service.ImmediateRequestHandler;
import org.sifunity.requestsconnector.service.RoutingService;

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
 * Identity-uniformity (Property 3) and fail-closed (Property 4) tests.
 *
 * <p><strong>Property 3 — Identity uniformity.</strong> For the same {@code (caller, queue)} pair
 * the outcome is identical whether {@code X-Environment-Id} arrived by direct authentication or by
 * admin run-as. The gateway resolves impersonation before any backend sees the request, so the
 * routing layer receives one effective identity and applies one rule. Proxying grants the ability
 * to act <em>as</em> A — not the ability to name queues A does not own.</p>
 *
 * <p><strong>Property 4 — Fail-closed.</strong> When ownership cannot be established the request is
 * rejected and no message is ever enqueued.</p>
 *
 * <p>Includes a <strong>bypass regression guard</strong>: an earlier draft of this design proposed
 * a {@code broker-services} fingerprint + internal-token bypass. That was wrong (a static shared
 * token carries no per-environment meaning, and the fingerprint is self-reported), so the validator
 * must remain structurally incapable of consulting those headers.</p>
 *
 * <p><strong>Validates: Requirements 2.3, 2.6, 2.7, 2.10, 3.9</strong></p>
 */
@Tag("delayed-queue-ownership-fix")
class DelayedQueueOwnershipIdentityAndFailClosedTest {

    private record Fixture(
            RoutingService routingService,
            ProviderRegistryCache providerCache,
            DelayedRequestHandler delayedHandler,
            QueueOwnerCache queueOwnerCache
    ) {}

    @SuppressWarnings("unchecked")
    private static Fixture fixture(String serviceName, UUID providerEnvId) {
        ProviderRegistryCache providerCache = mock(ProviderRegistryCache.class);
        ImmediateRequestHandler immediateHandler = mock(ImmediateRequestHandler.class);
        DelayedRequestHandler delayedHandler = mock(DelayedRequestHandler.class);
        TracingUtilsBase tracingUtils = mock(TracingUtilsBase.class);
        QueueOwnerCache queueOwnerCache = mock(QueueOwnerCache.class);

        lenient().when(tracingUtils.traceOperation(anyString(), any(Map.class), any(java.util.function.Supplier.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());

        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                providerEnvId,
                serviceName,
                "test-zone",
                "DEFAULT",
                "ProviderApp",
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/" + serviceName),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of())))),
                Instant.now()
        );

        lenient().when(providerCache.getProvider(anyString(), anyString(), eq(serviceName), anyString(), anyString()))
                .thenReturn(Optional.of(provider));

        lenient().when(delayedHandler.handleDelayedToDelayed(
                        any(UUID.class), anyString(), anyString(), anyString(),
                        any(ProviderRegistrationDTO.class), any(UUID.class),
                        any(DelayedRequestDTO.class), any(Map.class), anyString(), any()))
                .thenAnswer(inv -> RequestResponseDTO.builder()
                        .messageId(UUID.randomUUID())
                        .queueId(inv.getArgument(5))
                        .status("ACCEPTED")
                        .timestamp(Instant.now())
                        .build());

        ConsumerQueueOwnershipValidator validator = new ConsumerQueueOwnershipValidator(queueOwnerCache);
        RoutingService routingService = new RoutingService(
                providerCache, immediateHandler, delayedHandler, tracingUtils, validator);

        return new Fixture(routingService, providerCache, delayedHandler, queueOwnerCache);
    }

    private static DelayedRequestDTO request(String serviceName) {
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        return request;
    }

    // ── Property 3: Identity uniformity ──────────────────────────────────────

    /**
     * Requirement 3.9: an admin proxying as A, naming a queue A DOES own, still succeeds.
     * Proxying must remain usable for its intended purpose.
     */
    @Property(tries = 20)
    @Label("Identity uniformity: proxied-as-A naming A's OWN queue succeeds (202)")
    void proxiedAsOwnerNamingOwnQueueSucceeds(
            @ForAll("uuids") UUID envA,
            @ForAll("uuids") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        Fixture f = fixture(serviceName, UUID.randomUUID());
        when(f.queueOwnerCache().getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.of(envA));   // A owns the queue

        RequestResponseDTO result = f.routingService().routeDelayedRequest(
                envA, "admin-proxied-as-A",
                "domain1", "solution1", "test-zone", "DEFAULT",
                queueId, request(serviceName),
                Map.of(), "GET", "/" + serviceName, null);

        assertThat(result.getStatus()).isEqualTo("ACCEPTED");
        assertThat(result.getQueueId()).isEqualTo(queueId);
    }

    /**
     * Requirement 2.6: an admin proxying as A, naming a queue A does NOT own, is rejected —
     * identically to A making the request directly. There is no admin bypass.
     */
    @Property(tries = 20)
    @Label("Identity uniformity: proxied-as-A naming another env's queue is rejected (404)")
    void proxiedAsOwnerNamingForeignQueueIsRejected(
            @ForAll("uuids") UUID envA,
            @ForAll("uuids") UUID envB,
            @ForAll("uuids") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        org.junit.jupiter.api.Assumptions.assumeTrue(!envA.equals(envB));

        Fixture f = fixture(serviceName, UUID.randomUUID());
        when(f.queueOwnerCache().getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.of(envB));   // B owns the queue, caller is A

        assertThatThrownBy(() -> f.routingService().routeDelayedRequest(
                envA, "admin-proxied-as-A",
                "domain1", "solution1", "test-zone", "DEFAULT",
                queueId, request(serviceName),
                Map.of(), "GET", "/" + serviceName, null))
                .isInstanceOf(UnknownQueueException.class);

        verifyNoInteractions(f.delayedHandler());
    }

    /**
     * The outcome for a given {@code (caller, queue)} pair does not depend on how the identity
     * was established — the routing layer sees only the resolved environment ID.
     */
    @Property(tries = 20)
    @Label("Identity uniformity: direct and proxied callers get the same outcome")
    void directAndProxiedOutcomesMatch(
            @ForAll("uuids") UUID envA,
            @ForAll("uuids") UUID otherEnv,
            @ForAll("uuids") UUID queueId,
            @ForAll("serviceNames") String serviceName,
            @ForAll boolean callerOwnsQueue) {

        org.junit.jupiter.api.Assumptions.assumeTrue(!otherEnv.equals(envA));

        // The generated boolean drives BOTH branches. Deriving it from UUID equality (as an
        // earlier version did) made it always false, so the accepted path was never exercised.
        UUID resolvedOwner = callerOwnsQueue ? envA : otherEnv;
        String expected = callerOwnsQueue ? "ACCEPTED" : "REJECTED";

        // "Direct" invocation
        Fixture direct = fixture(serviceName, UUID.randomUUID());
        when(direct.queueOwnerCache().getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.of(resolvedOwner));
        String directOutcome = outcomeOf(direct, envA, queueId, serviceName, "direct-env");

        // "Admin run-as A" invocation — same resolved identity, different provenance
        Fixture proxied = fixture(serviceName, UUID.randomUUID());
        when(proxied.queueOwnerCache().getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.of(resolvedOwner));
        String proxiedOutcome = outcomeOf(proxied, envA, queueId, serviceName, "admin-proxied-as-A");

        // Assert the ABSOLUTE outcome too, so the property cannot pass by both sides being
        // wrong in the same direction.
        assertThat(directOutcome)
                .as("Direct caller outcome for callerOwnsQueue=%s", callerOwnsQueue)
                .isEqualTo(expected);
        assertThat(proxiedOutcome)
                .as("Outcome must not depend on how X-Environment-Id was established")
                .isEqualTo(directOutcome);
    }

    private static String outcomeOf(Fixture f, UUID caller, UUID queueId,
                                    String serviceName, String envName) {
        try {
            f.routingService().routeDelayedRequest(
                    caller, envName,
                    "domain1", "solution1", "test-zone", "DEFAULT",
                    queueId, request(serviceName),
                    Map.of(), "GET", "/" + serviceName, null);
            return "ACCEPTED";
        } catch (UnknownQueueException e) {
            return "REJECTED";
        }
    }

    /**
     * Bypass regression guard: {@code requireOwnership} must take only
     * {@code (domainId, solutionId, queueId, callerEnvironmentId)} — there is no parameter
     * through which a fingerprint or internal service token could influence the decision.
     */
    @Example
    @Label("Bypass regression guard: the validator cannot consult fingerprint or service token")
    void validatorHasNoBypassParameters() throws Exception {
        Method m = ConsumerQueueOwnershipValidator.class
                .getMethod("requireOwnership", String.class, String.class, UUID.class, UUID.class);

        assertThat(m.getParameterCount())
                .as("requireOwnership must take exactly (domainId, solutionId, queueId, callerEnvironmentId)")
                .isEqualTo(4);

        assertThat(ConsumerQueueOwnershipValidator.class.getDeclaredMethods())
                .as("No overload may accept extra arguments that could carry a bypass signal")
                .filteredOn(method -> method.getName().equals("requireOwnership"))
                .hasSize(1);
    }

    // ── Property 4: Fail-closed ──────────────────────────────────────────────

    /**
     * Empty lookup (queue absent, malformed body, or queue-service unreachable — the client
     * collapses all three to empty) must reject with 404 and never enqueue.
     */
    @Property(tries = 20)
    @Label("Fail-closed: unresolvable owner rejects with UnknownQueueException and never enqueues")
    void unresolvableOwnerRejects(
            @ForAll("uuids") UUID caller,
            @ForAll("uuids") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        Fixture f = fixture(serviceName, UUID.randomUUID());
        when(f.queueOwnerCache().getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> f.routingService().routeDelayedRequest(
                caller, "test-env",
                "domain1", "solution1", "test-zone", "DEFAULT",
                queueId, request(serviceName),
                Map.of(), "GET", "/" + serviceName, null))
                .isInstanceOf(UnknownQueueException.class);

        verifyNoInteractions(f.delayedHandler());
        verifyNoInteractions(f.providerCache());
    }

    /**
     * If the lookup itself throws, the request must still be rejected and nothing enqueued.
     *
     * <p>The invariant asserted here is the one the design says matters at every layer:
     * <em>rejected, and no enqueue</em> — not a specific status code, since an unexpected
     * throw surfaces higher up the stack than the gate's own 404.</p>
     */
    @Property(tries = 20)
    @Label("Fail-closed: a throwing owner lookup still rejects and never enqueues")
    void throwingLookupStillRejects(
            @ForAll("uuids") UUID caller,
            @ForAll("uuids") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        Fixture f = fixture(serviceName, UUID.randomUUID());
        when(f.queueOwnerCache().getQueueOwner("domain1", "solution1", queueId))
                .thenThrow(new RuntimeException("queue-service exploded"));

        assertThatThrownBy(() -> f.routingService().routeDelayedRequest(
                caller, "test-env",
                "domain1", "solution1", "test-zone", "DEFAULT",
                queueId, request(serviceName),
                Map.of(), "GET", "/" + serviceName, null))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(f.delayedHandler());
    }

    /**
     * Requirement 2.10: the validator's own null-caller branch returns the gate's rejection.
     *
     * <p>Exercised by invoking the validator directly. Through the controller an absent
     * {@code X-Environment-Id} fails Spring header binding with <strong>400</strong> before the
     * gate is reached — so this branch is defense-in-depth for any future non-HTTP caller.</p>
     */
    @Property(tries = 20)
    @Label("Fail-closed: null caller environment is rejected by the validator itself")
    void nullCallerIsRejectedByValidator(@ForAll("uuids") UUID queueId) {

        QueueOwnerCache queueOwnerCache = mock(QueueOwnerCache.class);
        ConsumerQueueOwnershipValidator validator = new ConsumerQueueOwnershipValidator(queueOwnerCache);

        assertThatThrownBy(() -> validator.requireOwnership("domain1", "solution1", queueId, null))
                .isInstanceOf(UnknownQueueException.class)
                .hasMessageContaining("does not exist");

        // No lookup should even be attempted for a null caller
        verifyNoInteractions(queueOwnerCache);
    }

    /**
     * The rejection message must not disclose the owner or any other environment identifier
     * (requirement 2.4) — 404 collapses "absent" and "not yours" into one response.
     */
    @Property(tries = 20)
    @Label("Fail-closed: rejection message discloses no owner information")
    void rejectionMessageDisclosesNothing(
            @ForAll("uuids") UUID caller,
            @ForAll("uuids") UUID owner,
            @ForAll("uuids") UUID queueId) {

        org.junit.jupiter.api.Assumptions.assumeTrue(!caller.equals(owner));

        QueueOwnerCache queueOwnerCache = mock(QueueOwnerCache.class);
        when(queueOwnerCache.getQueueOwner("domain1", "solution1", queueId))
                .thenReturn(Optional.of(owner));
        ConsumerQueueOwnershipValidator validator = new ConsumerQueueOwnershipValidator(queueOwnerCache);

        assertThatThrownBy(() -> validator.requireOwnership("domain1", "solution1", queueId, caller))
                .isInstanceOf(UnknownQueueException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(owner.toString());
                    assertThat(e.getMessage()).doesNotContain(caller.toString());
                    assertThat(e.getMessage()).doesNotContain(queueId.toString());
                });
    }

    // ── Arbitraries ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
                "StudentPersonal", "SchoolInfo", "StudentSchoolEnrollment",
                "StaffPersonal", "TeachingGroup");
    }
}
