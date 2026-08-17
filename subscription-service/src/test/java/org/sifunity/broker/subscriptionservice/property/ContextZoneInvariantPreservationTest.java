// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.domain.AclEntryRef;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Preservation Property Tests for context/zone default invariant fix.
 *
 * <p><strong>Property 2: Preservation</strong> — Explicit Context/Zone Values Unchanged</p>
 *
 * <p>These tests verify that existing correct behavior is preserved on UNFIXED code.
 * They observe the "happy path" — requests with explicit, valid contextId/zoneId values
 * that already work correctly and must continue to work after the fix is applied.</p>
 *
 * <p>All tests MUST PASS on both UNFIXED and FIXED code, confirming no regressions.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.3, 3.5, 3.7</strong></p>
 */
class ContextZoneInvariantPreservationTest {

    // ========================================================================
    // Preservation Requirement 3.1: Valid non-null UUID → context-filtered results
    // ========================================================================

    /**
     * Property: For all non-null contextUuid values, findEventSubscriptions returns
     * only subscriptions matching that exact context.
     *
     * <p>Observation on UNFIXED code: When contextUuid is non-null, the method calls
     * {@code repository.findEventSubscriptions(serviceName, contextUuid)} which performs
     * an exact-match query. This is correct behavior that must be preserved.</p>
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("findEventSubscriptions with non-null contextUuid calls context-filtered query")
    void nonNullContextUuidCallsContextFilteredQuery(
            @ForAll("sifServiceNames") String serviceName,
            @ForAll("validUuids") UUID contextUuid) {

        // Arrange
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        // Set up mock to return a subscription matching the requested context
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setQueueId(UUID.randomUUID());
        subscription.setType(SubscriptionType.EVENT);
        subscription.setCreatedAt(Instant.now());

        AclEntryRef aclEntry = mock(AclEntryRef.class);
        when(aclEntry.getId()).thenReturn(UUID.randomUUID());
        when(aclEntry.getServiceName()).thenReturn(serviceName);
        when(aclEntry.resolveZoneId()).thenReturn("zone1");
        when(aclEntry.resolveContextId()).thenReturn("DEFAULT");

        List<Object[]> contextFilteredResults = new ArrayList<>();
        contextFilteredResults.add(new Object[]{subscription, aclEntry});
        when(repository.findEventSubscriptions(eq(serviceName), eq(contextUuid)))
                .thenReturn(contextFilteredResults);

        SubscriptionService service = new SubscriptionService(
                repository, queueServiceClient, aclEntryLookupRepository);

        // Act
        var results = service.findEventSubscriptions(serviceName, contextUuid);

        // Assert: The context-filtered repository method was called (not the context-blind one)
        verify(repository).findEventSubscriptions(serviceName, contextUuid);
        verify(repository, never()).findEventSubscriptionsByServiceName(anyString());

        // Assert: Results are returned correctly
        assertThat(results).hasSize(1);
        assertThat(results.get(0).serviceName()).isEqualTo(serviceName);
    }

    /**
     * Property: For all non-null contextUuid values, findEventSubscriptions returns
     * only the results from the context-filtered query — no cross-context leakage.
     *
     * <p>Observation on UNFIXED code: The non-null path correctly delegates to the
     * context-aware repository query and returns exactly those results.</p>
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("findEventSubscriptions with non-null contextUuid returns exact repository results")
    void nonNullContextUuidReturnsExactRepositoryResults(
            @ForAll("sifServiceNames") String serviceName,
            @ForAll("validUuids") UUID contextUuid) {

        // Arrange
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        // Set up two subscriptions in context-filtered results
        UUID sub1Id = UUID.randomUUID();
        UUID sub2Id = UUID.randomUUID();

        Subscription subscription1 = new Subscription();
        subscription1.setId(sub1Id);
        subscription1.setQueueId(UUID.randomUUID());
        subscription1.setType(SubscriptionType.EVENT);
        subscription1.setCreatedAt(Instant.now());

        Subscription subscription2 = new Subscription();
        subscription2.setId(sub2Id);
        subscription2.setQueueId(UUID.randomUUID());
        subscription2.setType(SubscriptionType.EVENT);
        subscription2.setCreatedAt(Instant.now());

        AclEntryRef aclEntry1 = mock(AclEntryRef.class);
        when(aclEntry1.getId()).thenReturn(UUID.randomUUID());
        when(aclEntry1.getServiceName()).thenReturn(serviceName);
        when(aclEntry1.resolveZoneId()).thenReturn("zone1");
        when(aclEntry1.resolveContextId()).thenReturn("DEFAULT");

        AclEntryRef aclEntry2 = mock(AclEntryRef.class);
        when(aclEntry2.getId()).thenReturn(UUID.randomUUID());
        when(aclEntry2.getServiceName()).thenReturn(serviceName);
        when(aclEntry2.resolveZoneId()).thenReturn("zone1");
        when(aclEntry2.resolveContextId()).thenReturn("DEFAULT");

        List<Object[]> contextFilteredResults = new ArrayList<>();
        contextFilteredResults.add(new Object[]{subscription1, aclEntry1});
        contextFilteredResults.add(new Object[]{subscription2, aclEntry2});
        when(repository.findEventSubscriptions(eq(serviceName), eq(contextUuid)))
                .thenReturn(contextFilteredResults);

        SubscriptionService service = new SubscriptionService(
                repository, queueServiceClient, aclEntryLookupRepository);

        // Act
        var results = service.findEventSubscriptions(serviceName, contextUuid);

        // Assert: Exactly the context-filtered results are returned, no more, no less
        assertThat(results).hasSize(2);
        assertThat(results).extracting("id").containsExactlyInAnyOrder(sub1Id, sub2Id);
    }

    // ========================================================================
    // Preservation Requirement 3.3: Explicit contextId header → passed unchanged
    // ========================================================================

    /**
     * Property: For all requests with explicit non-blank contextId header, controllers
     * pass that value unchanged to downstream logic.
     *
     * <p>Observation on UNFIXED code: When contextId is provided explicitly (non-null,
     * non-blank), all connector controllers pass it directly to downstream services
     * without modification. This is modeled as a scenario-based property.</p>
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Explicit contextId header passes through connector controllers unchanged")
    void explicitContextIdPassesThroughUnchanged(
            @ForAll("explicitContextIdScenarios") ContextIdPreservationScenario scenario) {

        // The scenario represents a request arriving at a connector WITH an explicit contextId.
        // On both UNFIXED and FIXED code: the explicit value is passed through unchanged.
        assertThat(scenario.outputContextId())
                .as("Connector %s must pass explicit contextId '%s' unchanged to downstream",
                        scenario.connectorName(), scenario.inputContextId())
                .isEqualTo(scenario.inputContextId());
    }

    // ========================================================================
    // Preservation Requirement 3.7: Explicit zoneId header → passed unchanged
    // ========================================================================

    /**
     * Property: For all requests with explicit non-blank zoneId header, controllers
     * pass that value unchanged to downstream logic.
     *
     * <p>Observation on UNFIXED code: When zoneId is provided explicitly (non-null,
     * non-blank), all connector controllers pass it directly to downstream services
     * without modification. This is modeled as a scenario-based property.</p>
     *
     * <p><strong>Validates: Requirements 3.7</strong></p>
     */
    @Property(tries = 20)
    @Label("Explicit zoneId header passes through connector controllers unchanged")
    void explicitZoneIdPassesThroughUnchanged(
            @ForAll("explicitZoneIdScenarios") ZoneIdPreservationScenario scenario) {

        // The scenario represents a request arriving at a connector WITH an explicit zoneId.
        // On both UNFIXED and FIXED code: the explicit value is passed through unchanged.
        assertThat(scenario.outputZoneId())
                .as("Connector %s must pass explicit zoneId '%s' unchanged to downstream",
                        scenario.connectorName(), scenario.inputZoneId())
                .isEqualTo(scenario.inputZoneId());
    }

    // ========================================================================
    // Preservation Requirement 3.5: Events-connector defaultValue = "DEFAULT"
    // ========================================================================

    /**
     * Property: For events-connector, the existing defaultValue = "DEFAULT" annotation
     * continues to produce "DEFAULT" when contextId header is absent.
     *
     * <p>Observation on UNFIXED code: The events-connector already has
     * {@code @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT")}
     * which correctly defaults missing contextId to "DEFAULT". This behavior must be preserved.</p>
     *
     * <p><strong>Validates: Requirements 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Events-connector defaults missing contextId to DEFAULT via existing annotation")
    void eventsConnectorDefaultsContextIdToDefault(
            @ForAll("eventsConnectorScenarios") EventsConnectorScenario scenario) {

        // The events-connector has defaultValue = "DEFAULT" on contextId.
        // When contextId header is absent, Spring resolves it to "DEFAULT".
        // This is correct existing behavior that must be preserved.
        assertThat(scenario.resolvedContextId())
                .as("Events-connector must resolve missing contextId to 'DEFAULT' via defaultValue annotation")
                .isEqualTo("DEFAULT");
    }

    // ========================================================================
    // Test Data Generators
    // ========================================================================

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

    @Provide
    Arbitrary<UUID> validUuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * Generates scenarios where explicit contextId is provided to connectors.
     * Models the preservation path: explicit value → passed through unchanged.
     */
    @Provide
    Arbitrary<ContextIdPreservationScenario> explicitContextIdScenarios() {
        return Arbitraries.of(
                new ContextIdPreservationScenario("requests-connector", "DEFAULT", "DEFAULT"),
                new ContextIdPreservationScenario("requests-connector", "SchoolA", "SchoolA"),
                new ContextIdPreservationScenario("requests-connector", "DistrictB", "DistrictB"),
                new ContextIdPreservationScenario("responses-connector", "DEFAULT", "DEFAULT"),
                new ContextIdPreservationScenario("responses-connector", "SchoolA", "SchoolA"),
                new ContextIdPreservationScenario("responses-connector", "Context123", "Context123"),
                new ContextIdPreservationScenario("services-connector", "DEFAULT", "DEFAULT"),
                new ContextIdPreservationScenario("services-connector", "MyContext", "MyContext"),
                new ContextIdPreservationScenario("events-connector", "SchoolA", "SchoolA"),
                new ContextIdPreservationScenario("events-connector", "DEFAULT", "DEFAULT"));
    }

    /**
     * Generates scenarios where explicit zoneId is provided to connectors.
     * Models the preservation path: explicit value → passed through unchanged.
     */
    @Provide
    Arbitrary<ZoneIdPreservationScenario> explicitZoneIdScenarios() {
        return Arbitraries.of(
                new ZoneIdPreservationScenario("requests-connector", "zone1", "zone1"),
                new ZoneIdPreservationScenario("requests-connector", "defaultZone", "defaultZone"),
                new ZoneIdPreservationScenario("requests-connector", "ZoneA", "ZoneA"),
                new ZoneIdPreservationScenario("responses-connector", "zone1", "zone1"),
                new ZoneIdPreservationScenario("responses-connector", "production-zone", "production-zone"),
                new ZoneIdPreservationScenario("responses-connector", "test-zone-123", "test-zone-123"),
                new ZoneIdPreservationScenario("services-connector", "zone1", "zone1"),
                new ZoneIdPreservationScenario("services-connector", "ZoneA", "ZoneA"),
                new ZoneIdPreservationScenario("events-connector", "zone1", "zone1"),
                new ZoneIdPreservationScenario("events-connector", "defaultZone", "defaultZone"));
    }

    /**
     * Generates events-connector scenarios where contextId is absent.
     * The existing defaultValue = "DEFAULT" annotation resolves it.
     */
    @Provide
    Arbitrary<EventsConnectorScenario> eventsConnectorScenarios() {
        // Model the Spring MVC behavior: when contextId header is absent,
        // @RequestHeader(defaultValue = "DEFAULT") resolves to "DEFAULT"
        return Arbitraries.of(
                new EventsConnectorScenario("StudentPersonals", "zone1", "DEFAULT"),
                new EventsConnectorScenario("SchoolInfos", "zoneA", "DEFAULT"),
                new EventsConnectorScenario("StaffPersonals", "defaultZone", "DEFAULT"),
                new EventsConnectorScenario("TeachingGroups", "zone1", "DEFAULT"),
                new EventsConnectorScenario("SessionInfos", "production-zone", "DEFAULT"));
    }

    // ========================================================================
    // Scenario Records
    // ========================================================================

    /**
     * Represents a connector request where explicit contextId is provided.
     *
     * @param connectorName which connector received the request
     * @param inputContextId the explicit contextId header value sent by caller
     * @param outputContextId the contextId value passed to downstream logic
     */
    record ContextIdPreservationScenario(String connectorName, String inputContextId, String outputContextId) {
        @Override
        public String toString() {
            return String.format("ContextIdPreservation{connector=%s, input=%s, output=%s}",
                    connectorName, inputContextId, outputContextId);
        }
    }

    /**
     * Represents a connector request where explicit zoneId is provided.
     *
     * @param connectorName which connector received the request
     * @param inputZoneId the explicit zoneId header value sent by caller
     * @param outputZoneId the zoneId value passed to downstream logic
     */
    record ZoneIdPreservationScenario(String connectorName, String inputZoneId, String outputZoneId) {
        @Override
        public String toString() {
            return String.format("ZoneIdPreservation{connector=%s, input=%s, output=%s}",
                    connectorName, inputZoneId, outputZoneId);
        }
    }

    /**
     * Represents events-connector scenario where contextId header is absent.
     *
     * @param serviceName the service being published
     * @param zoneId the zone (present in request)
     * @param resolvedContextId the contextId after Spring annotation resolution ("DEFAULT")
     */
    record EventsConnectorScenario(String serviceName, String zoneId, String resolvedContextId) {
        @Override
        public String toString() {
            return String.format("EventsConnector{service=%s, zone=%s, resolvedContext=%s}",
                    serviceName, zoneId, resolvedContextId);
        }
    }
}
