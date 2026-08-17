// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a zone in the SIF Unity Broker.
 * 
 * <p>A zone provides logical grouping of services and data within the broker.
 * Zones are used for organizing and routing messages to appropriate services.</p>
 * 
 * <p>The zone_id must be unique across all zones and follow the naming pattern
 * (alphanumeric characters, underscores, and hyphens, starting with alphanumeric).</p>
 * 
 * <p>This entity owns the zones table.</p>
 * 
 * @see Context
 */
@Entity
@Table(name = "zones")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone {
    
    /**
     * Unique identifier for the zone.
     * Generated as UUID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Solution UUID - references the parent solution's UUID primary key.
     * Used for database-enforced referential integrity via FK constraint.
     */
    @Column(name = "solution_uuid", nullable = false)
    private UUID solutionUuid;
    
    /**
     * Zone identifier - must be unique within a solution.
     * Used for identification and routing purposes.
     * Must follow naming pattern: alphanumeric, underscore, hyphen, starting with alphanumeric.
     */
    @Column(name = "zone_id", nullable = false)
    private String zoneId;
    
    /**
     * Optional description of the zone.
     * Used for documentation and display purposes.
     */
    @Column(name = "description")
    private String description;
    
    /**
     * Timestamp when the zone was created.
     * Set automatically during zone creation.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    /**
     * Sets the created timestamp to current time.
     * Called automatically before persisting new entities.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getSolutionUuid() { return solutionUuid; }
    public void setSolutionUuid(UUID solutionUuid) { this.solutionUuid = solutionUuid; }
    
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}