// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.dto.AclEntryDTO;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test: AclEntryDTO serialization round-trip.
 *
 * <p><strong>Property:</strong> For any valid AclEntryDTO with zoneId/contextId
 * (String or null), JSON round-trip should produce an equivalent record.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
 *
 * <p>Pure unit test using Jackson — no Spring context needed.</p>
 */
@Tag("Feature: acl-sif-compliant-api, Property: AclEntryDTO serialization round-trip")
class AclEntryDtoSerializationPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Property: AclEntryDTO serialization round-trip.
     *
     * <p>For any valid AclEntryDTO with zoneId/contextId (String or null),
     * serializing to JSON and deserializing back should produce an equivalent record.</p>
     *
     * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
     */
    @Property(tries = 10)
    void dtoJsonRoundTripPreservesAllFields(
            @ForAll("validAclEntryDTOs") AclEntryDTO original) throws Exception {

        // Serialize to JSON
        String json = MAPPER.writeValueAsString(original);

        // Deserialize back
        AclEntryDTO deserialized = MAPPER.readValue(json, AclEntryDTO.class);

        // Assert full equivalence
        assertThat(deserialized).isEqualTo(original);

        // Explicitly verify zoneId/contextId survive round-trip
        assertThat(deserialized.zoneId())
                .as("zoneId must survive JSON round-trip")
                .isEqualTo(original.zoneId());
        assertThat(deserialized.contextId())
                .as("contextId must survive JSON round-trip")
                .isEqualTo(original.contextId());
    }

    /**
     * Property (null variant): Null zoneId/contextId serializes as JSON null
     * and deserializes back to null.
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     */
    @Property(tries = 10)
    void nullZoneContextSerializesCorrectly(
            @ForAll("validAclEntryDTOsWithNullContext") AclEntryDTO original) throws Exception {

        assertThat(original.zoneId()).isNull();
        assertThat(original.contextId()).isNull();

        String json = MAPPER.writeValueAsString(original);
        AclEntryDTO deserialized = MAPPER.readValue(json, AclEntryDTO.class);

        assertThat(deserialized.zoneId())
                .as("null zoneId must remain null after JSON round-trip")
                .isNull();
        assertThat(deserialized.contextId())
                .as("null contextId must remain null after JSON round-trip")
                .isNull();
        assertThat(deserialized).isEqualTo(original);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<AclEntryDTO> validAclEntryDTOs() {
        Arbitrary<UUID> ids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> serviceNames = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> zoneIds = Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20));
        Arbitrary<String> contextIds = Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20));
        Arbitrary<AccessType> accessTypes = Arbitraries.of(AccessType.values());
        Arbitrary<AccessLevel> accessLevels = Arbitraries.of(AccessLevel.values());

        return Combinators.combine(ids, ids, serviceNames, zoneIds, contextIds, accessTypes, accessLevels)
                .as(AclEntryDTO::new);
    }

    @Provide
    Arbitrary<AclEntryDTO> validAclEntryDTOsWithNullContext() {
        Arbitrary<UUID> ids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> serviceNames = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(50);
        Arbitrary<AccessType> accessTypes = Arbitraries.of(AccessType.values());
        Arbitrary<AccessLevel> accessLevels = Arbitraries.of(AccessLevel.values());

        return Combinators.combine(ids, ids, serviceNames, accessTypes, accessLevels)
                .as((id, envId, svc, at, al) -> new AclEntryDTO(id, envId, svc, null, null, at, al));
    }
}
