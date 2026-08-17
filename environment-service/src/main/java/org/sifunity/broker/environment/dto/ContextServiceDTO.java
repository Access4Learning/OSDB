// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for context service information.
 * 
 * <p>This record represents context service data returned by the Environment Service.
 * A context service defines a named service within a specific context, serving as
 * the normalized representation of "service X exists in context Y".</p>
 * 
 * <p>This DTO is used for all context service retrieval operations, including
 * individual queries, listing by context, and listing by zone.</p>
 * 
 * @param id Internal UUID identifier for the context service
 * @param contextUuid UUID of the context this service belongs to
 * @param serviceName Name of the service
 * @param serviceType Type of the service (e.g., UTILITY, OBJECT, FUNCTIONAL)
 * @param createdAt Timestamp when the context service was created
 * 
 * @see ContextServiceCreateRequest
 */
public record ContextServiceDTO(
    /**
     * Internal UUID identifier for the context service.
     * This is the primary key used internally by the system.
     */
    UUID id,

    /**
     * UUID of the context this service belongs to.
     */
    UUID contextUuid,

    /**
     * Business identifier of the context (human-readable string).
     * Populated from the parent Context entity for display purposes.
     */
    String contextId,

    /**
     * Business identifier of the zone (human-readable string).
     * Populated from the parent Zone entity for display purposes.
     */
    String zoneId,

    /**
     * Name of the service.
     */
    String serviceName,

    /**
     * Type of the service.
     * One of: UTILITY, OBJECT, FUNCTIONAL, SERVICEPATH, XQUERYTEMPLATE, SERVICE.
     */
    String serviceType,

    /**
     * Timestamp when the context service was created.
     * Automatically set during creation.
     */
    Instant createdAt
) {
}
