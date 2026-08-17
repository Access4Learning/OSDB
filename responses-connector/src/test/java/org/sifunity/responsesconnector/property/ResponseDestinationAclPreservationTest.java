// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.TracingUtils;
import org.sifunity.responsesconnector.client.EnvironmentServiceClient;
import org.sifunity.responsesconnector.client.QueueServiceClient;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.service.LegacyTagLookupService;
import org.sifunity.responsesconnector.service.ResponseDestinationValidator;
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
 * Preservation properties for {@code response-destination-acl-fix}.
 *
 * <p>These MUST pass both before and after the gate. They pin the behaviour the gate must
 * <em>not</em> break — which for an authorization change is where the real risk lives, since a gate
 * that simply denies everything satisfies every rejection test.</p>
 *
 * <p>The most important property here is {@link #providerNotOwningTheQueueIsStillDelivered()}. It
 * encodes the reason ownership is the <strong>wrong</strong> predicate on this leg: in the SIF
 * brokered flow a provider posts a response naming the <em>consumer's</em> queue, which the provider
 * does not and should not own. If someone later "simplifies" this gate by reusing
 * {@code ConsumerQueueOwnershipValidator} from the request side, that property is what fails.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3</strong></p>
 */
@Tag("response-destination-acl-fix")
class ResponseDestinationAclPreservationTest {

    private static final String DOMAIN = "test-domain";
    private static final String SOLUTION = "test-solution";
    private static final String ZONE = "test-zone";
    private static final String CONTEXT = "DEFAULT";
    private static final String SERVICE_PATH = "/StudentPersonal";

    private record Fixture(ResponseRoutingService service,
                           QueueServiceClient queueClient,
                           ResponseDestinationValidator validator) {}

    /**
     * @param validatorPermits when false, the validator throws — used to prove the internal path
     *                         never consults it
     */
    @SuppressWarnings("unchecked")
    private static Fixture fixture(UUID resolvedOwner, boolean ownerEntitled, boolean validatorPermits) {
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        MessageBuilder messageBuilder = mock(MessageBuilder.class);
        TracingUtils tracingUtils = mock(TracingUtils.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        EnvironmentServiceClient environmentServiceClient = mock(EnvironmentServiceClient.class);

        lenient().when(tracingUtils.traceOperation(any(), any(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        lenient().when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class)))
                .thenReturn(resolvedOwner);
        lenient().when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class)))
                .thenReturn(false);
        lenient().when(environmentServiceClient.hasAnyGrant(
                        anyString(), anyString(), any(UUID.class), anyString(), any(), any()))
                .thenReturn(ownerEntitled);
        // Both builders must be stubbed: routeResponse uses buildResponseEnvelope while
        // routeInternalResponse uses buildInternalResponseEnvelope. Stubbing only the former makes
        // the internal-path property fail on a null envelope rather than on the behaviour under test.
        lenient().when(messageBuilder.buildResponseEnvelope(any(), any(), any(), any(), any()))
                .thenReturn(SifMessageEnvelope.<Object>builder().messageId(UUID.randomUUID()).build());
        lenient().when(messageBuilder.buildInternalResponseEnvelope(any(), any()))
                .thenReturn(SifMessageEnvelope.<Object>builder().messageId(UUID.randomUUID()).build());
        lenient().when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class),
                        any(SifMessageEnvelope.class)))
                .thenAnswer(invocation -> UUID.randomUUID());

        ResponseDestinationValidator validator = validatorPermits
                ? new ResponseDestinationValidator(
                        new org.sifunity.responsesconnector.cache.QueueOwnerCache(
                                queueClient, java.time.Duration.ofMinutes(5), 10_000),
                        new org.sifunity.responsesconnector.cache.DestinationEntitlementCache(
                                environmentServiceClient, java.time.Duration.ofSeconds(30), 10_000))
                : failingValidator();

        return new Fixture(
                new ResponseRoutingService(queueClient, messageBuilder, tracingUtils,
                        legacyTagLookupService, validator),
                queueClient, validator);
    }

    /** A validator that fails the test if it is ever consulted. */
    private static ResponseDestinationValidator failingValidator() {
        ResponseDestinationValidator validator = mock(ResponseDestinationValidator.class);
        doThrow(new AssertionError("The internal path must NOT consult the destination gate"))
                .when(validator).requireEntitledDestination(
                        anyString(), anyString(), any(UUID.class), any(), any(), any(), any());
        return validator;
    }

    private static ResponseDTO responseFor(String serviceName) {
        ResponseDTO dto = new ResponseDTO();
        dto.setServiceName(serviceName);
        dto.setServiceType("OBJECT");
        return dto;
    }

    /**
     * Property 1 (Requirement 3.1) — the legitimate case: a provider that does NOT own the
     * destination queue is still delivered, provided the owner is entitled.
     *
     * <p>This is the guard against reusing the request-side ownership predicate here. The caller
     * and the queue owner are deliberately different environments, which is the normal shape of
     * every delayed response.</p>
     */
    @Property(tries = 20)
    @Label("A provider not owning the destination queue IS still delivered (ownership is the wrong predicate)")
    void providerNotOwningTheQueueIsStillDelivered(
            @ForAll("uuids") UUID providerEnvId,
            @ForAll("uuids") UUID consumerQueueId,
            @ForAll("uuids") UUID consumerEnvId,
            @ForAll("requestIds") String requestId) {

        // The queue is owned by the CONSUMER, and the caller is the PROVIDER — deliberately unequal.
        Fixture fixture = fixture(consumerEnvId, true, true);

        Throwable thrown = catchThrowable(() -> fixture.service().routeResponse(
                DOMAIN, SOLUTION, providerEnvId, "ProviderEnv", consumerQueueId, requestId,
                responseFor("StudentPersonal"), Collections.emptyMap(), ZONE, CONTEXT, SERVICE_PATH));

        assertThat(thrown)
                .as("A provider legitimately names the CONSUMER's queue. Rejecting that would break "
                        + "every delayed response — which is exactly what reusing the request-side "
                        + "ownership check would do")
                .isNull();

        verify(fixture.queueClient())
                .enqueueMessage(anyString(), anyString(), eq(consumerQueueId), any(SifMessageEnvelope.class));
    }

    /**
     * Property 2 (Requirement 3.2) — the internal path is exempt.
     *
     * <p>{@code /internal/**} is token-gated and its {@code queueId} was already validated at
     * request admission by {@code delayed-queue-ownership-fix}, so gating it again would be
     * redundant work on the broker's own hot path. The validator here throws if consulted.</p>
     */
    @Property(tries = 20)
    @Label("The internal response path does NOT consult the destination gate")
    void internalPathIsExempt(
            @ForAll("uuids") UUID queueId,
            @ForAll("requestIds") String requestId) {

        Fixture fixture = fixture(UUID.randomUUID(), true, false);

        Throwable thrown = catchThrowable(() -> fixture.service().routeInternalResponse(
                DOMAIN, SOLUTION, queueId, requestId, SERVICE_PATH,
                responseFor("StudentPersonal"), Collections.emptyMap()));

        assertThat(thrown)
                .as("routeInternalResponse must not invoke the destination gate")
                .isNull();

        verify(fixture.queueClient())
                .enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
    }

    /**
     * Property 3 (Requirement 3.3) — paging still delivers every page.
     *
     * <p>A single delayed request can legitimately produce many responses, one per page, all sharing
     * a {@code requestId}. This design holds no per-request state, so there is no single-use hazard
     * — but the correlation design that was rejected <em>did</em> have one, and asserting this makes
     * a regression toward that shape visible immediately.</p>
     */
    @Property(tries = 20)
    @Label("Every page of a multi-response delayed query is delivered")
    void allPagesAreDelivered(
            @ForAll("uuids") UUID providerEnvId,
            @ForAll("uuids") UUID consumerQueueId,
            @ForAll("uuids") UUID consumerEnvId,
            @ForAll("pageCounts") int pageCount,
            @ForAll("requestIds") String requestId) {

        Fixture fixture = fixture(consumerEnvId, true, true);

        for (int page = 1; page <= pageCount; page++) {
            Throwable thrown = catchThrowable(() -> fixture.service().routeResponse(
                    DOMAIN, SOLUTION, providerEnvId, "ProviderEnv", consumerQueueId, requestId,
                    responseFor("StudentPersonal"), Collections.emptyMap(), ZONE, CONTEXT, SERVICE_PATH));

            assertThat(thrown)
                    .as("page %d of %d was rejected — the gate must not be single-use", page, pageCount)
                    .isNull();
        }

        verify(fixture.queueClient(), times(pageCount))
                .enqueueMessage(anyString(), anyString(), eq(consumerQueueId), any(SifMessageEnvelope.class));
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<Integer> pageCounts() {
        return Arbitraries.integers().between(2, 10);
    }

    @Provide
    Arbitrary<String> requestIds() {
        return Arbitraries.of("req-1", "42", "cert-abc", UUID.randomUUID().toString());
    }
}
