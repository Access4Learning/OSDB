// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request DTO for updating an existing tag definition.
 * Tag name is immutable and not included.
 *
 * @param tagDescription optional updated description
 * @param applicableEntityTypes updated entity types this tag may be applied to
 * @param allowedValues optional updated constrained set of permitted values
 */
public record TagDefinitionUpdateRequest(
    String tagDescription,

    @NotEmpty(message = "At least one applicable entity type is required")
    List<String> applicableEntityTypes,

    List<String> allowedValues
) {}
