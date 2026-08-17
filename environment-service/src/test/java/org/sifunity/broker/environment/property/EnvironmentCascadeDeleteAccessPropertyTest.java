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
import org.sifunity.broker.environment.domain.AdminUser;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.UserEnvironmentAccess;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.AdminUserRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.UserEnvironmentAccessRepository;
import org.sifunity.broker.environment.service.AdminUserService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.UserEnvironmentAccessService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for cascade delete on environment deletion.
 *
 * <p><strong>Feature: admin-user-management, Property 6: Cascade delete preserves
 * referential integrity on environment deletion</strong></p>
 *
 * <p>For any environment with N ≥ 0 user associations, deleting the environment
 * should result in zero remaining {@code user_environment_access} records
 * referencing that environment's ID.</p>
 *
 * <p><strong>Validates: Requirements 2.6</strong></p>
 */
class EnvironmentCascadeDeleteAccessPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private EnvironmentService environmentService;
    private AdminUserService adminUserService;
    private UserEnvironmentAccessService accessService;
    private UserEnvironmentAccessRepository accessRepository;
    private AdminUserRepository adminUserRepository;
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
        domainService = context.getBean(DomainService.class);
        solutionService = context.getBean(SolutionService.class);
        zoneService = context.getBean(ZoneService.class);
        environmentService = context.getBean(EnvironmentService.class);
        adminUserService = context.getBean(AdminUserService.class);
        accessService = context.getBean(UserEnvironmentAccessService.class);
        accessRepository = context.getBean(UserEnvironmentAccessRepository.class);
        adminUserRepository = context.getBean(AdminUserRepository.class);
        environmentRepository = context.getBean(EnvironmentRepository.class);
    }

    /**
     * Property 6: Cascade delete preserves referential integrity on environment deletion.
     *
     * <p><strong>Validates: Requirements 2.6</strong></p>
     *
     * <p>For each try:</p>
     * <ol>
     *   <li>Create a domain, solution, zone, and environment</li>
     *   <li>Create N admin users (N generated by jqwik, 0-5) and associate them with the environment</li>
     *   <li>Delete the environment</li>
     *   <li>Verify zero user_environment_access records remain for that environment ID</li>
     * </ol>
     */
    @Property(tries = 10)
    @Tag("Feature: admin-user-management, Property 6: Cascade delete preserves referential integrity on environment deletion")
    void deletingEnvironmentCascadesAllUserAccessRecords(
            @ForAll("userCount") int numberOfUsers) {

        // 1. Create hierarchy: domain → solution → zone → environment
        String domainId = "env-cascade-domain-" + System.nanoTime();
        DomainDTO domain = domainService.createDomain(new DomainCreateRequest(domainId, "Cascade env test domain"));

        String solutionId = "env-cascade-solution-" + System.nanoTime();
        SolutionDTO solution = solutionService.createSolution(
            new SolutionCreateRequest(domainId, solutionId, "Cascade env test solution"));

        String zoneId = "env-cascade-zone-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));

        // Register environment
        String appName = "cascade-env-app-" + System.nanoTime();
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
            domainId,
            new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.DIRECT, DefaultZoneDTO.of(zoneId), solutionId, null)));
        UUID environmentId = envResponse.environment().id();

        // 2. Create N admin users and associate each with the environment
        for (int i = 0; i < numberOfUsers; i++) {
            String username = "envuser" + i + "-" + System.nanoTime();
            AdminUserDTO user = adminUserService.createUser(new AdminUserCreateRequest(
                username, "password123", domain.domainId(), solution.solutionId()));

            accessService.grantAccess(user.id(), new UserEnvironmentAccessRequest(environmentId));
        }

        // Verify associations exist before deletion
        List<UserEnvironmentAccess> accessBefore = accessRepository.findAll().stream()
            .filter(a -> a.getEnvironment().getId().equals(environmentId))
            .toList();
        assertThat(accessBefore)
            .as("Should have %d access records before environment deletion", numberOfUsers)
            .hasSize(numberOfUsers);

        // 3. Delete the environment
        environmentService.deleteEnvironment(environmentId);

        // 4. Verify zero user_environment_access records remain for that environment ID
        List<UserEnvironmentAccess> accessAfter = accessRepository.findAll().stream()
            .filter(a -> a.getEnvironment().getId().equals(environmentId))
            .toList();
        assertThat(accessAfter)
            .as("All user_environment_access records must be cascade-deleted when environment is deleted")
            .isEmpty();

        // Also verify the admin users themselves still exist (only the association is deleted)
        List<AdminUser> remainingUsers = adminUserRepository.findAllByDomain_IdAndSolution_Id(
            domain.id(), solution.id());
        assertThat(remainingUsers)
            .as("Admin users should still exist after environment deletion (only associations are cascade-deleted)")
            .hasSize(numberOfUsers);
    }

    // ========== Arbitraries ==========

    /**
     * Generates the number of user associations (0-5) as specified by the task.
     */
    @Provide
    Arbitrary<Integer> userCount() {
        return Arbitraries.integers().between(0, 5);
    }
}
