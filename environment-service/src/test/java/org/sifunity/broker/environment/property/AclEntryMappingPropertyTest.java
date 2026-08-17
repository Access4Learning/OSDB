// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.ContextServiceService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test: Entity-to-DTO mapping preserves zoneId/contextId.
 *
 * <p><strong>Property:</strong> For any AclEntry with a valid zone/context hierarchy,
 * mapping it to an AclEntryDTO via {@code mapToAclEntryDTO} should produce a DTO
 * whose {@code zoneId} and {@code contextId} fields match the zone/context names.</p>
 *
 * <p>Unit test — exercises the mapping through the public {@code createAclEntry}
 * method which internally calls {@code mapToAclEntryDTO}.</p>
 */
@Tag("Feature: acl-sif-compliant-api, Property: Entity-to-DTO mapping preserves zoneId/contextId")
class AclEntryMappingPropertyTest {

    /**
     * Property: Entity-to-DTO mapping preserves zoneId/contextId through createAclEntry.
     */
    @Property(tries = 10)
    void mappingPreservesZoneAndContextIds(
            @ForAll("accessTypes") AccessType accessType,
            @ForAll("accessLevels") AccessLevel accessLevel,
            @ForAll("serviceNames") String serviceName) {

        // Set up mocks
        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        ContextServiceService contextServiceService = mock(ContextServiceService.class);
        ContextServiceRepository contextServiceRepository = mock(ContextServiceRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);

        AclService aclService = new AclService(aclEntryRepository, environmentRepository, contextRepository,
                contextServiceService, contextServiceRepository, solutionRepository, zoneRepository, domainRepository);

        UUID environmentId = UUID.randomUUID();
        UUID contextServiceUuid = UUID.randomUUID();
        UUID domainUuid = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        UUID zoneUuid = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();
        String domainId = "test-domain";
        String solutionId = "test-solution";
        String zoneId = "TEST_ZONE";
        String contextId = "TEST_CONTEXT";

        Environment env = new Environment();
        env.setId(environmentId);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(env));

        // Mock resolution chain
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
        zone.setSolutionUuid(solutionUuid);
        zone.setZoneId(zoneId);
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId)).thenReturn(Optional.of(zone));

        Context context = new Context();
        context.setId(contextUuid);
        context.setZoneUuid(zoneUuid);
        context.setContextId(contextId);
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, contextId)).thenReturn(Optional.of(context));

        // Resolve context service
        when(contextServiceService.resolveOrCreate(any(), any(), any())).thenReturn(contextServiceUuid);

        // No duplicate
        when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessType(
                any(), any(), any())).thenReturn(false);

        // Single-provider check (for PROVIDE type)
        if (accessType == AccessType.PROVIDE) {
            when(aclEntryRepository.existsByContextServiceUuidAndAccessType(
                    contextServiceUuid, AccessType.PROVIDE)).thenReturn(false);
        }

        // Context service lookup for DTO mapping
        ContextService cs = new ContextService();
        cs.setId(contextServiceUuid);
        cs.setContextUuid(contextUuid);
        cs.setServiceName(serviceName);
        cs.setServiceType("OBJECT");
        when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(cs));

        // DTO mapping lookups
        when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
        when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));

        // Save returns entity with ID set
        when(aclEntryRepository.save(any())).thenAnswer(inv -> {
            AclEntry entry = inv.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });

        AclEntryCreateRequest request = new AclEntryCreateRequest(
                environmentId, serviceName, zoneId, contextId, accessType, accessLevel);

        // Act: create triggers mapToAclEntryDTO internally (admin path)
        AclEntryDTO dto = aclService.createAclEntry(domainId, solutionId, request, env, true);

        // Assert: zoneId/contextId are preserved through entity→DTO mapping
        assertThat(dto.zoneId())
                .as("mapToAclEntryDTO must produce zoneId=%s", zoneId)
                .isEqualTo(zoneId);
        assertThat(dto.contextId())
                .as("mapToAclEntryDTO must produce contextId=%s", contextId)
                .isEqualTo(contextId);

        // Also verify other fields are mapped correctly
        assertThat(dto.environmentId()).isEqualTo(environmentId);
        assertThat(dto.serviceName()).isEqualTo(serviceName);
        assertThat(dto.accessType()).isEqualTo(accessType);
        assertThat(dto.accessLevel()).isEqualTo(accessLevel);
        assertThat(dto.id()).isNotNull();
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }

    @Provide
    Arbitrary<AccessLevel> accessLevels() {
        return Arbitraries.of(AccessLevel.values());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
    }
}
