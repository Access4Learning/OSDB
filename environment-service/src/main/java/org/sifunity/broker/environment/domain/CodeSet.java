// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sifunity.broker.environment.dto.CodeItemsWrapper;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a code set in the SIF 3.7 Code Sets Registry Utility Service.
 *
 * <p>A code set is a named collection of legal code values that consumers and providers
 * can verify against. Code sets support zone-scoping: global entries
 * ({@code zone = "environment-global"}) can be overridden by zone-specific entries
 * sharing the same {@code codeSetId}.</p>
 *
 * <p>Maps to the {@code code_sets} table created by Flyway migration V29.</p>
 *
 * @see CodeItemsWrapper
 */
@Entity
@Table(name = "code_sets")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSet {

    /**
     * Internal primary key (broker-assigned UUID).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid")
    private UUID uuid;

    /**
     * User-provided code set identifier (max 128 chars).
     * If not provided on create, a UUID is generated.
     */
    @Column(name = "code_set_id", nullable = false, length = 128)
    private String codeSetId;

    /**
     * Zone this code set belongs to.
     * {@code "environment-global"} for global entries; any other value for zone-specific.
     */
    @Column(nullable = false)
    private String zone;

    /**
     * Version identifier for this code set (e.g., "2024.1").
     */
    @Column(nullable = false)
    private String version;

    /**
     * Timestamp string indicating when this code set was last modified by the source.
     */
    @Column(nullable = false)
    private String timestamp;

    /**
     * Optional URI indicating the external source of this code set.
     */
    @Column(length = 2048)
    private String source;

    /**
     * Code items stored as JSONB.
     * Contains the array of code item entries with nested alias structures.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "code_items", columnDefinition = "jsonb")
    private CodeItemsWrapper codeItems;

    /**
     * UUID of the solution this code set belongs to.
     */
    @Column(name = "solution_uuid", nullable = false)
    private UUID solutionUuid;

    /**
     * Timestamp when the code set was persisted (UTC).
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Timestamp when the code set was last updated (UTC).
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

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getCodeSetId() { return codeSetId; }
    public void setCodeSetId(String codeSetId) { this.codeSetId = codeSetId; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public CodeItemsWrapper getCodeItems() { return codeItems; }
    public void setCodeItems(CodeItemsWrapper codeItems) { this.codeItems = codeItems; }

    public UUID getSolutionUuid() { return solutionUuid; }
    public void setSolutionUuid(UUID solutionUuid) { this.solutionUuid = solutionUuid; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
