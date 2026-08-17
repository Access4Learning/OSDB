// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.repository;

import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing Subscription entities.
 * 
 * <p>Provides data access methods for subscription CRUD operations and
 * specialized JPQL JOIN queries that resolve ACL entry fields
 * (serviceName, contextUuid) at query time via context_services.</p>
 * 
 * <p>Key query methods:</p>
 * <ul>
 *   <li>Duplicate checking: existsByAclEntryIdAndQueueIdAndType</li>
 *   <li>Queue listing: findByQueueId, findByQueueIdWithAcl</li>
 *   <li>Event matching: findEventSubscriptions (JOIN through context_services)</li>
 *   <li>Exact matching: findDelayedRequestSubscription (JOIN through context_services)</li>
 *   <li>Single lookup with ACL: findByIdWithAcl</li>
 * </ul>
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    
    /**
     * Checks if a subscription already exists with the given ACL entry, queue, and type.
     */
    boolean existsByAclEntryIdAndQueueIdAndType(UUID aclEntryId, UUID queueId, SubscriptionType type);

    /**
     * Finds all subscriptions associated with a specific queue.
     */
    List<Subscription> findByQueueId(UUID queueId);
    
    /**
     * Finds EVENT subscriptions matching the given service name and context UUID
     * by joining through acl_entries → context_services.
     * 
     * <p>Returns subscriptions where the joined context_service matches:</p>
     * <ul>
     *   <li>serviceName matches exactly</li>
     *   <li>contextUuid matches exactly</li>
     *   <li>subscription type is EVENT</li>
     * </ul>
     */
    @Query("SELECT s, a FROM Subscription s " +
           "JOIN AclEntryRef a ON s.aclEntryId = a.id " +
           "JOIN a.contextService cs " +
           "LEFT JOIN cs.context c " +
           "LEFT JOIN c.zone z " +
           "WHERE cs.serviceName = :serviceName " +
           "AND cs.contextUuid = :contextUuid " +
           "AND s.type = 'EVENT'")
    List<Object[]> findEventSubscriptions(
        @Param("serviceName") String serviceName,
        @Param("contextUuid") UUID contextUuid
    );

    /**
     * Finds EVENT subscriptions by serviceName only (any context).
     * Used when contextUuid is not specified (e.g., alert event distribution).
     */
    @Query("SELECT s, a FROM Subscription s " +
           "JOIN AclEntryRef a ON s.aclEntryId = a.id " +
           "JOIN a.contextService cs " +
           "WHERE cs.serviceName = :serviceName " +
           "AND s.type = 'EVENT'")
    List<Object[]> findEventSubscriptionsByServiceName(
        @Param("serviceName") String serviceName
    );
    
    /**
     * Finds DELAYED_REQUEST subscriptions with exact context matching by joining
     * through acl_entries → context_services.
     */
    @Query("SELECT s, a FROM Subscription s " +
           "JOIN AclEntryRef a ON s.aclEntryId = a.id " +
           "JOIN a.contextService cs " +
           "LEFT JOIN cs.context c " +
           "LEFT JOIN c.zone z " +
           "WHERE cs.serviceName = :serviceName " +
           "AND cs.contextUuid = :contextUuid " +
           "AND s.type = 'DELAYED_REQUEST'")
    List<Object[]> findDelayedRequestSubscription(
        @Param("serviceName") String serviceName,
        @Param("contextUuid") UUID contextUuid
    );

    /**
     * Finds a single subscription by ID with its associated ACL entry data.
     */
    @Query("SELECT s, a FROM Subscription s " +
           "JOIN AclEntryRef a ON s.aclEntryId = a.id " +
           "JOIN a.contextService cs " +
           "LEFT JOIN cs.context c " +
           "LEFT JOIN c.zone z " +
           "WHERE s.id = :id")
    List<Object[]> findByIdWithAcl(@Param("id") UUID id);
    
    /**
     * Finds all subscriptions for a queue with their associated ACL entry data.
     */
    @Query("SELECT s, a FROM Subscription s " +
           "JOIN AclEntryRef a ON s.aclEntryId = a.id " +
           "JOIN a.contextService cs " +
           "LEFT JOIN cs.context c " +
           "LEFT JOIN c.zone z " +
           "WHERE s.queueId = :queueId")
    List<Object[]> findByQueueIdWithAcl(@Param("queueId") UUID queueId);

    /**
     * Finds all subscriptions for an environment by joining through the acl_entries table.
     */
    @Query("SELECT s, a FROM Subscription s " +
           "JOIN AclEntryRef a ON s.aclEntryId = a.id " +
           "JOIN a.contextService cs " +
           "LEFT JOIN cs.context c " +
           "LEFT JOIN c.zone z " +
           "WHERE a.environmentId = :environmentId")
    List<Object[]> findByEnvironmentIdWithAcl(@Param("environmentId") UUID environmentId);
}
