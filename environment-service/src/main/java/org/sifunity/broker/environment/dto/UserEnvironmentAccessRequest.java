// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for granting an admin user access to an environment.
 *
 * @param environmentId UUID of the environment to grant access to
 */
public record UserEnvironmentAccessRequest(
    @NotNull(message = "Environment ID is required")
    UUID environmentId
) {}
