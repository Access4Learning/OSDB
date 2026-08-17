// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.DuplicateQueryTokenException;
import org.sifunity.broker.environment.service.*;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for Property 2: Duplicate token rejection.
 *
 * <p><strong>Property:</strong> For any queryToken that already exists within a solution,
 * attempting to create another template with the same queryToken in the same solution
 * SHALL be rejected with {@link DuplicateQueryTokenException}, and the existing template
 * SHALL remain unchanged.</p>
 *
 * <p><strong>Validates: Requirements 1.5, 2.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class XQueryTemplateDuplicateTokenPropertyTest {

    @Autowired
    private XQueryTemplateService xQueryTemplateService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private ZoneService zoneService;

    private final Random random = new Random();

    private String domainId;
    private String solutionId;
    private UUID environmentId;

    @BeforeEach
    void setUp() {
        // Create infrastructure: domain → solution → environment
        domainId = "xq-dup-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "xq-dup-sol-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        // Create a uniquely-named zone so findByZoneId returns a single result
        String uniqueZoneId = "xq-dup-zone-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, uniqueZoneId, "Test zone for duplicate token property"));

        // Register a BROKERED environment with the unique defaultZoneId
        String appName = "xq-dup-provider-" + System.nanoTime();
        var envResponse = environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, DefaultZoneDTO.of(uniqueZoneId), solutionId, null))
        );
        environmentId = envResponse.environment().id();
    }

    /**
     * Property 2: Duplicate token rejection — duplicate queryToken in same solution is rejected
     * and original template remains unchanged.
     *
     * <p><strong>Validates: Requirements 1.5, 2.5</strong></p>
     */
    @RepeatedTest(20)
    @Tag("Feature: named-query-support, Property 2: Duplicate token rejection")
    void duplicateQueryTokenIsRejectedAndOriginalUnchanged() {
        // Generate arbitrary valid token and template data
        String queryToken = randomQueryToken();
        String type = randomType();
        String status = randomStatus();
        String description = "Original description " + random.nextInt(100000);
        String script = "SELECT * FROM original_" + random.nextInt(100000);
        String qualifier = random.nextBoolean() ? "qualifier-" + random.nextInt(1000) : null;
        String returnType = "urn:sif:return:" + random.nextInt(1000);
        List<XQueryParameterDTO> parameters = randomParameters();

        XQueryTemplateCreateRequest originalRequest = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, type, status, qualifier, description, script, new XQueryParametersDTO(parameters), returnType
        ));

        // Create the first template — should succeed
        XQueryTemplateDTO original = xQueryTemplateService.create(domainId, solutionId, environmentId, originalRequest);
        assertThat(original.id()).isEqualTo(queryToken);

        // Attempt to create another template with the SAME token but different data
        String differentType = randomDifferentType(type);
        String differentStatus = randomDifferentStatus(status);
        String differentDescription = "Duplicate attempt " + random.nextInt(100000);
        String differentScript = "SELECT * FROM duplicate_" + random.nextInt(100000);

        XQueryTemplateCreateRequest duplicateRequest = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, differentType, differentStatus, "dup-qualifier",
                differentDescription, differentScript, new XQueryParametersDTO(List.of()), "urn:sif:different"
        ));

        // Duplicate creation SHALL be rejected with DuplicateQueryTokenException (Req 1.5)
        assertThatThrownBy(() -> xQueryTemplateService.create(domainId, solutionId, environmentId, duplicateRequest))
                .isInstanceOf(DuplicateQueryTokenException.class);

        // Original template SHALL remain unchanged (Req 2.5)
        XQueryTemplateDTO retrieved = xQueryTemplateService.getByToken(domainId, solutionId, queryToken);
        assertThat(retrieved.id()).isEqualTo(queryToken);
        assertThat(retrieved.type()).isEqualTo(type);
        assertThat(retrieved.status()).isEqualTo(status);
        assertThat(retrieved.description()).isEqualTo(description);
        assertThat(retrieved.script()).isEqualTo(script);
        assertThat(retrieved.qualifier()).isEqualTo(qualifier);
        assertThat(retrieved.returnType()).isEqualTo(returnType);
        assertThat(retrieved.parameters().parameter()).hasSameSizeAs(parameters);
    }

    // ========== Random Data Generators ==========

    private String randomQueryToken() {
        String[] prefixes = {"StudentSnapshot", "RetrieveByGrade", "EnrollmentQuery",
                "AttendanceReport", "StaffLookup", "CourseSearch", "AssessmentResult"};
        return prefixes[random.nextInt(prefixes.length)] + "_" + random.nextInt(100000);
    }

    private String randomType() {
        String[] types = {"SINGULAR", "FORMULA", "EXTENDED"};
        return types[random.nextInt(types.length)];
    }

    private String randomStatus() {
        String[] statuses = {"PENDING", "APPROVED", "DISALLOWED"};
        return statuses[random.nextInt(statuses.length)];
    }

    private String randomDifferentType(String currentType) {
        String[] types = {"SINGULAR", "FORMULA", "EXTENDED"};
        String result;
        do {
            result = types[random.nextInt(types.length)];
        } while (result.equals(currentType));
        return result;
    }

    private String randomDifferentStatus(String currentStatus) {
        String[] statuses = {"PENDING", "APPROVED", "DISALLOWED"};
        String result;
        do {
            result = statuses[random.nextInt(statuses.length)];
        } while (result.equals(currentStatus));
        return result;
    }

    private List<XQueryParameterDTO> randomParameters() {
        int count = random.nextInt(4); // 0 to 3 params
        if (count == 0) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new XQueryParameterDTO(
                        "param" + i,
                        random.nextBoolean() ? "string" : "integer",
                        random.nextBoolean() ? "default" + i : null,
                        "Parameter " + i + " description",
                        random.nextBoolean()
                ))
                .toList();
    }
}
