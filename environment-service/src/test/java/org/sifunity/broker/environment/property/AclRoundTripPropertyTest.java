// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.service.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for ACL create/list round-trip preserving external contract.
 *
 * <p><strong>Feature: context-services-refactoring, Property 1: ACL create/list round-trip
 * preserves external contract</strong></p>
 *
 * <p>For any valid (serviceName, contextUuid, accessType, accessLevel) tuple and any existing
 * environment and context, creating an ACL entry and then listing ACL entries for that
 * environment SHALL return a DTO containing the original serviceName and contextUuid values.
 * Additionally, a context_service record SHALL exist for that (contextUuid, serviceName) pair.</p>
 *
 * <p><strong>Validates: Requirements 6.1, 6.2, 6.4</strong></p>
 */
class AclRoundTripPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private EnvironmentService environmentService;
    private AclService aclService;
    private ContextServiceRepository contextServiceRepository;
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
        if (context != null) context.close();
        if (postgres != null) postgres.stop();
    }

    @BeforeTry
    void setUp() {
        domainService = context.getBean(DomainService.class);
        solutionService = context.getBean(SolutionService.class);
        zoneService = context.getBean(ZoneService.class);
        environmentService = context.getBean(EnvironmentService.class);
        aclService = context.getBean(AclService.class);
        contextServiceRepository = context.getBean(ContextServiceRepository.class);
        domainRepository = context.getBean(DomainRepository.class);
        domainRepository.deleteAll();
    }

    /**
     * Property 1: ACL create/list round-trip preserves external contract.
     *
     * <p><strong>Validates: Requirements 6.1, 6.2, 6.4</strong></p>
     */
    @Property(tries = 100)
    @Tag("Feature: context-services-refactoring, Property 1: ACL create/list round-trip preserves external contract")
    void aclCreateListRoundTripPreservesExternalContract(
            @ForAll("serviceNames") String serviceName,
            @ForAll("accessTypes") AccessType accessType,
            @ForAll("accessLevels") AccessLevel accessLevel) {

        // Create hierarchy: domain → solution → zone → context → environment
        String domainId = "d-" + System.nanoTime();
        String solutionId = "s-" + System.nanoTime();
        String zoneId = "z-" + System.nanoTime();

        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        ZoneDTO zone = zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));

        // Get the auto-created DEFAULT context UUID
        List<ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);
        ContextDTO defaultContext = contexts.stream()
            .filter(c -> "DEFAULT".equals(c.contextId()))
            .findFirst()
            .orElseThrow();
        UUID contextUuid = defaultContext.id();

        // Create environment
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
            domainId, new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("App-" + System.nanoTime(), EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null)));
        UUID environmentId = envResponse.environment().id();

        // Create ACL entry with serviceName and zoneId/contextId
        AclEntryCreateRequest aclRequest = new AclEntryCreateRequest(
            environmentId, serviceName, zoneId, defaultContext.contextId(), accessType, accessLevel);
        AclEntryDTO created = aclService.createAclEntry(aclRequest);

        // List ACL entries for environment (Requirement 6.2)
        List<AclEntryDTO> listed = aclService.listAclEntries(environmentId);

        // Verify round-trip: DTO contains original serviceName and zoneId/contextId
        assertThat(listed).hasSize(1);
        AclEntryDTO retrieved = listed.get(0);
        assertThat(retrieved.serviceName())
            .as("Listed ACL entry must contain original serviceName")
            .isEqualTo(serviceName);
        assertThat(retrieved.zoneId())
            .as("Listed ACL entry must contain original zoneId")
            .isEqualTo(zoneId);
        assertThat(retrieved.accessType())
            .as("Listed ACL entry must contain original accessType")
            .isEqualTo(accessType);
        assertThat(retrieved.accessLevel())
            .as("Listed ACL entry must contain original accessLevel")
            .isEqualTo(accessLevel);

        // Verify context_service record exists (Requirement 6.4)
        assertThat(contextServiceRepository.existsByContextUuidAndServiceName(contextUuid, serviceName))
            .as("A context_service record must exist for (contextUuid, serviceName)")
            .isTrue();
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> serviceNames() {
        Arbitrary<Character> firstChar = Arbitraries.chars().range('a', 'z').range('A', 'Z');
        Arbitrary<String> rest = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .ofMinLength(1)
            .ofMaxLength(30);
        return Combinators.combine(firstChar, rest).as((f, r) -> f + r);
    }

    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }

    @Provide
    Arbitrary<AccessLevel> accessLevels() {
        return Arbitraries.of(AccessLevel.values());
    }
}
