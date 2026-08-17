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
 * Property-based test for Property 9: Extended element merge with server precedence.
 *
 * <p><strong>Property:</strong> For any valid alert creation request (from an admin
 * environment) that includes caller-provided SIF_ExtendedElements containing both
 * custom names and reserved names, the response's SIF_ExtendedElements SHALL contain
 * all custom name/value pairs unchanged AND all reserved names SHALL have
 * server-populated values (overriding any caller-provided values for reserved names).</p>
 *
 * <p><strong>Validates: Requirements 7.4, 7.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertExtendedElementMergePropertyTest {

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

    private static final List<String> RESERVED_NAMES = List.of(
            "reportingEnvironmentFingerprint",
            "sourceEnvironmentId",
            "createdAt"
    );

    private final Random random = new Random();

    private String domainId;
    private String solutionId;
    private Environment adminEnvironment;

    @BeforeEach
    void setUp() {
        doNothing().when(alertEventPublisher)
                .publishAlertCreatedEvent(any(), any(), anyString(), anyString());

        domainId = "alert-merge-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "alert-merge-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        // Register an admin environment (isAdmin=true will be passed to createAlert)
        String appName = "AlertMergeAdminApp-" + System.nanoTime();
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO("environment-global"), solutionId, null))
        );

        adminEnvironment = environmentRepository.findById(envResponse.environment().id())
                .orElseThrow();

        // Alerts provider is auto-registered by SolutionService.bootstrapEnvironmentGlobalZone()
    }

    /**
     * Property 9: Extended element merge with server precedence.
     *
     * <p>Each iteration generates a random mix of custom and reserved extended
     * element names with caller-provided values, creates an alert as admin,
     * and asserts:</p>
     * <ul>
     *   <li>All custom name/value pairs appear unchanged in the response</li>
     *   <li>All reserved names have server-populated values, NOT the caller-provided values</li>
     * </ul>
     *
     * <p><strong>Validates: Requirements 7.4, 7.5</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: alerts-service, Property 9: Extended element merge with server precedence")
    void adminExtendedElementsMergeWithServerPrecedence() {
        // Generate 1-5 custom extended elements
        int customCount = random.nextInt(5) + 1;
        Map<String, String> customElements = new LinkedHashMap<>();
        for (int i = 0; i < customCount; i++) {
            String name = "customField-" + UUID.randomUUID().toString().substring(0, 8);
            String value = "customValue-" + random.nextInt(100000);
            customElements.put(name, value);
        }

        // Randomly select 1-3 reserved names to include with spoofed values
        List<String> shuffled = new ArrayList<>(RESERVED_NAMES);
        Collections.shuffle(shuffled, random);
        int reservedCount = random.nextInt(3) + 1;
        List<String> selectedReserved = shuffled.subList(0, reservedCount);

        Map<String, String> spoofedReservedElements = new LinkedHashMap<>();
        for (String reservedName : selectedReserved) {
            spoofedReservedElements.put(reservedName, "spoofed-" + UUID.randomUUID());
        }

        // Build combined extended elements list (custom + reserved with spoofed values)
        List<SifExtendedElement> elements = new ArrayList<>();
        for (Map.Entry<String, String> entry : customElements.entrySet()) {
            elements.add(new SifExtendedElement(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, String> entry : spoofedReservedElements.entrySet()) {
            elements.add(new SifExtendedElement(entry.getKey(), entry.getValue()));
        }

        SifExtendedElements extendedElements = new SifExtendedElements(elements);

        // Build alert request
        AlertCreateBody createBody = new AlertCreateBody(
                randomReporter(),
                randomExchange(),
                randomAlertLevel(),
                null, null, null, // no messageID — defaults to environment-global zone
                null, null, null,
                null, null, null,
                extendedElements
        );

        // Create alert as admin (isAdmin=true)
        AlertDTO created = alertsService.createAlert(
                domainId, solutionId, createBody, adminEnvironment, true);

        // Extract response extended elements into a map
        assertThat(created.SIF_ExtendedElements())
                .as("Response should contain SIF_ExtendedElements")
                .isNotNull();
        assertThat(created.SIF_ExtendedElements().SIF_ExtendedElement())
                .as("Response SIF_ExtendedElement list should not be empty")
                .isNotEmpty();

        Map<String, String> responseElements = new LinkedHashMap<>();
        for (SifExtendedElement elem : created.SIF_ExtendedElements().SIF_ExtendedElement()) {
            responseElements.put(elem.Name(), elem.value());
        }

        // Assert: all custom name/value pairs preserved unchanged (Req 7.5)
        for (Map.Entry<String, String> entry : customElements.entrySet()) {
            assertThat(responseElements)
                    .as("Custom element '%s' should be present in response", entry.getKey())
                    .containsKey(entry.getKey());
            assertThat(responseElements.get(entry.getKey()))
                    .as("Custom element '%s' value should be preserved unchanged", entry.getKey())
                    .isEqualTo(entry.getValue());
        }

        // Assert: reserved names have server-populated values, NOT spoofed values (Req 7.4, 7.5)
        for (Map.Entry<String, String> entry : spoofedReservedElements.entrySet()) {
            String reservedName = entry.getKey();
            String spoofedValue = entry.getValue();

            if ("createdAt".equals(reservedName)) {
                // createdAt should be a server-generated ISO timestamp, not the spoofed value
                assertThat(responseElements).containsKey("createdAt");
                assertThat(responseElements.get("createdAt"))
                        .as("createdAt should be server-populated, not the spoofed value")
                        .isNotEqualTo(spoofedValue)
                        .isNotBlank();
            } else if ("reportingEnvironmentFingerprint".equals(reservedName)) {
                // Should be the environment's actual fingerprint (if present), not spoofed
                if (adminEnvironment.getFingerprint() != null) {
                    assertThat(responseElements).containsKey("reportingEnvironmentFingerprint");
                    assertThat(responseElements.get("reportingEnvironmentFingerprint"))
                            .as("reportingEnvironmentFingerprint should be server-populated from environment, not spoofed")
                            .isNotEqualTo(spoofedValue)
                            .isEqualTo(adminEnvironment.getFingerprint());
                }
            } else if ("sourceEnvironmentId".equals(reservedName)) {
                // Without messageID, sourceEnvironmentId may not be present in response
                // (only populated when messageID is provided and context found in Redis).
                // If present, it should NOT be the spoofed value.
                if (responseElements.containsKey("sourceEnvironmentId")) {
                    assertThat(responseElements.get("sourceEnvironmentId"))
                            .as("sourceEnvironmentId should be server-populated, not the spoofed value")
                            .isNotEqualTo(spoofedValue);
                }
            }
        }

        // Assert: server-populated elements that are always present
        assertThat(responseElements).containsKey("originatingZoneId");
        assertThat(responseElements.get("originatingZoneId"))
                .as("originatingZoneId should be environment-global (no messageID)")
                .isEqualTo("environment-global");

        assertThat(responseElements).containsKey("originatingContextId");
        assertThat(responseElements.get("originatingContextId"))
                .as("originatingContextId should be DEFAULT (no messageID)")
                .isEqualTo("DEFAULT");

        assertThat(responseElements).containsKey("createdAt");
        assertThat(responseElements.get("createdAt"))
                .as("createdAt should be a non-blank server timestamp")
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
}
