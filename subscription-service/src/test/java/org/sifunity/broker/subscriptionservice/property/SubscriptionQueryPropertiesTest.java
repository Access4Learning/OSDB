// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for subscription query correctness properties.
 */
class SubscriptionQueryPropertiesTest {

    /**
     * Property 5: EVENT subscription query matching - Validates Requirements 3.1, 3.2, 3.6
     */
    @Property(tries = 100)
    void eventSubscriptionQueryReturnsExactMatches(
            @ForAll("serviceNames") String serviceName,
            @ForAll("contextUuids") UUID contextUuid) {

        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        UUID aclEntryId = UUID.randomUUID();
        Subscription sub = createSubscription(aclEntryId, SubscriptionType.EVENT);
        AclEntryRef acl = createAclEntryRef(aclEntryId, serviceName, contextUuid, AccessType.SUBSCRIBE);

        List<Object[]> results = new ArrayList<>();
        results.add(new Object[]{sub, acl});

        when(repository.findEventSubscriptions(serviceName, contextUuid))
                .thenReturn(results);

        SubscriptionService service = new SubscriptionService(repository, queueServiceClient, aclEntryLookupRepository);

        List<SubscriptionDTO> dtos = service.findEventSubscriptions(serviceName, contextUuid);

        assertThat(dtos).hasSize(1);
        SubscriptionDTO result = dtos.get(0);
        assertThat(result.serviceName()).isEqualTo(serviceName);
        assertThat(result.zoneId()).isNotNull();
        assertThat(result.messageType()).isEqualTo(SubscriptionType.EVENT);
        assertThat(result.id()).isNotNull();
        assertThat(result.queueId()).isNotNull();
        assertThat(result.createdAt()).isNotNull();

        verify(repository).findEventSubscriptions(serviceName, contextUuid);
    }

    /**
     * Property 7: Empty EVENT subscription results - Validates Requirements 3.5
     */
    @Property(tries = 100)
    void eventSubscriptionQueryWithNoMatchesReturnsEmptyList(
            @ForAll("serviceNames") String serviceName,
            @ForAll("contextUuids") UUID contextUuid) {

        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        when(repository.findEventSubscriptions(serviceName, contextUuid))
                .thenReturn(new ArrayList<>());

        SubscriptionService service = new SubscriptionService(repository, queueServiceClient, aclEntryLookupRepository);

        List<SubscriptionDTO> dtos = service.findEventSubscriptions(serviceName, contextUuid);

        assertThat(dtos).isEmpty();
    }

    /**
     * Property 8: DELAYED_REQUEST subscription exact matching - Validates Requirements 4.1, 4.2, 4.3, 4.5, 9.3
     */
    @Property(tries = 100)
    void delayedRequestSubscriptionQueryRequiresExactMatch(
            @ForAll("serviceNames") String serviceName,
            @ForAll("contextUuids") UUID contextUuid) {

        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        UUID aclEntryId = UUID.randomUUID();
        Subscription sub = createSubscription(aclEntryId, SubscriptionType.DELAYED_REQUEST);
        AclEntryRef acl = createAclEntryRef(aclEntryId, serviceName, contextUuid, AccessType.PROVIDE);

        List<Object[]> results = new ArrayList<>();
        results.add(new Object[]{sub, acl});

        when(repository.findDelayedRequestSubscription(serviceName, contextUuid))
                .thenReturn(results);

        SubscriptionService service = new SubscriptionService(repository, queueServiceClient, aclEntryLookupRepository);

        SubscriptionDTO result = service.findDelayedRequestSubscription(serviceName, contextUuid);

        assertThat(result).isNotNull();
        assertThat(result.serviceName()).isEqualTo(serviceName);
        assertThat(result.zoneId()).isNotNull();
        assertThat(result.messageType()).isEqualTo(SubscriptionType.DELAYED_REQUEST);
        assertThat(result.id()).isNotNull();
        assertThat(result.queueId()).isNotNull();
        assertThat(result.createdAt()).isNotNull();

        verify(repository).findDelayedRequestSubscription(serviceName, contextUuid);
    }

    /**
     * Property 9: DELAYED_REQUEST not found error - Validates Requirements 4.4
     */
    @Property(tries = 100)
    void delayedRequestSubscriptionQueryThrowsNotFoundWhenNoMatch(
            @ForAll("serviceNames") String serviceName,
            @ForAll("contextUuids") UUID contextUuid) {

        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);

        when(repository.findDelayedRequestSubscription(serviceName, contextUuid))
                .thenReturn(new ArrayList<>());

        SubscriptionService service = new SubscriptionService(repository, queueServiceClient, aclEntryLookupRepository);

        assertThatThrownBy(() -> service.findDelayedRequestSubscription(serviceName, contextUuid))
                .isInstanceOf(SubscriptionNotFoundException.class)
                .hasMessageContaining("No DELAYED_REQUEST subscription found");
    }

    // ========== Helper Methods ==========

    private Subscription createSubscription(UUID aclEntryId, SubscriptionType type) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setQueueId(UUID.randomUUID());
        subscription.setAclEntryId(aclEntryId);
        subscription.setType(type);
        subscription.setCreatedAt(Instant.now());
        return subscription;
    }

    private AclEntryRef createAclEntryRef(UUID id, String serviceName, UUID contextUuid,
                                           AccessType accessType) {
        try {
            var constructor = AclEntryRef.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            AclEntryRef ref = constructor.newInstance();
            setField(ref, "id", id);
            setField(ref, "contextServiceUuid", UUID.randomUUID());
            setField(ref, "accessType", accessType);
            setField(ref, "accessLevel", AccessLevel.APPROVED);

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

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> serviceNames() {
        Arbitrary<Character> firstChar = Arbitraries.chars().range('a', 'z');
        Arbitrary<String> restChars = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(2)
                .ofMaxLength(29);
        return Combinators.combine(firstChar, restChars).as((first, rest) -> first + rest);
    }

    @Provide
    Arbitrary<UUID> contextUuids() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
