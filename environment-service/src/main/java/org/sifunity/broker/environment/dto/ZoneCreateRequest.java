// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a new zone.
 * 
 * <p>This record represents the data required to create a new zone within
 * the SIF Unity Broker. A zone is a logical grouping of services and data
 * that provides organizational structure for the broker.</p>
 * 
 * <p>The zone ID must follow a specific naming pattern: it must start with
 * an alphanumeric character and can contain alphanumeric characters, underscores,
 * and hyphens. The zone ID must be unique across all zones in the system.</p>
 * 
 * <p>The description is optional and can be used to provide additional
 * context about the purpose or contents of the zone.</p>
 * 
 * @param zoneId Unique identifier for the zone (must follow naming pattern)
 * @param description Optional description of the zone's purpose
 * 
 * @see ZoneDTO
 */
public record ZoneCreateRequest(
    /**
     * Solution identifier - populated from URL path, not required in request body.
     */
    String solutionId,
    
    /**
     * Unique identifier for the zone within the solution.
     * Must start with an alphanumeric character and can contain
     * alphanumeric characters, underscores, and hyphens.
     * Cannot be null or blank.
     */
    @NotBlank(message = "Zone ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?$",
        message = "Zone ID must start with alphanumeric character and contain only alphanumeric characters, underscores, and hyphens"
    )
    String zoneId,
    
    /**
     * Optional description of the zone's purpose.
     * Can be null if no description is needed.
     */
    String description
) {
}