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
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.AuthorizationService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Admin Detection via Domain/Solution Membership.
 *
 * <p><strong>Feature: global-admin-bootstrap, Property 3: Admin Detection via Domain/Solution Membership</strong></p>
 *
 * <p>For any environment, the AuthorizationService SHALL grant admin privileges
 * (canCreateEnvironment, canDeleteEnvironment, canCreateAcl, etc.) if and only if
 * the environment's solutionId resolves to GLOBAL_DOMAIN/GLOBAL_SOLUTION.
 * Environments with any other domain/solution combination SHALL NOT receive
 * admin privileges.</p>
 *
 * <p><strong>Validates: Requirements 3.2, 3.3</strong></p>
 */
class AdminDetectionPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;
    private AuthorizationService authorizationService;
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
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword(),
            "--spring.datasource.driver-class-name=org.postgresql.Driver",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
            "--spring.flyway.enabled=true",
            "--spring.flyway.locations=classpath:db/migration",
            "--spring.flyway.baseline-on-migrate=true",
            "--sif.broker.bootstrap.enabled=true",
            "--server.port=0"
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
        authorizationService = context.getBean(AuthorizationService.class);
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);
        environmentRepository = context.getBean(EnvironmentRepository.class);

        // Clean up test environments (but not the bootstrap admin)
        environmentRepository.findAll().stream()
            .filter(e -> e.getApplicationName() != null && e.getApplicationName().startsWith("test-admin-det-"))
            .forEach(e -> environmentRepository.delete(e));
        // Clean up test solutions and domains
        solutionRepository.findAll().stream()
            .filter(s -> s.getSolutionId() != null && s.getSolutionId().startsWith("test-sol-ad-"))
            .forEach(s -> solutionRepository.delete(s));
        domainRepository.findAll().stream()
            .filter(d -> d.getDomainId() != null && d.getDomainId().startsWith("test-dom-ad-"))
            .forEach(d -> domainRepository.delete(d));
    }

    /**
     * Property 3: Admin Detection via Domain/Solution Membership
     *
     * <p><strong>Validates: Requirements 3.2, 3.3</strong></p>
     *
     * <p>For any environment whose solutionId resolves to GLOBAL_DOMAIN/GLOBAL_SOLUTION,
     * all admin privilege checks return true. For any other domain/solution combination,
     * all admin privilege checks return false (except self-access for read/update).</p>
     */
    @Property(tries = 10)
    @Tag("global-admin-bootstrap-property-3")
    void adminPrivilegesGrantedIffGlobalDomainAndSolution(
            @ForAll("adminScenarios") AdminScenario scenario) {

        String suffix = scenario.uniqueSuffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String domainId = scenario.isGlobalAdmin
            ? GlobalAdminConstants.GLOBAL_DOMAIN
            : "test-dom-ad-" + suffix;
        String solutionId = scenario.isGlobalAdmin
            ? GlobalAdminConstants.GLOBAL_SOLUTION
            : "test-sol-ad-" + suffix;
        String appName = "test-admin-det-" + suffix;

        // Ensure domain exists (global domain already exists from bootstrap)
        if (!scenario.isGlobalAdmin) {
            if (domainRepository.findByDomainId(domainId).isEmpty()) {
                Domain domain = new Domain();
                domain.setDomainId(domainId);
                domain.setDescription("Test domain " + suffix);
                domainRepository.save(domain);
            }
        }

        // Ensure solution exists (global solution already exists from bootstrap)
        if (!scenario.isGlobalAdmin) {
            Domain dom = domainRepository.findByDomainId(domainId).orElse(null);
            if (dom != null && !solutionRepository.existsByDomainUuidAndSolutionId(dom.getId(), solutionId)) {
                Solution solution = new Solution();
                solution.setDomainUuid(dom.getId());
                solution.setSolutionId(solutionId);
                solution.setDescription("Test solution " + suffix);
                solutionRepository.save(solution);
            }
        }

        // Resolve solution UUID for environment creation (domain-scoped)
        Domain resolvedDomain = domainRepository.findByDomainId(domainId).orElse(null);
        Solution sol = resolvedDomain == null ? null
            : solutionRepository.findByDomainUuidAndSolutionId(resolvedDomain.getId(), solutionId).orElse(null);
        UUID solutionUuid = sol != null ? sol.getId() : null;

        // Create the environment under test
        Environment env = Environment.builder()
            .applicationName(appName)
            .type(scenario.envType)
            .solutionUuid(solutionUuid)
            .sessionToken(UUID.randomUUID().toString())
            .sharedSecret("dGVzdC1zZWNyZXQtMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkw")
            .defaultZoneId("default")
            .build();
        environmentRepository.save(env);

        // Create a separate target environment for read/update/delete checks
        String targetAppName = "test-admin-det-target-" + suffix;
        Environment target = Environment.builder()
            .applicationName(targetAppName)
            .type(EnvironmentType.BROKERED)
            .solutionUuid(solutionUuid)
            .sessionToken(UUID.randomUUID().toString())
            .sharedSecret("dGVzdC1zZWNyZXQtMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkw")
            .defaultZoneId("default")
            .build();
        environmentRepository.save(target);

        // Create a dummy ACL entry for ACL read/update/delete checks
        AclEntry acl = AclEntry.builder()
            .environment(target)
            .contextServiceUuid(UUID.randomUUID())
            .accessType(org.sifunity.broker.common.constant.AccessType.QUERY)
            .accessLevel(org.sifunity.broker.common.constant.AccessLevel.APPROVED)
            .build();

        boolean expectedAdmin = scenario.isGlobalAdmin;

        // Verify all authorization methods
        assertThat(authorizationService.canCreateEnvironment(env))
            .as("canCreateEnvironment for isGlobalAdmin=%s", expectedAdmin)
            .isEqualTo(expectedAdmin);

        assertThat(authorizationService.canDeleteEnvironment(env, target))
            .as("canDeleteEnvironment for isGlobalAdmin=%s", expectedAdmin)
            .isEqualTo(expectedAdmin);

        assertThat(authorizationService.canCreateAcl(env))
            .as("canCreateAcl for isGlobalAdmin=%s", expectedAdmin)
            .isEqualTo(expectedAdmin);

        assertThat(authorizationService.canUpdateAcl(env, acl))
            .as("canUpdateAcl for isGlobalAdmin=%s", expectedAdmin)
            .isEqualTo(expectedAdmin);

        assertThat(authorizationService.canDeleteAcl(env, acl))
            .as("canDeleteAcl for isGlobalAdmin=%s", expectedAdmin)
            .isEqualTo(expectedAdmin);

        // For read/update: admin gets access to any environment; non-admin only to self
        assertThat(authorizationService.canReadEnvironment(env, target))
            .as("canReadEnvironment(env, OTHER) for isGlobalAdmin=%s", expectedAdmin)
            .isEqualTo(expectedAdmin);

        assertThat(authorizationService.canUpdateEnvironment(env, target))
            .as("canUpdateEnvironment(env, OTHER) for isGlobalAdmin=%s", expectedAdmin)
            .isEqualTo(expectedAdmin);

        // Admin can read ACLs belonging to other environments
        assertThat(authorizationService.canReadAcl(env, acl))
            .as("canReadAcl(env, OTHER's acl) for isGlobalAdmin=%s", expectedAdmin)
            .isEqualTo(expectedAdmin);

        // Cleanup this iteration's data
        environmentRepository.delete(target);
        environmentRepository.delete(env);
        if (!scenario.isGlobalAdmin) {
            solutionRepository.findAll().stream()
                .filter(s -> solutionId.equals(s.getSolutionId()))
                .forEach(s -> solutionRepository.delete(s));
            domainRepository.findByDomainId(domainId)
                .ifPresent(d -> domainRepository.delete(d));
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<AdminScenario> adminScenarios() {
        Arbitrary<Boolean> isGlobalAdmin = Arbitraries.of(true, false);
        Arbitrary<EnvironmentType> envType = Arbitraries.of(EnvironmentType.DIRECT, EnvironmentType.BROKERED);
        Arbitrary<Integer> uniqueSuffix = Arbitraries.integers().between(1, 100_000);
        return Combinators.combine(isGlobalAdmin, envType, uniqueSuffix).as(AdminScenario::new);
    }

    /**
     * Represents a test scenario for admin detection.
     *
     * @param isGlobalAdmin whether the environment should be in the global admin domain/solution
     * @param envType the environment type (DIRECT or BROKERED)
     * @param uniqueSuffix a unique suffix to avoid collisions between iterations
     */
    record AdminScenario(boolean isGlobalAdmin, EnvironmentType envType, int uniqueSuffix) {}
}
