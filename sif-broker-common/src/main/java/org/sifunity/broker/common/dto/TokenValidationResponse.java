// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import org.sifunity.broker.common.constant.EnvironmentType;

import java.util.UUID;

/**
 * Response DTO for internal token validation API.
 * Contains environment details returned after successful token validation.
 * 
 * @param environmentId The unique identifier of the environment
 * @param applicationName The name of the registered application
 * @param type The type of environment (DIRECT, BROKERED)
 * @param defaultZoneId The default zone for this environment
 * @param fingerprint The environment's fingerprint for gateway stamping (optional)
 * @param domainId The domain the environment belongs to
 * @param solutionId The solution the environment belongs to
 * @param solutionUuid The UUID of the solution the environment belongs to
 */
public record TokenValidationResponse(
    UUID environmentId,
    String applicationName,
    EnvironmentType type,
    String defaultZoneId,
    String fingerprint,
    String domainId,
    String solutionId,
    UUID solutionUuid
) {}
