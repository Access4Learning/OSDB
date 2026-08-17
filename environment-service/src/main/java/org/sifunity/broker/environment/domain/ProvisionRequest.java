// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a provision request in the SIF Unity Broker.
 *
 * <p>A provision request captures a consumer's request to extend its per-zone,
 * per-context, per-service access rights. The {@code provisionedZonesPayload}
 * stores the full request/resolution payload as JSONB. The
 * {@code completionStatus} is {@code null} until the request is resolved.</p>
 *
 * @see CompletionStatus
 */
@Entity
@Table(name = "provision_requests")
public class ProvisionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_status")
    private CompletionStatus completionStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provisioned_zones_payload", columnDefinition = "jsonb", nullable = false)
    private String provisionedZonesPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(UUID environmentId) { this.environmentId = environmentId; }

    public CompletionStatus getCompletionStatus() { return completionStatus; }
    public void setCompletionStatus(CompletionStatus completionStatus) { this.completionStatus = completionStatus; }

    public String getProvisionedZonesPayload() { return provisionedZonesPayload; }
    public void setProvisionedZonesPayload(String provisionedZonesPayload) { this.provisionedZonesPayload = provisionedZonesPayload; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
