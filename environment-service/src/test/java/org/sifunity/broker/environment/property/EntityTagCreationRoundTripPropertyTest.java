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

import java.time.Instant;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for entity tag creation round-trip using JUnit 5 RepeatedTest.
 *
 * <p>This test validates Property 1 from the design document:
 * "For any valid solution, entity type, entity ID, tag key, and optional tag value,
 * creating a tag and then retrieving it by (solutionId, entityType, entityId, tagKey)
 * should return an EntityTagDTO with equivalent entityType, entityId, tagKey, tagValue,
 * a non-null id, the correct solutionId, and a createdAt timestamp that is approximately
 * the current time."</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 3.1, 3.4, 4.3, 12.1</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityTagCreationRoundTripPropertyTest {

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
     * Property 1: Tag Creation Round-Trip
     *
     * <p><strong>Validates: Requirements 2.1, 2.2, 3.1, 3.4, 4.3, 12.1</strong></p>
     *
     * <p>Creates a tag with random valid data, retrieves it by key, and verifies
     * all fields match: entityType, entityId, tagKey, tagValue, solutionId,
     * non-null id, and createdAt approximately now.</p>
     */
    @RepeatedTest(10)
    @Tag("entity-tags-property-1")
    void tagCreationRoundTrip() {
        // Set up parent domain and solution
        String domainId = generateValidDomainId();
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain for entity tags"));

        String solutionId = generateValidSolutionId();
        SolutionDTO solution = solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Test solution for entity tags"));

        // Generate random valid tag data
        EntityType entityType = generateRandomEntityType();
        UUID entityId = UUID.randomUUID();
        String tagKey = generateValidTagKey();
        String tagValue = random.nextBoolean() ? generateValidTagValue() : null;

        // Create a matching TagDefinition so validation passes
        ensureTagDefinitionExists(tagKey);

        // Record time before creation for timestamp validation
        Instant beforeCreation = Instant.now();

        // Create the tag (Requirement 3.1: persist and return with HTTP 201)
        EntityTagCreateRequest createRequest = new EntityTagCreateRequest(
                entityType, entityId, tagKey, tagValue);
        EntityTagDTO createdTag = entityTagService.createTag(domainId, solutionId, createRequest);

        // Record time after creation
        Instant afterCreation = Instant.now();

        // Verify created tag response
        assertThat(createdTag.id())
                .as("Created tag should have a non-null UUID")
                .isNotNull();

        assertThat(createdTag.solutionId())
                .as("Created tag solutionId should match the solution's internal UUID")
                .isEqualTo(solution.id());

        assertThat(createdTag.entityType())
                .as("Created tag entityType should match the request")
                .isEqualTo(entityType);

        assertThat(createdTag.entityId())
                .as("Created tag entityId should match the request")
                .isEqualTo(entityId);

        assertThat(createdTag.tagKey())
                .as("Created tag tagKey should match the request")
                .isEqualTo(tagKey);

        assertThat(createdTag.tagValue())
                .as("Created tag tagValue should match the request (including null for boolean flags)")
                .isEqualTo(tagValue);

        // Requirement 2.2: createdAt set via @PrePersist
        assertThat(createdTag.createdAt())
                .as("Created tag should have a creation timestamp")
                .isNotNull()
                .as("Creation timestamp should be approximately now")
                .isBetween(beforeCreation.minusSeconds(1), afterCreation.plusSeconds(1));

        // Retrieve the tag by key (Requirement 4.3: get specific tag)
        EntityTagDTO retrievedTag = entityTagService.getTag(
                domainId, solutionId, entityType, entityId, tagKey);

        // Verify round-trip: all fields should match
        assertThat(retrievedTag.id())
                .as("Retrieved tag UUID should match created tag UUID")
                .isEqualTo(createdTag.id());

        assertThat(retrievedTag.solutionId())
                .as("Retrieved tag solutionId should match created tag solutionId")
                .isEqualTo(createdTag.solutionId());

        assertThat(retrievedTag.entityType())
                .as("Retrieved tag entityType should match created tag entityType")
                .isEqualTo(createdTag.entityType());

        assertThat(retrievedTag.entityId())
                .as("Retrieved tag entityId should match created tag entityId")
                .isEqualTo(createdTag.entityId());

        assertThat(retrievedTag.tagKey())
                .as("Retrieved tag tagKey should match created tag tagKey")
                .isEqualTo(createdTag.tagKey());

        // Requirement 3.4: null tagValue represents a boolean flag
        assertThat(retrievedTag.tagValue())
                .as("Retrieved tag tagValue should match created tag tagValue")
                .isEqualTo(createdTag.tagValue());

        assertThat(retrievedTag.createdAt())
                .as("Retrieved tag createdAt should match created tag createdAt")
                .isEqualTo(createdTag.createdAt());
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
