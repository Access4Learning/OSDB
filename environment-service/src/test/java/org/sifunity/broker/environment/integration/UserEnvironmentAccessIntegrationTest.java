// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.UserEnvironmentAccessRepository;
import org.sifunity.broker.environment.service.*;
import org.sifunity.broker.environment.test.TestEntityHierarchy;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for user-environment access lifecycle via REST endpoints.
 *
 * <p>Tests the full grant/revoke/list lifecycle, domain+solution mismatch rejection,
 * duplicate association rejection, and cascade delete behavior through the
 * UserEnvironmentAccessController.</p>
 *
 * <p>Uses Testcontainers with PostgreSQL (via JDBC URL in application-test.yml)
 * and MockMvc with security disabled. The requester attribute is set manually
 * to simulate an authenticated global admin environment.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
@DisplayName("User-Environment Access Integration Tests")
class UserEnvironmentAccessIntegrationTest {

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
    private UserEnvironmentAccessRepository accessRepository;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private EntityManager entityManager;

    private TestEntityHierarchy hierarchy;
    private UUID domainUuid;
    private UUID solutionUuid;
    private Environment adminEnvironment;
    private String domainId;
    private String solutionId;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        domainUuid = hierarchy.getDomainUuid();
        solutionUuid = hierarchy.getSolutionUuid();
        domainId = hierarchy.getDomainId();
        solutionId = hierarchy.getSolutionId();

        // Register a global admin environment to use as the requester
        SifEnvironmentRegistrationResponse adminReg = environmentService.registerEnvironment(
                hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AdminApp-" + System.nanoTime(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null)));
        adminEnvironment = environmentRepository.findById(adminReg.environment().id()).orElseThrow();
    }

    // ========== Helper Methods ==========

    private UUID createAdminUser(String username) {
        AdminUserDTO user = adminUserService.createUser(
                new AdminUserCreateRequest(username, "securePass123", domainId, solutionId));
        return user.id();
    }

    private UUID registerEnvironmentInSolution(String appName) {
        SifEnvironmentRegistrationResponse reg = environmentService.registerEnvironment(
                hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null)));
        return reg.environment().id();
    }

    private String accessBasePath(UUID userId) {
        return "/" + domainId + "/" + solutionId + "/admin-users/" + userId + "/environments";
    }

    // ========== Happy Path: Grant/Revoke/List Lifecycle ==========

    @Test
    @DisplayName("Grant → List → Revoke lifecycle for user-environment access")
    void testGrantRevokeListLifecycle() throws Exception {
        String username = "lifecycle-user-" + System.nanoTime();
        UUID userId = createAdminUser(username);
        UUID environmentId = registerEnvironmentInSolution("LifecycleApp-" + System.nanoTime());

        // ===== Step 1: Grant access (POST → 201) =====
        UserEnvironmentAccessRequest grantRequest = new UserEnvironmentAccessRequest(environmentId);

        MvcResult grantResult = mockMvc.perform(post(accessBasePath(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantRequest))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.environmentId").value(environmentId.toString()))
                .andExpect(jsonPath("$.applicationName").exists())
                .andExpect(jsonPath("$.grantedAt").exists())
                .andReturn();

        UserEnvironmentAccessDTO grantedAccess = objectMapper.readValue(
                grantResult.getResponse().getContentAsString(), UserEnvironmentAccessDTO.class);

        assertThat(grantedAccess.id()).isNotNull();
        assertThat(grantedAccess.userId()).isEqualTo(userId);
        assertThat(grantedAccess.environmentId()).isEqualTo(environmentId);
        assertThat(grantedAccess.grantedAt()).isNotNull();

        // ===== Step 2: List access (GET → 200 with 1 entry) =====
        mockMvc.perform(get(accessBasePath(userId))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].environmentId").value(environmentId.toString()));

        // ===== Step 3: Revoke access (DELETE → 204) =====
        mockMvc.perform(delete(accessBasePath(userId) + "/" + environmentId)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNoContent());

        // ===== Step 4: Verify list is now empty (GET → 200 with 0 entries) =====
        mockMvc.perform(get(accessBasePath(userId))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Grant access to multiple environments, list returns all")
    void testGrantMultipleEnvironments_listReturnsAll() throws Exception {
        String username = "multi-env-user-" + System.nanoTime();
        UUID userId = createAdminUser(username);
        UUID envId1 = registerEnvironmentInSolution("MultiApp1-" + System.nanoTime());
        UUID envId2 = registerEnvironmentInSolution("MultiApp2-" + System.nanoTime());

        // Grant access to both environments
        mockMvc.perform(post(accessBasePath(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserEnvironmentAccessRequest(envId1)))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated());

        mockMvc.perform(post(accessBasePath(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserEnvironmentAccessRequest(envId2)))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated());

        // List should return both
        mockMvc.perform(get(accessBasePath(userId))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ========== Domain+Solution Mismatch Rejection ==========

    @Test
    @DisplayName("Grant access with environment from different solution → 400 Bad Request")
    void testGrantAccess_domainSolutionMismatch_returnsBadRequest() throws Exception {
        String username = "mismatch-user-" + System.nanoTime();
        UUID userId = createAdminUser(username);

        // Create a second hierarchy with different domain+solution
        TestEntityHierarchy otherHierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        // Register environment in the OTHER solution
        SifEnvironmentRegistrationResponse otherEnvReg = environmentService.registerEnvironment(
                otherHierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("OtherApp-" + System.nanoTime(), EnvironmentType.BROKERED, null, otherHierarchy.getSolutionId(), null)));
        UUID otherEnvironmentId = otherEnvReg.environment().id();

        // Attempt to grant access — should fail with 400 (domain+solution mismatch)
        UserEnvironmentAccessRequest grantRequest = new UserEnvironmentAccessRequest(otherEnvironmentId);

        mockMvc.perform(post(accessBasePath(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantRequest))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isBadRequest());
    }

    // ========== Duplicate Association Rejection ==========

    @Test
    @DisplayName("Grant access twice to same environment → 409 Conflict")
    void testGrantAccess_duplicate_returnsConflict() throws Exception {
        String username = "dup-access-user-" + System.nanoTime();
        UUID userId = createAdminUser(username);
        UUID environmentId = registerEnvironmentInSolution("DupApp-" + System.nanoTime());

        UserEnvironmentAccessRequest grantRequest = new UserEnvironmentAccessRequest(environmentId);

        // First grant — should succeed
        mockMvc.perform(post(accessBasePath(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantRequest))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated());

        // Second grant — should fail with 409
        mockMvc.perform(post(accessBasePath(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantRequest))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isConflict());
    }

    // ========== Cascade Delete on User Deletion ==========

    @Test
    @DisplayName("Deleting admin user cascades deletion of access associations")
    void testCascadeDelete_onUserDeletion() throws Exception {
        String username = "cascade-user-" + System.nanoTime();
        UUID userId = createAdminUser(username);
        UUID envId1 = registerEnvironmentInSolution("CascadeApp1-" + System.nanoTime());
        UUID envId2 = registerEnvironmentInSolution("CascadeApp2-" + System.nanoTime());

        // Grant access to two environments
        mockMvc.perform(post(accessBasePath(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserEnvironmentAccessRequest(envId1)))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated());

        mockMvc.perform(post(accessBasePath(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserEnvironmentAccessRequest(envId2)))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated());

        // Verify associations exist
        assertThat(accessRepository.findAllByUser_Id(userId)).hasSize(2);

        // Delete the admin user via REST
        mockMvc.perform(delete("/admin-users/{id}", userId)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNoContent());

        // Flush pending changes and clear persistence context to see DB-level cascade effects
        entityManager.flush();
        entityManager.clear();

        // Verify cascade: no access records remain for this user
        assertThat(accessRepository.findAllByUser_Id(userId)).isEmpty();
    }

    // ========== Cascade Delete on Environment Deletion ==========

    @Test
    @DisplayName("Deleting environment cascades deletion of access associations")
    void testCascadeDelete_onEnvironmentDeletion() throws Exception {
        String username1 = "envdel-user1-" + System.nanoTime();
        String username2 = "envdel-user2-" + System.nanoTime();
        UUID userId1 = createAdminUser(username1);
        UUID userId2 = createAdminUser(username2);
        UUID environmentId = registerEnvironmentInSolution("EnvDelApp-" + System.nanoTime());

        // Grant both users access to the same environment
        mockMvc.perform(post(accessBasePath(userId1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserEnvironmentAccessRequest(environmentId)))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated());

        mockMvc.perform(post(accessBasePath(userId2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserEnvironmentAccessRequest(environmentId)))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isCreated());

        // Verify associations exist
        assertThat(accessRepository.existsByUser_IdAndEnvironment_Id(userId1, environmentId)).isTrue();
        assertThat(accessRepository.existsByUser_IdAndEnvironment_Id(userId2, environmentId)).isTrue();

        // Delete the environment
        environmentService.deleteEnvironment(environmentId);

        // Flush pending changes and clear persistence context to see DB-level cascade effects
        entityManager.flush();
        entityManager.clear();

        // Verify cascade: no access records remain for this environment
        assertThat(accessRepository.existsByUser_IdAndEnvironment_Id(userId1, environmentId)).isFalse();
        assertThat(accessRepository.existsByUser_IdAndEnvironment_Id(userId2, environmentId)).isFalse();
    }

    // ========== Error Cases ==========

    @Test
    @DisplayName("Grant access with non-existent user → 404 Not Found")
    void testGrantAccess_nonExistentUser_returnsNotFound() throws Exception {
        UUID fakeUserId = UUID.randomUUID();
        UUID environmentId = registerEnvironmentInSolution("NotFoundApp-" + System.nanoTime());

        UserEnvironmentAccessRequest grantRequest = new UserEnvironmentAccessRequest(environmentId);

        mockMvc.perform(post("/" + domainId + "/" + solutionId + "/admin-users/" + fakeUserId + "/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantRequest))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Grant access with non-existent environment → 404 Not Found")
    void testGrantAccess_nonExistentEnvironment_returnsNotFound() throws Exception {
        String username = "noenv-user-" + System.nanoTime();
        UUID userId = createAdminUser(username);
        UUID fakeEnvironmentId = UUID.randomUUID();

        UserEnvironmentAccessRequest grantRequest = new UserEnvironmentAccessRequest(fakeEnvironmentId);

        mockMvc.perform(post(accessBasePath(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantRequest))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Revoke access for non-existent association → 404 Not Found")
    void testRevokeAccess_nonExistentAssociation_returnsNotFound() throws Exception {
        String username = "revoke-user-" + System.nanoTime();
        UUID userId = createAdminUser(username);
        UUID fakeEnvironmentId = UUID.randomUUID();

        mockMvc.perform(delete(accessBasePath(userId) + "/" + fakeEnvironmentId)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("List access for user with no associations returns empty list")
    void testListAccess_noAssociations_returnsEmptyList() throws Exception {
        String username = "empty-user-" + System.nanoTime();
        UUID userId = createAdminUser(username);

        mockMvc.perform(get(accessBasePath(userId))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
