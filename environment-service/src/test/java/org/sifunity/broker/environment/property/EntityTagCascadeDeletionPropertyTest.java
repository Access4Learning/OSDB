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
import org.sifunity.broker.environment.repository.EntityTagRepository;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EntityTagService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for entity tag cascade deletion using JUnit 5 RepeatedTest.
 *
 * <p>This test validates Property 5 from the design document:
 * "For any solution with one or more tags, deleting the solution should result in
 * all tags scoped to that solution being removed. Listing tags for that solution
 * (via direct DB query) should return an empty list."</p>
 *
 * <p><strong>Validates: Requirements 5.3, 12.4</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityTagCascadeDeletionPropertyTest {

    @Autowired
    private EntityTagService entityTagService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private EntityTagRepository entityTagRepository;

    @Autowired
    private TagDefinitionRepository tagDefinitionRepository;

    private final Random random = new Random();

    /**
     * Property 5: Cascade Deletion
     *
     * <p><strong>Validates: Requirements 5.3, 12.4</strong></p>
     *
     * <p>Creates 1-5 tags with distinct keys for a solution, verifies they exist,
     * deletes the solution, and verifies all tags are cascade-deleted via direct
     * repository query.</p>
     */
    @RepeatedTest(10)
    @Tag("entity-tags-property-5")
    void cascadeDeletion() {
        // Set up parent domain and solution
        String domainId = generateValidDomainId();
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain for cascade deletion"));

        String solutionId = generateValidSolutionId();
        SolutionDTO solution = solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Test solution for cascade deletion"));

        // Store the solution's internal UUID for direct repository query after deletion
        UUID solutionUuid = solution.id();

        // Generate random entity reference for the tags
        EntityType entityType = generateRandomEntityType();
        UUID entityId = UUID.randomUUID();

        // Create 1-5 tags with distinct keys (Requirement 5.3: tags scoped to solution)
        int tagCount = random.nextInt(5) + 1;
        Set<String> tagKeys = new HashSet<>();
        while (tagKeys.size() < tagCount) {
            tagKeys.add(generateValidTagKey());
        }

        for (String tagKey : tagKeys) {
            ensureTagDefinitionExists(tagKey);
            String tagValue = random.nextBoolean() ? generateValidTagValue() : null;
            EntityTagCreateRequest createRequest = new EntityTagCreateRequest(
                    entityType, entityId, tagKey, tagValue);
            entityTagService.createTag(domainId, solutionId, createRequest);
        }

        // Verify tags exist before deletion via service list
        List<EntityTagDTO> tagsBeforeDeletion = entityTagService.listTags(
                domainId, solutionId, entityType, entityId, null);
        assertThat(tagsBeforeDeletion)
                .as("Tags should exist before solution deletion")
                .hasSize(tagCount);

        // Delete the solution (Requirement 5.3: ON DELETE CASCADE removes all tags)
        solutionService.deleteSolution(domainId, solutionId);

        // Verify all tags are gone via direct repository query
        // (cannot use entityTagService.listTags because the solution no longer exists)
        List<?> tagsAfterDeletion = entityTagRepository.findBySolutionId(solutionUuid);
        assertThat(tagsAfterDeletion)
                .as("All tags scoped to the deleted solution should be cascade-deleted (Requirement 12.4)")
                .isEmpty();

        // Verify the solution itself no longer exists
        assertThat(solutionService.solutionExists(domainId, solutionId))
                .as("Solution should not exist after deletion")
                .isFalse();

        // Verify the parent domain still exists (should not be affected)
        DomainDTO retrievedDomain = domainService.getDomain(domainId);
        assertThat(retrievedDomain)
                .as("Parent domain should still exist after solution deletion")
                .isNotNull();
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
