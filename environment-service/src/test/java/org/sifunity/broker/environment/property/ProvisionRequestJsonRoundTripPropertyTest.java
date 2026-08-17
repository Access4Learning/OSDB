// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;

import org.sifunity.broker.environment.dto.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 8: JSON serialization round-trip.
 *
 * <p>For any valid ProvisionRequestResponseDTO (with arbitrary zones, services, and rights),
 * serializing to JSON via Jackson and deserializing back SHALL produce an equivalent object,
 * preserving zone count, service count per zone, right count per service, and all field values.</p>
 *
 * <p><strong>Validates: Requirements 10.1, 10.2</strong></p>
 *
 * <p>Pure function test — no Spring context needed. Just Jackson ObjectMapper.</p>
 */
@Tag("Feature: provision-requests, Property 8: JSON serialization round-trip")
class ProvisionRequestJsonRoundTripPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String[] RIGHT_TYPES = {"QUERY", "CREATE", "UPDATE", "DELETE", "SUBSCRIBE", "PROVIDE"};
    private static final String[] RIGHT_VALUES = {"APPROVED", "REJECTED", "REQUESTED"};
    private static final String[] SERVICE_TYPES = {"UTILITY", "OBJECT", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE"};
    private static final String[] COMPLETION_STATUSES = {null, "ACCEPTED", "MIXED", "REJECTED"};

    /**
     * Property 8: JSON serialization round-trip.
     *
     * <p><strong>Validates: Requirements 10.1, 10.2</strong></p>
     */
    @Property(tries = 20)
    void jsonRoundTripPreservesAllFields(
            @ForAll("validProvisionRequestResponseDTOs") ProvisionRequestResponseDTO original) throws Exception {

        String json = MAPPER.writeValueAsString(original);
        ProvisionRequestResponseDTO deserialized = MAPPER.readValue(json, ProvisionRequestResponseDTO.class);

        // Full equivalence
        assertThat(deserialized).isEqualTo(original);

        // Explicitly verify structural counts (Requirement 10.2)
        assertThat(deserialized.provisionedZones().provisionedZone())
                .as("zone count must be preserved")
                .hasSameSizeAs(original.provisionedZones().provisionedZone());

        List<ProvisionedZoneDTO> origZones = original.provisionedZones().provisionedZone();
        List<ProvisionedZoneDTO> deserZones = deserialized.provisionedZones().provisionedZone();
        for (int z = 0; z < origZones.size(); z++) {
            List<ProvisionedServiceDTO> origServices = origZones.get(z).services().service();
            List<ProvisionedServiceDTO> deserServices = deserZones.get(z).services().service();
            assertThat(deserServices)
                    .as("service count for zone %d must be preserved", z)
                    .hasSameSizeAs(origServices);

            for (int s = 0; s < origServices.size(); s++) {
                assertThat(deserServices.get(s).rights().right())
                        .as("right count for zone %d service %d must be preserved", z, s)
                        .hasSameSizeAs(origServices.get(s).rights().right());
            }
        }

        // Verify id and completionStatus
        assertThat(deserialized.id()).isEqualTo(original.id());
        assertThat(deserialized.completionStatus()).isEqualTo(original.completionStatus());
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<ProvisionRequestResponseDTO> validProvisionRequestResponseDTOs() {
        Arbitrary<UUID> ids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> completionStatuses = Arbitraries.of(COMPLETION_STATUSES);
        Arbitrary<ProvisionedZonesDTO> zones = provisionedZonesDTOs();

        return Combinators.combine(ids, completionStatuses, zones)
                .as(ProvisionRequestResponseDTO::new);
    }

    private Arbitrary<ProvisionedZonesDTO> provisionedZonesDTOs() {
        return Arbitraries.integers().between(1, 5).flatMap(zoneCount ->
                provisionedZoneDTOs().list().ofSize(zoneCount).map(ProvisionedZonesDTO::new));
    }

    private Arbitrary<ProvisionedZoneDTO> provisionedZoneDTOs() {
        Arbitrary<String> zoneIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20);
        return Combinators.combine(zoneIds, provisionedZoneServicesDTOs())
                .as(ProvisionedZoneDTO::new);
    }

    private Arbitrary<ProvisionedZoneServicesDTO> provisionedZoneServicesDTOs() {
        return Arbitraries.integers().between(1, 5).flatMap(serviceCount ->
                provisionedServiceDTOs().list().ofSize(serviceCount).map(ProvisionedZoneServicesDTO::new));
    }

    private Arbitrary<ProvisionedServiceDTO> provisionedServiceDTOs() {
        Arbitrary<String> types = Arbitraries.of(SERVICE_TYPES);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30);
        Arbitrary<String> contextIds = Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15));
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
        Arbitrary<String> values = Arbitraries.of(RIGHT_VALUES);
        return Combinators.combine(types, values).as(RightDTO::new);
    }
}
