// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sifunity.broker.common.constant.JobState;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a Job Phase in the Job Service.
 * 
 * A JobPhase represents a named stage within a job's execution with associated state and metadata.
 * Phases track detailed progress within a job and follow the same state transition rules as jobs.
 * 
 * @see JobState
 * @see Job
 */
@Entity
@Table(
    name = "job_phases",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_job_phase",
            columnNames = {"job_id", "phase_name"}
        )
    }
)
public class JobPhase {
    
    /**
     * Unique identifier for the phase.
     */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    
    /**
     * ID of the job this phase belongs to.
     * Foreign key reference to jobs table with cascade delete.
     */
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    
    /**
     * Name of the phase.
     * Must be unique within a job (enforced by unique constraint).
     * Maximum length: 100 characters
     */
    @Column(name = "phase_name", nullable = false, length = 100)
    private String phaseName;
    
    /**
     * Current state of the phase.
     * Valid states: NOTSTARTED, INPROGRESS, COMPLETED, FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private JobState state;
    
    /**
     * Timestamp when the phase was created.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;
    
    /**
     * Timestamp when the phase was last updated.
     */
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;
    
    /**
     * Custom metadata stored as JSONB.
     * Maximum size: 10KB
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    // Constructors
    
    /**
     * Default constructor required by JPA.
     */
    public JobPhase() {
    }
    
    /**
     * Constructor with required fields.
     * 
     * @param id unique identifier
     * @param jobId ID of the parent job
     * @param phaseName name of the phase
     * @param state initial state
     * @param createdAt creation timestamp
     * @param updatedAt last update timestamp
     */
    public JobPhase(UUID id, UUID jobId, String phaseName, JobState state,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.jobId = jobId;
        this.phaseName = phaseName;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    
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
    
    // toString, equals, and hashCode
    
    @Override
    public String toString() {
        return "JobPhase{" +
                "id=" + id +
                ", jobId=" + jobId +
                ", phaseName='" + phaseName + '\'' +
                ", state=" + state +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        JobPhase jobPhase = (JobPhase) o;
        
        return id != null && id.equals(jobPhase.id);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
