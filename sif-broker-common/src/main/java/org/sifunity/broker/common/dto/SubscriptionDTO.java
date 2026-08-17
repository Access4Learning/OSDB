// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import org.sifunity.broker.common.constant.SubscriptionType;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing a subscription per SIF 3.7 specification.
 *
 * <p>SIF 3.7 subscription fields:</p>
 * <ul>
 *   <li>{@code id} — unique identifier</li>
 *   <li>{@code queueId} — target queue (required)</li>
 *   <li>{@code serviceName} — service name (required)</li>
 *   <li>{@code serviceType} — OBJECT, UTILITY, FUNCTIONAL, etc. (required)</li>
 *   <li>{@code zoneId} — zone identifier string (nullable; null = wildcard)</li>
 *   <li>{@code contextId} — context identifier string (nullable; null = wildcard)</li>
 *   <li>{@code messageType} — EVENT or REQUEST (optional)</li>
 * </ul>
 *
 * <p>The {@code aclEntryId} and {@code createdAt} fields are internal implementation
 * details excluded from JSON responses.</p>
 *
 * @see SubscriptionType
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubscriptionDTO(
    UUID id,

    @NotNull
    UUID queueId,

    /**
     * ACL entry ID — accepted on creation, hidden from responses.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    UUID aclEntryId,

    /**
     * Required by the SIF schema (D4 / requirement 2.10) — but see {@link #isAclResolvable()}:
     * required only when no {@code aclEntryId} is supplied, because with one the broker derives this
     * from the resolved ACL entry.
     */
    String serviceName,

    /**
     * Required by the SIF schema (D4 / requirement 2.10).
     *
     * <p>Previously optional and silently defaulted to {@code "OBJECT"} by a convenience
     * constructor, which invented a value the client never sent — a subscription quietly bound to
     * the wrong service type is far harder to notice than a rejected request.</p>
     */
    String serviceType,

    /**
     * Required by the SIF schema (D4 / requirement 2.10).
     *
     * <p>This component was previously documented as "nullable; null = wildcard", but no wildcard
     * behaviour was ever implemented: {@code AclEntryLookupRepository} matches with
     * {@code z.zoneId = :zoneId}, and in JPQL {@code = null} matches nothing. A null zoneId therefore
     * produced a silent no-match rather than a wildcard, so requiring it converts a confusing empty
     * result into an explicit 400 instead of removing a working feature.</p>
     */
    String zoneId,

    /** Optional — requirement 2.10 requires queueId, serviceName, serviceType and zoneId only. */
    String contextId,

    /**
     * SIF 3.7 messageType: EVENT or DELAYED_REQUEST.
     */
    @NotNull
    SubscriptionType messageType,

    /**
     * Internal creation timestamp — not part of SIF 3.7 subscription schema.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    Instant createdAt
) {
    /**
     * D4 / requirement 2.10 — the spec-required subscription fields, expressed as the contract the
     * implementation actually has.
     *
     * <p>2.10 reads as though {@code serviceName}, {@code serviceType} and {@code zoneId} are
     * unconditionally required. They are not, because {@code POST /subscriptions/subscription}
     * supports <b>two</b> creation modes and only one of them carries those fields:</p>
     *
     * <ol>
     *   <li><b>By {@code aclEntryId}.</b> The client names an ACL entry and the broker derives
     *       {@code serviceName}, {@code zoneId} and {@code contextId} from it
     *       ({@code SubscriptionService.resolveAclEntry}). Demanding them here would be redundant and
     *       would break every existing client of this mode.</li>
     *   <li><b>By {@code serviceName}/{@code zoneId}/{@code contextId}.</b> The broker looks the ACL
     *       up from these, so they must all be present — this is the mode 2.10 is really about, and
     *       the mode where the old code silently accepted a partial body.</li>
     * </ol>
     *
     * <p>Requiring them unconditionally would satisfy the literal wording while rejecting valid
     * requests, so the constraint is expressed as "either an {@code aclEntryId}, or a complete
     * resolution triple". A body with neither is rejected with 400, which is what the defect was.</p>
     */
    @AssertTrue(message = "either aclEntryId, or all of serviceName, serviceType and zoneId, must be supplied")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isAclResolvable() {
        if (aclEntryId != null) {
            return true;
        }
        return serviceName != null && !serviceName.isBlank()
                && serviceType != null && !serviceType.isBlank()
                && zoneId != null && !zoneId.isBlank();
    }

    /** Ensure WRITE_ONLY is applied on the accessor for serialization. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Override
    public UUID aclEntryId() { return aclEntryId; }

    /** Ensure createdAt is not serialized in API responses. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override
    public Instant createdAt() { return createdAt; }

    /**
     * Convenience constructor for the 8-arg call sites that do not specify {@code serviceType}.
     *
     * <p><b>This is not the D4 defect path.</b> Jackson deserializes records through the canonical
     * constructor, so a JSON body omitting {@code serviceType} yields {@code null} and is now rejected
     * by {@code @NotBlank} — the wire contract is fixed by the constraint alone. This constructor is
     * reachable only from Java, at internal construction sites that are not client input, so it keeps
     * defaulting {@code "OBJECT"} rather than forcing ~20 unrelated fixtures to restate it.</p>
     */
    public SubscriptionDTO(UUID id, UUID queueId, UUID aclEntryId, String serviceName,
                           String zoneId, String contextId,
                           SubscriptionType messageType, Instant createdAt) {
        this(id, queueId, aclEntryId, serviceName, "OBJECT", zoneId, contextId, messageType, createdAt);
    }
}
