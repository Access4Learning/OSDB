// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.dto.AvailableProviderOptionDTO;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.ContextServiceService;
import org.sifunity.broker.environment.service.ProviderService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test: Available options equal PROVIDE ACLs minus existing registrations.
 *
 * <p><strong>Property 2:</strong> For any environment with a set of PROVIDE/APPROVED ACL entries
 * and a set of existing provider registrations, the available options endpoint SHALL return
 * exactly the context_service entries that appear in the ACL set but NOT in the registration
 * set — no more, no fewer.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.3</strong></p>
 */
@Tag("Feature: provider-registry-ui-improvements, Property 2: Available options equal PROVIDE ACLs minus existing registrations")
class AvailableProviderOptionsPropertyTest {

    /**
     * Property 2: Available options equal PROVIDE ACLs minus existing registrations.
     *
     * <p>For any environment with PROVIDE/APPROVED ACLs and existing registrations,
     * available options == ACL contextServiceUuid set minus registration contextServiceUuid set.</p>
     *
     * <p><strong>Validates: Requirements 4.1, 4.2, 4.3</strong></p>
     */
    @Property(tries = 20)
    void availableOptionsEqualsAclSetMinusRegistrationSet(
            @ForAll("aclContextServiceUuidSets") Set<UUID> aclContextServiceUuids,
            @ForAll("registeredContextServiceUuidSets") Set<UUID> registeredContextServiceUuids) {

        // Set up mocks
        ProviderRegistrationRepository providerRegistrationRepository = mock(ProviderRegistrationRepository.class);
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        ContextServiceService contextServiceService = mock(ContextServiceService.class);
        ContextServiceRepository contextServiceRepository = mock(ContextServiceRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);

        ProviderService providerService = new ProviderService(
                providerRegistrationRepository,
                environmentRepository,
                contextServiceService,
                contextServiceRepository,
                contextRepository,
                zoneRepository,
                solutionRepository,
                domainRepository,
                aclEntryRepository);

        UUID environmentId = UUID.randomUUID();

        // Build ACL entries for the environment (all PROVIDE/APPROVED)
        List<AclEntry> aclEntries = aclContextServiceUuids.stream()
                .map(csUuid -> {
                    AclEntry acl = new AclEntry();
                    acl.setId(UUID.randomUUID());
                    acl.setContextServiceUuid(csUuid);
                    acl.setAccessType(AccessType.PROVIDE);
                    acl.setAccessLevel(AccessLevel.APPROVED);
                    return acl;
                })
                .toList();

        // Build existing provider registrations (subset or overlapping with ACLs)
        List<ProviderRegistration> existingRegistrations = registeredContextServiceUuids.stream()
                .map(csUuid -> {
                    ProviderRegistration reg = new ProviderRegistration();
                    reg.setId(UUID.randomUUID());
                    reg.setContextServiceUuid(csUuid);
                    return reg;
                })
                .toList();

        // Mock repository calls
        when(aclEntryRepository.findByEnvironmentIdAndAccessTypeAndAccessLevel(
                environmentId, AccessType.PROVIDE, AccessLevel.APPROVED))
                .thenReturn(aclEntries);
        when(providerRegistrationRepository.findByEnvironmentId(environmentId))
                .thenReturn(existingRegistrations);

        // For each contextServiceUuid in the expected set difference, set up the resolution chain
        Set<UUID> expectedAvailableUuids = new HashSet<>(aclContextServiceUuids);
        expectedAvailableUuids.removeAll(registeredContextServiceUuids);

        // Set up ContextService → Context → Zone resolution for each expected UUID
        for (UUID csUuid : expectedAvailableUuids) {
            UUID contextUuid = UUID.randomUUID();
            UUID zoneUuid = UUID.randomUUID();

            ContextService cs = new ContextService();
            cs.setId(csUuid);
            cs.setContextUuid(contextUuid);
            cs.setServiceName("Service-" + csUuid.toString().substring(0, 8));
            cs.setServiceType("OBJECT");

            Context context = new Context();
            context.setId(contextUuid);
            context.setZoneUuid(zoneUuid);
            context.setContextId("ctx-" + contextUuid.toString().substring(0, 8));

            Zone zone = new Zone();
            zone.setId(zoneUuid);
            zone.setZoneId("zone-" + zoneUuid.toString().substring(0, 8));

            when(contextServiceRepository.findById(csUuid)).thenReturn(Optional.of(cs));
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
        }

        // Act
        List<AvailableProviderOptionDTO> options = providerService.getAvailableOptions(environmentId);

        // Assert: the returned contextServiceUuids match exactly the set difference
        Set<UUID> actualUuids = options.stream()
                .map(AvailableProviderOptionDTO::contextServiceUuid)
                .collect(Collectors.toSet());

        assertThat(actualUuids)
                .as("Available options should equal ACL set (%d) minus registration set (%d) = %d items",
                        aclContextServiceUuids.size(), registeredContextServiceUuids.size(), expectedAvailableUuids.size())
                .isEqualTo(expectedAvailableUuids);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Set<UUID>> aclContextServiceUuidSets() {
        return Arbitraries.integers().between(1, 8)
                .flatMap(size -> Arbitraries.just(size).map(s -> {
                    Set<UUID> uuids = new HashSet<>();
                    for (int i = 0; i < s; i++) {
                        uuids.add(UUID.randomUUID());
                    }
                    return uuids;
                }));
    }

    @Provide
    Arbitrary<Set<UUID>> registeredContextServiceUuidSets() {
        return Arbitraries.integers().between(0, 5)
                .flatMap(size -> Arbitraries.just(size).map(s -> {
                    Set<UUID> uuids = new HashSet<>();
                    for (int i = 0; i < s; i++) {
                        uuids.add(UUID.randomUUID());
                    }
                    return uuids;
                }));
    }
}
