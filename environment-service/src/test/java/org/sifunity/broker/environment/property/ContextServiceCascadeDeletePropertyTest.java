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
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.service.*;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;

/**
 * Property-based test for cascade delete removing all dependents.
 *
 * <p><strong>Feature: context-services-refactoring, Property 7: Cascade delete removes
 * all dependents</strong></p>
 *
 * <p>For any context_service that has K acl_entries and J provider_registry rows referencing
 * it, deleting that context_service SHALL result in all K acl_entries and all J
 * provider_registry rows being removed.</p>
 *
 * <p><strong>Validates: Requirements 5.6</strong></p>
 */
class ContextServiceCascadeDeletePropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private EnvironmentService environmentService;
    private AclService aclService;
    private ProviderService providerService;
    private ContextServiceService contextServiceService;
    private AclEntryRepository aclEntryRepository;
    private ProviderRegistrationRepository providerRegistrationRepository;
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
        providerService = context.getBean(ProviderService.class);
        contextServiceService = context.getBean(ContextServiceService.class);
        aclEntryRepository = context.getBean(AclEntryRepository.class);
        providerRegistrationRepository = context.getBean(ProviderRegistrationRepository.class);
        domainRepository = context.getBean(DomainRepository.class);
        domainRepository.deleteAll();
    }

    /**
     * Property 7: Cascade delete removes all dependents.
     *
     * <p><strong>Validates: Requirements 5.6</strong></p>
     */
    @Property(tries = 100)
    @Tag("Feature: context-services-refactoring, Property 7: Cascade delete removes all dependents")
    void cascadeDeleteRemovesAllDependents(
            @ForAll("aclCounts") int aclCount,
            @ForAll("hasProvider") boolean hasProvider) {

        // Create hierarchy: domain → solution → zone → context → environment(s)
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

        // Create context_service explicitly
        String serviceName = "CascadeSvc" + System.nanoTime();
        ContextServiceDTO csDto = contextServiceService.createContextService(contextUuid, serviceName, "OBJECT");
        UUID contextServiceId = csDto.id();

        // Create ACL entries referencing this context_service
        AccessType[] accessTypes = {AccessType.QUERY, AccessType.SUBSCRIBE, AccessType.CREATE, AccessType.DELETE, AccessType.UPDATE};
        for (int i = 0; i < aclCount; i++) {
            // Each ACL needs a unique environment + accessType combination
            SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AclApp" + i + "n" + System.nanoTime(), EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null)));
            UUID envId = envResponse.environment().id();

            aclService.createAclEntry(new AclEntryCreateRequest(
                envId, serviceName, zoneId, defaultContext.contextId(),
                accessTypes[i % accessTypes.length], AccessLevel.APPROVED));
        }

        // Create provider registration if requested
        if (hasProvider) {
            SifEnvironmentRegistrationResponse provEnvResponse = environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("ProvApp" + System.nanoTime(), EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null)));
            UUID provEnvId = provEnvResponse.environment().id();

            providerService.registerProvider(domainId, solutionId, new ProviderRegistrationRequest(new ProviderRegistrationBody(
                provEnvId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceId,
                ProtocolDTO.of("http://provider.example.com:8080/api"),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(EnumSet.of(Operation.QUERY))))))));
        }

        // Verify dependents exist before delete
        long aclCountBefore = aclEntryRepository.findAll().stream()
            .filter(e -> e.getContextServiceUuid().equals(contextServiceId))
            .count();
        assertThat(aclCountBefore).isEqualTo(aclCount);

        if (hasProvider) {
            assertThat(providerRegistrationRepository.findByContextServiceUuid(contextServiceId))
                .isPresent();
        }

        // Delete the context_service
        contextServiceService.deleteContextService(contextServiceId);

        // Verify all ACL entries referencing this context_service are gone
        long aclCountAfter = aclEntryRepository.findAll().stream()
            .filter(e -> e.getContextServiceUuid().equals(contextServiceId))
            .count();
        assertThat(aclCountAfter)
            .as("All ACL entries referencing deleted context_service must be cascade-deleted")
            .isZero();

        // Verify provider registration is gone
        if (hasProvider) {
            assertThat(providerRegistrationRepository.findByContextServiceUuid(contextServiceId))
                .as("Provider registration referencing deleted context_service must be cascade-deleted")
                .isEmpty();
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Integer> aclCounts() {
        return Arbitraries.integers().between(0, 4);
    }

    @Provide
    Arbitrary<Boolean> hasProvider() {
        return Arbitraries.of(true, false);
    }
}

