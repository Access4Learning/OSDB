// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.config.BootstrapProperties;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for BootstrapComponent idempotency (mock-based).
 */
class BootstrapIdempotencyProperties {

    @Property(tries = 10)
    void bootstrapMaintainsExactlyOneAdmin(@ForAll @IntRange(min = 1, max = 10) int executionCount) {
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
        when(adminCredentials.getSessionToken()).thenReturn("test-admin-token");
        when(adminCredentials.getSharedSecret()).thenReturn("test-admin-secret");

        List<Environment> savedEnvironments = new ArrayList<>();
        List<Domain> savedDomains = new ArrayList<>();
        List<Solution> savedSolutions = new ArrayList<>();

        // Track UUIDs for domain/solution
        java.util.UUID[] domainUuid = { java.util.UUID.randomUUID() };
        java.util.UUID[] solutionUuid = { java.util.UUID.randomUUID() };
        java.util.UUID[] zoneUuid = { java.util.UUID.randomUUID() };

        when(domainRepository.existsByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN))
            .thenAnswer(inv -> !savedDomains.isEmpty());
        when(domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN))
            .thenAnswer(inv -> {
                if (savedDomains.isEmpty()) return Optional.empty();
                return Optional.of(savedDomains.get(0));
            });
        when(solutionRepository.existsByDomainUuidAndSolutionId(
            any(java.util.UUID.class), eq(GlobalAdminConstants.GLOBAL_SOLUTION)))
            .thenAnswer(inv -> !savedSolutions.isEmpty());
        when(solutionRepository.findByDomainUuidAndSolutionId(
            any(java.util.UUID.class), eq(GlobalAdminConstants.GLOBAL_SOLUTION)))
            .thenAnswer(inv -> {
                if (savedSolutions.isEmpty()) return Optional.empty();
                return Optional.of(savedSolutions.get(0));
            });
        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            eq("ADMIN"), any(java.util.UUID.class)))
            .thenAnswer(inv -> savedEnvironments.isEmpty() ? Optional.empty() : Optional.of(savedEnvironments.get(0)));

        // Zone/context stubs for ensureEnvironmentGlobalZone
        org.sifunity.broker.environment.domain.Zone envGlobalZone = new org.sifunity.broker.environment.domain.Zone();
        envGlobalZone.setId(zoneUuid[0]);
        envGlobalZone.setZoneId("environment-global");
        when(zoneRepository.existsBySolutionUuidAndZoneId(any(java.util.UUID.class), eq("environment-global")))
            .thenReturn(true);
        when(zoneRepository.findBySolutionUuidAndZoneId(any(java.util.UUID.class), eq("environment-global")))
            .thenReturn(Optional.of(envGlobalZone));
        when(contextRepository.existsByZoneUuidAndContextId(zoneUuid[0], "DEFAULT")).thenReturn(true);

        // Stub context lookup for alerts provider registration
        org.sifunity.broker.environment.domain.Context defaultCtx = new org.sifunity.broker.environment.domain.Context();
        defaultCtx.setId(java.util.UUID.randomUUID());
        defaultCtx.setContextId("DEFAULT");
        defaultCtx.setZoneUuid(zoneUuid[0]);
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid[0], "DEFAULT"))
            .thenReturn(Optional.of(defaultCtx));

        // Stub contextServiceService.resolveOrCreate for alerts
        org.sifunity.broker.environment.service.ContextServiceService contextServiceService = mock(org.sifunity.broker.environment.service.ContextServiceService.class);
        java.util.UUID alertsContextServiceUuid = java.util.UUID.randomUUID();
        when(contextServiceService.resolveOrCreate(defaultCtx.getId(), "alerts", "UTILITY"))
            .thenReturn(alertsContextServiceUuid);

        // BROKER_SERVICES environment stubs
        List<Environment> savedBrokerEnvs = new ArrayList<>();
        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            argThat(name -> name != null && name.startsWith("BROKER_SERVICES_")), any(java.util.UUID.class)))
            .thenAnswer(inv -> savedBrokerEnvs.isEmpty() ? Optional.empty() : Optional.of(savedBrokerEnvs.get(0)));
        when(providerRegistrationRepository.findByContextServiceUuid(any()))
            .thenReturn(java.util.Optional.empty());
        when(providerRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(domainRepository.save(any(Domain.class))).thenAnswer(inv -> {
            Domain d = inv.getArgument(0);
            d.setId(domainUuid[0]);
            savedDomains.add(d);
            return d;
        });
        when(solutionRepository.save(any(Solution.class))).thenAnswer(inv -> {
            Solution s = inv.getArgument(0);
            s.setId(solutionUuid[0]);
            savedSolutions.add(s);
            return s;
        });
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> {
            Environment e = inv.getArgument(0);
            if (e.getApplicationName() != null && e.getApplicationName().startsWith("BROKER_SERVICES_")) {
                if (savedBrokerEnvs.isEmpty()) {
                    e.setId(java.util.UUID.randomUUID());
                    savedBrokerEnvs.add(e);
                }
            } else {
                savedEnvironments.add(e);
            }
            return e;
        });

        BootstrapComponent bootstrapComponent = new BootstrapComponent(
            environmentRepository, domainRepository, solutionRepository, zoneRepository, contextRepository, providerRegistrationRepository, contextServiceService, bootstrapProperties);

        for (int i = 0; i < executionCount; i++) {
            bootstrapComponent.bootstrapAdmin();
        }

        assertThat(savedEnvironments).hasSize(1);
        assertThat(savedDomains).hasSize(1);
        assertThat(savedSolutions).hasSize(1);

        Environment admin = savedEnvironments.get(0);
        assertThat(admin.getType()).isEqualTo(EnvironmentType.BROKERED);
        assertThat(admin.getSolutionUuid()).isEqualTo(solutionUuid[0]);
        // ADMIN + BROKER_SERVICES = 2 saves on first run, 0 on subsequent runs
        verify(environmentRepository, atMost(2)).save(any(Environment.class));
    }

    @Property(tries = 10)
    void bootstrapDoesNotCreateDuplicateWhenAdminExists(
            @ForAll @IntRange(min = 1, max = 10) int executionCount) {

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
        when(adminCredentials.getSessionToken()).thenReturn("test-admin-token");
        when(adminCredentials.getSharedSecret()).thenReturn("test-admin-secret");

        when(domainRepository.existsByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)).thenReturn(true);
        org.sifunity.broker.environment.domain.Domain globalDomain = new org.sifunity.broker.environment.domain.Domain();
        globalDomain.setId(java.util.UUID.randomUUID());
        globalDomain.setDomainId(GlobalAdminConstants.GLOBAL_DOMAIN);
        when(domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)).thenReturn(Optional.of(globalDomain));
        when(solutionRepository.existsByDomainUuidAndSolutionId(
            globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)).thenReturn(true);

        org.sifunity.broker.environment.domain.Solution globalSolution = new org.sifunity.broker.environment.domain.Solution();
        globalSolution.setId(java.util.UUID.randomUUID());
        globalSolution.setDomainUuid(globalDomain.getId());
        globalSolution.setSolutionId(GlobalAdminConstants.GLOBAL_SOLUTION);
        when(solutionRepository.findByDomainUuidAndSolutionId(globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)).thenReturn(Optional.of(globalSolution));

        // Zone/context stubs for ensureEnvironmentGlobalZone
        org.sifunity.broker.environment.domain.Zone envGlobalZone = new org.sifunity.broker.environment.domain.Zone();
        envGlobalZone.setId(java.util.UUID.randomUUID());
        envGlobalZone.setZoneId("environment-global");
        when(zoneRepository.existsBySolutionUuidAndZoneId(globalSolution.getId(), "environment-global")).thenReturn(true);
        when(zoneRepository.findBySolutionUuidAndZoneId(globalSolution.getId(), "environment-global"))
            .thenReturn(Optional.of(envGlobalZone));
        when(contextRepository.existsByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT")).thenReturn(true);

        // Stub context lookup for alerts provider registration
        org.sifunity.broker.environment.domain.Context defaultCtx = new org.sifunity.broker.environment.domain.Context();
        defaultCtx.setId(java.util.UUID.randomUUID());
        defaultCtx.setContextId("DEFAULT");
        defaultCtx.setZoneUuid(envGlobalZone.getId());
        when(contextRepository.findByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT"))
            .thenReturn(Optional.of(defaultCtx));

        // Stub contextServiceService.resolveOrCreate for alerts
        org.sifunity.broker.environment.service.ContextServiceService contextServiceService = mock(org.sifunity.broker.environment.service.ContextServiceService.class);
        java.util.UUID alertsContextServiceUuid = java.util.UUID.randomUUID();
        when(contextServiceService.resolveOrCreate(defaultCtx.getId(), "alerts", "UTILITY"))
            .thenReturn(alertsContextServiceUuid);

        // BROKER_SERVICES environment already exists
        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            argThat(name -> name != null && name.startsWith("BROKER_SERVICES_")), any(java.util.UUID.class)))
            .thenReturn(Optional.of(Environment.builder()
                .applicationName("BROKER_SERVICES_" + globalSolution.getId())
                .type(EnvironmentType.BROKERED)
                .solutionUuid(globalSolution.getId())
                .sessionToken("internal-broker-services-" + globalSolution.getId())
                .sharedSecret("not-used")
                .build()));
        when(providerRegistrationRepository.findByContextServiceUuid(any())).thenReturn(java.util.Optional.of(org.sifunity.broker.environment.domain.ProviderRegistration.builder().id(java.util.UUID.randomUUID()).build()));

        Environment existingAdmin = Environment.builder()
            .applicationName("ADMIN")
            .type(EnvironmentType.BROKERED)
            .solutionUuid(globalSolution.getId())
            .sessionToken("existing-token")
            .sharedSecret("existing-secret")
            .defaultZoneId("default")
            .build();
        when(environmentRepository.findByApplicationNameAndSolutionUuid(
            "ADMIN", globalSolution.getId())).thenReturn(Optional.of(existingAdmin));

        BootstrapComponent bootstrapComponent = new BootstrapComponent(
            environmentRepository, domainRepository, solutionRepository, zoneRepository, contextRepository, providerRegistrationRepository, contextServiceService, bootstrapProperties);

        for (int i = 0; i < executionCount; i++) {
            bootstrapComponent.bootstrapAdmin();
        }

        verify(environmentRepository, never()).save(any(Environment.class));
    }
}
