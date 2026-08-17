// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

// Feature: subscription-acl-binding, Property 1: Access Type Validation

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
import org.sifunity.broker.subscriptionservice.exception.AccessTypeMismatchException;
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
 * Property-based test for access type validation during subscription creation.
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3</b></p>
 *
 * <p>For any (SubscriptionType, AccessType) pair, creation succeeds if and only if
 * the mapping is correct: EVENT→SUBSCRIBE, DELAYED_REQUEST→PROVIDE. All other
 * combinations must be rejected with {@link AccessTypeMismatchException}.</p>
 */
class AccessTypeValidationPropertyTest {

    /**
     * Property 1: Access Type Validation
     *
     * <p><b>Validates: Requirements 3.1, 3.2, 3.3</b></p>
     *
     * <p>For any (SubscriptionType, AccessType) pair, creation succeeds iff the
     * mapping is correct (EVENT→SUBSCRIBE, DELAYED_REQUEST→PROVIDE). All mismatched
     * pairs must throw AccessTypeMismatchException.</p>
     */
    @Property(tries = 100)
    void creationSucceedsIffAccessTypeMappingIsCorrect(
            @ForAll("subscriptionTypes") SubscriptionType subscriptionType,
            @ForAll("accessTypes") AccessType accessType) {

        // Arrange
        UUID aclEntryId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        AclEntryRef aclEntry = createAclEntryRef(aclEntryId, "TestService", UUID.randomUUID(),
                accessType, AccessLevel.APPROVED);

        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);

        when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));
        when(queueServiceClient.queueExists(queueId)).thenReturn(true);
        when(repository.existsByAclEntryIdAndQueueIdAndType(aclEntryId, queueId, subscriptionType))
                .thenReturn(false);

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

        SubscriptionDTO inputDto = new SubscriptionDTO(
                null, queueId, aclEntryId, null, null, null, subscriptionType, null);

        boolean isValidMapping = isCorrectMapping(subscriptionType, accessType);

        // Act & Assert
        if (isValidMapping) {
            SubscriptionDTO result = service.createSubscription("test-domain", "test-solution", inputDto);

            assertThat(result).isNotNull();
            assertThat(result.id()).isNotNull();
            assertThat(result.messageType()).isEqualTo(subscriptionType);
            assertThat(result.aclEntryId()).isEqualTo(aclEntryId);
            assertThat(result.queueId()).isEqualTo(queueId);

            verify(repository).save(any(Subscription.class));
        } else {
            assertThatThrownBy(() -> service.createSubscription("test-domain", "test-solution", inputDto))
                    .isInstanceOf(AccessTypeMismatchException.class);

            verify(repository, never()).save(any(Subscription.class));
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<SubscriptionType> subscriptionTypes() {
        return Arbitraries.of(SubscriptionType.values());
    }

    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }

    // ========== Helpers ==========

    /**
     * Returns true iff the (subscriptionType, accessType) pair is a valid mapping.
     */
    private boolean isCorrectMapping(SubscriptionType subscriptionType, AccessType accessType) {
        return (subscriptionType == SubscriptionType.EVENT && accessType == AccessType.SUBSCRIBE)
                || (subscriptionType == SubscriptionType.DELAYED_REQUEST && accessType == AccessType.PROVIDE);
    }

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
            var csConstructor = org.sifunity.broker.subscriptionservice.domain.ContextServiceRef.class.getDeclaredConstructor();
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
