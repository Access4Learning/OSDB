// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

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
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.AdminUserCreateRequest;
import org.sifunity.broker.environment.dto.AdminUserDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Integration test for admin user CRUD lifecycle via REST endpoints.
 *
 * <p>Tests the full create/read/list/delete lifecycle, validation errors,
 * and duplicate username rejection through the AdminUserController.</p>
 *
 * <p>Uses Testcontainers with PostgreSQL (via JDBC URL in application-test.yml)
 * and MockMvc with security disabled. The requester attribute is set manually
 * to simulate an authenticated global admin environment.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.5, 1.6, 1.7, 1.8, 1.9</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
@DisplayName("Admin User CRUD Integration Tests")
class AdminUserCrudIntegrationTest {

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

    private TestEntityHierarchy hierarchy;
    private UUID domainUuid;
    private UUID solutionUuid;
    private String domainId;
    private String solutionId;
    private Environment adminEnvironment;

    @BeforeEach
    void setUp() {
        // Create a domain + solution + zone hierarchy for test data
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        domainUuid = hierarchy.getDomainUuid();
        solutionUuid = hierarchy.getSolutionUuid();
        domainId = hierarchy.getDomainId();
        solutionId = hierarchy.getSolutionId();

        // Register a global admin environment in the GLOBAL_SOLUTION (created by BootstrapComponent)
        // so that isGlobalAdmin() returns true and the requester can manage any scope
        SifEnvironmentRegistrationResponse adminReg = environmentService.registerEnvironment(
                GlobalAdminConstants.GLOBAL_DOMAIN,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AdminApp-" + System.nanoTime(), EnvironmentType.BROKERED, null, GlobalAdminConstants.GLOBAL_SOLUTION, null)));
        adminEnvironment = environmentRepository.findById(adminReg.environment().id()).orElseThrow();
    }

    // ========== Happy Path: Full CRUD Lifecycle ==========

    @Test
    @DisplayName("POST /admin-users â†’ 201 Created, then GET/LIST/DELETE lifecycle")
    void testAdminUserCrudLifecycle() throws Exception {
        String username = "testuser-" + System.nanoTime();
        String password = "securePass123";

        // ===== Step 1: Create admin user (POST /admin-users â†’ 201) =====
        AdminUserCreateRequest createRequest = new AdminUserCreateRequest(
                username, password, domainId, solutionId);

        MvcResult createResult = mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.domainUuid").value(domainUuid.toString()))
                .andExpect(jsonPath("$.solutionUuid").value(solutionUuid.toString()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        AdminUserDTO createdUser = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), AdminUserDTO.class);

        assertThat(createdUser).isNotNull();
        assertThat(createdUser.id()).isNotNull();
        assertThat(createdUser.username()).isEqualTo(username);
        assertThat(createdUser.domainUuid()).isEqualTo(domainUuid);
        assertThat(createdUser.solutionUuid()).isEqualTo(solutionUuid);

        UUID userId = createdUser.id();

        // ===== Step 2: Get admin user by ID (GET /admin-users/{id} â†’ 200) =====
        mockMvc.perform(get("/admin-users/{id}", userId)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.domainUuid").value(domainUuid.toString()))
                .andExpect(jsonPath("$.solutionUuid").value(solutionUuid.toString()));

        // ===== Step 3: List admin users (GET /admin-users?domainId=&solutionId= â†’ 200) =====
        mockMvc.perform(get("/admin-users")
                        .param("domainId", domainId)
                        .param("solutionId", solutionId)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.adminUsers.adminUser").isArray())
                .andExpect(jsonPath("$.adminUsers.adminUser[?(@.username == '" + username + "')]").exists());

        // ===== Step 4: Delete admin user (DELETE /admin-users/{id} â†’ 204) =====
        mockMvc.perform(delete("/admin-users/{id}", userId)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNoContent());

        // ===== Step 5: Verify user is deleted (GET /admin-users/{id} â†’ 404) =====
        mockMvc.perform(get("/admin-users/{id}", userId)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNotFound());
    }

    // ========== Validation Errors ==========

    @Test
    @DisplayName("POST /admin-users with invalid username â†’ 400 Bad Request")
    void testCreateAdminUser_invalidUsername_returnsBadRequest() throws Exception {
        // Username too short (less than 3 chars)
        AdminUserCreateRequest shortUsername = new AdminUserCreateRequest(
                "ab", "securePass123", domainId, solutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortUsername))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isBadRequest());

        // Username with invalid characters (spaces)
        AdminUserCreateRequest invalidChars = new AdminUserCreateRequest(
                "user name", "securePass123", domainId, solutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidChars))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isBadRequest());

        // Username with special characters
        AdminUserCreateRequest specialChars = new AdminUserCreateRequest(
                "user@name!", "securePass123", domainId, solutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(specialChars))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /admin-users with short password â†’ 400 Bad Request")
    void testCreateAdminUser_shortPassword_returnsBadRequest() throws Exception {
        // Password too short (less than 8 chars)
        AdminUserCreateRequest shortPassword = new AdminUserCreateRequest(
                "validuser", "short", domainId, solutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortPassword))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /admin-users with missing domain ID â†’ 400 Bad Request")
    void testCreateAdminUser_missingDomainId_returnsBadRequest() throws Exception {
        // domainId is null
        String requestJson = """
                {
                    "username": "validuser",
                    "password": "securePass123",
                    "domainId": null,
                    "solutionId": "%s"
                }
                """.formatted(solutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /admin-users with missing solution ID â†’ 400 Bad Request")
    void testCreateAdminUser_missingSolutionId_returnsBadRequest() throws Exception {
        // solutionId is null
        String requestJson = """
                {
                    "username": "validuser",
                    "password": "securePass123",
                    "domainId": "%s",
                    "solutionId": null
                }
                """.formatted(domainId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /admin-users with non-existent domain â†’ 404 Not Found")
    void testCreateAdminUser_nonExistentDomain_returnsNotFound() throws Exception {
        String fakeDomainId = "nonexistent-domain-xyz";

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "validuser", "securePass123", fakeDomainId, solutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /admin-users with non-existent solution â†’ 404 Not Found")
    void testCreateAdminUser_nonExistentSolution_returnsNotFound() throws Exception {
        String fakeSolutionId = "nonexistent-solution-xyz";

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "validuser", "securePass123", domainId, fakeSolutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNotFound());
    }

    // ========== Duplicate Username Rejection ==========

    @Test
    @DisplayName("POST /admin-users with duplicate username in same scope â†’ 409 Conflict")
    void testCreateAdminUser_duplicateUsername_returnsConflict() throws Exception {
        String username = "duplicate-user-" + System.nanoTime();

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                username, "securePass123", domainId, solutionId);

        // Create first user â€” should succeed
        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated());

        // Create second user with same username in same scope â€” should fail with 409
        AdminUserCreateRequest duplicateRequest = new AdminUserCreateRequest(
                username, "differentPass456", domainId, solutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /admin-users with same username in different scope â†’ 201 Created")
    void testCreateAdminUser_sameUsernameInDifferentScope_succeeds() throws Exception {
        String username = "scoped-user-" + System.nanoTime();

        // Create user in first scope
        AdminUserCreateRequest request1 = new AdminUserCreateRequest(
                username, "securePass123", domainId, solutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated());

        // Create a second domain+solution hierarchy
        TestEntityHierarchy hierarchy2 = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        // Create user with same username in different scope â€” should succeed
        AdminUserCreateRequest request2 = new AdminUserCreateRequest(
                username, "securePass456", hierarchy2.getDomainId(), hierarchy2.getSolutionId());

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("GET /admin-users/{id} with non-existent ID â†’ 404 Not Found")
    void testGetAdminUser_nonExistentId_returnsNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/admin-users/{id}", nonExistentId)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /admin-users/{id} with non-existent ID â†’ 404 Not Found")
    void testDeleteAdminUser_nonExistentId_returnsNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(delete("/admin-users/{id}", nonExistentId)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /admin-users with empty scope returns empty list")
    void testListAdminUsers_emptyScope_returnsEmptyList() throws Exception {
        // Create a fresh hierarchy with no users
        TestEntityHierarchy emptyHierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        mockMvc.perform(get("/admin-users")
                        .param("domainId", emptyHierarchy.getDomainId())
                        .param("solutionId", emptyHierarchy.getSolutionId())
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.adminUsers.adminUser").isArray())
                .andExpect(jsonPath("$.adminUsers.adminUser").isEmpty());
    }

    @Test
    @DisplayName("POST /admin-users with valid boundary username (3 chars) â†’ 201 Created")
    void testCreateAdminUser_minimumLengthUsername_succeeds() throws Exception {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "abc", "securePass123", domainId, solutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("abc"));
    }

    @Test
    @DisplayName("POST /admin-users with valid boundary password (8 chars) â†’ 201 Created")
    void testCreateAdminUser_minimumLengthPassword_succeeds() throws Exception {
        String username = "minpass-" + System.nanoTime();

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                username, "12345678", domainId, solutionId);

        mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    @DisplayName("POST /admin-users does not return password hash in response")
    void testCreateAdminUser_responseDoesNotContainPasswordHash() throws Exception {
        String username = "nohash-" + System.nanoTime();

        AdminUserCreateRequest request = new AdminUserCreateRequest(
                username, "securePass123", domainId, solutionId);

        MvcResult result = mockMvc.perform(post("/admin-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("passwordHash");
        assertThat(responseBody).doesNotContain("password_hash");
        assertThat(responseBody).doesNotContain("$2a$"); // bcrypt prefix
    }
}