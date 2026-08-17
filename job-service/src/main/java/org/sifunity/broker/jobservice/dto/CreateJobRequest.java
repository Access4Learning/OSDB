// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for creating a new job.
 */
public class CreateJobRequest {
    
    @NotNull(message = "environmentId is required")
    private UUID environmentId;
    
    @NotBlank(message = "serviceName is required")
    private String serviceName;
    
    @NotBlank(message = "zoneId is required")
    private String zoneId;
    
    private String contextId;
    
    @Size(max = 10240, message = "metadata must not exceed 10KB")
    private String metadata;
    
    public CreateJobRequest() {
    }
    
    public CreateJobRequest(UUID environmentId, String serviceName, String zoneId, String contextId, String metadata) {
        this.environmentId = environmentId;
        this.serviceName = serviceName;
        this.zoneId = zoneId;
        this.contextId = contextId;
        this.metadata = metadata;
    }
    
    public UUID getEnvironmentId() {
        return environmentId;
    }
    
    public void setEnvironmentId(UUID environmentId) {
        this.environmentId = environmentId;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getZoneId() {
        return zoneId;
    }
    
    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }
    
    public String getContextId() {
        return contextId;
    }
    
    public void setContextId(String contextId) {
        this.contextId = contextId;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
