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
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.*;
import org.sifunity.broker.environment.test.TestEntityHierarchy;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for GET /{domainId}/{solutionId}/environments/{envId}/admin-users endpoint.
 *
 * <p>Tests listing admin users with access to a specific environment, including
 * happy path (populated and empty results), 404 for non-existent environment,
 * and 404 for domain/solution mismatch.</p>
 *
 * <p>Uses Testcontainers with PostgreSQL (via JDBC URL in application-test.yml)
 * and MockMvc with security disabled.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.3, 1.5, 1.6</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
@DisplayName("Environment Users Endpoint Integration Tests")
class EnvironmentUsersIntegrationTest {

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
    private AdminUserService adminUserService;

    @Autowired
    private UserEnvironmentAccessService userEnvironmentAccessService;

    private TestEntityHierarchy hierarchy;
    private UUID domainUuid;
    private UUID solutionUuid;
    private String domainId;
    private String solutionId;
    private Environment adminEnvironment;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        domainUuid = hierarchy.getDomainUuid();
        solutionUuid = hierarchy.getSolutionUuid();
        domainId = hierarchy.getDomainId();
        solutionId = hierarchy.getSolutionId();

        // Register a global admin environment to use as the requester
        SifEnvironmentRegistrationResponse adminReg = environmentService.registerEnvironment(domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AdminApp-" + System.nanoTime(), EnvironmentType.BROKERED, null, solutionId, null)));
        adminEnvironment = environmentRepository.findById(adminReg.environment().id()).orElseThrow();
    }

    // ========== Helper Methods ==========

    private UUID createAdminUser(String username) {
        AdminUserDTO user = adminUserService.createUser(
                new AdminUserCreateRequest(username, "securePass123", domainId, solutionId));
        return user.id();
    }

    private UUID registerEnvironmentInSolution(String appName) {
        SifEnvironmentRegistrationResponse reg = environmentService.registerEnvironment(domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, null, solutionId, null)));
        return reg.environment().id();
    }

    private String environmentUsersPath(UUID envId) {
        return "/" + domainId + "/" + solutionId + "/environments/" + envId + "/admin-users";
    }

    private void grantAccess(UUID userId, UUID environmentId) {
        userEnvironmentAccessService.grantAccess(userId, new UserEnvironmentAccessRequest(environmentId));
    }

    // ========== Happy Path: Returns users with access ==========

    @Test
    @DisplayName("Returns 200 with populated array when users have access")
    void testListEnvironmentUsers_returnsUsersWithAccess() throws Exception {
        UUID environmentId = registerEnvironmentInSolution("UsersApp-" + System.nanoTime());
        String username1 = "env-user1-" + System.nanoTime();
        String username2 = "env-user2-" + System.nanoTime();
        UUID userId1 = createAdminUser(username1);
        UUID userId2 = createAdminUser(username2);

        grantAccess(userId1, environmentId);
        grantAccess(userId2, environmentId);

        mockMvc.perform(get(environmentUsersPath(environmentId))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].username").exists())
                .andExpect(jsonPath("$[0].grantedAt").exists())
                .andExpect(jsonPath("$[1].id").exists())
                .andExpect(jsonPath("$[1].username").exists())
                .andExpect(jsonPath("$[1].grantedAt").exists());
    }

    // ========== Empty Result ==========

    @Test
    @DisplayName("Returns 200 with empty array when no users have access")
    void testListEnvironmentUsers_noUsers_returnsEmptyArray() throws Exception {
        UUID environmentId = registerEnvironmentInSolution("EmptyApp-" + System.nanoTime());

        mockMvc.perform(get(environmentUsersPath(environmentId))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ========== 404: Non-existent environment ==========

    @Test
    @DisplayName("Returns 404 for non-existent environment")
    void testListEnvironmentUsers_nonExistentEnvironment_returns404() throws Exception {
        UUID fakeEnvId = UUID.randomUUID();

        mockMvc.perform(get(environmentUsersPath(fakeEnvId))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNotFound());
    }

    // ========== 404: Domain/solution mismatch ==========

    @Test
    @DisplayName("Returns 404 when domain/solution does not match environment")
    void testListEnvironmentUsers_domainSolutionMismatch_returns404() throws Exception {
        // Create a second hierarchy with different domain+solution
        TestEntityHierarchy otherHierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        // Register environment in the OTHER solution
        SifEnvironmentRegistrationResponse otherEnvReg = environmentService.registerEnvironment(otherHierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("OtherApp-" + System.nanoTime(), EnvironmentType.BROKERED, null, otherHierarchy.getSolutionId(), null)));
        UUID otherEnvironmentId = otherEnvReg.environment().id();

        // Try to access the environment using the FIRST hierarchy's domain/solution path
        // This should return 404 because the environment belongs to a different domain/solution
        mockMvc.perform(get(environmentUsersPath(otherEnvironmentId))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isNotFound());
    }
}
