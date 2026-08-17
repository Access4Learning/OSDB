// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for zone information.
 *
 * <p>This record represents zone data returned by the Environment Service.
 * A zone is a logical grouping of services and data that provides
 * organizational structure for the broker.</p>
 *
 * <p><strong>Serialized contract (SIF 3.7 zone object).</strong> Per the SIF 3.7
 * specification, a zone has: {@code id} (string business identifier),
 * {@code description}, and {@code properties}. The internal database UUID is
 * intentionally NOT exposed in the API response.</p>
 *
 * @param id          internal database UUID for the zone (primary key); NOT serialized
 * @param solutionId  identifier of the parent solution this zone belongs to; NOT serialized
 * @param zoneId      SIF business identifier for the zone; serialized as {@code id}
 * @param description optional description of the zone's purpose
 * @param createdAt   timestamp when the zone was created
 *
 * @see ZoneCreateRequest
 */
// NOTE: This record is SERIALIZE-ONLY. The @JsonIgnore on 'id' combined with
// @JsonProperty("id") on 'zoneId' creates a deserialization conflict. Do not
// attempt to readValue() this DTO — use jsonPath assertions or service-layer
// accessors instead. If deserialization is ever needed, split into separate
// request/response DTOs.
public record ZoneDTO(
    /**
     * Internal database UUID identifier for the zone.
     * Not exposed in API responses — used only internally.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    UUID id,

    /**
     * Solution identifier - references the parent solution this zone belongs to.
     * Not exposed in API responses.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    String solutionId,

    /**
     * SIF business identifier for the zone.
     * Serialized as the canonical {@code id} field per SIF 3.7 spec.
     */
    @JsonProperty("id")
    String zoneId,

    /**
     * Optional description of the zone's purpose.
     */
    String description,

    /**
     * Timestamp when the zone was created.
     * Internal implementation detail — not part of SIF 3.7 zone schema.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    Instant createdAt
) {
    /** Ensure createdAt is not serialized in API responses. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override
    public Instant createdAt() { return createdAt; }
}
