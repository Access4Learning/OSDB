// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.TracingUtils;
import org.sifunity.responsesconnector.client.QueueServiceClient;
import org.sifunity.responsesconnector.client.EnvironmentServiceClient;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.exception.ResponseDestinationDeniedException;
import org.sifunity.responsesconnector.service.ResponseDestinationValidator;
import org.sifunity.responsesconnector.service.LegacyTagLookupService;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.sifunity.responsesconnector.util.MessageBuilder;

import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug-condition exploration for {@code response-destination-acl-fix}.
 *
 * <p><strong>THESE PROPERTIES ARE EXPECTED TO FAIL until the destination-ACL gate is
 * implemented.</strong> Failure is the point: it is the counterexample proving the response
 * ingress path enqueues into a destination queue without establishing that the queue's owner is
 * entitled to the object being delivered.</p>
 *
 * <p>Do not "fix" these by weakening the assertions. They encode the required behaviour and flip
 * to passing when the gate lands.</p>
 *
 * <h2>Why this file starts with only the fail-closed property</h2>
 *
 * <p>The full bug condition is <em>"the destination queue's owner holds no ACL grant for the
 * submitted (serviceName, zoneId, contextId)"</em>. Expressing that requires the entitlement
 * lookup seam, which does not exist yet — {@code EnvironmentServiceClient} in this module
 * currently exposes only {@code getEntityTagValue}, and {@code ResponseRoutingService} takes no
 * entitlement collaborator. Writing it now would not compile.</p>
 *
 * <p>What <em>is</em> expressible today is the fail-closed half of the same requirement: the
 * queue-owner lookup already exists ({@code QueueServiceClient.getQueueOwnerEnvironmentId},
 * returning {@code null} on 404 / malformed body / transport failure), and is used today only to
 * choose legacy {@code relativeServicePath} formatting — it informs no authorization decision. So
 * a response is delivered even when the broker cannot establish who owns the destination.</p>
 *
 * <p>After task 3.1 introduces the entitlement lookup, extend this file with:</p>
 * <ul>
 *   <li>destination owner resolves but holds <em>no</em> grant on the object → reject (2.3)</li>
 *   <li>entitlement lookup itself fails → reject, never enqueue (2.7)</li>
 * </ul>
 *
 * <p>The absent-{@code requestId} case (1.3 / 2.6) is deliberately <em>not</em> here: that fix is a
 * required {@code @RequestHeader} binding, so it is rejected by Spring with 400 before the routing
 * layer runs. It belongs in a controller-level test, not this one.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 2.1, 2.2</strong></p>
 */
@Tag("response-destination-acl-fix")
class ResponseDestinationAclBugConditionExplorationTest {

    private static final String DOMAIN = "test-domain";
    private static final String SOLUTION = "test-solution";
    private static final String ZONE = "test-zone";
    private static final String CONTEXT = "DEFAULT";
    private static final String SERVICE_PATH = "/StudentPersonal";

    /** Collaborators plus the service under test, wired the way production wires them. */
    private record Fixture(ResponseRoutingService service, QueueServiceClient queueClient) {}

    private static Fixture fixtureWithQueueOwner(UUID resolvedOwnerOrNull) {
        return fixture(resolvedOwnerOrNull, true);
    }

    /**
     * Builds the service with the REAL {@link ResponseDestinationValidator}, so these properties
     * exercise the gate itself rather than a mock that would trivially permit everything.
     *
     * @param resolvedOwnerOrNull what the queue-owner lookup yields; {@code null} models an
     *                            unresolvable destination
     * @param ownerEntitled       what the entitlement lookup yields for a resolved owner
     */
    @SuppressWarnings("unchecked")
    private static Fixture fixture(UUID resolvedOwnerOrNull, boolean ownerEntitled) {
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        MessageBuilder messageBuilder = mock(MessageBuilder.class);
        TracingUtils tracingUtils = mock(TracingUtils.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);

        lenient().when(environmentServiceClient.hasAnyGrant(
                        anyString(), anyString(), any(UUID.class), anyString(), any(), any()))
                .thenReturn(ownerEntitled);

        // A real cache (fresh per iteration) so the gate is exercised through the caching path it
        // actually uses in production, rather than bypassing it.
        ResponseDestinationValidator destinationValidator = new ResponseDestinationValidator(
                new org.sifunity.responsesconnector.cache.QueueOwnerCache(
                        queueClient, java.time.Duration.ofMinutes(5), 10_000),
                new org.sifunity.responsesconnector.cache.DestinationEntitlementCache(
                                environmentServiceClient, java.time.Duration.ofSeconds(30), 10_000));

        // Run traced operations inline so the real logic executes.
        lenient().when(tracingUtils.traceOperation(any(), any(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());

        // THE BUG CONDITION: the broker cannot establish who owns the destination queue.
        lenient().when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class)))
                .thenReturn(resolvedOwnerOrNull);
        lenient().when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class)))
                .thenReturn(false);

        lenient().when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(SifMessageEnvelope.<Object>builder().messageId(UUID.randomUUID()).build());
        lenient().when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class),
                        any(SifMessageEnvelope.class)))
                .thenReturn(UUID.randomUUID());

        return new Fixture(
                new ResponseRoutingService(queueClient, messageBuilder, tracingUtils,
                        legacyTagLookupService, destinationValidator),
                queueClient);
    }

    /**
     * Builds a response DTO complete enough to reach the gate.
     *
     * <p>{@code serviceType} must be set even though these properties do not exercise it:
     * {@code routeResponse} opens with a {@code Map.of(...)} of span attributes that includes
     * {@code response.getServiceType()}, and {@code Map.of} rejects null values. An incomplete DTO
     * therefore throws {@link NullPointerException} before the gate runs — which briefly looked
     * like a production fail-closed behaviour and was in fact a gap in this fixture.</p>
     */
    private static ResponseDTO responseFor(String serviceName) {
        ResponseDTO dto = new ResponseDTO();
        dto.setServiceName(serviceName);
        dto.setServiceType("OBJECT");
        return dto;
    }

    /**
     * Property 1 (Requirement 2.2) — fail closed when the destination owner is unresolvable.
     *
     * <p>When {@code getQueueOwnerEnvironmentId} yields {@code null} — the queue does not exist, the
     * body was malformed, or {@code queue-service} was unreachable — the broker cannot establish
     * that the destination is entitled to anything. It must therefore reject rather than deliver on
     * an unverified {@code queueId}.</p>
     *
     * <p><strong>Expected failure today:</strong> the response is enqueued anyway, because the owner
     * lookup only feeds legacy formatting.</p>
     */
    @Property(tries = 20)
    @Label("Unresolvable destination owner must NOT be enqueued (fail closed)")
    void unresolvableDestinationOwnerIsNotEnqueued(
            @ForAll("uuids") UUID environmentId,
            @ForAll("uuids") UUID queueId,
            @ForAll("requestIds") String requestId) {

        Fixture fixture = fixtureWithQueueOwner(null);

        Throwable thrown = catchThrowable(() -> fixture.service().routeResponse(
                DOMAIN, SOLUTION, environmentId, "ProviderEnv", queueId, requestId,
                responseFor("StudentPersonal"), Collections.emptyMap(), ZONE, CONTEXT, SERVICE_PATH));

        // The invariant that matters, independent of how rejection is signalled.
        verify(fixture.queueClient(), never())
                .enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class));

        // ...but it must be a DELIBERATE rejection, not an incidental crash. Today this path throws
        // NullPointerException from Objects.requireNonNull inside a Map.of(...) built with the
        // unresolved owner, so delivery is prevented only by accident — and surfaces to the caller
        // as 500 rather than the specified 404 (requirement 2.4). Asserting "not an NPE" is what
        // stops this property from passing for the wrong reason; an earlier draft asserted merely
        // "some RuntimeException" and passed on exactly that accidental NPE.
        assertThat(thrown)
                .as("An unresolvable destination owner must be rejected deliberately (404), not by "
                        + "crashing. A NullPointerException here means the gate does not exist yet.")
                .isNotInstanceOf(NullPointerException.class);
    }

    /**
     * Property 2 (Requirements 1.1, 1.2, 2.1) — the owner lookup currently has no authorization
     * effect at all.
     *
     * <p>Documents the root cause directly: with the owner resolving to an environment entirely
     * unrelated to the caller and to any admitted request, the response is still enqueued. Once the
     * gate exists, delivery must depend on that owner's entitlement rather than being unconditional.
     * </p>
     *
     * <p><strong>Expected failure today:</strong> {@code enqueueMessage} is invoked.</p>
     */
    @Property(tries = 20)
    @Label("Destination owner with no grant on the object must NOT be enqueued")
    void unentitledDestinationOwnerIsNotEnqueued(
            @ForAll("uuids") UUID environmentId,
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID unentitledOwner,
            @ForAll("requestIds") String requestId) {

        // Owner resolves cleanly, but holds no approved grant for the object being delivered.
        Fixture fixture = fixture(unentitledOwner, false);

        Throwable thrown = catchThrowable(() -> fixture.service().routeResponse(
                DOMAIN, SOLUTION, environmentId, "ProviderEnv", queueId, requestId,
                responseFor("StudentPersonal"), Collections.emptyMap(), ZONE, CONTEXT, SERVICE_PATH));

        verify(fixture.queueClient(), never())
                .enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class));

        assertThat(thrown)
                .as("A destination whose owner is not entitled to the object must be rejected "
                        + "deliberately — this is the cross-tenant leak the gate exists to close")
                .isInstanceOf(ResponseDestinationDeniedException.class);
    }

    /**
     * Property 3 — positive control. An entitled destination IS delivered.
     *
     * <p>Deliberately in this file rather than only in the preservation suite. Properties 1 and 2
     * both assert rejection, and a gate that rejected <em>everything</em> would satisfy both — the
     * exact failure mode that let the request-side certification stay green while every DELAYED
     * request was broken. This property is what makes the other two mean something.</p>
     */
    @Property(tries = 20)
    @Label("Control: an entitled destination owner IS still delivered")
    void entitledDestinationOwnerIsDelivered(
            @ForAll("uuids") UUID environmentId,
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID entitledOwner,
            @ForAll("requestIds") String requestId) {

        Fixture fixture = fixture(entitledOwner, true);

        Throwable thrown = catchThrowable(() -> fixture.service().routeResponse(
                DOMAIN, SOLUTION, environmentId, "ProviderEnv", queueId, requestId,
                responseFor("StudentPersonal"), Collections.emptyMap(), ZONE, CONTEXT, SERVICE_PATH));

        assertThat(thrown)
                .as("The gate must discriminate, not deny everything: an entitled destination must "
                        + "not be rejected")
                .isNull();

        verify(fixture.queueClient())
                .enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class));
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> requestIds() {
        return Arbitraries.of("req-1", "42", "cert-abc", UUID.randomUUID().toString());
    }
}
