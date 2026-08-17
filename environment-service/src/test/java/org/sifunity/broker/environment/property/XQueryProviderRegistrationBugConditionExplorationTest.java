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
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.XQueryTemplateRepository;
import org.sifunity.broker.environment.service.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test: Template Create/Delete Auto-Creates/Removes ProviderRegistration.
 *
 * <p><strong>Property:</strong> For any valid XQueryTemplateCreateRequest, calling
 * {@code XQueryTemplateService.create()} SHALL NOT create any ProviderRegistration entity.
 * For any valid delete operation, calling {@code XQueryTemplateService.delete()} SHALL NOT
 * remove any ProviderRegistration entity.</p>
 *
 * <p><strong>Bug Condition:</strong> On UNFIXED code, this test FAILS because create()
 * invokes createProviderRegistration() and delete() invokes removeProviderRegistration().</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 2.1, 2.4</strong></p>
 */
class XQueryProviderRegistrationBugConditionExplorationTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private XQueryTemplateService xQueryTemplateService;
    private XQueryTemplateRepository xQueryTemplateRepository;
    private ProviderRegistrationRepository providerRegistrationRepository;
    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private EnvironmentService environmentService;

    private String domainId;
    private String solutionId;
    private UUID environmentId;

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
                "--server.port=0",
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
        xQueryTemplateService = context.getBean(XQueryTemplateService.class);
        xQueryTemplateRepository = context.getBean(XQueryTemplateRepository.class);
        providerRegistrationRepository = context.getBean(ProviderRegistrationRepository.class);
        domainService = context.getBean(DomainService.class);
        solutionService = context.getBean(SolutionService.class);
        zoneService = context.getBean(ZoneService.class);
        environmentService = context.getBean(EnvironmentService.class);

        // Clean state before each try
        xQueryTemplateRepository.deleteAll();

        // Set up infrastructure: domain → solution → zone → environment
        domainId = "bugcond-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "bugcond-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        String zoneId = "bugcond-zone-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone for bug condition"));

        String appName = "BugCondApp-" + System.nanoTime();
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null));
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(domainId, envRequest);
        environmentId = envResponse.environment().id();
    }

    /**
     * Property 1 (Create): Template creation SHALL NOT create any ProviderRegistration.
     *
     * <p>Bug condition: On unfixed code, create() calls createProviderRegistration()
     * which adds a ProviderRegistration with endpoint=null. This test asserts the
     * CORRECT behavior (no side effect) and is expected to FAIL on unfixed code.</p>
     *
     * <p><strong>Validates: Requirements 1.1, 1.2, 2.1</strong></p>
     */
    @Property(tries = 20)
    @Tag("xquery-provider-registration-fix")
    @Tag("bug-condition-create")
    void createTemplateShouldNotCreateProviderRegistration(
            @ForAll("validQueryTokens") String queryToken,
            @ForAll("validTypes") String type,
            @ForAll("validStatuses") String status) {

        // Record provider registration count before create
        long countBefore = providerRegistrationRepository.count();

        // Create template via service
        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, type, status, null, "Test description", null, new XQueryParametersDTO(List.of()), null));

        xQueryTemplateService.create(domainId, solutionId, environmentId, request);

        // Assert: provider registration count MUST be unchanged (no auto-creation)
        long countAfter = providerRegistrationRepository.count();
        assertThat(countAfter)
                .as("ProviderRegistration count must not change after template create " +
                    "(token='%s', type='%s', status='%s')", queryToken, type, status)
                .isEqualTo(countBefore);
    }

    /**
     * Property 1 (Delete): Template deletion SHALL NOT remove any ProviderRegistration.
     *
     * <p>Bug condition: On unfixed code, delete() calls removeProviderRegistration()
     * which removes ProviderRegistration records. This test asserts the CORRECT
     * behavior (no side effect) and is expected to FAIL on unfixed code.</p>
     *
     * <p><strong>Validates: Requirements 1.3, 2.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("xquery-provider-registration-fix")
    @Tag("bug-condition-delete")
    void deleteTemplateShouldNotRemoveProviderRegistration(
            @ForAll("validQueryTokens") String queryToken,
            @ForAll("validTypes") String type,
            @ForAll("validStatuses") String status) {

        // Create template first (this will auto-create a ProviderRegistration on unfixed code)
        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, type, status, null, "Test description", null, new XQueryParametersDTO(List.of()), null));

        xQueryTemplateService.create(domainId, solutionId, environmentId, request);

        // Record provider registration count before delete
        long countBefore = providerRegistrationRepository.count();

        // Delete template via service
        xQueryTemplateService.delete(domainId, solutionId, queryToken);

        // Assert: provider registration count MUST be unchanged (no auto-removal)
        long countAfter = providerRegistrationRepository.count();
        assertThat(countAfter)
                .as("ProviderRegistration count must not change after template delete " +
                    "(token='%s', type='%s', status='%s')", queryToken, type, status)
                .isEqualTo(countBefore);
    }

    // ========== Arbitraries ==========

    /**
     * Generates valid query tokens: non-empty alphanumeric strings.
     * Each token is unique via nanoTime suffix.
     */
    @Provide
    Arbitrary<String> validQueryTokens() {
        Arbitrary<String> prefixes = Arbitraries.of(
                "StudentGPA", "AttendanceReport", "EnrollmentCheck",
                "GradebookLookup", "StaffDirectory", "CourseSchedule",
                "TranscriptQuery", "DemographicSearch", "BehaviorLog",
                "HealthRecord");

        return prefixes.map(prefix -> prefix + "-" + System.nanoTime());
    }

    /**
     * Generates valid XQueryType values as strings.
     */
    @Provide
    Arbitrary<String> validTypes() {
        return Arbitraries.of("SINGULAR", "FORMULA", "EXTENDED");
    }

    /**
     * Generates valid XQueryStatus values as strings.
     */
    @Provide
    Arbitrary<String> validStatuses() {
        return Arbitraries.of("PENDING", "APPROVED", "DISALLOWED");
    }
}
