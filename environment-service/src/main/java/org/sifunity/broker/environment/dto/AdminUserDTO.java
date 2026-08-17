// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for admin user information.
 *
 * @param id           Internal UUID identifier for the admin user
 * @param username     Username of the admin user
 * @param domainUuid   UUID of the domain this user belongs to
 * @param solutionUuid UUID of the solution this user belongs to
 * @param createdAt    Timestamp when the user was created
 * @param updatedAt    Timestamp when the user was last updated
 */
public record AdminUserDTO(
    UUID id,
    String username,
    UUID domainUuid,
    UUID solutionUuid,
    Instant createdAt,
    Instant updatedAt
) {}
