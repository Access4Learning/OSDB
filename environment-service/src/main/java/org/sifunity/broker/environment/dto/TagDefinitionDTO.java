// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for tag definition response mapping.
 *
 * @param id unique identifier of the tag definition
 * @param tagName tag name (uppercase)
 * @param tagDescription optional description
 * @param applicableEntityTypes entity types this tag may be applied to
 * @param allowedValues optional constrained set of permitted values
 * @param createdBy identifier of the admin who created this definition
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record TagDefinitionDTO(
    UUID id,
    String tagName,
    String tagDescription,
    List<String> applicableEntityTypes,
    List<String> allowedValues,
    String createdBy,
    Instant createdAt,
    Instant updatedAt
) {}
