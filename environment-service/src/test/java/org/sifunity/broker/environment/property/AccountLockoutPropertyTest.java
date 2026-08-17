// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.domain.AdminUser;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.UserLoginRequest;
import org.sifunity.broker.environment.dto.UserLoginResponse;
import org.sifunity.broker.environment.repository.AdminUserRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.UserAuthenticationService;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for account lockout after consecutive failures.
 *
 * <p><strong>Feature: admin-user-management, Property 9: Account lockout after
 * consecutive failures</strong></p>
 *
 * <p>For any admin user, after exactly 5 consecutive failed login attempts, the account
 * should be locked (subsequent attempts rejected with 401). After fewer than 5 consecutive
 * failures, the account should remain unlocked. A successful login should reset the failure
 * counter to zero.</p>
 *
 * <p><strong>Validates: Requirements 3.6</strong></p>
 */
class AccountLockoutPropertyTest {

    private static final int REDIS_PORT = 6379;
    private static final String KNOWN_PASSWORD = "TestPassword123!";

    static PostgreSQLContainer<?> postgres;
    @SuppressWarnings("resource")
    static GenericContainer<?> redis;
    static ConfigurableApplicationContext context;

    private UserAuthenticationService authService;
    private AdminUserRepository adminUserRepository;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;
    private StringRedisTemplate redisTemplate;

    private UUID testDomainId;
    private UUID testSolutionId;
    private String testDomainStringId;
    private String testSolutionStringId;

    @BeforeContainer
    static void startContainers() {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
        postgres.start();

        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);
        redis.start();

        SpringApplication app = new SpringApplication(EnvironmentServiceApplication.class);
        // Use a custom profile that does NOT load TestRedisConfiguration (which mocks Redis)
        app.setAdditionalProfiles("lockout-test");
        context = app.run(
            "--server.port=0",
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword(),
            "--spring.datasource.driver-class-name=org.postgresql.Driver",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
            "--spring.flyway.enabled=true",
            "--spring.flyway.locations=classpath:db/migration",
            "--spring.flyway.baseline-on-migrate=true",
            "--spring.data.redis.host=" + redis.getHost(),
            "--spring.data.redis.port=" + redis.getMappedPort(REDIS_PORT),
            "--spring.main.allow-bean-definition-overriding=true",
            "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "--spring.security.user.name=test",
            "--spring.security.user.password=test",
            "--authentication.timestamp-tolerance-minutes=5",
            "--secrets.backend.type=plaintext",
            "--sif.broker.bootstrap.enabled=true",
            "--sif.broker.bootstrap.admin.sessionToken=test-admin-token",
            "--sif.broker.bootstrap.admin.sharedSecret=dGVzdC1hZG1pbi1zZWNyZXQ=",
            "--sif.broker.infrastructure.gateway-base-url=http://localhost:8080",
            "--sif.broker.infrastructure.environment=http://localhost:8081",
            "--sif.broker.infrastructure.provision-requests=http://localhost:8081",
            "--sif.broker.infrastructure.requests-connector=http://localhost:8083",
            "--sif.broker.infrastructure.events-connector=http://localhost:8084",
            "--sif.broker.infrastructure.queues=http://localhost:8085",
            "--sif.broker.infrastructure.subscriptions=http://localhost:8086",
            "--sif.broker.infrastructure.services-connector=http://localhost:8087",
            "--sif.broker.infrastructure.responses-connector=http://localhost:8088",
            "--observability.enabled=false",
            "--observability.metrics.enabled=false",
            "--observability.tracing.enabled=false",
            "--management.endpoints.enabled-by-default=false"
        );
    }

    @AfterContainer
    static void stopContainers() {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @BeforeTry
    void setUp() {
        authService = context.getBean(UserAuthenticationService.class);
        adminUserRepository = context.getBean(AdminUserRepository.class);
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);
        redisTemplate = context.getBean(StringRedisTemplate.class);

        // Flush Redis between tries to reset lockout counters
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        // Also clean up admin users to avoid uniqueness conflicts
        adminUserRepository.deleteAll();

        // Create fresh domain and solution for each try
        Domain domain = new Domain();
        domain.setDomainId("lockout-test-" + UUID.randomUUID().toString().substring(0, 8));
        domain.setDescription("Test domain for lockout property test");
        domain = domainRepository.save(domain);
        testDomainId = domain.getId();
        testDomainStringId = domain.getDomainId();

        Solution solution = new Solution();
        solution.setDomainUuid(testDomainId);
        solution.setSolutionId("sol-" + UUID.randomUUID().toString().substring(0, 8));
        solution.setDescription("Test solution for lockout property test");
        solution = solutionRepository.save(solution);
        testSolutionId = solution.getId();
        testSolutionStringId = solution.getSolutionId();
    }

    /**
     * After fewer than 5 consecutive failed attempts, the account remains unlocked
     * and a correct password login succeeds.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: admin-user-management, Property 9: Account lockout after consecutive failures")
    void accountNotLockedBelowFiveFailures(
            @ForAll("failureCountBelowThreshold") int failureCount) {

        // Create a user with known credentials
        String username = "user-" + UUID.randomUUID().toString().substring(0, 8);
        AdminUser user = createTestUser(username, KNOWN_PASSWORD);

        // Attempt N failed logins (N < 5)
        for (int i = 0; i < failureCount; i++) {
            UserLoginRequest badRequest = new UserLoginRequest(
                username, "wrong-password-" + i, testDomainStringId, testSolutionStringId);
            try {
                authService.login(badRequest);
            } catch (SifBrokerException e) {
                // Expected: invalid credentials
            }
        }

        // Verify account is NOT locked: next attempt with correct password succeeds
        UserLoginRequest goodRequest = new UserLoginRequest(
            username, KNOWN_PASSWORD, testDomainStringId, testSolutionStringId);
        UserLoginResponse response = authService.login(goodRequest);

        assertThat(response)
            .as("Login with correct password should succeed after %d failures (< 5)", failureCount)
            .isNotNull();
        assertThat(response.sessionToken())
            .as("Session token should be returned on successful login")
            .isNotNull()
            .isNotBlank();
    }

    /**
     * After exactly 5 consecutive failed attempts, the account is locked and
     * subsequent login with correct password fails.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: admin-user-management, Property 9: Account lockout after consecutive failures")
    void accountLockedAfterExactlyFiveFailures(
            @ForAll("validUsernames") String username) {

        // Create a user with known credentials
        createTestUser(username, KNOWN_PASSWORD);

        // Attempt exactly 5 failed logins
        for (int i = 0; i < 5; i++) {
            UserLoginRequest badRequest = new UserLoginRequest(
                username, "wrong-password-" + i, testDomainStringId, testSolutionStringId);
            try {
                authService.login(badRequest);
            } catch (SifBrokerException e) {
                // Expected: invalid credentials
            }
        }

        // Verify account IS locked: correct password now fails
        UserLoginRequest goodRequest = new UserLoginRequest(
            username, KNOWN_PASSWORD, testDomainStringId, testSolutionStringId);

        assertThatThrownBy(() -> authService.login(goodRequest))
            .as("Login should be rejected after 5 consecutive failures even with correct password")
            .isInstanceOf(SifBrokerException.class);
    }

    /**
     * A successful login resets the failure counter, so subsequent failures
     * start counting from zero again.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: admin-user-management, Property 9: Account lockout after consecutive failures")
    void successfulLoginResetsFailureCounter(
            @ForAll("failureCountBelowThreshold") int initialFailures) {

        // Create a user with known credentials
        String username = "reset-" + UUID.randomUUID().toString().substring(0, 8);
        createTestUser(username, KNOWN_PASSWORD);

        // Accumulate some failures (< 5)
        for (int i = 0; i < initialFailures; i++) {
            UserLoginRequest badRequest = new UserLoginRequest(
                username, "wrong-password-" + i, testDomainStringId, testSolutionStringId);
            try {
                authService.login(badRequest);
            } catch (SifBrokerException e) {
                // Expected
            }
        }

        // Successful login resets counter
        UserLoginRequest goodRequest = new UserLoginRequest(
            username, KNOWN_PASSWORD, testDomainStringId, testSolutionStringId);
        UserLoginResponse response = authService.login(goodRequest);
        assertThat(response).isNotNull();

        // Now attempt 4 more failures — should NOT lock (counter was reset)
        for (int i = 0; i < 4; i++) {
            UserLoginRequest badRequest = new UserLoginRequest(
                username, "wrong-again-" + i, testDomainStringId, testSolutionStringId);
            try {
                authService.login(badRequest);
            } catch (SifBrokerException e) {
                // Expected
            }
        }

        // Account should still be unlocked (only 4 failures since reset)
        UserLoginRequest finalGoodRequest = new UserLoginRequest(
            username, KNOWN_PASSWORD, testDomainStringId, testSolutionStringId);
        UserLoginResponse finalResponse = authService.login(finalGoodRequest);

        assertThat(finalResponse)
            .as("Account should not be locked after reset + 4 failures")
            .isNotNull();
        assertThat(finalResponse.sessionToken())
            .as("Session token should be returned")
            .isNotNull()
            .isNotBlank();
    }

    // ========== Helper Methods ==========

    private AdminUser createTestUser(String username, String password) {
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);

        Domain domain = domainRepository.findById(testDomainId)
            .orElseThrow(() -> new IllegalStateException("Test domain not found"));
        Solution solution = solutionRepository.findById(testSolutionId)
            .orElseThrow(() -> new IllegalStateException("Test solution not found"));

        user.setDomain(domain);
        user.setSolution(solution);
        return adminUserRepository.save(user);
    }

    // ========== Arbitraries ==========

    /**
     * Generates failure counts below the lockout threshold (0-4 inclusive).
     */
    @Provide
    Arbitrary<Integer> failureCountBelowThreshold() {
        return Arbitraries.integers().between(0, 4);
    }

    /**
     * Generates valid usernames matching {@code ^[a-zA-Z0-9_-]{3,128}$}.
     */
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('_', '-')
            .ofMinLength(3)
            .ofMaxLength(20)
            .filter(s -> s.matches("^[a-zA-Z0-9_-]{3,128}$"));
    }
}
