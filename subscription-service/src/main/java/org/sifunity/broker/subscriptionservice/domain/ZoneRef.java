// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.domain;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Read-only JPA entity mapped to the {@code zones} table.
 *
 * <p>Used by the subscription-service to resolve zone UUID → zoneId string
 * when building subscription response DTOs, and to scope ACL lookups to the
 * owning solution via {@code solutionUuid} (a zoneId is unique only within a
 * solution — uk_solution_uuid_zone).</p>
 */
@Entity
@Immutable
@Table(name = "zones")
public class ZoneRef {

    @Id
    private UUID id;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "solution_uuid", nullable = false)
    private UUID solutionUuid;

    protected ZoneRef() {}

    public UUID getId() { return id; }
    public String getZoneId() { return zoneId; }
    public UUID getSolutionUuid() { return solutionUuid; }
}
