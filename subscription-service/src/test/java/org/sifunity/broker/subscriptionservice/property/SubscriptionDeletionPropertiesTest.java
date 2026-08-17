// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.exception.SubscriptionNotFoundException;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for subscription deletion correctness properties.
 */
class SubscriptionDeletionPropertiesTest {

    /**
     * Property 12: Subscription deletion - Validates Requirements 7.1, 7.2, 7.4
     */
    @Property(tries = 100)
    void deletingExistingSubscriptionRemovesItPermanently(
            @ForAll("subscriptionIds") UUID subscriptionId) {

        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        when(repository.existsById(subscriptionId)).thenReturn(true);
        doNothing().when(repository).deleteById(subscriptionId);

        SubscriptionService service = new SubscriptionService(repository, queueServiceClient, aclEntryLookupRepository);

        assertThatCode(() -> service.deleteSubscription(subscriptionId))
                .doesNotThrowAnyException();

        verify(repository).existsById(subscriptionId);
        verify(repository).deleteById(subscriptionId);
    }

    /**
     * Property: Deleting non-existent subscription throws SubscriptionNotFoundException
     */
    @Property(tries = 100)
    void deletingNonExistentSubscriptionThrowsNotFoundException(
            @ForAll("subscriptionIds") UUID subscriptionId) {

        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        when(repository.existsById(subscriptionId)).thenReturn(false);

        SubscriptionService service = new SubscriptionService(repository, queueServiceClient, aclEntryLookupRepository);

        assertThatThrownBy(() -> service.deleteSubscription(subscriptionId))
                .isInstanceOf(SubscriptionNotFoundException.class)
                .hasMessageContaining("Subscription not found");

        verify(repository).existsById(subscriptionId);
        verify(repository, never()).deleteById(any(UUID.class));
    }

    /**
     * Property: Retrieving deleted subscription throws SubscriptionNotFoundException
     */
    @Property(tries = 100)
    void retrievingDeletedSubscriptionThrowsNotFoundException(
            @ForAll("subscriptionIds") UUID subscriptionId) {

        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        when(repository.findByIdWithAcl(subscriptionId)).thenReturn(Collections.emptyList());

        SubscriptionService service = new SubscriptionService(repository, queueServiceClient, aclEntryLookupRepository);

        assertThatThrownBy(() -> service.getSubscription(subscriptionId))
                .isInstanceOf(SubscriptionNotFoundException.class)
                .hasMessageContaining("Subscription not found");

        verify(repository).findByIdWithAcl(subscriptionId);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UUID> subscriptionIds() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
