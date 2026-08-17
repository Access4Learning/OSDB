// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for internal token validation API.
 * Used by the API Gateway to validate sessionTokens and HMAC signatures.
 * 
 * @param sessionToken The session token to validate (UUID format)
 * @param timestamp The timestamp used in HMAC computation (ISO-8601 format)
 * @param hash The HMAC hash to verify against
 */
public record TokenValidationRequest(
    @NotBlank(message = "Session token is required")
    String sessionToken,
    
    @NotBlank(message = "Timestamp is required")
    String timestamp,
    
    @NotBlank(message = "Hash is required")
    String hash
) {}
