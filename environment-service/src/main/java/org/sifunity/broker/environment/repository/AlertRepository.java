// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.Alert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Alert entities.
 * 
 * <p>Provides data access operations for the alerts table including
 * standard CRUD operations and custom query methods for ownership-filtered
 * alert retrieval.</p>
 * 
 * <p>Key operations:</p>
 * <ul>
 *   <li>Finding alerts by environment ID for non-admin ownership filtering</li>
 *   <li>Finding a single alert by ID and environment ID for ownership-checked retrieval</li>
 * </ul>
 * 
 * @see Alert
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    /**
     * Finds all alerts created by a specific environment.
     * 
     * <p>Used for non-admin queries where only the requester's own
     * alerts should be returned.</p>
     * 
     * @param environmentId the environment UUID to filter by
     * @return list of alerts belonging to the environment
     */
    List<Alert> findByEnvironmentId(UUID environmentId);

    /**
     * Finds a single alert by its ID, scoped to a specific environment.
     * 
     * <p>Used for non-admin single-alert retrieval where the alert must
     * belong to the requesting environment. Returns empty if the alert
     * does not exist or belongs to a different environment.</p>
     * 
     * @param id            the alert UUID
     * @param environmentId the environment UUID for ownership check
     * @return Optional containing the alert if found and owned, empty otherwise
     */
    Optional<Alert> findByIdAndEnvironmentId(UUID id, UUID environmentId);

    /**
     * Returns all alerts visible to the given caller:
     * <ul>
     *   <li>alerts the caller created (environment_id = callerId)</li>
     *   <li>alerts directed at the caller (target_environment_id = callerId)</li>
     *   <li>broadcast alerts (target_environment_id IS NULL)</li>
     * </ul>
     * 
     * <p>Uses SELECT DISTINCT to prevent duplicates when a caller is both
     * creator and target of the same alert.</p>
     * 
     * @param callerId the UUID of the calling environment
     * @return list of alerts visible to the caller
     */
    @Query("SELECT DISTINCT a FROM Alert a WHERE a.environment.id = :callerId " +
           "OR a.targetEnvironmentId = :callerId " +
           "OR a.targetEnvironmentId IS NULL")
    List<Alert> findVisibleAlerts(@Param("callerId") UUID callerId);

    /**
     * Returns a single alert by ID if visible to the given caller.
     * 
     * <p>Visibility is determined by the same three-condition predicate:
     * caller created it, directed at caller, or broadcast (NULL target).</p>
     * 
     * @param alertId  the alert UUID to retrieve
     * @param callerId the UUID of the calling environment
     * @return Optional containing the alert if found and visible, empty otherwise
     */
    @Query("SELECT a FROM Alert a WHERE a.id = :alertId " +
           "AND (a.environment.id = :callerId " +
           "OR a.targetEnvironmentId = :callerId " +
           "OR a.targetEnvironmentId IS NULL)")
    Optional<Alert> findVisibleAlertById(@Param("alertId") UUID alertId,
                                         @Param("callerId") UUID callerId);
}
