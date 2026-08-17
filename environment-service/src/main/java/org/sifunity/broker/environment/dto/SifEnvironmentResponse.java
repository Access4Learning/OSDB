// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.sifunity.broker.common.constant.EnvironmentType;

import java.util.UUID;

/**
 * DTO representing the full SIF 3.7 Environment response object.
 *
 * @param id                      the environment identifier
 * @param type                    the environment type (DIRECT, BROKERED, ADMIN)
 * @param fingerprint             a unique shareable identifier for the environment
 * @param sessionToken            the session token for authentication
 * @param solutionId              the solution this environment belongs to
 * @param defaultZone             the default zone for this environment
 * @param authenticationMethod    the authentication method (e.g., "SIF_HMACSHA256")
 * @param instanceId              optional instance identifier
 * @param userToken               optional user token
 * @param consumerName            the consumer/application name
 * @param applicationInfo         optional application metadata
 * @param infrastructureServices  the broker infrastructure service URLs
 * @param provisionedZones        the provisioned zones and service rights
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SifEnvironmentResponse(
    UUID id,
    EnvironmentType type,
    String fingerprint,
    String sessionToken,
    String solutionId,
    DefaultZoneDTO defaultZone,
    String authenticationMethod,
    String instanceId,
    String userToken,
    String consumerName,
    ApplicationInfoDTO applicationInfo,
    InfrastructureServicesDTO infrastructureServices,
    ProvisionedZonesDTO provisionedZones
) {
}
