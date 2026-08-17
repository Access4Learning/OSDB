// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import org.sifunity.broker.environment.domain.EntityType;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for entity tag information.
 *
 * @param id internal UUID of the tag
 * @param solutionId UUID of the parent solution
 * @param entityType type of the tagged entity
 * @param entityId UUID of the tagged entity
 * @param tagKey tag key
 * @param tagValue tag value (nullable — null means boolean flag)
 * @param createdAt creation timestamp
 */
public record EntityTagDTO(
    UUID id,
    UUID solutionId,
    EntityType entityType,
    UUID entityId,
    String tagKey,
    String tagValue,
    Instant createdAt
) {}
