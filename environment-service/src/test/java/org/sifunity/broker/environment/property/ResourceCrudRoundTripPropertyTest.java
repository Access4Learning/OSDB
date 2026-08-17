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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for resource CRUD round trip with URL prefix.
 *
 * <p><strong>Property 11: Resource CRUD Round Trip With URL Prefix</strong></p>
 *
 * <p>For any valid domainId, solutionId, and resource data, creating a resource
 * via POST /{domainId}/{solutionId}/{resource} and then retrieving it via
 * GET /{domainId}/{solutionId}/{resource}/{id} shall return data equivalent
 * to what was created.</p>
 *
 * <p><strong>Validates: Requirements 11.8</strong></p>
 */
class ResourceCrudRoundTripPropertyTest {

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
     * Property 11: Resource CRUD Round Trip With URL Prefix
     *
     * <p><strong>Validates: Requirements 11.8</strong></p>
     *
     * <p>For any valid domainId, solutionId, and environment data, creating an
     * environment and then retrieving it returns equivalent data. This tests
     * the round-trip property through the service layer (which is what the
     * controller delegates to when handling /{domainId}/{solutionId}/environments).</p>
     */
    @Property(tries = 10)
    @Tag("domain-solution-url-prefix-property-11")
    void resourceCrudRoundTripWithUrlPrefix(
            @ForAll("validIds") String domainId,
            @ForAll("validIds") String solutionId,
            @ForAll("validIds") String zoneId,
            @ForAll("appNames") String appName,
            @ForAll("environmentTypes") EnvironmentType envType) {

        // Set up the hierarchy: domain → solution → zone
        domainService.createDomain(new DomainCreateRequest(domainId, "Round-trip domain"));
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, null));

        // CREATE (simulates POST /{domainId}/{solutionId}/environments/environment)
        SifEnvironmentRegistrationResponse created = environmentService.registerEnvironment(
            domainId, new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, envType, DefaultZoneDTO.of(zoneId), solutionId, null)));

        assertThat(created).isNotNull();
        assertThat(created.environment()).isNotNull();
        assertThat(created.environment().id()).isNotNull();

        // RETRIEVE (simulates GET /{domainId}/{solutionId}/environments/{id})
        SifEnvironmentResponse retrieved = environmentService.getEnvironment(created.environment().id());

        // Round-trip: core fields must match
        assertThat(retrieved.id())
            .as("ID must survive round-trip")
            .isEqualTo(created.environment().id());

        assertThat(retrieved.consumerName())
            .as("consumerName must survive round-trip")
            .isEqualTo(appName);

        assertThat(retrieved.type())
            .as("type must survive round-trip")
            .isEqualTo(envType);

        assertThat(retrieved.solutionId())
            .as("solutionId must survive round-trip")
            .isEqualTo(solutionId);

        assertThat(retrieved.sessionToken())
            .as("sessionToken must survive round-trip")
            .isEqualTo(created.environment().sessionToken());

        assertThat(retrieved.fingerprint())
            .as("fingerprint must survive round-trip")
            .isEqualTo(created.environment().fingerprint());

        assertThat(retrieved.defaultZone())
            .as("defaultZone must survive round-trip")
            .isNotNull();

        assertThat(DefaultZoneDTO.idOf(retrieved.defaultZone()))
            .as("defaultZone.id must match the zone used at creation")
            .isEqualTo(zoneId);
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
            .map(s -> "rtrip" + s);
    }

    @Provide
    Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(EnvironmentType.DIRECT, EnvironmentType.BROKERED);
    }
}
