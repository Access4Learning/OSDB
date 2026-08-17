// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.Zone;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Zone entities.
 * 
 * <p>Provides data access operations for the zones table including
 * standard CRUD operations and custom query methods for zone management
 * and validation.</p>
 * 
 * <p>This repository supports the following key operations:</p>
 * <ul>
 *   <li>Finding zones by zone ID for lookup and validation</li>
 *   <li>Checking zone existence for uniqueness validation</li>
 *   <li>Supporting zone management operations</li>
 * </ul>
 * 
 * <p>The unique constraint on zone_id is enforced at the database level.</p>
 * 
 * @see Zone
 */
@Repository
public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    
    /**
     * Finds all zones within a specific solution.
     *
     * @param solutionUuid the solution UUID to filter by (must not be null)
     * @return List of zones in the solution (empty list if none found)
     */
    java.util.List<Zone> findBySolutionUuid(UUID solutionUuid);
    
    /**
     * Checks if a zone with the given solution UUID and zone ID exists.
     *
     * @param solutionUuid the solution UUID (must not be null)
     * @param zoneId the zone ID (must not be null)
     * @return true if a zone with this solution UUID and zone ID exists, false otherwise
     */
    boolean existsBySolutionUuidAndZoneId(UUID solutionUuid, String zoneId);

    /**
     * Finds a zone by its solution UUID and zone ID.
     * This is the correct solution-scoped lookup since zone_id is only unique
     * within a solution (constraint: uk_solution_uuid_zone).
     *
     * @param solutionUuid the solution UUID (must not be null)
     * @param zoneId the zone ID (must not be null)
     * @return Optional containing the zone if found, empty otherwise
     */
    Optional<Zone> findBySolutionUuidAndZoneId(UUID solutionUuid, String zoneId);

    /**
     * Deletes all zones within a specific solution.
     * Used for application-level cascade deletion fallback.
     *
     * @param solutionUuid the solution UUID whose zones should be deleted
     */
    void deleteBySolutionUuid(UUID solutionUuid);
}