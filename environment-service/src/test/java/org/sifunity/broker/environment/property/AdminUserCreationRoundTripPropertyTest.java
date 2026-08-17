// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.testcontainers.containers.PostgreSQLContainer;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.domain.AdminUser;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.AdminUserCreateRequest;
import org.sifunity.broker.environment.dto.AdminUserDTO;
import org.sifunity.broker.environment.repository.AdminUserRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.AdminUserService;
import org.sifunity.broker.environment.test.config.TestRedisConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for admin user creation round-trip with bcrypt verification.
 *
 * <p><strong>Property 1: User creation round-trip with bcrypt</strong></p>
 *
 * <p>For any valid username (3-128 chars, {@code [a-zA-Z0-9_-]+}) and valid password
 * (8-72 chars), creating an admin user and then retrieving it should produce a record
 * where: the username matches exactly, the password hash is a valid bcrypt hash with
 * cost factor ≥ 10, and {@code BCrypt.checkpw(originalPassword, storedHash)} returns true.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.4</strong></p>
 */
class AdminUserCreationRoundTripPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;
    private AdminUserService adminUserService;
    private AdminUserRepository adminUserRepository;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;

    private String testDomainId;
    private String testSolutionId;
    private UUID testDomainUuid;
    private UUID testSolutionUuid;

    @BeforeContainer
    static void startContainer() {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
        postgres.start();

        SpringApplication app = new SpringApplication(
            EnvironmentServiceApplication.class,
            org.sifunity.broker.environment.test.config.TestRedisConfiguration.class);
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
    }

    @AfterContainer
    static void stopContainer() {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeTry
    void setUp() {
        adminUserService = context.getBean(AdminUserService.class);
        adminUserRepository = context.getBean(AdminUserRepository.class);
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);

        // Clean up admin users before each try to avoid uniqueness conflicts
        adminUserRepository.deleteAll();

        // Create a fresh domain and solution for each try
        Domain domain = new Domain();
        domain.setDomainId("test-domain-" + UUID.randomUUID().toString().substring(0, 8));
        domain.setDescription("Test domain for property test");
        domain = domainRepository.save(domain);
        testDomainId = domain.getDomainId();
        testDomainUuid = domain.getId();

        Solution solution = new Solution();
        solution.setDomainUuid(testDomainUuid);
        solution.setSolutionId("test-solution-" + UUID.randomUUID().toString().substring(0, 8));
        solution.setDescription("Test solution for property test");
        solution = solutionRepository.save(solution);
        testSolutionId = solution.getSolutionId();
        testSolutionUuid = solution.getId();
    }

    /**
     * Property 1: User creation round-trip with bcrypt
     *
     * <p><strong>Validates: Requirements 1.1, 1.4</strong></p>
     *
     * <p>For any valid username and password, creating an admin user should produce
     * a stored record with a valid bcrypt hash (cost ≥ 10) that verifies against
     * the original password.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: admin-user-management, Property 1: User creation round-trip with bcrypt")
    void userCreationRoundTripWithBcrypt(
            @ForAll("validUsernames") String username,
            @ForAll("validPasswords") String password) {

        // Create the admin user via the service
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                username, password, testDomainId, testSolutionId);
        AdminUserDTO createdDto = adminUserService.createUser(request);

        // Verify the DTO has correct username
        assertThat(createdDto.username())
            .as("Created user DTO username must match input")
            .isEqualTo(username);
        assertThat(createdDto.id())
            .as("Created user must have a UUID assigned")
            .isNotNull();
        assertThat(createdDto.domainUuid())
            .as("Domain UUID must match")
            .isEqualTo(testDomainUuid);
        assertThat(createdDto.solutionUuid())
            .as("Solution UUID must match")
            .isEqualTo(testSolutionUuid);

        // Retrieve the entity directly to inspect the password hash
        AdminUser storedUser = adminUserRepository.findById(createdDto.id())
            .orElseThrow(() -> new AssertionError("User not found in database after creation"));

        String storedHash = storedUser.getPasswordHash();

        // Verify the stored hash is a valid bcrypt hash with cost ≥ 10
        // BCrypt hashes start with $2a$ or $2b$ followed by the cost factor
        assertThat(storedHash)
            .as("Stored hash must be a bcrypt hash with cost factor ≥ 10")
            .matches("^\\$2[ab]\\$1[0-9]\\$.*");

        // Extract cost factor and verify it's at least 10
        String costStr = storedHash.substring(4, 6);
        int cost = Integer.parseInt(costStr);
        assertThat(cost)
            .as("BCrypt cost factor must be at least 10")
            .isGreaterThanOrEqualTo(10);

        // Verify BCrypt.checkpw returns true for the original password
        assertThat(BCrypt.checkpw(password, storedHash))
            .as("BCrypt.checkpw must return true for original password against stored hash")
            .isTrue();

        // Verify BCrypt.checkpw returns false for a different password
        assertThat(BCrypt.checkpw(password + "X", storedHash))
            .as("BCrypt.checkpw must return false for a different password")
            .isFalse();
    }

    // ========== Arbitraries ==========

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
            .ofMaxLength(30)  // Keep reasonable for test performance
            .filter(s -> s.matches("^[a-zA-Z0-9_-]{3,128}$"));
    }

    /**
     * Generates valid passwords of length 8-72.
     * Uses printable ASCII characters to simulate realistic passwords.
     */
    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings()
            .withCharRange('!', '~')  // Printable ASCII range (33-126)
            .ofMinLength(8)
            .ofMaxLength(40)  // Keep reasonable for test performance (bcrypt is slow)
            .filter(s -> s.length() >= 8 && s.length() <= 72);
    }
}
