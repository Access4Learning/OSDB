// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.domain.TagDefinition;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.DuplicateEntityTagException;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EntityTagService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for duplicate entity tag rejection using JUnit 5 RepeatedTest.
 *
 * <p>This test validates Property 2 from the design document:
 * "For any valid tag, creating it once should succeed, and creating a second tag
 * with the same (solutionId, entityType, entityId, tagKey) should fail with a
 * DuplicateEntityTagException (HTTP 409). The original tag should remain unchanged."</p>
 *
 * <p><strong>Validates: Requirements 3.3, 10.1, 12.2</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DuplicateEntityTagRejectionPropertyTest {

    @Autowired
    private EntityTagService entityTagService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private TagDefinitionRepository tagDefinitionRepository;

    private final Random random = new Random();

    /**
     * Property 2: Duplicate Tag Rejection
     *
     * <p><strong>Validates: Requirements 3.3, 10.1, 12.2</strong></p>
     *
     * <p>Creates a tag with random valid data, attempts to create the same tag again
     * (same solutionId, entityType, entityId, tagKey), and verifies that a
     * DuplicateEntityTagException is thrown. Also verifies the original tag remains intact.</p>
     */
    @RepeatedTest(10)
    @Tag("entity-tags-property-2")
    void duplicateTagRejection() {
        // Set up parent domain and solution
        String domainId = generateValidDomainId();
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain for duplicate tag rejection"));

        String solutionId = generateValidSolutionId();
        solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Test solution for duplicate tag rejection"));

        // Generate random valid tag data
        EntityType entityType = generateRandomEntityType();
        UUID entityId = UUID.randomUUID();
        String tagKey = generateValidTagKey();
        String tagValue = random.nextBoolean() ? generateValidTagValue() : null;

        // Create a matching TagDefinition so validation passes
        ensureTagDefinitionExists(tagKey);

        // Create the tag successfully (first time) — Requirement 3.1
        EntityTagCreateRequest createRequest = new EntityTagCreateRequest(
                entityType, entityId, tagKey, tagValue);
        EntityTagDTO originalTag = entityTagService.createTag(domainId, solutionId, createRequest);

        // Verify the first creation succeeded
        assertThat(originalTag.id()).isNotNull();
        assertThat(originalTag.tagKey()).isEqualTo(tagKey);

        // Attempt to create the SAME tag again — Requirement 3.3, 10.1
        assertThatThrownBy(() -> entityTagService.createTag(domainId, solutionId, createRequest))
                .as("Creating a duplicate tag (same solutionId, entityType, entityId, tagKey) should throw DuplicateEntityTagException")
                .isInstanceOf(DuplicateEntityTagException.class);

        // Verify the original tag is still intact by retrieving it
        EntityTagDTO retrievedTag = entityTagService.getTag(domainId, solutionId, entityType, entityId, tagKey);

        assertThat(retrievedTag.id())
                .as("Original tag UUID should be unchanged after duplicate rejection")
                .isEqualTo(originalTag.id());

        assertThat(retrievedTag.entityType())
                .as("Original tag entityType should be unchanged")
                .isEqualTo(originalTag.entityType());

        assertThat(retrievedTag.entityId())
                .as("Original tag entityId should be unchanged")
                .isEqualTo(originalTag.entityId());

        assertThat(retrievedTag.tagKey())
                .as("Original tag tagKey should be unchanged")
                .isEqualTo(originalTag.tagKey());

        assertThat(retrievedTag.tagValue())
                .as("Original tag tagValue should be unchanged")
                .isEqualTo(originalTag.tagValue());

        assertThat(retrievedTag.createdAt())
                .as("Original tag createdAt should be unchanged")
                .isEqualTo(originalTag.createdAt());
    }

    // ========== Tag Definition Setup ==========

    private void ensureTagDefinitionExists(String tagKey) {
        if (!tagDefinitionRepository.existsByTagNameIgnoreCase(tagKey)) {
            TagDefinition def = new TagDefinition();
            def.setTagName(tagKey);
            def.setTagDescription("Auto-created for property test");
            def.setApplicableEntityTypes(
                    Arrays.stream(EntityType.values()).map(Enum::name).toList());
            def.setAllowedValues(null); // accept any value
            def.setCreatedBy("property-test");
            tagDefinitionRepository.save(def);
        }
    }

    // ========== Test Data Generators ==========

    private String generateValidDomainId() {
        String[] prefixes = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        return prefix + "-" + number + "-" + timestamp;
    }

    private String generateValidSolutionId() {
        String[] environments = {"production", "staging", "testing", "development", "qa", "uat", "demo", "sandbox"};
        String environment = environments[random.nextInt(environments.length)];
        int number = random.nextInt(100);
        long timestamp = System.nanoTime() % 10000;
        return environment + "-" + number + "-" + timestamp;
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
