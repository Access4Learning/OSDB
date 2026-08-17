// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.environment.domain.Alert;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.AlertCreateBody;
import org.sifunity.broker.environment.dto.AlertDTO;
import org.sifunity.broker.environment.repository.AlertRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.service.AlertEventPublisher;
import org.sifunity.broker.environment.service.AlertsService;
import org.sifunity.broker.environment.service.MessageContextCacheService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying that event publication failure does not block alert creation.
 *
 * <p>When {@link AlertEventPublisher} throws an exception, the alert should still
 * be persisted and the service should return the created alert (HTTP 201).</p>
 *
 * <p><strong>Validates: Requirement 5.6</strong></p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Alert Event Publication Failure Isolation")
class AlertEventPublicationFailureTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private MessageContextCacheService messageContextCacheService;

    @Mock
    private ProviderRegistrationRepository providerRegistrationRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private org.sifunity.broker.environment.repository.ContextRepository contextRepository;

    @Mock
    private org.sifunity.broker.environment.repository.ContextServiceRepository contextServiceRepository;

    @Mock
    private AlertEventPublisher alertEventPublisher;

    @InjectMocks
    private AlertsService alertsService;

    private Environment requester;
    private Domain domain;
    private Solution solution;
    private Zone envGlobalZone;

    @BeforeEach
    void setUp() {
        requester = new Environment();
        requester.setId(UUID.randomUUID());
        requester.setFingerprint("fp-test-env");

        domain = new Domain();
        domain.setId(UUID.randomUUID());
        domain.setDomainId("test-domain");

        solution = new Solution();
        solution.setId(UUID.randomUUID());
        solution.setSolutionId("test-solution");

        envGlobalZone = new Zone();
        envGlobalZone.setId(UUID.randomUUID());
        envGlobalZone.setZoneId("environment-global");
    }

    /**
     * Requirement 5.6: Event publication failure does not block alert creation.
     * Alert should still be persisted and returned successfully.
     */
    @Test
    @DisplayName("Alert creation returns successfully even when event publication throws exception")
    void createAlert_returnsSuccessfully_whenEventPublicationFails() {
        // Given
        AlertCreateBody request = new AlertCreateBody(
                "TestApp", Exchange.REQUEST, AlertLevel.ERROR,
                null, "Test alert", null, null, null, null,
                null, null, null, null);

        when(domainRepository.findByDomainId("test-domain")).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), "test-solution"))
                .thenReturn(Optional.of(solution));
        when(zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global"))
                .thenReturn(Optional.of(envGlobalZone));

        // Stub context → contextService → provider resolution chain
        org.sifunity.broker.environment.domain.Context defaultContext = new org.sifunity.broker.environment.domain.Context();
        defaultContext.setId(UUID.randomUUID());
        defaultContext.setContextId("DEFAULT");
        when(contextRepository.findByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT"))
                .thenReturn(Optional.of(defaultContext));

        org.sifunity.broker.environment.domain.ContextService alertsCs = new org.sifunity.broker.environment.domain.ContextService();
        alertsCs.setId(UUID.randomUUID());
        alertsCs.setContextUuid(defaultContext.getId());
        alertsCs.setServiceName("alerts");
        when(contextServiceRepository.findByContextUuidAndServiceName(defaultContext.getId(), "alerts"))
                .thenReturn(Optional.of(alertsCs));

        when(providerRegistrationRepository.findByContextServiceUuid(alertsCs.getId())).thenReturn(java.util.Optional.of(org.sifunity.broker.environment.domain.ProviderRegistration.builder().id(java.util.UUID.randomUUID()).build()));

        Alert savedAlert = new Alert();
        savedAlert.setId(UUID.randomUUID());
        savedAlert.setReporter("TestApp");
        savedAlert.setExchange(Exchange.REQUEST);
        savedAlert.setLevel(AlertLevel.ERROR);
        savedAlert.setDescription("Test alert");
        savedAlert.setEnvironment(requester);
        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);

        // Event publisher throws RuntimeException
        doThrow(new RuntimeException("Events connector unavailable"))
                .when(alertEventPublisher)
                .publishAlertCreatedEvent(any(Alert.class), any(AlertDTO.class),
                        eq("test-domain"), eq("test-solution"));

        // When
        AlertDTO result = alertsService.createAlert(
                "test-domain", "test-solution", request, requester, false);

        // Then — alert created successfully despite event publication failure
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(savedAlert.getId());
        assertThat(result.reporter()).isEqualTo("TestApp");

        // Verify alert was persisted
        verify(alertRepository).save(any(Alert.class));

        // Verify event publication was attempted
        verify(alertEventPublisher).publishAlertCreatedEvent(
                any(Alert.class), any(AlertDTO.class),
                eq("test-domain"), eq("test-solution"));
    }
}
