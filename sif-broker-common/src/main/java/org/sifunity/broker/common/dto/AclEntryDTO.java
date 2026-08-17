// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import jakarta.validation.constraints.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import java.util.UUID;

/**
 * Data Transfer Object representing an access control list (ACL) entry.
 * 
 * <p>An ACL entry defines the permissions an environment has for accessing
 * a specific service in a given zone/context. It specifies the access type
 * (QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE) and the access level
 * (APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, REQUESTED).</p>
 * 
 * <p>The {@code zoneId} and {@code contextId} fields identify the SIF zone and context
 * this ACL applies to. Null values represent a wildcard ACL that applies across
 * all zones/contexts.</p>
 * 
 * <p>This DTO is immutable and includes validation annotations to ensure
 * data integrity.</p>
 * 
 * @see AccessType
 * @see AccessLevel
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public record AclEntryDTO(
    /**
     * Unique identifier for the ACL entry.
     */
    @NotNull
    UUID id,
    
    /**
     * ID of the environment this ACL entry applies to.
     */
    @NotNull
    UUID environmentId,
    
    /**
     * Name of the service this ACL entry controls access to.
     */
    @NotBlank
    String serviceName,
    
    /**
     * SIF zone identifier this ACL entry applies to.
     * Null represents a wildcard ACL that applies across all zones.
     */
    String zoneId,
    
    /**
     * SIF context identifier this ACL entry applies to.
     * Null represents a wildcard ACL that applies across all contexts.
     */
    String contextId,
    
    /**
     * Type of access right per SIF 3.7 (QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE).
     */
    @NotNull
    AccessType accessType,
    
    /**
     * Access right value per SIF 3.7 (APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, REQUESTED).
     */
    @NotNull
    AccessLevel accessLevel
) {
}
