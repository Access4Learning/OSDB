// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.dto;

import org.sifunity.broker.common.constant.JobState;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for JobPhase entity.
 */
public class JobPhaseDTO {
    
    private UUID id;
    private UUID jobId;
    private String phaseName;
    private JobState state;
    private Instant createdAt;
    private Instant updatedAt;
    private String metadata;
    
    public JobPhaseDTO() {
    }
    
    public JobPhaseDTO(UUID id, UUID jobId, String phaseName, JobState state,
                       Instant createdAt, Instant updatedAt, String metadata) {
        this.id = id;
        this.jobId = jobId;
        this.phaseName = phaseName;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.metadata = metadata;
    }
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getJobId() {
        return jobId;
    }
    
    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }
    
    public String getPhaseName() {
        return phaseName;
    }
    
    public void setPhaseName(String phaseName) {
        this.phaseName = phaseName;
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
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
