// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for credential rotation.
 *
 * <p>Contains the new session token and shared secret after rotation.
 * The shared secret is shown ONLY in this response — it cannot be retrieved again.</p>
 *
 * @param id             the environment ID
 * @param sessionToken   the new session token (replaces the old one immediately)
 * @param sharedSecret   the new shared secret (shown only once — save it)
 * @param rotatedAt      timestamp of the rotation
 */
public record CredentialRotationResponse(
    UUID id,
    String sessionToken,
    String sharedSecret,
    Instant rotatedAt
) {}
