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
import org.sifunity.broker.environment.dto.ContextCreateRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.exception.ZoneNotFoundException;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for non-existent parent string ID returning 404.
 *
 * <p><strong>Feature: uuid-foreign-key-hierarchy, Property 6: Non-existent parent string ID returns 404</strong></p>
 *
 * <p>For any entity creation request where the parent string ID does not correspond
 * to an existing parent entity, the service must throw an appropriate not-found exception
 * (resulting in HTTP 404).</p>
 *
 * <p><strong>Validates: Requirements 9.4</strong></p>
 */
class NonExistentParent404PropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private SolutionService solutionService;
    private ZoneService zoneService;
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
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeTry
    void setUp() {
        solutionService = context.getBean(SolutionService.class);
        zoneService = context.getBean(ZoneService.class);
        domainRepository = context.getBean(DomainRepository.class);
        // Clean up all entities between tries to ensure random IDs don't match existing entities
        domainRepository.deleteAll();
    }

    /**
     * Property 6.1: Non-existent domain → DomainNotFoundException
     *
     * <p><strong>Validates: Requirements 9.4</strong></p>
     *
     * <p>For any random domainId that doesn't exist, attempting to create a solution
     * in that domain must throw DomainNotFoundException.</p>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-6")
    void nonExistentDomainThrowsDomainNotFoundException(
            @ForAll("nonExistentIds") String domainId) {

        SolutionCreateRequest request = new SolutionCreateRequest(domainId, "test-solution", "test");

        assertThatThrownBy(() -> solutionService.createSolution(request))
            .isInstanceOf(DomainNotFoundException.class);
    }

    /**
     * Property 6.2: Non-existent solution → SolutionNotFoundException
     *
     * <p><strong>Validates: Requirements 9.4</strong></p>
     *
     * <p>For any random solutionId that doesn't exist, attempting to create a zone
     * in that solution must throw SolutionNotFoundException.</p>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-6")
    void nonExistentSolutionThrowsSolutionNotFoundException(
            @ForAll("nonExistentIds") String solutionId) {

        ZoneCreateRequest request = new ZoneCreateRequest(solutionId, "test-zone", "test");

        assertThatThrownBy(() -> zoneService.createZone("nonexistent-domain", request))
            .isInstanceOf(SolutionNotFoundException.class);
    }

    /**
     * Property 6.3: Non-existent zone → ZoneNotFoundException
     *
     * <p><strong>Validates: Requirements 9.4</strong></p>
     *
     * <p>For any random zoneId that doesn't exist, attempting to create a context
     * in that zone must throw an appropriate not-found exception.
     * With solution-scoped lookups, a non-existent solutionId throws SolutionNotFoundException.</p>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-6")
    void nonExistentZoneThrowsZoneNotFoundException(
            @ForAll("nonExistentIds") String zoneId) {

        ContextCreateRequest request = new ContextCreateRequest("test-context", "test");

        assertThatThrownBy(() -> zoneService.createContext("nonexistent-domain", "nonexistent-solution", zoneId, request))
            .isInstanceOf(SolutionNotFoundException.class);
    }

    // ========== Arbitraries ==========

    /**
     * Generates random non-existent IDs that follow the valid naming pattern
     * (alphanumeric start/end, alphanumeric/underscore/hyphen middle) but
     * are prefixed with "nonexistent-" to ensure they don't match any
     * bootstrap or previously created entities.
     */
    @Provide
    Arbitrary<String> nonExistentIds() {
        Arbitrary<String> suffix = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofMinLength(3)
            .ofMaxLength(15);

        return suffix.map(s -> "nonexistent-" + s);
    }
}
