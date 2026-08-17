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

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for API contract preservation.
 *
 * <p><strong>Feature: uuid-foreign-key-hierarchy, Property 5: API contract preserves string IDs</strong></p>
 *
 * <p>For any entity created through the service layer using string IDs, the response DTO
 * must contain the same string IDs that were provided. The response DTOs must not contain
 * UUID foreign key fields ({@code domainUuid}, {@code solutionUuid}, {@code zoneUuid}).</p>
 *
 * <p><strong>Validates: Requirements 9.1, 9.2, 9.3</strong></p>
 */
class ApiContractPreservationPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private DomainRepository domainRepository;

    /** UUID FK field names that must NOT appear on any DTO. */
    private static final Set<String> FORBIDDEN_UUID_FK_FIELDS = Set.of(
        "domainUuid", "solutionUuid", "zoneUuid"
    );

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
     * Property 5: API contract preserves string IDs
     *
     * <p><strong>Validates: Requirements 9.1, 9.2, 9.3</strong></p>
     *
     * <p>For each try:</p>
     * <ol>
     *   <li>Create a domain with a random domainId via DomainService</li>
     *   <li>Create a solution with a random solutionId via SolutionService</li>
     *   <li>Verify the SolutionDTO contains {@code domainId} string matching the input</li>
     *   <li>Verify the SolutionDTO does NOT have a {@code domainUuid} field</li>
     *   <li>Create a zone with a random zoneId via ZoneService</li>
     *   <li>Verify the ZoneDTO contains {@code solutionId} string matching the input</li>
     *   <li>Verify the ZoneDTO does NOT have a {@code solutionUuid} field</li>
     *   <li>Create a context with a random contextId via ZoneService</li>
     *   <li>Verify the ContextDTO contains {@code zoneId} string matching the input</li>
     *   <li>Verify the ContextDTO does NOT have a {@code zoneUuid} field</li>
     * </ol>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-5")
    void apiContractPreservesStringIds(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String solutionId,
            @ForAll("validIds") String zoneId,
            @ForAll("validIds") String contextId) {

        // 1. Create a domain
        DomainDTO domainDTO = domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        assertThat(domainDTO.domainId()).isEqualTo(domainId);

        // 2. Create a solution in that domain
        SolutionDTO solutionDTO = solutionService.createSolution(
            new SolutionCreateRequest(domainId, solutionId, "Test solution"));

        // 3. Verify the SolutionDTO contains domainId string matching the input
        assertThat(solutionDTO.domainId())
            .as("SolutionDTO must contain domainId string matching the input")
            .isEqualTo(domainId);
        assertThat(solutionDTO.solutionId())
            .as("SolutionDTO must contain solutionId string matching the input")
            .isEqualTo(solutionId);

        // 4. Verify the SolutionDTO does NOT have UUID FK fields
        assertNoForbiddenUuidFkFields(SolutionDTO.class, "SolutionDTO");

        // 5. Create a zone in that solution
        ZoneDTO zoneDTO = zoneService.createZone(domainId,
            new ZoneCreateRequest(solutionId, zoneId, "Test zone"));

        // 6. Verify the ZoneDTO contains solutionId string matching the input
        assertThat(zoneDTO.solutionId())
            .as("ZoneDTO must contain solutionId string matching the input")
            .isEqualTo(solutionId);
        assertThat(zoneDTO.zoneId())
            .as("ZoneDTO must contain zoneId string matching the input")
            .isEqualTo(zoneId);

        // 7. Verify the ZoneDTO does NOT have UUID FK fields
        assertNoForbiddenUuidFkFields(ZoneDTO.class, "ZoneDTO");

        // 8. Create a context in that zone
        ContextDTO contextDTO = zoneService.createContext(domainId, solutionId, zoneId,
            new ContextCreateRequest(contextId, "Test context"));

        // 9. Verify the ContextDTO contains zoneId string matching the input
        assertThat(contextDTO.zoneId())
            .as("ContextDTO must contain zoneId string matching the input")
            .isEqualTo(zoneId);
        assertThat(contextDTO.contextId())
            .as("ContextDTO must contain contextId string matching the input")
            .isEqualTo(contextId);

        // 10. Verify the ContextDTO does NOT have UUID FK fields
        assertNoForbiddenUuidFkFields(ContextDTO.class, "ContextDTO");
    }

    /**
     * Asserts that the given record class does not contain any of the forbidden
     * UUID FK field names ({@code domainUuid}, {@code solutionUuid}, {@code zoneUuid}).
     * Since DTOs are Java records, their fields are defined as record components.
     */
    private void assertNoForbiddenUuidFkFields(Class<? extends Record> recordClass, String dtoName) {
        Set<String> fieldNames = Arrays.stream(recordClass.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());

        for (String forbidden : FORBIDDEN_UUID_FK_FIELDS) {
            assertThat(fieldNames)
                .as("%s must not contain UUID FK field '%s' — UUIDs are internal only", dtoName, forbidden)
                .doesNotContain(forbidden);
        }
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
