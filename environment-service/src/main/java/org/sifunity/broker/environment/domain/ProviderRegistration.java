// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SupportedRequestType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a provider service registration.
 * 
 * <p>A provider registration defines a service offering by a provider environment,
 * including the endpoint, SIF 3.7 service type, and structured
 * supported request types with per-request-type operation granularity.</p>
 * 
 * <p>The combination of environment and context_service_uuid must be unique
 * (enforced by database constraint) — only one provider per environment/context-service.</p>
 * 
 * @see ServiceType
 * @see SupportedRequestType
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@Entity
@Table(name = "provider_registry", uniqueConstraints = @UniqueConstraint(columnNames = {"environment_id", "context_service_uuid"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderRegistration {
    
    /**
     * Unique identifier for the provider registration.
     * Generated as UUID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * The environment this provider registration belongs to.
     * Many provider registrations can belong to one environment.
     * Lazy loading for performance.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;
    
    /**
     * Reference to the context_services entry that identifies the service and context.
     * Replaces the previous serviceName, zoneId, contextId fields.
     */
    @Column(name = "context_service_uuid", nullable = false)
    private UUID contextServiceUuid;
    
    /**
     * Endpoint URL where the service can be accessed.
     * Must be a valid URL format.
     */
    @Column(name = "endpoint", nullable = true)
    private String endpoint;
    
    /**
     * Classification of the service being provided.
     * Maps to the SIF 3.7 {@code serviceType} values:
     * UTILITY, OBJECT, FUNCTIONAL, SERVICEPATH, XQUERYTEMPLATE, SERVICE.
     */
    @Column(name = "service_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;
    
    /**
     * Structured list of supported request types, each mapping a request type
     * (IMMEDIATE or DELAYED) to the set of CRUD operations the provider supports.
     * Stored as JSONB in PostgreSQL via Hibernate's {@code @JdbcTypeCode(SqlTypes.JSON)}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_request_types", nullable = false, columnDefinition = "jsonb")
    private List<SupportedRequestType> supportedRequestTypes;
    
    /**
     * Timestamp when the provider registration was created.
     * Set automatically during registration.
     */
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
    
    /**
     * Sets the registration timestamp to current time.
     * Called automatically before persisting new entities.
     */
    @PrePersist
    protected void onCreate() {
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
    }
    
    /**
     * Sets the environment for this provider registration.
     * Used for maintaining bidirectional relationships.
     * 
     * @param environment the environment to set
     */
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    // Explicit getters and setters for all fields to work around Lombok issues
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public Environment getEnvironment() {
        return environment;
    }
    
    public UUID getContextServiceUuid() {
        return contextServiceUuid;
    }
    
    public void setContextServiceUuid(UUID contextServiceUuid) {
        this.contextServiceUuid = contextServiceUuid;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    
    public ServiceType getServiceType() {
        return serviceType;
    }
    
    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }
    
    public List<SupportedRequestType> getSupportedRequestTypes() {
        return supportedRequestTypes;
    }
    
    public void setSupportedRequestTypes(List<SupportedRequestType> supportedRequestTypes) {
        this.supportedRequestTypes = supportedRequestTypes;
    }
    
    public Instant getRegisteredAt() {
        return registeredAt;
    }
    
    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }
}
