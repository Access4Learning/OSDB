// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new admin user.
 *
 * @param username   Username for the admin user (3-128 chars, alphanumeric/underscore/hyphen)
 * @param password   Password for the admin user (8-72 chars)
 * @param domainId   Human-readable domain identifier
 * @param solutionId Human-readable solution identifier
 */
public record AdminUserCreateRequest(
    @NotBlank(message = "Username is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9_-]{3,128}$",
        message = "Username must be 3-128 characters, alphanumeric/underscore/hyphen only"
    )
    String username,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
    String password,

    @NotBlank(message = "Domain ID is required")
    String domainId,

    @NotBlank(message = "Solution ID is required")
    String solutionId
) {}
