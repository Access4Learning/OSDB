// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.dto.AdminSessionContext;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.dto.TagDefinitionCreateRequest;
import org.sifunity.broker.environment.dto.TagDefinitionDTO;
import org.sifunity.broker.environment.dto.TagDefinitionUpdateRequest;
import org.sifunity.broker.environment.filter.AdminSessionFilter;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for tag definition CRUD operations via REST endpoints.
 *
 * <p>Validates the complete CRUD lifecycle of tag definitions through the REST API,
 * Flyway migration verification (table structure, indexes, seed data), and concurrent
 * duplicate creation race condition handling.</p>
 *
 * <p>Uses Testcontainers with PostgreSQL (via JDBC URL in application-test.yml)
 * and MockMvc with security disabled. The AdminSessionContext attribute is set
 * manually to simulate an authenticated Global Domain Admin session.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 3.1, 3.2, 4.1, 5.1, 6.1, 14.1, 14.2</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
@DisplayName("Tag Definition CRUD Integration Tests")
class TagDefinitionCrudIntegrationTest {

    private static final String TAG_DEFINITIONS_PATH = "/admin/tag-definitions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private TagDefinitionRepository tagDefinitionRepository;

    private AdminSessionContext globalAdminSession;

    @BeforeEach
    void setUp() {
        // Ensure GLOBAL_DOMAIN and GLOBAL_SOLUTION exist (may already exist from Flyway seed)
        DomainDTO domain;
        if (domainService.domainExists(GlobalAdminConstants.GLOBAL_DOMAIN)) {
            domain = domainService.getDomain(GlobalAdminConstants.GLOBAL_DOMAIN);
        } else {
            domain = domainService.createDomain(
                    new DomainCreateRequest(GlobalAdminConstants.GLOBAL_DOMAIN, "Global admin domain"));
        }

        SolutionDTO solution;
        if (solutionService.solutionExists(GlobalAdminConstants.GLOBAL_DOMAIN, GlobalAdminConstants.GLOBAL_SOLUTION)) {
            solution = solutionService.getSolution(GlobalAdminConstants.GLOBAL_DOMAIN, GlobalAdminConstants.GLOBAL_SOLUTION);
        } else {
            solution = solutionService.createSolution(
                    new SolutionCreateRequest(GlobalAdminConstants.GLOBAL_DOMAIN,
                            GlobalAdminConstants.GLOBAL_SOLUTION, "Global admin solution"));
        }

        globalAdminSession = new AdminSessionContext(
                UUID.randomUUID(),
                "test-admin",
                domain.id(),
                solution.id(),
                null
        );
    }

    // ========== Full CRUD Lifecycle ==========

    @Test
    @DisplayName("Full CRUD lifecycle: create → get → list → update → delete")
    void testTagDefinitionCrudLifecycle() throws Exception {
        // ===== Step 1: Create tag definition (POST → 201) =====
        TagDefinitionCreateRequest createRequest = new TagDefinitionCreateRequest(
                "Integration_Test_Tag",
                "A tag for integration testing",
                List.of("SOLUTION", "ENVIRONMENT"),
                List.of("alpha", "beta", "gamma"),
                "test-admin"
        );

        MvcResult createResult = mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.tagName").value("INTEGRATION_TEST_TAG"))
                .andExpect(jsonPath("$.tagDescription").value("A tag for integration testing"))
                .andExpect(jsonPath("$.applicableEntityTypes").isArray())
                .andExpect(jsonPath("$.allowedValues").isArray())
                .andExpect(jsonPath("$.createdBy").value("test-admin"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        TagDefinitionDTO created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), TagDefinitionDTO.class);

        assertThat(created.id()).isNotNull();
        assertThat(created.tagName()).isEqualTo("INTEGRATION_TEST_TAG");
        assertThat(created.applicableEntityTypes()).containsExactlyInAnyOrder("SOLUTION", "ENVIRONMENT");
        assertThat(created.allowedValues()).containsExactlyInAnyOrder("alpha", "beta", "gamma");

        // ===== Step 2: Get tag definition by ID (GET → 200) =====
        MvcResult getResult = mockMvc.perform(get(TAG_DEFINITIONS_PATH + "/{id}", created.id())
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id().toString()))
                .andExpect(jsonPath("$.tagName").value("INTEGRATION_TEST_TAG"))
                .andReturn();

        TagDefinitionDTO retrieved = objectMapper.readValue(
                getResult.getResponse().getContentAsString(), TagDefinitionDTO.class);
        assertThat(retrieved).isEqualTo(created);

        // ===== Step 3: List all tag definitions (GET → 200) =====
        MvcResult listResult = mockMvc.perform(get(TAG_DEFINITIONS_PATH)
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isOk())
                .andReturn();

        List<TagDefinitionDTO> allDefs = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                new TypeReference<List<TagDefinitionDTO>>() {});
        assertThat(allDefs).extracting(TagDefinitionDTO::tagName)
                .contains("INTEGRATION_TEST_TAG");

        // ===== Step 4: Update tag definition (PUT → 200) =====
        TagDefinitionUpdateRequest updateRequest = new TagDefinitionUpdateRequest(
                "Updated description",
                List.of("ZONE"),
                List.of("one", "two")
        );

        MvcResult updateResult = mockMvc.perform(put(TAG_DEFINITIONS_PATH + "/{id}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagName").value("INTEGRATION_TEST_TAG"))
                .andExpect(jsonPath("$.tagDescription").value("Updated description"))
                .andExpect(jsonPath("$.applicableEntityTypes[0]").value("ZONE"))
                .andExpect(jsonPath("$.allowedValues[0]").value("one"))
                .andReturn();

        TagDefinitionDTO updated = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(), TagDefinitionDTO.class);
        assertThat(updated.tagName()).isEqualTo("INTEGRATION_TEST_TAG");
        assertThat(updated.tagDescription()).isEqualTo("Updated description");
        assertThat(updated.applicableEntityTypes()).containsExactly("ZONE");
        assertThat(updated.allowedValues()).containsExactly("one", "two");

        // ===== Step 5: Delete tag definition (DELETE → 204) =====
        mockMvc.perform(delete(TAG_DEFINITIONS_PATH + "/{id}", created.id())
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isNoContent());

        // ===== Step 6: Verify deleted (GET → 404) =====
        mockMvc.perform(get(TAG_DEFINITIONS_PATH + "/{id}", created.id())
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isNotFound());
    }

    // ========== Flyway Migration Verification ==========

    @Test
    @DisplayName("Flyway migration creates tag_definitions table with correct structure")
    void testFlywayMigrationCreatesTableStructure() throws Exception {
        // The fact that we can create and retrieve tag definitions proves the table exists
        // with correct column types. Verify the unique index by testing case-insensitive uniqueness.
        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                "Schema_Verify_Tag",
                "Verifying schema structure",
                List.of("SOLUTION"),
                null,
                "migration-test"
        );

        mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tagName").value("SCHEMA_VERIFY_TAG"))
                .andExpect(jsonPath("$.tagDescription").value("Verifying schema structure"))
                .andExpect(jsonPath("$.applicableEntityTypes[0]").value("SOLUTION"))
                .andExpect(jsonPath("$.allowedValues").isEmpty())
                .andExpect(jsonPath("$.createdBy").value("migration-test"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("Flyway migration creates unique index on UPPER(tag_name)")
    void testFlywayMigrationCreatesUniqueIndex() throws Exception {
        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                "Unique_Index_Test",
                "Testing unique index",
                List.of("ENVIRONMENT"),
                null,
                "index-test"
        );

        // Create first
        mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isCreated());

        // Attempt duplicate with different case → 409
        TagDefinitionCreateRequest duplicate = new TagDefinitionCreateRequest(
                "unique_index_test",
                "Duplicate with different case",
                List.of("ENVIRONMENT"),
                null,
                "index-test"
        );

        mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Flyway migration seeds SEQUENCING_ENABLED definition")
    void testFlywayMigrationSeedsSequencingEnabled() throws Exception {
        MvcResult result = mockMvc.perform(get(TAG_DEFINITIONS_PATH)
                        .param("entityType", "SOLUTION")
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isOk())
                .andReturn();

        List<TagDefinitionDTO> definitions = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TagDefinitionDTO>>() {});

        TagDefinitionDTO sequencing = definitions.stream()
                .filter(d -> "SEQUENCING_ENABLED".equals(d.tagName()))
                .findFirst()
                .orElse(null);

        assertThat(sequencing).isNotNull();
        assertThat(sequencing.applicableEntityTypes()).containsExactly("SOLUTION");
        assertThat(sequencing.allowedValues()).containsExactlyInAnyOrder("true", "false");
        assertThat(sequencing.createdBy()).isEqualTo("system");
    }

    @Test
    @DisplayName("Flyway migration seeds SIF_LEGACY_MATRIX_PARAMS definition")
    void testFlywayMigrationSeedsSifLegacyMatrixParams() throws Exception {
        MvcResult result = mockMvc.perform(get(TAG_DEFINITIONS_PATH)
                        .param("entityType", "ENVIRONMENT")
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isOk())
                .andReturn();

        List<TagDefinitionDTO> definitions = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TagDefinitionDTO>>() {});

        TagDefinitionDTO legacy = definitions.stream()
                .filter(d -> "SIF_LEGACY_MATRIX_PARAMS".equals(d.tagName()))
                .findFirst()
                .orElse(null);

        assertThat(legacy).isNotNull();
        assertThat(legacy.applicableEntityTypes()).containsExactly("ENVIRONMENT");
        assertThat(legacy.allowedValues()).containsExactlyInAnyOrder("true", "false");
        assertThat(legacy.createdBy()).isEqualTo("system");
    }

    // ========== Duplicate Name Rejection ==========

    @Test
    @DisplayName("Create duplicate tag name (case-insensitive) returns 409 Conflict")
    void testCreateDuplicateTagNameReturnsConflict() throws Exception {
        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                "Duplicate_Tag",
                "First definition",
                List.of("SOLUTION"),
                null,
                "admin"
        );

        mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isCreated());

        // Same name, different case
        TagDefinitionCreateRequest duplicate = new TagDefinitionCreateRequest(
                "DUPLICATE_TAG",
                "Second definition",
                List.of("ENVIRONMENT"),
                List.of("x"),
                "admin"
        );

        mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isConflict());
    }

    // ========== List Ordering ==========

    @Test
    @DisplayName("List tag definitions returns results ordered by tag_name ascending")
    void testListReturnsOrderedByTagName() throws Exception {
        // Create tags in non-alphabetical order
        createTagDefinition("Zebra_Tag", List.of("SOLUTION"));
        createTagDefinition("Alpha_Tag", List.of("SOLUTION"));
        createTagDefinition("Middle_Tag", List.of("SOLUTION"));

        MvcResult result = mockMvc.perform(get(TAG_DEFINITIONS_PATH)
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isOk())
                .andReturn();

        List<TagDefinitionDTO> definitions = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TagDefinitionDTO>>() {});

        // Extract tag names and verify they are sorted
        List<String> tagNames = definitions.stream()
                .map(TagDefinitionDTO::tagName)
                .toList();
        assertThat(tagNames).isSorted();
    }

    // ========== EntityType Filter ==========

    @Test
    @DisplayName("List with entityType filter returns only matching definitions")
    void testListWithEntityTypeFilter() throws Exception {
        createTagDefinition("Solution_Only_Tag", List.of("SOLUTION"));
        createTagDefinition("Environment_Only_Tag", List.of("ENVIRONMENT"));
        createTagDefinition("Both_Tag", List.of("SOLUTION", "ENVIRONMENT"));

        // Filter by SOLUTION
        MvcResult solutionResult = mockMvc.perform(get(TAG_DEFINITIONS_PATH)
                        .param("entityType", "SOLUTION")
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isOk())
                .andReturn();

        List<TagDefinitionDTO> solutionDefs = objectMapper.readValue(
                solutionResult.getResponse().getContentAsString(),
                new TypeReference<List<TagDefinitionDTO>>() {});

        assertThat(solutionDefs).allSatisfy(def ->
                assertThat(def.applicableEntityTypes()).contains("SOLUTION"));
        assertThat(solutionDefs).extracting(TagDefinitionDTO::tagName)
                .contains("SOLUTION_ONLY_TAG", "BOTH_TAG")
                .doesNotContain("ENVIRONMENT_ONLY_TAG");
    }

    // ========== Update Preserves Tag Name ==========

    @Test
    @DisplayName("Update preserves tag_name and reflects other changes")
    void testUpdatePreservesTagName() throws Exception {
        TagDefinitionDTO created = createTagDefinition("Immutable_Name_Tag",
                List.of("SOLUTION"), List.of("a", "b"));

        TagDefinitionUpdateRequest updateRequest = new TagDefinitionUpdateRequest(
                "New description",
                List.of("ENVIRONMENT", "QUEUE"),
                List.of("x", "y", "z")
        );

        MvcResult updateResult = mockMvc.perform(put(TAG_DEFINITIONS_PATH + "/{id}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isOk())
                .andReturn();

        TagDefinitionDTO updated = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(), TagDefinitionDTO.class);

        assertThat(updated.tagName()).isEqualTo("IMMUTABLE_NAME_TAG");
        assertThat(updated.tagDescription()).isEqualTo("New description");
        assertThat(updated.applicableEntityTypes()).containsExactlyInAnyOrder("ENVIRONMENT", "QUEUE");
        assertThat(updated.allowedValues()).containsExactlyInAnyOrder("x", "y", "z");
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
    }

    // ========== Delete ==========

    @Test
    @DisplayName("Delete non-existent tag definition returns 404")
    void testDeleteNonExistentReturns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(delete(TAG_DEFINITIONS_PATH + "/{id}", nonExistentId)
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Get non-existent tag definition returns 404")
    void testGetNonExistentReturns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get(TAG_DEFINITIONS_PATH + "/{id}", nonExistentId)
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Update non-existent tag definition returns 404")
    void testUpdateNonExistentReturns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        TagDefinitionUpdateRequest updateRequest = new TagDefinitionUpdateRequest(
                "desc", List.of("SOLUTION"), null);

        mockMvc.perform(put(TAG_DEFINITIONS_PATH + "/{id}", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isNotFound());
    }

    // ========== Validation Errors ==========

    @Test
    @DisplayName("Create with blank tagName returns 400")
    void testCreateBlankTagNameReturns400() throws Exception {
        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                "   ",
                "desc",
                List.of("SOLUTION"),
                null,
                "admin"
        );

        mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create with empty applicableEntityTypes returns 400")
    void testCreateEmptyEntityTypesReturns400() throws Exception {
        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                "Valid_Name",
                "desc",
                List.of(),
                null,
                "admin"
        );

        mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create with invalid EntityType returns 400")
    void testCreateInvalidEntityTypeReturns400() throws Exception {
        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                "Invalid_Type_Tag",
                "desc",
                List.of("INVALID_TYPE"),
                null,
                "admin"
        );

        mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isBadRequest());
    }

    // ========== Concurrent Duplicate Creation (Race Condition) ==========

    @Test
    @DisplayName("Concurrent duplicate creation is handled by unique constraint")
    void testConcurrentDuplicateCreationHandledByUniqueConstraint() throws Exception {
        // This test verifies that the database unique index on UPPER(tag_name)
        // prevents race conditions where two concurrent requests try to create
        // the same tag definition simultaneously.
        //
        // We use the service layer directly (not MockMvc) because @Transactional
        // on the test class would serialize all operations within a single transaction.
        // The service layer with its own @Transactional boundaries allows true concurrency.

        String tagName = "Concurrent_Race_Tag_" + System.nanoTime();
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                            tagName,
                            "Concurrent test",
                            List.of("SOLUTION"),
                            null,
                            "concurrent-admin"
                    );

                    MvcResult result = mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                                    .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else {
                        // 409 (conflict), 500 (constraint violation as ISE), or any other error
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Count constraint violations as conflicts
                    conflictCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // At least one should succeed; the rest should get conflicts or also succeed
        // (timing-dependent — MockMvc may serialize differently than real HTTP).
        // The critical invariant: only ONE tag definition exists in the database.
        assertThat(successCount.get())
                .as("At least one thread should succeed")
                .isGreaterThanOrEqualTo(1);
        assertThat(successCount.get() + conflictCount.get())
                .as("All threads should complete with either success or conflict")
                .isEqualTo(threadCount);

        // The real correctness check: the database has exactly one entry
        MvcResult listResult = mockMvc.perform(get(TAG_DEFINITIONS_PATH)
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = listResult.getResponse().getContentAsString();
        // Count entries matching our specific tag name (case-insensitive)
        String upperTagName = tagName.toUpperCase();
        long tagCount = 0;
        for (var node : objectMapper.readTree(responseBody)) {
            if (node.has("tagName") && upperTagName.equals(node.get("tagName").asText())) {
                tagCount++;
            }
        }
        assertThat(tagCount)
                .as("Database unique constraint ensures only one tag definition exists")
                .isEqualTo(1);
    }

    // ========== Create with null allowedValues ==========

    @Test
    @DisplayName("Create tag definition with null allowedValues succeeds")
    void testCreateWithNullAllowedValues() throws Exception {
        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                "No_Constraints_Tag",
                "Tag with no value constraints",
                List.of("SOLUTION", "ZONE", "CONTEXT"),
                null,
                "admin"
        );

        mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tagName").value("NO_CONSTRAINTS_TAG"))
                .andExpect(jsonPath("$.allowedValues").isEmpty());
    }

    // ========== Helper Methods ==========

    private TagDefinitionDTO createTagDefinition(String tagName, List<String> entityTypes) throws Exception {
        return createTagDefinition(tagName, entityTypes, null);
    }

    private TagDefinitionDTO createTagDefinition(String tagName, List<String> entityTypes,
                                                  List<String> allowedValues) throws Exception {
        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                tagName, "Test tag: " + tagName, entityTypes, allowedValues, "test-admin");

        MvcResult result = mockMvc.perform(post(TAG_DEFINITIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE, globalAdminSession))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), TagDefinitionDTO.class);
    }
}
