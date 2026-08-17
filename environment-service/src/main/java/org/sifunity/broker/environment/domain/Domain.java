// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a domain in the SIF Unity Broker.
 * 
 * <p>A domain provides the top-level organizational boundary for multi-tenancy,
 * representing a client or tenant (e.g., "State A", "State B"). Domains contain
 * solutions, which in turn contain zones and environments.</p>
 * 
 * <p>The domain_id must be unique across all domains and follow the naming pattern
 * (alphanumeric characters, underscores, and hyphens, starting with alphanumeric).</p>
 * 
 * @see Solution
 */
@Entity
@Table(name = "domains")
public class Domain {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "domain_id", nullable = false, unique = true)
    private String domainId;
    
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
    
    public String getDomainId() { return domainId; }
    public void setDomainId(String domainId) { this.domainId = domainId; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
