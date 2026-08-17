// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

// Feature: subscription-acl-binding, Property 2: Subscription Creation Round-Trip

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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for subscription creation round-trip.
 *
 * <p><b>Validates: Requirements 4.1, 5.1, 5.2, 5.3, 5.4</b></p>
 *
 * <p>For any valid ACL entry (with correct access type mapping) and queue,
 * creating a subscription and then retrieving it by ID should return a
 * SubscriptionDTO where {@code serviceName} and {@code contextUuid}
 * match the ACL entry's values, and {@code queueId}, {@code type}, and
 * {@code createdAt} are correctly populated.</p>
 */
class SubscriptionCreationRoundTripPropertyTest {

    /**
     * Property 2: Subscription Creation Round-Trip
     *
     * <p><b>Validates: Requirements 4.1, 5.1, 5.2, 5.3, 5.4</b></p>
     */
    @Property(tries = 100)
    void createThenRetrieveReturnsMatchingDtoFields(
            @ForAll("serviceNames") String serviceName,
            @ForAll("contextUuids") UUID contextUuid,
            @ForAll("queueIds") UUID queueId,
            @ForAll("validTypePairs") TypePair typePair) {

        // Arrange
        UUID aclEntryId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        AclEntryRef aclEntry = createAclEntryRef(
                aclEntryId, serviceName, contextUuid,
                typePair.accessType(), AccessLevel.APPROVED);

        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);

        // Mock ACL lookup
        when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));

        // Mock queue exists
        when(queueServiceClient.queueExists(queueId)).thenReturn(true);

        // Mock no duplicate
        when(repository.existsByAclEntryIdAndQueueIdAndType(aclEntryId, queueId, typePair.subscriptionType()))
                .thenReturn(false);

        // Mock save — capture and return with generated id and createdAt
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

        // Mock findByIdWithAcl for the retrieve step
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
                null, queueId, aclEntryId, null, null, null,
                typePair.subscriptionType(), null);

        // Act — create
        SubscriptionDTO created = service.createSubscription("test-domain", "test-solution", inputDto);

        // Act — retrieve
        SubscriptionDTO retrieved = service.getSubscription(created.id());

        // Assert — retrieved DTO fields match ACL entry and creation inputs
        assertThat(retrieved.id()).isEqualTo(subscriptionId);
        assertThat(retrieved.serviceName()).isEqualTo(serviceName);
        if (contextUuid != null) {
            assertThat(retrieved.zoneId()).isNotNull();
            assertThat(retrieved.contextId()).isNotNull();
        } else {
            assertThat(retrieved.zoneId()).isNull();
            assertThat(retrieved.contextId()).isNull();
        }
        assertThat(retrieved.queueId()).isEqualTo(queueId);
        assertThat(retrieved.aclEntryId()).isEqualTo(aclEntryId);
        assertThat(retrieved.messageType()).isEqualTo(typePair.subscriptionType());
        // createdAt is populated internally; suppressed from JSON by @JsonIgnore on accessor
        assertThat(retrieved.createdAt()).isNotNull();

        // Assert — created DTO also matches
        assertThat(created.serviceName()).isEqualTo(serviceName);
        assertThat(created.zoneId()).isEqualTo(retrieved.zoneId());
        assertThat(created.queueId()).isEqualTo(queueId);
        assertThat(created.messageType()).isEqualTo(typePair.subscriptionType());
        assertThat(created.createdAt()).isNotNull();
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
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.create(UUID::randomUUID)
        );
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

            if (contextUuid != null) {
                var zoneConstructor = ZoneRef.class.getDeclaredConstructor();
                zoneConstructor.setAccessible(true);
                ZoneRef zone = zoneConstructor.newInstance();
                setField(zone, "id", java.util.UUID.randomUUID());
                setField(zone, "zoneId", "zone-" + contextUuid.toString().substring(0, 8));
                var ctxConstructor = ContextRef.class.getDeclaredConstructor();
                ctxConstructor.setAccessible(true);
                ContextRef context = ctxConstructor.newInstance();
                setField(context, "id", contextUuid);
                setField(context, "contextId", "ctx-" + contextUuid.toString().substring(0, 8));
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
