// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EntityTagCreateRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for entity tag creation validation against tag definitions.
 *
 * <p>Validates that entity tag creation enforces tag definition governance:
 * tag key must match an existing definition, tag value must be in allowed_values
 * (when constrained), and entity type must be in applicableEntityTypes.</p>
 *
 * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfiguration.class)
@ActiveProfiles({"test", "disable-security"})
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
@Transactional
class EntityTagValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String testDomainId;
    private String testSolutionId;
    private String tagsBasePath;

    @BeforeEach
    void setUp() throws Exception {
        testDomainId = "tagval-domain-" + System.currentTimeMillis();
        testSolutionId = "tagval-solution-" + System.currentTimeMillis();
        tagsBasePath = "/" + testDomainId + "/" + testSolutionId + "/tags";

        // Create domain
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new DomainCreateRequest(testDomainId, "Tag validation test domain"))))
            .andExpect(status().isCreated());

        // Create solution
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SolutionCreateRequest(testDomainId, testSolutionId, "Tag validation test solution"))))
            .andExpect(status().isCreated());
    }

    // ========================================================================
    // Requirement 7.1, 7.2: Tag key must match existing tag definition
    // ========================================================================

    @Test
    void createEntityTag_withExistingDefinition_succeeds() throws Exception {
        // SEQUENCING_ENABLED is seeded by V40 migration, applicable to SOLUTION
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.SOLUTION, entityId, "SEQUENCING_ENABLED", "true");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tagKey").value("SEQUENCING_ENABLED"))
            .andExpect(jsonPath("$.tagValue").value("true"))
            .andExpect(jsonPath("$.entityType").value("SOLUTION"));
    }

    @Test
    void createEntityTag_withCaseInsensitiveKey_succeeds() throws Exception {
        // Tag key lookup is case-insensitive: "sequencing_enabled" matches "SEQUENCING_ENABLED"
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.SOLUTION, entityId, "sequencing_enabled", "false");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tagKey").value("sequencing_enabled"))
            .andExpect(jsonPath("$.tagValue").value("false"));
    }

    @Test
    void createEntityTag_withNonExistentDefinition_returns400() throws Exception {
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.ZONE, entityId, "NONEXISTENT_TAG_KEY", "somevalue");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_004"))
            .andExpect(jsonPath("$.error.message").value(
                    "No tag definition found for key: NONEXISTENT_TAG_KEY"));
    }

    @Test
    void createEntityTag_withUndefinedMixedCaseKey_returns400() throws Exception {
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.ENVIRONMENT, entityId, "Unknown_Tag", "val");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_004"));
    }

    // ========================================================================
    // Requirement 7.3, 7.4: Tag value must be in allowed_values when constrained
    // ========================================================================

    @Test
    void createEntityTag_withAllowedValue_succeeds() throws Exception {
        // SEQUENCING_ENABLED has allowed_values [true, false]
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.SOLUTION, entityId, "SEQUENCING_ENABLED", "false");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tagValue").value("false"));
    }

    @Test
    void createEntityTag_withDisallowedValue_returns400() throws Exception {
        // SEQUENCING_ENABLED only allows "true" or "false"
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.SOLUTION, entityId, "SEQUENCING_ENABLED", "maybe");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_003"))
            .andExpect(jsonPath("$.error.message").value(
                    "Invalid tag value 'maybe' for tag 'SEQUENCING_ENABLED'. Allowed values: [true, false]"));
    }

    @Test
    void createEntityTag_withNullValueWhenConstrained_returns400() throws Exception {
        // SEQUENCING_ENABLED has allowed_values, so null tagValue should be rejected
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.SOLUTION, entityId, "SEQUENCING_ENABLED", null);

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_003"));
    }

    @Test
    void createEntityTag_withUnconstrainedDefinition_acceptsAnyValue() throws Exception {
        // Create a tag definition with no allowed_values constraint
        createTagDefinition("UNCONSTRAINED_TAG", "A tag with no value constraints",
                List.of("ZONE"), null);

        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.ZONE, entityId, "UNCONSTRAINED_TAG", "anything-goes");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tagValue").value("anything-goes"));
    }

    // ========================================================================
    // Requirement 7.5, 7.6: Entity type must be in applicableEntityTypes
    // ========================================================================

    @Test
    void createEntityTag_withApplicableEntityType_succeeds() throws Exception {
        // SIF_LEGACY_MATRIX_PARAMS is applicable to ENVIRONMENT
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.ENVIRONMENT, entityId, "SIF_LEGACY_MATRIX_PARAMS", "true");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.entityType").value("ENVIRONMENT"))
            .andExpect(jsonPath("$.tagKey").value("SIF_LEGACY_MATRIX_PARAMS"));
    }

    @Test
    void createEntityTag_withNonApplicableEntityType_returns400() throws Exception {
        // SEQUENCING_ENABLED is only applicable to SOLUTION, not ENVIRONMENT
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.ENVIRONMENT, entityId, "SEQUENCING_ENABLED", "true");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_005"))
            .andExpect(jsonPath("$.error.message").value(
                    "Tag 'SEQUENCING_ENABLED' is not applicable to entity type: ENVIRONMENT"));
    }

    @Test
    void createEntityTag_withZoneOnSolutionOnlyTag_returns400() throws Exception {
        // SEQUENCING_ENABLED is only applicable to SOLUTION, not ZONE
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.ZONE, entityId, "SEQUENCING_ENABLED", "true");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_005"));
    }

    @Test
    void createEntityTag_withMultipleApplicableTypes_succeedsForEach() throws Exception {
        // Create a definition applicable to both ZONE and CONTEXT
        createTagDefinition("MULTI_TYPE_TAG", "Applicable to multiple types",
                List.of("ZONE", "CONTEXT"), null);

        UUID zoneEntityId = UUID.randomUUID();
        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.ZONE, zoneEntityId, "MULTI_TYPE_TAG", "val1"))))
            .andExpect(status().isCreated());

        UUID contextEntityId = UUID.randomUUID();
        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.CONTEXT, contextEntityId, "MULTI_TYPE_TAG", "val2"))))
            .andExpect(status().isCreated());
    }

    @Test
    void createEntityTag_withMultipleApplicableTypes_rejectsNonApplicable() throws Exception {
        // Create a definition applicable to ZONE and CONTEXT only
        createTagDefinition("ZONE_CONTEXT_ONLY", "Only for zones and contexts",
                List.of("ZONE", "CONTEXT"), null);

        UUID entityId = UUID.randomUUID();
        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.SOLUTION, entityId, "ZONE_CONTEXT_ONLY", "val"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_005"));
    }

    // ========================================================================
    // Combined validation: all constraints enforced together
    // ========================================================================

    @Test
    void createEntityTag_withCustomDefinition_fullValidation() throws Exception {
        // Create a definition with both allowed_values and specific entity types
        createTagDefinition("PRIORITY_LEVEL", "Priority level for queues",
                List.of("QUEUE"), List.of("HIGH", "MEDIUM", "LOW"));

        UUID entityId = UUID.randomUUID();

        // Valid: correct entity type and allowed value
        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.QUEUE, entityId, "PRIORITY_LEVEL", "HIGH"))))
            .andExpect(status().isCreated());

        // Invalid value
        UUID entityId2 = UUID.randomUUID();
        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.QUEUE, entityId2, "PRIORITY_LEVEL", "CRITICAL"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_003"));

        // Invalid entity type
        UUID entityId3 = UUID.randomUUID();
        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.ZONE, entityId3, "PRIORITY_LEVEL", "HIGH"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_005"));
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    /**
     * Creates a tag definition directly via native SQL (bypasses JPA converter issues in test context).
     */
    private void createTagDefinition(String tagName, String description,
                                     List<String> applicableEntityTypes,
                                     List<String> allowedValues) {
        String sql = "INSERT INTO tag_definitions (id, tag_name, tag_description, applicable_entity_types, allowed_values, created_by, created_at, updated_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?::varchar[], ?::varchar[], 'test-admin', now(), now())";
        String entityTypesArray = "{" + String.join(",", applicableEntityTypes) + "}";
        String valuesArray = allowedValues != null ? "{" + String.join(",", allowedValues) + "}" : null;
        jdbcTemplate.update(sql, tagName.toUpperCase(), description, entityTypesArray, valuesArray);
    }
}
