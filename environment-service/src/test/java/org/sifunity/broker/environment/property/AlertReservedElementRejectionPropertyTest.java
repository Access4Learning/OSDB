// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.AlertRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 8: Non-admin reserved extended element rejection.
 *
 * <p><strong>Property:</strong> For any non-admin environment and any alert creation
 * request that includes one or more reserved extended element names
 * (reportingEnvironmentFingerprint, sourceEnvironmentId, createdAt) in the
 * SIF_ExtendedElements, the service SHALL return HTTP 400.</p>
 *
 * <p><strong>Validates: Requirements 7.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertReservedElementRejectionPropertyTest {

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

    @Autowired
    private AlertRepository alertRepository;

    @MockBean
    private AlertEventPublisher alertEventPublisher;

    private static final List<String> RESERVED_NAMES = List.of(
            "reportingEnvironmentFingerprint",
            "sourceEnvironmentId",
            "createdAt"
    );

    private final Random random = new Random();

    private String domainId;
    private String solutionId;
    private Environment requesterEnvironment;

    @BeforeEach
    void setUp() {
        doNothing().when(alertEventPublisher)
                .publishAlertCreatedEvent(any(), any(), anyString(), anyString());

        domainId = "alert-resv-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "alert-resv-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        String appName = "AlertResvApp-" + System.nanoTime();
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO("environment-global"), solutionId, null))
        );

        requesterEnvironment = environmentRepository.findById(envResponse.environment().id())
                .orElseThrow();

        // Alerts provider is auto-registered by SolutionService.bootstrapEnvironmentGlobalZone()
    }

    /**
     * Property 8: Non-admin reserved extended element rejection.
     *
     * <p>Each iteration randomly selects 1-3 reserved extended element names,
     * builds a request with those names in SIF_ExtendedElements, and asserts
     * that the service throws SifBrokerException with ALERT_002 error code
     * and BAD_REQUEST status. Also asserts no alert is persisted.</p>
     *
     * <p><strong>Validates: Requirements 7.3</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: alerts-service, Property 8: Non-admin reserved extended element rejection")
    void nonAdminReservedExtendedElementsAreRejected() {
        long countBefore = alertRepository.count();

        // Randomly select 1-3 reserved element names
        List<String> shuffled = new ArrayList<>(RESERVED_NAMES);
        Collections.shuffle(shuffled, random);
        int count = random.nextInt(3) + 1; // 1 to 3
        List<String> selectedReserved = shuffled.subList(0, count);

        // Build extended elements with reserved names (and optionally a custom one)
        List<SifExtendedElement> elements = new ArrayList<>();
        for (String reservedName : selectedReserved) {
            elements.add(new SifExtendedElement(reservedName, "spoofed-value-" + random.nextInt(10000)));
        }

        // Optionally add a custom (non-reserved) element to mix things up
        if (random.nextBoolean()) {
            elements.add(new SifExtendedElement(
                    "customField-" + random.nextInt(1000),
                    "customValue-" + random.nextInt(1000)));
        }

        SifExtendedElements extendedElements = new SifExtendedElements(elements);

        // Build valid alert body with reserved extended elements
        AlertCreateBody createBody = new AlertCreateBody(
                randomReporter(),
                randomExchange(),
                randomAlertLevel(),
                null, null, null,
                null, null, null,
                null, null, null,
                extendedElements
        );

        // Assert SifBrokerException with ALERT_002 and BAD_REQUEST (isAdmin=false)
        assertThatThrownBy(() ->
                alertsService.createAlert(domainId, solutionId, createBody, requesterEnvironment, false)
        )
                .isInstanceOf(SifBrokerException.class)
                .satisfies(thrown -> {
                    SifBrokerException ex = (SifBrokerException) thrown;
                    assertThat(ex.getErrorCode())
                            .as("Error code should be ALERT_002 for reserved extended elements")
                            .isEqualTo("ALERT_002");
                    assertThat(ex.getHttpStatus())
                            .as("HTTP status should be BAD_REQUEST")
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                });

        // Assert no alert persisted
        long countAfter = alertRepository.count();
        assertThat(countAfter)
                .as("No alert should be persisted when non-admin includes reserved extended elements")
                .isEqualTo(countBefore);
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
}
