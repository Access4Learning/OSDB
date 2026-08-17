// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.ContextServiceDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.service.ContextServiceService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Integration test for provider registration round-trip with real H2 database.
 *
 * <p>Verifies that creating a provider registration via contextServiceUuid
 * and listing it back returns the original fields with resolved zone/context/service names.</p>
 *
 * <p><strong>Validates: Requirements 6.3, 6.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProviderRoundTripH2IntegrationTest {

    private static final List<SupportedRequestType> IMMEDIATE_QUERY = List.of(
            new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))));

    @Autowired
    private ProviderService providerService;

    @Autowired
    private ContextServiceService contextServiceService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    private TestEntityHierarchy hierarchy;
    private UUID environmentId;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("ProviderRoundTripApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null)));
        environmentId = envResponse.environment().id();
    }

    /**
     * Create provider registration via API with contextServiceUuid,
     * verify response preserves original fields.
     * Requirements: 6.3
     */
    @Test
    void registerProvider_responseContainsOriginalFields() {
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                hierarchy.getContextUuid(), "StudentService", "OBJECT");

        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://localhost:8080/students"),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY)));

        ProviderRegistrationDTO created = providerService.registerProvider(
                hierarchy.getDomainId(), hierarchy.getSolutionId(), request);

        assertThat(created.id()).isNotNull();
        assertThat(created.environmentId()).isEqualTo(environmentId);
        assertThat(created.serviceName()).isEqualTo("StudentService");
        assertThat(created.zoneId()).isEqualTo(hierarchy.getZoneId());
        assertThat(created.contextId()).isEqualTo(hierarchy.getContextId());
        assertThat(ProtocolDTO.locationOf(created.endPoint())).isEqualTo("http://localhost:8080/students");
        assertThat(created.serviceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(created.supportedRequestTypes().supportedRequestType()).isEqualTo(IMMEDIATE_QUERY);
        assertThat(created.registeredAt()).isNotNull();
    }

    /**
     * List provider registrations after creation, verify response contains original fields.
     * Requirements: 6.3
     */
    @Test
    void listProviderRegistrations_responseContainsOriginalFields() {
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                hierarchy.getContextUuid(), "TeacherService", "OBJECT");

        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),
                new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://localhost:8081/teachers"),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY))));

        List<ProviderRegistrationDTO> registrations = providerService.listProviderRegistrations(environmentId);

        assertThat(registrations).hasSize(1);
        ProviderRegistrationDTO reg = registrations.get(0);
        assertThat(reg.serviceName()).isEqualTo("TeacherService");
        assertThat(reg.zoneId()).isEqualTo(hierarchy.getZoneId());
        assertThat(reg.contextId()).isEqualTo(hierarchy.getContextId());
    }

    /**
     * Verify context_service record exists after resolveOrCreate + provider registration.
     * Requirements: 6.5
     */
    @Test
    void registerProvider_contextServiceExistsAfterRegistration() {
        String serviceName = "AutoProviderService";

        // Before registration, no context_service should exist for this name
        List<ContextServiceDTO> before = contextServiceService.listByContext(hierarchy.getContextUuid());
        assertThat(before).extracting(ContextServiceDTO::serviceName)
                .doesNotContain(serviceName);

        // Resolve context service (this creates it) and register provider
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                hierarchy.getContextUuid(), serviceName, "OBJECT");

        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),
                new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://localhost:8082/auto"),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY))));

        // After registration, context_service should exist
        List<ContextServiceDTO> after = contextServiceService.listByContext(hierarchy.getContextUuid());
        assertThat(after).extracting(ContextServiceDTO::serviceName)
                .contains(serviceName);
    }

    /**
     * Provider lookup by serviceName + zoneId + contextId works after registration.
     * Requirements: 6.3
     */
    @Test
    void lookupProvider_afterRegistration_findsProvider() {
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                hierarchy.getContextUuid(), "LookupService", "FUNCTIONAL");

        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),
                new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.FUNCTIONAL,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://localhost:8083/lookup"),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY))));

        var found = providerService.lookupProvider(
            hierarchy.getSolutionUuid(), "LookupService", hierarchy.getZoneId(), hierarchy.getContextId()
        );

        assertThat(found).isPresent();
        assertThat(found.get().serviceName()).isEqualTo("LookupService");
        assertThat(found.get().zoneId()).isEqualTo(hierarchy.getZoneId());
        assertThat(found.get().contextId()).isEqualTo(hierarchy.getContextId());
    }
}
