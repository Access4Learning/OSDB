// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.domain.AclEntryRef;
import org.sifunity.broker.subscriptionservice.domain.ContextRef;
import org.sifunity.broker.subscriptionservice.domain.ContextServiceRef;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.domain.ZoneRef;
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Preservation Property Tests for Security Enforcement - Valid ACL Subscription Creation
 *
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on unfixed code.</strong>
 * They capture the baseline behavior that must remain unchanged after the security fix.
 *
 * <p><strong>Purpose:</strong>
 * Verifies that subscription creation with valid ACL entries (correct access type
 * matching the subscription's message type) continues to return 201 Created after
 * the security enforcement fix changes AccessTypeMismatchException from 400 to 403.
 *
 * <p><strong>Validates: Requirement 3.6</strong>
 *
 * @see SubscriptionService
 */
class SecurityEnforcementPreservationTest {

    /**
     * Property 2.4: Preservation - Valid ACL+messageType subscription creation returns 201
     *
     * <p><strong>Validates: Requirement 3.6</strong>
     *
     * <p>For all valid ACL+messageType combinations (EVENT→SUBSCRIBE, DELAYED_REQUEST→PROVIDE),
     * subscription creation succeeds and returns the created subscription DTO.
     * This behavior must be preserved after changing the mismatch response from 400 to 403.
     *
     * @param validMapping a valid (SubscriptionType, AccessType) pair
     * @param queueId randomly generated queue ID
     */
    @Property(tries = 20)
    @Label("Preservation: Valid ACL+messageType subscription creation succeeds")
    void validAclMessageTypeSubscriptionCreationSucceeds(
            @ForAll("validMappings") ValidMapping validMapping,
            @ForAll("uuids") UUID queueId) {

        // Arrange
        UUID aclEntryId = UUID.randomUUID();

        AclEntryRef aclEntry = createAclEntryRef(aclEntryId, "TestService", UUID.randomUUID(),
                validMapping.accessType(), AccessLevel.APPROVED);

        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);

        when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));
        when(queueServiceClient.queueExists(queueId)).thenReturn(true);
        when(repository.existsByAclEntryIdAndQueueIdAndType(aclEntryId, queueId, validMapping.subscriptionType()))
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
                null, queueId, aclEntryId, null, null, null,
                validMapping.subscriptionType(), null);

        // Act
        SubscriptionDTO result = service.createSubscription("test-domain", "test-solution", inputDto);

        // Assert: subscription created successfully
        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.messageType()).isEqualTo(validMapping.subscriptionType());
        assertThat(result.aclEntryId()).isEqualTo(aclEntryId);
        assertThat(result.queueId()).isEqualTo(queueId);

        verify(repository).save(any(Subscription.class));
    }

    // ========== Test Data Generators ==========

    @Provide
    Arbitrary<ValidMapping> validMappings() {
        return Arbitraries.of(
                new ValidMapping(SubscriptionType.EVENT, AccessType.SUBSCRIBE),
                new ValidMapping(SubscriptionType.DELAYED_REQUEST, AccessType.PROVIDE));
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    // ========== Helper Types ==========

    record ValidMapping(SubscriptionType subscriptionType, AccessType accessType) {}

    // ========== Helper Methods ==========

    /**
     * Creates an AclEntryRef using reflection (protected constructor, no setters).
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
            setField(contextServiceRef, "createdAt", Instant.now());

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
