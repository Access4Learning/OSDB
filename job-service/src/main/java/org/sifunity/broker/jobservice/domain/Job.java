// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sifunity.broker.common.constant.JobState;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a Job in the Job Service.
 * 
 * A Job represents a long-running Functional Service process initiated by consumers
 * and executed by providers. Jobs track lifecycle, state transitions, and phase progression.
 * 
 * @see JobState
 */
@Entity
@Table(name = "jobs")
public class Job {
    
    /**
     * Unique identifier for the job.
     */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    
    /**
     * Environment ID that owns this job.
     */
    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;
    
    /**
     * Name of the functional service this job belongs to.
     */
    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;
    
    /**
     * Zone ID for routing purposes.
     */
    @Column(name = "zone_id", nullable = false, length = 255)
    private String zoneId;
    
    /**
     * Optional context ID for additional routing information.
     */
    @Column(name = "context_id", length = 255)
    private String contextId;
    
    /**
     * Current state of the job.
     * Valid states: NOTSTARTED, INPROGRESS, COMPLETED, FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private JobState state;
    
    /**
     * Timestamp when the job was created.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;
    
    /**
     * Timestamp when the job was last updated.
     */
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;
    
    /**
     * Name of the current phase being executed.
     */
    @Column(name = "current_phase", length = 100)
    private String currentPhase;
    
    /**
     * Custom metadata stored as JSONB.
     * Maximum size: 10KB
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    /**
     * Version field for optimistic locking.
     * Prevents concurrent modification conflicts.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
    
    // Constructors
    
    /**
     * Default constructor required by JPA.
     */
    public Job() {
    }
    
    /**
     * Constructor with required fields.
     * 
     * @param id unique identifier
     * @param environmentId environment that owns this job
     * @param serviceName name of the functional service
     * @param zoneId zone ID for routing
     * @param state initial state
     * @param createdAt creation timestamp
     * @param updatedAt last update timestamp
     */
    public Job(UUID id, UUID environmentId, String serviceName, String zoneId, 
               JobState state, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.environmentId = environmentId;
        this.serviceName = serviceName;
        this.zoneId = zoneId;
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
    
    public Integer getVersion() {
        return version;
    }
    
    public void setVersion(Integer version) {
        this.version = version;
    }
    
    // toString, equals, and hashCode
    
    @Override
    public String toString() {
        return "Job{" +
                "id=" + id +
                ", environmentId=" + environmentId +
                ", serviceName='" + serviceName + '\'' +
                ", zoneId='" + zoneId + '\'' +
                ", contextId='" + contextId + '\'' +
                ", state=" + state +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", currentPhase='" + currentPhase + '\'' +
                ", version=" + version +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Job job = (Job) o;
        
        return id != null && id.equals(job.id);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
