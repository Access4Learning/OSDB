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
import org.sifunity.broker.environment.service.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for listing context_services by zone.
 *
 * <p><strong>Feature: context-services-refactoring, Property 6: List by zone returns all
 * services across all contexts in zone</strong></p>
 *
 * <p>For any zone containing M contexts, where each context has some number of
 * context_service records totaling T, listing by that zone SHALL return exactly T
 * records spanning all M contexts.</p>
 *
 * <p><strong>Validates: Requirements 5.4</strong></p>
 */
class ContextServiceListByZonePropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private ContextServiceService contextServiceService;
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
        contextServiceService = context.getBean(ContextServiceService.class);
        domainRepository = context.getBean(DomainRepository.class);
        domainRepository.deleteAll();
    }

    /**
     * Property 6: List by zone returns all services across all contexts in zone.
     *
     * <p><strong>Validates: Requirements 5.4</strong></p>
     */
    @Property(tries = 100)
    @Tag("Feature: context-services-refactoring, Property 6: List by zone returns all services across all contexts in zone")
    void listByZoneReturnsAllServicesAcrossAllContexts(
            @ForAll("contextCounts") int extraContextCount,
            @ForAll("servicesPerContext") int servicesPerContext) {

        // Create hierarchy: domain → solution → zone
        String domainId = "d-" + System.nanoTime();
        String solutionId = "s-" + System.nanoTime();
        String zoneId = "z-" + System.nanoTime();

        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        ZoneDTO zone = zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));
        UUID zoneUuid = zone.id();

        // Zone auto-creates DEFAULT context; create additional contexts
        for (int i = 0; i < extraContextCount; i++) {
            String contextId = "ctx" + i + "n" + System.nanoTime();
            zoneService.createContext(domainId, solutionId, zoneId,
                new ContextCreateRequest(contextId, "Context " + i));
        }

        // Get all contexts in this zone
        List<ContextDTO> allContexts = zoneService.listContexts(domainId, solutionId, zoneId);
        int totalContexts = allContexts.size(); // DEFAULT + extraContextCount

        // Create services in each context
        String[] serviceTypes = {"UTILITY", "OBJECT", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE"};
        int totalServicesCreated = 0;
        for (ContextDTO ctx : allContexts) {
            for (int i = 0; i < servicesPerContext; i++) {
                String serviceName = "Svc" + totalServicesCreated + "n" + System.nanoTime();
                String serviceType = serviceTypes[totalServicesCreated % serviceTypes.length];
                contextServiceService.createContextService(ctx.id(), serviceName, serviceType);
                totalServicesCreated++;
            }
        }

        // List by zone
        List<ContextServiceDTO> listed = contextServiceService.listByZone(zoneUuid);

        // Verify total count equals sum across all contexts
        int expectedTotal = totalContexts * servicesPerContext;
        assertThat(listed)
            .as("List by zone must return exactly %d records (across %d contexts × %d services each)",
                expectedTotal, totalContexts, servicesPerContext)
            .hasSize(expectedTotal);

        // Verify all records belong to contexts in this zone
        List<UUID> contextUuids = allContexts.stream().map(ContextDTO::id).toList();
        assertThat(listed)
            .as("All listed records must belong to contexts within the queried zone")
            .allMatch(dto -> contextUuids.contains(dto.contextUuid()));
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Integer> contextCounts() {
        return Arbitraries.integers().between(0, 3);
    }

    @Provide
    Arbitrary<Integer> servicesPerContext() {
        return Arbitraries.integers().between(0, 3);
    }
}

