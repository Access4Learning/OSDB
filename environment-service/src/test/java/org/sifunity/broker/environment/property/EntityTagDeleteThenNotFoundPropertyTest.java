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
import org.sifunity.broker.environment.exception.EntityTagNotFoundException;
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
 * Property-based test for entity tag delete-then-not-found using JUnit 5 RepeatedTest.
 *
 * <p>This test validates Property 6 from the design document:
 * "For any tag that has been created and then deleted, attempting to delete or retrieve
 * the same tag again should fail with an EntityTagNotFoundException (HTTP 404)."</p>
 *
 * <p><strong>Validates: Requirements 5.1, 5.2, 12.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityTagDeleteThenNotFoundPropertyTest {

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
     * Property 6: Delete Then Not-Found
     *
     * <p><strong>Validates: Requirements 5.1, 5.2, 12.5</strong></p>
     *
     * <p>Creates a tag with random valid data, verifies it exists, deletes it,
     * then asserts that both retrieving and deleting the same tag again
     * throw EntityTagNotFoundException.</p>
     */
    @RepeatedTest(10)
    @Tag("entity-tags-property-6")
    void deleteThenNotFound() {
        // Set up parent domain and solution
        String domainId = generateValidDomainId();
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain for delete-then-not-found"));

        String solutionId = generateValidSolutionId();
        solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Test solution for delete-then-not-found"));

        // Generate random valid tag data
        EntityType entityType = generateRandomEntityType();
        UUID entityId = UUID.randomUUID();
        String tagKey = generateValidTagKey();
        String tagValue = random.nextBoolean() ? generateValidTagValue() : null;

        // Create a matching TagDefinition so validation passes
        ensureTagDefinitionExists(tagKey);

        // Create the tag (Requirement 5.1 precondition: tag must exist before deletion)
        EntityTagCreateRequest createRequest = new EntityTagCreateRequest(
                entityType, entityId, tagKey, tagValue);
        EntityTagDTO createdTag = entityTagService.createTag(domainId, solutionId, createRequest);

        // Verify the tag exists by retrieving it
        EntityTagDTO retrievedTag = entityTagService.getTag(domainId, solutionId, entityType, entityId, tagKey);
        assertThat(retrievedTag.id())
                .as("Tag should be retrievable after creation")
                .isEqualTo(createdTag.id());

        // Delete the tag (Requirement 5.1: remove matching tag and return HTTP 204)
        entityTagService.deleteTag(domainId, solutionId, entityType, entityId, tagKey);

        // Attempt to retrieve the deleted tag — should throw EntityTagNotFoundException (Requirement 5.2)
        assertThatThrownBy(() -> entityTagService.getTag(domainId, solutionId, entityType, entityId, tagKey))
                .as("Retrieving a deleted tag should throw EntityTagNotFoundException")
                .isInstanceOf(EntityTagNotFoundException.class);

        // Attempt to delete the same tag again — should throw EntityTagNotFoundException (Requirement 12.5)
        assertThatThrownBy(() -> entityTagService.deleteTag(domainId, solutionId, entityType, entityId, tagKey))
                .as("Deleting an already-deleted tag should throw EntityTagNotFoundException")
                .isInstanceOf(EntityTagNotFoundException.class);
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
