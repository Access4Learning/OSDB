// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.SolutionService;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for SolutionService entities.
 * 
 * <p>Provides data access operations for the solution_services table including
 * standard CRUD operations and custom query methods for solution service
 * catalog management.</p>
 * 
 * @see SolutionService
 */
@Repository
public interface SolutionServiceRepository extends JpaRepository<SolutionService, UUID> {

    /**
     * Finds all solution services within a specific solution.
     * 
     * @param solutionId the solution UUID to filter by
     * @return List of solution services within the solution (empty list if none found)
     */
    List<SolutionService> findBySolutionId(UUID solutionId);

    /**
     * Checks if a solution service exists with the specified solution UUID, service name, and service type.
     * 
     * @param solutionId the solution UUID to check
     * @param serviceName the service name to check
     * @param serviceType the service type to check
     * @return true if a solution service with these parameters exists, false otherwise
     */
    boolean existsBySolutionIdAndServiceNameAndServiceType(UUID solutionId, String serviceName, String serviceType);

    /**
     * Deletes solution services matching the specified solution UUID, service name, and service type.
     * 
     * @param solutionId the solution UUID
     * @param serviceName the service name
     * @param serviceType the service type
     */
    void deleteBySolutionIdAndServiceNameAndServiceType(UUID solutionId, String serviceName, String serviceType);
}
