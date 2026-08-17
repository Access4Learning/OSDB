// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.dto;

import org.sifunity.broker.common.constant.JobState;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for Job entity.
 */
public class JobDTO {
    
    private UUID id;
    private UUID environmentId;
    private String serviceName;
    private String zoneId;
    private String contextId;
    private JobState state;
    private Instant createdAt;
    private Instant updatedAt;
    private String currentPhase;
    private String metadata;
    
    public JobDTO() {
    }
    
    public JobDTO(UUID id, UUID environmentId, String serviceName, String zoneId, String contextId,
                  JobState state, Instant createdAt, Instant updatedAt, String currentPhase, String metadata) {
        this.id = id;
        this.environmentId = environmentId;
        this.serviceName = serviceName;
        this.zoneId = zoneId;
        this.contextId = contextId;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.currentPhase = currentPhase;
        this.metadata = metadata;
    }
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
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
    
    public JobState getState() {
        return state;
    }
    
    public void setState(JobState state) {
        this.state = state;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getCurrentPhase() {
        return currentPhase;
    }
    
    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
