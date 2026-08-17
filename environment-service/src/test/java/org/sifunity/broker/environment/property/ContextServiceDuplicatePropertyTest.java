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
import org.sifunity.broker.environment.exception.DuplicateContextServiceException;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.service.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for duplicate context_service rejection.
 *
 * <p><strong>Feature: context-services-refactoring, Property 4: Duplicate context_service
 * rejection</strong></p>
 *
 * <p>For any (contextUuid, serviceName) pair, if a context_service already exists with that
 * pair, attempting to create another with the same pair SHALL be rejected with HTTP 409
 * Conflict regardless of the serviceType value.</p>
 *
 * <p><strong>Validates: Requirements 5.2</strong></p>
 */
class ContextServiceDuplicatePropertyTest {

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
     * Property 4: Duplicate context_service rejection.
     *
     * <p><strong>Validates: Requirements 5.2</strong></p>
     */
    @Property(tries = 100)
    @Tag("Feature: context-services-refactoring, Property 4: Duplicate context_service rejection")
    void duplicateContextServiceIsRejected(
            @ForAll("serviceNames") String serviceName,
            @ForAll("serviceTypeStrings") String firstServiceType,
            @ForAll("serviceTypeStrings") String secondServiceType) {

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

        // First creation succeeds
        ContextServiceDTO created = contextServiceService.createContextService(
            contextUuid, serviceName, firstServiceType);
        assertThat(created).isNotNull();

        // Second creation with same (contextUuid, serviceName) must be rejected
        assertThatThrownBy(() -> contextServiceService.createContextService(
            contextUuid, serviceName, secondServiceType))
            .as("Duplicate (contextUuid, serviceName) must be rejected regardless of serviceType")
            .isInstanceOf(DuplicateContextServiceException.class);
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
    Arbitrary<String> serviceTypeStrings() {
        return Arbitraries.of("UTILITY", "OBJECT", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE");
    }
}
