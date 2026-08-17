// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.environment.domain.EntityType;
import java.util.UUID;

/**
 * Request DTO for creating a new entity tag.
 *
 * @param entityType the type of entity being tagged (SOLUTION, ZONE, etc.)
 * @param entityId the UUID of the entity being tagged
 * @param tagKey the tag key (e.g., "SDPC_ENABLED", "RATE_LIMIT_TIER")
 * @param tagValue optional tag value; null represents a boolean flag
 */
public record EntityTagCreateRequest(
    @NotNull(message = "Entity type is required")
    EntityType entityType,

    @NotNull(message = "Entity ID is required")
    UUID entityId,

    @NotBlank(message = "Tag key is required")
    String tagKey,

    String tagValue
) {}
