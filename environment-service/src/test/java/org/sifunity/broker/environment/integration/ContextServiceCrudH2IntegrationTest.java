// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.ContextCreateRequest;
import org.sifunity.broker.environment.dto.ContextServiceCreateRequest;
import org.sifunity.broker.environment.dto.ContextServiceDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.exception.ContextServiceNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateContextServiceException;
import org.sifunity.broker.environment.service.AclService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Integration test for context_service CRUD operations using H2 in-memory database.
 *
 * <p>Tests create, list by context, list by zone, delete, and cascade delete behavior.</p>
 *
 * <p><strong>Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContextServiceCrudH2IntegrationTest {

    @Autowired
    private ContextServiceService contextServiceService;

    @Autowired
    private AclService aclService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    private TestEntityHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
    }

    /**
     * Test creating a context_service and verifying returned DTO.
     * Requirements: 5.1
     */
    @Test
    void createContextService_returnsCorrectDTO() {
        ContextServiceDTO created = contextServiceService.createContextService(
                hierarchy.getContextUuid(), "StudentService", "OBJECT");

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.contextUuid()).isEqualTo(hierarchy.getContextUuid());
        assertThat(created.serviceName()).isEqualTo("StudentService");
        assertThat(created.serviceType()).isEqualTo("OBJECT");
        assertThat(created.createdAt()).isNotNull();
    }

    /**
     * Test duplicate context_service creation is rejected with 409.
     * Requirements: 5.2
     */
    @Test
    void createDuplicateContextService_throwsConflict() {
        contextServiceService.createContextService(
                hierarchy.getContextUuid(), "DuplicateService", "UTILITY");

        assertThatThrownBy(() -> contextServiceService.createContextService(
                hierarchy.getContextUuid(), "DuplicateService", "UTILITY"))
                .isInstanceOf(DuplicateContextServiceException.class);
    }

    /**
     * Test listing context_services by context UUID.
     * Requirements: 5.3
     */
    @Test
    void listByContext_returnsOnlyMatchingRecords() {
        contextServiceService.createContextService(hierarchy.getContextUuid(), "ServiceA", "OBJECT");
        contextServiceService.createContextService(hierarchy.getContextUuid(), "ServiceB", "UTILITY");

        List<ContextServiceDTO> results = contextServiceService.listByContext(hierarchy.getContextUuid());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ContextServiceDTO::serviceName)
                .containsExactlyInAnyOrder("ServiceA", "ServiceB");
    }

    /**
     * Test listing context_services by zone returns services across all contexts in zone.
     * Requirements: 5.4
     */
    @Test
    void listByZone_returnsServicesAcrossAllContextsInZone() {
        // Create a second context in the same zone
        ContextDTO secondContext = zoneService.createContext(
                hierarchy.getDomainId(), hierarchy.getSolutionId(), hierarchy.getZoneId(),
                new ContextCreateRequest("second-ctx", "Second context"));

        // Create services in both contexts
        contextServiceService.createContextService(hierarchy.getContextUuid(), "ServiceInDefault", "OBJECT");
        contextServiceService.createContextService(secondContext.id(), "ServiceInSecond", "FUNCTIONAL");

        List<ContextServiceDTO> results = contextServiceService.listByZone(hierarchy.getZoneUuid());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ContextServiceDTO::serviceName)
                .containsExactlyInAnyOrder("ServiceInDefault", "ServiceInSecond");
    }

    /**
     * Test deleting a context_service.
     * Requirements: 5.5
     */
    @Test
    void deleteContextService_removesRecord() {
        ContextServiceDTO created = contextServiceService.createContextService(hierarchy.getContextUuid(), "ToDelete", "SERVICE");

        contextServiceService.deleteContextService(created.id());

        List<ContextServiceDTO> remaining = contextServiceService.listByContext(hierarchy.getContextUuid());
        assertThat(remaining).isEmpty();
    }

    /**
     * Test deleting non-existent context_service throws 404.
     * Requirements: 5.5
     */
    @Test
    void deleteNonExistentContextService_throws404() {
        UUID nonExistentId = UUID.randomUUID();

        assertThatThrownBy(() -> contextServiceService.deleteContextService(nonExistentId))
                .isInstanceOf(ContextServiceNotFoundException.class);
    }

    /**
     * Test cascade delete propagates to dependent ACL entries and provider registrations.
     * Requirements: 5.6
     */
    @Test
    void deleteContextService_cascadesToDependents() {
        // Create context_service
        ContextServiceDTO cs = contextServiceService.createContextService(hierarchy.getContextUuid(), "CascadeService", "OBJECT");

        // Register environment
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("CascadeTestApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null)));
        UUID environmentId = envResponse.environment().id();

        // Create ACL entry referencing this context_service
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "CascadeService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.QUERY, AccessLevel.APPROVED));

        // Create provider registration referencing this context_service
        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),
                new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                cs.id(),
                ProtocolDTO.of("http://localhost:9090/cascade"),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))))))));

        // Delete context_service â€” should cascade
        contextServiceService.deleteContextService(cs.id());

        // Verify ACL entries for this environment are gone
        var aclEntries = aclService.listAclEntries(environmentId);
        assertThat(aclEntries).isEmpty();

        // Verify provider registrations are gone
        var providers = providerService.listProviderRegistrations(environmentId);
        assertThat(providers).isEmpty();
    }

    /**
     * Test creating context_service with non-existent context returns 404.
     * Requirements: 5.7
     */
    @Test
    void createContextService_withNonExistentContext_throws404() {
        UUID fakeContextUuid = UUID.randomUUID();

        assertThatThrownBy(() -> contextServiceService.createContextService(fakeContextUuid, "Orphan", "OBJECT"))
                .isInstanceOf(org.sifunity.broker.environment.exception.ContextNotFoundException.class);
    }
}

