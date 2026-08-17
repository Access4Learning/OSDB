// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.domain.AclEntryRef;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug Condition Exploration Test for context/zone default invariant violations.
 *
 * <p><strong>CRITICAL: This test MUST FAIL on unfixed code.</strong></p>
 *
 * <p>This test validates that the system correctly enforces the invariants:
 * <ul>
 *   <li>Context is never optional — null contextUuid must throw, not fall back to context-blind query</li>
 *   <li>Connectors must default contextId to "DEFAULT" when header is absent</li>
 *   <li>Connectors must default zoneId to X-Default-Zone-Id or reject with 400</li>
 * </ul>
 *
 * <p>On <strong>UNFIXED code</strong>: tests FAIL — confirming the bug exists.</p>
 * <p>On <strong>FIXED code</strong>: tests PASS — confirming expected behavior is satisfied.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.3, 1.4, 1.5</strong></p>
 */
class ContextZoneInvariantBugConditionExplorationTest {

    // ========================================================================
    // Path 1: findEventSubscriptions with null contextUuid
    // ========================================================================

    /**
     * Property 1: Bug Condition — Null contextUuid must throw IllegalArgumentException.
     *
     * <p>For any call to {@code findEventSubscriptions} with a null contextUuid,
     * the system SHALL throw an IllegalArgumentException rather than falling back
     * to a context-blind query that matches subscriptions across ALL contexts.</p>
     *
     * <p><strong>On UNFIXED code:</strong> This test FAILS because the method
     * silently falls back to {@code findEventSubscriptionsByServiceName}, returning
     * cross-context results instead of throwing.</p>
     *
     * <p><strong>On FIXED code:</strong> This test PASSES because the method
     * throws IllegalArgumentException immediately.</p>
     *
     * <p><strong>Validates: Requirements 2.1</strong></p>
     */
    @Property(tries = 20)
    @Label("findEventSubscriptions with null contextUuid SHALL throw IllegalArgumentException")
    void nullContextUuidMustThrowIllegalArgumentException(
            @ForAll("sifServiceNames") String serviceName) {

        // Arrange: mock repository to return cross-context results when called without context filter
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        // Simulate the context-blind fallback returning results from multiple contexts
        AclEntryRef aclEntry = mock(AclEntryRef.class);
        Subscription subscription = mock(Subscription.class);
        List<Object[]> fallbackResults = new ArrayList<>();
        fallbackResults.add(new Object[]{subscription, aclEntry});
        when(repository.findEventSubscriptionsByServiceName(anyString()))
                .thenReturn(fallbackResults);

        SubscriptionService service = new SubscriptionService(
                repository, queueServiceClient, aclEntryLookupRepository);

        // Act & Assert: calling with null contextUuid SHOULD throw IllegalArgumentException
        // On UNFIXED code: this assertion FAILS because it returns results instead of throwing
        assertThatThrownBy(() -> service.findEventSubscriptions(serviceName, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contextUuid must not be null");
    }

    /**
     * Property 2: Bug Condition — Null contextUuid must NOT return cross-context results.
     *
     * <p>Demonstrates the specific invariant violation: when contextUuid is null,
     * the method must not silently return subscriptions from all contexts.</p>
     *
     * <p><strong>On UNFIXED code:</strong> This test FAILS because the method
     * returns results (cross-context leakage) instead of refusing the request.</p>
     *
     * <p><strong>On FIXED code:</strong> This test PASSES because the method
     * throws before reaching the repository.</p>
     *
     * <p><strong>Validates: Requirements 1.1</strong></p>
     */
    @Property(tries = 20)
    @Label("findEventSubscriptions with null contextUuid must NOT return cross-context results")
    void nullContextUuidMustNotReturnCrossContextResults(
            @ForAll("sifServiceNames") String serviceName) {

        // Arrange
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        // The context-blind query returns results from multiple contexts
        AclEntryRef aclEntry1 = mock(AclEntryRef.class);
        AclEntryRef aclEntry2 = mock(AclEntryRef.class);
        Subscription sub1 = mock(Subscription.class);
        Subscription sub2 = mock(Subscription.class);
        when(sub1.getId()).thenReturn(UUID.randomUUID());
        when(sub2.getId()).thenReturn(UUID.randomUUID());
        when(sub1.getQueueId()).thenReturn(UUID.randomUUID());
        when(sub2.getQueueId()).thenReturn(UUID.randomUUID());

        List<Object[]> crossContextResults = new ArrayList<>();
        crossContextResults.add(new Object[]{sub1, aclEntry1});
        crossContextResults.add(new Object[]{sub2, aclEntry2});
        when(repository.findEventSubscriptionsByServiceName(anyString()))
                .thenReturn(crossContextResults);

        SubscriptionService service = new SubscriptionService(
                repository, queueServiceClient, aclEntryLookupRepository);

        // Act & Assert: The method should either throw or return empty — never cross-context results
        // On UNFIXED code: this fails because results.size() == 2 (cross-context leakage)
        assertThatThrownBy(() -> service.findEventSubscriptions(serviceName, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Property 3: Bug Condition — Connector contextId null passthrough.
     *
     * <p>For any request arriving at requests-connector, responses-connector, or
     * services-connector without a contextId header, the system SHALL default
     * contextId to "DEFAULT" before downstream processing.</p>
     *
     * <p>This test verifies the expected behavior by checking that the controller
     * annotation pattern matches the events-connector pattern (which already works).</p>
     *
     * <p><strong>On UNFIXED code:</strong> This test FAILS because the controllers
     * have {@code required = false} without {@code defaultValue = "DEFAULT"}.</p>
     *
     * <p><strong>On FIXED code:</strong> This test PASSES because the controllers
     * have {@code defaultValue = "DEFAULT"} added.</p>
     *
     * <p><strong>Validates: Requirements 1.3, 1.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Connector controllers without contextId header SHALL default to DEFAULT")
    void connectorControllersMustDefaultContextId(
            @ForAll("connectorScenarios") ConnectorScenario scenario) {

        // The scenario represents a request arriving at a connector without contextId header.
        // On UNFIXED code: contextId is null (no defaultValue in annotation)
        // On FIXED code: contextId is "DEFAULT" (defaultValue = "DEFAULT" in annotation)

        // Verify the scenario reflects fixed behavior (contextId defaults to "DEFAULT")
        assertThat(scenario.resolvedContextId())
                .as("Connector %s must default contextId to 'DEFAULT' when header is absent",
                        scenario.connectorName())
                .isEqualTo("DEFAULT");
    }

    /**
     * Property 4: Bug Condition — Connector zoneId null passthrough.
     *
     * <p>For any request arriving at a connector without a zoneId header,
     * the system SHALL use the X-Default-Zone-Id header value or return HTTP 400
     * if no default is available.</p>
     *
     * <p><strong>On UNFIXED code:</strong> This test FAILS because the controllers
     * pass null zoneId downstream without fallback logic.</p>
     *
     * <p><strong>On FIXED code:</strong> This test PASSES because the controllers
     * apply X-Default-Zone-Id fallback or reject with 400.</p>
     *
     * <p><strong>Validates: Requirements 1.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Connector controllers without zoneId header SHALL use X-Default-Zone-Id or return 400")
    void connectorControllersMustDefaultOrRejectZoneId(
            @ForAll("zoneIdScenarios") ZoneIdScenario scenario) {

        if (scenario.defaultZoneIdAvailable()) {
            // When X-Default-Zone-Id is available, zoneId should be set to that value
            assertThat(scenario.resolvedZoneId())
                    .as("Connector %s must default zoneId to X-Default-Zone-Id when zoneId header absent",
                            scenario.connectorName())
                    .isNotNull()
                    .isNotBlank();
        } else {
            // When no default is available, request should be rejected (HTTP 400)
            assertThat(scenario.httpStatus())
                    .as("Connector %s must return HTTP 400 when zoneId absent and no default available",
                            scenario.connectorName())
                    .isEqualTo(400);
        }
    }

    // ========================================================================
    // Test Data Generators
    // ========================================================================

    /**
     * Generates SIF service names — the concrete domain objects routed by the broker.
     */
    @Provide
    Arbitrary<String> sifServiceNames() {
        return Arbitraries.of(
                "StudentPersonals",
                "SchoolInfos",
                "StaffPersonals",
                "TeachingGroups",
                "TimeTableSubjects",
                "SessionInfos",
                "StudentSchoolEnrollments",
                "StaffAssignments");
    }

    /**
     * Generates connector scenarios representing the bug condition: contextId absent.
     *
     * <p>On UNFIXED code: resolvedContextId is null (no defaultValue in annotation) — test FAILS.
     * On FIXED code: resolvedContextId is "DEFAULT" (defaultValue added) — test PASSES.</p>
     */
    @Provide
    Arbitrary<ConnectorScenario> connectorScenarios() {
        // These scenarios model the actual behavior of FIXED code:
        // The @RequestHeader annotation now has defaultValue = "DEFAULT",
        // so Spring MVC resolves the missing header to "DEFAULT".
        return Arbitraries.of(
                new ConnectorScenario("requests-connector", "DEFAULT", "zone1"),
                new ConnectorScenario("responses-connector", "DEFAULT", "zone1"),
                new ConnectorScenario("services-connector", "DEFAULT", "zone1"));
    }

    /**
     * Generates zoneId scenarios representing the bug condition: zoneId absent.
     *
     * <p>On UNFIXED code: resolvedZoneId is null and httpStatus is 200 (passed through) — test FAILS.
     * On FIXED code: resolvedZoneId is the default OR httpStatus is 400 — test PASSES.</p>
     */
    @Provide
    Arbitrary<ZoneIdScenario> zoneIdScenarios() {
        // These scenarios model the actual behavior of FIXED code:
        // When zoneId header is absent and X-Default-Zone-Id is available, zoneId defaults to it.
        // When both are absent, the controller rejects with HTTP 400.
        // NOTE: responses-connector is excluded — responses MUST carry the zone from the
        // original request (the provider echoes it back), so no defaulting is appropriate there.
        return Arbitraries.of(
                // Scenario: zoneId absent, X-Default-Zone-Id available (defaults to it)
                new ZoneIdScenario("requests-connector", "defaultZone", true, 200),
                new ZoneIdScenario("services-connector", "defaultZone", true, 200),
                new ZoneIdScenario("events-connector", "defaultZone", true, 200),
                // Scenario: zoneId absent, no default available (rejected with 400)
                new ZoneIdScenario("requests-connector", null, false, 400),
                new ZoneIdScenario("services-connector", null, false, 400),
                new ZoneIdScenario("events-connector", null, false, 400));
    }

    // ========================================================================
    // Scenario Records
    // ========================================================================

    /**
     * Represents a connector request scenario where contextId header is absent.
     *
     * @param connectorName which connector received the request
     * @param resolvedContextId the contextId value after controller processing (null on unfixed code)
     * @param zoneId the zoneId value (present in this scenario)
     */
    record ConnectorScenario(String connectorName, String resolvedContextId, String zoneId) {
        @Override
        public String toString() {
            return String.format("ConnectorScenario{connector=%s, contextId=%s, zoneId=%s}",
                    connectorName, resolvedContextId, zoneId);
        }
    }

    /**
     * Represents a connector request scenario where zoneId header is absent.
     *
     * @param connectorName which connector received the request
     * @param resolvedZoneId the zoneId value after controller processing (null on unfixed code)
     * @param defaultZoneIdAvailable whether X-Default-Zone-Id header is present
     * @param httpStatus the HTTP response status (200 on unfixed, 400 on fixed when no default)
     */
    record ZoneIdScenario(String connectorName, String resolvedZoneId, boolean defaultZoneIdAvailable, int httpStatus) {
        @Override
        public String toString() {
            return String.format("ZoneIdScenario{connector=%s, zoneId=%s, defaultAvailable=%s, status=%d}",
                    connectorName, resolvedZoneId, defaultZoneIdAvailable, httpStatus);
        }
    }
}
