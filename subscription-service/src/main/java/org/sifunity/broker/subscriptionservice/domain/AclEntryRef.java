// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.domain;

import org.hibernate.annotations.Immutable;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Read-only JPA entity mapped to the {@code acl_entries} table.
 *
 * <p>The subscription-service only reads from this table — the environment-service
 * owns the ACL entry lifecycle. This entity is intentionally minimal, containing
 * only the fields needed for subscription creation and query resolution.</p>
 *
 * @see org.sifunity.broker.common.constant.AccessType
 * @see org.sifunity.broker.common.constant.AccessLevel
 */
@Entity
@Immutable
@Table(name = "acl_entries")
public class AclEntryRef {

    /**
     * Unique identifier for the ACL entry.
     */
    @Id
    private UUID id;

    /**
     * UUID referencing the context_service this ACL entry applies to.
     * The context_service holds the service_name and context_uuid.
     */
    @Column(name = "context_service_uuid", nullable = false)
    private UUID contextServiceUuid;

    /**
     * Read-only reference to the context_service entity for resolving serviceName/contextUuid.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "context_service_uuid", insertable = false, updatable = false)
    private ContextServiceRef contextService;

    /**
     * UUID of the environment that owns this ACL entry.
     * Read-only — the environment-service manages this field.
     */
    @Column(name = "environment_id", nullable = false, updatable = false, insertable = false)
    private UUID environmentId;

    /**
     * Type of access right (SUBSCRIBE, PROVIDE, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false)
    private AccessType accessType;

    /**
     * Access right value (APPROVED, SUPPORTED, REJECTED, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    private AccessLevel accessLevel;

    protected AclEntryRef() {
        // JPA requires a no-arg constructor
    }

    public UUID getId() {
        return id;
    }

    public String getServiceName() {
        return contextService != null ? contextService.getServiceName() : null;
    }

    public UUID getContextUuid() {
        return contextService != null ? contextService.getContextUuid() : null;
    }

    public UUID getContextServiceUuid() {
        return contextServiceUuid;
    }

    /**
     * Returns the read-only context_service entity reference.
     */
    public ContextServiceRef getContextService() {
        return contextService;
    }

    /**
     * Resolves the zoneId string by navigating contextService → context → zone.
     * Returns null if contextService or context is null.
     */
    public String resolveZoneId() {
        if (contextService == null || contextService.getContext() == null) return null;
        return contextService.getContext().getZone() != null
                ? contextService.getContext().getZone().getZoneId() : null;
    }

    /**
     * Resolves the contextId string from the context entity.
     * Returns null if contextService or context is null.
     */
    public String resolveContextId() {
        if (contextService == null || contextService.getContext() == null) return null;
        return contextService.getContext().getContextId();
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public AccessType getAccessType() {
        return accessType;
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }
}
