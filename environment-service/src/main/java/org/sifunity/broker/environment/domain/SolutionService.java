// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a service catalog entry within a solution.
 * 
 * <p>Defines which service name + type combinations are available within a solution.
 * Administrators use this catalog when provisioning zones, creating ACL entries,
 * or registering providers — it provides a selectable list of services.</p>
 * 
 * <p>This is a broker-specific extension (not defined in the SIF 3.7 XSD).
 * Mirrors the legacy schema's {@code solutionServices} table.</p>
 * 
 * @see Solution
 */
@Entity
@Table(name = "solution_services",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_solution_service_name_type",
           columnNames = {"solution_id", "service_name", "service_type"}
       ))
public class SolutionService {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * UUID of the owning solution.
     */
    @Column(name = "solution_id", nullable = false)
    private UUID solutionId;
    
    /**
     * Name of the service (e.g., "StudentPersonal", "SchoolInfo", "queues").
     */
    @Column(name = "service_name", nullable = false)
    private String serviceName;
    
    /**
     * Type of the service per SIF 3.7 serviceTypeType.
     * Values: UTILITY, OBJECT, FUNCTIONAL, SERVICEPATH, XQUERYTEMPLATE, SERVICE.
     */
    @Column(name = "service_type", nullable = false)
    private String serviceType;

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
    
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
