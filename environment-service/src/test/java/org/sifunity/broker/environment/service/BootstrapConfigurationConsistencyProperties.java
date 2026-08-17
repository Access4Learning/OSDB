// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.*;

/**
 * Property-based tests for BootstrapComponent configuration consistency.
 */
class BootstrapConfigurationConsistencyProperties {

    @Property(tries = 10)
    void bootstrapCreatesAdminWithConfiguredCredentials(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("sharedSecrets") String sharedSecret) {

        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        ProviderRegistrationRepository providerRegistrationRepository = mock(ProviderRegistrationRepository.class);
        BootstrapProperties bootstrapProperties = mock(BootstrapProperties.class);
        BootstrapProperties.AdminCredentials adminCredentials = mock(BootstrapProperties.AdminCredentials.class);

        when(bootstrapProperties.isEnabled()).thenReturn(true);
        when(bootstrapProperties.getAdmin()).thenReturn(adminCredentials);
        when(adminCredentials.getSessionToken()).thenReturn(sessionToken);
        when(adminCredentials.getSharedSecret()).thenReturn(sharedSecret);

        // Domain lookup for ensureGlobalSolutionExists
        org.sifunity.broker.environment.domain.Domain globalDomain = new org.sifunity.broker.environment.domain.Domain();
        globalDomain.setId(java.util.UUID.randomUUID());
        globalDomain.setDomainId(GlobalAdminConstants.GLOBAL_DOMAIN);

        when(domainRepository.existsByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)).thenReturn(true);
        when(domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)).thenReturn(Optional.of(globalDomain));
        when(solutionRepository.existsByDomainUuidAndSolutionId(
            globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)).thenReturn(true);

        // Solution lookup for admin env creation
        org.sifunity.broker.environment.domain.Solution globalSolution = new org.sifunity.broker.environment.domain.Solution();
        globalSolution.setId(java.util.UUID.randomUUID());
        globalSolution.setDomainUuid(globalDomain.getId());
        globalSolution.setSolutionId(GlobalAdminConstants.GLOBAL_SOLUTION);
        when(solutionRepository.findByDomainUuidAndSolutionId(globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)).thenReturn(Optional.of(globalSolution));

        // Zone/context for ensureEnvironmentGlobalZone
        when(zoneRepository.existsBySolutionUuidAndZoneId(globalSolution.getId(), "environment-global")).thenReturn(true);
        org.sifunity.broker.environment.domain.Zone envGlobalZone = new org.sifunity.broker.environment.domain.Zone();
        envGlobalZone.setId(java.util.UUID.randomUUID());
        envGlobalZone.setSolutionUuid(globalSolution.getId());
        envGlobalZone.setZoneId("environment-global");
        when(zoneRepository.findBySolutionUuidAndZoneId(globalSolution.getId(), "environment-global")).thenReturn(Optional.of(envGlobalZone));
        when(contextRepository.existsByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT")).thenReturn(true);
        org.sifunity.broker.environment.domain.Context defaultCtx1 = new org.sifunity.broker.environment.domain.Context();
        defaultCtx1.setId(java.util.UUID.randomUUID());
        defaultCtx1.setZoneUuid(envGlobalZone.getId());
        defaultCtx1.setContextId("DEFAULT");
        when(contextRepository.findByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT")).thenReturn(Optional.of(defaultCtx1));

        // BROKER_SERVICES environment already exists
        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            eq("BROKER_SERVICES_" + globalSolution.getId()), eq(globalSolution.getId())))
            .thenReturn(Optional.of(Environment.builder()
                .applicationName("BROKER_SERVICES_" + globalSolution.getId())
                .type(EnvironmentType.BROKERED)
                .solutionUuid(globalSolution.getId())
                .sessionToken("internal-broker-services-" + globalSolution.getId())
                .sharedSecret("not-used")
                .build()));
        when(providerRegistrationRepository.findByContextServiceUuid(any())).thenReturn(Optional.of(
            org.sifunity.broker.environment.domain.ProviderRegistration.builder()
                .id(java.util.UUID.randomUUID())
                .build()));

        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            "ADMIN", globalSolution.getId())).thenReturn(Optional.empty());

        ArgumentCaptor<Environment> envCaptor = ArgumentCaptor.forClass(Environment.class);
        when(environmentRepository.save(any(Environment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ContextServiceService contextServiceService = mock(ContextServiceService.class);
        when(contextServiceService.resolveOrCreate(any(), any(), any())).thenReturn(java.util.UUID.randomUUID());
        BootstrapComponent bootstrapComponent = new BootstrapComponent(
            environmentRepository, domainRepository, solutionRepository, zoneRepository, contextRepository, providerRegistrationRepository, contextServiceService, bootstrapProperties);

        bootstrapComponent.bootstrapAdmin();

        verify(environmentRepository).save(envCaptor.capture());

        Environment savedAdmin = envCaptor.getValue();
        assertThat(savedAdmin.getSessionToken()).isEqualTo(sessionToken);
        assertThat(savedAdmin.getSharedSecret()).isEqualTo(sharedSecret);
        assertThat(savedAdmin.getType()).isEqualTo(EnvironmentType.BROKERED);
        assertThat(savedAdmin.getSolutionUuid()).isEqualTo(globalSolution.getId());
    }

    @Property(tries = 10)
    void bootstrapPreservesCredentialFormatExactly(
            @ForAll("credentialStrings") String sessionToken,
            @ForAll("credentialStrings") String sharedSecret) {

        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        ProviderRegistrationRepository providerRegistrationRepository = mock(ProviderRegistrationRepository.class);
        BootstrapProperties bootstrapProperties = mock(BootstrapProperties.class);
        BootstrapProperties.AdminCredentials adminCredentials = mock(BootstrapProperties.AdminCredentials.class);

        when(bootstrapProperties.isEnabled()).thenReturn(true);
        when(bootstrapProperties.getAdmin()).thenReturn(adminCredentials);
        when(adminCredentials.getSessionToken()).thenReturn(sessionToken);
        when(adminCredentials.getSharedSecret()).thenReturn(sharedSecret);

        // Domain lookup for ensureGlobalSolutionExists
        org.sifunity.broker.environment.domain.Domain globalDomain2 = new org.sifunity.broker.environment.domain.Domain();
        globalDomain2.setId(java.util.UUID.randomUUID());
        globalDomain2.setDomainId(GlobalAdminConstants.GLOBAL_DOMAIN);

        when(domainRepository.existsByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)).thenReturn(true);
        when(domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)).thenReturn(Optional.of(globalDomain2));
        when(solutionRepository.existsByDomainUuidAndSolutionId(
            globalDomain2.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)).thenReturn(true);

        org.sifunity.broker.environment.domain.Solution globalSolution2 = new org.sifunity.broker.environment.domain.Solution();
        globalSolution2.setId(java.util.UUID.randomUUID());
        globalSolution2.setDomainUuid(globalDomain2.getId());
        globalSolution2.setSolutionId(GlobalAdminConstants.GLOBAL_SOLUTION);
        when(solutionRepository.findByDomainUuidAndSolutionId(globalDomain2.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)).thenReturn(Optional.of(globalSolution2));

        when(zoneRepository.existsBySolutionUuidAndZoneId(globalSolution2.getId(), "environment-global")).thenReturn(true);
        org.sifunity.broker.environment.domain.Zone envGlobalZone2 = new org.sifunity.broker.environment.domain.Zone();
        envGlobalZone2.setId(java.util.UUID.randomUUID());
        envGlobalZone2.setSolutionUuid(globalSolution2.getId());
        envGlobalZone2.setZoneId("environment-global");
        when(zoneRepository.findBySolutionUuidAndZoneId(globalSolution2.getId(), "environment-global")).thenReturn(Optional.of(envGlobalZone2));
        when(contextRepository.existsByZoneUuidAndContextId(envGlobalZone2.getId(), "DEFAULT")).thenReturn(true);
        org.sifunity.broker.environment.domain.Context defaultCtx2 = new org.sifunity.broker.environment.domain.Context();
        defaultCtx2.setId(java.util.UUID.randomUUID());
        defaultCtx2.setZoneUuid(envGlobalZone2.getId());
        defaultCtx2.setContextId("DEFAULT");
        when(contextRepository.findByZoneUuidAndContextId(envGlobalZone2.getId(), "DEFAULT")).thenReturn(Optional.of(defaultCtx2));

        // BROKER_SERVICES environment already exists
        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            eq("BROKER_SERVICES_" + globalSolution2.getId()), eq(globalSolution2.getId())))
            .thenReturn(Optional.of(Environment.builder()
                .applicationName("BROKER_SERVICES_" + globalSolution2.getId())
                .type(EnvironmentType.BROKERED)
                .solutionUuid(globalSolution2.getId())
                .sessionToken("internal-broker-services-" + globalSolution2.getId())
                .sharedSecret("not-used")
                .build()));
        when(providerRegistrationRepository.findByContextServiceUuid(any())).thenReturn(Optional.of(
            org.sifunity.broker.environment.domain.ProviderRegistration.builder()
                .id(java.util.UUID.randomUUID())
                .build()));

        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            "ADMIN", globalSolution2.getId())).thenReturn(Optional.empty());

        ArgumentCaptor<Environment> envCaptor = ArgumentCaptor.forClass(Environment.class);
        when(environmentRepository.save(any(Environment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ContextServiceService contextServiceService = mock(ContextServiceService.class);
        when(contextServiceService.resolveOrCreate(any(), any(), any())).thenReturn(java.util.UUID.randomUUID());
        BootstrapComponent bootstrapComponent = new BootstrapComponent(
            environmentRepository, domainRepository, solutionRepository, zoneRepository, contextRepository, providerRegistrationRepository, contextServiceService, bootstrapProperties);

        bootstrapComponent.bootstrapAdmin();

        verify(environmentRepository).save(envCaptor.capture());

        Environment savedAdmin = envCaptor.getValue();
        assertThat(savedAdmin.getSessionToken()).isEqualTo(sessionToken);
        assertThat(savedAdmin.getSharedSecret()).isEqualTo(sharedSecret);
    }

    @Provide
    Arbitrary<String> sessionTokens() {
        return Arbitraries.strings()
            .withCharRange('a', 'z').withCharRange('0', '9').withChars('-')
            .ofMinLength(10).ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> sharedSecrets() {
        return Arbitraries.strings()
            .withCharRange('A', 'Z').withCharRange('a', 'z').withCharRange('0', '9').withChars('+', '/', '=')
            .ofMinLength(16).ofMaxLength(64);
    }

    @Provide
    Arbitrary<String> credentialStrings() {
        return Arbitraries.strings()
            .alpha().numeric().ofMinLength(8).ofMaxLength(32);
    }
}
