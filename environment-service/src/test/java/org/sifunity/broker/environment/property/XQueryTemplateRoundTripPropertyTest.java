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
import org.sifunity.broker.environment.domain.XQueryStatus;
import org.sifunity.broker.environment.domain.XQueryType;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.XQueryTemplateRepository;
import org.sifunity.broker.environment.service.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Property 1: Template creation round-trip.
 *
 * <p><strong>Property:</strong> For any valid XQueryTemplate with a unique queryToken
 * and any combination of valid type, status, qualifier, description, script,
 * parameters (0 to N with varying fields), and returnType, creating it via the
 * service and then retrieving it by queryToken SHALL return an equivalent template
 * with all fields preserved.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.3, 2.1, 2.2</strong></p>
 */
class XQueryTemplateRoundTripPropertyTest {

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
        domainId = "xqrt-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "xqrt-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        String zoneId = "xqrt-zone-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone for xQuery round-trip"));

        String appName = "XQueryRoundTripApp-" + System.nanoTime();
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null));
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(domainId, envRequest);
        environmentId = envResponse.environment().id();
    }

    /**
     * Property 1: Template creation round-trip preserves all fields.
     *
     * <p><strong>Validates: Requirements 1.1, 1.3, 2.1, 2.2</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: named-query-support, Property 1: Template creation round-trip")
    void templateCreationRoundTripPreservesAllFields(
            @ForAll("validQueryTokens") String queryToken,
            @ForAll("validTypes") String type,
            @ForAll("validStatuses") String status,
            @ForAll("optionalStrings") String qualifier,
            @ForAll("optionalStrings") String description,
            @ForAll("optionalStrings") String script,
            @ForAll("validParameterLists") List<XQueryParameterDTO> parameters,
            @ForAll("optionalStrings") String returnType) {

        // Create template via service (Requirement 1.1)
        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, type, status, qualifier, description, script, new XQueryParametersDTO(parameters), returnType));

        XQueryTemplateDTO created = xQueryTemplateService.create(domainId, solutionId, environmentId, request);

        // Verify created response fields match request
        assertThat(created.id())
                .as("id (queryToken) must match request")
                .isEqualTo(queryToken);
        assertThat(created.type())
                .as("type must match request")
                .isEqualTo(type.toUpperCase());
        assertThat(created.status())
                .as("status must match request")
                .isEqualTo(status.toUpperCase());
        assertThat(created.qualifier())
                .as("qualifier must match request")
                .isEqualTo(qualifier);
        assertThat(created.description())
                .as("description must match request")
                .isEqualTo(description);
        assertThat(created.script())
                .as("script must match request")
                .isEqualTo(script);
        assertThat(created.returnType())
                .as("returnType must match request")
                .isEqualTo(returnType);

        // Verify parameters round-trip
        assertParametersMatch(parameters, created.parameters().parameter());

        // Retrieve by token (Requirement 1.3) and verify full round-trip
        XQueryTemplateDTO retrieved = xQueryTemplateService.getByToken(domainId, solutionId, queryToken);

        assertThat(retrieved.id())
                .as("Retrieved id must equal created id")
                .isEqualTo(created.id());
        assertThat(retrieved.type())
                .as("Retrieved type must equal created type")
                .isEqualTo(created.type());
        assertThat(retrieved.status())
                .as("Retrieved status must equal created status")
                .isEqualTo(created.status());
        assertThat(retrieved.qualifier())
                .as("Retrieved qualifier must equal created qualifier")
                .isEqualTo(created.qualifier());
        assertThat(retrieved.description())
                .as("Retrieved description must equal created description")
                .isEqualTo(created.description());
        assertThat(retrieved.script())
                .as("Retrieved script must equal created script")
                .isEqualTo(created.script());
        assertThat(retrieved.returnType())
                .as("Retrieved returnType must equal created returnType")
                .isEqualTo(created.returnType());

        // Verify parameters survived the database round-trip
        assertParametersMatch(created.parameters().parameter(), retrieved.parameters().parameter());
    }

    // ========== Assertion Helpers ==========

    private void assertParametersMatch(List<XQueryParameterDTO> expected, List<XQueryParameterDTO> actual) {
        if (expected == null || expected.isEmpty()) {
            assertThat(actual)
                    .as("Parameters should be empty when none provided")
                    .isNotNull()
                    .isEmpty();
            return;
        }

        assertThat(actual)
                .as("Parameters list should not be null")
                .isNotNull();
        assertThat(actual)
                .as("Parameters list size should match")
                .hasSameSizeAs(expected);

        for (int i = 0; i < expected.size(); i++) {
            XQueryParameterDTO exp = expected.get(i);
            XQueryParameterDTO act = actual.get(i);

            assertThat(act.name())
                    .as("parameter[%d].name should round-trip", i)
                    .isEqualTo(exp.name());
            assertThat(act.type())
                    .as("parameter[%d].type should round-trip", i)
                    .isEqualTo(exp.type());
            assertThat(act.defaultValue())
                    .as("parameter[%d].defaultValue should round-trip", i)
                    .isEqualTo(exp.defaultValue());
            assertThat(act.description())
                    .as("parameter[%d].description should round-trip", i)
                    .isEqualTo(exp.description());
            assertThat(act.required())
                    .as("parameter[%d].required should round-trip", i)
                    .isEqualTo(exp.required());
        }
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
