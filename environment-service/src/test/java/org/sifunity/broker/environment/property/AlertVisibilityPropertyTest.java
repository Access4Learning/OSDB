// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the alert visibility predicate logic in isolation.
 *
 * <p>Tests the pure predicate without Spring/DB:
 * <pre>
 *   visible if: environment_id = callerId
 *            OR target_environment_id = callerId
 *            OR target_environment_id IS NULL
 * </pre>
 *
 * <p><strong>Property 3: Creator Visibility</strong> —
 * For any non-admin caller and any alert where environment_id equals the caller's ID,
 * the alert SHALL appear in the caller's list results.</p>
 *
 * <p><strong>Property 4: Target Visibility</strong> —
 * For any non-admin caller and any alert where target_environment_id equals the caller's ID,
 * the alert SHALL appear in the caller's list results.</p>
 *
 * <p><strong>Property 5: Broadcast Visibility</strong> —
 * For any non-admin caller and any alert where target_environment_id is NULL,
 * the alert SHALL appear in the caller's list results.</p>
 *
 * <p><strong>Property 6: Exclusion of Irrelevant Directed Alerts</strong> —
 * For any non-admin caller and any alert where environment_id differs from the caller's ID
 * AND target_environment_id differs from the caller's ID AND target_environment_id is NOT NULL,
 * the alert SHALL NOT appear.</p>
 *
 * <p><strong>Property 7: No Duplicates</strong> —
 * For any non-admin caller who is both creator and target of the same alert,
 * the alert SHALL appear exactly once in filtered results.</p>
 *
 * <p><strong>Property 8: Admin Unrestricted Access</strong> —
 * For any admin caller, all alerts SHALL appear regardless of environment_id or
 * target_environment_id values.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 3.5, 5.3</strong></p>
 */
@Tag("Feature: alert-visibility-expansion, Property 3: Creator Visibility")
@Tag("Feature: alert-visibility-expansion, Property 4: Target Visibility")
@Tag("Feature: alert-visibility-expansion, Property 5: Broadcast Visibility")
@Tag("Feature: alert-visibility-expansion, Property 6: Exclusion of Irrelevant Directed Alerts")
@Tag("Feature: alert-visibility-expansion, Property 7: No Duplicates")
@Tag("Feature: alert-visibility-expansion, Property 8: Admin Unrestricted Access")
class AlertVisibilityPropertyTest {

    /**
     * Lightweight record representing an alert for predicate testing.
     */
    record TestAlert(UUID id, UUID environmentId, UUID targetEnvironmentId) {}

    /**
     * Implements the same visibility predicate as the JPQL query:
     * visible if: environment_id = callerId OR target_environment_id = callerId OR target_environment_id IS NULL
     *
     * Uses DISTINCT semantics (returns each alert at most once).
     */
    static List<TestAlert> findVisibleAlerts(List<TestAlert> allAlerts, UUID callerId) {
        return allAlerts.stream()
                .filter(a -> a.environmentId().equals(callerId)
                        || callerId.equals(a.targetEnvironmentId())
                        || a.targetEnvironmentId() == null)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Admin visibility: no filtering, all alerts returned.
     */
    static List<TestAlert> findAllAlerts(List<TestAlert> allAlerts) {
        return new ArrayList<>(allAlerts);
    }

    // ========== Property 3: Creator Visibility ==========

    /**
     * Property 3: Creator Visibility.
     *
     * <p>For any non-admin caller and any alert where environment_id equals the caller's ID,
     * the alert SHALL appear in the caller's list results.</p>
     *
     * <p><strong>Validates: Requirements 2.1, 3.1</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: alert-visibility-expansion, Property 3: Creator Visibility")
    void creatorAlertIsAlwaysVisible(
            @ForAll("randomCallerId") UUID callerId,
            @ForAll("randomAlertSets") List<TestAlert> otherAlerts) {

        // Create an alert where caller is the creator
        UUID targetId = UUID.randomUUID(); // some other environment
        TestAlert creatorAlert = new TestAlert(UUID.randomUUID(), callerId, targetId);

        List<TestAlert> allAlerts = new ArrayList<>(otherAlerts);
        allAlerts.add(creatorAlert);

        List<TestAlert> visible = findVisibleAlerts(allAlerts, callerId);

        assertThat(visible)
                .as("Alert created by caller (environment_id = callerId) must be visible")
                .contains(creatorAlert);
    }

    // ========== Property 4: Target Visibility ==========

    /**
     * Property 4: Target Visibility.
     *
     * <p>For any non-admin caller and any alert where target_environment_id equals the caller's ID,
     * the alert SHALL appear in the caller's list results.</p>
     *
     * <p><strong>Validates: Requirements 2.2, 3.2</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: alert-visibility-expansion, Property 4: Target Visibility")
    void targetedAlertIsVisibleToTarget(
            @ForAll("randomCallerId") UUID callerId,
            @ForAll("randomAlertSets") List<TestAlert> otherAlerts) {

        // Create an alert directed at the caller (target_environment_id = callerId)
        UUID creatorId = UUID.randomUUID(); // some other environment created it
        TestAlert targetedAlert = new TestAlert(UUID.randomUUID(), creatorId, callerId);

        List<TestAlert> allAlerts = new ArrayList<>(otherAlerts);
        allAlerts.add(targetedAlert);

        List<TestAlert> visible = findVisibleAlerts(allAlerts, callerId);

        assertThat(visible)
                .as("Alert targeted at caller (target_environment_id = callerId) must be visible")
                .contains(targetedAlert);
    }

    // ========== Property 5: Broadcast Visibility ==========

    /**
     * Property 5: Broadcast Visibility.
     *
     * <p>For any non-admin caller and any alert where target_environment_id is NULL,
     * the alert SHALL appear in the caller's list results.</p>
     *
     * <p><strong>Validates: Requirements 2.3, 3.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: alert-visibility-expansion, Property 5: Broadcast Visibility")
    void broadcastAlertIsAlwaysVisible(
            @ForAll("randomCallerId") UUID callerId,
            @ForAll("randomAlertSets") List<TestAlert> otherAlerts) {

        // Create a broadcast alert (target_environment_id = NULL)
        UUID creatorId = UUID.randomUUID();
        TestAlert broadcastAlert = new TestAlert(UUID.randomUUID(), creatorId, null);

        List<TestAlert> allAlerts = new ArrayList<>(otherAlerts);
        allAlerts.add(broadcastAlert);

        List<TestAlert> visible = findVisibleAlerts(allAlerts, callerId);

        assertThat(visible)
                .as("Broadcast alert (target_environment_id IS NULL) must be visible to all callers")
                .contains(broadcastAlert);
    }

    // ========== Property 6: Exclusion of Irrelevant Directed Alerts ==========

    /**
     * Property 6: Exclusion of Irrelevant Directed Alerts.
     *
     * <p>For any non-admin caller and any alert where environment_id differs from the caller's ID
     * AND target_environment_id differs from the caller's ID AND target_environment_id is NOT NULL,
     * the alert SHALL NOT appear.</p>
     *
     * <p><strong>Validates: Requirements 2.4, 3.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: alert-visibility-expansion, Property 6: Exclusion of Irrelevant Directed Alerts")
    void irrelevantDirectedAlertIsExcluded(
            @ForAll("randomCallerId") UUID callerId,
            @ForAll("randomAlertSets") List<TestAlert> otherAlerts) {

        // Create an alert where caller is neither creator nor target, and target is NOT NULL
        UUID otherCreator = UUID.randomUUID();
        UUID otherTarget = UUID.randomUUID();
        // Ensure otherCreator and otherTarget differ from callerId
        while (otherCreator.equals(callerId)) { otherCreator = UUID.randomUUID(); }
        while (otherTarget.equals(callerId)) { otherTarget = UUID.randomUUID(); }

        TestAlert irrelevantAlert = new TestAlert(UUID.randomUUID(), otherCreator, otherTarget);

        List<TestAlert> allAlerts = new ArrayList<>(otherAlerts);
        allAlerts.add(irrelevantAlert);

        List<TestAlert> visible = findVisibleAlerts(allAlerts, callerId);

        assertThat(visible)
                .as("Alert where caller is neither creator nor target (and target is non-null) must NOT be visible")
                .doesNotContain(irrelevantAlert);
    }

    // ========== Property 7: No Duplicates ==========

    /**
     * Property 7: No Duplicates.
     *
     * <p>For any non-admin caller who is both the creator and the target of the same alert,
     * the alert SHALL appear exactly once in the list results.</p>
     *
     * <p><strong>Validates: Requirements 2.6, 5.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: alert-visibility-expansion, Property 7: No Duplicates")
    void dualRoleAlertAppearsExactlyOnce(
            @ForAll("randomCallerId") UUID callerId,
            @ForAll("randomAlertSets") List<TestAlert> otherAlerts) {

        // Create an alert where caller is BOTH creator AND target
        TestAlert dualRoleAlert = new TestAlert(UUID.randomUUID(), callerId, callerId);

        List<TestAlert> allAlerts = new ArrayList<>(otherAlerts);
        allAlerts.add(dualRoleAlert);

        List<TestAlert> visible = findVisibleAlerts(allAlerts, callerId);

        long count = visible.stream().filter(a -> a.id().equals(dualRoleAlert.id())).count();
        assertThat(count)
                .as("Alert where caller is both creator and target must appear exactly once")
                .isEqualTo(1);
    }

    // ========== Property 8: Admin Unrestricted Access ==========

    /**
     * Property 8: Admin Unrestricted Access.
     *
     * <p>For any admin caller, all alerts SHALL appear regardless of environment_id
     * or target_environment_id values.</p>
     *
     * <p><strong>Validates: Requirements 2.5, 3.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: alert-visibility-expansion, Property 8: Admin Unrestricted Access")
    void adminSeesAllAlerts(
            @ForAll("randomMixedAlertSets") List<TestAlert> allAlerts) {

        List<TestAlert> adminVisible = findAllAlerts(allAlerts);

        assertThat(adminVisible)
                .as("Admin must see all alerts with no filtering")
                .containsExactlyInAnyOrderElementsOf(allAlerts);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UUID> randomCallerId() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * Generates a list of alerts with random environment_id and target_environment_id values.
     * Target may be null (broadcast) or a random UUID (directed).
     */
    @Provide
    Arbitrary<List<TestAlert>> randomAlertSets() {
        Arbitrary<TestAlert> alertArb = Combinators.combine(
                Arbitraries.create(UUID::randomUUID),  // id
                Arbitraries.create(UUID::randomUUID),  // environmentId
                Arbitraries.create(UUID::randomUUID)   // targetEnvironmentId candidate
                        .injectNull(0.3)               // 30% chance of broadcast (NULL target)
        ).as(TestAlert::new);

        return alertArb.list().ofMinSize(1).ofMaxSize(10);
    }

    /**
     * Generates a mixed set of alerts for admin test — includes directed and broadcast alerts
     * from various environments.
     */
    @Provide
    Arbitrary<List<TestAlert>> randomMixedAlertSets() {
        Arbitrary<TestAlert> alertArb = Combinators.combine(
                Arbitraries.create(UUID::randomUUID),  // id
                Arbitraries.create(UUID::randomUUID),  // environmentId
                Arbitraries.create(UUID::randomUUID)   // targetEnvironmentId candidate
                        .injectNull(0.3)               // 30% broadcast
        ).as(TestAlert::new);

        return alertArb.list().ofMinSize(2).ofMaxSize(15);
    }
}
