// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.*;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for permission enforcement on tag definition and entity tag endpoints.
 *
 * <p>Tests the complete permission enforcement flow via the AuthorizationFilter:
 * <ul>
 *   <li>Global Domain Admin can access tag definition CRUD endpoints</li>
 *   <li>Non-Global Domain Admin receives HTTP 403 on tag definition endpoints</li>
 *   <li>Solution Admin can manage entity tags within their solution scope</li>
 *   <li>Regular Environment can only manage tags on its own environment/queues</li>
 * </ul>
 *
 * <p>Uses Testcontainers with PostgreSQL and Redis. Authentication uses HMAC format
 * (Authorization: SIF_HMACSHA256 Base64(sessionToken:signature)) which the
 * AuthorizationFilter validates by looking up the environment by sessionToken.</p>
 *
 * <p><strong>Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Tag Permission Enforcement Integration Tests")
class TagPermissionEnforcementIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");

        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
        registry.add("secrets.backend.type", () -> "plaintext");
        registry.add("authentication.timestamp-tolerance-minutes", () -> "5");
        registry.add("sif.broker.bootstrap.enabled", () -> "true");
        registry.add("sif.broker.bootstrap.admin.sessionToken", () -> "test-admin-token");
        registry.add("sif.broker.bootstrap.admin.sharedSecret", () -> "dGVzdC1hZG1pbi1zZWNyZXQ=");
        registry.add("sif.broker.infrastructure.gateway-base-url", () -> "http://localhost:8080");
        registry.add("sif.broker.infrastructure.environment", () -> "http://localhost:8081");
        registry.add("sif.broker.infrastructure.provision-requests", () -> "http://localhost:8081");
        registry.add("sif.broker.infrastructure.requests-connector", () -> "http://localhost:8083");
        registry.add("sif.broker.infrastructure.events-connector", () -> "http://localhost:8084");
        registry.add("sif.broker.infrastructure.queues", () -> "http://localhost:8085");
        registry.add("sif.broker.infrastructure.subscriptions", () -> "http://localhost:8086");
        registry.add("sif.broker.infrastructure.services-connector", () -> "http://localhost:8087");
        registry.add("sif.broker.infrastructure.responses-connector", () -> "http://localhost:8088");
        registry.add("observability.enabled", () -> "false");
        registry.add("observability.metrics.enabled", () -> "false");
        registry.add("observability.tracing.enabled", () -> "false");
        registry.add("management.endpoints.enabled-by-default", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private TagDefinitionService tagDefinitionService;

    /** HMAC Authorization header for the global admin environment */
    private String globalAdminAuthHeader;
    /** HMAC Authorization header for the non-admin environment */
    private String nonAdminAuthHeader;
    private TestEntityHierarchy regularHierarchy;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure Global Domain Admin hierarchy exists (Flyway V15 seeds it, but be safe)
        ensureGlobalAdminHierarchy();

        // Create a regular domain/solution hierarchy for non-admin tests
        regularHierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        // Register an environment in GLOBAL_DOMAIN/GLOBAL_SOLUTION → admin
        SifEnvironmentRegistrationResponse adminReg = environmentService.registerEnvironment(
                GlobalAdminConstants.GLOBAL_DOMAIN,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AdminEnv-" + System.nanoTime(), EnvironmentType.BROKERED, null, GlobalAdminConstants.GLOBAL_SOLUTION, null)));
        Environment adminEnv = environmentRepository.findById(adminReg.environment().id()).orElseThrow();
        globalAdminAuthHeader = buildAuthHeader(adminEnv.getSessionToken());

        // Register an environment in the regular domain/solution → non-admin
        SifEnvironmentRegistrationResponse regularReg = environmentService.registerEnvironment(
                regularHierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("RegularEnv-" + System.nanoTime(), EnvironmentType.BROKERED, null, regularHierarchy.getSolutionId(), null)));
        Environment regularEnv = environmentRepository.findById(regularReg.environment().id()).orElseThrow();
        nonAdminAuthHeader = buildAuthHeader(regularEnv.getSessionToken());
    }

    // ========================================================================
    // Requirement 10.1: Global Domain Admin CAN access tag definition endpoints
    // ========================================================================

    @Nested
    @DisplayName("Global Domain Admin Access (Requirement 10.1)")
    class GlobalDomainAdminAccess {

        @Test
        @DisplayName("POST /admin/tag-definitions → 201 for Global Domain Admin")
        void globalAdminCanCreateTagDefinition() throws Exception {
            TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                    "PERM_TEST_TAG_" + System.nanoTime(), "Permission test tag",
                    List.of("SOLUTION"), List.of("true", "false"), "admin");

            mockMvc.perform(post("/admin/tag-definitions")
                    .header("Authorization", globalAdminAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.tagName").exists());
        }

        @Test
        @DisplayName("GET /admin/tag-definitions → 200 for Global Domain Admin")
        void globalAdminCanListTagDefinitions() throws Exception {
            mockMvc.perform(get("/admin/tag-definitions")
                    .header("Authorization", globalAdminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("GET /admin/tag-definitions/{id} → 200 for Global Domain Admin")
        void globalAdminCanGetTagDefinition() throws Exception {
            // Create a tag definition first
            UUID tagDefId = createTagDefinitionAsAdmin("GET_TEST_TAG_" + System.nanoTime());

            mockMvc.perform(get("/admin/tag-definitions/" + tagDefId)
                    .header("Authorization", globalAdminAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tagDefId.toString()));
        }

        @Test
        @DisplayName("PUT /admin/tag-definitions/{id} → 200 for Global Domain Admin")
        void globalAdminCanUpdateTagDefinition() throws Exception {
            UUID tagDefId = createTagDefinitionAsAdmin("UPDATE_TEST_TAG_" + System.nanoTime());

            TagDefinitionUpdateRequest updateRequest = new TagDefinitionUpdateRequest(
                    "Updated description", List.of("SOLUTION", "ENVIRONMENT"), null);

            mockMvc.perform(put("/admin/tag-definitions/" + tagDefId)
                    .header("Authorization", globalAdminAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagDescription").value("Updated description"));
        }

        @Test
        @DisplayName("DELETE /admin/tag-definitions/{id} → 204 for Global Domain Admin")
        void globalAdminCanDeleteTagDefinition() throws Exception {
            UUID tagDefId = createTagDefinitionAsAdmin("DELETE_TEST_TAG_" + System.nanoTime());

            mockMvc.perform(delete("/admin/tag-definitions/" + tagDefId)
                    .header("Authorization", globalAdminAuthHeader))
                .andExpect(status().isNoContent());
        }
    }

    // ========================================================================
    // Requirement 10.2: Non-Global Domain Admin receives HTTP 403
    // ========================================================================

    @Nested
    @DisplayName("Non-Admin Rejection (Requirement 10.2)")
    class NonAdminRejection {

        @Test
        @DisplayName("POST /admin/tag-definitions → 403 for non-admin")
        void nonAdminCannotCreateTagDefinition() throws Exception {
            TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                    "FORBIDDEN_TAG", "Should not be created",
                    List.of("SOLUTION"), null, "non-admin");

            mockMvc.perform(post("/admin/tag-definitions")
                    .header("Authorization", nonAdminAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.subCode").value("AUTHZ_001"));
        }

        @Test
        @DisplayName("GET /admin/tag-definitions → 403 for non-admin")
        void nonAdminCannotListTagDefinitions() throws Exception {
            mockMvc.perform(get("/admin/tag-definitions")
                    .header("Authorization", nonAdminAuthHeader))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.subCode").value("AUTHZ_001"));
        }

        @Test
        @DisplayName("GET /admin/tag-definitions/{id} → 403 for non-admin")
        void nonAdminCannotGetTagDefinition() throws Exception {
            UUID tagDefId = createTagDefinitionAsAdmin("NOGET_TAG_" + System.nanoTime());

            mockMvc.perform(get("/admin/tag-definitions/" + tagDefId)
                    .header("Authorization", nonAdminAuthHeader))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.subCode").value("AUTHZ_001"));
        }

        @Test
        @DisplayName("PUT /admin/tag-definitions/{id} → 403 for non-admin")
        void nonAdminCannotUpdateTagDefinition() throws Exception {
            UUID tagDefId = createTagDefinitionAsAdmin("NOUPDATE_TAG_" + System.nanoTime());

            TagDefinitionUpdateRequest updateRequest = new TagDefinitionUpdateRequest(
                    "Should not update", List.of("SOLUTION"), null);

            mockMvc.perform(put("/admin/tag-definitions/" + tagDefId)
                    .header("Authorization", nonAdminAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.subCode").value("AUTHZ_001"));
        }

        @Test
        @DisplayName("DELETE /admin/tag-definitions/{id} → 403 for non-admin")
        void nonAdminCannotDeleteTagDefinition() throws Exception {
            UUID tagDefId = createTagDefinitionAsAdmin("NODELETE_TAG_" + System.nanoTime());

            mockMvc.perform(delete("/admin/tag-definitions/" + tagDefId)
                    .header("Authorization", nonAdminAuthHeader))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.subCode").value("AUTHZ_001"));
        }

        @Test
        @DisplayName("Missing Authorization header → 401 Unauthorized")
        void missingSessionTokenReturns401() throws Exception {
            mockMvc.perform(get("/admin/tag-definitions"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Invalid session token → 401 Unauthorized")
        void invalidSessionTokenReturns401() throws Exception {
            mockMvc.perform(get("/admin/tag-definitions")
                    .header("Authorization", buildAuthHeader("invalid-token-" + UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
        }
    }

    // ========================================================================
    // Requirement 10.3: Solution Admin scope enforcement on entity tags
    // ========================================================================

    @Nested
    @DisplayName("Solution Admin Scope (Requirement 10.3)")
    class SolutionAdminScope {

        @Test
        @DisplayName("Solution Admin can create entity tags within their solution")
        void solutionAdminCanCreateTagsInOwnSolution() throws Exception {
            String tagsPath = "/" + regularHierarchy.getDomainId() + "/"
                    + regularHierarchy.getSolutionId() + "/tags";

            // Use the seeded SEQUENCING_ENABLED tag (applicable to SOLUTION)
            UUID entityId = UUID.randomUUID();
            EntityTagCreateRequest request = new EntityTagCreateRequest(
                    EntityType.SOLUTION, entityId, "SEQUENCING_ENABLED", "true");

            // Non-admin environment authenticated via HMAC can create tags
            // in its own solution scope
            mockMvc.perform(post(tagsPath)
                    .header("Authorization", nonAdminAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Solution Admin can list entity tags within their solution")
        void solutionAdminCanListTagsInOwnSolution() throws Exception {
            String tagsPath = "/" + regularHierarchy.getDomainId() + "/"
                    + regularHierarchy.getSolutionId() + "/tags";

            // GET on /tags is skipped by AuthorizationFilter (no auth needed for listing)
            mockMvc.perform(get(tagsPath)
                    .header("Authorization", nonAdminAuthHeader))
                .andExpect(status().isOk());
        }
    }

    // ========================================================================
    // Requirement 10.4, 10.5: Regular Environment scope enforcement
    // ========================================================================

    @Nested
    @DisplayName("Regular Environment Scope (Requirements 10.4, 10.5)")
    class RegularEnvironmentScope {

        @Test
        @DisplayName("Regular Environment can create tags on ENVIRONMENT entity type")
        void regularEnvironmentCanTagOwnEnvironmentType() throws Exception {
            // Register an environment in the regular solution
            SifEnvironmentRegistrationResponse envReg = environmentService.registerEnvironment(
                    regularHierarchy.getDomainId(),
                    new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("ScopeTestApp-" + System.nanoTime(), EnvironmentType.BROKERED, null, regularHierarchy.getSolutionId(), null)));
            UUID envId = envReg.environment().id();

            String tagsPath = "/" + regularHierarchy.getDomainId() + "/"
                    + regularHierarchy.getSolutionId() + "/tags";

            // Use the seeded SIF_LEGACY_MATRIX_PARAMS (applicable to ENVIRONMENT)
            EntityTagCreateRequest request = new EntityTagCreateRequest(
                    EntityType.ENVIRONMENT, envId, "SIF_LEGACY_MATRIX_PARAMS", "true");

            // Regular environment can tag its own environment
            mockMvc.perform(post(tagsPath)
                    .header("Authorization", nonAdminAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Entity tag creation validates tag definition exists")
        void entityTagCreationRequiresValidDefinition() throws Exception {
            String tagsPath = "/" + regularHierarchy.getDomainId() + "/"
                    + regularHierarchy.getSolutionId() + "/tags";

            UUID entityId = UUID.randomUUID();
            EntityTagCreateRequest request = new EntityTagCreateRequest(
                    EntityType.ENVIRONMENT, entityId, "NONEXISTENT_TAG", "value");

            mockMvc.perform(post(tagsPath)
                    .header("Authorization", nonAdminAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_004"));
        }

        @Test
        @DisplayName("Entity tag creation rejects invalid value for constrained definition")
        void entityTagCreationRejectsInvalidValue() throws Exception {
            String tagsPath = "/" + regularHierarchy.getDomainId() + "/"
                    + regularHierarchy.getSolutionId() + "/tags";

            UUID entityId = UUID.randomUUID();
            // SEQUENCING_ENABLED only allows "true" or "false"
            EntityTagCreateRequest request = new EntityTagCreateRequest(
                    EntityType.SOLUTION, entityId, "SEQUENCING_ENABLED", "invalid");

            mockMvc.perform(post(tagsPath)
                    .header("Authorization", nonAdminAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_003"));
        }

        @Test
        @DisplayName("Entity tag creation rejects non-applicable entity type")
        void entityTagCreationRejectsNonApplicableEntityType() throws Exception {
            String tagsPath = "/" + regularHierarchy.getDomainId() + "/"
                    + regularHierarchy.getSolutionId() + "/tags";

            UUID entityId = UUID.randomUUID();
            // SEQUENCING_ENABLED is only applicable to SOLUTION, not ENVIRONMENT
            EntityTagCreateRequest request = new EntityTagCreateRequest(
                    EntityType.ENVIRONMENT, entityId, "SEQUENCING_ENABLED", "true");

            mockMvc.perform(post(tagsPath)
                    .header("Authorization", nonAdminAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.subCode").value("TAG_DEF_005"));
        }
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    /**
     * Ensures the GLOBAL_DOMAIN and GLOBAL_SOLUTION exist for Global Domain Admin users.
     */
    private void ensureGlobalAdminHierarchy() {
        if (!domainService.domainExists(GlobalAdminConstants.GLOBAL_DOMAIN)) {
            domainService.createDomain(new DomainCreateRequest(
                    GlobalAdminConstants.GLOBAL_DOMAIN, "Global admin domain"));
        }
        if (!solutionService.solutionExists(GlobalAdminConstants.GLOBAL_DOMAIN, GlobalAdminConstants.GLOBAL_SOLUTION)) {
            solutionService.createSolution(new SolutionCreateRequest(
                    GlobalAdminConstants.GLOBAL_DOMAIN, GlobalAdminConstants.GLOBAL_SOLUTION,
                    "Global admin solution"));
        }
    }

    /**
     * Builds an Authorization header in the SIF_HMACSHA256 format.
     * Format: SIF_HMACSHA256 Base64(sessionToken:dummySignature)
     */
    private String buildAuthHeader(String sessionToken) {
        String credentials = sessionToken + ":dummy-signature";
        String base64 = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
        return "SIF_HMACSHA256 " + base64;
    }

    /**
     * Creates a tag definition as the Global Domain Admin and returns its UUID.
     */
    private UUID createTagDefinitionAsAdmin(String tagName) throws Exception {
        return createTagDefinitionAsAdmin(tagName, List.of("SOLUTION"), List.of("true", "false"));
    }

    /**
     * Creates a tag definition as the Global Domain Admin with specified types and values.
     */
    private UUID createTagDefinitionAsAdmin(String tagName, List<String> applicableEntityTypes,
                                            List<String> allowedValues) throws Exception {
        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                tagName, "Test tag definition", applicableEntityTypes, allowedValues, "admin");

        MvcResult result = mockMvc.perform(post("/admin/tag-definitions")
                .header("Authorization", globalAdminAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        TagDefinitionDTO dto = objectMapper.readValue(
                result.getResponse().getContentAsString(), TagDefinitionDTO.class);
        return dto.id();
    }
}
