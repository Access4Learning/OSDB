// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import jakarta.validation.constraints.*;
import org.sifunity.broker.common.constant.JobState;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing a functional service job.
 * 
 * <p>A job represents a long-running functional service operation that progresses
 * through multiple phases. Jobs track their state (NOTSTARTED, INPROGRESS, COMPLETED, FAILED)
 * and the current phase of execution.</p>
 * 
 * <p>This DTO is immutable and includes validation annotations to ensure
 * data integrity.</p>
 * 
 * @see JobState
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public record JobDTO(
    /**
     * Unique identifier for the job.
     */
    @NotNull
    UUID id,
    
    /**
     * ID of the environment that created this job.
     */
    @NotNull
    UUID environmentId,
    
    /**
     * Name of the functional service this job belongs to.
     */
    @NotBlank
    String serviceName,
    
    /**
     * Zone ID for this job.
     */
    @NotBlank
    String zoneId,
    
    /**
     * Optional context ID for this job.
     */
    String contextId,
    
    /**
     * Current state of the job.
     */
    @NotNull
    JobState state,
    
    /**
     * Timestamp when the job was created.
     */
    @NotNull
    Instant createdAt,
    
    /**
     * Timestamp when the job was last updated.
     */
    Instant updatedAt,
    
    /**
     * Current phase of job execution (provider-specific).
     */
    String currentPhase
) {
}
