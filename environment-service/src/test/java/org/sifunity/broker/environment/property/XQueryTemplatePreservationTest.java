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
import org.sifunity.broker.environment.exception.DuplicateQueryTokenException;
import org.sifunity.broker.environment.exception.XQueryTemplateNotFoundException;
import org.sifunity.broker.environment.repository.XQueryTemplateRepository;
import org.sifunity.broker.environment.service.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based preservation tests for XQueryTemplate CRUD behavior.
 *
 * <p>These tests capture baseline template CRUD behavior that MUST remain unchanged
 * after the provider registration decoupling fix. They test only template catalog
 * operations, NOT ProviderRegistration side effects.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4</strong></p>
 */
class XQueryTemplatePreservationTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;
    private XQueryTemplateService xQueryTemplateService;
    private XQueryTemplateRepository xQueryTemplateRepository;
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
        domainService = context.getBean(DomainService.class);
        solutionService = context.getBean(SolutionService.class);
        zoneService = context.getBean(ZoneService.class);
        environmentService = context.getBean(EnvironmentService.class);

        // Clean templates before each try to avoid uniqueness conflicts
        xQueryTemplateRepository.deleteAll();

        // Set up infrastructure: domain → solution → zone → environment
        domainId = "pres-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "pres-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        String zoneId = "pres-zone-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone for preservation"));

        String appName = "PreservationApp-" + System.nanoTime();
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null));
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(domainId, envRequest);
        environmentId = envResponse.environment().id();
    }

    /**
     * Property: For all valid (queryToken, type, status) tuples, create() returns DTO where
     * dto.queryToken == input.id, dto.type == input.type, dto.status == input.status.
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: xquery-provider-registration-fix, Property 2: Preservation - create returns correct DTO")
    void createReturnsCorrectDTO(
            @ForAll("validQueryTokens") String queryToken,
            @ForAll("validTypes") String type,
            @ForAll("validStatuses") String status,
            @ForAll("optionalStrings") String qualifier,
            @ForAll("optionalStrings") String description,
            @ForAll("optionalStrings") String script,
            @ForAll("optionalStrings") String returnType) {

        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, type, status, qualifier, description, script, new XQueryParametersDTO(List.of()), returnType));

        XQueryTemplateDTO created = xQueryTemplateService.create(domainId, solutionId, environmentId, request);

        assertThat(created.id())
                .as("DTO id (queryToken) must match input")
                .isEqualTo(queryToken);
        assertThat(created.type())
                .as("DTO type must match input (uppercased)")
                .isEqualTo(type.toUpperCase());
        assertThat(created.status())
                .as("DTO status must match input (uppercased)")
                .isEqualTo(status.toUpperCase());
    }

    /**
     * Property: For all valid templates, after create() the template is retrievable via
     * getByToken() with identical fields.
     *
     * <p><strong>Validates: Requirements 3.2, 3.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: xquery-provider-registration-fix, Property 2: Preservation - create then getByToken")
    void createThenGetByTokenReturnsIdenticalFields(
            @ForAll("validQueryTokens") String queryToken,
            @ForAll("validTypes") String type,
            @ForAll("validStatuses") String status,
            @ForAll("optionalStrings") String qualifier,
            @ForAll("optionalStrings") String description,
            @ForAll("optionalStrings") String script,
            @ForAll("validParameterLists") List<XQueryParameterDTO> parameters,
            @ForAll("optionalStrings") String returnType) {

        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, type, status, qualifier, description, script, new XQueryParametersDTO(parameters), returnType));

        XQueryTemplateDTO created = xQueryTemplateService.create(domainId, solutionId, environmentId, request);

        // Retrieve by token and verify fields are identical
        XQueryTemplateDTO retrieved = xQueryTemplateService.getByToken(domainId, solutionId, queryToken);

        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.type()).isEqualTo(created.type());
        assertThat(retrieved.status()).isEqualTo(created.status());
        assertThat(retrieved.qualifier()).isEqualTo(created.qualifier());
        assertThat(retrieved.description()).isEqualTo(created.description());
        assertThat(retrieved.script()).isEqualTo(created.script());
        assertThat(retrieved.returnType()).isEqualTo(created.returnType());
        assertThat(retrieved.parameters().parameter()).isEqualTo(created.parameters().parameter());

        // Also verify list() includes this template
        List<XQueryTemplateDTO> allTemplates = xQueryTemplateService.list(domainId, solutionId);
        assertThat(allTemplates)
                .as("list() should include the created template")
                .anyMatch(t -> t.id().equals(queryToken));
    }

    /**
     * Property: For all valid templates, delete() removes template such that
     * getByToken() throws XQueryTemplateNotFoundException.
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: xquery-provider-registration-fix, Property 2: Preservation - delete removes template")
    void deleteRemovesTemplateSoGetByTokenThrowsNotFound(
            @ForAll("validQueryTokens") String queryToken,
            @ForAll("validTypes") String type,
            @ForAll("validStatuses") String status) {

        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, type, status, null, null, null, new XQueryParametersDTO(List.of()), null));

        xQueryTemplateService.create(domainId, solutionId, environmentId, request);

        // Verify it exists
        XQueryTemplateDTO retrieved = xQueryTemplateService.getByToken(domainId, solutionId, queryToken);
        assertThat(retrieved.id()).isEqualTo(queryToken);

        // Delete it
        xQueryTemplateService.delete(domainId, solutionId, queryToken);

        // Verify getByToken throws not-found
        assertThatThrownBy(() -> xQueryTemplateService.getByToken(domainId, solutionId, queryToken))
                .isInstanceOf(XQueryTemplateNotFoundException.class);
    }

    /**
     * Property: For all empty/blank queryTokens, create() throws IllegalArgumentException.
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: xquery-provider-registration-fix, Property 2: Preservation - empty token validation")
    void createWithEmptyTokenThrowsValidationException(
            @ForAll("emptyOrBlankTokens") String emptyToken,
            @ForAll("validTypes") String type,
            @ForAll("validStatuses") String status) {

        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                emptyToken, type, status, null, null, null, new XQueryParametersDTO(List.of()), null));

        assertThatThrownBy(() -> xQueryTemplateService.create(domainId, solutionId, environmentId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Query token must not be empty");
    }

    // ========== Arbitraries ==========

    /**
     * Generates valid query tokens: non-empty alphanumeric strings with hyphens/underscores.
     * Each token is unique via nanoTime suffix.
     */
    @Provide
    Arbitrary<String> validQueryTokens() {
        Arbitrary<String> prefixes = Arbitraries.of(
                "StudentSnapshot", "RetrieveByGrade", "AttendanceSummary",
                "EnrollmentQuery", "StaffBySchool", "CourseRoster",
                "GradebookExport", "DemographicFilter", "TranscriptLookup",
                "ScheduleByTerm");

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

    /**
     * Generates optional strings (null or non-empty text).
     */
    @Provide
    Arbitrary<String> optionalStrings() {
        Arbitrary<String> nonEmpty = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_', '.', '/', ':')
                .ofMinLength(1)
                .ofMaxLength(80);

        return Arbitraries.oneOf(
                Arbitraries.just(null),
                nonEmpty
        );
    }

    /**
     * Generates empty or blank query tokens to test validation.
     */
    @Provide
    Arbitrary<String> emptyOrBlankTokens() {
        return Arbitraries.of(null, "", "   ", " ", "\t", "\n");
    }

    /**
     * Generates valid parameter lists (0 to 5 parameters with valid field combinations).
     */
    @Provide
    Arbitrary<List<XQueryParameterDTO>> validParameterLists() {
        Arbitrary<XQueryParameterDTO> paramArbitrary = Combinators.combine(
                parameterNames(),
                parameterTypes(),
                optionalParamStrings(),
                optionalParamStrings(),
                optionalBooleans()
        ).as(XQueryParameterDTO::new);

        return paramArbitrary.list().ofMinSize(0).ofMaxSize(5);
    }

    private Arbitrary<String> parameterNames() {
        return Arbitraries.of(
                "studentId", "schoolYear", "gradeLevel", "startDate", "endDate",
                "termId", "courseId", "staffId", "sectionId", "districtId",
                "pageSize", "offset", "format", "includeHistory", "filterBy");
    }

    private Arbitrary<String> parameterTypes() {
        return Arbitraries.of(
                "xs:string", "xs:int", "xs:date", "xs:boolean",
                "xs:decimal", "xs:dateTime", "xs:integer", "xs:long");
    }

    private Arbitrary<String> optionalParamStrings() {
        Arbitrary<String> values = Arbitraries.of(
                "default-val", "2024", "true", "100", "ALL",
                "2024-01-01", "none", "json", "xml");

        return Arbitraries.oneOf(
                Arbitraries.just(null),
                values
        );
    }

    private Arbitrary<Boolean> optionalBooleans() {
        return Arbitraries.of(null, Boolean.TRUE, Boolean.FALSE);
    }
}
