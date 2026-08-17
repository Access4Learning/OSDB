// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.*;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.clearInvocations;

/**
 * Property-based tests for target environment persistence during alert creation.
 *
 * <p><strong>Property 1: Target Environment Persistence Round-Trip</strong> —
 * For any alert created with a messageID that resolves to a sourceEnvironmentId,
 * the persisted alert entity SHALL have targetEnvironmentId equal to that resolved
 * sourceEnvironmentId.</p>
 *
 * <p><strong>Property 2: Null Target for Broadcast Alerts</strong> —
 * For any alert created without a messageID, the persisted alert entity SHALL have
 * targetEnvironmentId equal to NULL.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3</strong></p>
 */
@Tag("Feature: alert-visibility-expansion, Property 1: Target Environment Persistence Round-Trip")
@Tag("Feature: alert-visibility-expansion, Property 2: Null Target for Broadcast Alerts")
class AlertTargetPersistencePropertyTest {

    private AlertRepository alertRepository;
    private MessageContextCacheService messageContextCacheService;
    private ProviderRegistrationRepository providerRegistrationRepository;
    private ContextRepository contextRepository;
    private ContextServiceRepository contextServiceRepository;
    private ZoneRepository zoneRepository;
    private EnvironmentRepository environmentRepository;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;
    private AlertEventPublisher alertEventPublisher;
    private AlertsService alertsService;

    // Shared test infrastructure UUIDs
    private UUID solutionUuid;
    private UUID zoneUuid;
    private UUID contextUuid;
    private UUID contextServiceUuid;
    private UUID providerUuid;
    private Environment requester;

    @BeforeProperty
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        messageContextCacheService = mock(MessageContextCacheService.class);
        providerRegistrationRepository = mock(ProviderRegistrationRepository.class);
        contextRepository = mock(ContextRepository.class);
        contextServiceRepository = mock(ContextServiceRepository.class);
        zoneRepository = mock(ZoneRepository.class);
        environmentRepository = mock(EnvironmentRepository.class);
        domainRepository = mock(DomainRepository.class);
        solutionRepository = mock(SolutionRepository.class);
        alertEventPublisher = mock(AlertEventPublisher.class);

        alertsService = new AlertsService(
                alertRepository,
                messageContextCacheService,
                providerRegistrationRepository,
                contextRepository,
                contextServiceRepository,
                zoneRepository,
                environmentRepository,
                domainRepository,
                solutionRepository,
                alertEventPublisher
        );

        // Set up common infrastructure mocks
        solutionUuid = UUID.randomUUID();
        zoneUuid = UUID.randomUUID();
        contextUuid = UUID.randomUUID();
        contextServiceUuid = UUID.randomUUID();
        providerUuid = UUID.randomUUID();

        // Domain resolution
        Domain domain = new Domain();
        domain.setId(UUID.randomUUID());
        domain.setDomainId("test-domain");
        when(domainRepository.findByDomainId("test-domain"))
                .thenReturn(Optional.of(domain));

        // Solution resolution
        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId("test-solution");
        when(solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), "test-solution"))
                .thenReturn(Optional.of(solution));

        // Zone resolution (environment-global)
        Zone zone = new Zone();
        zone.setId(zoneUuid);
        zone.setZoneId("environment-global");
        zone.setSolutionUuid(solutionUuid);
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, "environment-global"))
                .thenReturn(Optional.of(zone));

        // Context resolution (DEFAULT)
        Context context = new Context();
        context.setId(contextUuid);
        context.setContextId("DEFAULT");
        context.setZoneUuid(zoneUuid);
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, "DEFAULT"))
                .thenReturn(Optional.of(context));

        // ContextService resolution
        ContextService contextService = new ContextService();
        contextService.setId(contextServiceUuid);
        contextService.setContextUuid(contextUuid);
        contextService.setServiceName("alerts");
        when(contextServiceRepository.findByContextUuidAndServiceName(contextUuid, "alerts"))
                .thenReturn(Optional.of(contextService));

        // Provider registration exists
        ProviderRegistration provider = new ProviderRegistration();
        provider.setId(providerUuid);
        when(providerRegistrationRepository.findByContextServiceUuid(contextServiceUuid))
                .thenReturn(Optional.of(provider));

        // Requester environment
        requester = mock(Environment.class);
        when(requester.getId()).thenReturn(UUID.randomUUID());
        when(requester.getFingerprint()).thenReturn("test-fingerprint-abc123");

        // Alert save: capture and return with generated ID
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert alert = inv.getArgument(0);
            if (alert.getId() == null) {
                alert.setId(UUID.randomUUID());
            }
            return alert;
        });

        // Stub event publisher
        doNothing().when(alertEventPublisher)
                .publishAlertCreatedEvent(any(), any(), anyString(), anyString());
    }

    /**
     * Property 1: Target Environment Persistence Round-Trip.
     *
     * <p>For any alert created with a messageID that resolves to a sourceEnvironmentId,
     * the persisted alert entity SHALL have targetEnvironmentId equal to that resolved
     * sourceEnvironmentId.</p>
     *
     * <p><strong>Validates: Requirements 1.1, 1.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: alert-visibility-expansion, Property 1: Target Environment Persistence Round-Trip")
    void directedAlertPersistsTargetEnvironmentId(
            @ForAll("randomSourceEnvironmentIds") UUID sourceEnvironmentId,
            @ForAll("randomExchanges") Exchange exchange,
            @ForAll("randomAlertLevels") AlertLevel level,
            @ForAll("randomReporters") String reporter) {

        // Reset mock invocation tracking before each try
        clearInvocations(alertRepository);

        // Mock message context cache to return the sourceEnvironmentId
        String messageId = "msg-" + UUID.randomUUID();
        MessageContext messageContext = new MessageContext(
                sourceEnvironmentId.toString(),
                "environment-global",
                "DEFAULT"
        );
        when(messageContextCacheService.getMessageContext("test-domain", "test-solution", messageId))
                .thenReturn(Optional.of(messageContext));

        // Build alert creation request WITH messageID
        AlertCreateBody createBody = new AlertCreateBody(
                reporter, exchange, level,
                null, null, messageId,
                null, null, null,
                null, null, null,
                null
        );

        // Act: create alert
        alertsService.createAlert("test-domain", "test-solution", createBody, requester, false);

        // Capture persisted alert entity
        var alertCaptor = org.mockito.ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        Alert persistedAlert = alertCaptor.getValue();

        // Assert: targetEnvironmentId equals the resolved sourceEnvironmentId
        assertThat(persistedAlert.getTargetEnvironmentId())
                .as("targetEnvironmentId should equal resolved sourceEnvironmentId %s", sourceEnvironmentId)
                .isEqualTo(sourceEnvironmentId);
    }

    /**
     * Property 2: Null Target for Broadcast Alerts.
     *
     * <p>For any alert created without a messageID, the persisted alert entity SHALL
     * have targetEnvironmentId equal to NULL.</p>
     *
     * <p><strong>Validates: Requirements 1.2</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: alert-visibility-expansion, Property 2: Null Target for Broadcast Alerts")
    void broadcastAlertPersistsNullTargetEnvironmentId(
            @ForAll("randomExchanges") Exchange exchange,
            @ForAll("randomAlertLevels") AlertLevel level,
            @ForAll("randomReporters") String reporter) {

        // Reset mock invocation tracking before each try
        clearInvocations(alertRepository);

        // Build alert creation request WITHOUT messageID (broadcast)
        AlertCreateBody createBody = new AlertCreateBody(
                reporter, exchange, level,
                null, null, null,  // no messageID
                null, null, null,
                null, null, null,
                null
        );

        // Act: create alert
        alertsService.createAlert("test-domain", "test-solution", createBody, requester, false);

        // Capture persisted alert entity
        var alertCaptor = org.mockito.ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        Alert persistedAlert = alertCaptor.getValue();

        // Assert: targetEnvironmentId is NULL for broadcast alerts
        assertThat(persistedAlert.getTargetEnvironmentId())
                .as("targetEnvironmentId should be NULL for broadcast alerts (no messageID)")
                .isNull();
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UUID> randomSourceEnvironmentIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<Exchange> randomExchanges() {
        return Arbitraries.of(Exchange.values());
    }

    @Provide
    Arbitrary<AlertLevel> randomAlertLevels() {
        return Arbitraries.of(AlertLevel.values());
    }

    @Provide
    Arbitrary<String> randomReporters() {
        return Arbitraries.of(
                "StudentInfoApp", "LearningMgmt", "AssessmentSvc",
                "GradebookApp", "AttendanceTracker", "TransportSvc"
        ).map(name -> name + "-" + UUID.randomUUID().toString().substring(0, 6));
    }
}
