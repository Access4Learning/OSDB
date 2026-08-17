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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Property-based test for Property 7: Message context flows to extended elements.
 *
 * <p><strong>Property:</strong> For any valid alert creation request where messageID is
 * present and a matching message context exists in Redis, the response's
 * SIF_ExtendedElements SHALL contain sourceEnvironmentId, originatingZoneId, and
 * originatingContextId values matching the cached message context.</p>
 *
 * <p><strong>Validates: Requirements 2.4</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertMessageContextPropertyTest {

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

    @MockBean
    private MessageContextCacheService messageContextCacheService;

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
        domainId = "alert-ctx-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "alert-ctx-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        // Register environment
        String appName = "AlertCtxApp-" + System.nanoTime();
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO("environment-global"), solutionId, null))
        );

        requesterEnvironment = environmentRepository.findById(envResponse.environment().id())
                .orElseThrow();

        // Alerts provider is auto-registered by SolutionService.bootstrapEnvironmentGlobalZone()
    }

    /**
     * Property 7: Message context flows to extended elements.
     *
     * <p>For each iteration, generates random sourceEnvironmentId, zoneId, and contextId
     * values, configures the mock MessageContextCacheService to return them for a random
     * messageID, creates an alert with that messageID, and verifies the response's
     * SIF_ExtendedElements contain the cached values.</p>
     *
     * <p><strong>Validates: Requirements 2.4</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: alerts-service, Property 7: Message context flows to extended elements")
    void messageContextFlowsToExtendedElements() {
        // Generate random message context values
        String messageID = "msg-" + UUID.randomUUID();
        String cachedSourceEnvId = UUID.randomUUID().toString();
        String cachedZoneId = randomZoneId();
        String cachedContextId = randomContextId();

        // Configure mock to return matching message context for this messageID
        MessageContext messageContext = new MessageContext(cachedSourceEnvId, cachedZoneId, cachedContextId);
        when(messageContextCacheService.getMessageContext(eq(domainId), eq(solutionId), eq(messageID)))
                .thenReturn(Optional.of(messageContext));

        // Generate random valid required fields
        String reporter = randomReporter();
        Exchange exchange = randomExchange();
        AlertLevel level = randomAlertLevel();

        // Build request with messageID
        AlertCreateBody createBody = new AlertCreateBody(
                reporter, exchange, level,
                randomOptionalString("Cause"),
                randomOptionalString("Desc"),
                messageID,
                randomOptionalString("Body"),
                randomOptionalString("Error"),
                randomOptionalXpath(),
                randomOptionalInteger(),
                randomOptionalInteger(),
                randomOptionalString("INT"),
                null // no caller extended elements
        );

        // Create alert
        AlertDTO created = alertsService.createAlert(
                domainId, solutionId, createBody, requesterEnvironment, false);

        // Verify SIF_ExtendedElements present
        assertThat(created.SIF_ExtendedElements())
                .as("SIF_ExtendedElements should be present")
                .isNotNull();
        assertThat(created.SIF_ExtendedElements().SIF_ExtendedElement())
                .as("SIF_ExtendedElement list should not be empty")
                .isNotEmpty();

        // Build map for easy lookup
        List<SifExtendedElement> elements = created.SIF_ExtendedElements().SIF_ExtendedElement();
        Map<String, String> elementMap = new HashMap<>();
        for (SifExtendedElement elem : elements) {
            elementMap.put(elem.Name(), elem.value());
        }

        // Verify sourceEnvironmentId matches cached value (Req 2.4)
        assertThat(elementMap).containsKey("sourceEnvironmentId");
        assertThat(elementMap.get("sourceEnvironmentId"))
                .as("sourceEnvironmentId should match cached message context value")
                .isEqualTo(cachedSourceEnvId);

        // Verify originatingZoneId matches cached value (Req 2.4)
        assertThat(elementMap).containsKey("originatingZoneId");
        assertThat(elementMap.get("originatingZoneId"))
                .as("originatingZoneId should match cached message context zoneId")
                .isEqualTo(cachedZoneId);

        // Verify originatingContextId matches cached value (Req 2.4)
        assertThat(elementMap).containsKey("originatingContextId");
        assertThat(elementMap.get("originatingContextId"))
                .as("originatingContextId should match cached message context contextId")
                .isEqualTo(cachedContextId);

        // Verify createdAt is still present
        assertThat(elementMap).containsKey("createdAt");
        assertThat(elementMap.get("createdAt"))
                .as("createdAt should be a non-blank ISO timestamp")
                .isNotBlank();
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

    private String randomZoneId() {
        String[] zones = {
                "environment-global", "district-zone-1", "school-zone-A",
                "assessment-zone", "transport-zone", "zone-" + random.nextInt(1000)
        };
        return zones[random.nextInt(zones.length)];
    }

    private String randomContextId() {
        String[] contexts = {
                "DEFAULT", "SIS-Context", "LMS-Context",
                "Assessment-Context", "ctx-" + random.nextInt(1000)
        };
        return contexts[random.nextInt(contexts.length)];
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
