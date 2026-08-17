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
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for cascade deletion through the entity hierarchy.
 *
 * <p><strong>Feature: uuid-foreign-key-hierarchy, Property 2: Cascade deletion propagates
 * through the hierarchy</strong></p>
 *
 * <p>For any parent entity (domain, solution, or zone) that has child entities, deleting
 * the parent via the service layer must result in all descendant entities being removed
 * from the database. The DB ON DELETE CASCADE constraints handle the propagation.</p>
 *
 * <p><strong>Validates: Requirements 1.3, 2.3, 3.3, 4.3, 7.5</strong></p>
 */
class CascadeDeletionPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;
    private ZoneRepository zoneRepository;
    private ContextRepository contextRepository;

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
        solutionRepository = context.getBean(SolutionRepository.class);
        zoneRepository = context.getBean(ZoneRepository.class);
        contextRepository = context.getBean(ContextRepository.class);
        // Clean up all entities between tries (cascade from domains handles children)
        domainRepository.deleteAll();
    }

    /**
     * Domain-level cascade: deleting a domain removes all descendants.
     *
     * <p><strong>Validates: Requirements 1.3, 2.3, 3.3, 4.3, 7.5</strong></p>
     *
     * <p>For each try:</p>
     * <ol>
     *   <li>Create a domain, solution, zone, and context</li>
     *   <li>Verify all entities exist (including auto-created environment-global zone and DEFAULT context)</li>
     *   <li>Delete the domain</li>
     *   <li>Verify domain, solution, zone, context, and auto-created entities are all gone</li>
     * </ol>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-2")
    void domainDeletionCascadesToAllDescendants(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String solutionId,
            @ForAll("validIds") String zoneId,
            @ForAll("validIds") String contextId) {

        // 1. Create the hierarchy: domain → solution → zone → context
        DomainDTO domainDTO = domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        SolutionDTO solutionDTO = solutionService.createSolution(
            new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        // SolutionService auto-creates environment-global zone + DEFAULT context
        ZoneDTO zoneDTO = zoneService.createZone(domainId,
            new ZoneCreateRequest(solutionId, zoneId, "Test zone"));
        // ZoneService auto-creates DEFAULT context in the new zone
        ContextDTO contextDTO = zoneService.createContext(domainId, solutionId, zoneId,
            new ContextCreateRequest(contextId, "Test context"));

        // 2. Verify all entities exist
        assertThat(domainRepository.existsByDomainId(domainId)).isTrue();
        assertThat(solutionRepository.existsByDomainUuidAndSolutionId(domainDTO.id(), solutionId)).isTrue();
        assertThat(zoneRepository.existsBySolutionUuidAndZoneId(solutionDTO.id(), zoneId)).isTrue();
        assertThat(zoneRepository.existsBySolutionUuidAndZoneId(solutionDTO.id(), "environment-global")).isTrue();

        // Verify the user-created context exists
        var zoneEntity = zoneRepository.findBySolutionUuidAndZoneId(solutionDTO.id(), zoneId).orElseThrow();
        assertThat(contextRepository.existsByZoneUuidAndContextId(zoneEntity.getId(), contextId)).isTrue();

        // Verify the auto-created DEFAULT context in the user zone
        assertThat(contextRepository.existsByZoneUuidAndContextId(zoneEntity.getId(), "DEFAULT")).isTrue();

        // Verify the auto-created DEFAULT context in the environment-global zone
        var envGlobalZone = zoneRepository.findBySolutionUuidAndZoneId(solutionDTO.id(), "environment-global").orElseThrow();
        assertThat(contextRepository.existsByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT")).isTrue();

        // 3. Delete the domain
        domainService.deleteDomain(domainId);

        // 4. Verify all entities are gone (DB ON DELETE CASCADE)
        assertThat(domainRepository.existsByDomainId(domainId))
            .as("Domain must be deleted").isFalse();
        assertThat(solutionRepository.existsByDomainUuidAndSolutionId(domainDTO.id(), solutionId))
            .as("Solution must be cascade-deleted when domain is deleted").isFalse();
        assertThat(zoneRepository.existsBySolutionUuidAndZoneId(solutionDTO.id(), zoneId))
            .as("Zone must be cascade-deleted when domain is deleted").isFalse();
        assertThat(zoneRepository.existsBySolutionUuidAndZoneId(solutionDTO.id(), "environment-global"))
            .as("Auto-created environment-global zone must be cascade-deleted").isFalse();
        assertThat(contextRepository.findAll())
            .as("All contexts must be cascade-deleted when domain is deleted")
            .isEmpty();
    }

    /**
     * Solution-level cascade: deleting a solution removes its descendants but domain remains.
     *
     * <p><strong>Validates: Requirements 2.3, 3.3, 4.3, 7.5</strong></p>
     *
     * <p>For each try:</p>
     * <ol>
     *   <li>Create a domain, solution, zone, and context</li>
     *   <li>Delete the solution</li>
     *   <li>Verify solution, zone, context are gone but domain remains</li>
     * </ol>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-2")
    void solutionDeletionCascadesToZonesAndContexts(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String solutionId,
            @ForAll("validIds") String zoneId,
            @ForAll("validIds") String contextId) {

        // 1. Create the hierarchy: domain → solution → zone → context
        DomainDTO domainDTO = domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        SolutionDTO solutionDTO = solutionService.createSolution(
            new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        ZoneDTO zoneDTO = zoneService.createZone(domainId,
            new ZoneCreateRequest(solutionId, zoneId, "Test zone"));
        ContextDTO contextDTO = zoneService.createContext(domainId, solutionId, zoneId,
            new ContextCreateRequest(contextId, "Test context"));

        // Verify entities exist before deletion
        assertThat(solutionRepository.existsByDomainUuidAndSolutionId(domainDTO.id(), solutionId)).isTrue();
        assertThat(zoneRepository.existsBySolutionUuidAndZoneId(solutionDTO.id(), zoneId)).isTrue();

        // 2. Delete the solution
        solutionService.deleteSolution(domainId, solutionId);

        // 3. Verify solution and descendants are gone
        assertThat(solutionRepository.existsByDomainUuidAndSolutionId(domainDTO.id(), solutionId))
            .as("Solution must be deleted").isFalse();
        assertThat(zoneRepository.existsBySolutionUuidAndZoneId(solutionDTO.id(), zoneId))
            .as("Zone must be cascade-deleted when solution is deleted").isFalse();
        assertThat(zoneRepository.existsBySolutionUuidAndZoneId(solutionDTO.id(), "environment-global"))
            .as("Auto-created environment-global zone must be cascade-deleted").isFalse();
        assertThat(contextRepository.findAll())
            .as("All contexts must be cascade-deleted when solution is deleted")
            .isEmpty();

        // 4. Verify domain still exists
        assertThat(domainRepository.existsByDomainId(domainId))
            .as("Domain must remain after solution deletion").isTrue();
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
