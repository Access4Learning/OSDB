// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.domain;

import org.sifunity.broker.common.constant.SubscriptionType;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a subscription in the SIF Unity Broker.
 * 
 * <p>A subscription binds an ACL entry to a queue for message routing.
 * Two types of subscriptions are supported:</p>
 * <ul>
 *   <li>EVENT: Consumers subscribe to event notifications from providers</li>
 *   <li>DELAYED_REQUEST: Providers subscribe to their request queues</li>
 * </ul>
 * 
 * <p>The uniqueness constraint ensures no duplicate subscriptions exist for
 * the same ACL entry, queue, and type combination.</p>
 */
@Entity
@Table(name = "subscriptions",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_subscription_acl",
           columnNames = {"acl_entry_id", "queue_id", "type"}
       ),
       indexes = {
           @Index(name = "idx_subscriptions_acl_entry_id", columnList = "acl_entry_id"),
           @Index(name = "idx_queue_id", columnList = "queue_id")
       })
public class Subscription {
    
    /**
     * Unique identifier for the subscription.
     */
    @Id
    @GeneratedValue
    private UUID id;
    
    /**
     * ID of the ACL entry that this subscription is bound to.
     * References acl_entries(id) with ON DELETE CASCADE at the database level.
     */
    @Column(name = "acl_entry_id", nullable = false)
    private UUID aclEntryId;
    
    /**
     * ID of the queue that will receive messages for this subscription.
     */
    @Column(name = "queue_id", nullable = false)
    private UUID queueId;
    
    /**
     * Type of subscription (EVENT or DELAYED_REQUEST).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionType type;
    
    /**
     * Timestamp when the subscription was created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    /**
     * JPA lifecycle callback to set createdAt timestamp before persisting.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    // Getters and setters
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getAclEntryId() {
        return aclEntryId;
    }
    
    public void setAclEntryId(UUID aclEntryId) {
        this.aclEntryId = aclEntryId;
    }
    
    public UUID getQueueId() {
        return queueId;
    }
    
    public void setQueueId(UUID queueId) {
        this.queueId = queueId;
    }
    
    public SubscriptionType getType() {
        return type;
    }
    
    public void setType(SubscriptionType type) {
        this.type = type;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
