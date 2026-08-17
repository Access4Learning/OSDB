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
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for listing children by parent string ID.
 *
 * <p><strong>Feature: uuid-foreign-key-hierarchy, Property 7: Listing by string ID returns correct children</strong></p>
 *
 * <p>For any parent entity, listing children by the parent's string ID must return
 * exactly the expected set of children. This ensures the string→UUID resolution
 * in list operations is correct.</p>
 *
 * <p><strong>Validates: Requirements 7.6, 7.7, 7.8</strong></p>
 */
class ListByStringIdPropertyTest {

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
     * Property 7.1: List solutions by domain string ID returns correct children.
     *
     * <p><strong>Validates: Requirements 7.6</strong></p>
     *
     * <p>Create a domain, then create 2-3 solutions in that domain.
     * List solutions by domainId via SolutionService.listSolutionsByDomain().
     * Verify the returned list contains exactly the created solutions (by solutionId).</p>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-7")
    void listSolutionsByDomainStringId(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String sol1,
            @ForAll("validIds") String sol2,
            @ForAll("validIds") String sol3) {

        // Ensure unique solution IDs
        Assume.that(!sol1.equals(sol2) && !sol1.equals(sol3) && !sol2.equals(sol3));

        // Create domain
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));

        // Create 3 solutions in that domain
        Set<String> createdSolutionIds = Set.of(sol1, sol2, sol3);
        for (String solId : createdSolutionIds) {
            solutionService.createSolution(new SolutionCreateRequest(domainId, solId, "Solution " + solId));
        }

        // List solutions by domain string ID
        List<SolutionDTO> listed = solutionService.listSolutionsByDomain(domainId);

        // Extract solutionIds from the listed results
        Set<String> listedSolutionIds = listed.stream()
            .map(SolutionDTO::solutionId)
            .collect(Collectors.toSet());

        // Verify the returned list contains exactly the created solutions
        assertThat(listedSolutionIds)
            .as("Listing solutions by domain string ID must return exactly the created solutions")
            .isEqualTo(createdSolutionIds);
    }

    /**
     * Property 7.2: List zones by solution string ID returns correct children.
     *
     * <p><strong>Validates: Requirements 7.7</strong></p>
     *
     * <p>Create a domain and solution, then create 2-3 zones in that solution.
     * List zones by solutionId via ZoneService.listZonesBySolution().
     * Verify the returned list contains exactly the created zones (by zoneId)
     * plus the auto-created "environment-global" zone.</p>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-7")
    void listZonesBySolutionStringId(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String solutionId,
            @ForAll("validIds") String zone1,
            @ForAll("validIds") String zone2,
            @ForAll("validIds") String zone3) {

        // Ensure unique zone IDs and none collide with auto-created "environment-global"
        Assume.that(!zone1.equals(zone2) && !zone1.equals(zone3) && !zone2.equals(zone3));
        Assume.that(!zone1.equals("environment-global") && !zone2.equals("environment-global") && !zone3.equals("environment-global"));

        // Create domain and solution
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Test solution"));

        // Create 3 zones in that solution
        Set<String> createdZoneIds = Set.of(zone1, zone2, zone3);
        for (String zId : createdZoneIds) {
            zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zId, "Zone " + zId));
        }

        // Expected zones: created zones + auto-created "environment-global" from solution bootstrap
        Set<String> expectedZoneIds = new HashSet<>(createdZoneIds);
        expectedZoneIds.add("environment-global");

        // List zones by solution string ID
        List<ZoneDTO> listed = zoneService.listZonesBySolution(domainId, solutionId);

        Set<String> listedZoneIds = listed.stream()
            .map(ZoneDTO::zoneId)
            .collect(Collectors.toSet());

        assertThat(listedZoneIds)
            .as("Listing zones by solution string ID must return created zones plus auto-created 'environment-global'")
            .isEqualTo(expectedZoneIds);
    }

    /**
     * Property 7.3: List contexts by zone string ID returns correct children.
     *
     * <p><strong>Validates: Requirements 7.8</strong></p>
     *
     * <p>Create a domain, solution, and zone, then create 2-3 contexts in that zone.
     * List contexts by zoneId via ZoneService.listContexts().
     * Verify the returned list contains exactly the created contexts (by contextId)
     * plus the auto-created "DEFAULT" context.</p>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-7")
    void listContextsByZoneStringId(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String solutionId,
            @ForAll("validIds") String zoneId,
            @ForAll("validIds") String ctx1,
            @ForAll("validIds") String ctx2,
            @ForAll("validIds") String ctx3) {

        // Ensure unique context IDs and none collide with auto-created "DEFAULT"
        Assume.that(!ctx1.equals(ctx2) && !ctx1.equals(ctx3) && !ctx2.equals(ctx3));
        Assume.that(!ctx1.equals("DEFAULT") && !ctx2.equals("DEFAULT") && !ctx3.equals("DEFAULT"));
        // Ensure zone doesn't collide with auto-created "environment-global"
        Assume.that(!zoneId.equals("environment-global"));

        // Create domain, solution, and zone
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));

        // Create 3 contexts in that zone
        Set<String> createdContextIds = Set.of(ctx1, ctx2, ctx3);
        for (String cId : createdContextIds) {
            zoneService.createContext(domainId, solutionId, zoneId, new ContextCreateRequest(cId, "Context " + cId));
        }

        // Expected contexts: created contexts + auto-created "DEFAULT" from zone creation
        Set<String> expectedContextIds = new HashSet<>(createdContextIds);
        expectedContextIds.add("DEFAULT");

        // List contexts by zone string ID
        List<ContextDTO> listed = zoneService.listContexts(domainId, solutionId, zoneId);

        Set<String> listedContextIds = listed.stream()
            .map(ContextDTO::contextId)
            .collect(Collectors.toSet());

        assertThat(listedContextIds)
            .as("Listing contexts by zone string ID must return created contexts plus auto-created 'DEFAULT'")
            .isEqualTo(expectedContextIds);
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
