// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.domain.AdminUser;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.UserLoginRequest;
import org.sifunity.broker.environment.dto.UserLoginResponse;
import org.sifunity.broker.environment.repository.AdminUserRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.UserEnvironmentAccessRepository;
import org.sifunity.broker.environment.service.UserAuthenticationService;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for admin session token uniqueness.
 *
 * <p><strong>Feature: admin-user-management, Property 10: Session token uniqueness</strong></p>
 *
 * <p>For any N successful authentication attempts (N ≥ 2), all generated session tokens
 * should be distinct (no collisions).</p>
 *
 * <p><strong>Validates: Requirements 3.4</strong></p>
 */
class AdminSessionTokenUniquenessPropertyTest {

    private static final int REDIS_PORT = 6379;

    static PostgreSQLContainer<?> postgres;
    @SuppressWarnings("resource")
    static GenericContainer<?> redis;
    static ConfigurableApplicationContext context;
    static LettuceConnectionFactory connectionFactory;

    private UserAuthenticationService authService;
    private AdminUserRepository adminUserRepository;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;

    private UUID testDomainId;
    private UUID testSolutionId;
    private String testDomainStringId;
    private String testSolutionStringId;
    private String testPassword;

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

        // Boot Spring context with test profile (uses mock Redis via TestRedisConfiguration)
        SpringApplication app = new SpringApplication(EnvironmentServiceApplication.class);
        app.setAdditionalProfiles("test");
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
            "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        );

        // Create a real Redis connection factory and template outside Spring context
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(
            redis.getHost(), redis.getMappedPort(REDIS_PORT));
        connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
    }

    @AfterContainer
    static void stopContainers() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
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
        adminUserRepository = context.getBean(AdminUserRepository.class);
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);
        UserEnvironmentAccessRepository accessRepository =
            context.getBean(UserEnvironmentAccessRepository.class);

        // Create a real StringRedisTemplate connected to the Redis container
        StringRedisTemplate realRedisTemplate = new StringRedisTemplate(connectionFactory);
        realRedisTemplate.afterPropertiesSet();

        // Instantiate UserAuthenticationService with real Redis template
        authService = new UserAuthenticationService(
            realRedisTemplate, adminUserRepository, accessRepository,
            domainRepository, solutionRepository);

        // Clean up between tries
        adminUserRepository.deleteAll();

        // Create fresh domain and solution
        Domain domain = new Domain();
        domain.setDomainId("token-test-" + UUID.randomUUID().toString().substring(0, 8));
        domain.setDescription("Test domain for session token uniqueness");
        domain = domainRepository.save(domain);
        testDomainId = domain.getId();
        testDomainStringId = domain.getDomainId();

        Solution solution = new Solution();
        solution.setDomainUuid(testDomainId);
        solution.setSolutionId("sol-" + UUID.randomUUID().toString().substring(0, 8));
        solution.setDescription("Test solution");
        solution = solutionRepository.save(solution);
        testSolutionId = solution.getId();
        testSolutionStringId = solution.getSolutionId();

        // Create admin user with known credentials
        testPassword = "TestPass123!";
        String passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt(10));

        AdminUser user = new AdminUser();
        user.setUsername("tokenuser");
        user.setPasswordHash(passwordHash);
        user.setDomain(domain);
        user.setSolution(solution);
        adminUserRepository.save(user);
    }

    /**
     * Property 10: Session token uniqueness
     *
     * <p>N ≥ 2 successful authentications with the same credentials produce
     * distinct session tokens every time.</p>
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: admin-user-management, Property 10: Session token uniqueness")
    void multipleLoginsProduceDistinctSessionTokens(
            @ForAll("loginCount") int loginCount) {

        UserLoginRequest request = new UserLoginRequest(
            "tokenuser", testPassword, testDomainStringId, testSolutionStringId);

        Set<String> sessionTokens = new HashSet<>();

        for (int i = 0; i < loginCount; i++) {
            UserLoginResponse response = authService.login(request);

            assertThat(response.sessionToken())
                .as("Session token from login attempt %d must not be null or blank", i + 1)
                .isNotNull()
                .isNotBlank();

            // Verify UUID format (128 bits of entropy)
            UUID.fromString(response.sessionToken());

            boolean isUnique = sessionTokens.add(response.sessionToken());
            assertThat(isUnique)
                .as("Session token '%s' from login attempt %d must be unique (no collision)",
                    response.sessionToken(), i + 1)
                .isTrue();
        }

        // Final verification: all tokens are distinct
        assertThat(sessionTokens)
            .as("All %d session tokens must be distinct", loginCount)
            .hasSize(loginCount);
    }

    // ========== Arbitraries ==========

    /**
     * Generates login count between 5 and 10 (per task specification: N = 5-10).
     */
    @Provide
    Arbitrary<Integer> loginCount() {
        return Arbitraries.integers().between(5, 10);
    }
}
