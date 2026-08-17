// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Environment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Environment entities.
 * 
 * <p>Provides data access operations for the environments table including
 * standard CRUD operations and custom query methods for authentication
 * and filtering.</p>
 * 
 * <p>This repository supports the following key operations:</p>
 * <ul>
 *   <li>Finding environments by sessionToken for authentication</li>
 *   <li>Checking application name uniqueness during registration</li>
 *   <li>Filtering environments by type for listing operations</li>
 *   <li>Fetching environments with collections for cascade deletion</li>
 * </ul>
 * 
 * @see Environment
 * @see EnvironmentType
 */
@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {
    
    /**
     * Finds an environment by its session token.
     * 
     * <p>This method is used during authentication to lookup the environment
     * associated with a given sessionToken. The sessionToken is unique across
     * all environments.</p>
     * 
     * <p>Used by:</p>
     * <ul>
     *   <li>AuthenticationService for token validation</li>
     *   <li>Internal authentication API (/internal/validate-token)</li>
     * </ul>
     * 
     * @param sessionToken the session token to search for (must not be null)
     * @return Optional containing the environment if found, empty otherwise
     * @see org.sifunity.broker.environment.service.AuthenticationService#validateToken
     */
    Optional<Environment> findBySessionToken(String sessionToken);
    
    /**
     * Finds an environment by ID with ACL entries eagerly loaded.
     * 
     * <p>This method is used for cascade deletion operations where we need
     * to ensure ACL entries are loaded so that JPA can properly
     * cascade the deletion.</p>
     * 
     * @param id the environment ID to search for (must not be null)
     * @return Optional containing the environment with ACL entries loaded, empty otherwise
     */
    @Query("SELECT e FROM Environment e " +
           "LEFT JOIN FETCH e.aclEntries " +
           "WHERE e.id = :id")
    Optional<Environment> findByIdWithAclEntries(@Param("id") UUID id);
    
    /**
     * Finds an environment by ID with provider registrations eagerly loaded.
     * 
     * <p>This method is used for cascade deletion operations where we need
     * to ensure provider registrations are loaded so that JPA can properly
     * cascade the deletion.</p>
     * 
     * @param id the environment ID to search for (must not be null)
     * @return Optional containing the environment with provider registrations loaded, empty otherwise
     */
    @Query("SELECT e FROM Environment e " +
           "LEFT JOIN FETCH e.providerRegistrations " +
           "WHERE e.id = :id")
    Optional<Environment> findByIdWithProviderRegistrations(@Param("id") UUID id);
    
    /**
     * Checks if an environment with the given application name already exists.
     * 
     * <p>This method is used during environment registration to enforce
     * the uniqueness constraint on application names. Application names
     * must be unique across all environments.</p>
     * 
     * <p>Used by:</p>
     * <ul>
     *   <li>EnvironmentService during registration validation</li>
     * </ul>
     * 
     * @param applicationName the application name to check (must not be null)
     * @return true if an environment with this application name exists, false otherwise
     * @see org.sifunity.broker.environment.service.EnvironmentService#registerEnvironment
     */
    boolean existsByApplicationName(String applicationName);
    
    /**
     * Finds environments by type with pagination support.
     * 
     * <p>This method is used for filtering environments by their type
     * (DIRECT, BROKERED, ADMIN) in listing operations. Supports
     * pagination to handle large numbers of environments efficiently.</p>
     * 
     * <p>Used by:</p>
     * <ul>
     *   <li>EnvironmentService for filtered environment listing</li>
     *   <li>External API GET /environments with type filter</li>
     * </ul>
     * 
     * @param type the environment type to filter by (must not be null)
     * @param pageable pagination information (page number, size, sorting)
     * @return Page containing environments of the specified type
     * @see org.sifunity.broker.environment.service.EnvironmentService#listEnvironments
     * @see EnvironmentType
     */
    Page<Environment> findByType(EnvironmentType type, Pageable pageable);


    /**
     * Finds all environments within a specific solution.
     *
     * @param solutionUuid the solution UUID to filter by (must not be null)
     * @return List of environments in the solution
     */
    List<Environment> findBySolutionUuid(UUID solutionUuid);

    /**
     * Deletes all environments within a specific solution.
     * Used for application-level cascade deletion fallback.
     *
     * @param solutionUuid the solution UUID whose environments should be deleted
     */
    void deleteBySolutionUuid(UUID solutionUuid);

    /**
     * Finds an environment by its application name and solution UUID.
     *
     * @param applicationName the application name to search for (must not be null)
     * @param solutionUuid the solution UUID to search for (must not be null)
     * @return Optional containing the environment if found, empty otherwise
     */
    Optional<Environment> findByApplicationNameAndSolutionUuid(String applicationName, UUID solutionUuid);

    /**
     * Checks if an environment with the given application name exists within a specific solution.
     *
     * <p>This method enforces solution-scoped uniqueness for application names.
     * Two environments in different solutions may share the same application name,
     * but within a single solution each application name must be unique.</p>
     *
     * @param applicationName the application name to check (must not be null)
     * @param solutionUuid the solution UUID to scope the check to (must not be null)
     * @return true if an environment with this application name exists in the solution, false otherwise
     */
    boolean existsByApplicationNameAndSolutionUuid(String applicationName, UUID solutionUuid);

    /**
     * Finds an environment by its fingerprint and solution UUID.
     *
     * <p>Used by the internal lookup API for proxy impersonation target resolution.
     * The gateway calls this to resolve a fingerprint shorthand (e.g. "broker-services")
     * to a specific environment within a solution.</p>
     *
     * @param fingerprint the environment fingerprint to search for (must not be null)
     * @param solutionUuid the solution UUID to scope the search to (must not be null)
     * @return Optional containing the environment if found, empty otherwise
     */
    Optional<Environment> findByFingerprintAndSolutionUuid(String fingerprint, UUID solutionUuid);

}