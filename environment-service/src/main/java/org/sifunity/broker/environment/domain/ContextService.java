// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a context_service record.
 * 
 * <p>A context_service defines which services are available in a given context.
 * It normalizes the relationship between services and contexts, serving as the
 * single source of truth referenced by both {@code acl_entries} and
 * {@code provider_registry} via foreign key.</p>
 * 
 * <p>The combination of (context_uuid, service_name) must be unique
 * (enforced by database constraint).</p>
 * 
 * @see Context
 */
@Entity
@Table(name = "context_services")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextService {

    /**
     * Unique identifier for the context service.
     * Generated as UUID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * UUID referencing the parent context.
     * Foreign key to contexts(id) with ON DELETE CASCADE at the database level.
     * Part of the composite unique constraint (context_uuid, service_name).
     */
    @Column(name = "context_uuid", nullable = false)
    private UUID contextUuid;

    /**
     * Name of the service available in this context.
     * Part of the composite unique constraint (context_uuid, service_name).
     */
    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    /**
     * Type of the service. Must be one of: UTILITY, OBJECT, FUNCTIONAL,
     * SERVICEPATH, XQUERYTEMPLATE, SERVICE.
     * Enforced by a CHECK constraint at the database level.
     */
    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType;

    /**
     * Timestamp when the context service was created.
     * Set automatically during entity creation.
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

    public UUID getContextUuid() { return contextUuid; }
    public void setContextUuid(UUID contextUuid) { this.contextUuid = contextUuid; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
