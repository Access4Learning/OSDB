// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new solution service catalog entry.
 * 
 * <p>Defines a service name + type combination to add to a solution's
 * service catalog. The combination of (solutionId, serviceName, serviceType)
 * must be unique.</p>
 * 
 * @param serviceName Name of the service (e.g., "StudentPersonal", "SchoolInfo")
 * @param serviceType Type of the service per SIF 3.7 serviceTypeType
 * 
 * @see SolutionServiceDTO
 */
public record SolutionServiceCreateRequest(
    @NotBlank(message = "Service name is required")
    String serviceName,

    @NotBlank(message = "Service type is required")
    String serviceType
) {}
