// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.sifunity.broker.common.constant.JobState;

/**
 * Request DTO for creating a new job phase.
 */
public class CreatePhaseRequest {
    
    @NotBlank(message = "phaseName is required")
    @Size(max = 100, message = "phaseName must not exceed 100 characters")
    private String phaseName;
    
    @NotNull(message = "state is required")
    private JobState state;
    
    @Size(max = 10240, message = "metadata must not exceed 10KB")
    private String metadata;
    
    public CreatePhaseRequest() {
    }
    
    public CreatePhaseRequest(String phaseName, JobState state, String metadata) {
        this.phaseName = phaseName;
        this.state = state;
        this.metadata = metadata;
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
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
