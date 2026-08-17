// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.repository;

import org.sifunity.broker.queueservice.domain.Queue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Queue entity operations.
 * Provides database access for queue metadata stored in PostgreSQL.
 */
@Repository
public interface QueueRepository extends JpaRepository<Queue, UUID> {
    
    /**
     * Find all queues owned by a specific environment
     * 
     * @param ownerId the environment ID that owns the queues
     * @return list of queues owned by the environment
     */
    List<Queue> findByOwnerId(UUID ownerId);
    
    /**
     * Find all queues belonging to a specific solution
     * 
     * @param solutionUuid the solution UUID
     * @return list of queues in the solution
     */
    List<Queue> findBySolutionUuid(UUID solutionUuid);
    
    /**
     * Find a specific queue by owner and name
     * 
     * @param ownerId the environment ID that owns the queue
     * @param name the queue name
     * @return optional containing the queue if found
     */
    Optional<Queue> findByOwnerIdAndName(UUID ownerId, String name);
    
    /**
     * Lightweight projection for the depth gauge updater.
     * Returns only the fields needed to resolve stream keys: id, name, ownerId, domainId, solutionId.
     * Avoids loading full entity graph every 15 seconds.
     */
    @Query("SELECT q.id AS id, q.name AS name, q.ownerId AS ownerId, q.domainId AS domainId, q.solutionId AS solutionId FROM Queue q")
    List<QueueKeyProjection> findAllKeyProjections();
    
    /**
     * Projection containing only the fields needed for depth gauge stream key resolution.
     */
    interface QueueKeyProjection {
        UUID getId();
        String getName();
        UUID getOwnerId();
        String getDomainId();
        String getSolutionId();
    }
}
