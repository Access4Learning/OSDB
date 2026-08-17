// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a solution within a domain in the SIF Unity Broker.
 * 
 * <p>A solution represents an operating environment within a domain (e.g., "production",
 * "staging", "testing"). Solutions map to SIF 3.3's solutionId concept for separating
 * production from non-production environments.</p>
 * 
 * <p>The combination of domain_id and solution_id must be unique. The solution_id
 * must follow the naming pattern (alphanumeric characters, underscores, and hyphens,
 * starting with alphanumeric).</p>
 * 
 * @see Domain
 * @see Zone
 */
@Entity
@Table(name = "solutions")
public class Solution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "domain_uuid", nullable = false)
    private UUID domainUuid;
    
    @Column(name = "solution_id", nullable = false)
    private String solutionId;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getDomainUuid() { return domainUuid; }
    public void setDomainUuid(UUID domainUuid) { this.domainUuid = domainUuid; }
    
    public String getSolutionId() { return solutionId; }
    public void setSolutionId(String solutionId) { this.solutionId = solutionId; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
