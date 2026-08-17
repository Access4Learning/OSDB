// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for context information.
 *
 * <p>This record represents context data returned by the Environment Service.
 * A context provides an additional routing dimension for message delivery
 * within a zone, allowing for more granular organization of services and data.</p>
 *
 * <p><strong>Serialized contract (SIF 3.7 context object).</strong> Per the SIF 3.7
 * specification, a context has: {@code id} (string business identifier) and
 * {@code description}. The internal database UUID is intentionally NOT exposed
 * in the API response. The parent zone's business identifier is included as
 * {@code zoneId} for navigation convenience.</p>
 *
 * @param id          internal database UUID for the context (primary key); NOT serialized
 * @param zoneId      business identifier for the parent zone
 * @param contextId   SIF business identifier for the context; serialized as {@code id}
 * @param description optional description of the context's purpose
 * @param createdAt   timestamp when the context was created
 *
 * @see ContextCreateRequest
 * @see ZoneDTO
 */
// NOTE: This record is SERIALIZE-ONLY. The @JsonIgnore on 'id' combined with
// @JsonProperty("id") on 'contextId' creates a deserialization conflict. Do not
// attempt to readValue() this DTO — use jsonPath assertions or service-layer
// accessors instead. If deserialization is ever needed, split into separate
// request/response DTOs.
public record ContextDTO(
    /**
     * Internal database UUID identifier for the context.
     * Not exposed in API responses — used only internally.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    UUID id,

    /**
     * Business identifier for the parent zone.
     */
    String zoneId,

    /**
     * SIF business identifier for the context.
     * Serialized as the canonical {@code id} field per SIF 3.7 spec.
     */
    @JsonProperty("id")
    String contextId,

    /**
     * Optional description of the context's purpose.
     */
    String description,

    /**
     * Timestamp when the context was created.
     * Internal implementation detail — not part of SIF 3.7 context schema.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    Instant createdAt
) {
    /** Ensure createdAt is not serialized in API responses. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override
    public Instant createdAt() { return createdAt; }
}
