// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 1: Alert creation round-trip.
 *
 * <p><strong>Property:</strong> For any valid alert creation request (with valid reporter,
 * exchange, level, and arbitrary optional fields), creating the alert via POST then
 * retrieving it by the returned ID via GET SHALL produce a response where all request
 * fields match the response fields, plus the response includes a broker-assigned id,
 * SIF_ExtendedElements with server-populated fields, and a createdAt timestamp.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.6, 1.7, 1.10, 7.1, 7.2, 7.6, 7.8</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertCreationRoundTripPropertyTest {

    @Autowired
    private AlertsService alertsService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @MockBean
    private AlertEventPublisher alertEventPublisher;

    private final Random random = new Random();

    private String domainId;
    private String solutionId;
    private Environment requesterEnvironment;

    @BeforeEach
    void setUp() {
        // Stub event publisher to avoid HTTP calls
        doNothing().when(alertEventPublisher)
                .publishAlertCreatedEvent(any(), any(), anyString(), anyString());

        // Create infrastructure: domain, solution, environment-global zone, environment, alerts provider
        domainId = "alert-rt-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "alert-rt-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        // SolutionService auto-creates environment-global zone + DEFAULT context

        // Register environment
        String appName = "AlertRTApp-" + System.nanoTime();
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO("environment-global"), solutionId, null))
        );

        // Retrieve full environment entity (with fingerprint)
        requesterEnvironment = environmentRepository.findById(envResponse.environment().id())
                .orElseThrow();

        // Alerts provider is auto-registered by SolutionService.bootstrapEnvironmentGlobalZone()
    }

    /**
     * Property 1: Alert creation round-trip preserves all fields.
     *
     * <p><strong>Validates: Requirements 1.1, 1.6, 1.7, 1.10, 7.1, 7.2, 7.6, 7.8</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: alerts-service, Property 1: Alert creation round-trip")
    void alertCreationRoundTripPreservesAllFields() {
        // Generate random valid inputs
        String reporter = randomReporter();
        Exchange exchange = randomExchange();
        AlertLevel level = randomAlertLevel();

        // Generate random optional fields
        String cause = randomOptionalString("Cause");
        String description = randomOptionalString("Desc");
        String body = randomOptionalString("Body");
        String error = randomOptionalString("Error");
        String xpath = randomOptionalXpath();
        Integer category = randomOptionalInteger();
        Integer code = randomOptionalInteger();
        String internal = randomOptionalString("INT");

        // Build request (no messageID — defaults to environment-global zone)
        AlertCreateBody createBody = new AlertCreateBody(
                reporter, exchange, level,
                cause, description, null, // no messageID
                body, error, xpath,
                category, code, internal,
                null // no caller extended elements
        );

        // Create alert
        AlertDTO created = alertsService.createAlert(
                domainId, solutionId, createBody, requesterEnvironment, false);

        // Verify broker-assigned ID (Req 1.1, 1.8)
        assertThat(created.id())
                .as("Broker should assign a non-null UUID")
                .isNotNull();

        // Retrieve by ID
        AlertDTO retrieved = alertsService.getAlertById(
                created.id(), requesterEnvironment, false);

        // Assert all request fields match response (Req 7.1, 7.8)
        assertThat(retrieved.reporter())
                .as("reporter should round-trip")
                .isEqualTo(reporter);
        assertThat(retrieved.exchange())
                .as("exchange should round-trip")
                .isEqualTo(exchange);
        assertThat(retrieved.level())
                .as("level should round-trip")
                .isEqualTo(level);

        // Optional string fields (Req 1.6)
        assertThat(retrieved.cause())
                .as("cause should round-trip")
                .isEqualTo(cause);
        assertThat(retrieved.description())
                .as("description should round-trip")
                .isEqualTo(description);
        assertThat(retrieved.body())
                .as("body should round-trip")
                .isEqualTo(body);
        assertThat(retrieved.error())
                .as("error should round-trip")
                .isEqualTo(error);
        assertThat(retrieved.xpath())
                .as("xpath should round-trip")
                .isEqualTo(xpath);
        assertThat(retrieved.internal())
                .as("internal should round-trip")
                .isEqualTo(internal);

        // Optional integer fields (Req 1.7)
        assertThat(retrieved.category())
                .as("category should round-trip")
                .isEqualTo(category);
        assertThat(retrieved.code())
                .as("code should round-trip")
                .isEqualTo(code);

        // messageID should be null (we didn't set it)
        assertThat(retrieved.messageID())
                .as("messageID should be null when not provided")
                .isNull();

        // SIF_ExtendedElements should be present with server-populated fields (Req 7.2)
        assertThat(retrieved.SIF_ExtendedElements())
                .as("SIF_ExtendedElements should be present")
                .isNotNull();
        assertThat(retrieved.SIF_ExtendedElements().SIF_ExtendedElement())
                .as("SIF_ExtendedElement list should not be empty")
                .isNotEmpty();

        List<SifExtendedElement> elements = retrieved.SIF_ExtendedElements().SIF_ExtendedElement();
        Map<String, String> elementMap = new HashMap<>();
        for (SifExtendedElement elem : elements) {
            elementMap.put(elem.Name(), elem.value());
        }

        // Verify server-populated extended elements
        assertThat(elementMap).containsKey("originatingZoneId");
        assertThat(elementMap.get("originatingZoneId"))
                .as("originatingZoneId should default to environment-global")
                .isEqualTo("environment-global");

        assertThat(elementMap).containsKey("originatingContextId");
        assertThat(elementMap.get("originatingContextId"))
                .as("originatingContextId should default to DEFAULT")
                .isEqualTo("DEFAULT");

        // createdAt should be present (Req 1.10)
        assertThat(elementMap).containsKey("createdAt");
        assertThat(elementMap.get("createdAt"))
                .as("createdAt should be a non-blank ISO timestamp")
                .isNotBlank();

        // reportingEnvironmentFingerprint should be present if environment has fingerprint
        if (requesterEnvironment.getFingerprint() != null) {
            assertThat(elementMap).containsKey("reportingEnvironmentFingerprint");
            assertThat(elementMap.get("reportingEnvironmentFingerprint"))
                    .as("reportingEnvironmentFingerprint should match environment fingerprint")
                    .isEqualTo(requesterEnvironment.getFingerprint());
        }

        // Created and retrieved should be identical (Req 7.6, 7.8)
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.reporter()).isEqualTo(created.reporter());
        assertThat(retrieved.exchange()).isEqualTo(created.exchange());
        assertThat(retrieved.level()).isEqualTo(created.level());
    }

    // ========== Random Data Generators ==========

    private Exchange randomExchange() {
        Exchange[] values = Exchange.values();
        return values[random.nextInt(values.length)];
    }

    private AlertLevel randomAlertLevel() {
        AlertLevel[] values = AlertLevel.values();
        return values[random.nextInt(values.length)];
    }

    private String randomReporter() {
        String[] reporters = {
                "StudentInfoApp", "LearningMgmt", "AssessmentSvc",
                "GradebookApp", "AttendanceTracker", "TransportSvc"
        };
        return reporters[random.nextInt(reporters.length)] + "-" + random.nextInt(10000);
    }

    private String randomOptionalString(String prefix) {
        if (random.nextBoolean()) {
            return null;
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String randomOptionalXpath() {
        if (random.nextBoolean()) {
            return null;
        }
        String[] paths = {
                "/StudentPersonal/Name/FirstName",
                "/SchoolInfo/Address/City",
                "/StaffAssignment/Role",
                "/Assessment/Score"
        };
        return paths[random.nextInt(paths.length)];
    }

    private Integer randomOptionalInteger() {
        if (random.nextBoolean()) {
            return null;
        }
        return random.nextInt(20) + 1;
    }
}
