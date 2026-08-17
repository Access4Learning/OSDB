// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.AclEntryUpdateRequest;
import org.sifunity.broker.environment.exception.AclEntryNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateAclEntryException;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AclService.
 * 
 * <p>Tests cover the ACL creation flow with zoneId/contextId resolution,
 * admin/self-provisioning enforcement, listing, updating, and deleting.</p>
 */
@ExtendWith(MockitoExtension.class)
class AclServiceTest {
    
    @Mock
    private AclEntryRepository aclEntryRepository;
    
    @Mock
    private EnvironmentRepository environmentRepository;
    
    @Mock
    private ContextRepository contextRepository;
    
    @Mock
    private ContextServiceService contextServiceService;
    
    @Mock
    private ContextServiceRepository contextServiceRepository;
    
    @Mock
    private SolutionRepository solutionRepository;
    
    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private DomainRepository domainRepository;

    private AclService aclService;

    private static final String SOLUTION_ID = "test-solution";
    private static final String ZONE_ID = "DEFAULT";
    private static final String CONTEXT_ID = "DEFAULT";
    private static final String DOMAIN_ID = "test-domain";
    private static final UUID DOMAIN_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        aclService = new AclService(aclEntryRepository, environmentRepository, contextRepository,
                contextServiceService, contextServiceRepository, solutionRepository, zoneRepository,
                domainRepository);
    }
    
    @Nested
    class CreateAclEntry {
        
        @Test
        void success_resolvesContextServiceAndSetsUuidOnEntity() {
            // Given
            UUID environmentId = UUID.randomUUID();
            String serviceName = "StudentService";
            UUID contextUuid = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            UUID zoneUuid = UUID.randomUUID();
            AccessType accessType = AccessType.QUERY;
            AccessLevel accessLevel = AccessLevel.APPROVED;
            
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                    environmentId, serviceName, ZONE_ID, CONTEXT_ID, accessType, accessLevel);
            
            Environment environment = createTestEnvironment(environmentId);
            
            // Mock resolution chain
            mockResolveContext(solutionUuid, zoneUuid, contextUuid);
            
            AclEntry savedAclEntry = new AclEntry();
            savedAclEntry.setId(UUID.randomUUID());
            savedAclEntry.setEnvironment(environment);
            savedAclEntry.setContextServiceUuid(contextServiceUuid);
            savedAclEntry.setAccessType(accessType);
            savedAclEntry.setAccessLevel(accessLevel);
            
            ContextService cs = createTestContextService(contextServiceUuid, contextUuid, serviceName);
            Context context = createTestContext(contextUuid, zoneUuid, CONTEXT_ID);
            Zone zone = createTestZone(zoneUuid, solutionUuid, ZONE_ID);
            
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
            when(contextServiceService.resolveOrCreate(contextUuid, serviceName, "OBJECT"))
                    .thenReturn(contextServiceUuid);
            when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, accessType)).thenReturn(false);
            when(aclEntryRepository.save(any(AclEntry.class))).thenReturn(savedAclEntry);
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(cs));
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
            
            // When (admin path)
            AclEntryDTO result = aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, environment, true);
            
            // Then - verify contextServiceUuid is set on entity
            ArgumentCaptor<AclEntry> captor = ArgumentCaptor.forClass(AclEntry.class);
            verify(aclEntryRepository).save(captor.capture());
            AclEntry capturedEntry = captor.getValue();
            assertThat(capturedEntry.getContextServiceUuid()).isEqualTo(contextServiceUuid);
            
            // Verify DTO maps back to zoneId/contextId
            assertThat(result).isNotNull();
            assertThat(result.serviceName()).isEqualTo(serviceName);
            assertThat(result.zoneId()).isEqualTo(ZONE_ID);
            assertThat(result.contextId()).isEqualTo(CONTEXT_ID);
            assertThat(result.accessType()).isEqualTo(accessType);
            assertThat(result.accessLevel()).isEqualTo(accessLevel);
            assertThat(result.environmentId()).isEqualTo(environmentId);
        }
        
        @Test
        void success_withProvideAccessType() {
            // Given
            UUID environmentId = UUID.randomUUID();
            String serviceName = "StudentService";
            UUID contextUuid = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            UUID zoneUuid = UUID.randomUUID();
            AccessType accessType = AccessType.PROVIDE;
            AccessLevel accessLevel = AccessLevel.APPROVED;
            
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                    environmentId, serviceName, ZONE_ID, CONTEXT_ID, accessType, accessLevel);
            
            Environment environment = createTestEnvironment(environmentId);
            
            mockResolveContext(solutionUuid, zoneUuid, contextUuid);
            
            AclEntry savedAclEntry = new AclEntry();
            savedAclEntry.setId(UUID.randomUUID());
            savedAclEntry.setEnvironment(environment);
            savedAclEntry.setContextServiceUuid(contextServiceUuid);
            savedAclEntry.setAccessType(accessType);
            savedAclEntry.setAccessLevel(accessLevel);
            
            ContextService cs = createTestContextService(contextServiceUuid, contextUuid, serviceName);
            Context context = createTestContext(contextUuid, zoneUuid, CONTEXT_ID);
            Zone zone = createTestZone(zoneUuid, solutionUuid, ZONE_ID);
            
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
            when(contextServiceService.resolveOrCreate(contextUuid, serviceName, "OBJECT"))
                    .thenReturn(contextServiceUuid);
            when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, accessType)).thenReturn(false);
            when(aclEntryRepository.existsByContextServiceUuidAndAccessType(
                    contextServiceUuid, AccessType.PROVIDE)).thenReturn(false);
            when(aclEntryRepository.save(any(AclEntry.class))).thenReturn(savedAclEntry);
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(cs));
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
            
            // When
            AclEntryDTO result = aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, environment, true);
            
            // Then
            assertThat(result).isNotNull();
            assertThat(result.accessType()).isEqualTo(AccessType.PROVIDE);
            verify(aclEntryRepository).existsByContextServiceUuidAndAccessType(
                    contextServiceUuid, AccessType.PROVIDE);
        }
        
        @Test
        void throwsEnvironmentNotFoundException_whenEnvironmentMissing() {
            UUID environmentId = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            UUID zoneUuid = UUID.randomUUID();
            UUID contextUuid = UUID.randomUUID();
            
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                    environmentId, "Service", ZONE_ID, CONTEXT_ID, AccessType.QUERY, AccessLevel.APPROVED);
            
            Environment requester = createTestEnvironment(environmentId);
            mockResolveContext(solutionUuid, zoneUuid, contextUuid);
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.empty());
            
            assertThatThrownBy(() -> aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, requester, true))
                    .isInstanceOf(EnvironmentNotFoundException.class);
        }
        
        @Test
        void throwsSifBrokerException_whenSolutionNotFound() {
            UUID environmentId = UUID.randomUUID();
            
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                    environmentId, "Service", ZONE_ID, CONTEXT_ID, AccessType.QUERY, AccessLevel.APPROVED);
            
            Environment requester = createTestEnvironment(environmentId);
            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, SOLUTION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, requester, true))
                    .isInstanceOf(SifBrokerException.class)
                    .hasMessageContaining("Solution not found");
        }
        
        @Test
        void throwsSifBrokerException_whenZoneNotFound() {
            UUID environmentId = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                    environmentId, "Service", ZONE_ID, CONTEXT_ID, AccessType.QUERY, AccessLevel.APPROVED);
            
            Environment requester = createTestEnvironment(environmentId);
            Solution solution = new Solution();
            solution.setId(solutionUuid);
            solution.setSolutionId(SOLUTION_ID);
            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, SOLUTION_ID)).thenReturn(Optional.of(solution));
            when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, ZONE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, requester, true))
                    .isInstanceOf(SifBrokerException.class)
                    .hasMessageContaining("Zone not found");
        }
        
        @Test
        void throwsSifBrokerException_whenContextNotFound() {
            UUID environmentId = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            UUID zoneUuid = UUID.randomUUID();
            
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                    environmentId, "Service", ZONE_ID, CONTEXT_ID, AccessType.QUERY, AccessLevel.APPROVED);
            
            Environment requester = createTestEnvironment(environmentId);
            Solution solution = new Solution();
            solution.setId(solutionUuid);
            solution.setSolutionId(SOLUTION_ID);
            Zone zone = createTestZone(zoneUuid, solutionUuid, ZONE_ID);
            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, SOLUTION_ID)).thenReturn(Optional.of(solution));
            when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, ZONE_ID)).thenReturn(Optional.of(zone));
            when(contextRepository.findByZoneUuidAndContextId(zoneUuid, CONTEXT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, requester, true))
                    .isInstanceOf(SifBrokerException.class)
                    .hasMessageContaining("Context not found");
        }
        
        @Test
        void throwsDuplicateAclEntryException_whenDuplicateExists() {
            UUID environmentId = UUID.randomUUID();
            UUID contextUuid = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            UUID zoneUuid = UUID.randomUUID();
            String serviceName = "StudentService";
            AccessType accessType = AccessType.QUERY;
            
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                    environmentId, serviceName, ZONE_ID, CONTEXT_ID, accessType, AccessLevel.APPROVED);
            
            Environment environment = createTestEnvironment(environmentId);
            
            mockResolveContext(solutionUuid, zoneUuid, contextUuid);
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
            when(contextServiceService.resolveOrCreate(contextUuid, serviceName, "OBJECT"))
                    .thenReturn(contextServiceUuid);
            when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, accessType)).thenReturn(true);
            
            assertThatThrownBy(() -> aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, environment, true))
                    .isInstanceOf(DuplicateAclEntryException.class);
        }
        
        @Test
        void throwsSifBrokerException_whenSecondProviderForSameContextService() {
            UUID environmentId = UUID.randomUUID();
            UUID contextUuid = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            UUID zoneUuid = UUID.randomUUID();
            String serviceName = "StudentService";
            
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                    environmentId, serviceName, ZONE_ID, CONTEXT_ID, AccessType.PROVIDE, AccessLevel.APPROVED);
            
            Environment environment = createTestEnvironment(environmentId);
            
            mockResolveContext(solutionUuid, zoneUuid, contextUuid);
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
            when(contextServiceService.resolveOrCreate(contextUuid, serviceName, "OBJECT"))
                    .thenReturn(contextServiceUuid);
            when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, AccessType.PROVIDE)).thenReturn(false);
            when(aclEntryRepository.existsByContextServiceUuidAndAccessType(
                    contextServiceUuid, AccessType.PROVIDE)).thenReturn(true);
            
            assertThatThrownBy(() -> aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, environment, true))
                    .isInstanceOf(SifBrokerException.class)
                    .hasMessageContaining("provider already exists");
        }
        
        @Test
        void nonAdmin_forcesOwnEnvironmentIdAndPendingAccessLevel() {
            // Given
            UUID requestedEnvironmentId = UUID.randomUUID();
            UUID requesterEnvironmentId = UUID.randomUUID();
            String serviceName = "StudentService";
            UUID contextUuid = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            UUID zoneUuid = UUID.randomUUID();
            
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                    requestedEnvironmentId, serviceName, ZONE_ID, CONTEXT_ID, 
                    AccessType.QUERY, AccessLevel.APPROVED);
            
            Environment requester = createTestEnvironment(requesterEnvironmentId);
            
            mockResolveContext(solutionUuid, zoneUuid, contextUuid);
            
            AclEntry savedAclEntry = new AclEntry();
            savedAclEntry.setId(UUID.randomUUID());
            savedAclEntry.setEnvironment(requester);
            savedAclEntry.setContextServiceUuid(contextServiceUuid);
            savedAclEntry.setAccessType(AccessType.QUERY);
            savedAclEntry.setAccessLevel(AccessLevel.PENDING);
            
            ContextService cs = createTestContextService(contextServiceUuid, contextUuid, serviceName);
            Context context = createTestContext(contextUuid, zoneUuid, CONTEXT_ID);
            Zone zone = createTestZone(zoneUuid, solutionUuid, ZONE_ID);
            
            when(environmentRepository.findById(requesterEnvironmentId)).thenReturn(Optional.of(requester));
            when(contextServiceService.resolveOrCreate(contextUuid, serviceName, "OBJECT"))
                    .thenReturn(contextServiceUuid);
            when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessType(
                    requesterEnvironmentId, contextServiceUuid, AccessType.QUERY)).thenReturn(false);
            when(aclEntryRepository.save(any(AclEntry.class))).thenReturn(savedAclEntry);
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(cs));
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
            
            // When (non-admin path)
            AclEntryDTO result = aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, requester, false);
            
            // Then - verify environment forced to requester's own, access level forced to PENDING
            ArgumentCaptor<AclEntry> captor = ArgumentCaptor.forClass(AclEntry.class);
            verify(aclEntryRepository).save(captor.capture());
            AclEntry capturedEntry = captor.getValue();
            assertThat(capturedEntry.getEnvironment().getId()).isEqualTo(requesterEnvironmentId);
            assertThat(capturedEntry.getAccessLevel()).isEqualTo(AccessLevel.PENDING);
            
            assertThat(result.environmentId()).isEqualTo(requesterEnvironmentId);
            assertThat(result.accessLevel()).isEqualTo(AccessLevel.PENDING);
        }
    }
    
    @Nested
    class ListAclEntries {
        
        @Test
        void returnsEmptyList_whenNoEntries() {
            UUID environmentId = UUID.randomUUID();
            when(aclEntryRepository.findByEnvironmentId(environmentId)).thenReturn(List.of());
            
            List<AclEntryDTO> result = aclService.listAclEntries(environmentId);
            
            assertThat(result).isEmpty();
        }
        
        @Test
        void mapsContextServiceBackToZoneIdAndContextId() {
            // Given
            UUID environmentId = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            UUID contextUuid = UUID.randomUUID();
            UUID zoneUuid = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            String serviceName = "StudentService";
            
            Environment environment = createTestEnvironment(environmentId);
            
            AclEntry entry = new AclEntry();
            entry.setId(UUID.randomUUID());
            entry.setEnvironment(environment);
            entry.setContextServiceUuid(contextServiceUuid);
            entry.setAccessType(AccessType.QUERY);
            entry.setAccessLevel(AccessLevel.APPROVED);
            
            ContextService cs = createTestContextService(contextServiceUuid, contextUuid, serviceName);
            Context context = createTestContext(contextUuid, zoneUuid, CONTEXT_ID);
            Zone zone = createTestZone(zoneUuid, solutionUuid, ZONE_ID);
            
            when(aclEntryRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(entry));
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(cs));
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
            
            // When
            List<AclEntryDTO> result = aclService.listAclEntries(environmentId);
            
            // Then - verify mapToAclEntryDTO resolves back to zoneId/contextId
            assertThat(result).hasSize(1);
            AclEntryDTO dto = result.get(0);
            assertThat(dto.serviceName()).isEqualTo(serviceName);
            assertThat(dto.zoneId()).isEqualTo(ZONE_ID);
            assertThat(dto.contextId()).isEqualTo(CONTEXT_ID);
            assertThat(dto.environmentId()).isEqualTo(environmentId);
            assertThat(dto.accessType()).isEqualTo(AccessType.QUERY);
            assertThat(dto.accessLevel()).isEqualTo(AccessLevel.APPROVED);
            
            verify(contextServiceRepository).findById(contextServiceUuid);
        }
        
        @Test
        void handlesMultipleEntries_eachResolvingDifferentContextService() {
            // Given
            UUID environmentId = UUID.randomUUID();
            UUID csUuid1 = UUID.randomUUID();
            UUID csUuid2 = UUID.randomUUID();
            UUID contextUuid1 = UUID.randomUUID();
            UUID contextUuid2 = UUID.randomUUID();
            UUID zoneUuid1 = UUID.randomUUID();
            UUID zoneUuid2 = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            
            Environment environment = createTestEnvironment(environmentId);
            
            AclEntry entry1 = new AclEntry();
            entry1.setId(UUID.randomUUID());
            entry1.setEnvironment(environment);
            entry1.setContextServiceUuid(csUuid1);
            entry1.setAccessType(AccessType.QUERY);
            entry1.setAccessLevel(AccessLevel.APPROVED);
            
            AclEntry entry2 = new AclEntry();
            entry2.setId(UUID.randomUUID());
            entry2.setEnvironment(environment);
            entry2.setContextServiceUuid(csUuid2);
            entry2.setAccessType(AccessType.PROVIDE);
            entry2.setAccessLevel(AccessLevel.APPROVED);
            
            ContextService cs1 = createTestContextService(csUuid1, contextUuid1, "StudentService");
            ContextService cs2 = createTestContextService(csUuid2, contextUuid2, "SchoolService");
            Context context1 = createTestContext(contextUuid1, zoneUuid1, "CTX1");
            Context context2 = createTestContext(contextUuid2, zoneUuid2, "CTX2");
            Zone zone1 = createTestZone(zoneUuid1, solutionUuid, "ZONE1");
            Zone zone2 = createTestZone(zoneUuid2, solutionUuid, "ZONE2");
            
            when(aclEntryRepository.findByEnvironmentId(environmentId))
                    .thenReturn(List.of(entry1, entry2));
            when(contextServiceRepository.findById(csUuid1)).thenReturn(Optional.of(cs1));
            when(contextServiceRepository.findById(csUuid2)).thenReturn(Optional.of(cs2));
            when(contextRepository.findById(contextUuid1)).thenReturn(Optional.of(context1));
            when(contextRepository.findById(contextUuid2)).thenReturn(Optional.of(context2));
            when(zoneRepository.findById(zoneUuid1)).thenReturn(Optional.of(zone1));
            when(zoneRepository.findById(zoneUuid2)).thenReturn(Optional.of(zone2));
            
            // When
            List<AclEntryDTO> result = aclService.listAclEntries(environmentId);
            
            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).serviceName()).isEqualTo("StudentService");
            assertThat(result.get(0).zoneId()).isEqualTo("ZONE1");
            assertThat(result.get(0).contextId()).isEqualTo("CTX1");
            assertThat(result.get(1).serviceName()).isEqualTo("SchoolService");
            assertThat(result.get(1).zoneId()).isEqualTo("ZONE2");
            assertThat(result.get(1).contextId()).isEqualTo("CTX2");
        }
        
        @Test
        void handlesNullContextService_whenNotFound() {
            // Given - contextServiceRepository returns empty (orphaned entry edge case)
            UUID environmentId = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            
            Environment environment = createTestEnvironment(environmentId);
            
            AclEntry entry = new AclEntry();
            entry.setId(UUID.randomUUID());
            entry.setEnvironment(environment);
            entry.setContextServiceUuid(contextServiceUuid);
            entry.setAccessType(AccessType.QUERY);
            entry.setAccessLevel(AccessLevel.APPROVED);
            
            when(aclEntryRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(entry));
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.empty());
            
            // When
            List<AclEntryDTO> result = aclService.listAclEntries(environmentId);
            
            // Then - DTO has null serviceName/zoneId/contextId when context_service not found
            assertThat(result).hasSize(1);
            assertThat(result.get(0).serviceName()).isNull();
            assertThat(result.get(0).zoneId()).isNull();
            assertThat(result.get(0).contextId()).isNull();
        }
    }
    
    @Nested
    class UpdateAclEntry {
        
        @Test
        void success_updatesAccessLevelAndResolvesContextService() {
            // Given
            UUID aclEntryId = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            UUID contextUuid = UUID.randomUUID();
            UUID zoneUuid = UUID.randomUUID();
            UUID solutionUuid = UUID.randomUUID();
            UUID environmentId = UUID.randomUUID();
            String serviceName = "StudentService";
            
            Environment environment = createTestEnvironment(environmentId);
            
            AclEntry existingEntry = new AclEntry();
            existingEntry.setId(aclEntryId);
            existingEntry.setEnvironment(environment);
            existingEntry.setContextServiceUuid(contextServiceUuid);
            existingEntry.setAccessType(AccessType.QUERY);
            existingEntry.setAccessLevel(AccessLevel.REJECTED);
            
            AclEntry updatedEntry = new AclEntry();
            updatedEntry.setId(aclEntryId);
            updatedEntry.setEnvironment(environment);
            updatedEntry.setContextServiceUuid(contextServiceUuid);
            updatedEntry.setAccessType(AccessType.QUERY);
            updatedEntry.setAccessLevel(AccessLevel.APPROVED);
            
            ContextService cs = createTestContextService(contextServiceUuid, contextUuid, serviceName);
            Context context = createTestContext(contextUuid, zoneUuid, CONTEXT_ID);
            Zone zone = createTestZone(zoneUuid, solutionUuid, ZONE_ID);
            
            AclEntryUpdateRequest request = new AclEntryUpdateRequest(AccessLevel.APPROVED);
            
            when(aclEntryRepository.findById(aclEntryId)).thenReturn(Optional.of(existingEntry));
            when(aclEntryRepository.save(any(AclEntry.class))).thenReturn(updatedEntry);
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(cs));
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
            
            // When
            AclEntryDTO result = aclService.updateAclEntry(aclEntryId, request);
            
            // Then
            assertThat(result.accessLevel()).isEqualTo(AccessLevel.APPROVED);
            assertThat(result.serviceName()).isEqualTo(serviceName);
            assertThat(result.zoneId()).isEqualTo(ZONE_ID);
            assertThat(result.contextId()).isEqualTo(CONTEXT_ID);
            verify(contextServiceRepository).findById(contextServiceUuid);
        }
        
        @Test
        void throwsAclEntryNotFoundException_whenEntryMissing() {
            UUID aclEntryId = UUID.randomUUID();
            AclEntryUpdateRequest request = new AclEntryUpdateRequest(AccessLevel.APPROVED);
            
            when(aclEntryRepository.findById(aclEntryId)).thenReturn(Optional.empty());
            
            assertThatThrownBy(() -> aclService.updateAclEntry(aclEntryId, request))
                    .isInstanceOf(AclEntryNotFoundException.class);
        }
    }
    
    @Nested
    class DeleteAclEntry {
        
        @Test
        void success_deletesEntry() {
            UUID aclEntryId = UUID.randomUUID();
            AclEntry entry = new AclEntry();
            entry.setId(aclEntryId);
            
            when(aclEntryRepository.findById(aclEntryId)).thenReturn(Optional.of(entry));
            
            aclService.deleteAclEntry(aclEntryId);
            
            verify(aclEntryRepository).delete(entry);
        }
        
        @Test
        void throwsAclEntryNotFoundException_whenEntryMissing() {
            UUID aclEntryId = UUID.randomUUID();
            
            when(aclEntryRepository.findById(aclEntryId)).thenReturn(Optional.empty());
            
            assertThatThrownBy(() -> aclService.deleteAclEntry(aclEntryId))
                    .isInstanceOf(AclEntryNotFoundException.class);
        }
    }
    
    @Nested
    class HasPermission {
        
        @Test
        void returnsTrue_whenApprovedEntryExists() {
            UUID environmentId = UUID.randomUUID();
            String serviceName = "StudentService";
            UUID contextUuid = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            AccessType accessType = AccessType.QUERY;
            
            ContextService cs = createTestContextService(contextServiceUuid, contextUuid, serviceName);
            
            AclEntry approvedEntry = new AclEntry();
            approvedEntry.setId(UUID.randomUUID());
            approvedEntry.setAccessLevel(AccessLevel.APPROVED);
            
            when(contextServiceRepository.findByContextUuidAndServiceName(contextUuid, serviceName))
                    .thenReturn(Optional.of(cs));
            when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, accessType))
                    .thenReturn(Optional.of(approvedEntry));
            
            boolean result = aclService.hasPermission(environmentId, serviceName, contextUuid, accessType);
            
            assertThat(result).isTrue();
        }
        
        @Test
        void returnsFalse_whenNoContextServiceExists() {
            UUID environmentId = UUID.randomUUID();
            String serviceName = "StudentService";
            UUID contextUuid = UUID.randomUUID();
            AccessType accessType = AccessType.QUERY;
            
            when(contextServiceRepository.findByContextUuidAndServiceName(contextUuid, serviceName))
                    .thenReturn(Optional.empty());
            
            boolean result = aclService.hasPermission(environmentId, serviceName, contextUuid, accessType);
            
            assertThat(result).isFalse();
            verifyNoInteractions(aclEntryRepository);
        }
        
        @Test
        void returnsFalse_whenEntryExistsButNotApproved() {
            UUID environmentId = UUID.randomUUID();
            String serviceName = "StudentService";
            UUID contextUuid = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            AccessType accessType = AccessType.QUERY;
            
            ContextService cs = createTestContextService(contextServiceUuid, contextUuid, serviceName);
            
            AclEntry rejectedEntry = new AclEntry();
            rejectedEntry.setId(UUID.randomUUID());
            rejectedEntry.setAccessLevel(AccessLevel.REJECTED);
            
            when(contextServiceRepository.findByContextUuidAndServiceName(contextUuid, serviceName))
                    .thenReturn(Optional.of(cs));
            when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, accessType))
                    .thenReturn(Optional.of(rejectedEntry));
            
            boolean result = aclService.hasPermission(environmentId, serviceName, contextUuid, accessType);
            
            assertThat(result).isFalse();
        }
        
        @Test
        void returnsFalse_whenNoAclEntryExistsForContextService() {
            UUID environmentId = UUID.randomUUID();
            String serviceName = "StudentService";
            UUID contextUuid = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            AccessType accessType = AccessType.QUERY;
            
            ContextService cs = createTestContextService(contextServiceUuid, contextUuid, serviceName);
            
            when(contextServiceRepository.findByContextUuidAndServiceName(contextUuid, serviceName))
                    .thenReturn(Optional.of(cs));
            when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, accessType))
                    .thenReturn(Optional.empty());
            
            boolean result = aclService.hasPermission(environmentId, serviceName, contextUuid, accessType);
            
            assertThat(result).isFalse();
        }
        
        @Test
        void usesContextServiceRepository_toResolveServiceNameAndContextUuid() {
            UUID environmentId = UUID.randomUUID();
            String serviceName = "SchoolService";
            UUID contextUuid = UUID.randomUUID();
            UUID contextServiceUuid = UUID.randomUUID();
            AccessType accessType = AccessType.SUBSCRIBE;
            
            ContextService cs = createTestContextService(contextServiceUuid, contextUuid, serviceName);
            
            AclEntry approvedEntry = new AclEntry();
            approvedEntry.setAccessLevel(AccessLevel.APPROVED);
            
            when(contextServiceRepository.findByContextUuidAndServiceName(contextUuid, serviceName))
                    .thenReturn(Optional.of(cs));
            when(aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, accessType))
                    .thenReturn(Optional.of(approvedEntry));
            
            aclService.hasPermission(environmentId, serviceName, contextUuid, accessType);
            
            verify(contextServiceRepository).findByContextUuidAndServiceName(contextUuid, serviceName);
            verify(aclEntryRepository).findByEnvironmentIdAndContextServiceUuidAndAccessType(
                    environmentId, contextServiceUuid, accessType);
        }
    }
    
    // --- Helper methods ---
    
    private void mockResolveContext(UUID solutionUuid, UUID zoneUuid, UUID contextUuid) {
        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId(SOLUTION_ID);

        Domain domain = createTestDomain();

        Zone zone = createTestZone(zoneUuid, solutionUuid, ZONE_ID);

        Context context = createTestContext(contextUuid, zoneUuid, CONTEXT_ID);

        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, ZONE_ID)).thenReturn(Optional.of(zone));
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, CONTEXT_ID)).thenReturn(Optional.of(context));
    }

    private Domain createTestDomain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_UUID);
        domain.setDomainId(DOMAIN_ID);
        return domain;
    }
    
    private Environment createTestEnvironment(UUID environmentId) {
        Environment environment = new Environment();
        environment.setId(environmentId);
        environment.setApplicationName("Test Application");
        environment.setSessionToken(UUID.randomUUID().toString());
        environment.setSharedSecret("test-secret");
        environment.setCreatedAt(Instant.now());
        environment.setDefaultZoneId("default-zone");
        environment.setType(EnvironmentType.BROKERED);
        return environment;
    }
    
    private ContextService createTestContextService(UUID id, UUID contextUuid, String serviceName) {
        ContextService cs = new ContextService();
        cs.setId(id);
        cs.setContextUuid(contextUuid);
        cs.setServiceName(serviceName);
        cs.setServiceType("OBJECT");
        cs.setCreatedAt(Instant.now());
        return cs;
    }
    
    private Context createTestContext(UUID contextUuid, UUID zoneUuid, String contextId) {
        Context context = new Context();
        context.setId(contextUuid);
        context.setZoneUuid(zoneUuid);
        context.setContextId(contextId);
        return context;
    }
    
    private Zone createTestZone(UUID zoneUuid, UUID solutionUuid, String zoneId) {
        Zone zone = new Zone();
        zone.setId(zoneUuid);
        zone.setSolutionUuid(solutionUuid);
        zone.setZoneId(zoneId);
        return zone;
    }
}
