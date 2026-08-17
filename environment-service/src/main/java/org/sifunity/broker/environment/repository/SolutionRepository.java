// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.Solution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Solution entities.
 */
@Repository
public interface SolutionRepository extends JpaRepository<Solution, UUID> {
    
    /**
     * Finds all solutions within a specific domain.
     * 
     * @param domainUuid the domain UUID to filter by
     * @return List of solutions in the domain
     */
    List<Solution> findByDomainUuid(UUID domainUuid);
    
    /**
     * Finds a solution by its domain UUID and solution ID.
     * 
     * @param domainUuid the domain UUID
     * @param solutionId the solution ID
     * @return Optional containing the solution if found, empty otherwise
     */
    Optional<Solution> findByDomainUuidAndSolutionId(UUID domainUuid, String solutionId);
    
    /**
     * Checks if a solution with the given domain UUID and solution ID exists.
     * 
     * @param domainUuid the domain UUID
     * @param solutionId the solution ID
     * @return true if the solution exists, false otherwise
     */
    boolean existsByDomainUuidAndSolutionId(UUID domainUuid, String solutionId);
}
