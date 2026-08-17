// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.domain;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only JPA entity mapped to the {@code context_services} table.
 *
 * <p>Used by the subscription-service to resolve context_service_uuid → serviceName
 * and to navigate to the parent context for contextId/zoneId resolution.</p>
 */
@Entity
@Immutable
@Table(name = "context_services")
public class ContextServiceRef {

    @Id
    private UUID id;

    @Column(name = "context_uuid", nullable = false)
    private UUID contextUuid;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "service_type", nullable = false)
    private String serviceType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "context_uuid", insertable = false, updatable = false)
    private ContextRef context;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContextServiceRef() {}

    public UUID getId() { return id; }
    public UUID getContextUuid() { return contextUuid; }
    public String getServiceName() { return serviceName; }
    public String getServiceType() { return serviceType; }
    public ContextRef getContext() { return context; }
}
