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
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.service.AclService;
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
 * End-to-end integration test for environment lifecycle using H2 in-memory database.
 * 
 * <p>This test validates the complete lifecycle of an environment:
 * <ol>
 *   <li>Register environment</li>
 *   <li>Query environment</li>
 *   <li>Create associated ACL entries and provider registrations</li>
 *   <li>Delete environment</li>
 *   <li>Verify environment is deleted</li>
 *   <li>Verify cascade deletion of ACL entries and provider registrations</li>
 * </ol>
 * 
 * <p><strong>Validates: Requirements 1.1, 2.1, 3.1, 3.2, 3.3</strong></p>
 * 
 * <p>This test uses H2 in-memory database configured to emulate PostgreSQL behavior,
 * including cascade deletion and foreign key constraints. For production-like testing
 * with actual PostgreSQL, see {@link EnvironmentLifecycleIntegrationTest}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnvironmentLifecycleH2IntegrationTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private AclService aclService;
    
    @Autowired
    private ProviderService providerService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private org.sifunity.broker.environment.service.ContextServiceService contextServiceService;

    private TestEntityHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
    }

    /**
     * Test the complete environment lifecycle with cascade deletion.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>Requirement 1.1: Environment registration</li>
     *   <li>Requirement 2.1: Environment retrieval</li>
     *   <li>Requirement 3.1: Environment deletion</li>
     *   <li>Requirement 3.2: Cascade deletion of ACL entries</li>
     *   <li>Requirement 3.3: Cascade deletion of provider registrations</li>
     * </ul>
     */
    @Test
    void testEnvironmentLifecycleWithCascadeDeletion() {
        // ========== Step 1: Register Environment ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("H2IntegrationTestApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse =
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);

        // Verify registration response
        assertThat(registrationResponse).isNotNull();
        assertThat(registrationResponse.environment().id()).isNotNull();
        assertThat(registrationResponse.environment().consumerName()).isEqualTo(registrationRequest.environment().consumerName());
        assertThat(registrationResponse.environment().type()).isEqualTo(registrationRequest.environment().type());
        assertThat(DefaultZoneDTO.idOf(registrationResponse.environment().defaultZone())).isEqualTo(registrationRequest.environment().defaultZoneId());
        assertThat(registrationResponse.environment().sessionToken()).isNotNull().isNotBlank();
        assertThat(registrationResponse.sharedSecret()).isNotNull().isNotBlank();
        assertThat(registrationResponse.environment().id()).isNotNull();
        
        UUID environmentId = registrationResponse.environment().id();
        
        // ========== Step 2: Query Environment ==========
        
        SifEnvironmentResponse queriedEnvironment = environmentService.getEnvironment(environmentId);
        
        // Verify queried environment matches registration
        assertThat(queriedEnvironment).isNotNull();
        assertThat(queriedEnvironment.id()).isEqualTo(environmentId);
        assertThat(queriedEnvironment.consumerName()).isEqualTo(registrationRequest.environment().consumerName());
        assertThat(queriedEnvironment.type()).isEqualTo(registrationRequest.environment().type());
        assertThat(DefaultZoneDTO.idOf(queriedEnvironment.defaultZone())).isEqualTo(registrationRequest.environment().defaultZoneId());
        assertThat(queriedEnvironment.sessionToken()).isEqualTo(registrationResponse.environment().sessionToken());
        
        // ========== Step 3: Create ACL Entries ==========
        
        // Create multiple ACL entries for the environment
        AclEntryCreateRequest aclRequest1 = new AclEntryCreateRequest(
            environmentId,
            "StudentService",
            hierarchy.getZoneId(), hierarchy.getContextId(),
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );
        
        AclEntryDTO aclEntry1 = aclService.createAclEntry(aclRequest1);
        assertThat(aclEntry1).isNotNull();
        assertThat(aclEntry1.id()).isNotNull();
        assertThat(aclEntry1.environmentId()).isEqualTo(environmentId);
        assertThat(aclEntry1.serviceName()).isEqualTo("StudentService");
        
        AclEntryCreateRequest aclRequest2 = new AclEntryCreateRequest(
            environmentId,
            "TeacherService",
            hierarchy.getZoneId(), hierarchy.getContextId(),
            AccessType.QUERY,
            AccessLevel.APPROVED
        );
        
        AclEntryDTO aclEntry2 = aclService.createAclEntry(aclRequest2);
        assertThat(aclEntry2).isNotNull();
        assertThat(aclEntry2.id()).isNotNull();
        assertThat(aclEntry2.environmentId()).isEqualTo(environmentId);
        assertThat(aclEntry2.serviceName()).isEqualTo("TeacherService");
        
        AclEntryCreateRequest aclRequest3 = new AclEntryCreateRequest(
            environmentId,
            "GradeService",
            hierarchy.getZoneId(), hierarchy.getContextId(),
            AccessType.SUBSCRIBE,
            AccessLevel.REQUESTED
        );
        
        AclEntryDTO aclEntry3 = aclService.createAclEntry(aclRequest3);
        assertThat(aclEntry3).isNotNull();
        assertThat(aclEntry3.id()).isNotNull();
        assertThat(aclEntry3.environmentId()).isEqualTo(environmentId);
        assertThat(aclEntry3.serviceName()).isEqualTo("GradeService");
        
        // Verify ACL entries exist
        List<AclEntryDTO> aclEntries = aclService.listAclEntries(environmentId);
        assertThat(aclEntries)
            .hasSize(3)
            .extracting(AclEntryDTO::id)
            .containsExactlyInAnyOrder(aclEntry1.id(), aclEntry2.id(), aclEntry3.id());
        
        // ========== Step 4: Create Provider Registrations ==========
        
        // Create multiple provider registrations for the environment
        UUID csUuid1 = contextServiceService.resolveOrCreate(hierarchy.getContextUuid(), "StudentService", "OBJECT");
        ProviderRegistrationRequest providerRequest1 = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                csUuid1,
                ProtocolDTO.of("http://localhost:8080/api/students"),
                SupportedRequestsDTO.of(List.of( new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY, Operation.UPDATE))), new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE))) ))));
        
        ProviderRegistrationDTO providerRegistration1 = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(), providerRequest1);
        assertThat(providerRegistration1).isNotNull();
        assertThat(providerRegistration1.id()).isNotNull();
        assertThat(providerRegistration1.environmentId()).isEqualTo(environmentId);
        assertThat(providerRegistration1.serviceName()).isEqualTo("StudentService");
        assertThat(ProtocolDTO.locationOf(providerRegistration1.endPoint())).isEqualTo("http://localhost:8080/api/students");
        
        UUID csUuid2 = contextServiceService.resolveOrCreate(hierarchy.getContextUuid(), "AttendanceService", "OBJECT");
        ProviderRegistrationRequest providerRequest2 = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                csUuid2,
                ProtocolDTO.of("http://localhost:8081/api/attendance"),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY)))))));
        
        ProviderRegistrationDTO providerRegistration2 = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(), providerRequest2);
        assertThat(providerRegistration2).isNotNull();
        assertThat(providerRegistration2.id()).isNotNull();
        assertThat(providerRegistration2.environmentId()).isEqualTo(environmentId);
        assertThat(providerRegistration2.serviceName()).isEqualTo("AttendanceService");
        
        // Verify provider registrations exist
        List<ProviderRegistrationDTO> providerRegistrations = 
            providerService.listProviderRegistrations(environmentId);
        assertThat(providerRegistrations)
            .hasSize(2)
            .extracting(ProviderRegistrationDTO::id)
            .containsExactlyInAnyOrder(providerRegistration1.id(), providerRegistration2.id());
        
        // ========== Step 5: Verify Environment and Associations Exist ==========
        
        // Verify environment still exists
        SifEnvironmentResponse environmentBeforeDeletion = environmentService.getEnvironment(environmentId);
        assertThat(environmentBeforeDeletion).isNotNull();
        
        // Verify ACL entries still exist
        List<AclEntryDTO> aclEntriesBeforeDeletion = aclService.listAclEntries(environmentId);
        assertThat(aclEntriesBeforeDeletion).hasSize(3);
        
        // Verify provider registrations still exist
        List<ProviderRegistrationDTO> providerRegistrationsBeforeDeletion = 
            providerService.listProviderRegistrations(environmentId);
        assertThat(providerRegistrationsBeforeDeletion).hasSize(2);
        
        // ========== Step 6: Delete Environment ==========
        
        environmentService.deleteEnvironment(environmentId);
        
        // ========== Step 7: Verify Environment is Deleted ==========
        
        // Verify environment is deleted (Requirement 3.1)
        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId))
            .isInstanceOf(EnvironmentNotFoundException.class)
            .hasMessageContaining(environmentId.toString());
        
        // ========== Step 8: Verify Cascade Deletion of ACL Entries ==========
        
        // Verify all ACL entries are cascade deleted (Requirement 3.2)
        List<AclEntryDTO> aclEntriesAfterDeletion = aclService.listAclEntries(environmentId);
        assertThat(aclEntriesAfterDeletion)
            .as("All ACL entries should be cascade deleted when environment is deleted")
            .isEmpty();
        
        // ========== Step 9: Verify Cascade Deletion of Provider Registrations ==========
        
        // Verify all provider registrations are cascade deleted (Requirement 3.3)
        List<ProviderRegistrationDTO> providerRegistrationsAfterDeletion = 
            providerService.listProviderRegistrations(environmentId);
        assertThat(providerRegistrationsAfterDeletion)
            .as("All provider registrations should be cascade deleted when environment is deleted")
            .isEmpty();
    }
    
    /**
     * Test environment lifecycle with consumer environment (no provider registrations).
     * 
     * <p>This test validates that cascade deletion works correctly for consumer
     * environments that only have ACL entries and no provider registrations.</p>
     */
    @Test
    void testConsumerEnvironmentLifecycleWithCascadeDeletion() {
        // Register a CONSUMER environment
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("H2ConsumerApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse =
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);

        UUID environmentId = registrationResponse.environment().id();

        // Create ACL entries for the consumer environment
        AclEntryCreateRequest aclRequest1 = new AclEntryCreateRequest(
            environmentId,
            "StudentService",
            hierarchy.getZoneId(), hierarchy.getContextId(),
            AccessType.QUERY,
            AccessLevel.APPROVED
        );
        
        AclEntryDTO aclEntry1 = aclService.createAclEntry(aclRequest1);
        assertThat(aclEntry1).isNotNull();
        
        AclEntryCreateRequest aclRequest2 = new AclEntryCreateRequest(
            environmentId,
            "TeacherService",
            hierarchy.getZoneId(), hierarchy.getContextId(),
            AccessType.SUBSCRIBE,
            AccessLevel.APPROVED
        );
        
        AclEntryDTO aclEntry2 = aclService.createAclEntry(aclRequest2);
        assertThat(aclEntry2).isNotNull();
        
        // Verify ACL entries exist
        List<AclEntryDTO> aclEntries = aclService.listAclEntries(environmentId);
        assertThat(aclEntries).hasSize(2);
        
        // Verify no provider registrations (consumer environment)
        List<ProviderRegistrationDTO> providerRegistrations = 
            providerService.listProviderRegistrations(environmentId);
        assertThat(providerRegistrations).isEmpty();
        
        // Delete the environment
        environmentService.deleteEnvironment(environmentId);
        
        // Verify environment is deleted
        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId))
            .isInstanceOf(EnvironmentNotFoundException.class);
        
        // Verify ACL entries are cascade deleted
        List<AclEntryDTO> aclEntriesAfterDeletion = aclService.listAclEntries(environmentId);
        assertThat(aclEntriesAfterDeletion).isEmpty();
    }
    
    /**
     * Test environment lifecycle with no associations.
     * 
     * <p>This test validates that deleting an environment with no ACL entries
     * or provider registrations works correctly.</p>
     */
    @Test
    void testEnvironmentLifecycleWithNoAssociations() {
        // Register an environment
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("H2EmptyApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse =
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);

        UUID environmentId = registrationResponse.environment().id();

        // Query the environment
        SifEnvironmentResponse queriedEnvironment = environmentService.getEnvironment(environmentId);
        assertThat(queriedEnvironment).isNotNull();
        assertThat(queriedEnvironment.id()).isEqualTo(environmentId);
        
        // Verify no ACL entries
        List<AclEntryDTO> aclEntries = aclService.listAclEntries(environmentId);
        assertThat(aclEntries).isEmpty();
        
        // Verify no provider registrations
        List<ProviderRegistrationDTO> providerRegistrations = 
            providerService.listProviderRegistrations(environmentId);
        assertThat(providerRegistrations).isEmpty();
        
        // Delete the environment
        environmentService.deleteEnvironment(environmentId);
        
        // Verify environment is deleted
        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId))
            .isInstanceOf(EnvironmentNotFoundException.class);
    }
    
    /**
     * Test multiple environments with overlapping ACL entries.
     * 
     * <p>This test validates that deleting one environment does not affect
     * ACL entries or provider registrations of other environments.</p>
     */
    @Test
    void testMultipleEnvironmentsIndependentDeletion() {
        // Create first environment
        EnvironmentRegistrationRequest request1 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("H2App1-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse response1 = environmentService.registerEnvironment(hierarchy.getDomainId(), request1);
        UUID environmentId1 = response1.environment().id();
        
        // Create second environment
        EnvironmentRegistrationRequest request2 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("H2App2-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse response2 = environmentService.registerEnvironment(hierarchy.getDomainId(), request2);
        UUID environmentId2 = response2.environment().id();
        
        // Create ACL entries for both environments (same service name)
        AclEntryCreateRequest aclRequest1 = new AclEntryCreateRequest(
            environmentId1,
            "SharedService-" + System.currentTimeMillis(),
            hierarchy.getZoneId(), hierarchy.getContextId(),
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );
        aclService.createAclEntry(aclRequest1);
        
        AclEntryCreateRequest aclRequest2 = new AclEntryCreateRequest(
            environmentId2,
            "SharedService-" + System.currentTimeMillis(),
            hierarchy.getZoneId(), hierarchy.getContextId(),
            AccessType.QUERY,
            AccessLevel.APPROVED
        );
        aclService.createAclEntry(aclRequest2);
        
        // Create provider registrations for both environments (different services)
        UUID csUuidSvc1 = contextServiceService.resolveOrCreate(hierarchy.getContextUuid(), "Service1-" + System.currentTimeMillis(), "OBJECT");
        ProviderRegistrationRequest providerRequest1 = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId1,
                null,
                ServiceType.OBJECT,
                null,
                null,
                csUuidSvc1,
                ProtocolDTO.of("http://localhost:8080/api/service1"),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY)))))));
        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(), providerRequest1);

        UUID csUuidSvc2 = contextServiceService.resolveOrCreate(hierarchy.getContextUuid(), "Service2-" + System.currentTimeMillis(), "OBJECT");
        ProviderRegistrationRequest providerRequest2 = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId2,
                null,
                ServiceType.OBJECT,
                null,
                null,
                csUuidSvc2,
                ProtocolDTO.of("http://localhost:8081/api/service2"),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE)))))));
        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(), providerRequest2);

        // Verify both environments have their associations
        assertThat(aclService.listAclEntries(environmentId1)).hasSize(1);
        assertThat(aclService.listAclEntries(environmentId2)).hasSize(1);
        assertThat(providerService.listProviderRegistrations(environmentId1)).hasSize(1);
        assertThat(providerService.listProviderRegistrations(environmentId2)).hasSize(1);
        
        // Delete first environment
        environmentService.deleteEnvironment(environmentId1);
        
        // Verify first environment is deleted
        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId1))
            .isInstanceOf(EnvironmentNotFoundException.class);
        
        // Verify first environment's associations are deleted
        assertThat(aclService.listAclEntries(environmentId1)).isEmpty();
        assertThat(providerService.listProviderRegistrations(environmentId1)).isEmpty();
        
        // Verify second environment still exists
        SifEnvironmentResponse environment2 = environmentService.getEnvironment(environmentId2);
        assertThat(environment2).isNotNull();
        
        // Verify second environment's associations still exist
        assertThat(aclService.listAclEntries(environmentId2)).hasSize(1);
        assertThat(providerService.listProviderRegistrations(environmentId2)).hasSize(1);
        
        // Clean up: delete second environment
        environmentService.deleteEnvironment(environmentId2);
        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId2))
            .isInstanceOf(EnvironmentNotFoundException.class);
    }
}



