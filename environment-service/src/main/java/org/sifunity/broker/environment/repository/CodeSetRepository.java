// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.CodeSet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for CodeSet entities.
 *
 * <p>Provides data access operations for the code_sets table including
 * standard CRUD operations and custom query methods for solution-scoped
 * code set retrieval and duplicate checking.</p>
 *
 * <p>Key operations:</p>
 * <ul>
 *   <li>Finding all code sets by solution UUID for solution-scoped queries</li>
 *   <li>Finding a single code set by codeSetId, zone, and solution UUID for lookup and duplicate detection</li>
 * </ul>
 *
 * @see CodeSet
 */
@Repository
public interface CodeSetRepository extends JpaRepository<CodeSet, UUID> {

    /**
     * Finds all code sets belonging to a specific solution.
     *
     * <p>Used for bulk queries where all code sets within a solution
     * are retrieved before applying zone-scoping logic.</p>
     *
     * @param solutionUuid the solution UUID to filter by
     * @return list of code sets belonging to the solution
     */
    List<CodeSet> findBySolutionUuid(UUID solutionUuid);

    /**
     * Finds a single code set by its codeSetId, zone, and solution UUID.
     *
     * <p>Used for single code set lookup and duplicate checking on create.
     * The combination of (codeSetId, zone, solutionUuid) is unique per the
     * database constraint {@code uk_code_sets_id_zone_solution}.</p>
     *
     * @param codeSetId    the user-provided code set identifier
     * @param zone         the zone this code set belongs to
     * @param solutionUuid the solution UUID for scoping
     * @return Optional containing the code set if found, empty otherwise
     */
    Optional<CodeSet> findByCodeSetIdAndZoneAndSolutionUuid(String codeSetId, String zone, UUID solutionUuid);
}
