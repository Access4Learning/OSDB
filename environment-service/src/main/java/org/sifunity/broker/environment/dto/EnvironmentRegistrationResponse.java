// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.constant.EnvironmentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for environment registration.
 * 
 * <p>This record represents the response returned after successfully registering
 * a new application environment. It includes the generated credentials
 * (sessionToken and sharedSecret) that the application needs to authenticate
 * with the broker.</p>
 * 
 * <p><strong>Security Note:</strong> The sharedSecret is only returned during
 * registration and is never exposed in subsequent queries. Applications must
 * store this securely as it cannot be retrieved later.</p>
 * 
 * @param id Unique identifier for the created environment
 * @param applicationName Name of the registered application
 * @param sessionToken Generated session token for authentication (UUID format)
 * @param sharedSecret Generated shared secret for HMAC computation (Base64 encoded, 256+ bits)
 * @param createdAt Timestamp when the environment was created
 * @param defaultZoneId Default zone ID for this environment
 * @param type Type of environment (DIRECT, BROKERED, or ADMIN)
 * 
 * @see EnvironmentType
 * @see EnvironmentRegistrationRequest
 */
public record EnvironmentRegistrationResponse(
    /**
     * Unique identifier for the created environment.
     * Generated as UUID during registration.
     */
    @NotNull
    UUID id,
    
    /**
     * Name of the registered application.
     * Matches the applicationName from the registration request.
     */
    @NotBlank
    String applicationName,
    
    /**
     * Generated session token for authentication.
     * UUID format, unique across all environments.
     * Used by applications to authenticate with the broker.
     */
    @NotBlank
    String sessionToken,
    
    /**
     * Generated shared secret for HMAC computation.
     * Base64 encoded, minimum 256 bits (32 bytes) of cryptographically random data.
     * <strong>Only returned during registration - never exposed in queries.</strong>
     * Applications must store this securely.
     */
    @NotBlank
    String sharedSecret,
    
    /**
     * Timestamp when the environment was created.
     * Set automatically during registration.
     */
    @NotNull
    Instant createdAt,
    
    /**
     * Default zone ID for this environment.
     * Matches the defaultZoneId from the registration request.
     */
    @NotBlank
    String defaultZoneId,
    
    /**
     * Type of environment (DIRECT, BROKERED, or ADMIN).
     * Matches the type from the registration request.
     */
    @NotNull
    EnvironmentType type,
    
    /**
     * Optional solution ID that this environment belongs to.
     * Maps to SIF 3.3's solutionId concept for separating production from non-production environments.
     * Can be null if no solution was specified during registration.
     */
    String solutionId
) {
}