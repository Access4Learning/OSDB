// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.DuplicateQueryTokenException;
import org.sifunity.broker.environment.repository.XQueryTemplateRepository;
import org.sifunity.broker.environment.service.*;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the full Named Query Template (xQueryTemplate) CRUD lifecycle.
 *
 * <p>Tests the complete lifecycle: create → list → get by token → delete → verify gone,
 * as well as uniqueness enforcement and cross-solution token isolation.</p>
 *
 * <p>Uses Testcontainers with real PostgreSQL for full-fidelity testing.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5</strong></p>
 */
@SpringBootTest
@Testcontainers
class XQueryTemplateCrudIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private XQueryTemplateService xQueryTemplateService;

    @Autowired
    private XQueryTemplateRepository xQueryTemplateRepository;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private EnvironmentService environmentService;

    private TestEntityHierarchy hierarchy;
    private String solutionId;
    private UUID environmentId;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("secrets.backend.type", () -> "plaintext");
    }

    @BeforeAll
    static void beforeAll() {
        assertThat(postgres.isRunning()).isTrue();
    }

    @BeforeEach
    void setUp() {
        // Create Domain → Solution → Zone hierarchy
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        solutionId = hierarchy.getSolutionId();

        // Register an environment (provider) with defaultZoneId set
        String appName = "XQueryCrudTestApp-" + System.nanoTime();
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), solutionId, null));
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(hierarchy.getDomainId(), envRequest);
        environmentId = envResponse.environment().id();
    }

    /**
     * Tests the full CRUD lifecycle: create → list → get by token → delete → verify gone.
     *
     * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4</strong></p>
     */
    @Test
    void testFullCrudLifecycle() {
        String queryToken = "StudentSnapshot-" + System.nanoTime();

        // Step 1: Create template (Requirement 1.1)
        XQueryTemplateCreateRequest createRequest = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, "SINGULAR", "APPROVED", null,
                "Snapshot of student data", "SELECT * FROM students",
                new XQueryParametersDTO(List.of(new XQueryParameterDTO("gradeLevel", "string", null, "Grade filter", true))),
                "http://sif.org/StudentPersonal"));

        XQueryTemplateDTO created = xQueryTemplateService.create(hierarchy.getDomainId(), solutionId, environmentId, createRequest);

        assertThat(created.id()).isEqualTo(queryToken);
        assertThat(created.type()).isEqualTo("SINGULAR");
        assertThat(created.status()).isEqualTo("APPROVED");
        assertThat(created.description()).isEqualTo("Snapshot of student data");
        assertThat(created.script()).isEqualTo("SELECT * FROM students");
        assertThat(created.parameters().parameter()).hasSize(1);
        assertThat(created.parameters().parameter().get(0).name()).isEqualTo("gradeLevel");
        assertThat(created.returnType()).isEqualTo("http://sif.org/StudentPersonal");

        // Step 2: List templates (Requirement 1.2)
        List<XQueryTemplateDTO> listed = xQueryTemplateService.list(hierarchy.getDomainId(), solutionId);

        assertThat(listed).isNotEmpty();
        assertThat(listed).anyMatch(t -> t.id().equals(queryToken));

        // Step 3: Get by token (Requirement 1.3)
        XQueryTemplateDTO retrieved = xQueryTemplateService.getByToken(hierarchy.getDomainId(), solutionId, queryToken);

        assertThat(retrieved.id()).isEqualTo(queryToken);
        assertThat(retrieved.type()).isEqualTo("SINGULAR");
        assertThat(retrieved.status()).isEqualTo("APPROVED");
        assertThat(retrieved.description()).isEqualTo("Snapshot of student data");
        assertThat(retrieved.script()).isEqualTo("SELECT * FROM students");
        assertThat(retrieved.parameters().parameter()).hasSize(1);
        assertThat(retrieved.returnType()).isEqualTo("http://sif.org/StudentPersonal");

        // Step 4: Delete template (Requirement 1.4)
        xQueryTemplateService.delete(hierarchy.getDomainId(), solutionId, queryToken);

        // Step 5: Verify gone — getByToken should throw
        assertThatThrownBy(() -> xQueryTemplateService.getByToken(hierarchy.getDomainId(), solutionId, queryToken))
                .hasMessageContaining(queryToken);
    }

    /**
     * Tests that creating a duplicate token in the same solution results in rejection.
     *
     * <p><strong>Validates: Requirement 1.5</strong></p>
     */
    @Test
    void testDuplicateTokenInSameSolution_rejected() {
        String queryToken = "DuplicateTest-" + System.nanoTime();

        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, "FORMULA", "PENDING", null,
                "First template", null, new XQueryParametersDTO(List.of()), null));

        // First creation succeeds
        xQueryTemplateService.create(hierarchy.getDomainId(), solutionId, environmentId, request);

        // Second creation with same token in same solution → DuplicateQueryTokenException (409)
        XQueryTemplateCreateRequest duplicateRequest = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, "EXTENDED", "APPROVED", null,
                "Duplicate template", null, new XQueryParametersDTO(List.of()), null));

        assertThatThrownBy(() -> xQueryTemplateService.create(hierarchy.getDomainId(), solutionId, environmentId, duplicateRequest))
                .isInstanceOf(DuplicateQueryTokenException.class)
                .hasMessageContaining(queryToken);
    }

    /**
     * Tests that the same token in different solutions both succeed.
     *
     * <p><strong>Validates: Requirement 1.5 (uniqueness is per-solution)</strong></p>
     */
    @Test
    void testSameTokenInDifferentSolutions_bothSucceed() {
        String queryToken = "CrossSolutionToken-" + System.nanoTime();

        // Create second solution with its own hierarchy
        TestEntityHierarchy hierarchy2 = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        String solutionId2 = hierarchy2.getSolutionId();

        // Register environment in second solution
        String appName2 = "XQueryCrudTestApp2-" + System.nanoTime();
        EnvironmentRegistrationRequest envRequest2 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName2, EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy2.getZoneId()), solutionId2, null));
        SifEnvironmentRegistrationResponse envResponse2 = environmentService.registerEnvironment(hierarchy2.getDomainId(), envRequest2);
        UUID environmentId2 = envResponse2.environment().id();

        // Create template with same token in solution A
        XQueryTemplateCreateRequest requestA = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, "SINGULAR", "APPROVED", null,
                "Template in solution A", null, new XQueryParametersDTO(List.of()), null));
        XQueryTemplateDTO createdA = xQueryTemplateService.create(hierarchy.getDomainId(), solutionId, environmentId, requestA);

        // Create template with same token in solution B → should succeed
        XQueryTemplateCreateRequest requestB = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, "FORMULA", "PENDING", null,
                "Template in solution B", null, new XQueryParametersDTO(List.of()), null));
        XQueryTemplateDTO createdB = xQueryTemplateService.create(hierarchy2.getDomainId(), solutionId2, environmentId2, requestB);

        // Both should exist independently
        assertThat(createdA.id()).isEqualTo(queryToken);
        assertThat(createdB.id()).isEqualTo(queryToken);

        // Each solution returns its own version
        XQueryTemplateDTO retrievedA = xQueryTemplateService.getByToken(hierarchy.getDomainId(), solutionId, queryToken);
        XQueryTemplateDTO retrievedB = xQueryTemplateService.getByToken(hierarchy2.getDomainId(), solutionId2, queryToken);

        assertThat(retrievedA.description()).isEqualTo("Template in solution A");
        assertThat(retrievedB.description()).isEqualTo("Template in solution B");
    }


}
