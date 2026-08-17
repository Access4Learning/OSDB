// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.repository.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for bootstrap UUID FK correctness.
 *
 * <p><strong>Feature: uuid-foreign-key-hierarchy, Property 8: Bootstrap entities have correct UUID foreign keys</strong></p>
 *
 * <p>For any bootstrap execution, the global admin solution's {@code domainUuid} must equal
 * the global admin domain's UUID PK, the environment-global zone's {@code solutionUuid} must
 * equal the global solution's UUID PK, the DEFAULT context's {@code zoneUuid} must equal the
 * environment-global zone's UUID PK, and the admin environment's {@code solutionUuid} must
 * equal the global solution's UUID PK.</p>
 *
 * <p><strong>Validates: Requirements 8.1, 8.2, 8.3, 8.4</strong></p>
 */
class BootstrapUuidFkPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;
    private ZoneRepository zoneRepository;
    private ContextRepository contextRepository;
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
            "--sif.broker.bootstrap.admin.session-token=test-bootstrap-token",
            "--sif.broker.bootstrap.admin.shared-secret=test-bootstrap-secret"
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
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);
        zoneRepository = context.getBean(ZoneRepository.class);
        contextRepository = context.getBean(ContextRepository.class);
        environmentRepository = context.getBean(EnvironmentRepository.class);
    }

    /**
     * Property 8: Bootstrap entities have correct UUID foreign keys
     *
     * <p><strong>Validates: Requirements 8.1, 8.2, 8.3, 8.4</strong></p>
     *
     * <p>Bootstrap is deterministic — one try is sufficient. We use property test format
     * for consistency with the rest of the property test suite.</p>
     */
    @Property(tries = 1)
    @Tag("uuid-foreign-key-hierarchy-property-8")
    void bootstrapEntitiesHaveCorrectUuidForeignKeys() {
        // 1. Look up GLOBAL_DOMAIN domain entity
        Domain globalDomain = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)
            .orElseThrow(() -> new AssertionError("GLOBAL_DOMAIN not found after bootstrap"));

        // 2. Look up GLOBAL_SOLUTION solution entity
        Solution globalSolution = solutionRepository.findByDomainUuidAndSolutionId(globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)
            .orElseThrow(() -> new AssertionError("GLOBAL_SOLUTION not found after bootstrap"));

        // Requirement 8.1: Global solution's domainUuid equals global domain's UUID PK
        assertThat(globalSolution.getDomainUuid())
            .as("Global solution's domainUuid must equal global domain's UUID PK")
            .isEqualTo(globalDomain.getId());

        // 3. Look up "environment-global" zone entity
        Zone envGlobalZone = zoneRepository.findBySolutionUuidAndZoneId(globalSolution.getId(), "environment-global")
            .orElseThrow(() -> new AssertionError("environment-global zone not found after bootstrap"));

        // Requirement 8.2: Environment-global zone's solutionUuid equals global solution's UUID PK
        assertThat(envGlobalZone.getSolutionUuid())
            .as("Environment-global zone's solutionUuid must equal global solution's UUID PK")
            .isEqualTo(globalSolution.getId());

        // 4. Look up DEFAULT context in the environment-global zone
        List<Context> contexts = contextRepository.findByZoneUuid(envGlobalZone.getId());
        Context defaultContext = contexts.stream()
            .filter(c -> "DEFAULT".equals(c.getContextId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("DEFAULT context not found in environment-global zone"));

        // Requirement 8.3: DEFAULT context's zoneUuid equals environment-global zone's UUID PK
        assertThat(defaultContext.getZoneUuid())
            .as("DEFAULT context's zoneUuid must equal environment-global zone's UUID PK")
            .isEqualTo(envGlobalZone.getId());

        // 5. Look up ADMIN environment entity
        Environment adminEnv = environmentRepository
            .findByApplicationNameAndSolutionUuid("ADMIN", globalSolution.getId())
            .orElseThrow(() -> new AssertionError("ADMIN environment not found after bootstrap"));

        // Requirement 8.4: Admin environment's solutionUuid equals global solution's UUID PK
        assertThat(adminEnv.getSolutionUuid())
            .as("Admin environment's solutionUuid must equal global solution's UUID PK")
            .isEqualTo(globalSolution.getId());
    }
}
