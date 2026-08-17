// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a new context within a zone.
 * 
 * <p>This record represents the data required to create a new context within
 * an existing zone. A context provides an additional routing dimension for
 * message delivery within a zone, allowing for more granular organization
 * of services and data.</p>
 * 
 * <p>The context ID must follow a specific naming pattern: it must start with
 * an alphanumeric character and can contain alphanumeric characters, underscores,
 * and hyphens. The combination of zone ID and context ID must be unique across
 * all contexts in the system.</p>
 * 
 * <p>The description is optional and can be used to provide additional
 * context about the purpose or contents of the context.</p>
 * 
 * @param contextId Unique identifier for the context within the zone (must follow naming pattern)
 * @param description Optional description of the context's purpose
 * 
 * @see ContextDTO
 */
public record ContextCreateRequest(
    /**
     * Unique identifier for the context within the zone.
     * Must start with an alphanumeric character and can contain
     * alphanumeric characters, underscores, and hyphens.
     * Cannot be null or blank.
     */
    @NotBlank(message = "Context ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?$",
        message = "Context ID must start with alphanumeric character and contain only alphanumeric characters, underscores, and hyphens"
    )
    String contextId,
    
    /**
     * Optional description of the context's purpose.
     * Can be null if no description is needed.
     */
    String description
) {
}