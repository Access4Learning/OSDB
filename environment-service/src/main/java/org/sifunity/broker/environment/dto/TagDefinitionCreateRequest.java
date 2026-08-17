// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request DTO for creating a new tag definition.
 *
 * @param tagName unique tag name (stored uppercase)
 * @param tagDescription optional description of the tag's purpose
 * @param applicableEntityTypes entity types this tag may be applied to
 * @param allowedValues optional constrained set of permitted values
 * @param createdBy identifier of the admin creating this definition
 */
public record TagDefinitionCreateRequest(
    @NotBlank(message = "Tag name is required")
    String tagName,

    String tagDescription,

    @NotEmpty(message = "At least one applicable entity type is required")
    List<String> applicableEntityTypes,

    List<String> allowedValues,

    @NotBlank(message = "Created by is required")
    String createdBy
) {}
