// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Queue entity representing a message queue in the system.
 * Stores queue metadata in PostgreSQL.
 * Aligned with SIF 3.7 queue object fields.
 */
@Entity
@Table(name = "queues")
public class Queue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, name = "owner_id")
    private UUID ownerId;  // Environment ID
    
    @Column(name = "solution_uuid")
    private UUID solutionUuid;
    
    @Column(name = "domain_id")
    private String domainId;
    
    @Column(name = "solution_id")
    private String solutionId;
    
    @Column(nullable = false, length = 20)
    private String polling;
    
    @Column(name = "queue_uri", length = 512)
    private String queueUri;
    
    @Column(nullable = false, name = "max_concurrent_connections")
    private Integer maxConcurrentConnections;
    
    @Column(nullable = false)
    private Instant created;

    @Column(name = "last_accessed")
    private Instant lastAccessed;
    
    @Column(name = "last_modified")
    private Instant lastModified;
    
    @Column(nullable = false, name = "max_retries")
    private Integer maxRetries;
    
    @Column(nullable = false, name = "retry_delay_ms")
    private Long retryDelayMs;
    
    /**
     * Set default values before persisting the entity
     */
    @PrePersist
    protected void onCreate() {
        if (created == null) {
            created = Instant.now();
        }
        if (polling == null) {
            polling = "IMMEDIATE";
        }
        if (maxConcurrentConnections == null) {
            maxConcurrentConnections = 1;
        }
        if (maxRetries == null) {
            maxRetries = 3;
        }
        if (retryDelayMs == null) {
            retryDelayMs = 5000L;
        }
    }
    
    // Constructors
    public Queue() {
    }
    
    public Queue(UUID id, String name, UUID ownerId, String polling, String queueUri,
                 Integer maxConcurrentConnections, Instant created, Instant lastAccessed,
                 Instant lastModified, Integer maxRetries, Long retryDelayMs, UUID solutionUuid,
                 String domainId, String solutionId) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.solutionUuid = solutionUuid;
        this.domainId = domainId;
        this.solutionId = solutionId;
        this.polling = polling;
        this.queueUri = queueUri;
        this.maxConcurrentConnections = maxConcurrentConnections;
        this.created = created;
        this.lastAccessed = lastAccessed;
        this.lastModified = lastModified;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public UUID getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }
    
    public UUID getSolutionUuid() {
        return solutionUuid;
    }
    
    public void setSolutionUuid(UUID solutionUuid) {
        this.solutionUuid = solutionUuid;
    }
    
    public String getDomainId() {
        return domainId;
    }
    
    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }
    
    public String getSolutionId() {
        return solutionId;
    }
    
    public void setSolutionId(String solutionId) {
        this.solutionId = solutionId;
    }
    
    public String getPolling() {
        return polling;
    }
    
    public void setPolling(String polling) {
        this.polling = polling;
    }
    
    public String getQueueUri() {
        return queueUri;
    }
    
    public void setQueueUri(String queueUri) {
        this.queueUri = queueUri;
    }
    
    public Integer getMaxConcurrentConnections() {
        return maxConcurrentConnections;
    }
    
    public void setMaxConcurrentConnections(Integer maxConcurrentConnections) {
        this.maxConcurrentConnections = maxConcurrentConnections;
    }
    
    public Instant getCreated() {
        return created;
    }
    
    public void setCreated(Instant created) {
        this.created = created;
    }
    
    public Instant getLastAccessed() {
        return lastAccessed;
    }
    
    public void setLastAccessed(Instant lastAccessed) {
        this.lastAccessed = lastAccessed;
    }
    
    public Instant getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }
    
    public Integer getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public Long getRetryDelayMs() {
        return retryDelayMs;
    }
    
    public void setRetryDelayMs(Long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID id;
        private String name;
        private UUID ownerId;
        private UUID solutionUuid;
        private String domainId;
        private String solutionId;
        private String polling;
        private String queueUri;
        private Integer maxConcurrentConnections;
        private Instant created;
        private Instant lastAccessed;
        private Instant lastModified;
        private Integer maxRetries;
        private Long retryDelayMs;
        
        public Builder id(UUID id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder ownerId(UUID ownerId) {
            this.ownerId = ownerId;
            return this;
        }
        
        public Builder solutionUuid(UUID solutionUuid) {
            this.solutionUuid = solutionUuid;
            return this;
        }
        
        public Builder domainId(String domainId) {
            this.domainId = domainId;
            return this;
        }
        
        public Builder solutionId(String solutionId) {
            this.solutionId = solutionId;
            return this;
        }
        
        public Builder polling(String polling) {
            this.polling = polling;
            return this;
        }
        
        public Builder queueUri(String queueUri) {
            this.queueUri = queueUri;
            return this;
        }
        
        public Builder maxConcurrentConnections(Integer maxConcurrentConnections) {
            this.maxConcurrentConnections = maxConcurrentConnections;
            return this;
        }
        
        public Builder created(Instant created) {
            this.created = created;
            return this;
        }
        
        public Builder lastAccessed(Instant lastAccessed) {
            this.lastAccessed = lastAccessed;
            return this;
        }
        
        public Builder lastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }
        
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder retryDelayMs(Long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
            return this;
        }
        
        public Queue build() {
            return new Queue(id, name, ownerId, polling, queueUri, maxConcurrentConnections,
                           created, lastAccessed, lastModified, maxRetries, retryDelayMs, solutionUuid,
                           domainId, solutionId);
        }
    }
}
