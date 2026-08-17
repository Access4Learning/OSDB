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
 * Property-based test for listing context_services by context.
 *
 * <p><strong>Feature: context-services-refactoring, Property 5: List by context returns
 * exactly matching records</strong></p>
 *
 * <p>For any context with N context_service records (where N >= 0), listing by that
 * contextUuid SHALL return exactly those N records and no others.</p>
 *
 * <p><strong>Validates: Requirements 5.3</strong></p>
 */
class ContextServiceListByContextPropertyTest {

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
     * Property 5: List by context returns exactly matching records.
     *
     * <p><strong>Validates: Requirements 5.3</strong></p>
     */
    @Property(tries = 100)
    @Tag("Feature: context-services-refactoring, Property 5: List by context returns exactly matching records")
    void listByContextReturnsExactlyMatchingRecords(
            @ForAll("serviceCounts") int serviceCount) {

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

        // Create N context_services for this context
        String[] serviceTypes = {"UTILITY", "OBJECT", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE"};
        for (int i = 0; i < serviceCount; i++) {
            String serviceName = "Svc" + i + "n" + System.nanoTime();
            String serviceType = serviceTypes[i % serviceTypes.length];
            contextServiceService.createContextService(contextUuid, serviceName, serviceType);
        }

        // List by context
        List<ContextServiceDTO> listed = contextServiceService.listByContext(contextUuid);

        // Verify exact count
        assertThat(listed)
            .as("List by context must return exactly %d records", serviceCount)
            .hasSize(serviceCount);

        // Verify all records belong to this context
        assertThat(listed)
            .as("All listed records must belong to the queried context")
            .allMatch(dto -> dto.contextUuid().equals(contextUuid));

        // Verify all records have unique IDs
        List<UUID> ids = listed.stream().map(ContextServiceDTO::id).toList();
        assertThat(ids)
            .as("All listed records must have unique IDs")
            .doesNotHaveDuplicates();
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Integer> serviceCounts() {
        return Arbitraries.integers().between(0, 5);
    }
}

