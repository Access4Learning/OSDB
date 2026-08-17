// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.domain.AclEntryRef;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.exception.DuplicateSubscriptionException;
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for subscription creation correctness properties.
 */
class SubscriptionCreationPropertiesTest {

    /**
     * Property 1: Successful subscription creation - Validates Requirements 1.1, 1.2, 1.6
     */
    @Property(tries = 100)
    void successfulSubscriptionCreationHasIdAndTimestamp(
            @ForAll("validSubscriptionRequests") SubscriptionDTO request) {

        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        UUID aclEntryId = request.aclEntryId();
        AclEntryRef aclEntry = mockAclEntryRef(aclEntryId, "TestService", UUID.randomUUID(),
                request.messageType() == SubscriptionType.EVENT ? AccessType.SUBSCRIBE : AccessType.PROVIDE);

        when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));
        when(queueServiceClient.queueExists(any(UUID.class))).thenReturn(true);
        when(repository.existsByAclEntryIdAndQueueIdAndType(any(), any(), any())).thenReturn(false);

        when(repository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription sub = invocation.getArgument(0);
            Subscription saved = new Subscription();
            saved.setId(UUID.randomUUID());
            saved.setQueueId(sub.getQueueId());
            saved.setAclEntryId(sub.getAclEntryId());
            saved.setType(sub.getType());
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        SubscriptionService service = new SubscriptionService(repository, queueServiceClient, aclEntryLookupRepository);

        SubscriptionDTO created = service.createSubscription("test-domain", "test-solution", request);

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.queueId()).isEqualTo(request.queueId());
        assertThat(created.messageType()).isEqualTo(request.messageType());

        verify(queueServiceClient).queueExists(request.queueId());
        verify(repository).existsByAclEntryIdAndQueueIdAndType(aclEntryId, request.queueId(), request.messageType());
        verify(repository).save(any(Subscription.class));
    }

    /**
     * Property 4: Subscription uniqueness enforcement - Validates Requirements 2.1, 2.2, 2.3
     */
    @Property(tries = 100)
    void duplicateSubscriptionsAreRejected(
            @ForAll("validSubscriptionRequests") SubscriptionDTO request) {

        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        UUID aclEntryId = request.aclEntryId();
        AclEntryRef aclEntry = mockAclEntryRef(aclEntryId, "TestService", UUID.randomUUID(),
                request.messageType() == SubscriptionType.EVENT ? AccessType.SUBSCRIBE : AccessType.PROVIDE);

        when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));
        when(queueServiceClient.queueExists(any(UUID.class))).thenReturn(true);
        when(repository.existsByAclEntryIdAndQueueIdAndType(any(), any(), any())).thenReturn(true);

        SubscriptionService service = new SubscriptionService(repository, queueServiceClient, aclEntryLookupRepository);

        assertThatThrownBy(() -> service.createSubscription("test-domain", "test-solution", request))
                .isInstanceOf(DuplicateSubscriptionException.class)
                .hasMessageContaining("already exists");

        verify(repository, never()).save(any(Subscription.class));
    }

    // ========== Helpers ==========

    private AclEntryRef mockAclEntryRef(UUID id, String serviceName, UUID contextUuid,
                                         AccessType accessType) {
        AclEntryRef ref = mock(AclEntryRef.class);
        when(ref.getId()).thenReturn(id);
        when(ref.getServiceName()).thenReturn(serviceName);
        when(ref.getContextUuid()).thenReturn(contextUuid);
        when(ref.getAccessType()).thenReturn(accessType);
        when(ref.getAccessLevel()).thenReturn(AccessLevel.APPROVED);
        when(ref.resolveZoneId()).thenReturn("test-zone");
        when(ref.resolveContextId()).thenReturn("test-context");
        return ref;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<SubscriptionDTO> validSubscriptionRequests() {
        return Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.of(SubscriptionType.values())
        ).as((queueId, aclEntryId, type) ->
                new SubscriptionDTO(null, queueId, aclEntryId, null, null, null, null, type, null)
        );
    }
}
