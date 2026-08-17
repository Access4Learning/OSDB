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
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.service.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for single-provider constraint enforcement.
 *
 * <p><strong>Feature: context-services-refactoring, Property 8: Single-provider constraint
 * enforcement</strong></p>
 *
 * <p>For any context_service_uuid, if one environment already has a PROVIDE ACL entry for
 * that context_service_uuid, then a second environment attempting to create a PROVIDE entry
 * for the same context_service_uuid SHALL be rejected with a conflict error.</p>
 *
 * <p><strong>Validates: Requirements 4.3</strong></p>
 */
class SingleProviderConstraintPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private EnvironmentService environmentService;
    private AclService aclService;
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
        domainRepository = context.getBean(DomainRepository.class);
        domainRepository.deleteAll();
    }

    /**
     * Property 8: Single-provider constraint enforcement.
     *
     * <p><strong>Validates: Requirements 4.3</strong></p>
     */
    @Property(tries = 100)
    @Tag("Feature: context-services-refactoring, Property 8: Single-provider constraint enforcement")
    void singleProviderConstraintEnforced(
            @ForAll("serviceNames") String serviceName) {

        // Create hierarchy: domain → solution → zone → context
        String domainId = "d-" + System.nanoTime();
        String solutionId = "s-" + System.nanoTime();
        String zoneId = "z-" + System.nanoTime();

        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));

        // Get the auto-created DEFAULT context UUID
        List<ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);
        ContextDTO defaultContext = contexts.stream()
            .filter(c -> "DEFAULT".equals(c.contextId()))
            .findFirst()
            .orElseThrow();
        UUID contextUuid = defaultContext.id();

        // Create first environment and register as PROVIDE — should succeed
        SifEnvironmentRegistrationResponse env1Response = environmentService.registerEnvironment(
            domainId,
            new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("Env1App" + System.nanoTime(), EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null)));
        UUID env1Id = env1Response.environment().id();

        aclService.createAclEntry(new AclEntryCreateRequest(
            env1Id, serviceName, zoneId, defaultContext.contextId(), AccessType.PROVIDE, AccessLevel.APPROVED));

        // Create second environment and attempt PROVIDE for same service/context — must fail
        SifEnvironmentRegistrationResponse env2Response = environmentService.registerEnvironment(
            domainId,
            new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("Env2App" + System.nanoTime(), EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null)));
        UUID env2Id = env2Response.environment().id();

        assertThatThrownBy(() -> aclService.createAclEntry(new AclEntryCreateRequest(
                env2Id, serviceName, zoneId, defaultContext.contextId(), AccessType.PROVIDE, AccessLevel.APPROVED)))
            .as("Second PROVIDE for same context_service must be rejected with conflict")
            .isInstanceOf(SifBrokerException.class)
            .hasMessageContaining("provider already exists");
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
}
