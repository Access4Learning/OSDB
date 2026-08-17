// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;

import org.sifunity.broker.environment.config.ProvisionRequestConfig;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.ProvisionRequest;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.ProvisionRequestService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 4: Auto-approval policy controls immediate resolution.
 *
 * <p>For any valid provisionRequest payload, when auto-approve is enabled, creation SHALL
 * immediately resolve all assertions to APPROVED and set completionStatus to ACCEPTED.
 * When auto-approve is disabled, creation SHALL leave all assertions as REQUESTED and
 * completionStatus as null.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.3</strong></p>
 */
@Tag("Feature: provision-requests, Property 4: Auto-approval policy")
class AutoApprovalPolicyPropertyTest {

    private static final String[] RIGHT_TYPES = {"QUERY", "CREATE", "UPDATE", "DELETE", "SUBSCRIBE", "PROVIDE"};
    private static final String[] SERVICE_TYPES = {"UTILITY", "OBJECT", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE"};

    private static final UUID REQUESTER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final ObjectMapper REAL_OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Property 4 (auto-approve ENABLED): all assertions APPROVED, completionStatus ACCEPTED.
     *
     * <p><strong>Validates: Requirements 4.1</strong></p>
     */
    @Property(tries = 20)
    void whenAutoApproveEnabled_allAssertionsApproved_completionStatusAccepted(
            @ForAll("validCreatePayloads") ProvisionRequestCreateDTO payload) {

        // --- Set up mocks ---
        ProvisionRequestRepository provisionRequestRepo = mock(ProvisionRequestRepository.class);
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);

        // Auto-approve ENABLED
        ProvisionRequestConfig config = new ProvisionRequestConfig(true);

        // Mock save to return entity with generated UUID
        when(provisionRequestRepo.save(any(ProvisionRequest.class))).thenAnswer(invocation -> {
            ProvisionRequest entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });

        // Mock requester environment
        Environment requester = mock(Environment.class);
        when(requester.getId()).thenReturn(REQUESTER_ID);

        // Mock environmentRepository.findById for auto-approve path
        Environment requesterEntity = new Environment();
        requesterEntity.setId(REQUESTER_ID);
        requesterEntity.setSolutionUuid(UUID.randomUUID());
        when(environmentRepo.findById(REQUESTER_ID)).thenReturn(Optional.of(requesterEntity));

        // Mock ZoneRepository.findBySolutionUuidAndZoneId — return a zone entity for any zone ID
        when(zoneRepo.findBySolutionUuidAndZoneId(any(UUID.class), any(String.class))).thenAnswer(invocation -> {
            String zoneIdArg = invocation.getArgument(1);
            Zone z = new Zone();
            z.setId(UUID.randomUUID());
            z.setZoneId(zoneIdArg);
            return Optional.of(z);
        });

        // Mock ContextRepository — return a context entity for any zone UUID + context ID
        when(contextRepo.findByZoneUuidAndContextId(any(UUID.class), any(String.class))).thenAnswer(invocation -> {
            UUID zoneUuidArg = invocation.getArgument(0);
            String contextIdArg = invocation.getArgument(1);
            Context ctx = new Context();
            ctx.setId(UUID.randomUUID());
            ctx.setZoneUuid(zoneUuidArg);
            ctx.setContextId(contextIdArg);
            return Optional.of(ctx);
        });

        // Mock AclEntryRepository: no existing entries, save returns entity
        ContextServiceRepository contextServiceRepo = mock(ContextServiceRepository.class);
        when(contextServiceRepo.findByContextUuidAndServiceName(any(), any())).thenReturn(Optional.empty());
        when(contextServiceRepo.save(any())).thenAnswer(invocation -> {
            var cs = invocation.getArgument(0);
            if (cs instanceof org.sifunity.broker.environment.domain.ContextService csEntity) {
                if (csEntity.getId() == null) csEntity.setId(UUID.randomUUID());
            }
            return cs;
        });
        when(aclEntryRepo.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                any(), any(), any())).thenReturn(Optional.empty());
        when(aclEntryRepo.save(any())).thenAnswer(invocation -> {
            var entry = invocation.getArgument(0);
            return entry;
        });

        ProvisionRequestService service = new ProvisionRequestService(
                provisionRequestRepo, aclEntryRepo, environmentRepo,
                zoneRepo, contextRepo, contextServiceRepo, config, authorizationService, REAL_OBJECT_MAPPER);

        // --- Act ---
        ProvisionRequestResponseDTO response = service.createProvisionRequest(
                REQUESTER_ID, payload, requester);

        // --- Assert ---
        assertThat(response.id()).as("ID must not be null").isNotNull();
        assertThat(response.completionStatus())
                .as("completionStatus must be ACCEPTED when auto-approve enabled")
                .isEqualTo("ACCEPTED");

        for (ProvisionedZoneDTO zone : response.provisionedZones().provisionedZone()) {
            for (ProvisionedServiceDTO svc : zone.services().service()) {
                for (RightDTO right : svc.rights().right()) {
                    assertThat(right.value())
                            .as("Every right value must be APPROVED when auto-approve enabled, " +
                                    "but found %s for type %s in service %s zone %s",
                                    right.value(), right.type(), svc.name(), zone.id())
                            .isEqualTo("APPROVED");
                }
            }
        }
    }

    /**
     * Property 4 (auto-approve DISABLED): all assertions REQUESTED, completionStatus null.
     *
     * <p><strong>Validates: Requirements 4.3</strong></p>
     */
    @Property(tries = 20)
    void whenAutoApproveDisabled_allAssertionsRequested_completionStatusNull(
            @ForAll("validCreatePayloads") ProvisionRequestCreateDTO payload) {

        // --- Set up mocks ---
        ProvisionRequestRepository provisionRequestRepo = mock(ProvisionRequestRepository.class);
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);

        // Auto-approve DISABLED
        ProvisionRequestConfig config = new ProvisionRequestConfig(false);

        // Mock save to return entity with generated UUID
        when(provisionRequestRepo.save(any(ProvisionRequest.class))).thenAnswer(invocation -> {
            ProvisionRequest entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });

        // Mock requester environment
        Environment requester = mock(Environment.class);
        when(requester.getId()).thenReturn(REQUESTER_ID);

        ProvisionRequestService service = new ProvisionRequestService(
                provisionRequestRepo, aclEntryRepo, environmentRepo,
                zoneRepo, contextRepo, mock(ContextServiceRepository.class), config, authorizationService, REAL_OBJECT_MAPPER);

        // --- Act ---
        ProvisionRequestResponseDTO response = service.createProvisionRequest(
                REQUESTER_ID, payload, requester);

        // --- Assert ---
        assertThat(response.id()).as("ID must not be null").isNotNull();
        assertThat(response.completionStatus())
                .as("completionStatus must be null when auto-approve disabled")
                .isNull();

        for (ProvisionedZoneDTO zone : response.provisionedZones().provisionedZone()) {
            for (ProvisionedServiceDTO svc : zone.services().service()) {
                for (RightDTO right : svc.rights().right()) {
                    assertThat(right.value())
                            .as("Every right value must be REQUESTED when auto-approve disabled, " +
                                    "but found %s for type %s in service %s zone %s",
                                    right.value(), right.type(), svc.name(), zone.id())
                            .isEqualTo("REQUESTED");
                }
            }
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<ProvisionRequestCreateDTO> validCreatePayloads() {
        return provisionedZonesDTOs().map(ProvisionRequestCreateDTO::new);
    }

    private Arbitrary<ProvisionedZonesDTO> provisionedZonesDTOs() {
        return Arbitraries.integers().between(1, 4).flatMap(zoneCount ->
                provisionedZoneDTOs().list().ofSize(zoneCount).map(ProvisionedZonesDTO::new));
    }

    private Arbitrary<ProvisionedZoneDTO> provisionedZoneDTOs() {
        Arbitrary<String> zoneIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15);
        return Combinators.combine(zoneIds, provisionedZoneServicesDTOs())
                .as(ProvisionedZoneDTO::new);
    }

    private Arbitrary<ProvisionedZoneServicesDTO> provisionedZoneServicesDTOs() {
        return Arbitraries.integers().between(1, 4).flatMap(serviceCount ->
                provisionedServiceDTOs().list().ofSize(serviceCount).map(ProvisionedZoneServicesDTO::new));
    }

    private Arbitrary<ProvisionedServiceDTO> provisionedServiceDTOs() {
        Arbitrary<String> types = Arbitraries.of(SERVICE_TYPES);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20);
        Arbitrary<String> contextIds = Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10));
        Arbitrary<ProvisionedServiceRightsDTO> rights = provisionedServiceRightsDTOs();

        return Combinators.combine(types, names, contextIds, rights)
                .as(ProvisionedServiceDTO::new);
    }

    private Arbitrary<ProvisionedServiceRightsDTO> provisionedServiceRightsDTOs() {
        return Arbitraries.integers().between(1, 6).flatMap(rightCount ->
                rightDTOs().list().ofSize(rightCount).map(ProvisionedServiceRightsDTO::new));
    }

    private Arbitrary<RightDTO> rightDTOs() {
        Arbitrary<String> types = Arbitraries.of(RIGHT_TYPES);
        return types.map(type -> new RightDTO(type, "REQUESTED"));
    }
}
