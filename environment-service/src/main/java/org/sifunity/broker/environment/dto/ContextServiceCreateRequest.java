// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new context service.
 * 
 * <p>This record represents the data required to create a new context service,
 * which defines a named service within a specific context. Context services
 * are the normalized representation of "service X exists in context Y" and
 * are referenced by ACL entries and provider registrations.</p>
 * 
 * <p>The combination of (zoneId, contextId, serviceName) within a solution must be
 * unique. Attempting to create a duplicate entry will result in a 409 Conflict error.</p>
 * 
 * <p>The caller provides string identifiers (zoneId + contextId) rather than internal
 * UUIDs — the service resolves the context UUID internally using the domain and solution
 * from the URL path.</p>
 * 
 * @param zoneId      business identifier for the parent zone
 * @param contextId   business identifier for the context within the zone
 * @param serviceName Name of the service (must follow SIF naming conventions)
 * @param serviceType Type of the service (e.g., UTILITY, OBJECT, FUNCTIONAL, SERVICEPATH, XQUERYTEMPLATE, SERVICE)
 * 
 * @see ContextServiceDTO
 */
public record ContextServiceCreateRequest(
    /**
     * Zone business identifier.
     * Used together with contextId to resolve the target context.
     */
    @NotBlank(message = "Zone ID is required")
    String zoneId,

    /**
     * Context business identifier (unique within zone).
     * Used together with zoneId to resolve the target context.
     */
    @NotBlank(message = "Context ID is required")
    String contextId,

    /**
     * Name of the service.
     * Service names follow SIF 3.7 naming conventions.
     * Cannot be null or blank.
     */
    @NotBlank(message = "Service name is required")
    String serviceName,

    /**
     * Type of the service.
     * Must be one of: UTILITY, OBJECT, FUNCTIONAL, SERVICEPATH, XQUERYTEMPLATE, SERVICE.
     * Cannot be null or blank.
     */
    @NotBlank(message = "Service type is required")
    String serviceType
) {
}
