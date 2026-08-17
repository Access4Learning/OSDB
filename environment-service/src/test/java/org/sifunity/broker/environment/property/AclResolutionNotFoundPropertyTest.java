// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.ContextServiceService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 2: Invalid Zone or Context Produces Not-Found Error.
 *
 * <p>For any zoneId that does not exist in the solution's zones, or for any contextId
 * that does not exist in the resolved zone's contexts, the service SHALL throw an
 * exception that maps to HTTP 404, and no ACL entry SHALL be persisted.</p>
 *
 * <p><strong>Validates: Requirements 2.2, 2.4</strong></p>
 */
@Tag("Feature: acl-sif-compliant-api, Property 2: Invalid Zone or Context Produces Not-Found Error")
class AclResolutionNotFoundPropertyTest {

    private static final String DOMAIN_ID = "test-domain";
    private static final String SOLUTION_ID = "test-solution";
    private static final AccessType[] ACCESS_TYPES = AccessType.values();
    private static final AccessLevel[] ACCESS_LEVELS = AccessLevel.values();

    /**
     * Sub-property 1: Zone not found — solution exists, but zone lookup returns empty → ACL_006 thrown.
     *
     * <p><strong>Validates: Requirements 2.2</strong></p>
     */
    @Property(tries = 20)
    void zoneNotFoundThrows404AndNoPersistence(
            @ForAll("nonExistentZoneIds") String nonExistentZoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("accessTypes") AccessType accessType,
            @ForAll("accessLevels") AccessLevel accessLevel) {

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

        // Domain exists
        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(DOMAIN_ID);
        when(domainRepo.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));

        // Solution exists
        Solution solution = new Solution();
        solution.setId(UUID.randomUUID());
        solution.setSolutionId(SOLUTION_ID);
        solution.setDomainUuid(domainUuid);
        when(solutionRepo.findByDomainUuidAndSolutionId(domainUuid, SOLUTION_ID)).thenReturn(Optional.of(solution));

        // Zone NOT found
        when(zoneRepo.findBySolutionUuidAndZoneId(solution.getId(), nonExistentZoneId))
                .thenReturn(Optional.empty());

        // Build request
        UUID environmentId = UUID.randomUUID();
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                environmentId, serviceName, nonExistentZoneId, contextId, accessType, accessLevel);

        Environment requester = mock(Environment.class);
        when(requester.getId()).thenReturn(environmentId);

        // --- Act & Assert ---
        assertThatThrownBy(() -> aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, requester, true))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(thrown -> {
                    SifBrokerException ex = (SifBrokerException) thrown;
                    assertThat(ex.getErrorCode()).isEqualTo("ACL_006");
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        // No ACL entry persisted
        verify(aclEntryRepo, never()).save(any());
    }

    /**
     * Sub-property 2: Context not found — solution + zone exist, but context lookup returns empty → ACL_007 thrown.
     *
     * <p><strong>Validates: Requirements 2.4</strong></p>
     */
    @Property(tries = 20)
    void contextNotFoundThrows404AndNoPersistence(
            @ForAll("zoneIds") String zoneId,
            @ForAll("nonExistentContextIds") String nonExistentContextId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("accessTypes") AccessType accessType,
            @ForAll("accessLevels") AccessLevel accessLevel) {

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

        // Domain exists
        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(DOMAIN_ID);
        when(domainRepo.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));

        // Solution exists
        Solution solution = new Solution();
        solution.setId(UUID.randomUUID());
        solution.setSolutionId(SOLUTION_ID);
        solution.setDomainUuid(domainUuid);
        when(solutionRepo.findByDomainUuidAndSolutionId(domainUuid, SOLUTION_ID)).thenReturn(Optional.of(solution));

        // Zone exists
        Zone zone = new Zone();
        zone.setId(UUID.randomUUID());
        zone.setZoneId(zoneId);
        zone.setSolutionUuid(solution.getId());
        when(zoneRepo.findBySolutionUuidAndZoneId(solution.getId(), zoneId))
                .thenReturn(Optional.of(zone));

        // Context NOT found
        when(contextRepo.findByZoneUuidAndContextId(zone.getId(), nonExistentContextId))
                .thenReturn(Optional.empty());

        // Build request
        UUID environmentId = UUID.randomUUID();
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                environmentId, serviceName, zoneId, nonExistentContextId, accessType, accessLevel);

        Environment requester = mock(Environment.class);
        when(requester.getId()).thenReturn(environmentId);

        // --- Act & Assert ---
        assertThatThrownBy(() -> aclService.createAclEntry(DOMAIN_ID, SOLUTION_ID, request, requester, true))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(thrown -> {
                    SifBrokerException ex = (SifBrokerException) thrown;
                    assertThat(ex.getErrorCode()).isEqualTo("ACL_007");
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        // No ACL entry persisted
        verify(aclEntryRepo, never()).save(any());
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> nonExistentZoneIds() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(s -> "nonexistent-zone-" + s);
    }

    @Provide
    Arbitrary<String> nonExistentContextIds() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(s -> "nonexistent-ctx-" + s);
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> "zone-" + s);
    }

    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> "ctx-" + s);
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of("StudentPersonals", "SchoolInfos", "StaffPersonals",
                "TeachingGroups", "TimeTableSubjects", "RoomInfos");
    }

    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(ACCESS_TYPES);
    }

    @Provide
    Arbitrary<AccessLevel> accessLevels() {
        return Arbitraries.of(ACCESS_LEVELS);
    }
}
