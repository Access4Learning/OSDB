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
 * Property-based test for Property 2: Invalid required fields rejected.
 *
 * <p><strong>Property:</strong> For any alert creation request where at least one
 * required field (reporter, exchange, or level) is missing, null, or invalid
 * (e.g., reporter is blank/whitespace-only), the service SHALL return HTTP 400
 * and the alert SHALL NOT be persisted.</p>
 *
 * <p><strong>Validates: Requirements 1.2, 1.3, 1.4, 1.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertValidationPropertyTest {

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

    private final Random random = new Random();

    private String domainId;
    private String solutionId;
    private Environment requesterEnvironment;

    @BeforeEach
    void setUp() {
        doNothing().when(alertEventPublisher)
                .publishAlertCreatedEvent(any(), any(), anyString(), anyString());

        domainId = "alert-val-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "alert-val-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        String appName = "AlertValApp-" + System.nanoTime();
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO("environment-global"), solutionId, null))
        );

        requesterEnvironment = environmentRepository.findById(envResponse.environment().id())
                .orElseThrow();

        // Alerts provider is auto-registered by SolutionService.bootstrapEnvironmentGlobalZone()
    }

    /**
     * Property 2: Invalid required fields rejected.
     *
     * <p>Each iteration randomly invalidates at least one required field
     * (reporter, exchange, level) and asserts that the service throws
     * SifBrokerException with ALERT_001 error code and BAD_REQUEST status,
     * and that no alert is persisted.</p>
     *
     * <p><strong>Validates: Requirements 1.2, 1.3, 1.4, 1.5</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: alerts-service, Property 2: Invalid required fields rejected")
    void invalidRequiredFieldsAreRejectedAndNotPersisted() {
        long countBefore = alertRepository.count();

        // Randomly decide which fields to invalidate (at least one)
        boolean invalidateReporter = random.nextBoolean();
        boolean invalidateExchange = random.nextBoolean();
        boolean invalidateLevel = random.nextBoolean();

        // Ensure at least one field is invalidated
        if (!invalidateReporter && !invalidateExchange && !invalidateLevel) {
            // Pick one at random
            int pick = random.nextInt(3);
            if (pick == 0) invalidateReporter = true;
            else if (pick == 1) invalidateExchange = true;
            else invalidateLevel = true;
        }

        // Build request with selectively invalid fields
        String reporter = invalidateReporter ? randomInvalidReporter() : randomValidReporter();
        Exchange exchange = invalidateExchange ? null : randomValidExchange();
        AlertLevel level = invalidateLevel ? null : randomValidAlertLevel();

        AlertCreateBody createBody = new AlertCreateBody(
                reporter, exchange, level,
                null, null, null,
                null, null, null,
                null, null, null,
                null
        );

        // Assert SifBrokerException with ALERT_001 and BAD_REQUEST
        assertThatThrownBy(() ->
                alertsService.createAlert(domainId, solutionId, createBody, requesterEnvironment, false)
        )
                .isInstanceOf(SifBrokerException.class)
                .satisfies(thrown -> {
                    SifBrokerException ex = (SifBrokerException) thrown;
                    assertThat(ex.getErrorCode())
                            .as("Error code should be ALERT_001 for invalid required fields")
                            .isEqualTo("ALERT_001");
                    assertThat(ex.getHttpStatus())
                            .as("HTTP status should be BAD_REQUEST")
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                });

        // Assert alert was NOT persisted
        long countAfter = alertRepository.count();
        assertThat(countAfter)
                .as("No alert should be persisted when required fields are invalid")
                .isEqualTo(countBefore);
    }

    // ========== Random Data Generators ==========

    /**
     * Generates an invalid reporter value: null, empty, or whitespace-only.
     */
    private String randomInvalidReporter() {
        int choice = random.nextInt(3);
        return switch (choice) {
            case 0 -> null;
            case 1 -> "";
            case 2 -> "   "; // whitespace-only
            default -> null;
        };
    }

    private String randomValidReporter() {
        String[] reporters = {
                "StudentInfoApp", "LearningMgmt", "AssessmentSvc",
                "GradebookApp", "AttendanceTracker", "TransportSvc"
        };
        return reporters[random.nextInt(reporters.length)] + "-" + random.nextInt(10000);
    }

    private Exchange randomValidExchange() {
        Exchange[] values = Exchange.values();
        return values[random.nextInt(values.length)];
    }

    private AlertLevel randomValidAlertLevel() {
        AlertLevel[] values = AlertLevel.values();
        return values[random.nextInt(values.length)];
    }
}
