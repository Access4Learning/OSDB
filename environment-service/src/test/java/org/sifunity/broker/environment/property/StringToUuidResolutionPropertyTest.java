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
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for string-to-UUID resolution round-trip.
 *
 * <p><strong>Feature: uuid-foreign-key-hierarchy, Property 4: String-to-UUID resolution round-trip</strong></p>
 *
 * <p>For any entity creation request that provides a parent string ID (domainId, solutionId,
 * or zoneId), the service layer must resolve the string to the parent's UUID PK and persist
 * it as the child's UUID FK. When the child entity is subsequently retrieved, the UUID FK
 * must resolve back to the original parent string ID.</p>
 *
 * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4, 5.1, 5.2, 5.3, 5.4</strong></p>
 */
class StringToUuidResolutionPropertyTest {

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
     * Property 4: String-to-UUID resolution round-trip
     *
     * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4, 5.1, 5.2, 5.3, 5.4</strong></p>
     *
     * <p>For each try:</p>
     * <ol>
     *   <li>Create a domain with a random domainId via DomainService</li>
     *   <li>Create a solution with a random solutionId in that domain via SolutionService</li>
     *   <li>Verify the Solution entity's domainUuid equals the Domain's UUID PK</li>
     *   <li>Retrieve the solution via SolutionService.getSolution() and verify the DTO's domainId matches</li>
     *   <li>Create a zone with a random zoneId in that solution via ZoneService</li>
     *   <li>Verify the Zone entity's solutionUuid equals the Solution's UUID PK</li>
     *   <li>Retrieve zones via ZoneService.listZonesBySolution() and verify the DTO's solutionId matches</li>
     *   <li>Create a context with a random contextId in that zone via ZoneService</li>
     *   <li>Verify the Context entity's zoneUuid equals the Zone's UUID PK</li>
     *   <li>Retrieve contexts via ZoneService.listContexts() and verify the DTO's zoneId matches</li>
     * </ol>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-4")
    void stringToUuidResolutionRoundTrip(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String solutionId,
            @ForAll("validIds") String zoneId,
            @ForAll("validIds") String contextId) {

        // 1. Create a domain
        DomainDTO domainDTO = domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        assertThat(domainDTO.id()).isNotNull();

        // 2. Create a solution in that domain
        SolutionDTO solutionDTO = solutionService.createSolution(
            new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        assertThat(solutionDTO.id()).isNotNull();

        // 3. Verify the Solution entity's domainUuid equals the Domain's UUID PK
        Solution solutionEntity = solutionRepository.findByDomainUuidAndSolutionId(domainDTO.id(), solutionId).orElseThrow();
        assertThat(solutionEntity.getDomainUuid())
            .as("Solution's domainUuid must equal the Domain's UUID PK")
            .isEqualTo(domainDTO.id());

        // 4. Retrieve the solution and verify the DTO's domainId matches the original string
        SolutionDTO retrievedSolution = solutionService.getSolution(domainId, solutionId);
        assertThat(retrievedSolution.domainId())
            .as("Retrieved solution DTO's domainId must match the original domain string ID")
            .isEqualTo(domainId);

        // 5. Create a zone in that solution
        ZoneDTO zoneDTO = zoneService.createZone(domainId,
            new ZoneCreateRequest(solutionId, zoneId, "Test zone"));
        assertThat(zoneDTO.id()).isNotNull();

        // 6. Verify the Zone entity's solutionUuid equals the Solution's UUID PK
        Zone zoneEntity = zoneRepository.findBySolutionUuidAndZoneId(solutionEntity.getId(), zoneId).orElseThrow();
        assertThat(zoneEntity.getSolutionUuid())
            .as("Zone's solutionUuid must equal the Solution's UUID PK")
            .isEqualTo(solutionDTO.id());

        // 7. Retrieve zones and verify the DTO's solutionId matches
        List<ZoneDTO> zones = zoneService.listZonesBySolution(domainId, solutionId);
        ZoneDTO matchingZone = zones.stream()
            .filter(z -> z.zoneId().equals(zoneId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Zone " + zoneId + " not found in list"));
        assertThat(matchingZone.solutionId())
            .as("Listed zone DTO's solutionId must match the original solution string ID")
            .isEqualTo(solutionId);

        // 8. Create a context in that zone
        ContextDTO contextDTO = zoneService.createContext(domainId, solutionId, zoneId,
            new ContextCreateRequest(contextId, "Test context"));
        assertThat(contextDTO.id()).isNotNull();

        // 9. Verify the Context entity's zoneUuid equals the Zone's UUID PK
        Context contextEntity = contextRepository.findByZoneUuid(zoneEntity.getId()).stream()
            .filter(c -> c.getContextId().equals(contextId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Context " + contextId + " not found"));
        assertThat(contextEntity.getZoneUuid())
            .as("Context's zoneUuid must equal the Zone's UUID PK")
            .isEqualTo(zoneDTO.id());

        // 10. Retrieve contexts and verify the DTO's zoneId matches
        List<ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);
        ContextDTO matchingContext = contexts.stream()
            .filter(c -> c.contextId().equals(contextId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Context " + contextId + " not found in list"));
        assertThat(matchingContext.zoneId())
            .as("Listed context DTO's zoneId must match the original zone string ID")
            .isEqualTo(zoneId);
    }

    // ========== Arbitraries ==========

    /**
     * Generates valid IDs matching the naming pattern:
     * starts with alphanumeric, contains alphanumeric, underscore, or hyphen,
     * ends with alphanumeric. Each ID is prefixed with a random segment to
     * ensure uniqueness across tries.
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
