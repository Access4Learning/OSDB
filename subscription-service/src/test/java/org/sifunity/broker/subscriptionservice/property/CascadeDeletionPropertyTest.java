// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

// Feature: subscription-acl-binding, Property 7: Cascade Deletion on ACL Entry Removal

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.exception.SubscriptionNotFoundException;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.domain.AclEntryRef;
import org.sifunity.broker.subscriptionservice.domain.ContextRef;
import org.sifunity.broker.subscriptionservice.domain.ContextServiceRef;
import org.sifunity.broker.subscriptionservice.domain.ZoneRef;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for cascade deletion behavior on ACL entry removal.
 *
 * <p><b>Validates: Requirements 7.1, 7.2</b></p>
 *
 * <p>For any ACL entry that has one or more associated subscriptions, deleting
 * the ACL entry should result in all associated subscriptions being removed.
 * No orphaned subscriptions should remain.</p>
 *
 * <p>Since cascade deletion happens at the database level (ON DELETE CASCADE),
 * this property test verifies the conceptual behavior through mocks — that after
 * an ACL entry is removed, no subscriptions referencing it are returned by any
 * query method.</p>
 */
class CascadeDeletionPropertyTest {

    /**
     * Property 7: Cascade Deletion on ACL Entry Removal
     *
     * <p><b>Validates: Requirements 7.1, 7.2</b></p>
     *
     * <p>For any ACL entry with 1-5 subscriptions:
     * <ol>
     *   <li>Before deletion: querying by service/contextUuid returns all subscriptions</li>
     *   <li>After ACL entry deletion (simulated cascade): all query methods return empty results</li>
     *   <li>No orphaned subscriptions remain for the deleted ACL entry</li>
     * </ol>
     */
    @Property(tries = 100)
    void deletingAclEntryRemovesAllAssociatedSubscriptions(
            @ForAll("serviceNames") String serviceName,
            @ForAll("contextUuids") UUID contextUuid,
            @ForAll("subscriptionCounts") int subscriptionCount) {

        // Arrange — create an ACL entry with multiple subscriptions
        UUID aclEntryId = UUID.randomUUID();

        AclEntryRef aclEntry = createAclEntryRef(
                aclEntryId, serviceName, contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        Instant now = Instant.now();
        List<Subscription> subscriptions = IntStream.range(0, subscriptionCount)
                .mapToObj(i -> createSubscription(
                        UUID.randomUUID(), aclEntryId, UUID.randomUUID(),
                        SubscriptionType.EVENT, now))
                .toList();

        // Build pre-deletion query results (subscription + ACL entry pairs)
        List<Object[]> preDeleteResults = subscriptions.stream()
                .map(sub -> new Object[]{sub, aclEntry})
                .toList();

        // Mock repositories
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);

        // --- Phase 1: Before deletion — subscriptions are returned ---
        when(repository.findEventSubscriptions(serviceName, contextUuid))
                .thenReturn(preDeleteResults);

        SubscriptionService service = new SubscriptionService(
                repository, queueServiceClient, aclEntryLookupRepository);

        List<SubscriptionDTO> beforeDeletion = service.findEventSubscriptions(
                serviceName, contextUuid);

        assertThat(beforeDeletion).hasSize(subscriptionCount);
        assertThat(beforeDeletion).allSatisfy(dto -> {
            assertThat(dto.serviceName()).isEqualTo(serviceName);
            assertThat(dto.zoneId()).isNotNull();
            assertThat(dto.messageType()).isEqualTo(SubscriptionType.EVENT);
            assertThat(dto.aclEntryId()).isEqualTo(aclEntryId);
        });

        // --- Phase 2: Simulate cascade deletion — repository returns empty ---
        reset(repository);
        when(repository.findEventSubscriptions(serviceName, contextUuid))
                .thenReturn(Collections.emptyList());

        // Also simulate that individual subscription lookups fail
        for (Subscription sub : subscriptions) {
            when(repository.findByIdWithAcl(sub.getId()))
                    .thenReturn(Collections.emptyList());
        }

        // Also simulate queue-based queries return empty
        subscriptions.stream()
                .map(Subscription::getQueueId)
                .distinct()
                .forEach(queueId ->
                        when(repository.findByQueueIdWithAcl(queueId))
                                .thenReturn(Collections.emptyList()));

        // --- Assert: All query methods return empty after cascade ---

        // EVENT query returns empty
        List<SubscriptionDTO> afterDeletion = service.findEventSubscriptions(
                serviceName, contextUuid);
        assertThat(afterDeletion).isEmpty();

        // Individual subscription lookups throw SubscriptionNotFoundException
        for (Subscription sub : subscriptions) {
            assertThatThrownBy(() -> service.getSubscription(sub.getId()))
                    .isInstanceOf(SubscriptionNotFoundException.class);
        }

        // Queue-based queries return empty for each queue
        subscriptions.stream()
                .map(Subscription::getQueueId)
                .distinct()
                .forEach(queueId -> {
                    List<SubscriptionDTO> queueResults = service.findSubscriptionsByQueue(queueId);
                    assertThat(queueResults).isEmpty();
                });

        // Verify repository was queried after cascade
        verify(repository).findEventSubscriptions(serviceName, contextUuid);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    @Provide
    Arbitrary<UUID> contextUuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<Integer> subscriptionCounts() {
        return Arbitraries.integers().between(1, 5);
    }

    // ========== Helpers ==========

    private Subscription createSubscription(UUID id, UUID aclEntryId, UUID queueId,
                                             SubscriptionType type, Instant createdAt) {
        Subscription sub = new Subscription();
        sub.setId(id);
        sub.setAclEntryId(aclEntryId);
        sub.setQueueId(queueId);
        sub.setType(type);
        sub.setCreatedAt(createdAt);
        return sub;
    }

    /**
     * Creates an AclEntryRef using reflection (protected constructor, no setters).
     * Sets up the contextService chain for proper serviceName/contextUuid resolution.
     */
    private AclEntryRef createAclEntryRef(UUID id, String serviceName, UUID contextUuid,
                                           AccessType accessType, AccessLevel accessLevel) {
        try {
            var constructor = AclEntryRef.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            AclEntryRef ref = constructor.newInstance();
            setField(ref, "id", id);
            setField(ref, "contextServiceUuid", UUID.randomUUID());
            setField(ref, "accessType", accessType);
            setField(ref, "accessLevel", accessLevel);

            // Build ContextServiceRef with serviceName and contextUuid
            var csConstructor = ContextServiceRef.class.getDeclaredConstructor();
            csConstructor.setAccessible(true);
            var contextServiceRef = csConstructor.newInstance();
            setField(contextServiceRef, "id", UUID.randomUUID());
            setField(contextServiceRef, "serviceName", serviceName);
            setField(contextServiceRef, "contextUuid", contextUuid);
            setField(contextServiceRef, "serviceType", "OBJECT");
            setField(contextServiceRef, "createdAt", java.time.Instant.now());

            // Build context → zone chain
            var zoneConstructor = ZoneRef.class.getDeclaredConstructor();
            zoneConstructor.setAccessible(true);
            ZoneRef zone = zoneConstructor.newInstance();
            setField(zone, "id", UUID.randomUUID());
            setField(zone, "zoneId", "zone-" + contextUuid.toString().substring(0, 8));

            var ctxConstructor = ContextRef.class.getDeclaredConstructor();
            ctxConstructor.setAccessible(true);
            ContextRef context = ctxConstructor.newInstance();
            setField(context, "id", contextUuid);
            setField(context, "contextId", "ctx-" + contextUuid.toString().substring(0, 8));
            setField(context, "zone", zone);

            setField(contextServiceRef, "context", context);
            setField(ref, "contextService", contextServiceRef);

            return ref;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create AclEntryRef via reflection", e);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
