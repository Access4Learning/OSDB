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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 5: Admin sees all alerts.
 *
 * <p><strong>Property:</strong> For any admin environment and any set of alerts
 * created by multiple environments, querying alerts (GET /alerts) SHALL return
 * all alerts in the system regardless of which environment created them.</p>
 *
 * <p><strong>Validates: Requirements 3.3, 8.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertAdminAccessPropertyTest {

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
    private List<Environment> nonAdminEnvironments;
    private Environment adminEnvironment;

    @BeforeEach
    void setUp() {
        // Stub event publisher to avoid HTTP calls
        doNothing().when(alertEventPublisher)
                .publishAlertCreatedEvent(any(), any(), anyString(), anyString());

        // Create infrastructure: domain, solution, environment-global zone
        domainId = "alert-admin-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "alert-admin-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        // Register 3 non-admin environments
        nonAdminEnvironments = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String appName = "AdminTestApp" + i + "-" + System.nanoTime();
            SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                    domainId,
                    new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO("environment-global"), solutionId, null))
            );
            Environment env = environmentRepository.findById(envResponse.environment().id())
                    .orElseThrow();
            nonAdminEnvironments.add(env);
        }

        // Use first environment as the "admin" querier (we pass isAdmin=true to service)
        adminEnvironment = nonAdminEnvironments.get(0);

        // Alerts provider is auto-registered by SolutionService.bootstrapEnvironmentGlobalZone()
    }

    /**
     * Property 5: Admin sees all alerts — listAlerts with isAdmin=true returns all alerts.
     *
     * <p>Creates a random number of alerts from each of the 3 environments,
     * then queries as admin and verifies ALL alerts from ALL environments
     * are returned.</p>
     *
     * <p><strong>Validates: Requirements 3.3, 8.3</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: alerts-service, Property 5: Admin sees all alerts")
    void adminListAlertsReturnsAllAlerts() {
        // Create random alerts from each environment
        Set<UUID> allAlertIds = new HashSet<>();
        for (Environment env : nonAdminEnvironments) {
            int alertCount = 1 + random.nextInt(4); // 1 to 4 alerts per env
            for (int j = 0; j < alertCount; j++) {
                AlertDTO created = alertsService.createAlert(
                        domainId, solutionId,
                        randomAlertBody(),
                        env, false);
                allAlertIds.add(created.id());
            }
        }

        // Query as admin (isAdmin=true) using any environment
        List<AlertDTO> results = alertsService.listAlerts(adminEnvironment, true);

        // Extract returned alert IDs
        Set<UUID> returnedIds = results.stream()
                .map(AlertDTO::id)
                .collect(Collectors.toSet());

        // Admin should see ALL alerts from ALL environments (Req 3.3, 8.3)
        assertThat(returnedIds)
                .as("Admin should see all alerts from all environments")
                .containsAll(allAlertIds);

        // Verify count matches — admin sees at least all alerts we created
        assertThat(returnedIds.size())
                .as("Admin should see at least as many alerts as were created")
                .isGreaterThanOrEqualTo(allAlertIds.size());
    }

    /**
     * Property 5: Admin sees all alerts — getAlertById with isAdmin=true
     * can access any environment's alert.
     *
     * <p>Creates alerts from two different environments, then retrieves each
     * alert as admin using a different environment. Verifies admin can access
     * alerts regardless of ownership.</p>
     *
     * <p><strong>Validates: Requirements 3.3, 8.3</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: alerts-service, Property 5: Admin sees all alerts")
    void adminGetAlertByIdCanAccessAnyEnvironmentsAlert() {
        Environment envA = nonAdminEnvironments.get(0);
        Environment envB = nonAdminEnvironments.get(1);
        Environment envC = nonAdminEnvironments.get(2);

        // Create alert from envA
        AlertDTO alertFromA = alertsService.createAlert(
                domainId, solutionId,
                randomAlertBody(),
                envA, false);

        // Create alert from envB
        AlertDTO alertFromB = alertsService.createAlert(
                domainId, solutionId,
                randomAlertBody(),
                envB, false);

        // Create alert from envC
        AlertDTO alertFromC = alertsService.createAlert(
                domainId, solutionId,
                randomAlertBody(),
                envC, false);

        // Admin (using envB) can retrieve envA's alert
        AlertDTO retrieved = alertsService.getAlertById(alertFromA.id(), envB, true);
        assertThat(retrieved.id())
                .as("Admin should retrieve envA's alert using envB")
                .isEqualTo(alertFromA.id());

        // Admin (using envC) can retrieve envB's alert
        retrieved = alertsService.getAlertById(alertFromB.id(), envC, true);
        assertThat(retrieved.id())
                .as("Admin should retrieve envB's alert using envC")
                .isEqualTo(alertFromB.id());

        // Admin (using envA) can retrieve envC's alert
        retrieved = alertsService.getAlertById(alertFromC.id(), envA, true);
        assertThat(retrieved.id())
                .as("Admin should retrieve envC's alert using envA")
                .isEqualTo(alertFromC.id());
    }

    // ========== Random Data Generators ==========

    private AlertCreateBody randomAlertBody() {
        return new AlertCreateBody(
                randomReporter(),
                randomExchange(),
                randomAlertLevel(),
                randomOptionalString("Cause"),
                randomOptionalString("Desc"),
                null, // no messageID
                randomOptionalString("Body"),
                randomOptionalString("Error"),
                null, null, null, null,
                null
        );
    }

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
}
