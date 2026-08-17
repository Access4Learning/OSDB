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
 * Property-based test for entity tag count metamorphic property using JUnit 5 RepeatedTest.
 *
 * <p>This test validates Property 4 from the design document:
 * "For any solution and entity, if N tags with distinct keys are created for that entity,
 * then listing tags by (solutionId, entityType, entityId) should return exactly N tags."</p>
 *
 * <p><strong>Validates: Requirements 4.1, 12.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityTagCountMetamorphicPropertyTest {

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
     * Property 4: Tag Count Metamorphic
     *
     * <p><strong>Validates: Requirements 4.1, 12.3</strong></p>
     *
     * <p>Creates N tags with distinct keys for a single entity, then lists all tags
     * for that entity and verifies the returned count equals exactly N.</p>
     */
    @RepeatedTest(10)
    @Tag("entity-tags-property-4")
    void tagCountMetamorphic() {
        // Set up parent domain and solution
        String domainId = generateValidDomainId();
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain for entity tags"));

        String solutionId = generateValidSolutionId();
        solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Test solution for entity tags"));

        // Generate a fixed entity type and entity ID for this iteration
        EntityType entityType = generateRandomEntityType();
        UUID entityId = UUID.randomUUID();

        // Generate a random N between 1 and 10
        int n = random.nextInt(10) + 1;

        // Generate N distinct tag keys
        Set<String> tagKeys = new HashSet<>();
        while (tagKeys.size() < n) {
            tagKeys.add(generateValidTagKey());
        }

        // Create N tags with distinct keys for the same entity (Requirement 4.1)
        for (String tagKey : tagKeys) {
            ensureTagDefinitionExists(tagKey);
            String tagValue = random.nextBoolean() ? generateValidTagValue() : null;
            EntityTagCreateRequest createRequest = new EntityTagCreateRequest(
                    entityType, entityId, tagKey, tagValue);
            entityTagService.createTag(domainId, solutionId, createRequest);
        }

        // List all tags for the entity (Requirement 4.1: list tags by entity)
        List<EntityTagDTO> listedTags = entityTagService.listTags(
                domainId, solutionId, entityType, entityId, null);

        // Verify the count equals exactly N (Requirement 12.3: metamorphic property)
        assertThat(listedTags)
                .as("Listing tags for entity should return exactly %d tags after creating %d distinct tags", n, n)
                .hasSize(n);
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
