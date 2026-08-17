// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.environment.config.ProvisionRequestConfig;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.ProvisionRequestService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property 5: Resolution creates correct ACL entries.
 *
 * <p>For any resolved provisionRequest, for each right assertion, there SHALL exist a
 * corresponding ACL entry with the correct environment ID, service name, context UUID,
 * access type matching the right type, and access level matching the resolved right value.</p>
 *
 * <p><strong>Validates: Requirements 4.2, 5.3, 5.4, 6.2</strong></p>
 */
@Tag("Feature: provision-requests, Property 5: Resolution creates correct ACL entries")
class ResolutionAclCreationPropertyTest {

    private static final String[] RIGHT_TYPES = {"QUERY", "CREATE", "UPDATE", "DELETE", "SUBSCRIBE", "PROVIDE"};
    private static final String[] SERVICE_TYPES = {"OBJECT", "UTILITY", "FUNCTIONAL"};
    private static final String[] RIGHT_VALUES = {"APPROVED", "REJECTED"};

    private static final ObjectMapper REAL_OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Property 5: Resolution creates correct ACL entries.
     *
     * <p><strong>Validates: Requirements 4.2, 5.3, 5.4, 6.2</strong></p>
     */
    @Property(tries = 20)
    void resolutionCreatesCorrectAclEntries(
            @ForAll("resolvedPayloads") ResolvedTestData testData) {

        // --- Set up mocks ---
        ProvisionRequestRepository provisionRequestRepo = mock(ProvisionRequestRepository.class);
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ProvisionRequestConfig config = new ProvisionRequestConfig(false);

        UUID provisionRequestId = UUID.randomUUID();
        UUID environmentId = testData.environmentId;

        // Mock admin check
        Environment adminRequester = mock(Environment.class);
        when(adminRequester.getId()).thenReturn(UUID.randomUUID());
        when(authorizationService.isAdmin(adminRequester)).thenReturn(true);

        // Mock provision request
        ProvisionRequest pr = new ProvisionRequest();
        pr.setId(provisionRequestId);
        pr.setEnvironmentId(environmentId);
        pr.setProvisionedZonesPayload("{}");
        pr.setCreatedAt(Instant.now());
        when(provisionRequestRepo.findById(provisionRequestId)).thenReturn(Optional.of(pr));
        when(provisionRequestRepo.save(any(ProvisionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock environment
        Environment environment = mock(Environment.class);
        when(environment.getId()).thenReturn(environmentId);
        UUID solutionUuid = UUID.randomUUID();
        when(environment.getSolutionUuid()).thenReturn(solutionUuid);
        when(environmentRepo.findById(environmentId)).thenReturn(Optional.of(environment));

        // Mock zones and contexts
        for (var zoneMapping : testData.zoneMappings.entrySet()) {
            String zoneId = zoneMapping.getKey();
            UUID zoneUuid = zoneMapping.getValue();
            Zone zone = new Zone();
            zone.setId(zoneUuid);
            zone.setZoneId(zoneId);
            when(zoneRepo.findBySolutionUuidAndZoneId(eq(solutionUuid), eq(zoneId))).thenReturn(Optional.of(zone));

            for (var ctxMapping : testData.contextMappings.getOrDefault(zoneId, Map.of()).entrySet()) {
                String contextId = ctxMapping.getKey();
                UUID contextUuid = ctxMapping.getValue();
                Context ctx = new Context();
                ctx.setId(contextUuid);
                ctx.setContextId(contextId);
                ctx.setZoneUuid(zoneUuid);
                when(contextRepo.findByZoneUuidAndContextId(zoneUuid, contextId))
                        .thenReturn(Optional.of(ctx));
            }
        }

        // Mock ACL entry repo — all new entries (no existing)
        ContextServiceRepository contextServiceRepo = mock(ContextServiceRepository.class);
        ContextService mockCs = new ContextService();
        mockCs.setId(UUID.randomUUID());
        mockCs.setServiceType("OBJECT");
        when(contextServiceRepo.findByContextUuidAndServiceName(any(), any())).thenReturn(Optional.empty());
        when(contextServiceRepo.save(any())).thenAnswer(inv -> {
            var cs = inv.getArgument(0);
            if (cs instanceof ContextService csEntity && csEntity.getId() == null) {
                csEntity.setId(UUID.randomUUID());
            }
            return cs;
        });
        when(aclEntryRepo.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                any(), any(), any())).thenReturn(Optional.empty());
        when(aclEntryRepo.save(any(AclEntry.class))).thenAnswer(inv -> {
            AclEntry e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        ProvisionRequestService service = new ProvisionRequestService(
                provisionRequestRepo, aclEntryRepo, environmentRepo,
                zoneRepo, contextRepo, contextServiceRepo, config, authorizationService, REAL_OBJECT_MAPPER);

        // --- Act ---
        service.resolveProvisionRequest(provisionRequestId, testData.resolveDTO, adminRequester);

        // --- Assert ---
        ArgumentCaptor<AclEntry> captor = ArgumentCaptor.forClass(AclEntry.class);
        verify(aclEntryRepo, atLeast(1)).save(captor.capture());

        List<AclEntry> savedEntries = captor.getAllValues();

        // Count expected rights
        int expectedRightCount = 0;
        for (ProvisionedZoneDTO zone : testData.resolveDTO.provisionedZones().provisionedZone()) {
            for (ProvisionedServiceDTO svc : zone.services().service()) {
                expectedRightCount += svc.rights().right().size();
            }
        }

        assertThat(savedEntries).hasSize(expectedRightCount);

        // Verify each saved entry has correct accessType and accessLevel
        for (AclEntry entry : savedEntries) {
            assertThat(entry.getAccessType()).isNotNull();
            assertThat(entry.getAccessLevel()).isNotNull();
            assertThat(entry.getAccessLevel())
                    .isIn(AccessLevel.APPROVED, AccessLevel.REJECTED);
        }
    }

    // ========== Test Data ==========

    record ResolvedTestData(
            UUID environmentId,
            ProvisionRequestResolveDTO resolveDTO,
            Map<String, UUID> zoneMappings,
            Map<String, Map<String, UUID>> contextMappings
    ) {}

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<ResolvedTestData> resolvedPayloads() {
        return Arbitraries.integers().between(1, 3).flatMap(zoneCount ->
                Arbitraries.integers().between(1, 3).flatMap(serviceCount ->
                        Arbitraries.integers().between(1, 4).flatMap(rightCount ->
                                buildTestData(zoneCount, serviceCount, rightCount)
                        )
                )
        );
    }

    private Arbitrary<ResolvedTestData> buildTestData(int zoneCount, int serviceCount, int rightCount) {
        return Arbitraries.create(() -> {
            UUID environmentId = UUID.randomUUID();
            Map<String, UUID> zoneMappings = new LinkedHashMap<>();
            Map<String, Map<String, UUID>> contextMappings = new LinkedHashMap<>();
            List<ProvisionedZoneDTO> zones = new ArrayList<>();

            for (int z = 0; z < zoneCount; z++) {
                String zoneId = "zone" + z;
                UUID zoneUuid = UUID.randomUUID();
                zoneMappings.put(zoneId, zoneUuid);

                String contextId = "ctx" + z;
                UUID contextUuid = UUID.randomUUID();
                contextMappings.computeIfAbsent(zoneId, k -> new LinkedHashMap<>())
                        .put(contextId, contextUuid);

                List<ProvisionedServiceDTO> services = new ArrayList<>();
                for (int s = 0; s < serviceCount; s++) {
                    List<RightDTO> rights = new ArrayList<>();
                    for (int r = 0; r < rightCount; r++) {
                        String rightType = RIGHT_TYPES[r % RIGHT_TYPES.length];
                        String rightValue = RIGHT_VALUES[(z + s + r) % RIGHT_VALUES.length];
                        rights.add(new RightDTO(rightType, rightValue));
                    }
                    String svcType = SERVICE_TYPES[s % SERVICE_TYPES.length];
                    services.add(new ProvisionedServiceDTO(svcType, "svc" + s, contextId,
                            new ProvisionedServiceRightsDTO(rights)));
                }
                zones.add(new ProvisionedZoneDTO(zoneId,
                        new ProvisionedZoneServicesDTO(services)));
            }

            ProvisionRequestResolveDTO resolveDTO = new ProvisionRequestResolveDTO(
                    new ProvisionedZonesDTO(zones));

            return new ResolvedTestData(environmentId, resolveDTO, zoneMappings, contextMappings);
        });
    }
}
