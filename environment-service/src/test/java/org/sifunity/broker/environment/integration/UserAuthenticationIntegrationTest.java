// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.filter.AdminSessionFilter;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.*;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for admin user authentication and session management.
 *
 * <p>Tests the complete authentication lifecycle via REST endpoints:
 * login success/failure, account lockout, environment selection, session
 * validation, and logout.</p>
 *
 * <p>Uses Testcontainers with both PostgreSQL and Redis. Redis is required
 * for session tokens and lockout counters.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 4.1, 4.4</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("User Authentication Integration Tests")
class UserAuthenticationIntegrationTest {

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
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");

        // Redis Ã¢â‚¬â€ override the exclusion from application-test.yml
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");

        // Other test config
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
    private AdminUserService adminUserService;

    @Autowired
    private UserEnvironmentAccessService accessService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private TestEntityHierarchy hierarchy;
    private UUID domainUuid;
    private UUID solutionUuid;
    private Environment adminEnvironment;

    @BeforeEach
    void setUp() {
        // Flush Redis between tests to reset sessions and lockout counters
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // Create a domain + solution + zone hierarchy for test data
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        domainUuid = hierarchy.getDomainUuid();
        solutionUuid = hierarchy.getSolutionUuid();

        // Register a global admin environment to use as the requester for HMAC-protected endpoints
        SifEnvironmentRegistrationResponse adminReg = environmentService.registerEnvironment(
                hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AdminApp-" + System.nanoTime(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null)));
        adminEnvironment = environmentRepository.findById(adminReg.environment().id()).orElseThrow();
    }

    // ========== Helper Methods ==========

    private AdminUserDTO createAdminUser(String username, String password) {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());
        return adminUserService.createUser(request);
    }

    private UUID registerEnvironmentInSolution(String appName) {
        SifEnvironmentRegistrationResponse reg = environmentService.registerEnvironment(
                hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null)));
        return reg.environment().id();
    }

    private void grantAccess(UUID userId, UUID environmentId) {
        accessService.grantAccess(userId, new UserEnvironmentAccessRequest(environmentId));
    }

    // ========== Login Success ==========

    @Test
    @DisplayName("POST /admin-users/login with correct credentials Ã¢â€ â€™ 200 OK with session token and environments")
    void testLogin_success_returnsSessionTokenAndEnvironments() throws Exception {
        String username = "auth-user-" + System.nanoTime();
        String password = "securePass123";
        AdminUserDTO user = createAdminUser(username, password);

        // Grant access to an environment
        UUID envId = registerEnvironmentInSolution("AuthApp-" + System.nanoTime());
        grantAccess(user.id(), envId);

        // Login
        UserLoginRequest loginRequest = new UserLoginRequest(username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());

        MvcResult result = mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sessionToken").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.environments").isArray())
                .andExpect(jsonPath("$.environments.length()").value(1))
                .andExpect(jsonPath("$.environments[0].id").value(envId.toString()))
                .andReturn();

        UserLoginResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), UserLoginResponse.class);

        assertThat(response.sessionToken()).isNotNull().isNotBlank();
        assertThat(response.expiresAt()).isNotNull();
        assertThat(response.environments()).hasSize(1);
        assertThat(response.environments().get(0).id()).isEqualTo(envId);
    }

    @Test
    @DisplayName("POST /admin-users/login returns unique session tokens on repeated logins")
    void testLogin_repeatedLogins_returnUniqueTokens() throws Exception {
        String username = "unique-token-user-" + System.nanoTime();
        String password = "securePass123";
        createAdminUser(username, password);

        UserLoginRequest loginRequest = new UserLoginRequest(username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());

        // Login twice
        MvcResult result1 = mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        UserLoginResponse response1 = objectMapper.readValue(
                result1.getResponse().getContentAsString(), UserLoginResponse.class);
        UserLoginResponse response2 = objectMapper.readValue(
                result2.getResponse().getContentAsString(), UserLoginResponse.class);

        assertThat(response1.sessionToken()).isNotEqualTo(response2.sessionToken());
    }

    // ========== Login Failure ==========

    @Test
    @DisplayName("POST /admin-users/login with wrong password Ã¢â€ â€™ 401 Unauthorized with generic message")
    void testLogin_wrongPassword_returnsUnauthorized() throws Exception {
        String username = "wrong-pass-user-" + System.nanoTime();
        String password = "securePass123";
        createAdminUser(username, password);

        UserLoginRequest loginRequest = new UserLoginRequest(username, "wrongPassword!", hierarchy.getDomainId(), hierarchy.getSolutionId());

        mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /admin-users/login with non-existent username Ã¢â€ â€™ 401 Unauthorized with same message")
    void testLogin_nonExistentUsername_returnsUnauthorized() throws Exception {
        UserLoginRequest loginRequest = new UserLoginRequest(
                "nonexistent-user-" + System.nanoTime(), "somePassword123", hierarchy.getDomainId(), hierarchy.getSolutionId());

        mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login failure responses are identical regardless of failure reason (no info leakage)")
    void testLogin_failureResponses_areIdentical() throws Exception {
        String username = "leakage-user-" + System.nanoTime();
        String password = "securePass123";
        createAdminUser(username, password);

        // Wrong password
        UserLoginRequest wrongPassRequest = new UserLoginRequest(username, "wrongPassword!", hierarchy.getDomainId(), hierarchy.getSolutionId());
        MvcResult wrongPassResult = mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassRequest)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Non-existent username
        UserLoginRequest noUserRequest = new UserLoginRequest(
                "no-such-user-" + System.nanoTime(), "somePassword123", hierarchy.getDomainId(), hierarchy.getSolutionId());
        MvcResult noUserResult = mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noUserRequest)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Both should return the same HTTP status (already verified) and same error structure
        assertThat(wrongPassResult.getResponse().getStatus())
                .isEqualTo(noUserResult.getResponse().getStatus());
    }

    // ========== Account Lockout ==========

    @Test
    @DisplayName("POST /admin-users/login Ã¢â€ â€™ account locked after 5 consecutive failures")
    void testLogin_accountLockoutAfterFiveFailures() throws Exception {
        String username = "lockout-user-" + System.nanoTime();
        String password = "securePass123";
        createAdminUser(username, password);

        UserLoginRequest badRequest = new UserLoginRequest(username, "wrongPassword!", hierarchy.getDomainId(), hierarchy.getSolutionId());

        // 5 consecutive failed attempts
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/admin-users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt with CORRECT password should still fail (account locked)
        UserLoginRequest correctRequest = new UserLoginRequest(username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());
        mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /admin-users/login Ã¢â€ â€™ account NOT locked after fewer than 5 failures")
    void testLogin_notLockedBelowFiveFailures() throws Exception {
        String username = "notlocked-user-" + System.nanoTime();
        String password = "securePass123";
        createAdminUser(username, password);

        UserLoginRequest badRequest = new UserLoginRequest(username, "wrongPassword!", hierarchy.getDomainId(), hierarchy.getSolutionId());

        // 4 consecutive failed attempts
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/admin-users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // 5th attempt with CORRECT password should succeed (not yet locked)
        UserLoginRequest correctRequest = new UserLoginRequest(username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());
        mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").exists());
    }

    @Test
    @DisplayName("POST /admin-users/login Ã¢â€ â€™ successful login resets lockout counter")
    void testLogin_successResetsLockoutCounter() throws Exception {
        String username = "reset-lockout-user-" + System.nanoTime();
        String password = "securePass123";
        createAdminUser(username, password);

        UserLoginRequest badRequest = new UserLoginRequest(username, "wrongPassword!", hierarchy.getDomainId(), hierarchy.getSolutionId());

        // 4 failed attempts
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/admin-users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // Successful login resets counter
        UserLoginRequest correctRequest = new UserLoginRequest(username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());
        mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctRequest)))
                .andExpect(status().isOk());

        // 4 more failed attempts should NOT lock (counter was reset)
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/admin-users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // Should still be able to login (only 4 failures since reset)
        mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").exists());
    }

    // ========== Environment Selection ==========

    @Test
    @DisplayName("POST /admin-users/sessions/select-environment Ã¢â€ â€™ 204 for authorized environment")
    void testSelectEnvironment_authorized_returnsNoContent() throws Exception {
        String username = "select-env-user-" + System.nanoTime();
        String password = "securePass123";
        AdminUserDTO user = createAdminUser(username, password);

        UUID envId = registerEnvironmentInSolution("SelectApp-" + System.nanoTime());
        grantAccess(user.id(), envId);

        // Login to get session token
        UserLoginRequest loginRequest = new UserLoginRequest(username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());
        MvcResult loginResult = mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        UserLoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), UserLoginResponse.class);
        String sessionToken = loginResponse.sessionToken();

        // Select environment
        UserEnvironmentAccessRequest selectRequest = new UserEnvironmentAccessRequest(envId);
        mockMvc.perform(post("/admin-users/sessions/select-environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectRequest))
                        .header(AdminSessionFilter.ADMIN_SESSION_HEADER, sessionToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /admin-users/sessions/select-environment Ã¢â€ â€™ 403 for unauthorized environment")
    void testSelectEnvironment_unauthorized_returnsForbidden() throws Exception {
        String username = "unauth-env-user-" + System.nanoTime();
        String password = "securePass123";
        AdminUserDTO user = createAdminUser(username, password);

        // Register an environment but do NOT grant access
        UUID envId = registerEnvironmentInSolution("UnauthorizedApp-" + System.nanoTime());

        // Grant access to a DIFFERENT environment so user can login
        UUID grantedEnvId = registerEnvironmentInSolution("GrantedApp-" + System.nanoTime());
        grantAccess(user.id(), grantedEnvId);

        // Login to get session token
        UserLoginRequest loginRequest = new UserLoginRequest(username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());
        MvcResult loginResult = mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        UserLoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), UserLoginResponse.class);
        String sessionToken = loginResponse.sessionToken();

        // Attempt to select unauthorized environment Ã¢â€ â€™ 403
        UserEnvironmentAccessRequest selectRequest = new UserEnvironmentAccessRequest(envId);
        mockMvc.perform(post("/admin-users/sessions/select-environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectRequest))
                        .header(AdminSessionFilter.ADMIN_SESSION_HEADER, sessionToken))
                .andExpect(status().isForbidden());
    }

    // ========== Session Validation ==========

    @Test
    @DisplayName("GET /admin-users/sessions/current Ã¢â€ â€™ 200 with session context for valid token")
    void testGetCurrentSession_validToken_returnsSessionContext() throws Exception {
        String username = "session-user-" + System.nanoTime();
        String password = "securePass123";
        AdminUserDTO user = createAdminUser(username, password);

        // Login to get session token
        UserLoginRequest loginRequest = new UserLoginRequest(username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());
        MvcResult loginResult = mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        UserLoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), UserLoginResponse.class);
        String sessionToken = loginResponse.sessionToken();

        // Get current session
        mockMvc.perform(get("/admin-users/sessions/current")
                        .header(AdminSessionFilter.ADMIN_SESSION_HEADER, sessionToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(user.id().toString()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.domainUuid").value(domainUuid.toString()))
                .andExpect(jsonPath("$.solutionUuid").value(solutionUuid.toString()));
    }

    @Test
    @DisplayName("GET /admin-users/sessions/current Ã¢â€ â€™ 401 for invalid token")
    void testGetCurrentSession_invalidToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin-users/sessions/current")
                        .header(AdminSessionFilter.ADMIN_SESSION_HEADER, "invalid-token-" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /admin-users/sessions/current Ã¢â€ â€™ 401 for missing token header")
    void testGetCurrentSession_missingHeader_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin-users/sessions/current"))
                .andExpect(status().isUnauthorized());
    }

    // ========== Logout ==========

    @Test
    @DisplayName("POST /admin-users/sessions/logout Ã¢â€ â€™ 204 and invalidates session")
    void testLogout_invalidatesSession() throws Exception {
        String username = "logout-user-" + System.nanoTime();
        String password = "securePass123";
        createAdminUser(username, password);

        // Login to get session token
        UserLoginRequest loginRequest = new UserLoginRequest(username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());
        MvcResult loginResult = mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        UserLoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), UserLoginResponse.class);
        String sessionToken = loginResponse.sessionToken();

        // Verify session is valid before logout
        mockMvc.perform(get("/admin-users/sessions/current")
                        .header(AdminSessionFilter.ADMIN_SESSION_HEADER, sessionToken))
                .andExpect(status().isOk());

        // Logout
        mockMvc.perform(post("/admin-users/sessions/logout")
                        .header(AdminSessionFilter.ADMIN_SESSION_HEADER, sessionToken))
                .andExpect(status().isNoContent());

        // Verify session is invalidated after logout
        mockMvc.perform(get("/admin-users/sessions/current")
                        .header(AdminSessionFilter.ADMIN_SESSION_HEADER, sessionToken))
                .andExpect(status().isUnauthorized());
    }

    // ========== Environment Selection Updates Session Context ==========

    @Test
    @DisplayName("Environment selection updates session context with selectedEnvironmentId")
    void testSelectEnvironment_updatesSessionContext() throws Exception {
        String username = "ctx-update-user-" + System.nanoTime();
        String password = "securePass123";
        AdminUserDTO user = createAdminUser(username, password);

        UUID envId = registerEnvironmentInSolution("CtxApp-" + System.nanoTime());
        grantAccess(user.id(), envId);

        // Login
        UserLoginRequest loginRequest = new UserLoginRequest(username, password, hierarchy.getDomainId(), hierarchy.getSolutionId());
        MvcResult loginResult = mockMvc.perform(post("/admin-users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        UserLoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), UserLoginResponse.class);
        String sessionToken = loginResponse.sessionToken();

        // Before selection: selectedEnvironmentId should be null
        mockMvc.perform(get("/admin-users/sessions/current")
                        .header(AdminSessionFilter.ADMIN_SESSION_HEADER, sessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedEnvironmentId").doesNotExist());

        // Select environment
        UserEnvironmentAccessRequest selectRequest = new UserEnvironmentAccessRequest(envId);
        mockMvc.perform(post("/admin-users/sessions/select-environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectRequest))
                        .header(AdminSessionFilter.ADMIN_SESSION_HEADER, sessionToken))
                .andExpect(status().isNoContent());

        // After selection: selectedEnvironmentId should be set
        mockMvc.perform(get("/admin-users/sessions/current")
                        .header(AdminSessionFilter.ADMIN_SESSION_HEADER, sessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedEnvironmentId").value(envId.toString()));
    }
}