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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EntityTagCreateRequest;
import org.sifunity.broker.environment.dto.EntityTagDTO;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.TagDefinitionCreateRequest;
import org.sifunity.broker.environment.service.TagDefinitionService;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for entity tag CRUD operations.
 *
 * <p>Validates the complete CRUD lifecycle of entity tags through the REST API:
 * create, list, get, delete, duplicate rejection, and cascade deletion.</p>
 *
 * <p><strong>Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.5, 11.6</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfiguration.class)
@ActiveProfiles({"test", "disable-security"})
@Transactional
class EntityTagCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TagDefinitionService tagDefinitionService;

    private String testDomainId;
    private String testSolutionId;
    private String tagsBasePath;

    @BeforeEach
    void setUp() throws Exception {
        testDomainId = "tag-test-domain-" + System.currentTimeMillis();
        testSolutionId = "tag-test-solution-" + System.currentTimeMillis();
        tagsBasePath = "/" + testDomainId + "/" + testSolutionId + "/tags";

        // Create tag definitions for all tag keys used in tests.
        // The EntityTagService validates against tag definitions before creating entity tags.
        ensureTagDefinition("env-tier", List.of("ZONE", "ENVIRONMENT", "SOLUTION"));
        ensureTagDefinition("key1", List.of("ZONE", "ENVIRONMENT", "SOLUTION"));
        ensureTagDefinition("key2", List.of("ZONE", "ENVIRONMENT", "SOLUTION"));
        ensureTagDefinition("sdpc", List.of("ENVIRONMENT"));
        ensureTagDefinition("temp", List.of("ZONE"));
        ensureTagDefinition("dup-key", List.of("SOLUTION", "ZONE", "ENVIRONMENT"));
        ensureTagDefinition("cascade-key", List.of("ZONE"));

        // Create domain
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new DomainCreateRequest(testDomainId, "Tag test domain"))))
            .andExpect(status().isCreated());

        // Create solution
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SolutionCreateRequest(testDomainId, testSolutionId, "Tag test solution"))))
            .andExpect(status().isCreated());
    }

    @Test
    void testCreateTag() throws Exception {
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.ZONE, entityId, "env-tier", "production");

        MvcResult result = mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.entityType").value("ZONE"))
            .andExpect(jsonPath("$.tagKey").value("env-tier"))
            .andExpect(jsonPath("$.tagValue").value("production"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn();

        EntityTagDTO dto = objectMapper.readValue(
                result.getResponse().getContentAsString(), EntityTagDTO.class);
        assertThat(dto.solutionId()).isNotNull();
        assertThat(dto.entityId()).isEqualTo(entityId);
    }

    @Test
    void testListTagsByEntity() throws Exception {
        UUID entityId = UUID.randomUUID();

        // Create two tags on the same entity
        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.ZONE, entityId, "key1", "val1"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.ZONE, entityId, "key2", "val2"))))
            .andExpect(status().isCreated());

        mockMvc.perform(get(tagsBasePath)
                .param("entityType", "ZONE")
                .param("entityId", entityId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testGetSpecificTag() throws Exception {
        UUID entityId = UUID.randomUUID();

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.ENVIRONMENT, entityId, "sdpc", "true"))))
            .andExpect(status().isCreated());

        mockMvc.perform(get(tagsBasePath + "/{entityType}/{entityId}/{tagKey}",
                "ENVIRONMENT", entityId, "sdpc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tagKey").value("sdpc"))
            .andExpect(jsonPath("$.tagValue").value("true"))
            .andExpect(jsonPath("$.entityType").value("ENVIRONMENT"));
    }

    @Test
    void testDeleteTag() throws Exception {
        UUID entityId = UUID.randomUUID();

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.ZONE, entityId, "temp", "val"))))
            .andExpect(status().isCreated());

        // Delete
        mockMvc.perform(delete(tagsBasePath + "/{entityType}/{entityId}/{tagKey}",
                "ZONE", entityId, "temp"))
            .andExpect(status().isNoContent());

        // Verify gone
        mockMvc.perform(get(tagsBasePath + "/{entityType}/{entityId}/{tagKey}",
                "ZONE", entityId, "temp"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testCreateDuplicateTagReturnsConflict() throws Exception {
        UUID entityId = UUID.randomUUID();
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                EntityType.SOLUTION, entityId, "dup-key", "val");

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Same tag again
        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }

    @Test
    void testCascadeDeletionOnSolutionDelete() throws Exception {
        UUID entityId = UUID.randomUUID();

        mockMvc.perform(post(tagsBasePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new EntityTagCreateRequest(EntityType.ZONE, entityId, "cascade-key", "val"))))
            .andExpect(status().isCreated());

        // Delete the solution (should cascade-delete tags)
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", testSolutionId))
            .andExpect(status().isNoContent());

        // Solution is gone, so tag endpoint should return 404 (solution not found)
        mockMvc.perform(get(tagsBasePath))
            .andExpect(status().isNotFound());
    }

    /**
     * Ensures a tag definition exists for the given tag key.
     * Creates one if it doesn't already exist (e.g., not seeded by Flyway).
     */
    private void ensureTagDefinition(String tagName, List<String> applicableEntityTypes) {
        if (tagDefinitionService.findByTagName(tagName).isEmpty()) {
            tagDefinitionService.createTagDefinition(new TagDefinitionCreateRequest(
                    tagName, "Test tag: " + tagName, applicableEntityTypes, null, "test-setup"));
        }
    }
}
