// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.UUID;

/**
 * Context object representing an authenticated admin user session.
 * Set as a request attribute after successful session validation by AdminSessionFilter.
 *
 * @param userId                UUID of the authenticated admin user
 * @param username              Username of the authenticated admin user
 * @param domainUuid            UUID of the domain the user belongs to
 * @param solutionUuid          UUID of the solution the user belongs to
 * @param selectedEnvironmentId UUID of the currently selected environment (null if not yet selected)
 */
public record AdminSessionContext(
    UUID userId,
    String username,
    UUID domainUuid,
    UUID solutionUuid,
    UUID selectedEnvironmentId
) {}
