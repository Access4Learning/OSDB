// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.UserEnvironmentAccess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for UserEnvironmentAccess entities.
 *
 * <p>Provides data access operations for the user_environment_access table
 * including standard CRUD operations and custom query methods for managing
 * user-to-environment associations.</p>
 *
 * @see UserEnvironmentAccess
 */
@Repository
public interface UserEnvironmentAccessRepository extends JpaRepository<UserEnvironmentAccess, UUID> {

    /**
     * Finds all environment access records for a specific user.
     *
     * @param userId the admin user's UUID
     * @return List of access records for the user (empty list if none found)
     */
    List<UserEnvironmentAccess> findAllByUser_Id(UUID userId);

    /**
     * Finds a specific access record by user and environment.
     *
     * @param userId the admin user's UUID
     * @param environmentId the environment's UUID
     * @return Optional containing the access record if found, empty otherwise
     */
    Optional<UserEnvironmentAccess> findByUser_IdAndEnvironment_Id(UUID userId, UUID environmentId);

    /**
     * Checks if an access record exists for a specific user and environment.
     *
     * @param userId the admin user's UUID
     * @param environmentId the environment's UUID
     * @return true if the user has access to the environment, false otherwise
     */
    boolean existsByUser_IdAndEnvironment_Id(UUID userId, UUID environmentId);

    /**
     * Deletes all access records for a specific user.
     *
     * @param userId the admin user's UUID
     */
    void deleteAllByUser_Id(UUID userId);

    /**
     * Deletes all access records for a specific environment.
     *
     * @param environmentId the environment's UUID
     */
    void deleteAllByEnvironment_Id(UUID environmentId);

    /**
     * Finds all environment access records for a specific environment,
     * eagerly fetching the associated AdminUser entity.
     *
     * <p>Uses an {@link EntityGraph} to avoid N+1 queries when mapping
     * access records to DTOs that include user details.</p>
     *
     * @param environmentId the environment's UUID
     * @return List of access records with user data loaded (empty list if none found)
     */
    @EntityGraph(attributePaths = {"user"})
    List<UserEnvironmentAccess> findAllByEnvironment_Id(UUID environmentId);
}
