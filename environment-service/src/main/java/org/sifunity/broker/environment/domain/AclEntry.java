// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;

import java.util.UUID;

/**
 * JPA entity representing an ACL (Access Control List) entry.
 * 
 * <p>An ACL entry defines permissions for an environment to access a specific
 * context_service with a particular access type.</p>
 * 
 * <p>The combination of environment, context_service_uuid, and access type
 * must be unique (enforced by database constraint).</p>
 * 
 * @see org.sifunity.broker.common.constant.AccessType
 * @see org.sifunity.broker.common.constant.AccessLevel
 */
@Entity
@Table(name = "acl_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AclEntry {
    
    /**
     * Unique identifier for the ACL entry.
     * Generated as UUID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * The environment this ACL entry belongs to.
     * Many ACL entries can belong to one environment.
     * Lazy loading for performance.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;
    
    /**
     * UUID referencing the context_service this ACL entry applies to.
     * Foreign key to context_services(id) with ON DELETE CASCADE.
     * Part of the unique constraint (environment_id, context_service_uuid, access_type).
     */
    @Column(name = "context_service_uuid", nullable = false)
    private UUID contextServiceUuid;
    
    /**
     * Type of access right per SIF 3.7 (QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE).
     * Part of the unique constraint.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false)
    private AccessType accessType;
    
    /**
     * Access right value per SIF 3.7 (APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, REQUESTED).
     * Determines whether the permission is granted.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    private AccessLevel accessLevel;
    
    /**
     * Sets the environment for this ACL entry.
     * Used for maintaining bidirectional relationships.
     * 
     * @param environment the environment to set
     */
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    /**
     * Gets the access level for this ACL entry.
     * 
     * @return the access level
     */
    public AccessLevel getAccessLevel() {
        return accessLevel;
    }
    
    // Additional getters and setters needed for tests
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getContextServiceUuid() { return contextServiceUuid; }
    public void setContextServiceUuid(UUID contextServiceUuid) { this.contextServiceUuid = contextServiceUuid; }
    
    public AccessType getAccessType() { return accessType; }
    public void setAccessType(AccessType accessType) { this.accessType = accessType; }
    
    public void setAccessLevel(AccessLevel accessLevel) { this.accessLevel = accessLevel; }
    
    public Environment getEnvironment() { return environment; }
}
