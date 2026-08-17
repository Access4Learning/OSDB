// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.domain;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Read-only JPA entity mapped to the {@code domains} table.
 *
 * <p>Used by the subscription-service purely as a JPQL join target to scope
 * tenant-sensitive lookups by the {@code domainId} string supplied in the
 * request path (see {@code /{domainId}/{solutionId}/subscriptions}).</p>
 */
@Entity
@Immutable
@Table(name = "domains")
public class DomainRef {

    @Id
    private UUID id;

    @Column(name = "domain_id", nullable = false)
    private String domainId;

    protected DomainRef() {}

    public UUID getId() { return id; }
    public String getDomainId() { return domainId; }
}
