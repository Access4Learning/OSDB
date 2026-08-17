// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

// Feature: subscription-acl-binding, Property 3: ACL Resolution from Service/Context

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
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based test for ACL resolution equivalence.
 *
 * <p><b>Validates: Requirements 4.2, 4.3</b></p>
 *
 * <p>For any valid combination of {@code serviceName}, {@code contextUuid},
 * and subscription type where a matching ACL entry exists,
 * creating a subscription using those fields (without explicit {@code aclEntryId})
 * should produce the same result as creating with the resolved {@code aclEntryId}
 * directly.</p>
 */
class AclResolutionPropertyTest {

    /**
     * Property 3: ACL Resolution from Service/Context
     *
     * <p><b>Validates: Requirements 4.2, 4.3</b></p>
     *
     * <p>For any valid service/contextUuid/type combination, the resolution path
     * (serviceName/contextUuid) produces the same result as the direct
     * aclEntryId path.</p>
     */
    @Property(tries = 100)
    void resolutionPathProducesSameResultAsDirectAclEntryIdPath(
            @ForAll("serviceNames") String serviceName,
            @ForAll("contextUuids") UUID contextUuid,
            @ForAll("validTypePairs") TypePair typePair) {

        // Arrange — shared ACL entry
        UUID aclEntryId = UUID.randomUUID();
        UUID queueIdDirect = UUID.randomUUID();
        UUID queueIdResolved = UUID.randomUUID();

        AclEntryRef aclEntry = createAclEntryRef(
                aclEntryId, serviceName, contextUuid,
                typePair.accessType(), AccessLevel.APPROVED);

        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);

        // Mock: findById returns the same ACL entry
        when(aclEntryLookupRepository.findById(aclEntryId))
                .thenReturn(Optional.of(aclEntry));

        // Mock: findByServiceNameAndZoneContextAndAccessType returns the same ACL entry
        when(aclEntryLookupRepository.findByServiceNameAndZoneContextAndAccessType(
                eq(serviceName), eq("test-domain"), eq("test-solution"),
                eq("test-zone"), eq("test-ctx"), eq(typePair.accessType())))
                .thenReturn(Optional.of(aclEntry));

        // Mock: both queues exist
        when(queueServiceClient.queueExists(any(UUID.class))).thenReturn(true);

        // Mock: no duplicates
        when(repository.existsByAclEntryIdAndQueueIdAndType(any(), any(), any()))
                .thenReturn(false);

        // Mock: save returns subscription with generated id and timestamp
        when(repository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription sub = invocation.getArgument(0);
            Subscription saved = new Subscription();
            saved.setId(UUID.randomUUID());
            saved.setAclEntryId(sub.getAclEntryId());
            saved.setQueueId(sub.getQueueId());
            saved.setType(sub.getType());
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        SubscriptionService service = new SubscriptionService(
                repository, queueServiceClient, aclEntryLookupRepository);

        // Path 1: Direct aclEntryId
        SubscriptionDTO directInput = new SubscriptionDTO(
                null, queueIdDirect, aclEntryId, null, null, null,
                typePair.subscriptionType(), null);

        SubscriptionDTO directResult = service.createSubscription("test-domain", "test-solution", directInput);

        // Path 2: Resolution via serviceName/zoneId/contextId
        SubscriptionDTO resolvedInput = new SubscriptionDTO(
                null, queueIdResolved, null, serviceName, "test-zone", "test-ctx",
                typePair.subscriptionType(), null);

        SubscriptionDTO resolvedResult = service.createSubscription("test-domain", "test-solution", resolvedInput);

        // Assert — both paths resolve to the same aclEntryId
        assertThat(resolvedResult.aclEntryId()).isEqualTo(directResult.aclEntryId());
        assertThat(resolvedResult.aclEntryId()).isEqualTo(aclEntryId);

        // Assert — both paths produce the same serviceName/zoneId
        assertThat(resolvedResult.serviceName()).isEqualTo(directResult.serviceName());
        assertThat(resolvedResult.zoneId()).isEqualTo(directResult.zoneId());

        // Assert — both paths produce the same subscription type
        assertThat(resolvedResult.messageType()).isEqualTo(directResult.messageType());

        // Assert — the resolved fields match the ACL entry values
        assertThat(directResult.serviceName()).isEqualTo(serviceName);
        assertThat(directResult.zoneId()).isNotNull();
    }

    // ========== Data Types ==========

    record TypePair(SubscriptionType subscriptionType, AccessType accessType) {}

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
    }

    @Provide
    Arbitrary<UUID> contextUuids() {
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
            setField(zone, "zoneId", "test-zone");

            var ctxConstructor = ContextRef.class.getDeclaredConstructor();
            ctxConstructor.setAccessible(true);
            ContextRef context = ctxConstructor.newInstance();
            setField(context, "id", contextUuid);
            setField(context, "contextId", "test-ctx");
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
