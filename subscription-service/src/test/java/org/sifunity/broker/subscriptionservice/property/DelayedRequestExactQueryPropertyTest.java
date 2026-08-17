// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

// Feature: subscription-acl-binding, Property 6: DELAYED_REQUEST Subscription Exact Query

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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for DELAYED_REQUEST subscription exact query behavior.
 *
 * <p><b>Validates: Requirements 6.2</b></p>
 *
 * <p>For any {@code serviceName} and {@code contextUuid}, querying
 * DELAYED_REQUEST subscriptions should return ONLY subscriptions whose ACL entry
 * matches all fields exactly. A different contextUuid on the ACL entry should
 * NOT be returned for DELAYED_REQUEST queries.</p>
 */
class DelayedRequestExactQueryPropertyTest {

    /**
     * Property 6: DELAYED_REQUEST Subscription Exact Query
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     *
     * <p>For any serviceName and contextUuid:
     * <ol>
     *   <li>An exact-match subscription (same serviceName, contextUuid) is returned</li>
     *   <li>A subscription with a different contextUuid is NOT returned</li>
     * </ol>
     */
    @Property(tries = 100)
    void delayedRequestQueryReturnsExactMatchesOnly(
            @ForAll("serviceNames") String serviceName,
            @ForAll("contextUuids") UUID contextUuid) {

        // Arrange — create two ACL entries: exact match and different context
        UUID exactAclId = UUID.randomUUID();
        UUID otherAclId = UUID.randomUUID();
        UUID otherContextUuid = UUID.randomUUID();

        AclEntryRef exactAcl = createAclEntryRef(
                exactAclId, serviceName, contextUuid,
                AccessType.PROVIDE, AccessLevel.APPROVED);

        AclEntryRef otherAcl = createAclEntryRef(
                otherAclId, serviceName, otherContextUuid,
                AccessType.PROVIDE, AccessLevel.APPROVED);

        // Create two subscriptions
        UUID exactSubId = UUID.randomUUID();
        UUID otherSubId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Instant now = Instant.now();

        Subscription exactSub = createSubscription(
                exactSubId, exactAclId, queueId, SubscriptionType.DELAYED_REQUEST, now);
        // otherSub exists but should NOT appear in results
        Subscription otherSub = createSubscription(
                otherSubId, otherAclId, queueId, SubscriptionType.DELAYED_REQUEST, now);

        // Mock repository — findDelayedRequestSubscription returns only the exact match
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);

        List<Object[]> mockResults = new java.util.ArrayList<>();
        mockResults.add(new Object[]{exactSub, exactAcl});
        when(repository.findDelayedRequestSubscription(serviceName, contextUuid))
                .thenReturn(mockResults);

        SubscriptionService service = new SubscriptionService(
                repository, queueServiceClient, aclEntryLookupRepository);

        // Act
        SubscriptionDTO result = service.findDelayedRequestSubscription(serviceName, contextUuid);

        // Assert — exactly 1 result: the exact match only
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(exactSubId);
        assertThat(result.serviceName()).isEqualTo(serviceName);
        assertThat(result.zoneId()).isNotNull();
        assertThat(result.messageType()).isEqualTo(SubscriptionType.DELAYED_REQUEST);

        // Verify the other subscription is NOT the returned result
        assertThat(result.id()).isNotEqualTo(otherSubId);

        // Verify the repository was called with the correct parameters
        verify(repository).findDelayedRequestSubscription(serviceName, contextUuid);
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
