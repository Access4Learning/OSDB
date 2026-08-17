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
import org.sifunity.broker.environment.config.BootstrapProperties;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.service.BootstrapComponent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Bootstrap Idempotency.
 *
 * <p><strong>Feature: global-admin-bootstrap, Property 1: Bootstrap Idempotency</strong></p>
 *
 * <p>For any initial database state (empty, or already containing the global
 * domain/solution/admin environment), running the bootstrap process one or more
 * times SHALL result in exactly one domain with id GLOBAL_DOMAIN, exactly one
 * solution with id GLOBAL_SOLUTION under that domain, and exactly one admin
 * environment with solutionId = GLOBAL_SOLUTION. No duplicates are created.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4</strong></p>
 */
class BootstrapIdempotencyPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;
    private BootstrapComponent bootstrapComponent;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;
    private EnvironmentRepository environmentRepository;
    private ZoneRepository zoneRepository;
    private ContextRepository contextRepository;
    private ProviderRegistrationRepository providerRegistrationRepository;

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
            "--sif.broker.bootstrap.enabled=false",
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
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);
        environmentRepository = context.getBean(EnvironmentRepository.class);
        zoneRepository = context.getBean(ZoneRepository.class);
        contextRepository = context.getBean(ContextRepository.class);
        providerRegistrationRepository = context.getBean(ProviderRegistrationRepository.class);

        // Create a BootstrapComponent with enabled properties for testing
        BootstrapProperties props = new BootstrapProperties();
        props.setEnabled(true);
        BootstrapProperties.AdminCredentials creds = new BootstrapProperties.AdminCredentials();
        creds.setSessionToken("test-admin-token");
        creds.setSharedSecret("dGVzdC1hZG1pbi1zZWNyZXQ=");
        props.setAdmin(creds);
        bootstrapComponent = new BootstrapComponent(
            environmentRepository, domainRepository, solutionRepository, zoneRepository, contextRepository, providerRegistrationRepository, context.getBean(org.sifunity.broker.environment.service.ContextServiceService.class), props);

        // Clean up all bootstrap-related data before each try
        environmentRepository.findAll().stream()
            .filter(e -> "ADMIN".equals(e.getApplicationName()) || 
                         (e.getApplicationName() != null && e.getApplicationName().startsWith("BROKER_SERVICES_")))
            .forEach(env -> environmentRepository.delete(env));
        // Use domain lookup to find solutions by UUID
        domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)
            .ifPresent(dom -> solutionRepository.findByDomainUuid(dom.getId())
                .forEach(sol -> solutionRepository.delete(sol)));
        domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)
            .ifPresent(dom -> domainRepository.delete(dom));
    }

    /**
     * Property 1: Bootstrap Idempotency
     *
     * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4</strong></p>
     *
     * <p>For any initial state (domain present/absent, solution present/absent,
     * admin env present/absent) and any number of bootstrap runs (1-5),
     * exactly one global domain, one global solution, and one admin environment exist.</p>
     */
    @Property(tries = 10)
    @Tag("global-admin-bootstrap-property-1")
    void bootstrapIsIdempotent(
            @ForAll("initialStates") InitialState initialState,
            @ForAll("runCounts") int runCount) {

        // Set up the initial state
        if (initialState.domainExists) {
            Domain domain = new Domain();
            domain.setDomainId(GlobalAdminConstants.GLOBAL_DOMAIN);
            domain.setDescription("Global admin domain");
            domainRepository.save(domain);
        }

        if (initialState.solutionExists && initialState.domainExists) {
            Domain dom = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN).orElse(null);
            if (dom != null) {
                Solution solution = new Solution();
                solution.setDomainUuid(dom.getId());
                solution.setSolutionId(GlobalAdminConstants.GLOBAL_SOLUTION);
                solution.setDescription("Global admin solution");
                solutionRepository.save(solution);
            }
        }

        if (initialState.adminEnvExists && initialState.domainExists && initialState.solutionExists) {
            Domain adminDom = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN).orElse(null);
            Solution sol = adminDom == null ? null
                : solutionRepository.findByDomainUuidAndSolutionId(adminDom.getId(), GlobalAdminConstants.GLOBAL_SOLUTION).orElse(null);
            if (sol != null) {
                Environment admin = Environment.builder()
                    .applicationName("ADMIN")
                    .type(org.sifunity.broker.common.constant.EnvironmentType.BROKERED)
                    .solutionUuid(sol.getId())
                    .sessionToken("test-admin-token")
                    .sharedSecret("dGVzdC1hZG1pbi1zZWNyZXQ=")
                    .defaultZoneId("default")
                    .build();
                environmentRepository.save(admin);
            }
        }

        // Run bootstrap the specified number of times
        for (int i = 0; i < runCount; i++) {
            bootstrapComponent.bootstrapAdmin();
        }

        // Assert exactly one global domain exists
        long domainCount = domainRepository.findAll().stream()
            .filter(d -> GlobalAdminConstants.GLOBAL_DOMAIN.equals(d.getDomainId()))
            .count();
        assertThat(domainCount)
            .as("Exactly one global domain must exist after bootstrap")
            .isEqualTo(1);

        // Assert exactly one global solution exists under the global domain
        Domain globalDom = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN).orElse(null);
        assertThat(globalDom).as("Global domain must exist").isNotNull();
        long solutionCount = solutionRepository.findByDomainUuid(globalDom.getId()).stream()
            .filter(s -> GlobalAdminConstants.GLOBAL_SOLUTION.equals(s.getSolutionId()))
            .count();
        assertThat(solutionCount)
            .as("Exactly one global solution must exist after bootstrap")
            .isEqualTo(1);

        // Assert exactly one admin environment exists with the correct solutionUuid
        Solution globalSol = solutionRepository.findByDomainUuidAndSolutionId(globalDom.getId(), GlobalAdminConstants.GLOBAL_SOLUTION).orElse(null);
        assertThat(globalSol).as("Global solution must exist").isNotNull();
        long adminEnvCount = environmentRepository.findBySolutionUuid(globalSol.getId()).stream()
            .filter(e -> "ADMIN".equals(e.getApplicationName()))
            .count();
        assertThat(adminEnvCount)
            .as("Exactly one admin environment must exist after bootstrap")
            .isEqualTo(1);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<InitialState> initialStates() {
        return Combinators.combine(
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false)
        ).as(InitialState::new);
    }

    @Provide
    Arbitrary<Integer> runCounts() {
        return Arbitraries.integers().between(1, 5);
    }

    /**
     * Represents a possible initial database state before bootstrap runs.
     */
    record InitialState(boolean domainExists, boolean solutionExists, boolean adminEnvExists) {}
}
