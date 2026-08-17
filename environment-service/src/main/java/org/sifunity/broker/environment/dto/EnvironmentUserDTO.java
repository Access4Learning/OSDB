// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing an admin user with access to a specific environment.
 *
 * @param id        UUID of the admin user
 * @param username  Username of the admin user
 * @param grantedAt Timestamp when environment access was granted
 */
public record EnvironmentUserDTO(
    UUID id,
    String username,
    Instant grantedAt
) {}
