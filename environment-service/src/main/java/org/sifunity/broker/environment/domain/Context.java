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
 * JPA entity representing a context within a zone in the SIF Unity Broker.
 * 
 * <p>A context provides additional routing dimension for message delivery within a zone.
 * Contexts allow for finer-grained organization of services and data routing.</p>
 * 
 * <p>The combination of zone_id and context_id must be unique (enforced by database constraint).
 * The context_id must follow the naming pattern (alphanumeric characters, underscores, 
 * and hyphens, starting with alphanumeric).</p>
 * 
 * <p>This entity owns the contexts table and has a foreign key relationship to zones.</p>
 * 
 * @see Zone
 */
@Entity
@Table(name = "contexts")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Context {
    
    /**
     * Unique identifier for the context.
     * Generated as UUID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Zone UUID - references the parent zone's UUID primary key.
     * Used for database-enforced referential integrity via FK constraint.
     * Part of the composite unique constraint.
     */
    @Column(name = "zone_uuid", nullable = false)
    private UUID zoneUuid;
    
    /**
     * Context identifier within the zone.
     * Must be unique within the zone (composite unique constraint with zone_id).
     * Must follow naming pattern: alphanumeric, underscore, hyphen, starting with alphanumeric.
     */
    @Column(name = "context_id", nullable = false)
    private String contextId;
    
    /**
     * Optional description of the context.
     * Used for documentation and display purposes.
     */
    @Column(name = "description")
    private String description;
    
    /**
     * Timestamp when the context was created.
     * Set automatically during context creation.
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
    
    public UUID getZoneUuid() { return zoneUuid; }
    public void setZoneUuid(UUID zoneUuid) { this.zoneUuid = zoneUuid; }
    
    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}