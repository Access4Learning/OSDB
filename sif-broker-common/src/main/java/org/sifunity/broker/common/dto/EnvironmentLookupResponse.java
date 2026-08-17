// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import java.util.UUID;

/**
 * Response DTO for internal environment lookup APIs.
 * Used by both the environment-service internal endpoints and the gateway client
 * for proxy impersonation target resolution.
 *
 * @param id The unique identifier of the environment
 * @param applicationName The name of the registered application
 * @param fingerprint The environment's fingerprint (e.g. "broker-services")
 * @param solutionUuid The solution this environment belongs to
 */
public record EnvironmentLookupResponse(
    UUID id,
    String applicationName,
    String fingerprint,
    UUID solutionUuid
) {}
