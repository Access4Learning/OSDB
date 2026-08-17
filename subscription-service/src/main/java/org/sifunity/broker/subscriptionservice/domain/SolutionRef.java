// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.domain;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Read-only JPA entity mapped to the {@code solutions} table.
 *
 * <p>Used by the subscription-service purely as a JPQL join target to scope
 * tenant-sensitive lookups by (domainId, solutionId). A {@code solutionId} is
 * unique only within a domain (uk_domain_uuid_solution), so both the parent
 * {@code domainUuid} and the {@code solutionId} string are needed to identify
 * a tenant's solution.</p>
 */
@Entity
@Immutable
@Table(name = "solutions")
public class SolutionRef {

    @Id
    private UUID id;

    @Column(name = "domain_uuid", nullable = false)
    private UUID domainUuid;

    @Column(name = "solution_id", nullable = false)
    private String solutionId;

    protected SolutionRef() {}

    public UUID getId() { return id; }
    public UUID getDomainUuid() { return domainUuid; }
    public String getSolutionId() { return solutionId; }
}
