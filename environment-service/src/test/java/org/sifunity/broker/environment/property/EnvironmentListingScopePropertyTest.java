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
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for environment listing scoped to domain and solution.
 *
 * <p><strong>Property 10: Environment Listing Scoped to Domain and Solution</strong></p>
 *
 * <p>For any valid domainId and solutionId combination, listing environments via
 * GET /{domainId}/{solutionId}/environments shall return only environments
 * belonging to that specific domain and solution. No environments from other
 * domains or solutions shall appear in the result.</p>
 *
 * <p><strong>Validates: Requirements 11.7</strong></p>
 */
class EnvironmentListingScopePropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;
    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private EnvironmentService environmentService;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;
    private ZoneRepository zoneRepository;
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
            "--spring.flyway.baseline-on-migrate=true"
        );
    }

    @AfterContainer
    static void stopContainer() {
        if (context != null) context.close();
        if (postgres != null) postgres.stop();
    }

    @BeforeTry
    void setUp() {
        domainService = context.getBean(DomainService.class);
        solutionService = context.getBean(SolutionService.class);
        zoneService = context.getBean(ZoneService.class);
        environmentService = context.getBean(EnvironmentService.class);
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);
        zoneRepository = context.getBean(ZoneRepository.class);
        environmentRepository = context.getBean(EnvironmentRepository.class);
        // Clean up in dependency order
        environmentRepository.deleteAll();
        zoneRepository.deleteAll();
        solutionRepository.deleteAll();
        domainRepository.deleteAll();
    }

    /**
     * Property 10: Environment Listing Scoped to Domain and Solution
     *
     * <p><strong>Validates: Requirements 11.7</strong></p>
     *
     * <p>Create environments in two different domain/solution pairs and verify
     * that listing by one solution returns only that solution's environments.</p>
     */
    @Property(tries = 10)
    @Tag("domain-solution-url-prefix-property-10")
    void environmentListingScopedToDomainAndSolution(
            @ForAll("validIds") String domainIdA,
            @ForAll("validIds") String domainIdB,
            @ForAll("validIds") String solutionIdA,
            @ForAll("validIds") String solutionIdB,
            @ForAll("validIds") String zoneIdA,
            @ForAll("validIds") String zoneIdB,
            @ForAll("appNames") String appNameA,
            @ForAll("appNames") String appNameB) {

        // Ensure all IDs are distinct to avoid collisions
        Assume.that(!domainIdA.equals(domainIdB));
        Assume.that(!solutionIdA.equals(solutionIdB));
        Assume.that(!zoneIdA.equals(zoneIdB));
        Assume.that(!appNameA.equals(appNameB));

        // Create domain A → solution A → zone A → environment A
        domainService.createDomain(new DomainCreateRequest(domainIdA, "Domain A"));
        solutionService.createSolution(new SolutionCreateRequest(domainIdA, solutionIdA, null));
        zoneService.createZone(domainIdA, new ZoneCreateRequest(solutionIdA, zoneIdA, null));
        environmentService.registerEnvironment(domainIdA, new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appNameA, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneIdA), solutionIdA, null)));

        // Create domain B → solution B → zone B → environment B
        domainService.createDomain(new DomainCreateRequest(domainIdB, "Domain B"));
        solutionService.createSolution(new SolutionCreateRequest(domainIdB, solutionIdB, null));
        zoneService.createZone(domainIdB, new ZoneCreateRequest(solutionIdB, zoneIdB, null));
        environmentService.registerEnvironment(domainIdB, new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appNameB, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneIdB), solutionIdB, null)));

        // List environments for solution A (simulates GET /{domainIdA}/{solutionIdA}/environments)
        List<SifEnvironmentResponse> listedForA = environmentService.listEnvironmentsBySolution(domainIdA, solutionIdA);

        assertThat(listedForA)
            .as("All listed environments must belong to solution A")
            .allMatch(e -> solutionIdA.equals(e.solutionId()));

        assertThat(listedForA)
            .as("Environment A must appear in solution A listing")
            .anyMatch(e -> appNameA.equals(e.consumerName()));

        assertThat(listedForA)
            .as("No environments from solution B should appear")
            .noneMatch(e -> appNameB.equals(e.consumerName()));

        // List environments for solution B
        List<SifEnvironmentResponse> listedForB = environmentService.listEnvironmentsBySolution(domainIdB, solutionIdB);

        assertThat(listedForB)
            .as("All listed environments must belong to solution B")
            .allMatch(e -> solutionIdB.equals(e.solutionId()));

        assertThat(listedForB)
            .as("Environment B must appear in solution B listing")
            .anyMatch(e -> appNameB.equals(e.consumerName()));

        assertThat(listedForB)
            .as("No environments from solution A should appear")
            .noneMatch(e -> appNameA.equals(e.consumerName()));
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> validIds() {
        Arbitrary<Character> alphaNum = Arbitraries.chars()
            .range('a', 'z')
            .range('0', '9');

        Arbitrary<String> middle = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('_', '-')
            .ofMinLength(0)
            .ofMaxLength(12);

        Arbitrary<String> singleChar = alphaNum.map(String::valueOf);

        Arbitrary<String> multiChar = Combinators.combine(alphaNum, middle, alphaNum)
            .as((first, mid, last) -> first + mid + last);

        return Arbitraries.oneOf(singleChar, multiChar);
    }

    @Provide
    Arbitrary<String> appNames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofMinLength(3)
            .ofMaxLength(20)
            .map(s -> "app" + s);
    }
}
