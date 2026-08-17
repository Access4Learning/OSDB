// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.Context;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Context entities.
 * 
 * <p>Provides data access operations for the contexts table including
 * standard CRUD operations and custom query methods for context management
 * and validation within zones.</p>
 * 
 * <p>This repository supports the following key operations:</p>
 * <ul>
 *   <li>Finding contexts by zone ID for listing operations</li>
 *   <li>Finding specific contexts by zone ID and context ID for lookup</li>
 *   <li>Checking context existence for uniqueness validation</li>
 *   <li>Supporting context management operations within zones</li>
 * </ul>
 * 
 * <p>The unique constraint on contexts is enforced at the database level
 * on the combination of (zone_id, context_id).</p>
 * 
 * @see Context
 * @see Zone
 */
@Repository
public interface ContextRepository extends JpaRepository<Context, UUID> {
    
    /**
     * Finds all contexts within a specific zone.
     *
     * @param zoneUuid the zone UUID to search for (must not be null)
     * @return List of contexts within the zone (empty list if none found)
     */
    List<Context> findByZoneUuid(UUID zoneUuid);
    
    /**
     * Finds a specific context by zone UUID and context ID.
     *
     * @param zoneUuid the zone UUID to search for (must not be null)
     * @param contextId the context ID to search for (must not be null)
     * @return Optional containing the context if found, empty otherwise
     */
    Optional<Context> findByZoneUuidAndContextId(UUID zoneUuid, String contextId);
    
    /**
     * Checks if a context exists with the specified zone UUID and context ID.
     *
     * @param zoneUuid the zone UUID to check (must not be null)
     * @param contextId the context ID to check (must not be null)
     * @return true if a context with these parameters exists, false otherwise
     */
    boolean existsByZoneUuidAndContextId(UUID zoneUuid, String contextId);

    /**
     * Deletes all contexts within a specific zone.
     * Used for application-level cascade deletion fallback.
     *
     * @param zoneUuid the zone UUID whose contexts should be deleted
     */
    void deleteByZoneUuid(UUID zoneUuid);
}