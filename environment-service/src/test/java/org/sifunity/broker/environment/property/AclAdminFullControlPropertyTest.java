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
 * Property 4: Admin Full Control.
 *
 * <p>For any ACL creation request made by an admin environment, the persisted ACL entry
 * SHALL have {@code environmentId} and {@code accessLevel} equal to the values provided
 * in the request body.</p>
 *
 * <p><strong>Validates: Requirements 4.1</strong></p>
 */
@Tag("Feature: acl-sif-compliant-api, Property 4: Admin Full Control")
class AclAdminFullControlPropertyTest {

    @Property(tries = 20)
    void adminRequestPersistsExactEnvironmentIdAndAccessLevel(
            @ForAll("arbitraryEnvironmentIds") UUID requestEnvironmentId,
            @ForAll("arbitraryAccessLevels") AccessLevel requestAccessLevel,
            @ForAll("arbitraryAccessTypes") AccessType accessType) {

        // --- Arrange mocks ---
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);
        ContextServiceService contextServiceService = mock(ContextServiceService.class);
        ContextServiceRepository contextServiceRepository = mock(ContextServiceRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);

        AclService aclService = new AclService(
                aclEntryRepository, environmentRepository, contextRepository,
                contextServiceService, contextServiceRepository,
                solutionRepository, zoneRepository, domainRepository);

        // Solution/zone/context resolution stubs
        String domainId = "test-domain";
        String solutionId = "test-solution";
        String zoneId = "DEFAULT";
        String contextId = "DEFAULT";

        UUID domainUuid = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        UUID zoneUuid = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();
        UUID contextServiceUuid = UUID.randomUUID();

        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(domainId);
        when(domainRepository.findByDomainId(domainId)).thenReturn(Optional.of(domain));

        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId(solutionId);
        solution.setDomainUuid(domainUuid);
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)).thenReturn(Optional.of(solution));

        Zone zone = new Zone();
        zone.setId(zoneUuid);
        zone.setZoneId(zoneId);
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId)).thenReturn(Optional.of(zone));

        Context context = new Context();
        context.setId(contextUuid);
        context.setContextId(contextId);
        context.setZoneUuid(zoneUuid);
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, contextId)).thenReturn(Optional.of(context));

        // Environment lookup — the environment specified in request must exist
        Environment targetEnvironment = new Environment();
        targetEnvironment.setId(requestEnvironmentId);
        when(environmentRepository.findById(requestEnvironmentId)).thenReturn(Optional.of(targetEnvironment));

        // ContextServiceService resolution
        when(contextServiceService.resolveOrCreate(eq(contextUuid), any(), any())).thenReturn(contextServiceUuid);

        // No duplicate, no existing provider
        when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessType(any(), any(), any()))
                .thenReturn(false);
        when(aclEntryRepository.existsByContextServiceUuidAndAccessType(any(), any()))
                .thenReturn(false);

        // Save returns entity with generated ID
        when(aclEntryRepository.save(any(AclEntry.class))).thenAnswer(inv -> {
            AclEntry entry = inv.getArgument(0);
            if (entry.getId() == null) entry.setId(UUID.randomUUID());
            return entry;
        });

        // ContextService lookup for DTO mapping
        ContextService cs = new ContextService();
        cs.setId(contextServiceUuid);
        cs.setContextUuid(contextUuid);
        cs.setServiceName("TestService");
        cs.setServiceType("OBJECT");
        when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(cs));

        // For mapToAclEntryDTO reverse resolution
        when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
        when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));

        // Admin requester (different from target environment)
        Environment adminRequester = new Environment();
        adminRequester.setId(UUID.randomUUID());

        // Build request with arbitrary environmentId and accessLevel
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                requestEnvironmentId, "TestService", zoneId, contextId, accessType, requestAccessLevel);

        // --- Act ---
        aclService.createAclEntry(domainId, solutionId, request, adminRequester, true);

        // --- Assert: capture persisted AclEntry ---
        ArgumentCaptor<AclEntry> captor = ArgumentCaptor.forClass(AclEntry.class);
        verify(aclEntryRepository).save(captor.capture());

        AclEntry persisted = captor.getValue();
        assertThat(persisted.getEnvironment().getId())
                .as("Persisted environmentId must equal request body value")
                .isEqualTo(requestEnvironmentId);
        assertThat(persisted.getAccessLevel())
                .as("Persisted accessLevel must equal request body value")
                .isEqualTo(requestAccessLevel);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UUID> arbitraryEnvironmentIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<AccessLevel> arbitraryAccessLevels() {
        return Arbitraries.of(AccessLevel.values());
    }

    @Provide
    Arbitrary<AccessType> arbitraryAccessTypes() {
        return Arbitraries.of(AccessType.values());
    }
}
