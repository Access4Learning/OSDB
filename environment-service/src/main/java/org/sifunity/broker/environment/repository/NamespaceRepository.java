// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.Namespace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Namespace entities.
 *
 * <p>Provides data access operations for the namespaces table including
 * standard CRUD operations and custom query methods for solution-scoped
 * namespace retrieval and duplicate checking.</p>
 *
 * <p>Key operations:</p>
 * <ul>
 *   <li>Finding all namespaces by solution UUID for solution-scoped queries</li>
 *   <li>Finding a single namespace by URI, zone, and solution UUID for duplicate detection</li>
 * </ul>
 *
 * @see Namespace
 */
@Repository
public interface NamespaceRepository extends JpaRepository<Namespace, UUID> {

    /**
     * Finds all namespaces belonging to a specific solution.
     *
     * <p>Used for bulk queries where all namespaces within a solution
     * are retrieved before applying zone-scoping logic.</p>
     *
     * @param solutionUuid the solution UUID to filter by
     * @return list of namespaces belonging to the solution
     */
    List<Namespace> findBySolutionUuid(UUID solutionUuid);

    /**
     * Finds a single namespace by its URI, zone, and solution UUID.
     *
     * <p>Used for duplicate checking on create and update.
     * The combination of (uri, zone, solutionUuid) is unique per the
     * database constraint {@code uk_namespaces_uri_zone_solution}.</p>
     *
     * @param uri          the namespace URI
     * @param zone         the zone this namespace belongs to
     * @param solutionUuid the solution UUID for scoping
     * @return Optional containing the namespace if found, empty otherwise
     */
    Optional<Namespace> findByUriAndZoneAndSolutionUuid(String uri, String zone, UUID solutionUuid);
}
