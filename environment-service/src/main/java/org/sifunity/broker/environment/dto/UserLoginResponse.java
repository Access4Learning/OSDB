// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for successful admin user login.
 *
 * @param sessionToken  Session token for subsequent authenticated requests
 * @param expiresAt     Timestamp when the session token expires
 * @param environments  List of environments the user has access to
 */
public record UserLoginResponse(
    String sessionToken,
    Instant expiresAt,
    List<EnvironmentSummaryDTO> environments
) {}
