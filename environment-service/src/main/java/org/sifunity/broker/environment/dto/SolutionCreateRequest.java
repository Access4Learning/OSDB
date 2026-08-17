// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a new solution.
 * 
 * @param domainId Identifier for the parent domain (populated from URL path, not required in request body)
 * @param solutionId Unique identifier for the solution within the domain
 * @param description Optional description of the solution's purpose
 */
public record SolutionCreateRequest(
    String domainId,
    
    @NotBlank(message = "Solution ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?$",
        message = "Solution ID must start with alphanumeric character and contain only alphanumeric characters, underscores, and hyphens"
    )
    String solutionId,
    
    String description
) {}
