// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a namespace in the SIF 3.7 Namespaces Registry Utility Service.
 *
 * <p>A namespace maps an XML namespace URI to its corresponding schema location URL.
 * Namespaces support zone-scoping: global entries ({@code zone = "environment-global"})
 * can be overridden by zone-specific entries sharing the same {@code uri}.</p>
 *
 * <p>Unlike code sets, namespaces are flat objects with no nested child objects (no JSONB).
 * The {@code id} is a broker-assigned UUID (not user-provided).</p>
 *
 * <p>Maps to the {@code namespaces} table created by Flyway migration V30.</p>
 */
@Entity
@Table(name = "namespaces")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Namespace {

    /**
     * Broker-assigned UUID primary key.
     * Unlike code sets (where id is user-provided), namespace id is always
     * broker-generated. This column serves as both the PK and the API-visible id.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    /**
     * Zone this namespace belongs to.
     * {@code "environment-global"} for global entries; any other value for zone-specific.
     */
    @Column(nullable = false)
    private String zone;

    /**
     * The namespace URI (e.g., "http://www.sifassociation.org/datamodel/na/4.x").
     * Max 2048 characters per SIF 3.7 schema.
     */
    @Column(nullable = false, length = 2048)
    private String uri;

    /**
     * The schema location URL for this namespace URI.
     * Max 2048 characters per SIF 3.7 schema.
     */
    @Column(nullable = false, length = 2048)
    private String url;

    /**
     * UUID of the solution this namespace belongs to.
     */
    @Column(name = "solution_uuid", nullable = false)
    private UUID solutionUuid;

    /**
     * Timestamp when the namespace was persisted (UTC).
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Timestamp when the namespace was last updated (UTC).
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Sets created and updated timestamps to current time if not already set.
     * Called automatically before persisting new entities.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /**
     * Updates the updated timestamp to current time.
     * Called automatically before updating existing entities.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public UUID getSolutionUuid() { return solutionUuid; }
    public void setSolutionUuid(UUID solutionUuid) { this.solutionUuid = solutionUuid; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
