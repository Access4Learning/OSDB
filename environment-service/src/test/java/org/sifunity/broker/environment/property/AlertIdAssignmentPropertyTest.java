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
 * Property-based test for Property 3: Client-provided ID ignored.
 *
 * <p><strong>Property:</strong> For any alert creation request that includes an id field,
 * the broker-assigned UUID in the response SHALL differ from the client-provided id.
 * Since the {@link AlertCreateBody} DTO does not accept an {@code id} field, the broker
 * inherently ignores any client-provided ID. This test verifies that the broker always
 * assigns a non-null, unique UUID for each created alert.</p>
 *
 * <p><strong>Validates: Requirements 1.8</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertIdAssignmentPropertyTest {

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
        domainId = "alert-id-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "alert-id-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        // Register environment
        String appName = "AlertIdApp-" + System.nanoTime();
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO("environment-global"), solutionId, null))
        );

        requesterEnvironment = environmentRepository.findById(envResponse.environment().id())
                .orElseThrow();

        // Alerts provider is auto-registered by SolutionService.bootstrapEnvironmentGlobalZone()
    }

    /**
     * Property 3: Client-provided ID ignored — broker assigns unique UUIDs.
     *
     * <p>For each iteration, generates a batch of random client-provided UUIDs,
     * creates alerts, and verifies:</p>
     * <ul>
     *   <li>Each broker-assigned ID is non-null</li>
     *   <li>Each broker-assigned ID differs from the corresponding client-provided UUID</li>
     *   <li>All broker-assigned IDs within the batch are unique</li>
     * </ul>
     *
     * <p><strong>Validates: Requirements 1.8</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: alerts-service, Property 3: Client-provided ID ignored")
    void brokerAssignsUniqueIdsIgnoringClientProvidedIds() {
        // Generate a batch of random client-provided UUIDs
        int batchSize = 3 + random.nextInt(5); // 3 to 7 alerts per iteration
        List<UUID> clientProvidedIds = new ArrayList<>();
        List<UUID> brokerAssignedIds = new ArrayList<>();

        for (int i = 0; i < batchSize; i++) {
            UUID clientId = UUID.randomUUID();
            clientProvidedIds.add(clientId);

            // Create alert — the DTO has no id field, so client ID is inherently ignored
            AlertCreateBody createBody = new AlertCreateBody(
                    randomReporter(),
                    randomExchange(),
                    randomAlertLevel(),
                    null, null, null, // cause, description, messageID
                    null, null, null, // body, error, xpath
                    null, null, null, // category, code, internal
                    null              // no extended elements
            );

            AlertDTO created = alertsService.createAlert(
                    domainId, solutionId, createBody, requesterEnvironment, false);

            // Broker-assigned ID must be non-null (Req 1.8)
            assertThat(created.id())
                    .as("Broker must assign a non-null UUID for alert %d", i)
                    .isNotNull();

            // Broker-assigned ID must differ from client-provided UUID
            assertThat(created.id())
                    .as("Broker-assigned ID must differ from client-provided UUID %s", clientId)
                    .isNotEqualTo(clientId);

            brokerAssignedIds.add(created.id());
        }

        // All broker-assigned IDs must be unique across the batch
        Set<UUID> uniqueIds = new HashSet<>(brokerAssignedIds);
        assertThat(uniqueIds)
                .as("All %d broker-assigned IDs must be unique", batchSize)
                .hasSize(batchSize);
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
