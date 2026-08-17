// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a tag definition in the tag governance system.
 *
 * <p>Tag definitions are globally-managed templates that enforce naming consistency
 * and optional value constraints on entity tags. They define which entity types
 * a tag can be applied to and optionally restrict the allowed values.</p>
 *
 * <p>The tag_name is stored in uppercase and enforced unique via a functional
 * index on UPPER(tag_name) in PostgreSQL. The @PrePersist and @PreUpdate hooks
 * provide defense-in-depth by uppercasing at the application layer as well.</p>
 *
 * @see EntityTag
 * @see EntityType
 */
@Entity
@Table(name = "tag_definitions")
public class TagDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tag_name", nullable = false, length = 255)
    private String tagName;

    @Column(name = "tag_description", length = 1024)
    private String tagDescription;

    @Column(name = "applicable_entity_types", nullable = false, columnDefinition = "varchar[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> applicableEntityTypes;

    @Column(name = "allowed_values", columnDefinition = "varchar[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> allowedValues;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        tagName = tagName != null ? tagName.toUpperCase() : null;
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        tagName = tagName != null ? tagName.toUpperCase() : null;
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public String getTagDescription() { return tagDescription; }
    public void setTagDescription(String tagDescription) { this.tagDescription = tagDescription; }

    public List<String> getApplicableEntityTypes() { return applicableEntityTypes; }
    public void setApplicableEntityTypes(List<String> applicableEntityTypes) { this.applicableEntityTypes = applicableEntityTypes; }

    public List<String> getAllowedValues() { return allowedValues; }
    public void setAllowedValues(List<String> allowedValues) { this.allowedValues = allowedValues; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
