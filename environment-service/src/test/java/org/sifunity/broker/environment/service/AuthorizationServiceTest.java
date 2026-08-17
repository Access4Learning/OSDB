// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.PermissionCheckRequest;
import org.sifunity.broker.environment.dto.PermissionCheckResponse;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuthorizationService.
 * 
 * <p>Tests the business logic for ACL permission checking and role-based authorization
 * including various scenarios for granted and denied permissions.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {
    
    @Mock
    private AclEntryRepository aclEntryRepository;
    
    @Mock
    private EnvironmentRepository environmentRepository;
    
    @Mock
    private SolutionService solutionService;
    
    @Mock
    private DomainService domainService;
    
    @Mock
    private org.sifunity.broker.environment.repository.ContextRepository contextRepository;
    
    @Mock
    private org.sifunity.broker.environment.repository.ContextServiceRepository contextServiceRepository;
    
    @Mock
    private org.sifunity.broker.environment.repository.ZoneRepository zoneRepository;
    
    private AuthorizationService authorizationService;
    
    private UUID environmentId;
    private String serviceName;
    private String zoneId;
    private String contextId;
    private AccessType accessType;
    
    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationService(aclEntryRepository, environmentRepository, solutionService, domainService, contextRepository, contextServiceRepository, zoneRepository);
        
        environmentId = UUID.randomUUID();
        serviceName = "TestService";
        zoneId = "test-zone";
        contextId = "test-context";
        accessType = AccessType.QUERY;
    }
    
    @Test
    void checkPermission_WithApprovedAclEntry_ReturnsGranted() {
        // Given
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, accessType);
        
        Environment environment = createEnvironment(EnvironmentType.BROKERED);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        
        AclEntry approvedEntry = new AclEntry();
        approvedEntry.setId(UUID.randomUUID());
        approvedEntry.setContextServiceUuid(mockContextServiceUuid);
        approvedEntry.setAccessType(accessType);
        approvedEntry.setAccessLevel(AccessLevel.APPROVED);
        
        setupZoneContextMocks();
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, mockContextServiceUuid, accessType))
                .thenReturn(Optional.of(approvedEntry));
        
        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);
        
        // Then
        assertThat(response.granted()).isTrue();
    }
    
    @Test
    void checkPermission_WithAdminEnvironment_ReturnsGranted() {
        // Given
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, accessType);
        
        Environment adminEnvironment = createAdminEnvironment();
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(adminEnvironment));
        
        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);
        
        // Then
        assertThat(response.granted()).isTrue();
    }
    
    @Test
    void checkPermission_WithRejectedAclEntry_ReturnsDenied() {
        // Given
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, accessType);
        
        Environment environment = createEnvironment(EnvironmentType.BROKERED);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        
        AclEntry rejectedEntry = new AclEntry();
        rejectedEntry.setId(UUID.randomUUID());
        rejectedEntry.setContextServiceUuid(mockContextServiceUuid);
        rejectedEntry.setAccessType(accessType);
        rejectedEntry.setAccessLevel(AccessLevel.REJECTED);
        
        setupZoneContextMocks();
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, mockContextServiceUuid, accessType))
                .thenReturn(Optional.of(rejectedEntry));
        
        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);
        
        // Then
        assertThat(response.granted()).isFalse();
    }
    
    @Test
    void checkPermission_WithPendingAclEntry_ReturnsDenied() {
        // Given
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, accessType);
        
        Environment environment = createEnvironment(EnvironmentType.BROKERED);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        
        AclEntry pendingEntry = new AclEntry();
        pendingEntry.setId(UUID.randomUUID());
        pendingEntry.setContextServiceUuid(mockContextServiceUuid);
        pendingEntry.setAccessType(accessType);
        pendingEntry.setAccessLevel(AccessLevel.REQUESTED);
        
        setupZoneContextMocks();
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, mockContextServiceUuid, accessType))
                .thenReturn(Optional.of(pendingEntry));
        
        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);
        
        // Then
        assertThat(response.granted()).isFalse();
    }
    
    @Test
    void checkPermission_WithNoAclEntry_ReturnsDenied() {
        // Given
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, accessType);
        
        Environment environment = createEnvironment(EnvironmentType.BROKERED);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        
        setupZoneContextMocks();
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, mockContextServiceUuid, accessType))
                .thenReturn(Optional.empty());
        
        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);
        
        // Then
        assertThat(response.granted()).isFalse();
    }
    
    @Test
    void checkPermission_WithNullContextId_HandlesCorrectly() {
        // Given
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, null, accessType);
        
        Environment environment = createEnvironment(EnvironmentType.BROKERED);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        
        // When contextId is null, contextUuid stays null. Need to mock context_service resolution for null contextUuid.
        org.sifunity.broker.environment.domain.ContextService cs = new org.sifunity.broker.environment.domain.ContextService();
        cs.setId(mockContextServiceUuid);
        cs.setContextUuid(null);
        cs.setServiceName(serviceName);
        cs.setServiceType("OBJECT");
        when(contextServiceRepository.findByContextUuidAndServiceName(null, serviceName)).thenReturn(Optional.of(cs));
        
        AclEntry approvedEntry = new AclEntry();
        approvedEntry.setId(UUID.randomUUID());
        approvedEntry.setContextServiceUuid(mockContextServiceUuid);
        approvedEntry.setAccessType(accessType);
        approvedEntry.setAccessLevel(AccessLevel.APPROVED);
        
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, mockContextServiceUuid, accessType))
                .thenReturn(Optional.of(approvedEntry));
        
        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);
        
        // Then
        assertThat(response.granted()).isTrue();
    }
    
    @Test
    void checkPermission_WithDifferentAccessTypes_AreIndependent() {
        // Given
        PermissionCheckRequest consumeRequest = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, AccessType.QUERY);
        PermissionCheckRequest provideRequest = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, AccessType.PROVIDE);
        
        Environment environment = createEnvironment(EnvironmentType.BROKERED);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        
        // Only CONSUME permission is approved
        AclEntry consumeEntry = new AclEntry();
        consumeEntry.setId(UUID.randomUUID());
        consumeEntry.setContextServiceUuid(mockContextServiceUuid);
        consumeEntry.setAccessType(AccessType.QUERY);
        consumeEntry.setAccessLevel(AccessLevel.APPROVED);
        
        setupZoneContextMocks();
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, mockContextServiceUuid, AccessType.QUERY))
                .thenReturn(Optional.of(consumeEntry));
        
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, mockContextServiceUuid, AccessType.PROVIDE))
                .thenReturn(Optional.empty());
        
        // When
        PermissionCheckResponse consumeResponse = authorizationService.checkPermission(consumeRequest);
        PermissionCheckResponse provideResponse = authorizationService.checkPermission(provideRequest);
        
        // Then
        assertThat(consumeResponse.granted()).isTrue();
        assertThat(provideResponse.granted()).isFalse();
    }
    
    @Test
    void checkPermission_WithRepositoryException_ReturnsDenied() {
        // Given
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, accessType);
        
        when(environmentRepository.findById(any())).thenThrow(new RuntimeException("Database error"));
        
        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);
        
        // Then
        assertThat(response.granted()).isFalse();
    }
    
    @Test
    void checkPermission_SelfAccess_EnvironmentCanQueryOwnRecord() {
        // Given: environment queries its own record (serviceName = its own UUID)
        Environment environment = createEnvironment(EnvironmentType.DIRECT);
        UUID envId = environment.getId();
        PermissionCheckRequest request = new PermissionCheckRequest(
                envId, envId.toString(), zoneId, contextId, AccessType.QUERY);

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(environment));

        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);

        // Then
        assertThat(response.granted()).isTrue();
    }

    @Test
    void checkPermission_SelfAccess_EnvironmentCannotQueryOtherRecord() {
        // Given: environment tries to query a different environment's record
        Environment environment = createEnvironment(EnvironmentType.DIRECT);
        UUID envId = environment.getId();
        UUID otherId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
                envId, otherId.toString(), zoneId, contextId, AccessType.QUERY);

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(environment));
        setupZoneContextMocks();
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(envId, mockContextServiceUuid, AccessType.QUERY))
                .thenReturn(Optional.empty());

        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);

        // Then
        assertThat(response.granted()).isFalse();
    }

    @Test
    void checkPermission_SelfAccess_BrokeredEnvironmentCanQueryOwnRecord() {
        // Given: BROKERED environment queries its own record
        Environment environment = createEnvironment(EnvironmentType.BROKERED);
        UUID envId = environment.getId();
        PermissionCheckRequest request = new PermissionCheckRequest(
                envId, envId.toString(), zoneId, contextId, AccessType.QUERY);

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(environment));

        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);

        // Then
        assertThat(response.granted()).isTrue();
    }

    private Environment createEnvironment(EnvironmentType type) {
        Environment env = new Environment();
        env.setId(UUID.randomUUID());
        env.setType(type);
        env.setApplicationName("Test App");
        env.setSessionToken(UUID.randomUUID().toString());
        env.setSharedSecret("secret");
        env.setDefaultZoneId("default-zone");
        env.setSolutionUuid(UUID.randomUUID());
        return env;
    }

    private Environment createAdminEnvironment() {
        Environment env = createEnvironment(EnvironmentType.BROKERED);
        UUID solUuid = UUID.randomUUID();
        env.setSolutionUuid(solUuid);
        Solution globalSolution = new Solution();
        globalSolution.setId(solUuid);
        globalSolution.setDomainUuid(UUID.randomUUID());
        globalSolution.setSolutionId(GlobalAdminConstants.GLOBAL_SOLUTION);
        when(solutionService.findByIdCached(solUuid))
            .thenReturn(Optional.of(globalSolution));
        org.sifunity.broker.environment.domain.Domain globalDomain = new org.sifunity.broker.environment.domain.Domain();
        globalDomain.setId(globalSolution.getDomainUuid());
        globalDomain.setDomainId(GlobalAdminConstants.GLOBAL_DOMAIN);
        when(domainService.findByIdCached(globalSolution.getDomainUuid()))
            .thenReturn(Optional.of(globalDomain));
        return env;
    }

    
    // ========== Tests for isAdmin ==========
    
    @Test
    void isAdmin_WhenFindByIdCachedReturnsEmpty_ReturnsFalse() {
        // Given: an environment with a solutionUuid, but solutionService returns empty
        Environment env = createEnvironment(EnvironmentType.BROKERED);
        UUID solUuid = env.getSolutionUuid();
        when(solutionService.findByIdCached(solUuid)).thenReturn(Optional.empty());
        
        // When
        boolean result = authorizationService.isAdmin(env);
        
        // Then
        assertThat(result).isFalse();
    }
    
    // ========== Tests for canCreateEnvironment ==========
    
    @Test
    void canCreateEnvironment_WithAdminEnvironment_ReturnsTrue() {
        // Given
        Environment admin = createAdminEnvironment();
        
        // When
        boolean canCreate = authorizationService.canCreateEnvironment(admin);
        
        // Then
        assertThat(canCreate).isTrue();
    }
    
    @Test
    void canCreateEnvironment_WithConsumerEnvironment_ReturnsFalse() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canCreate = authorizationService.canCreateEnvironment(consumer);
        
        // Then
        assertThat(canCreate).isFalse();
    }
    
    @Test
    void canCreateEnvironment_WithProviderEnvironment_ReturnsFalse() {
        // Given
        Environment provider = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canCreate = authorizationService.canCreateEnvironment(provider);
        
        // Then
        assertThat(canCreate).isFalse();
    }
    
    @Test
    void canCreateEnvironment_WithBrokerEnvironment_ReturnsFalse() {
        // Given
        Environment broker = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canCreate = authorizationService.canCreateEnvironment(broker);
        
        // Then
        assertThat(canCreate).isFalse();
    }
    
    @Test
    void canCreateEnvironment_WithNullEnvironment_ReturnsFalse() {
        // When
        boolean canCreate = authorizationService.canCreateEnvironment(null);
        
        // Then
        assertThat(canCreate).isFalse();
    }
    
    // ========== Tests for canReadEnvironment ==========
    
    @Test
    void canReadEnvironment_AdminCanReadAnyEnvironment() {
        // Given
        Environment admin = createAdminEnvironment();
        Environment target = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canRead = authorizationService.canReadEnvironment(admin, target);
        
        // Then
        assertThat(canRead).isTrue();
    }
    
    @Test
    void canReadEnvironment_EnvironmentCanReadOwnData() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canRead = authorizationService.canReadEnvironment(consumer, consumer);
        
        // Then
        assertThat(canRead).isTrue();
    }
    
    @Test
    void canReadEnvironment_EnvironmentCannotReadOtherEnvironment() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        Environment other = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canRead = authorizationService.canReadEnvironment(consumer, other);
        
        // Then
        assertThat(canRead).isFalse();
    }
    
    @Test
    void canReadEnvironment_WithNullRequester_ReturnsFalse() {
        // Given
        Environment target = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canRead = authorizationService.canReadEnvironment(null, target);
        
        // Then
        assertThat(canRead).isFalse();
    }
    
    @Test
    void canReadEnvironment_WithNullTarget_ReturnsFalse() {
        // Given
        Environment requester = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canRead = authorizationService.canReadEnvironment(requester, null);
        
        // Then
        assertThat(canRead).isFalse();
    }
    
    // ========== Tests for canUpdateEnvironment ==========
    
    @Test
    void canUpdateEnvironment_AdminCanUpdateAnyEnvironment() {
        // Given
        Environment admin = createAdminEnvironment();
        Environment target = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canUpdate = authorizationService.canUpdateEnvironment(admin, target);
        
        // Then
        assertThat(canUpdate).isTrue();
    }
    
    @Test
    void canUpdateEnvironment_EnvironmentCanUpdateOwnData() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canUpdate = authorizationService.canUpdateEnvironment(consumer, consumer);
        
        // Then
        assertThat(canUpdate).isTrue();
    }
    
    @Test
    void canUpdateEnvironment_EnvironmentCannotUpdateOtherEnvironment() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        Environment other = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canUpdate = authorizationService.canUpdateEnvironment(consumer, other);
        
        // Then
        assertThat(canUpdate).isFalse();
    }
    
    @Test
    void canUpdateEnvironment_WithNullRequester_ReturnsFalse() {
        // Given
        Environment target = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canUpdate = authorizationService.canUpdateEnvironment(null, target);
        
        // Then
        assertThat(canUpdate).isFalse();
    }
    
    @Test
    void canUpdateEnvironment_WithNullTarget_ReturnsFalse() {
        // Given
        Environment requester = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canUpdate = authorizationService.canUpdateEnvironment(requester, null);
        
        // Then
        assertThat(canUpdate).isFalse();
    }
    
    // ========== Tests for canDeleteEnvironment ==========
    
    @Test
    void canDeleteEnvironment_AdminCanDeleteAnyEnvironment() {
        // Given
        Environment admin = createAdminEnvironment();
        Environment target = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canDelete = authorizationService.canDeleteEnvironment(admin, target);
        
        // Then
        assertThat(canDelete).isTrue();
    }
    
    @Test
    void canDeleteEnvironment_ConsumerCannotDeleteEnvironment() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        Environment target = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canDelete = authorizationService.canDeleteEnvironment(consumer, target);
        
        // Then
        assertThat(canDelete).isFalse();
    }
    
    @Test
    void canDeleteEnvironment_EnvironmentCannotDeleteOwnData() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canDelete = authorizationService.canDeleteEnvironment(consumer, consumer);
        
        // Then
        assertThat(canDelete).isFalse();
    }
    
    @Test
    void canDeleteEnvironment_WithNullRequester_ReturnsFalse() {
        // Given
        Environment target = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canDelete = authorizationService.canDeleteEnvironment(null, target);
        
        // Then
        assertThat(canDelete).isFalse();
    }
    
    @Test
    void canDeleteEnvironment_WithNullTarget_ReturnsFalse() {
        // Given
        Environment requester = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canDelete = authorizationService.canDeleteEnvironment(requester, null);
        
        // Then
        assertThat(canDelete).isFalse();
    }
    
    // ========== Tests for canCreateAcl ==========
    
    @Test
    void canCreateAcl_AdminCanCreateAcl() {
        // Given
        Environment admin = createAdminEnvironment();
        
        // When
        boolean canCreate = authorizationService.canCreateAcl(admin);
        
        // Then
        assertThat(canCreate).isTrue();
    }
    
    @Test
    void canCreateAcl_ConsumerCannotCreateAcl() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canCreate = authorizationService.canCreateAcl(consumer);
        
        // Then
        assertThat(canCreate).isFalse();
    }
    
    @Test
    void canCreateAcl_ProviderCannotCreateAcl() {
        // Given
        Environment provider = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canCreate = authorizationService.canCreateAcl(provider);
        
        // Then
        assertThat(canCreate).isFalse();
    }
    
    @Test
    void canCreateAcl_BrokerCannotCreateAcl() {
        // Given
        Environment broker = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canCreate = authorizationService.canCreateAcl(broker);
        
        // Then
        assertThat(canCreate).isFalse();
    }
    
    @Test
    void canCreateAcl_WithNullEnvironment_ReturnsFalse() {
        // When
        boolean canCreate = authorizationService.canCreateAcl(null);
        
        // Then
        assertThat(canCreate).isFalse();
    }
    
    // ========== Tests for canReadAcl ==========
    
    @Test
    void canReadAcl_AdminCanReadAnyAcl() {
        // Given
        Environment admin = createAdminEnvironment();
        Environment owner = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(owner);
        
        // When
        boolean canRead = authorizationService.canReadAcl(admin, acl);
        
        // Then
        assertThat(canRead).isTrue();
    }
    
    @Test
    void canReadAcl_EnvironmentCanReadOwnAcl() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(consumer);
        
        // When
        boolean canRead = authorizationService.canReadAcl(consumer, acl);
        
        // Then
        assertThat(canRead).isTrue();
    }
    
    @Test
    void canReadAcl_EnvironmentCannotReadOtherAcl() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        Environment other = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(other);
        
        // When
        boolean canRead = authorizationService.canReadAcl(consumer, acl);
        
        // Then
        assertThat(canRead).isFalse();
    }
    
    @Test
    void canReadAcl_WithNullRequester_ReturnsFalse() {
        // Given
        Environment owner = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(owner);
        
        // When
        boolean canRead = authorizationService.canReadAcl(null, acl);
        
        // Then
        assertThat(canRead).isFalse();
    }
    
    @Test
    void canReadAcl_WithNullAcl_ReturnsFalse() {
        // Given
        Environment requester = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canRead = authorizationService.canReadAcl(requester, null);
        
        // Then
        assertThat(canRead).isFalse();
    }
    
    // ========== Tests for canUpdateAcl ==========
    
    @Test
    void canUpdateAcl_AdminCanUpdateAnyAcl() {
        // Given
        Environment admin = createAdminEnvironment();
        Environment owner = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(owner);
        
        // When
        boolean canUpdate = authorizationService.canUpdateAcl(admin, acl);
        
        // Then
        assertThat(canUpdate).isTrue();
    }
    
    @Test
    void canUpdateAcl_ConsumerCannotUpdateAcl() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(consumer);
        
        // When
        boolean canUpdate = authorizationService.canUpdateAcl(consumer, acl);
        
        // Then
        assertThat(canUpdate).isFalse();
    }
    
    @Test
    void canUpdateAcl_WithNullRequester_ReturnsFalse() {
        // Given
        Environment owner = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(owner);
        
        // When
        boolean canUpdate = authorizationService.canUpdateAcl(null, acl);
        
        // Then
        assertThat(canUpdate).isFalse();
    }
    
    @Test
    void canUpdateAcl_WithNullAcl_ReturnsFalse() {
        // Given
        Environment requester = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canUpdate = authorizationService.canUpdateAcl(requester, null);
        
        // Then
        assertThat(canUpdate).isFalse();
    }
    
    // ========== Tests for canDeleteAcl ==========
    
    @Test
    void canDeleteAcl_AdminCanDeleteAnyAcl() {
        // Given
        Environment admin = createAdminEnvironment();
        Environment owner = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(owner);
        
        // When
        boolean canDelete = authorizationService.canDeleteAcl(admin, acl);
        
        // Then
        assertThat(canDelete).isTrue();
    }
    
    @Test
    void canDeleteAcl_ConsumerCannotDeleteAcl() {
        // Given
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(consumer);
        
        // When
        boolean canDelete = authorizationService.canDeleteAcl(consumer, acl);
        
        // Then
        assertThat(canDelete).isFalse();
    }
    
    @Test
    void canDeleteAcl_WithNullRequester_ReturnsFalse() {
        // Given
        Environment owner = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(owner);
        
        // When
        boolean canDelete = authorizationService.canDeleteAcl(null, acl);
        
        // Then
        assertThat(canDelete).isFalse();
    }
    
    @Test
    void canDeleteAcl_WithNullAcl_ReturnsFalse() {
        // Given
        Environment requester = createEnvironment(EnvironmentType.BROKERED);
        
        // When
        boolean canDelete = authorizationService.canDeleteAcl(requester, null);
        
        // Then
        assertThat(canDelete).isFalse();
    }
    
    // ========== Tests for zone/context string resolution to contextUuid ==========

    @Test
    void checkPermission_WithUnknownZoneId_ReturnsDenied() {
        // Given: zoneId that doesn't exist in the zones table
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, "unknown-zone", contextId, accessType);

        Environment environment = createEnvironment(EnvironmentType.BROKERED);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(zoneRepository.findBySolutionUuidAndZoneId(any(), eq("unknown-zone"))).thenReturn(Optional.empty());

        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);

        // Then — deny by default when zone can't be resolved
        assertThat(response.granted()).isFalse();
    }

    @Test
    void checkPermission_WithUnknownContextId_ReturnsDenied() {
        // Given: valid zone but contextId that doesn't exist under that zone
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, "unknown-context", accessType);

        Environment environment = createEnvironment(EnvironmentType.BROKERED);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        org.sifunity.broker.environment.domain.Zone zone = new org.sifunity.broker.environment.domain.Zone();
        zone.setId(mockZoneUuid);
        zone.setZoneId(zoneId);
        when(zoneRepository.findBySolutionUuidAndZoneId(any(), eq(zoneId))).thenReturn(Optional.of(zone));
        when(contextRepository.findByZoneUuidAndContextId(mockZoneUuid, "unknown-context")).thenReturn(Optional.empty());

        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);

        // Then — deny by default when context can't be resolved
        assertThat(response.granted()).isFalse();
    }

    @Test
    void checkPermission_ZoneContextResolution_ResolvesToCorrectContextUuid() {
        // Given: valid zone and context that resolve to a known contextUuid
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, accessType);

        Environment environment = createEnvironment(EnvironmentType.BROKERED);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        setupZoneContextMocks();

        AclEntry approvedEntry = new AclEntry();
        approvedEntry.setId(UUID.randomUUID());
        approvedEntry.setContextServiceUuid(mockContextServiceUuid);
        approvedEntry.setAccessType(accessType);
        approvedEntry.setAccessLevel(AccessLevel.APPROVED);

        // The repository should be called with the resolved mockContextUuid, not with strings
        when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, mockContextServiceUuid, accessType))
                .thenReturn(Optional.of(approvedEntry));

        // When
        PermissionCheckResponse response = authorizationService.checkPermission(request);

        // Then
        assertThat(response.granted()).isTrue();
    }

    // ========== Helper Methods ==========
    
    private static final UUID mockZoneUuid = UUID.randomUUID();
    private static final UUID mockContextUuid = UUID.randomUUID();
    private static final UUID mockContextServiceUuid = UUID.randomUUID();
    
    private void setupZoneContextMocks() {
        org.sifunity.broker.environment.domain.Zone zone = new org.sifunity.broker.environment.domain.Zone();
        zone.setId(mockZoneUuid);
        zone.setZoneId(zoneId);
        when(zoneRepository.findBySolutionUuidAndZoneId(any(), eq(zoneId))).thenReturn(Optional.of(zone));
        
        org.sifunity.broker.environment.domain.Context context = new org.sifunity.broker.environment.domain.Context();
        context.setId(mockContextUuid);
        context.setZoneUuid(mockZoneUuid);
        context.setContextId(contextId);
        when(contextRepository.findByZoneUuidAndContextId(mockZoneUuid, contextId)).thenReturn(Optional.of(context));
        
        org.sifunity.broker.environment.domain.ContextService cs = new org.sifunity.broker.environment.domain.ContextService();
        cs.setId(mockContextServiceUuid);
        cs.setContextUuid(mockContextUuid);
        cs.setServiceName(serviceName);
        cs.setServiceType("OBJECT");
        when(contextServiceRepository.findByContextUuidAndServiceName(eq(mockContextUuid), any())).thenReturn(Optional.of(cs));
    }
    
    private AclEntry createAclEntry(Environment owner) {
        AclEntry acl = new AclEntry();
        acl.setId(UUID.randomUUID());
        acl.setEnvironment(owner);
        acl.setContextServiceUuid(mockContextServiceUuid);
        acl.setAccessType(AccessType.QUERY);
        acl.setAccessLevel(AccessLevel.APPROVED);
        return acl;
    }
}
