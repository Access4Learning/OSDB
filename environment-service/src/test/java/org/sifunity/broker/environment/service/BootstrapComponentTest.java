// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.config.BootstrapProperties;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BootstrapComponent.
 *
 * <p>Tests the bootstrap logic for creating admin environments on startup,
 * including configuration validation, idempotency, and error handling.</p>
 */
@ExtendWith(MockitoExtension.class)
class BootstrapComponentTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ContextRepository contextRepository;

    @Mock
    private ProviderRegistrationRepository providerRegistrationRepository;

    @Mock
    private BootstrapProperties bootstrapProperties;

    @Mock
    private BootstrapProperties.AdminCredentials adminCredentials;

    @Mock
    private org.sifunity.broker.environment.service.ContextServiceService contextServiceService;

    private BootstrapComponent bootstrapComponent;

    @BeforeEach
    void setUp() {
        bootstrapComponent = new BootstrapComponent(
            environmentRepository, domainRepository, solutionRepository, zoneRepository, contextRepository, providerRegistrationRepository, contextServiceService, bootstrapProperties);
    }

    @Test
    void bootstrapAdmin_CreatesAdminWhenDatabaseIsEmpty() {
        when(bootstrapProperties.isEnabled()).thenReturn(true);
        when(bootstrapProperties.getAdmin()).thenReturn(adminCredentials);
        when(adminCredentials.getSessionToken()).thenReturn("test-admin-token");
        when(adminCredentials.getSharedSecret()).thenReturn("test-admin-secret");

        when(domainRepository.existsByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)).thenReturn(false);
        java.util.UUID domainUuid = java.util.UUID.randomUUID();
        when(domainRepository.save(any(org.sifunity.broker.environment.domain.Domain.class))).thenAnswer(inv -> {
            org.sifunity.broker.environment.domain.Domain d = inv.getArgument(0);
            d.setId(domainUuid);
            return d;
        });
        org.sifunity.broker.environment.domain.Domain savedDomain = new org.sifunity.broker.environment.domain.Domain();
        savedDomain.setId(domainUuid);
        savedDomain.setDomainId(GlobalAdminConstants.GLOBAL_DOMAIN);
        when(domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN))
            .thenReturn(Optional.of(savedDomain));

        java.util.UUID solUuid = java.util.UUID.randomUUID();
        when(solutionRepository.existsByDomainUuidAndSolutionId(
            any(java.util.UUID.class), eq(GlobalAdminConstants.GLOBAL_SOLUTION))).thenReturn(false);
        when(solutionRepository.save(any(org.sifunity.broker.environment.domain.Solution.class))).thenAnswer(inv -> {
            org.sifunity.broker.environment.domain.Solution s = inv.getArgument(0);
            s.setId(solUuid);
            return s;
        });
        org.sifunity.broker.environment.domain.Solution globalSol = new org.sifunity.broker.environment.domain.Solution();
        globalSol.setId(solUuid);
        globalSol.setSolutionId(GlobalAdminConstants.GLOBAL_SOLUTION);
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, GlobalAdminConstants.GLOBAL_SOLUTION)).thenReturn(Optional.of(globalSol));

        // Zone/context stubs for ensureEnvironmentGlobalZone
        java.util.UUID zoneUuid = java.util.UUID.randomUUID();
        when(zoneRepository.existsBySolutionUuidAndZoneId(any(java.util.UUID.class), eq("environment-global"))).thenReturn(false);
        org.sifunity.broker.environment.domain.Zone envGlobalZone = new org.sifunity.broker.environment.domain.Zone();
        envGlobalZone.setId(zoneUuid);
        envGlobalZone.setZoneId("environment-global");
        when(zoneRepository.save(any(org.sifunity.broker.environment.domain.Zone.class))).thenReturn(envGlobalZone);
        when(zoneRepository.findBySolutionUuidAndZoneId(any(java.util.UUID.class), eq("environment-global"))).thenReturn(Optional.of(envGlobalZone));
        when(contextRepository.existsByZoneUuidAndContextId(zoneUuid, "DEFAULT")).thenReturn(false);
        org.sifunity.broker.environment.domain.Context savedContext = new org.sifunity.broker.environment.domain.Context();
        savedContext.setId(java.util.UUID.randomUUID());
        savedContext.setZoneUuid(zoneUuid);
        savedContext.setContextId("DEFAULT");
        when(contextRepository.save(any(org.sifunity.broker.environment.domain.Context.class))).thenReturn(savedContext);
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, "DEFAULT")).thenReturn(java.util.Optional.of(savedContext));

        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            "ADMIN", solUuid)).thenReturn(Optional.empty());

        // Stub for BROKER_SERVICES environment creation in ensureEnvironmentGlobalZone
        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            eq("BROKER_SERVICES_" + solUuid), eq(solUuid))).thenReturn(Optional.empty());
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> {
            Environment e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(java.util.UUID.randomUUID());
            }
            return e;
        });
        when(providerRegistrationRepository.findByContextServiceUuid(any())).thenReturn(java.util.Optional.empty());
        when(providerRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contextServiceService.resolveOrCreate(any(), any(), any())).thenReturn(java.util.UUID.randomUUID());

        bootstrapComponent.bootstrapAdmin();

        ArgumentCaptor<Environment> envCaptor = ArgumentCaptor.forClass(Environment.class);
        verify(environmentRepository, atLeast(1)).save(envCaptor.capture());

        // Find the ADMIN environment among saved environments
        Environment savedAdmin = envCaptor.getAllValues().stream()
            .filter(e -> "ADMIN".equals(e.getApplicationName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("ADMIN environment not saved"));

        assertThat(savedAdmin.getApplicationName()).isEqualTo("ADMIN");
        assertThat(savedAdmin.getType()).isEqualTo(EnvironmentType.BROKERED);
        assertThat(savedAdmin.getSolutionUuid()).isEqualTo(solUuid);
        assertThat(savedAdmin.getSessionToken()).isEqualTo("test-admin-token");
        assertThat(savedAdmin.getSharedSecret()).isEqualTo("test-admin-secret");
    }

    @Test
    void bootstrapAdmin_SkipsCreationWhenAdminExists() {
        when(bootstrapProperties.isEnabled()).thenReturn(true);
        when(bootstrapProperties.getAdmin()).thenReturn(adminCredentials);
        when(adminCredentials.getSessionToken()).thenReturn("test-admin-token");
        when(adminCredentials.getSharedSecret()).thenReturn("test-admin-secret");

        when(domainRepository.existsByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)).thenReturn(true);
        org.sifunity.broker.environment.domain.Domain globalDomain = new org.sifunity.broker.environment.domain.Domain();
        globalDomain.setId(java.util.UUID.randomUUID());
        globalDomain.setDomainId(GlobalAdminConstants.GLOBAL_DOMAIN);
        when(domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)).thenReturn(Optional.of(globalDomain));
        when(solutionRepository.existsByDomainUuidAndSolutionId(
            globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)).thenReturn(true);

        java.util.UUID solUuid = java.util.UUID.randomUUID();
        org.sifunity.broker.environment.domain.Solution globalSol = new org.sifunity.broker.environment.domain.Solution();
        globalSol.setId(solUuid);
        globalSol.setDomainUuid(globalDomain.getId());
        globalSol.setSolutionId(GlobalAdminConstants.GLOBAL_SOLUTION);
        when(solutionRepository.findByDomainUuidAndSolutionId(globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)).thenReturn(Optional.of(globalSol));

        // Zone/context stubs for ensureEnvironmentGlobalZone
        java.util.UUID zoneUuid = java.util.UUID.randomUUID();
        when(zoneRepository.existsBySolutionUuidAndZoneId(solUuid, "environment-global")).thenReturn(true);
        org.sifunity.broker.environment.domain.Zone envGlobalZone = new org.sifunity.broker.environment.domain.Zone();
        envGlobalZone.setId(zoneUuid);
        envGlobalZone.setZoneId("environment-global");
        when(zoneRepository.findBySolutionUuidAndZoneId(solUuid, "environment-global")).thenReturn(Optional.of(envGlobalZone));
        when(contextRepository.existsByZoneUuidAndContextId(zoneUuid, "DEFAULT")).thenReturn(true);

        // Stub for BROKER_SERVICES environment (already exists)
        Environment brokerEnv = Environment.builder()
            .applicationName("BROKER_SERVICES_" + solUuid)
            .type(EnvironmentType.BROKERED)
            .solutionUuid(solUuid)
            .sessionToken("internal-broker-services-" + solUuid)
            .sharedSecret("not-used")
            .build();
        brokerEnv.setId(java.util.UUID.randomUUID());
        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            eq("BROKER_SERVICES_" + solUuid), eq(solUuid))).thenReturn(Optional.of(brokerEnv));
        when(contextServiceService.resolveOrCreate(any(), any(), any())).thenReturn(java.util.UUID.randomUUID());
        when(providerRegistrationRepository.findByContextServiceUuid(any())).thenReturn(java.util.Optional.of(org.sifunity.broker.environment.domain.ProviderRegistration.builder().id(java.util.UUID.randomUUID()).build()));

        // Need contextRepository.findByZoneUuidAndContextId for ensureEnvironmentGlobalZone
        org.sifunity.broker.environment.domain.Context defaultCtx = new org.sifunity.broker.environment.domain.Context();
        defaultCtx.setId(java.util.UUID.randomUUID());
        defaultCtx.setZoneUuid(zoneUuid);
        defaultCtx.setContextId("DEFAULT");
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, "DEFAULT")).thenReturn(java.util.Optional.of(defaultCtx));

        Environment existingAdmin = Environment.builder()
            .applicationName("ADMIN")
            .type(EnvironmentType.BROKERED)
            .solutionUuid(solUuid)
            .sessionToken("existing-token")
            .sharedSecret("existing-secret")
            .defaultZoneId("default")
            .build();
        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            "ADMIN", solUuid)).thenReturn(Optional.of(existingAdmin));

        bootstrapComponent.bootstrapAdmin();

        verify(environmentRepository, never()).save(any(Environment.class));
    }

    @Test
    void bootstrapAdmin_HandlesDisabledConfiguration() {
        when(bootstrapProperties.isEnabled()).thenReturn(false);

        bootstrapComponent.bootstrapAdmin();

        verify(environmentRepository, never()).save(any());
        verify(domainRepository, never()).existsByDomainId(any());
    }

    @Test
    void bootstrapAdmin_HandlesMissingCredentials() {
        when(bootstrapProperties.isEnabled()).thenReturn(true);
        when(bootstrapProperties.getAdmin()).thenReturn(null);

        bootstrapComponent.bootstrapAdmin();

        verify(environmentRepository, never()).save(any());
    }

    @Test
    void bootstrapAdmin_HandlesMissingSessionToken() {
        when(bootstrapProperties.isEnabled()).thenReturn(true);
        when(bootstrapProperties.getAdmin()).thenReturn(adminCredentials);
        when(adminCredentials.getSessionToken()).thenReturn(null);

        bootstrapComponent.bootstrapAdmin();

        verify(environmentRepository, never()).save(any());
    }

    @Test
    void bootstrapAdmin_HandlesEmptySessionToken() {
        when(bootstrapProperties.isEnabled()).thenReturn(true);
        when(bootstrapProperties.getAdmin()).thenReturn(adminCredentials);
        when(adminCredentials.getSessionToken()).thenReturn("   ");

        bootstrapComponent.bootstrapAdmin();

        verify(environmentRepository, never()).save(any());
    }

    @Test
    void bootstrapAdmin_HandlesMissingSharedSecret() {
        when(bootstrapProperties.isEnabled()).thenReturn(true);
        when(bootstrapProperties.getAdmin()).thenReturn(adminCredentials);
        when(adminCredentials.getSessionToken()).thenReturn("test-admin-token");
        when(adminCredentials.getSharedSecret()).thenReturn(null);

        bootstrapComponent.bootstrapAdmin();

        verify(environmentRepository, never()).save(any());
    }

    @Test
    void bootstrapAdmin_HandlesDatabaseErrors() {
        when(bootstrapProperties.isEnabled()).thenReturn(true);
        when(bootstrapProperties.getAdmin()).thenReturn(adminCredentials);
        when(adminCredentials.getSessionToken()).thenReturn("test-admin-token");
        when(adminCredentials.getSharedSecret()).thenReturn("test-admin-secret");

        when(domainRepository.existsByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN))
            .thenThrow(new RuntimeException("Database connection failed"));

        // Should not throw
        bootstrapComponent.bootstrapAdmin();

        verify(environmentRepository, never()).save(any());
    }
}
