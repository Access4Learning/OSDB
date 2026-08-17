// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;

import java.util.UUID;

/**
 * Request DTO for creating a new ACL entry.
 * 
 * <p>This record represents the data required to create a new access control
 * list (ACL) entry. An ACL entry defines the permissions an environment has
 * for accessing a specific service in a given context.</p>
 * 
 * <p>The combination of environmentId, serviceName, zoneId, contextId, and
 * accessType must be unique across all ACL entries. Attempting to create
 * a duplicate entry will result in a conflict error.</p>
 * 
 * <p>The zoneId and contextId are SIF-standard string identifiers that the
 * server resolves to an internal context UUID via the solution's zone/context
 * hierarchy.</p>
 * 
 * @param environmentId ID of the environment this ACL entry applies to
 * @param serviceName Name of the service this ACL entry controls access to
 * @param zoneId SIF zone identifier within the solution
 * @param contextId SIF context identifier within the zone
 * @param accessType Type of access (PROVIDE, CONSUME, or SUBSCRIBE)
 * @param accessLevel Level of access (APPROVED, REJECTED, or PENDING)
 * 
 * @see AccessType
 * @see AccessLevel
 * @see org.sifunity.broker.common.dto.AclEntryDTO
 */
public record AclEntryCreateRequest(
    /**
     * ID of the environment this ACL entry applies to.
     * The environment must exist in the system.
     * Cannot be null.
     */
    @NotNull(message = "Environment ID is required")
    UUID environmentId,

    /**
     * Name of the service this ACL entry controls access to.
     * Service names follow SIF 3.7 naming conventions.
     * Cannot be null or blank.
     */
    @NotBlank(message = "Service name is required")
    String serviceName,

    /**
     * SIF zone identifier within the solution.
     * Resolved server-side to an internal zone UUID.
     * Cannot be null or blank.
     */
    @NotBlank(message = "Zone ID is required")
    String zoneId,

    /**
     * SIF context identifier within the zone.
     * Resolved server-side to an internal context UUID.
     * Cannot be null or blank.
     */
    @NotBlank(message = "Context ID is required")
    String contextId,

    /**
     * Type of access being granted or denied.
     * Determines what operations the environment can perform.
     * Cannot be null.
     */
    @NotNull(message = "Access type is required")
    AccessType accessType,

    /**
     * Level of access being set.
     * APPROVED grants permission, REJECTED denies permission,
     * PENDING indicates the request is awaiting approval.
     * Cannot be null.
     */
    @NotNull(message = "Access level is required")
    AccessLevel accessLevel
) {
}
