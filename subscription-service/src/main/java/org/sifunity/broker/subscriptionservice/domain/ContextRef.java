// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.domain;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Read-only JPA entity mapped to the {@code contexts} table.
 *
 * <p>Used by the subscription-service to resolve context_uuid → contextId string
 * and to navigate to the parent zone for zoneId resolution.</p>
 */
@Entity
@Immutable
@Table(name = "contexts")
public class ContextRef {

    @Id
    private UUID id;

    @Column(name = "context_id", nullable = false)
    private String contextId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_uuid", insertable = false, updatable = false)
    private ZoneRef zone;

    protected ContextRef() {}

    public UUID getId() { return id; }
    public String getContextId() { return contextId; }
    public ZoneRef getZone() { return zone; }
}
