// SPDX-License-Identifier: MIT
package org.sifunity.integration.integrity;

import lombok.extern.slf4j.Slf4j;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.dto.DomainResponse;
import org.sifunity.integration.generator.DomainGenerator;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Property-based test for duplicate domain ID conflict detection.
 *
 * <p><strong>Property 7: Duplicate domain ID conflict</strong></p>
 * <p>For any domain ID string, creating a domain with that ID and then attempting
 * to create a second domain with the same ID SHALL return HTTP 409 Conflict.</p>
 *
 * <p>This is an integration-level property test that runs against the full broker stack.
 * Infrastructure (containers, services) is started once via lazy init. Per iteration,
 * a random domain ID is generated, a domain is created, a duplicate creation is attempted,
 * and the test asserts a 409 response.</p>
 *
 * <p><strong>Validates: Requirements 12.2</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("data-integrity")
@Tag("Feature: cross-service-integration-tests, Property 7: Duplicate domain ID conflict")
public class DuplicateDomainPropertyTest extends ServiceEnabledIntegrationTestBase {

    private DomainGenerator domainGenerator;
    private boolean infrastructureReady = false;

    /**
     * Sets up the full broker infrastructure once before all property iterations.
     * Initializes the DomainGenerator for domain CRUD operations.
     */
    private void ensureInfrastructure() {
        if (infrastructureReady) {
            return;
        }

        log.info("Setting up infrastructure for DuplicateDomainPropertyTest");

        // Start containers and services via base class
        setupInfrastructure();

        // Initialize DomainGenerator
        String environmentServiceUrl = getEnvironmentServiceUrl();
        domainGenerator = new DomainGenerator(environmentServiceUrl);

        infrastructureReady = true;
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

    // ── Property ─────────────────────────────────────────────────────

    /**
     * <strong>Property 7: Duplicate domain ID conflict</strong>
     *
     * <p>For any random domain ID, creating a domain with that ID and then
     * attempting to create a second domain with the same ID SHALL return
     * HTTP 409 Conflict.</p>
     *
     * <p><strong>Validates: Requirements 12.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: cross-service-integration-tests, Property 7: Duplicate domain ID conflict")
    void duplicateDomainIdConflict(
        @ForAll("domainSuffixes") String domainSuffix
    ) {
        // Ensure infrastructure is running (idempotent)
        ensureInfrastructure();

        // Build unique domain ID to avoid collisions across iterations
        String domainId = "pbt-dup-" + domainSuffix;

        log.info("Property iteration: domainId={}", domainId);

        try {
            // Create domain — should succeed
            DomainResponse domain = domainGenerator.createDomain(domainId, "PBT Duplicate Domain Test");
            assertThat(domain)
                .as("First domain creation should succeed")
                .isNotNull();
            assertThat(domain.domainId())
                .as("Created domain should have the expected domainId")
                .isEqualTo(domainId);

            log.info("First domain created successfully: domainId={}", domainId);

            // Attempt duplicate creation — should return 409 Conflict
            HttpClientErrorException conflictException = catchThrowableOfType(
                () -> domainGenerator.createDomain(domainId, "PBT Duplicate Domain Test - Second Attempt"),
                HttpClientErrorException.class
            );

            assertThat(conflictException)
                .as("Creating domain with duplicate domainId should throw HttpClientErrorException")
                .isNotNull();
            assertThat(conflictException.getStatusCode().value())
                .as("Duplicate domain creation should return 409 Conflict")
                .isEqualTo(409);

            log.info("Property iteration passed: domainId={} correctly returned 409 on duplicate", domainId);

        } finally {
            // Clean up domain after each iteration
            cleanupDomain(domainId);
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
