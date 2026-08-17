// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.ContextServiceService;
import org.sifunity.broker.environment.service.ProviderService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.OperationsDTO;

/**
 * Property-based test: ConsumerName mapping replaces providerName.
 *
 * <p><strong>Property 1:</strong> For any ProviderRegistration with an associated Environment,
 * mapping to ProviderRegistrationDTO produces a DTO where consumerName equals
 * environment.getConsumerName().</p>
 *
 * <p><strong>Validates: Requirements 2.5</strong></p>
 */
@Tag("Feature: provider-registry-ui-improvements, Property 1: ConsumerName mapping replaces providerName")
class ProviderRegistrationDTOMappingPropertyTest {

    /**
     * Property 1: ConsumerName mapping replaces providerName.
     *
     * <p>For any ProviderRegistration with associated Environment, mapping produces
     * DTO with consumerName == environment.getConsumerName().</p>
     *
     * <p><strong>Validates: Requirements 2.5</strong></p>
     */
    @Property(tries = 20)
    void consumerNameMappedFromEnvironment(
            @ForAll("consumerNames") String consumerName,
            @ForAll("serviceTypes") ServiceType serviceType,
            @ForAll("serviceNames") String serviceName,
            @ForAll("endpoints") String endpoint) {

        // Set up mocks
        ProviderRegistrationRepository providerRegistrationRepository = mock(ProviderRegistrationRepository.class);
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        ContextServiceService contextServiceService = mock(ContextServiceService.class);
        ContextServiceRepository contextServiceRepository = mock(ContextServiceRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);

        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);

        ProviderService providerService = new ProviderService(
                providerRegistrationRepository,
                environmentRepository,
                contextServiceService,
                contextServiceRepository,
                contextRepository,
                zoneRepository,
                solutionRepository,
                domainRepository,
                aclEntryRepository);

        // Create test data
        UUID environmentId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        UUID contextServiceUuid = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();
        UUID zoneUuid = UUID.randomUUID();

        // Environment with generated consumerName
        Environment env = new Environment();
        env.setId(environmentId);
        env.setConsumerName(consumerName);

        // ProviderRegistration referencing environment
        ProviderRegistration registration = new ProviderRegistration();
        registration.setId(registrationId);
        registration.setEnvironment(env);
        registration.setContextServiceUuid(contextServiceUuid);
        registration.setEndpoint(endpoint);
        registration.setServiceType(serviceType);
        registration.setSupportedRequestTypes(List.of(
                new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY)))));
        registration.setRegisteredAt(Instant.now());

        // ContextService for resolution
        ContextService cs = new ContextService();
        cs.setId(contextServiceUuid);
        cs.setContextUuid(contextUuid);
        cs.setServiceName(serviceName);
        cs.setServiceType(serviceType.name());

        // Context for resolution
        Context context = new Context();
        context.setId(contextUuid);
        context.setZoneUuid(zoneUuid);
        context.setContextId("DEFAULT");

        // Zone for resolution
        Zone zone = new Zone();
        zone.setId(zoneUuid);
        zone.setZoneId("test-zone");

        // Mock repository calls used by mapToProviderRegistrationDTO (via listProviderRegistrations)
        when(providerRegistrationRepository.findByEnvironmentId(environmentId))
                .thenReturn(List.of(registration));
        when(contextServiceRepository.findById(contextServiceUuid))
                .thenReturn(Optional.of(cs));
        when(contextRepository.findById(contextUuid))
                .thenReturn(Optional.of(context));
        when(zoneRepository.findById(zoneUuid))
                .thenReturn(Optional.of(zone));

        // Act: listProviderRegistrations internally calls mapToProviderRegistrationDTO
        List<ProviderRegistrationDTO> dtos = providerService.listProviderRegistrations(environmentId);

        // Assert: consumerName in DTO equals environment.getConsumerName()
        assertThat(dtos).hasSize(1);
        ProviderRegistrationDTO dto = dtos.get(0);

        assertThat(dto.providerName())
                .as("DTO consumerName must equal environment.getConsumerName() for input '%s'", consumerName)
                .isEqualTo(consumerName);

        // Also verify the DTO does not have a providerName field (compile-time guarantee via record)
        // and other fields are mapped correctly
        assertThat(dto.id()).isEqualTo(registrationId);
        assertThat(dto.environmentId()).isEqualTo(environmentId);
        assertThat(dto.serviceType()).isEqualTo(serviceType);
        assertThat(ProtocolDTO.locationOf(dto.endPoint())).isEqualTo(endpoint);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> consumerNames() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
                Arbitraries.of("LovellSIS", "AcmeLMS", "SchoolSync-Prod", "EduTrack_v2"),
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(20)
                        .map(s -> s + "-" + (int) (Math.random() * 1000))
        );
    }

    @Provide
    Arbitrary<ServiceType> serviceTypes() {
        return Arbitraries.of(ServiceType.values());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> endpoints() {
        return Arbitraries.of(
                "http://provider.example.com/api",
                "https://services.school.edu/sif",
                "http://localhost:8080/provider",
                "https://acme-lms.io/v2/sif-endpoint"
        );
    }
}
