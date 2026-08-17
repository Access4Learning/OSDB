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
 * Integration tests for duplicate domain ID conflict detection.
 *
 * <p>Verifies that creating a domain with a unique domain_id succeeds with 201,
 * and that attempting to create a second domain with the same domain_id returns
 * 409 Conflict.</p>
 *
 * <p><strong>Validates: Requirements 12.1, 12.2</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("data-integrity")
@DisplayName("Duplicate Domain — Same Domain ID Returns 409 Conflict")
public class DuplicateDomainTest extends ServiceEnabledIntegrationTestBase {

    private DomainGenerator domainGenerator;

    private String domainId;

    /**
     * Sets up test data before each test.
     *
     * <ol>
     *   <li>Initializes DomainGenerator</li>
     *   <li>Generates a unique domain ID for the test</li>
     * </ol>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();

        log.info("Setting up Duplicate Domain test");

        String environmentServiceUrl = getEnvironmentServiceUrl();
        domainGenerator = new DomainGenerator(environmentServiceUrl);

        // Generate unique ID to avoid collisions with other tests
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        domainId = "dup-domain-" + uniqueSuffix;
    }

    /**
     * Cleans up test data after each test.
     * Deletes the domain created during the test (cascade cleans children).
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Cleaning up Duplicate Domain test");

        if (domainGenerator != null && domainId != null) {
            try {
                domainGenerator.deleteDomain(domainId);
                log.info("Domain deleted during cleanup: {}", domainId);
            } catch (Exception e) {
                log.warn("Failed to delete domain during cleanup (may already be deleted): {}", domainId, e);
            }
        }

        super.teardownTest();
    }

    /**
     * Tests that creating a domain with a unique ID succeeds, and creating
     * a second domain with the same ID returns 409 Conflict.
     *
     * <p><strong>Validates: Requirements 12.1, 12.2</strong></p>
     *
     * <p>Test scenario:
     * <ol>
     *   <li>Create domain with unique domainId → assert success (Requirement 12.1)</li>
     *   <li>Attempt to create second domain with same domainId → assert 409 Conflict (Requirement 12.2)</li>
     * </ol>
     */
    @Test
    @DisplayName("Creating domain with duplicate domainId returns 409 Conflict")
    public void testDuplicateDomainId_Returns409Conflict() {
        log.info("Starting test: Duplicate Domain — same domainId returns 409 Conflict");

        // Step 1: Create domain with unique domainId → should succeed (Requirement 12.1)
        DomainResponse domain = domainGenerator.createDomain(domainId, "Duplicate Domain Test");
        assertThat(domain)
            .as("First domain creation should succeed")
            .isNotNull();
        assertThat(domain.domainId())
            .as("Created domain should have the expected domainId")
            .isEqualTo(domainId);
        log.info("First domain created successfully: domainId={}", domain.domainId());

        // Step 2: Attempt to create second domain with same domainId → expect 409 (Requirement 12.2)
        HttpClientErrorException conflictException = catchThrowableOfType(
            () -> domainGenerator.createDomain(domainId, "Duplicate Domain Test - Second Attempt"),
            HttpClientErrorException.class
        );
        assertThat(conflictException)
            .as("Creating domain with duplicate domainId should throw HttpClientErrorException")
            .isNotNull();
        assertThat(conflictException.getStatusCode().value())
            .as("Duplicate domain creation should return 409 Conflict")
            .isEqualTo(409);
        log.info("Duplicate domain creation correctly returned 409 Conflict");

        log.info("Test completed: Duplicate Domain — same domainId returns 409 Conflict");
    }
}
