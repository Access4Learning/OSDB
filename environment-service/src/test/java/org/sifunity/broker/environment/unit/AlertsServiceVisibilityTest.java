// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.dto.AlertCreateBody;
import org.sifunity.broker.environment.dto.AlertDTO;
import org.sifunity.broker.environment.dto.MessageContext;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.AlertEventPublisher;
import org.sifunity.broker.environment.service.AlertsService;
import org.sifunity.broker.environment.service.MessageContextCacheService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertsService visibility changes.
 *
 * <p>Validates that:</p>
 * <ul>
 *   <li>createAlert sets targetEnvironmentId from resolved sourceEnvironmentId</li>
 *   <li>createAlert leaves targetEnvironmentId null when no messageID</li>
 *   <li>listAlerts (non-admin) calls findVisibleAlerts</li>
 *   <li>listAlerts (admin) calls findAll unchanged</li>
 *   <li>getAlertById (non-admin) calls findVisibleAlertById</li>
 *   <li>getAlertById (admin) calls findById unchanged</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 2.1, 2.5, 3.1, 3.5</strong></p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertsService Visibility Changes")
class AlertsServiceVisibilityTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private MessageContextCacheService messageContextCacheService;

    @Mock
    private ProviderRegistrationRepository providerRegistrationRepository;

    @Mock
    private ContextRepository contextRepository;

    @Mock
    private ContextServiceRepository contextServiceRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private AlertEventPublisher alertEventPublisher;

    @InjectMocks
    private AlertsService alertsService;

    private Environment requester;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        requester = new Environment();
        requester.setId(requesterId);
        requester.setFingerprint("test-fingerprint-abc123");
    }

    @Nested
    @DisplayName("createAlert - targetEnvironmentId persistence")
    class CreateAlertTargetEnvironment {

        private Domain domain;
        private Solution solution;
        private Zone envGlobalZone;
        private Context defaultContext;
        private ContextService alertsCs;

        @BeforeEach
        void setUpCreateAlert() {
            domain = new Domain();
            domain.setId(UUID.randomUUID());
            domain.setDomainId("test-domain");

            solution = new Solution();
            solution.setId(UUID.randomUUID());
            solution.setSolutionId("test-solution");

            envGlobalZone = new Zone();
            envGlobalZone.setId(UUID.randomUUID());
            envGlobalZone.setZoneId("environment-global");
            envGlobalZone.setSolutionUuid(solution.getId());

            defaultContext = new Context();
            defaultContext.setId(UUID.randomUUID());
            defaultContext.setContextId("DEFAULT");
            defaultContext.setZoneUuid(envGlobalZone.getId());

            alertsCs = new ContextService();
            alertsCs.setId(UUID.randomUUID());
            alertsCs.setContextUuid(defaultContext.getId());
            alertsCs.setServiceName("alerts");

            // Common stubs for createAlert resolution chain
            when(domainRepository.findByDomainId("test-domain")).thenReturn(Optional.of(domain));
            when(solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), "test-solution"))
                    .thenReturn(Optional.of(solution));
            when(zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global"))
                    .thenReturn(Optional.of(envGlobalZone));
            when(contextRepository.findByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT"))
                    .thenReturn(Optional.of(defaultContext));
            when(contextServiceRepository.findByContextUuidAndServiceName(defaultContext.getId(), "alerts"))
                    .thenReturn(Optional.of(alertsCs));
            when(providerRegistrationRepository.findByContextServiceUuid(alertsCs.getId()))
                    .thenReturn(Optional.of(ProviderRegistration.builder().id(UUID.randomUUID()).build()));

            // Alert save: return argument with generated ID
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
         * Requirement 1.1: When alert created with messageID resolving to sourceEnvironmentId,
         * targetEnvironmentId SHALL be set to that value.
         */
        @Test
        @DisplayName("createAlert sets targetEnvironmentId from resolved sourceEnvironmentId")
        void createAlert_setsTargetEnvironmentId_whenMessageIdResolvesToSource() {
            // Given
            UUID sourceEnvId = UUID.randomUUID();
            String messageId = "msg-" + UUID.randomUUID();

            MessageContext messageContext = new MessageContext(
                    sourceEnvId.toString(), "environment-global", "DEFAULT");
            when(messageContextCacheService.getMessageContext("test-domain", "test-solution", messageId))
                    .thenReturn(Optional.of(messageContext));

            AlertCreateBody request = new AlertCreateBody(
                    "TestApp", Exchange.REQUEST, AlertLevel.WARNING,
                    null, null, messageId,
                    null, null, null, null, null, null, null);

            // When
            alertsService.createAlert("test-domain", "test-solution", request, requester, false);

            // Then
            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(captor.capture());
            Alert persisted = captor.getValue();

            assertThat(persisted.getTargetEnvironmentId())
                    .as("targetEnvironmentId should equal resolved sourceEnvironmentId")
                    .isEqualTo(sourceEnvId);
        }

        /**
         * Requirement 1.2: When alert created without messageID,
         * targetEnvironmentId SHALL be NULL.
         */
        @Test
        @DisplayName("createAlert leaves targetEnvironmentId null when no messageID")
        void createAlert_leavesTargetNull_whenNoMessageId() {
            // Given — no messageID (broadcast alert)
            AlertCreateBody request = new AlertCreateBody(
                    "TestApp", Exchange.EVENT, AlertLevel.INFO,
                    null, "Broadcast alert", null,
                    null, null, null, null, null, null, null);

            // When
            alertsService.createAlert("test-domain", "test-solution", request, requester, false);

            // Then
            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(captor.capture());
            Alert persisted = captor.getValue();

            assertThat(persisted.getTargetEnvironmentId())
                    .as("targetEnvironmentId should be NULL for broadcast alerts")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("listAlerts - visibility routing")
    class ListAlerts {

        private Alert sampleAlert;

        @BeforeEach
        void setUpListAlerts() {
            sampleAlert = new Alert();
            sampleAlert.setId(UUID.randomUUID());
            sampleAlert.setReporter("TestApp");
            sampleAlert.setExchange(Exchange.REQUEST);
            sampleAlert.setLevel(AlertLevel.ERROR);
            sampleAlert.setEnvironment(requester);
            sampleAlert.setCreatedAt(Instant.now());
        }

        /**
         * Requirement 2.1: Non-admin caller uses findVisibleAlerts for expanded visibility.
         */
        @Test
        @DisplayName("listAlerts (non-admin) calls findVisibleAlerts with caller ID")
        void listAlerts_nonAdmin_callsFindVisibleAlerts() {
            // Given
            when(alertRepository.findVisibleAlerts(requesterId))
                    .thenReturn(List.of(sampleAlert));

            // When
            List<AlertDTO> result = alertsService.listAlerts(requester, false);

            // Then
            verify(alertRepository).findVisibleAlerts(requesterId);
            verify(alertRepository, never()).findAll();
            assertThat(result).hasSize(1);
        }

        /**
         * Requirement 2.5: Admin caller uses findAll unchanged.
         */
        @Test
        @DisplayName("listAlerts (admin) calls findAll unchanged")
        void listAlerts_admin_callsFindAll() {
            // Given
            when(alertRepository.findAll()).thenReturn(List.of(sampleAlert));

            // When
            List<AlertDTO> result = alertsService.listAlerts(requester, true);

            // Then
            verify(alertRepository).findAll();
            verify(alertRepository, never()).findVisibleAlerts(any());
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getAlertById - visibility routing")
    class GetAlertById {

        private UUID alertId;
        private Alert sampleAlert;

        @BeforeEach
        void setUpGetAlertById() {
            alertId = UUID.randomUUID();
            sampleAlert = new Alert();
            sampleAlert.setId(alertId);
            sampleAlert.setReporter("TestApp");
            sampleAlert.setExchange(Exchange.RESPONSE);
            sampleAlert.setLevel(AlertLevel.WARNING);
            sampleAlert.setEnvironment(requester);
            sampleAlert.setCreatedAt(Instant.now());
        }

        /**
         * Requirement 3.1: Non-admin caller uses findVisibleAlertById for expanded visibility.
         */
        @Test
        @DisplayName("getAlertById (non-admin) calls findVisibleAlertById with alert ID and caller ID")
        void getAlertById_nonAdmin_callsFindVisibleAlertById() {
            // Given
            when(alertRepository.findVisibleAlertById(alertId, requesterId))
                    .thenReturn(Optional.of(sampleAlert));

            // When
            AlertDTO result = alertsService.getAlertById(alertId, requester, false);

            // Then
            verify(alertRepository).findVisibleAlertById(alertId, requesterId);
            verify(alertRepository, never()).findById(any());
            assertThat(result.id()).isEqualTo(alertId);
        }

        /**
         * Requirement 3.5: Admin caller uses findById unchanged.
         */
        @Test
        @DisplayName("getAlertById (admin) calls findById unchanged")
        void getAlertById_admin_callsFindById() {
            // Given
            when(alertRepository.findById(alertId)).thenReturn(Optional.of(sampleAlert));

            // When
            AlertDTO result = alertsService.getAlertById(alertId, requester, true);

            // Then
            verify(alertRepository).findById(alertId);
            verify(alertRepository, never()).findVisibleAlertById(any(), any());
            assertThat(result.id()).isEqualTo(alertId);
        }
    }
}
