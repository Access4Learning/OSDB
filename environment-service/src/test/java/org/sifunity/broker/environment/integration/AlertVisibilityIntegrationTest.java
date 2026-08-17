// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.environment.domain.Alert;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.repository.AlertRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for expanded alert visibility queries.
 *
 * <p>Uses {@code @DataJpaTest} with Testcontainers PostgreSQL (via JDBC URL in
 * application-test.yml) to verify the repository-level visibility predicates
 * against a real database with Flyway-migrated schema.</p>
 *
 * <p>Validates:</p>
 * <ul>
 *   <li>findVisibleAlerts returns alerts created by caller, directed at caller, and broadcasts</li>
 *   <li>findVisibleAlerts excludes alerts directed at other environments</li>
 *   <li>findVisibleAlertById returns/excludes based on visibility rules</li>
 *   <li>Backward compatibility: NULL target_environment_id treated as broadcast</li>
 *   <li>No duplicates when caller is both creator and target</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.6, 4.2, 4.3, 5.1, 5.2, 5.3</strong></p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AlertVisibilityIntegrationTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private SolutionRepository solutionRepository;

    @Autowired
    private DomainRepository domainRepository;

    private Environment envA;
    private Environment envB;
    private Environment envC;

    /** Alert created by envA, targeted at envB. */
    private Alert alertAtoB;

    /** Alert created by envA, no target (broadcast). */
    private Alert alertBroadcast;

    /** Alert created by envB, targeted at envC. */
    private Alert alertBtoC;

    @BeforeEach
    void setUp() {
        // Create domain
        Domain domain = new Domain();
        domain.setDomainId("test-domain-" + UUID.randomUUID().toString().substring(0, 8));
        domain.setDescription("Test domain for alert visibility");
        domain = domainRepository.saveAndFlush(domain);

        // Create solution
        Solution solution = new Solution();
        solution.setDomainUuid(domain.getId());
        solution.setSolutionId("test-solution-" + UUID.randomUUID().toString().substring(0, 8));
        solution.setDescription("Test solution");
        solution = solutionRepository.saveAndFlush(solution);

        // Create three environments
        envA = createEnvironment("AppA", solution.getId());
        envB = createEnvironment("AppB", solution.getId());
        envC = createEnvironment("AppC", solution.getId());

        // Alert created by envA, targeted at envB
        alertAtoB = createAlert(envA, envB.getId(), "Alert from A to B");

        // Alert created by envA, no target (broadcast)
        alertBroadcast = createAlert(envA, null, "Broadcast alert from A");

        // Alert created by envB, targeted at envC
        alertBtoC = createAlert(envB, envC.getId(), "Alert from B to C");
    }

    /**
     * envA should see:
     * - alertAtoB (created by envA)
     * - alertBroadcast (broadcast, created by envA)
     * - alertBtoC should NOT appear (created by envB, targeted at envC)
     *
     * Wait — broadcast alerts are visible to ALL callers, so envA also sees alertBroadcast
     * via broadcast rule. And envA is creator of alertAtoB and alertBroadcast.
     * alertBtoC: envA is not creator, not target, and target is NOT null → excluded.
     */
    @Test
    void findVisibleAlerts_forEnvA_returnsCreatedAndBroadcast() {
        List<Alert> visible = alertRepository.findVisibleAlerts(envA.getId());

        assertThat(visible)
            .extracting(Alert::getId)
            .containsExactlyInAnyOrder(alertAtoB.getId(), alertBroadcast.getId());

        // Verify alertBtoC is excluded
        assertThat(visible)
            .extracting(Alert::getId)
            .doesNotContain(alertBtoC.getId());
    }

    /**
     * envB should see:
     * - alertAtoB (targeted at envB)
     * - alertBroadcast (broadcast, NULL target)
     * - alertBtoC (created by envB)
     */
    @Test
    void findVisibleAlerts_forEnvB_returnsTargetedBroadcastAndCreated() {
        List<Alert> visible = alertRepository.findVisibleAlerts(envB.getId());

        assertThat(visible)
            .extracting(Alert::getId)
            .containsExactlyInAnyOrder(alertAtoB.getId(), alertBroadcast.getId(), alertBtoC.getId());
    }

    /**
     * envC should see:
     * - alertBtoC (targeted at envC)
     * - alertBroadcast (broadcast, NULL target)
     *
     * alertAtoB: envC is not creator, not target, target is NOT null → excluded.
     */
    @Test
    void findVisibleAlerts_forEnvC_returnsTargetedAndBroadcast() {
        List<Alert> visible = alertRepository.findVisibleAlerts(envC.getId());

        assertThat(visible)
            .extracting(Alert::getId)
            .containsExactlyInAnyOrder(alertBtoC.getId(), alertBroadcast.getId());

        // Verify alertAtoB is excluded for envC
        assertThat(visible)
            .extracting(Alert::getId)
            .doesNotContain(alertAtoB.getId());
    }

    /**
     * envC queries alertAtoB by ID → should get empty (not visible).
     * alertAtoB is created by envA, targeted at envB. envC has no relationship.
     */
    @Test
    void findVisibleAlertById_forEnvC_onAlertTargetedAtEnvB_returnsEmpty() {
        Optional<Alert> result = alertRepository.findVisibleAlertById(alertAtoB.getId(), envC.getId());

        assertThat(result).isEmpty();
    }

    /**
     * envB queries alertAtoB by ID → should find it (targeted at envB).
     */
    @Test
    void findVisibleAlertById_forEnvB_onAlertTargetedAtEnvB_returnsAlert() {
        Optional<Alert> result = alertRepository.findVisibleAlertById(alertAtoB.getId(), envB.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(alertAtoB.getId());
    }

    /**
     * envA queries alertAtoB by ID → should find it (created by envA).
     */
    @Test
    void findVisibleAlertById_forEnvA_onAlertCreatedByEnvA_returnsAlert() {
        Optional<Alert> result = alertRepository.findVisibleAlertById(alertAtoB.getId(), envA.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(alertAtoB.getId());
    }

    /**
     * Backward compatibility: alert with NULL target_environment_id is visible to all.
     * Any caller should see the broadcast alert via findVisibleAlertById.
     */
    @Test
    void findVisibleAlertById_broadcastAlert_visibleToAllCallers() {
        // envA (creator) can see it
        assertThat(alertRepository.findVisibleAlertById(alertBroadcast.getId(), envA.getId()))
            .isPresent();

        // envB (not creator, but broadcast) can see it
        assertThat(alertRepository.findVisibleAlertById(alertBroadcast.getId(), envB.getId()))
            .isPresent();

        // envC (not creator, but broadcast) can see it
        assertThat(alertRepository.findVisibleAlertById(alertBroadcast.getId(), envC.getId()))
            .isPresent();
    }

    /**
     * No duplicates: if caller is both creator and target of same alert,
     * it appears exactly once in results.
     */
    @Test
    void findVisibleAlerts_callerIsBothCreatorAndTarget_noDuplicates() {
        // Create alert where envA is both creator and target
        Alert selfAlert = createAlert(envA, envA.getId(), "Self-directed alert");

        List<Alert> visible = alertRepository.findVisibleAlerts(envA.getId());

        // selfAlert should appear exactly once
        long selfAlertCount = visible.stream()
            .filter(a -> a.getId().equals(selfAlert.getId()))
            .count();
        assertThat(selfAlertCount).isEqualTo(1);
    }

    /**
     * Backward compatibility: pre-existing alerts (NULL target) are treated as broadcast.
     * A random caller with no relationship to creator should still see NULL-target alerts.
     */
    @Test
    void findVisibleAlerts_nullTargetAlert_visibleToUnrelatedCaller() {
        List<Alert> visible = alertRepository.findVisibleAlerts(envC.getId());

        // envC should see alertBroadcast (NULL target) even though envC didn't create it
        assertThat(visible)
            .extracting(Alert::getId)
            .contains(alertBroadcast.getId());
    }

    // --- Helper methods ---

    private Environment createEnvironment(String appName, UUID solutionUuid) {
        Environment env = new Environment();
        env.setApplicationName(appName);
        env.setSessionToken(UUID.randomUUID().toString());
        env.setSharedSecret(UUID.randomUUID().toString());
        env.setType(EnvironmentType.BROKERED);
        env.setSolutionUuid(solutionUuid);
        return environmentRepository.saveAndFlush(env);
    }

    private Alert createAlert(Environment creator, UUID targetEnvironmentId, String description) {
        Alert alert = new Alert();
        alert.setEnvironment(creator);
        alert.setTargetEnvironmentId(targetEnvironmentId);
        alert.setReporter(creator.getApplicationName());
        alert.setExchange(Exchange.REQUEST);
        alert.setLevel(AlertLevel.INFO);
        alert.setDescription(description);
        return alertRepository.saveAndFlush(alert);
    }
}
