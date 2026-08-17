// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for admin user login.
 *
 * @param username     Username of the admin user
 * @param password     Password of the admin user
 * @param domainId     Human-readable domain identifier
 * @param solutionId   Human-readable solution identifier
 */
public record UserLoginRequest(
    @NotBlank(message = "Username is required")
    String username,

    @NotBlank(message = "Password is required")
    String password,

    @NotBlank(message = "Domain ID is required")
    String domainId,

    @NotBlank(message = "Solution ID is required")
    String solutionId
) {}
