// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.exception.DuplicateAclEntryException;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.AclService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test: Duplicate ACL entry detection with contextUuid.
 *
 * <p><strong>Property 4:</strong> For any duplicate (environmentId, serviceName,
 * contextUuid, accessType), the second creation attempt should be rejected
 * with 409 Conflict.</p>
 *
 * <p><strong>Validates: Requirements 5.1</strong></p>
 *
 * <p>Unit test with mocked repositories — no Spring context needed.</p>
 */
@Tag("Feature: acl-context-fk-migration, Property 4: Duplicate ACL entry detection with contextUuid")
class AclEntryDuplicateDetectionPropertyTest {

    /**
     * Property 4: Duplicate ACL entry detection.
     *
     * <p>For any valid ACL entry creation request, if an ACL entry already exists
     * with the same (environmentId, serviceName, contextUuid, accessType), the
     * second creation attempt should throw {@link DuplicateAclEntryException}
     * (HTTP 409 Conflict).</p>
     *
     * <p><strong>Validates: Requirements 5.1</strong></p>
     */
    @Property(tries = 10)
    void duplicateAclEntryIsRejectedWith409(
            @ForAll("contextUuids") UUID contextUuid,
            @ForAll("accessTypes") AccessType accessType,
            @ForAll("accessLevels") AccessLevel accessLevel,
            @ForAll("serviceNames") String serviceName) {

        // Set up mocks
        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        org.sifunity.broker.environment.service.ContextServiceService contextServiceService = mock(org.sifunity.broker.environment.service.ContextServiceService.class);
        org.sifunity.broker.environment.repository.ContextServiceRepository contextServiceRepository = mock(org.sifunity.broker.environment.repository.ContextServiceRepository.class);
        org.sifunity.broker.environment.repository.SolutionRepository solutionRepository = mock(org.sifunity.broker.environment.repository.SolutionRepository.class);
        org.sifunity.broker.environment.repository.ZoneRepository zoneRepository = mock(org.sifunity.broker.environment.repository.ZoneRepository.class);
        org.sifunity.broker.environment.repository.DomainRepository domainRepository = mock(org.sifunity.broker.environment.repository.DomainRepository.class);

        AclService aclService = new AclService(aclEntryRepository, environmentRepository, contextRepository, contextServiceService, contextServiceRepository, solutionRepository, zoneRepository, domainRepository);

        UUID environmentId = UUID.randomUUID();
        UUID domainUuid = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        String domainId = "test-domain";
        String solutionId = "test-solution";
        String zoneId = "DEFAULT";
        String contextId = "DEFAULT";
        UUID zoneUuid = UUID.randomUUID();

        Environment env = new Environment();
        env.setId(environmentId);
        env.setSolutionUuid(solutionUuid);
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(env));

        // Mock domain resolution for domain-scoped ACL resolution
        org.sifunity.broker.environment.domain.Domain domain = new org.sifunity.broker.environment.domain.Domain();
        domain.setId(domainUuid);
        domain.setDomainId(domainId);
        when(domainRepository.findById(domainUuid)).thenReturn(Optional.of(domain));
        when(domainRepository.findByDomainId(domainId)).thenReturn(Optional.of(domain));

        // Mock solution resolution for the backward-compatible overload
        org.sifunity.broker.environment.domain.Solution solution = new org.sifunity.broker.environment.domain.Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId(solutionId);
        solution.setDomainUuid(domainUuid);
        when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)).thenReturn(Optional.of(solution));

        // Mock zone resolution
        org.sifunity.broker.environment.domain.Zone zone = new org.sifunity.broker.environment.domain.Zone();
        zone.setId(zoneUuid);
        zone.setZoneId(zoneId);
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId)).thenReturn(Optional.of(zone));

        // Mock context resolution
        UUID resolvedContextUuid = contextUuid != null ? contextUuid : UUID.randomUUID();
        org.sifunity.broker.environment.domain.Context context = new org.sifunity.broker.environment.domain.Context();
        context.setId(resolvedContextUuid);
        context.setContextId(contextId);
        context.setZoneUuid(zoneUuid);
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, contextId)).thenReturn(Optional.of(context));

        // Stub contextServiceService.resolveOrCreate to return a valid UUID
        UUID contextServiceUuid = UUID.randomUUID();
        when(contextServiceService.resolveOrCreate(resolvedContextUuid, serviceName, "OBJECT")).thenReturn(contextServiceUuid);

        // Stub contextServiceRepository.findById for DTO mapping
        org.sifunity.broker.environment.domain.ContextService cs = new org.sifunity.broker.environment.domain.ContextService();
        cs.setId(contextServiceUuid);
        cs.setContextUuid(resolvedContextUuid);
        cs.setServiceName(serviceName);
        when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(cs));

        // Mock context/zone lookup for mapToAclEntryDTO
        when(contextRepository.findById(resolvedContextUuid)).thenReturn(Optional.of(context));
        when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));

        // First creation succeeds, second detects duplicate
        when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessType(environmentId, contextServiceUuid, accessType))
                .thenReturn(false)   // first call: no duplicate
                .thenReturn(true);   // second call: duplicate exists

        // No existing provider for single-provider constraint
        when(aclEntryRepository.existsByContextServiceUuidAndAccessType(any(), any())).thenReturn(false);

        when(aclEntryRepository.save(any())).thenAnswer(inv -> {
            AclEntry entry = inv.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });

        AclEntryCreateRequest request = new AclEntryCreateRequest(
                environmentId, serviceName, zoneId, contextId, accessType, accessLevel);

        // First creation should succeed
        var firstResult = aclService.createAclEntry(request);
        assertThat(firstResult).isNotNull();

        // Second creation with same key should throw DuplicateAclEntryException (409)
        assertThatThrownBy(() -> aclService.createAclEntry(request))
                .isInstanceOf(DuplicateAclEntryException.class);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UUID> contextUuids() {
        return Arbitraries.oneOf(
                Arbitraries.just((UUID) null),
                Arbitraries.create(UUID::randomUUID));
    }

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
