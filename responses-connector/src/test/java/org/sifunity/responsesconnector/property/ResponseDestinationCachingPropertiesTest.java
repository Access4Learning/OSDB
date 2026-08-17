// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.property;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.responsesconnector.cache.DestinationEntitlementCache;
import org.sifunity.responsesconnector.cache.QueueOwnerCache;
import org.sifunity.responsesconnector.client.EnvironmentServiceClient;
import org.sifunity.responsesconnector.client.QueueServiceClient;
import org.sifunity.responsesconnector.exception.ResponseDestinationDeniedException;
import org.sifunity.responsesconnector.service.ResponseDestinationValidator;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caching properties for the response destination gate (task 3.3 / requirement 2.9).
 *
 * <p>Two caches sit behind the gate and they must behave differently, because what they cache
 * differs in mutability: queue ownership is immutable (generous TTL) while ACL grants are revocable
 * (~30s TTL). The properties here pin the invariants that are easy to break by copying one cache's
 * configuration onto the other, or by "simplifying" the positives-only rule.</p>
 *
 * <p>The load-bearing rule in both caches is <b>cache positives only</b>. Both underlying lookups
 * fail closed, so a negative result is indistinguishable from a transient outage; caching it would
 * convert a momentary blip into a sticky denial for the whole TTL. A cache miss must mean
 * <em>fetch</em>, never <em>deny</em>.</p>
 */
class ResponseDestinationCachingPropertiesTest {

    private static final String DOMAIN = "test-domain";
    private static final String SOLUTION = "test-solution";
    private static final String SERVICE = "StudentPersonals";
    private static final String ZONE = "test-zone";
    private static final String CONTEXT = "DEFAULT";

    private record Fixture(
            ResponseDestinationValidator validator,
            QueueServiceClient queueClient,
            EnvironmentServiceClient environmentClient) {
    }

    private static Fixture fixture(UUID ownerOrNull, boolean entitled, Duration entitlementTtl) {
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        EnvironmentServiceClient environmentClient = mock(EnvironmentServiceClient.class);

        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class)))
                .thenReturn(ownerOrNull);
        when(environmentClient.hasAnyGrant(anyString(), anyString(), any(UUID.class), anyString(), any(), any()))
                .thenReturn(entitled);

        return new Fixture(
                new ResponseDestinationValidator(
                        new QueueOwnerCache(queueClient, Duration.ofMinutes(5), 10_000),
                        new DestinationEntitlementCache(environmentClient, entitlementTtl, 10_000)),
                queueClient,
                environmentClient);
    }

    /**
     * Paging is the motivating case: one admitted request yields N correlated responses, and each
     * would otherwise re-query both services.
     */
    @Property(tries = 20)
    void repeatedAdmissionsQueryEachServiceOnlyOnce(@ForAll @IntRange(min = 2, max = 12) int submissions) {
        UUID owner = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Fixture f = fixture(owner, true, Duration.ofSeconds(30));

        for (int i = 0; i < submissions; i++) {
            f.validator().requireEntitledDestination(
                    DOMAIN, SOLUTION, queueId, SERVICE, ZONE, CONTEXT, UUID.randomUUID());
        }

        verify(f.queueClient(), times(1))
                .getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class));
        verify(f.environmentClient(), times(1))
                .hasAnyGrant(anyString(), anyString(), any(UUID.class), anyString(), any(), any());
    }

    /**
     * An unresolvable owner must not be cached — otherwise one queue-service blip pins a
     * legitimately-owned queue into denials for the whole TTL.
     */
    @Property(tries = 20)
    void unresolvedOwnerIsNeverCached(@ForAll @IntRange(min = 2, max = 8) int attempts) {
        UUID queueId = UUID.randomUUID();
        Fixture f = fixture(null, true, Duration.ofSeconds(30));

        for (int i = 0; i < attempts; i++) {
            assertThatThrownBy(() -> f.validator().requireEntitledDestination(
                    DOMAIN, SOLUTION, queueId, SERVICE, ZONE, CONTEXT, UUID.randomUUID()))
                    .isInstanceOf(ResponseDestinationDeniedException.class);
        }

        // Every attempt must have re-queried: a miss means fetch, never deny.
        verify(f.queueClient(), times(attempts))
                .getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class));
    }

    /**
     * A negative entitlement must not be cached, for the same reason: {@code hasAnyGrant} fails
     * closed, so {@code false} may be a transient failure rather than a real denial.
     */
    @Property(tries = 20)
    void negativeEntitlementIsNeverCached(@ForAll @IntRange(min = 2, max = 8) int attempts) {
        UUID queueId = UUID.randomUUID();
        Fixture f = fixture(UUID.randomUUID(), false, Duration.ofSeconds(30));

        for (int i = 0; i < attempts; i++) {
            assertThatThrownBy(() -> f.validator().requireEntitledDestination(
                    DOMAIN, SOLUTION, queueId, SERVICE, ZONE, CONTEXT, UUID.randomUUID()))
                    .isInstanceOf(ResponseDestinationDeniedException.class);
        }

        verify(f.environmentClient(), times(attempts))
                .hasAnyGrant(anyString(), anyString(), any(UUID.class), anyString(), any(), any());
    }

    /**
     * A revoked grant must stop admitting once the entitlement TTL lapses. Uses a deliberately tiny
     * TTL so the expiry is observable without a clock abstraction.
     */
    @Property(tries = 5)
    void revokedGrantStopsAdmittingAfterEntitlementTtl(@ForAll @IntRange(min = 1, max = 3) int ignored)
            throws InterruptedException {
        UUID queueId = UUID.randomUUID();
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        EnvironmentServiceClient environmentClient = mock(EnvironmentServiceClient.class);

        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class)))
                .thenReturn(UUID.randomUUID());
        // Entitled at first, revoked thereafter.
        when(environmentClient.hasAnyGrant(anyString(), anyString(), any(UUID.class), anyString(), any(), any()))
                .thenReturn(true, false);

        ResponseDestinationValidator validator = new ResponseDestinationValidator(
                new QueueOwnerCache(queueClient, Duration.ofMinutes(5), 10_000),
                new DestinationEntitlementCache(environmentClient, Duration.ofMillis(50), 10_000));

        // Admitted while the grant holds.
        validator.requireEntitledDestination(
                DOMAIN, SOLUTION, queueId, SERVICE, ZONE, CONTEXT, UUID.randomUUID());

        Thread.sleep(120);

        // After the TTL lapses the revocation must take effect.
        assertThatThrownBy(() -> validator.requireEntitledDestination(
                DOMAIN, SOLUTION, queueId, SERVICE, ZONE, CONTEXT, UUID.randomUUID()))
                .isInstanceOf(ResponseDestinationDeniedException.class);
    }

    /**
     * The asymmetry itself: ownership is immutable so it stays cached across an entitlement
     * expiry. This is what fails if someone copies the 30s TTL onto the queue-owner cache.
     */
    @Property(tries = 5)
    void ownershipStaysCachedAcrossAnEntitlementExpiry(@ForAll @IntRange(min = 1, max = 3) int ignored)
            throws InterruptedException {
        UUID queueId = UUID.randomUUID();
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        EnvironmentServiceClient environmentClient = mock(EnvironmentServiceClient.class);

        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class)))
                .thenReturn(UUID.randomUUID());
        when(environmentClient.hasAnyGrant(anyString(), anyString(), any(UUID.class), anyString(), any(), any()))
                .thenReturn(true);

        ResponseDestinationValidator validator = new ResponseDestinationValidator(
                new QueueOwnerCache(queueClient, Duration.ofMinutes(5), 10_000),
                new DestinationEntitlementCache(environmentClient, Duration.ofMillis(50), 10_000));

        validator.requireEntitledDestination(
                DOMAIN, SOLUTION, queueId, SERVICE, ZONE, CONTEXT, UUID.randomUUID());

        Thread.sleep(120);

        validator.requireEntitledDestination(
                DOMAIN, SOLUTION, queueId, SERVICE, ZONE, CONTEXT, UUID.randomUUID());

        // Entitlement was re-fetched after expiry; ownership was not.
        verify(queueClient, times(1))
                .getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class));
        verify(environmentClient, times(2))
                .hasAnyGrant(anyString(), anyString(), any(UUID.class), anyString(), any(), any());
    }

    /** A cached positive must not be reused for a different queue. */
    @Property(tries = 20)
    void positivesAreNotSharedAcrossQueues(@ForAll @IntRange(min = 2, max = 6) int queues) {
        Fixture f = fixture(UUID.randomUUID(), true, Duration.ofSeconds(30));

        for (int i = 0; i < queues; i++) {
            f.validator().requireEntitledDestination(
                    DOMAIN, SOLUTION, UUID.randomUUID(), SERVICE, ZONE, CONTEXT, UUID.randomUUID());
        }

        // Distinct queues => distinct owner lookups.
        verify(f.queueClient(), times(queues))
                .getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class));
        assertThat(queues).isGreaterThan(1);
    }
}
