// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EntityTagCreateRequest;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EntityTagService;

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for non-existent solution rejection using JUnit 5 RepeatedTest.
 *
 * <p>This test validates Property 3 from the design document:
 * "For any solution ID that does not exist in the database, attempting to create
 * a tag scoped to that solution should fail with a SolutionNotFoundException (HTTP 404)."</p>
 *
 * <p><strong>Validates: Requirements 3.2, 6.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityTagNonExistentSolutionPropertyTest {

    @Autowired
    private EntityTagService entityTagService;

    @Autowired
    private DomainService domainService;

    private final Random random = new Random();

    /**
     * Property 3: Non-Existent Solution Rejection
     *
     * <p><strong>Validates: Requirements 3.2, 6.5</strong></p>
     *
     * <p>Generates a random solutionId that does not exist in the database,
     * attempts to create a tag scoped to that solution, and verifies that
     * a SolutionNotFoundException is thrown.</p>
     */
    @RepeatedTest(10)
    @Tag("entity-tags-property-3")
    void nonExistentSolutionRejection() {
        // Set up a real parent domain so the failure is specifically solution-not-found
        // (production resolveSolution throws SolutionNotFoundException for a missing solution
        // under an existing domain)
        String domainId = generateValidDomainId();
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain for non-existent solution"));

        // Generate a random solutionId that does NOT exist in the database
        String nonExistentSolutionId = generateNonExistentSolutionId();

        // Generate random valid tag data
        EntityType entityType = generateRandomEntityType();
        UUID entityId = UUID.randomUUID();
        String tagKey = generateValidTagKey();
        String tagValue = random.nextBoolean() ? generateValidTagValue() : null;

        // Attempt to create a tag with the non-existent solutionId — Requirement 3.2, 6.5
        EntityTagCreateRequest createRequest = new EntityTagCreateRequest(
                entityType, entityId, tagKey, tagValue);

        assertThatThrownBy(() -> entityTagService.createTag(domainId, nonExistentSolutionId, createRequest))
                .as("Creating a tag with a non-existent solutionId should throw SolutionNotFoundException")
                .isInstanceOf(SolutionNotFoundException.class);
    }

    // ========== Test Data Generators ==========

    private String generateValidDomainId() {
        String[] prefixes = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        return prefix + "-" + number + "-" + timestamp;
    }

    private String generateNonExistentSolutionId() {
        String[] prefixes = {"nonexistent", "missing", "unknown", "phantom", "ghost", "void", "absent", "invalid"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        return prefix + "-" + number + "-" + timestamp;
    }

    private EntityType generateRandomEntityType() {
        EntityType[] values = EntityType.values();
        return values[random.nextInt(values.length)];
    }

    private String generateValidTagKey() {
        String[] prefixes = {"SDPC_ENABLED", "RATE_LIMIT_TIER", "FEATURE_FLAG", "ENV_MODE",
                "ROUTING_POLICY", "AUDIT_LEVEL", "CACHE_TTL", "MAX_CONNECTIONS"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int suffix = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        return prefix + "_" + suffix + "_" + timestamp;
    }

    private String generateValidTagValue() {
        String[] values = {"true", "false", "gold", "silver", "bronze", "enabled", "disabled",
                "high", "medium", "low", "300", "600", "1200", "custom-value-" + random.nextInt(100)};
        return values[random.nextInt(values.length)];
    }
}
