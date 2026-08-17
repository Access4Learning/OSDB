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
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.service.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;

/**
 * Property-based test for Provider create/list round-trip preserving external contract.
 *
 * <p><strong>Feature: context-services-refactoring, Property 2: Provider create/list round-trip
 * preserves external contract</strong></p>
 *
 * <p>For any valid provider registration inputs with existing zones/contexts, creating a
 * provider registration and then listing registrations SHALL return a DTO containing the
 * original serviceName, zoneId, and contextId values.</p>
 *
 * <p><strong>Validates: Requirements 6.3, 6.5</strong></p>
 */
class ProviderRoundTripPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private EnvironmentService environmentService;
    private ProviderService providerService;
    private ContextServiceService contextServiceService;
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
        providerService = context.getBean(ProviderService.class);
        contextServiceService = context.getBean(ContextServiceService.class);
        contextServiceRepository = context.getBean(ContextServiceRepository.class);
        domainRepository = context.getBean(DomainRepository.class);
        domainRepository.deleteAll();
    }

    /**
     * Property 2: Provider create/list round-trip preserves external contract.
     *
     * <p><strong>Validates: Requirements 6.3, 6.5</strong></p>
     */
    @Property(tries = 100)
    @Tag("Feature: context-services-refactoring, Property 2: Provider create/list round-trip preserves external contract")
    void providerCreateListRoundTripPreservesExternalContract(
            @ForAll("serviceNames") String serviceName,
            @ForAll("serviceTypes") ServiceType serviceType) {

        // Create hierarchy: domain → solution → zone → context → environment
        String domainId = "d-" + System.nanoTime();
        String solutionId = "s-" + System.nanoTime();
        String zoneId = "z-" + System.nanoTime();

        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));

        // Get the auto-created DEFAULT context
        List<ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);
        ContextDTO defaultContext = contexts.stream()
            .filter(c -> "DEFAULT".equals(c.contextId()))
            .findFirst()
            .orElseThrow();
        String contextId = defaultContext.contextId();
        UUID contextUuid = defaultContext.id();

        // Create BROKERED environment
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
            domainId, new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("App-" + System.nanoTime(), EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null)));
        UUID environmentId = envResponse.environment().id();

        // Create provider registration (Requirement 6.3)
        String endpoint = "http://provider.example.com:" + (8000 + Math.abs(serviceName.hashCode() % 1000)) + "/api";
        List<SupportedRequestType> supportedRequestTypes = List.of(
            new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(EnumSet.of(Operation.CREATE, Operation.QUERY)))
        );

        // Resolve context_service UUID for the provider registration
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(contextUuid, serviceName, serviceType.name());

        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                serviceType,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of(endpoint),
                SupportedRequestsDTO.of(supportedRequestTypes)));
        ProviderRegistrationDTO created = providerService.registerProvider(domainId, solutionId, request);

        // List provider registrations for environment
        List<ProviderRegistrationDTO> listed = providerService.listProviderRegistrations(environmentId);

        // Verify round-trip: DTO contains original serviceName, zoneId, contextId
        assertThat(listed).hasSize(1);
        ProviderRegistrationDTO retrieved = listed.get(0);
        assertThat(retrieved.serviceName())
            .as("Listed provider must contain original serviceName")
            .isEqualTo(serviceName);
        assertThat(retrieved.zoneId())
            .as("Listed provider must contain original zoneId")
            .isEqualTo(zoneId);
        assertThat(retrieved.contextId())
            .as("Listed provider must contain original contextId")
            .isEqualTo(contextId);
        assertThat(retrieved.serviceType())
            .as("Listed provider must contain original serviceType")
            .isEqualTo(serviceType);
        // Note: providerName was removed; consumerName mapping will be done in task 4.3

        // Verify context_service record exists (Requirement 6.5)
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
    Arbitrary<ServiceType> serviceTypes() {
        return Arbitraries.of(ServiceType.values());
    }

    @Provide
    Arbitrary<String> providerNames() {
        Arbitrary<Character> firstChar = Arbitraries.chars().range('A', 'Z');
        Arbitrary<String> rest = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .ofMinLength(2)
            .ofMaxLength(20);
        return Combinators.combine(firstChar, rest).as((f, r) -> f + r);
    }
}
