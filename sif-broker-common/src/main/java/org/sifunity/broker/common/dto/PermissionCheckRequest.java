// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import org.sifunity.broker.common.constant.AccessType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for internal permission check API.
 * Used by the API Gateway to check ACL permissions for authenticated environments.
 * 
 * @param environmentId The unique identifier of the environment
 * @param serviceName The name of the service being accessed
 * @param zoneId The zone identifier
 * @param contextId The context identifier (optional)
 * @param accessType The type of access being requested (QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE)
 */
public record PermissionCheckRequest(
    @NotNull(message = "Environment ID is required")
    UUID environmentId,
    
    @NotBlank(message = "Service name is required")
    String serviceName,
    
    @NotBlank(message = "Zone ID is required")
    String zoneId,
    
    String contextId,
    
    @NotNull(message = "Access type is required")
    AccessType accessType
) {}
