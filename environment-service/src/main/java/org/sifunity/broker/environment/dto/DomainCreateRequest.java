// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a new domain.
 * 
 * @param domainId Unique identifier for the domain (must follow naming pattern)
 * @param description Optional description of the domain's purpose
 */
public record DomainCreateRequest(
    @NotBlank(message = "Domain ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?$",
        message = "Domain ID must start with alphanumeric character and contain only alphanumeric characters, underscores, and hyphens"
    )
    String domainId,
    
    String description
) {}
