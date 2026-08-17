// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.AlertCreateBody;
import org.sifunity.broker.environment.repository.AlertRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying that alert creation returns 404 when no alerts provider
 * is registered in the environment-global zone.
 *
 * <p><strong>Validates: Requirements 10.1, 10.2</strong></p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Alert No Provider Registered Error")
class AlertNoProviderRegisteredTest {

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
    private Domain domain;
    private Solution solution;
    private Zone envGlobalZone;
    private AlertCreateBody validRequest;

    @BeforeEach
    void setUp() {
        requester = new Environment();
        requester.setId(UUID.randomUUID());
        requester.setFingerprint("fp-test");

        domain = new Domain();
        domain.setId(UUID.randomUUID());
        domain.setDomainId("test-domain");

        solution = new Solution();
        solution.setId(UUID.randomUUID());
        solution.setSolutionId("test-solution");

        envGlobalZone = new Zone();
        envGlobalZone.setId(UUID.randomUUID());
        envGlobalZone.setZoneId("environment-global");

        validRequest = new AlertCreateBody(
                "TestApp", Exchange.REQUEST, AlertLevel.WARNING,
                null, "Test description", null, null, null, null,
                null, null, null, null);
    }

    /**
     * Requirement 10.1: No provider registered → 404 with ALERT_004.
     * Zone exists but no provider registration for "alerts" service.
     */
    @Test
    @DisplayName("Returns 404 with ALERT_004 when no alerts provider registered")
    void createAlert_returns404_whenNoProviderRegistered() {
        // Given — zone exists but no provider registered for "alerts"
        when(domainRepository.findByDomainId("test-domain")).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), "test-solution"))
                .thenReturn(Optional.of(solution));
        when(zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global"))
                .thenReturn(Optional.of(envGlobalZone));
        // contextRepository returns empty → no DEFAULT context → provider check fails
        when(contextRepository.findByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT"))
                .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> alertsService.createAlert(
                "test-domain", "test-solution", validRequest, requester, false))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(ex -> {
                    SifBrokerException sbe = (SifBrokerException) ex;
                    assertThat(sbe.getErrorCode()).isEqualTo("ALERT_004");
                    assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        // Alert should NOT be persisted
        verify(alertRepository, never()).save(any());
    }

    /**
     * Requirement 10.2: Error message includes descriptive text about registering provider.
     */
    @Test
    @DisplayName("Error message describes that alerts provider must be registered")
    void createAlert_errorMessage_mentionsProviderRegistration() {
        // Given — zone exists but no provider
        when(domainRepository.findByDomainId("test-domain")).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), "test-solution"))
                .thenReturn(Optional.of(solution));
        when(zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global"))
                .thenReturn(Optional.of(envGlobalZone));
        // contextRepository returns empty → no DEFAULT context → provider check fails
        when(contextRepository.findByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT"))
                .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> alertsService.createAlert(
                "test-domain", "test-solution", validRequest, requester, false))
                .isInstanceOf(SifBrokerException.class)
                .hasMessageContaining("No provider registered")
                .hasMessageContaining("alerts")
                .hasMessageContaining("environment-global");
    }

    /**
     * Requirement 10.1 (negative path): When environment-global zone itself doesn't exist,
     * the service also returns 404 with ALERT_004.
     */
    @Test
    @DisplayName("Returns 404 with ALERT_004 when environment-global zone does not exist")
    void createAlert_returns404_whenEnvironmentGlobalZoneMissing() {
        // Given — zone does not exist
        when(domainRepository.findByDomainId("test-domain")).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), "test-solution"))
                .thenReturn(Optional.of(solution));
        when(zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global"))
                .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> alertsService.createAlert(
                "test-domain", "test-solution", validRequest, requester, false))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(ex -> {
                    SifBrokerException sbe = (SifBrokerException) ex;
                    assertThat(sbe.getErrorCode()).isEqualTo("ALERT_004");
                    assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verify(alertRepository, never()).save(any());
    }
}
