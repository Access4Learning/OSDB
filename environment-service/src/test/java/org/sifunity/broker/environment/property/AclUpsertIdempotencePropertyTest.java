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
 * Property 6: ACL upsert idempotence — no duplicates on re-resolution.
 *
 * <p>For any environment, service, context, and access type combination where an ACL entry
 * already exists, resolving a provisionRequest containing an assertion for that same
 * combination SHALL update the existing ACL entry's access level rather than creating
 * a duplicate entry.</p>
 *
 * <p><strong>Validates: Requirements 6.3</strong></p>
 */
@Tag("Feature: provision-requests, Property 6: ACL upsert idempotence")
class AclUpsertIdempotencePropertyTest {

    private static final String[] RIGHT_TYPES = {"QUERY", "CREATE", "UPDATE", "DELETE", "SUBSCRIBE", "PROVIDE"};

    private static final ObjectMapper REAL_OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Property 6: ACL upsert idempotence — existing entries are updated, not duplicated.
     *
     * <p><strong>Validates: Requirements 6.3</strong></p>
     */
    @Property(tries = 20)
    void existingAclEntriesAreUpdatedNotDuplicated(
            @ForAll("upsertTestData") UpsertTestData testData) {

        // --- Set up mocks ---
        ProvisionRequestRepository provisionRequestRepo = mock(ProvisionRequestRepository.class);
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ProvisionRequestConfig config = new ProvisionRequestConfig(false);

        UUID provisionRequestId = UUID.randomUUID();

        // Mock admin
        Environment adminRequester = mock(Environment.class);
        when(adminRequester.getId()).thenReturn(UUID.randomUUID());
        when(authorizationService.isAdmin(adminRequester)).thenReturn(true);

        // Mock provision request
        ProvisionRequest pr = new ProvisionRequest();
        pr.setId(provisionRequestId);
        pr.setEnvironmentId(testData.environmentId);
        pr.setProvisionedZonesPayload("{}");
        pr.setCreatedAt(Instant.now());
        when(provisionRequestRepo.findById(provisionRequestId)).thenReturn(Optional.of(pr));
        when(provisionRequestRepo.save(any(ProvisionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock environment
        Environment environment = mock(Environment.class);
        when(environment.getId()).thenReturn(testData.environmentId);
        UUID solutionUuid = UUID.randomUUID();
        when(environment.getSolutionUuid()).thenReturn(solutionUuid);
        when(environmentRepo.findById(testData.environmentId)).thenReturn(Optional.of(environment));

        // Mock zone and context
        Zone zone = new Zone();
        zone.setId(testData.zoneUuid);
        zone.setZoneId(testData.zoneId);
        when(zoneRepo.findBySolutionUuidAndZoneId(eq(solutionUuid), eq(testData.zoneId))).thenReturn(Optional.of(zone));

        Context ctx = new Context();
        ctx.setId(testData.contextUuid);
        ctx.setContextId(testData.contextId);
        ctx.setZoneUuid(testData.zoneUuid);
        when(contextRepo.findByZoneUuidAndContextId(testData.zoneUuid, testData.contextId))
                .thenReturn(Optional.of(ctx));

        // Mock existing ACL entries — return pre-existing entries for each right
        ContextServiceRepository contextServiceRepo = mock(ContextServiceRepository.class);
        // All entries share the same contextServiceUuid (same service + context)
        UUID sharedContextServiceUuid = testData.existingEntries.get(0).getContextServiceUuid();
        ContextService cs = new ContextService();
        cs.setId(sharedContextServiceUuid);
        cs.setContextUuid(testData.contextUuid);
        cs.setServiceName("students");
        cs.setServiceType("OBJECT");
        when(contextServiceRepo.findByContextUuidAndServiceName(testData.contextUuid, "students"))
                .thenReturn(Optional.of(cs));

        for (AclEntry existing : testData.existingEntries) {
            when(aclEntryRepo.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                    testData.environmentId, sharedContextServiceUuid,
                    existing.getAccessType()))
                    .thenReturn(Optional.of(existing));
        }
        when(aclEntryRepo.save(any(AclEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        ProvisionRequestService service = new ProvisionRequestService(
                provisionRequestRepo, aclEntryRepo, environmentRepo,
                zoneRepo, contextRepo, contextServiceRepo, config, authorizationService, REAL_OBJECT_MAPPER);

        // --- Act ---
        service.resolveProvisionRequest(provisionRequestId, testData.resolveDTO, adminRequester);

        // --- Assert ---
        // Verify save was called on the EXISTING entries (same object references)
        ArgumentCaptor<AclEntry> captor = ArgumentCaptor.forClass(AclEntry.class);
        verify(aclEntryRepo, times(testData.existingEntries.size())).save(captor.capture());

        List<AclEntry> savedEntries = captor.getAllValues();

        // Each saved entry should be one of the pre-existing entries (same UUID)
        for (AclEntry saved : savedEntries) {
            assertThat(saved.getId())
                    .as("Saved entry should be an existing entry (updated), not a new one")
                    .isNotNull();

            boolean matchesExisting = testData.existingEntries.stream()
                    .anyMatch(e -> e.getId().equals(saved.getId()));
            assertThat(matchesExisting)
                    .as("Saved entry ID %s should match one of the pre-existing entries", saved.getId())
                    .isTrue();
        }
    }

    // ========== Test Data ==========

    record UpsertTestData(
            UUID environmentId,
            String zoneId,
            UUID zoneUuid,
            String contextId,
            UUID contextUuid,
            ProvisionRequestResolveDTO resolveDTO,
            List<AclEntry> existingEntries
    ) {}

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UpsertTestData> upsertTestData() {
        return Arbitraries.integers().between(1, 4).flatMap(rightCount ->
                Arbitraries.of("APPROVED", "REJECTED").list().ofSize(rightCount).map(newValues -> {
                    UUID environmentId = UUID.randomUUID();
                    String zoneId = "testZone";
                    UUID zoneUuid = UUID.randomUUID();
                    String contextId = "testCtx";
                    UUID contextUuid = UUID.randomUUID();
                    String serviceName = "students";

                    // All entries share the same contextServiceUuid since they map to the same
                    // (contextUuid, serviceName) pair in context_services
                    UUID contextServiceUuid = UUID.randomUUID();

                    List<RightDTO> rights = new ArrayList<>();
                    List<AclEntry> existingEntries = new ArrayList<>();

                    for (int i = 0; i < rightCount; i++) {
                        String rightType = RIGHT_TYPES[i % RIGHT_TYPES.length];
                        String newValue = newValues.get(i);
                        rights.add(new RightDTO(rightType, newValue));

                        // Pre-existing entry with shared contextServiceUuid
                        AclEntry existing = new AclEntry();
                        existing.setId(UUID.randomUUID());
                        existing.setContextServiceUuid(contextServiceUuid);
                        existing.setAccessType(AccessType.valueOf(rightType));
                        existing.setAccessLevel(
                                "APPROVED".equals(newValue) ? AccessLevel.REJECTED : AccessLevel.APPROVED);
                        existingEntries.add(existing);
                    }

                    ProvisionedServiceDTO svc = new ProvisionedServiceDTO(
                            "OBJECT", serviceName, contextId,
                            new ProvisionedServiceRightsDTO(rights));
                    ProvisionedZoneDTO zoneDTO = new ProvisionedZoneDTO(zoneId,
                            new ProvisionedZoneServicesDTO(List.of(svc)));
                    ProvisionRequestResolveDTO resolveDTO = new ProvisionRequestResolveDTO(
                            new ProvisionedZonesDTO(List.of(zoneDTO)));

                    return new UpsertTestData(environmentId, zoneId, zoneUuid,
                            contextId, contextUuid, resolveDTO, existingEntries);
                })
        );
    }
}
