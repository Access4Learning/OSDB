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
import org.sifunity.broker.environment.exception.AccountLockedException;
import org.sifunity.broker.environment.repository.AdminUserRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.UserAuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Property-based test for no information leakage on authentication failure.
 *
 * <p><strong>Feature: admin-user-management, Property 8: No information leakage on
 * authentication failure</strong></p>
 *
 * <p>For any invalid credential combination (non-existent username, incorrect password,
 * or empty fields), the authentication error response should be identical in structure
 * and message content — specifically, it must not reveal whether the username exists
 * or whether the password was the incorrect field.</p>
 *
 * <p><strong>Validates: Requirements 3.2</strong></p>
 */
class AuthenticationNoLeakagePropertyTest {

    private static final int REDIS_PORT = 6379;
    private static final String KNOWN_USERNAME = "known-user";
    private static final String KNOWN_PASSWORD = "SecurePass123!";
    private static final String EXPECTED_ERROR_MESSAGE = "Invalid credentials";

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
        // Do NOT use "test" profile — it disables Redis auto-configuration and provides a mock.
        // We need real Redis for authentication tests.
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
            "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
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
            "--authentication.timestamp-tolerance-minutes=5",
            "--observability.enabled=false",
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

        // Clean up admin users
        adminUserRepository.deleteAll();

        // Create fresh domain and solution for each try
        Domain domain = new Domain();
        domain.setDomainId("noleak-test-" + UUID.randomUUID().toString().substring(0, 8));
        domain.setDescription("Test domain for no-leakage property test");
        domain = domainRepository.save(domain);
        testDomainId = domain.getId();
        testDomainStringId = domain.getDomainId();

        Solution solution = new Solution();
        solution.setDomainUuid(testDomainId);
        solution.setSolutionId("sol-" + UUID.randomUUID().toString().substring(0, 8));
        solution.setDescription("Test solution for no-leakage property test");
        solution = solutionRepository.save(solution);
        testSolutionId = solution.getId();
        testSolutionStringId = solution.getSolutionId();

        // Create a known user for testing
        createTestUser(KNOWN_USERNAME, KNOWN_PASSWORD);
    }

    /**
     * Property 8: All invalid credential combinations produce identical error responses.
     *
     * <p>Verifies that login with wrong password, non-existent username, empty username,
     * and empty password all produce the same exception type, HTTP status, error code,
     * and message — revealing no information about which field was incorrect or whether
     * the username exists.</p>
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: admin-user-management, Property 8: No information leakage on authentication failure")
    void allInvalidCredentialCombinationsProduceIdenticalError(
            @ForAll("wrongPasswords") String wrongPassword,
            @ForAll("nonExistentUsernames") String nonExistentUsername) {

        // Case 1: Wrong password for existing user
        SifBrokerException wrongPasswordError = catchThrowableOfType(
            () -> authService.login(new UserLoginRequest(
                KNOWN_USERNAME, wrongPassword, testDomainStringId, testSolutionStringId)),
            SifBrokerException.class);

        // Case 2: Non-existent username
        SifBrokerException nonExistentUserError = catchThrowableOfType(
            () -> authService.login(new UserLoginRequest(
                nonExistentUsername, KNOWN_PASSWORD, testDomainStringId, testSolutionStringId)),
            SifBrokerException.class);

        // Case 3: Empty username
        SifBrokerException emptyUsernameError = catchThrowableOfType(
            () -> authService.login(new UserLoginRequest(
                "", KNOWN_PASSWORD, testDomainStringId, testSolutionStringId)),
            SifBrokerException.class);

        // Case 4: Empty password
        SifBrokerException emptyPasswordError = catchThrowableOfType(
            () -> authService.login(new UserLoginRequest(
                KNOWN_USERNAME, "", testDomainStringId, testSolutionStringId)),
            SifBrokerException.class);

        // All errors must be non-null (login must fail for all cases)
        assertThat(wrongPasswordError)
            .as("Wrong password should produce an error")
            .isNotNull();
        assertThat(nonExistentUserError)
            .as("Non-existent username should produce an error")
            .isNotNull();
        assertThat(emptyUsernameError)
            .as("Empty username should produce an error")
            .isNotNull();
        assertThat(emptyPasswordError)
            .as("Empty password should produce an error")
            .isNotNull();

        // Verify all produce identical HTTP status (401 Unauthorized)
        assertThat(wrongPasswordError.getHttpStatus())
            .as("Wrong password error must return 401")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(nonExistentUserError.getHttpStatus())
            .as("Non-existent user error must return 401")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(emptyUsernameError.getHttpStatus())
            .as("Empty username error must return 401")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(emptyPasswordError.getHttpStatus())
            .as("Empty password error must return 401")
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Verify all produce identical error message ("Invalid credentials")
        assertThat(wrongPasswordError.getMessage())
            .as("Wrong password error message must be generic")
            .isEqualTo(EXPECTED_ERROR_MESSAGE);
        assertThat(nonExistentUserError.getMessage())
            .as("Non-existent user error message must be identical")
            .isEqualTo(EXPECTED_ERROR_MESSAGE);
        assertThat(emptyUsernameError.getMessage())
            .as("Empty username error message must be identical")
            .isEqualTo(EXPECTED_ERROR_MESSAGE);
        assertThat(emptyPasswordError.getMessage())
            .as("Empty password error message must be identical")
            .isEqualTo(EXPECTED_ERROR_MESSAGE);

        // Verify all produce identical error code
        assertThat(wrongPasswordError.getErrorCode())
            .as("All error codes must be identical — wrong password vs non-existent user")
            .isEqualTo(nonExistentUserError.getErrorCode());
        assertThat(wrongPasswordError.getErrorCode())
            .as("All error codes must be identical — wrong password vs empty username")
            .isEqualTo(emptyUsernameError.getErrorCode());
        assertThat(wrongPasswordError.getErrorCode())
            .as("All error codes must be identical — wrong password vs empty password")
            .isEqualTo(emptyPasswordError.getErrorCode());

        // Verify no difference in exception class reveals the failure reason
        assertThat(wrongPasswordError.getClass())
            .as("Exception class must not differ between wrong password and non-existent user")
            .isEqualTo(nonExistentUserError.getClass());
        assertThat(wrongPasswordError.getClass())
            .as("Exception class must not differ between wrong password and empty username")
            .isEqualTo(emptyUsernameError.getClass());
        assertThat(wrongPasswordError.getClass())
            .as("Exception class must not differ between wrong password and empty password")
            .isEqualTo(emptyPasswordError.getClass());
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
     * Generates wrong passwords that are valid in format but different from KNOWN_PASSWORD.
     */
    @Provide
    Arbitrary<String> wrongPasswords() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('!', '@', '#', '$')
            .ofMinLength(8)
            .ofMaxLength(40)
            .filter(s -> !s.equals(KNOWN_PASSWORD));
    }

    /**
     * Generates usernames that do NOT match the known user's username.
     * These simulate login attempts with non-existent accounts.
     */
    @Provide
    Arbitrary<String> nonExistentUsernames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('_', '-')
            .ofMinLength(3)
            .ofMaxLength(20)
            .filter(s -> !s.equals(KNOWN_USERNAME)
                && s.matches("^[a-zA-Z0-9_-]{3,128}$"));
    }
}
