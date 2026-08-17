// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

// Feature: subscription-acl-binding, Property 4: Duplicate Subscription Rejection

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.domain.AclEntryRef;
import org.sifunity.broker.subscriptionservice.domain.ContextRef;
import org.sifunity.broker.subscriptionservice.domain.ContextServiceRef;
import org.sifunity.broker.subscriptionservice.domain.ZoneRef;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.exception.DuplicateSubscriptionException;
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for duplicate subscription rejection.
 *
 * <p><b>Validates: Requirements 2.3, 4.4</b></p>
 *
 * <p>For any {@code aclEntryId}, {@code queueId}, and {@code type} combination,
 * attempting to create a second subscription with the same triple should be rejected
 * with {@link DuplicateSubscriptionException}, and the original subscription should
 * remain unchanged.</p>
 */
class DuplicateSubscriptionRejectionPropertyTest {

    /**
     * Property 4: Duplicate Subscription Rejection
     *
     * <p><b>Validates: Requirements 2.3, 4.4</b></p>
     *
     * <p>For any (aclEntryId, queueId, type) combination:
     * <ol>
     *   <li>First creation succeeds</li>
     *   <li>Second creation with the same triple is rejected with DuplicateSubscriptionException</li>
     *   <li>The original subscription remains unchanged</li>
     * </ol>
     */
    @Property(tries = 100)
    void duplicateCreationIsRejectedAndOriginalUnchanged(
            @ForAll("aclEntryIds") UUID aclEntryId,
            @ForAll("queueIds") UUID queueId,
            @ForAll("validTypePairs") TypePair typePair) {

        // Arrange
        UUID subscriptionId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        AclEntryRef aclEntry = createAclEntryRef(
                aclEntryId, "TestService", UUID.randomUUID(),
                typePair.accessType(), AccessLevel.APPROVED);

        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);

        // Mock ACL lookup — always resolves
        when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));

        // Mock queue exists
        when(queueServiceClient.queueExists(queueId)).thenReturn(true);

        // Mock duplicate check: false on first call, true on second call
        when(repository.existsByAclEntryIdAndQueueIdAndType(aclEntryId, queueId, typePair.subscriptionType()))
                .thenReturn(false)
                .thenReturn(true);

        // Mock save — return a saved subscription on first call
        when(repository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription sub = invocation.getArgument(0);
            Subscription saved = new Subscription();
            saved.setId(subscriptionId);
            saved.setAclEntryId(sub.getAclEntryId());
            saved.setQueueId(sub.getQueueId());
            saved.setType(sub.getType());
            saved.setCreatedAt(createdAt);
            return saved;
        });

        // Mock findByIdWithAcl to verify original subscription is unchanged
        when(repository.findByIdWithAcl(subscriptionId)).thenAnswer(invocation -> {
            Subscription sub = new Subscription();
            sub.setId(subscriptionId);
            sub.setAclEntryId(aclEntryId);
            sub.setQueueId(queueId);
            sub.setType(typePair.subscriptionType());
            sub.setCreatedAt(createdAt);
            return java.util.Collections.singletonList(new Object[]{sub, aclEntry});
        });

        SubscriptionService service = new SubscriptionService(
                repository, queueServiceClient, aclEntryLookupRepository);

        SubscriptionDTO inputDto = new SubscriptionDTO(
                null, queueId, aclEntryId, null, null, null, null,
                typePair.subscriptionType(), null);

        // Act — first creation succeeds
        SubscriptionDTO created = service.createSubscription("test-domain", "test-solution", inputDto);
        assertThat(created).isNotNull();
        assertThat(created.id()).isEqualTo(subscriptionId);

        // Act — second creation with same (aclEntryId, queueId, type) is rejected
        assertThatThrownBy(() -> service.createSubscription("test-domain", "test-solution", inputDto))
                .isInstanceOf(DuplicateSubscriptionException.class);

        // Assert — save was called exactly once (only for the first creation)
        verify(repository, times(1)).save(any(Subscription.class));

        // Assert — original subscription remains unchanged
        SubscriptionDTO original = service.getSubscription(subscriptionId);
        assertThat(original.id()).isEqualTo(subscriptionId);
        assertThat(original.aclEntryId()).isEqualTo(aclEntryId);
        assertThat(original.queueId()).isEqualTo(queueId);
        assertThat(original.messageType()).isEqualTo(typePair.subscriptionType());
        // createdAt is populated internally; suppressed from JSON by @JsonIgnore on accessor
        assertThat(original.createdAt()).isNotNull();
    }

    // ========== Data Types ==========

    record TypePair(SubscriptionType subscriptionType, AccessType accessType) {}

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UUID> aclEntryIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<UUID> queueIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<TypePair> validTypePairs() {
        return Arbitraries.of(
                new TypePair(SubscriptionType.EVENT, AccessType.SUBSCRIBE),
                new TypePair(SubscriptionType.DELAYED_REQUEST, AccessType.PROVIDE)
        );
    }

    // ========== Helpers ==========

    /**
     * Creates an AclEntryRef using reflection (protected constructor, no setters).
     * Sets up the contextService chain for proper serviceName/contextUuid resolution.
     */
    private AclEntryRef createAclEntryRef(UUID id, String serviceName, UUID contextUuid,
                                           AccessType accessType,
                                           AccessLevel accessLevel) {
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

            // Set up context → zone chain for zoneId/contextId resolution
            if (contextUuid != null) {
                var zoneConstructor = ZoneRef.class.getDeclaredConstructor();
                zoneConstructor.setAccessible(true);
                ZoneRef zone = zoneConstructor.newInstance();
                setField(zone, "id", UUID.randomUUID());
                setField(zone, "zoneId", "test-zone");

                var ctxConstructor = ContextRef.class.getDeclaredConstructor();
                ctxConstructor.setAccessible(true);
                ContextRef context = ctxConstructor.newInstance();
                setField(context, "id", contextUuid);
                setField(context, "contextId", "test-context");
                setField(context, "zone", zone);

                setField(contextServiceRef, "context", context);
            }

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
