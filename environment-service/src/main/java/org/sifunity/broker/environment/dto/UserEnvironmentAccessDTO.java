// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for a user-environment access association.
 *
 * @param id              UUID of the access association record
 * @param userId          UUID of the admin user
 * @param environmentId   UUID of the environment
 * @param applicationName Name of the environment's registered application
 * @param grantedAt       Timestamp when access was granted
 */
public record UserEnvironmentAccessDTO(
    UUID id,
    UUID userId,
    UUID environmentId,
    String applicationName,
    Instant grantedAt
) {}
