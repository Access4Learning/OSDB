// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a tag attached to an entity within a solution.
 *
 * <p>Tags are solution-scoped key-value metadata. The entity_type + entity_id
 * form a polymorphic reference to the tagged entity (no FK on entity_id).
 * The unique constraint (solution_id, entity_type, entity_id, tag_key) prevents
 * duplicate tags.</p>
 */
@Entity
@Table(name = "entity_tags")
public class EntityTag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "solution_id", nullable = false)
    private UUID solutionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "tag_key", nullable = false, length = 255)
    private String tagKey;

    @Column(name = "tag_value", length = 1024)
    private String tagValue;

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

    public UUID getSolutionId() { return solutionId; }
    public void setSolutionId(UUID solutionId) { this.solutionId = solutionId; }

    public EntityType getEntityType() { return entityType; }
    public void setEntityType(EntityType entityType) { this.entityType = entityType; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getTagKey() { return tagKey; }
    public void setTagKey(String tagKey) { this.tagKey = tagKey; }

    public String getTagValue() { return tagValue; }
    public void setTagValue(String tagValue) { this.tagValue = tagValue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
