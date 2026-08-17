// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.sifunity.broker.common.constant.JobState;

/**
 * Request DTO for updating job state.
 */
public class UpdateJobStateRequest {
    
    @NotNull(message = "state is required")
    private JobState state;
    
    @Size(max = 10240, message = "metadata must not exceed 10KB")
    private String metadata;
    
    public UpdateJobStateRequest() {
    }
    
    public UpdateJobStateRequest(JobState state, String metadata) {
        this.state = state;
        this.metadata = metadata;
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
