// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for solution service catalog entries.
 * 
 * <p>Represents a service name + type combination within a solution's
 * service catalog. Used for listing and returning created entries.</p>
 * 
 * @param id Internal UUID identifier for the solution service
 * @param serviceName Name of the service
 * @param serviceType Type of the service (UTILITY, OBJECT, FUNCTIONAL, SERVICEPATH, XQUERYTEMPLATE, SERVICE)
 * @param createdAt Timestamp when the solution service was created
 * 
 * @see SolutionServiceCreateRequest
 */
public record SolutionServiceDTO(
    UUID id,
    String serviceName,
    String serviceType,
    Instant createdAt
) {}
