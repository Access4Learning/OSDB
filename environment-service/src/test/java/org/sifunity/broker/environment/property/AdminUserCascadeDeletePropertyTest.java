// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.repository.*;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for cascade delete on admin user deletion.
 *
 * <p><strong>Feature: admin-user-management, Property 5: Cascade delete preserves
 * referential integrity on user deletion</strong></p>
 *
 * <p>For any admin user with N ≥ 0 environment associations, deleting the admin user
 * should result in zero remaining user_environment_access records referencing that
 * user's ID.</p>
 *
 * <p><strong>Validates: Requirements 2.5</strong></p>
 */
class AdminUserCascadeDeletePropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private AdminUserRepository adminUserRepository;
    private UserEnvironmentAccessRepository accessRepository;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;
    private EnvironmentRepository environmentRepository;

    @BeforeContainer
    static void startContainer() {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
        postgres.start();

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
        adminUserRepository = context.getBean(AdminUserRepository.class);
        accessRepository = context.getBean(UserEnvironmentAccessRepository.class);
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);
        environmentRepository = context.getBean(EnvironmentRepository.class);
        // Clean up between tries
        accessRepository.deleteAll();
        adminUserRepository.deleteAll();
        environmentRepository.deleteAll();
    }

    /**
     * Deleting an admin user with N environment associations (0-5) results in
     * zero remaining user_environment_access records for that user ID.
     *
     * <p><strong>Validates: Requirements 2.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: admin-user-management, Property 5: Cascade delete preserves referential integrity on user deletion")
    void deletingAdminUserCascadesAllAccessRecords(
            @ForAll("environmentCount") int numEnvironments) {

        // 1. Create supporting hierarchy: domain + solution
        Domain domain = new Domain();
        domain.setDomainId("cascade-test-" + UUID.randomUUID().toString().substring(0, 8));
        domain.setDescription("Test domain for cascade delete");
        domain = domainRepository.save(domain);

        Solution solution = new Solution();
        solution.setDomainUuid(domain.getId());
        solution.setSolutionId("sol-" + UUID.randomUUID().toString().substring(0, 8));
        solution.setDescription("Test solution");
        solution = solutionRepository.save(solution);

        // 2. Create admin user
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername("user-" + UUID.randomUUID().toString().substring(0, 8));
        adminUser.setPasswordHash("$2a$10$dummyhashforpropertytest000000000000000000000000000000");
        adminUser.setDomain(domain);
        adminUser.setSolution(solution);
        adminUser = adminUserRepository.save(adminUser);

        UUID userId = adminUser.getId();

        // 3. Create N environments and associate them with the user
        for (int i = 0; i < numEnvironments; i++) {
            Environment env = new Environment();
            env.setApplicationName("app-" + UUID.randomUUID().toString().substring(0, 8));
            env.setSessionToken(UUID.randomUUID().toString());
            env.setSharedSecret("PLACEHOLDER");
            env.setType(EnvironmentType.DIRECT);
            env.setSolutionUuid(solution.getId());
            env.setCreatedAt(Instant.now());
            env = environmentRepository.save(env);

            UserEnvironmentAccess access = new UserEnvironmentAccess();
            access.setUser(adminUser);
            access.setEnvironment(env);
            access = accessRepository.save(access);
        }

        // 4. Verify associations exist before deletion
        assertThat(accessRepository.findAllByUser_Id(userId))
            .as("Should have %d access records before deletion", numEnvironments)
            .hasSize(numEnvironments);

        // 5. Delete the admin user
        adminUserRepository.delete(adminUser);
        adminUserRepository.flush();

        // 6. Verify zero user_environment_access records remain for that user ID
        assertThat(accessRepository.findAllByUser_Id(userId))
            .as("All access records must be cascade-deleted when admin user is deleted")
            .isEmpty();
    }

    // ========== Arbitraries ==========

    /**
     * Generates environment count between 0 and 5 (inclusive).
     */
    @Provide
    Arbitrary<Integer> environmentCount() {
        return Arbitraries.integers().between(0, 5);
    }
}
