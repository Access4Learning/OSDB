// SPDX-License-Identifier: MIT
package org.sifunity.integration.tenant;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.dto.DomainResponse;
import org.sifunity.integration.dto.SolutionResponse;
import org.sifunity.integration.dto.ZoneResponse;
import org.sifunity.integration.generator.DomainGenerator;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Integration tests for cascade deletion of domain hierarchies.
 *
 * <p>Verifies that deleting a domain removes all child entities (solutions, zones)
 * and that subsequent queries for deleted entities return 404 Not Found.</p>
 *
 * <p>No {@code @AfterEach} cleanup is needed because domain deletion cascades everything.</p>
 *
 * <p><strong>Validates: Requirements 10.1, 10.2, 10.3, 10.4</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("multi-tenant")
@Tag("cascade")
@DisplayName("Cascade Deletion — Domain Deletion Removes All Child Entities")
public class CascadeDeletionTest extends ServiceEnabledIntegrationTestBase {

    private DomainGenerator domainGenerator;

    private String domainId;
    private String solutionId;
    private String zoneId;
    private DomainResponse domain;
    private SolutionResponse solution;
    private ZoneResponse zone;

    /**
     * Sets up test data before each test.
     *
     * <ol>
     *   <li>Initializes DomainGenerator</li>
     *   <li>Creates a domain with a solution and a zone</li>
     * </ol>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();

        log.info("Setting up Cascade Deletion test");

        String environmentServiceUrl = getEnvironmentServiceUrl();
        domainGenerator = new DomainGenerator(environmentServiceUrl);

        // Generate unique IDs to avoid collisions
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        domainId = "cascade-domain-" + uniqueSuffix;
        solutionId = "cascade-solution-" + uniqueSuffix;
        zoneId = "cascade-zone-" + uniqueSuffix;

        // Create domain → solution → zone hierarchy
        domain = domainGenerator.createDomain(domainId, "Cascade Deletion Test Domain");
        log.info("Domain created: domainId={}", domain.domainId());

        solution = domainGenerator.createSolution(domainId, solutionId, "Cascade Deletion Test Solution");
        log.info("Solution created: solutionId={} in domain={}", solution.solutionId(), domainId);

        zone = domainGenerator.createZone(domainId, solutionId, zoneId, "Cascade Deletion Test Zone");
        log.info("Zone created: zoneId={} in solution={}/{}", zone.zoneId(), domainId, solutionId);
    }

    // No @AfterEach needed — domain deletion cascades everything

    /**
     * Tests that deleting a domain cascades to all child entities.
     *
     * <p><strong>Validates: Requirements 10.1, 10.2, 10.3, 10.4</strong></p>
     *
     * <p>Test scenario:
     * <ol>
     *   <li>Delete domain → assert no exception (204 No Content)</li>
     *   <li>Query deleted domain → assert 404 Not Found</li>
     *   <li>Query solutions under deleted domain → assert 404 Not Found</li>
     *   <li>Query zones under deleted domain → assert 404 Not Found</li>
     * </ol>
     */
    @Test
    @DisplayName("Deleting domain cascades to solutions and zones — all return 404")
    public void testCascadeDeletion_DomainDeleteRemovesAllChildren() {
        log.info("Starting test: Cascade Deletion — domain delete removes all children");

        // Verify entities exist before deletion
        DomainResponse existingDomain = domainGenerator.getDomain(domainId);
        assertThat(existingDomain).as("Domain should exist before deletion").isNotNull();
        assertThat(existingDomain.domainId()).isEqualTo(domainId);

        SolutionResponse existingSolution = domainGenerator.getSolution(domainId, solutionId);
        assertThat(existingSolution).as("Solution should exist before deletion").isNotNull();
        assertThat(existingSolution.solutionId()).isEqualTo(solutionId);

        // Step 1: Delete domain → should succeed with no exception (Requirement 10.1)
        domainGenerator.deleteDomain(domainId);
        log.info("Domain deleted successfully: {}", domainId);

        // Step 2: Query deleted domain → expect 404 (Requirement 10.2)
        HttpClientErrorException domainException = catchThrowableOfType(
            () -> domainGenerator.getDomain(domainId),
            HttpClientErrorException.class
        );
        assertThat(domainException)
            .as("Querying deleted domain should throw HttpClientErrorException")
            .isNotNull();
        assertThat(domainException.getStatusCode().value())
            .as("Deleted domain should return 404 Not Found")
            .isEqualTo(404);
        log.info("Deleted domain returns 404 as expected");

        // Step 3: Query solutions under deleted domain → expect 404 (Requirement 10.3)
        HttpClientErrorException solutionException = catchThrowableOfType(
            () -> domainGenerator.getSolution(domainId, solutionId),
            HttpClientErrorException.class
        );
        assertThat(solutionException)
            .as("Querying solution under deleted domain should throw HttpClientErrorException")
            .isNotNull();
        assertThat(solutionException.getStatusCode().value())
            .as("Solution under deleted domain should return 404 Not Found")
            .isEqualTo(404);
        log.info("Solution under deleted domain returns 404 as expected");

        // Step 4: Query zones under deleted domain → expect 404 (Requirement 10.4)
        HttpClientErrorException zoneException = catchThrowableOfType(
            () -> domainGenerator.listZones(domainId, solutionId),
            HttpClientErrorException.class
        );
        assertThat(zoneException)
            .as("Querying zones under deleted domain should throw HttpClientErrorException")
            .isNotNull();
        assertThat(zoneException.getStatusCode().value())
            .as("Zones under deleted domain should return 404 Not Found")
            .isEqualTo(404);
        log.info("Zones under deleted domain return 404 as expected");

        log.info("Test completed: Cascade Deletion — domain delete removes all children");
    }
}
