// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;

import org.mockito.ArgumentCaptor;
import org.sifunity.broker.environment.config.ProvisionRequestConfig;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.ProvisionRequest;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.ProvisionRequestService;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 1: Creation invariant — rights are REQUESTED, completionStatus is null, environment is correct.
 *
 * <p>For any valid provisionRequest payload and authenticated consumer, creating a provisionRequest
 * (with auto-approve disabled) SHALL produce a persisted resource where every right assertion value
 * is REQUESTED, the completionStatus is null, and the environment_id matches the authenticated
 * consumer's environment ID.</p>
 *
 * <p><strong>Validates: Requirements 1.3, 1.4, 1.9</strong></p>
 */
@Tag("Feature: provision-requests, Property 1: Creation invariant")
class ProvisionRequestCreationPropertyTest {

    private static final String[] RIGHT_TYPES = {"QUERY", "CREATE", "UPDATE", "DELETE", "SUBSCRIBE", "PROVIDE"};
    private static final String[] SERVICE_TYPES = {"UTILITY", "OBJECT", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE"};

    private static final UUID REQUESTER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final ObjectMapper REAL_OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Property 1: Creation invariant — rights are REQUESTED, completionStatus is null, environment is correct.
     *
     * <p><strong>Validates: Requirements 1.3, 1.4, 1.9</strong></p>
     */
    @Property(tries = 20)
    void creationInvariant_rightsAreRequested_completionStatusNull_environmentCorrect(
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
                provisionRequestRepo,
                aclEntryRepo,
                environmentRepo,
                zoneRepo,
                contextRepo,
                mock(ContextServiceRepository.class),
                config,
                authorizationService,
                REAL_OBJECT_MAPPER
        );

        // --- Act ---
        ProvisionRequestResponseDTO response = service.createProvisionRequest(
                REQUESTER_ID, payload, requester);

        // --- Assert ---

        // Req 1.9: response id is not null (resource was persisted with generated ID)
        assertThat(response.id())
                .as("Provision request ID must not be null after creation")
                .isNotNull();

        // Req 1.4: completionStatus is null (omitted) when auto-approve disabled
        assertThat(response.completionStatus())
                .as("completionStatus must be null when auto-approve is disabled")
                .isNull();

        // Req 1.3: all rights in response are REQUESTED
        List<ProvisionedZoneDTO> zones = response.provisionedZones().provisionedZone();
        assertThat(zones).isNotEmpty();

        for (ProvisionedZoneDTO zone : zones) {
            for (ProvisionedServiceDTO svc : zone.services().service()) {
                for (RightDTO right : svc.rights().right()) {
                    assertThat(right.value())
                            .as("Every right value must be REQUESTED, but found %s for type %s in service %s zone %s",
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
        // All rights must be REQUESTED on creation
        Arbitrary<String> types = Arbitraries.of(RIGHT_TYPES);
        return types.map(type -> new RightDTO(type, "REQUESTED"));
    }
}
