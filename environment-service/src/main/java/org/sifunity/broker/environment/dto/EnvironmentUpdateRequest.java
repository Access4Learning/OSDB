// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request DTO for updating an environment.
 *
 * <p>Only mutable fields are included. Immutable fields (id, type, sessionToken,
 * solutionId) cannot be changed via update.</p>
 *
 * <p>All fields are optional — only non-null fields are applied to the entity.</p>
 *
 * @param consumerName       descriptive name for the application
 * @param defaultZone        default zone for this environment
 * @param fingerprint        shareable identifier (must not match id/sessionToken/userToken/applicationKey)
 * @param instanceId         optional instance identifier
 * @param userToken          optional user token
 * @param applicationInfo    optional application metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnvironmentUpdateRequest(
    String consumerName,
    DefaultZoneDTO defaultZone,
    String fingerprint,
    String instanceId,
    String userToken,
    ApplicationInfoDTO applicationInfo
) {}
