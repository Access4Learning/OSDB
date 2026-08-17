// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Optional request body for credential rotation.
 *
 * <p>All fields are optional. If not provided, credentials are auto-generated.</p>
 *
 * @param sessionToken  desired new session token (optional — auto-generated if null)
 * @param sharedSecret  desired new shared secret (optional — auto-generated if null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CredentialRotationRequest(
    String sessionToken,
    String sharedSecret
) {}
