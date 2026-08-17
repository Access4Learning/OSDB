// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.DuplicateContextException;
import org.sifunity.broker.environment.exception.DuplicateSolutionException;
import org.sifunity.broker.environment.exception.DuplicateZoneException;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for composite uniqueness enforced via UUID FK.
 *
 * <p><strong>Feature: uuid-foreign-key-hierarchy, Property 3: Composite uniqueness enforced via UUID FK</strong></p>
 *
 * <p>For any parent UUID and child string ID pair, the database must reject a second insert
 * with the same (parent_uuid, child_string_id) combination. This applies to:
 * (domain_uuid, solution_id), (solution_uuid, zone_id), and (zone_uuid, context_id).</p>
 *
 * <p><strong>Validates: Requirements 1.5, 2.4, 3.4</strong></p>
 */
class CompositeUniquenessPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private DomainRepository domainRepository;

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
        domainRepository = context.getBean(DomainRepository.class);
        // Clean up all entities between tries (cascade from domains handles children)
        domainRepository.deleteAll();
    }

    /**
     * Property 3.1: Solution uniqueness within domain (domain_uuid, solution_id)
     *
     * <p><strong>Validates: Requirements 1.5</strong></p>
     *
     * <p>For any domain and solution ID, creating a second solution with the same
     * (domain_uuid, solution_id) pair must be rejected with DuplicateSolutionException.</p>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-3")
    void duplicateSolutionWithinDomainIsRejected(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String solutionId) {

        // Create a domain
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));

        // Create a solution with solutionId in that domain
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "First solution"));

        // Attempt to create a duplicate solution with the same (domain, solutionId) pair
        assertThatThrownBy(() ->
            solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Duplicate solution"))
        ).isInstanceOf(DuplicateSolutionException.class);
    }

    /**
     * Property 3.2: Zone uniqueness within solution (solution_uuid, zone_id)
     *
     * <p><strong>Validates: Requirements 2.4</strong></p>
     *
     * <p>For any solution and zone ID, creating a second zone with the same
     * (solution_uuid, zone_id) pair must be rejected with DuplicateZoneException.</p>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-3")
    void duplicateZoneWithinSolutionIsRejected(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String solutionId,
            @ForAll("validIds") String zoneId) {

        // Create a domain and solution
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Test solution"));

        // Create a zone with zoneId in that solution
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "First zone"));

        // Attempt to create a duplicate zone with the same (solution, zoneId) pair
        assertThatThrownBy(() ->
            zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Duplicate zone"))
        ).isInstanceOf(DuplicateZoneException.class);
    }

    /**
     * Property 3.3: Context uniqueness within zone (zone_uuid, context_id)
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     *
     * <p>For any zone and context ID, creating a second context with the same
     * (zone_uuid, context_id) pair must be rejected with DuplicateContextException.</p>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-3")
    void duplicateContextWithinZoneIsRejected(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String solutionId,
            @ForAll("validIds") String zoneId,
            @ForAll("validIds") String contextId) {

        // Create a domain, solution, and zone
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));

        // Create a context with contextId in that zone
        zoneService.createContext(domainId, solutionId, zoneId, new ContextCreateRequest(contextId, "First context"));

        // Attempt to create a duplicate context with the same (zone, contextId) pair
        assertThatThrownBy(() ->
            zoneService.createContext(domainId, solutionId, zoneId, new ContextCreateRequest(contextId, "Duplicate context"))
        ).isInstanceOf(DuplicateContextException.class);
    }

    // ========== Arbitraries ==========

    /**
     * Generates valid IDs matching the naming pattern:
     * starts with alphanumeric, contains alphanumeric, underscore, or hyphen,
     * ends with alphanumeric.
     */
    @Provide
    Arbitrary<String> validIds() {
        Arbitrary<Character> alphaNum = Arbitraries.chars()
            .range('a', 'z')
            .range('A', 'Z')
            .range('0', '9');

        Arbitrary<String> middle = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('_', '-')
            .ofMinLength(0)
            .ofMaxLength(10);

        Arbitrary<String> singleChar = alphaNum.map(String::valueOf);

        Arbitrary<String> multiChar = Combinators.combine(alphaNum, middle, alphaNum)
            .as((first, mid, last) -> first + mid + last);

        return Arbitraries.oneOf(singleChar, multiChar);
    }
}
