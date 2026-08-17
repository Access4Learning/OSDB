// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.dto.AclEntryDTO;
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
 * Property 1: Resolution Round-Trip.
 *
 * <p>For any valid {@code zoneId} and {@code contextId} pair that exists within the
 * solution's zone/context hierarchy, creating an ACL entry with those fields SHALL
 * produce a response DTO containing the same {@code zoneId} and {@code contextId} values.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.4, 2.1, 2.3, 3.1, 3.2, 3.4</strong></p>
 */
@Tag("Feature: acl-sif-compliant-api, Property 1: Resolution Round-Trip")
class AclResolutionRoundTripPropertyTest {

    /**
     * Property 1: Resolution Round-Trip.
     *
     * <p><strong>Validates: Requirements 1.1, 1.2, 1.4, 2.1, 2.3, 3.1, 3.2, 3.4</strong></p>
     */
    @Property(tries = 20)
    void resolutionRoundTripPreservesZoneAndContextIds(
            @ForAll("validZoneContextHierarchies") RoundTripTestData testData) {

        // --- Set up mocks ---
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        ContextServiceService contextServiceService = mock(ContextServiceService.class);
        ContextServiceRepository contextServiceRepo = mock(ContextServiceRepository.class);
        SolutionRepository solutionRepo = mock(SolutionRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        DomainRepository domainRepo = mock(DomainRepository.class);

        AclService aclService = new AclService(
                aclEntryRepo, environmentRepo, contextRepo,
                contextServiceService, contextServiceRepo,
                solutionRepo, zoneRepo, domainRepo);

        // --- Mock resolution chain: domainId → Domain → Solution → Zone → Context ---
        String domainId = "test-domain";
        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(domainId);
        when(domainRepo.findByDomainId(domainId))
                .thenReturn(Optional.of(domain));

        Solution solution = new Solution();
        solution.setId(testData.solutionUuid);
        solution.setSolutionId(testData.solutionId);
        solution.setDomainUuid(domainUuid);
        when(solutionRepo.findByDomainUuidAndSolutionId(domainUuid, testData.solutionId))
                .thenReturn(Optional.of(solution));

        Zone zone = new Zone();
        zone.setId(testData.zoneUuid);
        zone.setZoneId(testData.zoneId);
        zone.setSolutionUuid(testData.solutionUuid);
        when(zoneRepo.findBySolutionUuidAndZoneId(testData.solutionUuid, testData.zoneId))
                .thenReturn(Optional.of(zone));

        Context context = new Context();
        context.setId(testData.contextUuid);
        context.setContextId(testData.contextId);
        context.setZoneUuid(testData.zoneUuid);
        when(contextRepo.findByZoneUuidAndContextId(testData.zoneUuid, testData.contextId))
                .thenReturn(Optional.of(context));

        // --- Mock environment lookup ---
        Environment environment = mock(Environment.class);
        when(environment.getId()).thenReturn(testData.environmentId);
        when(environmentRepo.findById(testData.environmentId))
                .thenReturn(Optional.of(environment));

        // --- Mock contextServiceService.resolveOrCreate ---
        UUID contextServiceUuid = UUID.randomUUID();
        when(contextServiceService.resolveOrCreate(eq(testData.contextUuid), eq(testData.serviceName), any()))
                .thenReturn(contextServiceUuid);

        // --- Mock no duplicate and no existing provider ---
        when(aclEntryRepo.existsByEnvironmentIdAndContextServiceUuidAndAccessType(
                any(), any(), any())).thenReturn(false);
        when(aclEntryRepo.existsByContextServiceUuidAndAccessType(
                any(), eq(AccessType.PROVIDE))).thenReturn(false);

        // --- Mock save: assign ID and return ---
        when(aclEntryRepo.save(any(AclEntry.class))).thenAnswer(inv -> {
            AclEntry entry = inv.getArgument(0);
            if (entry.getId() == null) entry.setId(UUID.randomUUID());
            return entry;
        });

        // --- Mock contextServiceRepo.findById for DTO mapping ---
        ContextService contextService = new ContextService();
        contextService.setId(contextServiceUuid);
        contextService.setContextUuid(testData.contextUuid);
        contextService.setServiceName(testData.serviceName);
        contextService.setServiceType("OBJECT");
        when(contextServiceRepo.findById(contextServiceUuid))
                .thenReturn(Optional.of(contextService));

        // --- Mock reverse resolution in mapToAclEntryDTO ---
        when(contextRepo.findById(testData.contextUuid))
                .thenReturn(Optional.of(context));
        when(zoneRepo.findById(testData.zoneUuid))
                .thenReturn(Optional.of(zone));

        // --- Build request ---
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                testData.environmentId,
                testData.serviceName,
                testData.zoneId,
                testData.contextId,
                testData.accessType,
                testData.accessLevel
        );

        // --- Act (admin path for simplicity) ---
        AclEntryDTO result = aclService.createAclEntry(
                domainId, testData.solutionId, request, environment, true);

        // --- Assert round-trip: response DTO has same zoneId and contextId as input ---
        assertThat(result.zoneId()).isEqualTo(testData.zoneId);
        assertThat(result.contextId()).isEqualTo(testData.contextId);
        assertThat(result.serviceName()).isEqualTo(testData.serviceName);
        assertThat(result.environmentId()).isEqualTo(testData.environmentId);
        assertThat(result.accessType()).isEqualTo(testData.accessType);
        assertThat(result.accessLevel()).isEqualTo(testData.accessLevel);
    }

    // ========== Test Data ==========

    record RoundTripTestData(
            String solutionId,
            UUID solutionUuid,
            String zoneId,
            UUID zoneUuid,
            String contextId,
            UUID contextUuid,
            UUID environmentId,
            String serviceName,
            AccessType accessType,
            AccessLevel accessLevel
    ) {}

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<RoundTripTestData> validZoneContextHierarchies() {
        Arbitrary<String> alphanumericIds = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1).ofMaxLength(20)
                .filter(s -> !s.isBlank() && Character.isLetterOrDigit(s.charAt(0)));

        Arbitrary<String> serviceNames = Arbitraries.strings()
                .alpha()
                .ofMinLength(1).ofMaxLength(20)
                .filter(s -> !s.isBlank());

        Arbitrary<AccessType> accessTypes = Arbitraries.of(AccessType.values());
        Arbitrary<AccessLevel> accessLevels = Arbitraries.of(AccessLevel.values());

        return Combinators.combine(
                alphanumericIds,  // solutionId
                alphanumericIds,  // zoneId
                alphanumericIds,  // contextId
                serviceNames,     // serviceName
                accessTypes,
                accessLevels
        ).as((solutionId, zoneId, contextId, serviceName, accessType, accessLevel) ->
                new RoundTripTestData(
                        solutionId,
                        UUID.randomUUID(),
                        zoneId,
                        UUID.randomUUID(),
                        contextId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        serviceName,
                        accessType,
                        accessLevel
                )
        );
    }
}
