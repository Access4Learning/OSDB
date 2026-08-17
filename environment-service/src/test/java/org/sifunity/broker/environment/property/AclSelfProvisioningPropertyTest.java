// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.ContextServiceService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property 3: Non-Admin Self-Provisioning Enforcement.
 *
 * <p>For any ACL creation request made by a non-admin environment, the persisted ACL entry
 * SHALL have {@code environmentId} equal to the requester's own environment ID and
 * {@code accessLevel} equal to PENDING, regardless of the values provided in the request body.</p>
 *
 * <p><strong>Validates: Requirements 4.2, 4.3</strong></p>
 */
@Tag("Feature: acl-sif-compliant-api, Property 3: Non-Admin Self-Provisioning Enforcement")
class AclSelfProvisioningPropertyTest {

    private static final AccessLevel[] NON_PENDING_LEVELS = {
            AccessLevel.APPROVED, AccessLevel.REJECTED, AccessLevel.SUPPORTED,
            AccessLevel.UNSUPPORTED, AccessLevel.REQUESTED
    };

    /**
     * Property 3: Non-Admin Self-Provisioning Enforcement.
     *
     * <p><strong>Validates: Requirements 4.2, 4.3</strong></p>
     */
    @Property(tries = 20)
    void nonAdminRequestForcesOwnEnvironmentIdAndPendingAccessLevel(
            @ForAll("nonAdminRequests") NonAdminTestData testData) {

        // --- Set up mocks ---
        SolutionRepository solutionRepo = mock(SolutionRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        ContextServiceService contextServiceService = mock(ContextServiceService.class);
        ContextServiceRepository contextServiceRepo = mock(ContextServiceRepository.class);
        DomainRepository domainRepo = mock(DomainRepository.class);

        // Mock domain resolution
        String domainId = "test-domain";
        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(domainId);
        when(domainRepo.findByDomainId(domainId)).thenReturn(Optional.of(domain));

        // Mock solution resolution
        Solution solution = new Solution();
        solution.setId(testData.solutionUuid);
        solution.setDomainUuid(domainUuid);
        when(solutionRepo.findByDomainUuidAndSolutionId(domainUuid, testData.solutionId)).thenReturn(Optional.of(solution));

        // Mock zone resolution
        Zone zone = new Zone();
        zone.setId(testData.zoneUuid);
        zone.setZoneId(testData.zoneId);
        when(zoneRepo.findBySolutionUuidAndZoneId(testData.solutionUuid, testData.zoneId))
                .thenReturn(Optional.of(zone));

        // Mock context resolution
        Context context = new Context();
        context.setId(testData.contextUuid);
        context.setContextId(testData.contextId);
        context.setZoneUuid(testData.zoneUuid);
        when(contextRepo.findByZoneUuidAndContextId(testData.zoneUuid, testData.contextId))
                .thenReturn(Optional.of(context));

        // Mock requester environment
        Environment requester = new Environment();
        requester.setId(testData.requesterId);
        requester.setApplicationName("requester-app");

        // Mock environment lookup for effective environment (requester's own ID)
        when(environmentRepo.findById(testData.requesterId)).thenReturn(Optional.of(requester));

        // Mock context service resolution
        UUID contextServiceUuid = UUID.randomUUID();
        when(contextServiceService.resolveOrCreate(eq(testData.contextUuid), eq(testData.serviceName), any()))
                .thenReturn(contextServiceUuid);

        // Mock no duplicate exists
        when(aclEntryRepo.existsByEnvironmentIdAndContextServiceUuidAndAccessType(any(), any(), any()))
                .thenReturn(false);
        // Mock no existing provider
        when(aclEntryRepo.existsByContextServiceUuidAndAccessType(any(), any()))
                .thenReturn(false);

        // Mock save — capture the entity
        when(aclEntryRepo.save(any(AclEntry.class))).thenAnswer(inv -> {
            AclEntry e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        // Mock context service repo for DTO mapping
        ContextService cs = new ContextService();
        cs.setId(contextServiceUuid);
        cs.setContextUuid(testData.contextUuid);
        cs.setServiceName(testData.serviceName);
        cs.setServiceType("OBJECT");
        when(contextServiceRepo.findById(contextServiceUuid)).thenReturn(Optional.of(cs));

        // Mock context/zone lookups for DTO mapping
        when(contextRepo.findById(testData.contextUuid)).thenReturn(Optional.of(context));
        when(zoneRepo.findById(testData.zoneUuid)).thenReturn(Optional.of(zone));

        // Build the service under test
        AclService aclService = new AclService(
                aclEntryRepo, environmentRepo, contextRepo,
                contextServiceService, contextServiceRepo,
                solutionRepo, zoneRepo, domainRepo);

        // --- Act: call createAclEntry as non-admin ---
        aclService.createAclEntry(domainId, testData.solutionId, testData.request, requester, false);

        // --- Assert: capture persisted AclEntry ---
        ArgumentCaptor<AclEntry> captor = ArgumentCaptor.forClass(AclEntry.class);
        verify(aclEntryRepo).save(captor.capture());
        AclEntry saved = captor.getValue();

        // The persisted entry MUST have requester's own ID, NOT the request body's environmentId
        assertThat(saved.getEnvironment().getId())
                .as("Non-admin: environmentId must be requester's own ID, not request body's")
                .isEqualTo(testData.requesterId)
                .isNotEqualTo(testData.requestBodyEnvironmentId);

        // The persisted entry MUST have PENDING access level, NOT the request body's accessLevel
        assertThat(saved.getAccessLevel())
                .as("Non-admin: accessLevel must be PENDING, not request body's value")
                .isEqualTo(AccessLevel.PENDING);
    }

    // ========== Test Data ==========

    record NonAdminTestData(
            UUID requesterId,
            UUID requestBodyEnvironmentId,
            AccessLevel requestBodyAccessLevel,
            String solutionId,
            UUID solutionUuid,
            String zoneId,
            UUID zoneUuid,
            String contextId,
            UUID contextUuid,
            String serviceName,
            AccessType accessType,
            AclEntryCreateRequest request
    ) {}

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<NonAdminTestData> nonAdminRequests() {
        Arbitrary<AccessLevel> nonPendingLevels = Arbitraries.of(NON_PENDING_LEVELS);
        Arbitrary<AccessType> accessTypes = Arbitraries.of(AccessType.values());
        Arbitrary<String> ids = Arbitraries.integers().between(1, 9999)
                .map(String::valueOf);

        return Combinators.combine(nonPendingLevels, accessTypes, ids, ids, ids, ids)
                .as((requestBodyLevel, accessType, solSuffix, zoneSuffix, ctxSuffix, svcSuffix) -> {
                    UUID requesterId = UUID.randomUUID();
                    UUID requestBodyEnvId = UUID.randomUUID();
                    UUID solutionUuid = UUID.randomUUID();
                    UUID zoneUuid = UUID.randomUUID();
                    UUID contextUuid = UUID.randomUUID();

                    String solutionId = "solution" + solSuffix;
                    String zoneId = "zone" + zoneSuffix;
                    String contextId = "ctx" + ctxSuffix;
                    String serviceName = "Service" + svcSuffix;

                    AclEntryCreateRequest request = new AclEntryCreateRequest(
                            requestBodyEnvId, serviceName, zoneId, contextId,
                            accessType, requestBodyLevel);

                    return new NonAdminTestData(
                            requesterId, requestBodyEnvId, requestBodyLevel,
                            solutionId, solutionUuid, zoneId, zoneUuid,
                            contextId, contextUuid, serviceName, accessType, request);
                });
    }
}
