// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import jakarta.validation.constraints.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing an application environment registration.
 * 
 * <p>An environment represents a registered application (consumer or provider)
 * that can interact with the SIF Unity Broker. Each environment has a unique
 * session token for authentication and default zone/context settings.</p>
 * 
 * <p>This DTO is immutable and includes validation annotations to ensure
 * data integrity.</p>
 * 
 * @see EnvironmentType
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public record EnvironmentDTO(
    /**
     * Unique identifier for the environment.
     */
    @NotNull
    UUID id,
    
    /**
     * Name of the application.
     */
    @NotBlank
    String applicationName,
    
    /**
     * Session token for authentication.
     */
    @NotBlank
    String sessionToken,
    
    /**
     * Timestamp when the environment was created.
     */
    @NotNull
    Instant createdAt,
    
    /**
     * Optional expiration timestamp for the environment.
     */
    Instant expiresAt,
    
    /**
     * Default zone ID for this environment.
     */
    @NotBlank
    String defaultZoneId,
    
    /**
     * Type of environment (DIRECT or BROKERED).
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
