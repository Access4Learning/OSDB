// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.domain;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.SubscriptionType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Subscription entity.
 * 
 * <p>Tests verify:</p>
 * <ul>
 *   <li>@PrePersist lifecycle callback sets createdAt automatically</li>
 *   <li>Entity field getters and setters work correctly</li>
 * </ul>
 * 
 * <p>Validates: Requirements 1.6, 2.1</p>
 */
class SubscriptionTest {
    
    @Test
    void testPrePersistSetsCreatedAtWhenNull() {
        Subscription subscription = new Subscription();
        subscription.setQueueId(UUID.randomUUID());
        subscription.setAclEntryId(UUID.randomUUID());
        subscription.setType(SubscriptionType.EVENT);
        
        Instant beforeCall = Instant.now();
        subscription.onCreate();
        Instant afterCall = Instant.now();
        
        assertThat(subscription.getCreatedAt())
            .isNotNull()
            .isAfterOrEqualTo(beforeCall)
            .isBeforeOrEqualTo(afterCall);
    }
    
    @Test
    void testPrePersistDoesNotOverrideExistingCreatedAt() {
        Subscription subscription = new Subscription();
        Instant existingTimestamp = Instant.parse("2024-01-15T10:30:00Z");
        subscription.setCreatedAt(existingTimestamp);
        subscription.setQueueId(UUID.randomUUID());
        subscription.setAclEntryId(UUID.randomUUID());
        subscription.setType(SubscriptionType.EVENT);
        
        subscription.onCreate();
        
        assertThat(subscription.getCreatedAt()).isEqualTo(existingTimestamp);
    }
    
    @Test
    void testGettersAndSetters() {
        Subscription subscription = new Subscription();
        
        UUID id = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        UUID aclEntryId = UUID.randomUUID();
        SubscriptionType type = SubscriptionType.EVENT;
        Instant createdAt = Instant.now();
        
        subscription.setId(id);
        subscription.setQueueId(queueId);
        subscription.setAclEntryId(aclEntryId);
        subscription.setType(type);
        subscription.setCreatedAt(createdAt);
        
        assertThat(subscription.getId()).isEqualTo(id);
        assertThat(subscription.getQueueId()).isEqualTo(queueId);
        assertThat(subscription.getAclEntryId()).isEqualTo(aclEntryId);
        assertThat(subscription.getType()).isEqualTo(type);
        assertThat(subscription.getCreatedAt()).isEqualTo(createdAt);
    }
    
    @Test
    void testDelayedRequestSubscriptionType() {
        Subscription subscription = new Subscription();
        subscription.setQueueId(UUID.randomUUID());
        subscription.setAclEntryId(UUID.randomUUID());
        subscription.setType(SubscriptionType.DELAYED_REQUEST);
        
        assertThat(subscription.getType()).isEqualTo(SubscriptionType.DELAYED_REQUEST);
    }
    
    @Test
    void testEventSubscriptionType() {
        Subscription subscription = new Subscription();
        subscription.setQueueId(UUID.randomUUID());
        subscription.setAclEntryId(UUID.randomUUID());
        subscription.setType(SubscriptionType.EVENT);
        
        assertThat(subscription.getType()).isEqualTo(SubscriptionType.EVENT);
    }
}
