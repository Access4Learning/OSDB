// SPDX-License-Identifier: MIT
package org.sifunity.integration.integrity;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.dto.DomainResponse;
import org.sifunity.integration.generator.DomainGenerator;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Integration tests for referential integrity enforcement.
 *
 * <p>Verifies that creating a solution referencing a non-existent domain returns
 * 404 Not Found, and that creating a zone referencing a non-existent solution
 * returns 404 Not Found.</p>
 *
 * <p><strong>Validates: Requirements 13.1, 14.1</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("data-integrity")
@DisplayName("Referential Integrity — Non-Existent Parent Returns 404")
public class ReferentialIntegrityTest extends ServiceEnabledIntegrationTestBase {

    private DomainGenerator domainGenerator;

    private String domainId;

    /**
     * Sets up test infrastructure before each test.
     *
     * <ol>
     *   <li>Initializes DomainGenerator</li>
     * </ol>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();

        log.info("Setting up Referential Integrity test");

        String environmentServiceUrl = getEnvironmentServiceUrl();
        domainGenerator = new DomainGenerator(environmentServiceUrl);
    }

    /**
     * Cleans up test data after each test.
     * Deletes any domain created during the test (cascade cleans children).
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Cleaning up Referential Integrity test");

        if (domainGenerator != null && domainId != null) {
            try {
                domainGenerator.deleteDomain(domainId);
                log.info("Domain deleted during cleanup: {}", domainId);
            } catch (Exception e) {
                log.warn("Failed to delete domain during cleanup (may not have been created): {}", domainId, e);
            }
        }

        super.teardownTest();
    }

    /**
     * Tests that creating a solution referencing a non-existent domain returns 404.
     *
     * <p><strong>Validates: Requirement 13.1</strong></p>
     *
     * <p>Test scenario:
     * <ol>
     *   <li>Attempt to create solution with non-existent domainId → assert 404 Not Found</li>
     * </ol>
     */
    @Test
    @DisplayName("Creating solution with non-existent domainId returns 404")
    public void testCreateSolution_NonExistentDomain_Returns404() {
        log.info("Starting test: Referential Integrity — solution with non-existent domain returns 404");

        String nonExistentDomainId = "non-existent-domain-" + UUID.randomUUID().toString().substring(0, 8);
        String solutionId = "ref-solution-" + UUID.randomUUID().toString().substring(0, 8);

        HttpClientErrorException exception = catchThrowableOfType(
            () -> domainGenerator.createSolution(nonExistentDomainId, solutionId, "Solution under non-existent domain"),
            HttpClientErrorException.class
        );
        assertThat(exception)
            .as("Creating solution under non-existent domain should throw HttpClientErrorException")
            .isNotNull();
        assertThat(exception.getStatusCode().value())
            .as("Solution creation with non-existent domain should return 404 Not Found")
            .isEqualTo(404);
        log.info("Solution creation with non-existent domain correctly returned 404");

        log.info("Test completed: Referential Integrity — solution with non-existent domain returns 404");
    }

    /**
     * Tests that creating a zone referencing a non-existent solution returns 404.
     *
     * <p><strong>Validates: Requirement 14.1</strong></p>
     *
     * <p>Test scenario:
     * <ol>
     *   <li>Create a valid domain</li>
     *   <li>Attempt to create zone with non-existent solutionId under valid domain → assert 404 Not Found</li>
     * </ol>
     */
    @Test
    @DisplayName("Creating zone with non-existent solutionId returns 404")
    public void testCreateZone_NonExistentSolution_Returns404() {
        log.info("Starting test: Referential Integrity — zone with non-existent solution returns 404");

        // Create a valid domain so we can test zone creation with non-existent solution
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        domainId = "ref-domain-" + uniqueSuffix;
        DomainResponse domain = domainGenerator.createDomain(domainId, "Referential Integrity Test Domain");
        assertThat(domain)
            .as("Domain creation should succeed")
            .isNotNull();
        assertThat(domain.domainId())
            .as("Created domain should have the expected domainId")
            .isEqualTo(domainId);
        log.info("Domain created: domainId={}", domain.domainId());

        String nonExistentSolutionId = "non-existent-solution-" + uniqueSuffix;
        String zoneId = "ref-zone-" + uniqueSuffix;

        HttpClientErrorException exception = catchThrowableOfType(
            () -> domainGenerator.createZone(domainId, nonExistentSolutionId, zoneId, "Zone under non-existent solution"),
            HttpClientErrorException.class
        );
        assertThat(exception)
            .as("Creating zone under non-existent solution should throw HttpClientErrorException")
            .isNotNull();
        assertThat(exception.getStatusCode().value())
            .as("Zone creation with non-existent solution should return 404 Not Found")
            .isEqualTo(404);
        log.info("Zone creation with non-existent solution correctly returned 404");

        log.info("Test completed: Referential Integrity — zone with non-existent solution returns 404");
    }
}
