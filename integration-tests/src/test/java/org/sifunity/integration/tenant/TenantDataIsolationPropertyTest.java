// SPDX-License-Identifier: MIT
package org.sifunity.integration.tenant;

import lombok.extern.slf4j.Slf4j;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.dto.SolutionResponse;
import org.sifunity.integration.generator.DomainGenerator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for tenant data isolation between domains.
 *
 * <p><strong>Property 6: Tenant data isolation</strong></p>
 * <p>For any two distinct domains, solutions created under one domain SHALL NOT
 * be visible when querying solutions under the other domain.</p>
 *
 * <p>This is an integration-level property test that runs against the full broker stack.
 * Infrastructure (containers, services) is started once via lazy init. Per iteration,
 * random domain/solution ID pairs are generated, a solution is created under Domain A,
 * and Domain B's solutions are queried to assert the solution is not visible.</p>
 *
 * <p><strong>Validates: Requirements 9.2</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("multi-tenant")
@Tag("Feature: cross-service-integration-tests, Property 6: Tenant data isolation")
public class TenantDataIsolationPropertyTest extends ServiceEnabledIntegrationTestBase {

    private DomainGenerator domainGenerator;
    private boolean infrastructureReady = false;

    /**
     * Sets up the full broker infrastructure once before all property iterations.
     * Initializes the DomainGenerator for domain/solution CRUD operations.
     */
    private void ensureInfrastructure() {
        if (infrastructureReady) {
            return;
        }

        log.info("Setting up infrastructure for TenantDataIsolationPropertyTest");

        // Start containers and services via base class
        setupInfrastructure();

        // Initialize DomainGenerator
        String environmentServiceUrl = getEnvironmentServiceUrl();
        domainGenerator = new DomainGenerator(environmentServiceUrl);

        infrastructureReady = true;
    }

    /**
     * Tears down infrastructure. Called after all property iterations complete.
     */
    private void cleanupInfrastructure() {
        log.info("Cleaning up TenantDataIsolationPropertyTest infrastructure");

        try {
            teardownInfrastructure();
        } catch (Exception e) {
            log.error("Error during infrastructure teardown", e);
        }
    }

    // ── Arbitraries ──────────────────────────────────────────────────

    /**
     * Generates random domain ID suffixes (8-char lowercase alphanumeric).
     */
    @Provide
    Arbitrary<String> domainSuffixes() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofLength(8);
    }

    /**
     * Generates random solution ID suffixes (8-char lowercase alphanumeric).
     */
    @Provide
    Arbitrary<String> solutionSuffixes() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofLength(8);
    }

    // ── Property ─────────────────────────────────────────────────────

    /**
     * <strong>Property 6: Tenant data isolation</strong>
     *
     * <p>For any two distinct domains with random IDs, creating a solution under
     * Domain A and then querying solutions under Domain B SHALL return a list
     * that does NOT contain Domain A's solution.</p>
     *
     * <p><strong>Validates: Requirements 9.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: cross-service-integration-tests, Property 6: Tenant data isolation")
    void tenantDataIsolation(
        @ForAll("domainSuffixes") String domainASuffix,
        @ForAll("domainSuffixes") String domainBSuffix,
        @ForAll("solutionSuffixes") String solutionSuffix
    ) {
        // Ensure infrastructure is running (idempotent)
        ensureInfrastructure();

        // Build unique IDs to avoid collisions across iterations
        String domainAId = "pbt-iso-a-" + domainASuffix;
        String domainBId = "pbt-iso-b-" + domainBSuffix;
        String solutionAId = "pbt-sol-" + solutionSuffix;

        log.info("Property iteration: domainA={}, domainB={}, solutionA={}",
            domainAId, domainBId, solutionAId);

        try {
            // Create Domain A with a solution
            domainGenerator.createDomain(domainAId, "PBT Tenant Isolation - Domain A");
            domainGenerator.createSolution(domainAId, solutionAId, "PBT Solution under Domain A");

            // Create Domain B (no solutions)
            domainGenerator.createDomain(domainBId, "PBT Tenant Isolation - Domain B");

            // Query solutions under Domain B
            List<SolutionResponse> domainBSolutions = domainGenerator.listSolutions(domainBId);

            // Assert Domain A's solution is NOT visible under Domain B
            assertThat(domainBSolutions)
                .as("Domain B should NOT contain Domain A's solution '%s'", solutionAId)
                .extracting(SolutionResponse::solutionId)
                .doesNotContain(solutionAId);

            log.info("Property iteration passed: domainA={}, domainB={}, solutionA={}, domainBSolutions={}",
                domainAId, domainBId, solutionAId,
                domainBSolutions.stream().map(SolutionResponse::solutionId).toList());

        } finally {
            // Clean up both domains (cascade deletes children)
            cleanupDomain(domainBId);
            cleanupDomain(domainAId);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Safely deletes a domain, logging but not throwing on failure.
     *
     * @param domainId the domain ID to delete
     */
    private void cleanupDomain(String domainId) {
        try {
            domainGenerator.deleteDomain(domainId);
            log.debug("Cleaned up domain: {}", domainId);
        } catch (Exception e) {
            log.warn("Failed to clean up domain '{}': {}", domainId, e.getMessage());
        }
    }
}
