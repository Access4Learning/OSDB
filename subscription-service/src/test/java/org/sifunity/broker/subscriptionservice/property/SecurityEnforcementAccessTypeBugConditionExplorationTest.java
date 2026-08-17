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
import org.sifunity.broker.subscriptionservice.domain.ZoneRef;
import org.sifunity.broker.subscriptionservice.exception.AccessTypeMismatchException;
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug Condition Exploration Test for Security Enforcement Gap 4.
 *
 * <p><strong>CRITICAL: This test MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Gap 4: When an environment with a QUERY ACL entry POSTs a subscription with EVENT messageType,
 * the system currently returns HTTP 400 (AccessTypeMismatchException maps to BAD_REQUEST).
 * It should return HTTP 403 Forbidden because this is an authorization failure, not a validation error.</p>
 *
 * <p>The root cause is that AccessTypeMismatchException hardcodes HttpStatus.BAD_REQUEST.
 * After the fix, it should use HttpStatus.FORBIDDEN.</p>
 *
 * <p><strong>Validates: Requirements 1.4</strong></p>
 */
class SecurityEnforcementAccessTypeBugConditionExplorationTest {

    /**
     * Property 1: Bug Condition — Access type mismatch should return 403 (not 400).
     *
     * <p>For any subscription creation request where the ACL entry's access type does NOT
     * match the required type for the subscription's message type (e.g., QUERY ACL for
     * EVENT subscription which requires SUBSCRIBE), the system SHALL return HTTP 403 Forbidden.</p>
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — AccessTypeMismatchException has HTTP 400.</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — AccessTypeMismatchException has HTTP 403.</p>
     *
     * <p><strong>Validates: Requirements 1.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Gap 4: Access type mismatch should return 403 Forbidden (currently returns 400)")
    void accessTypeMismatchShouldReturn403(
            @ForAll("environmentTypes") String environmentType,
            @ForAll("mismatchedPairs") MismatchedPair pair) {

        // Arrange: Create an ACL entry with the WRONG access type for the subscription type
        UUID aclEntryId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        AclEntryRef aclEntry = createAclEntryRef(aclEntryId, "TestService",
                UUID.randomUUID(), pair.actualAccessType(), AccessLevel.APPROVED);

        AclEntryLookupRepository aclEntryLookupRepository = mock(AclEntryLookupRepository.class);
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);

        when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));

        SubscriptionService service = new SubscriptionService(
                repository, queueServiceClient, aclEntryLookupRepository);

        SubscriptionDTO inputDto = new SubscriptionDTO(
                null, queueId, aclEntryId, null, null, null, pair.subscriptionType(), null);

        // Act: Attempt to create subscription with mismatched access type
        AccessTypeMismatchException exception = catchThrowableOfType(
                () -> service.createSubscription("test-domain", "test-solution", inputDto),
                AccessTypeMismatchException.class);

        // Assert: The exception should exist (access type mismatch detected)
        assertThat(exception)
                .as("AccessTypeMismatchException should be thrown for %s subscription with %s ACL",
                        pair.subscriptionType(), pair.actualAccessType())
                .isNotNull();

        // Assert: The HTTP status should be 403 FORBIDDEN (not 400 BAD_REQUEST)
        // On UNFIXED code: this will FAIL because exception has HTTP 400
        // On FIXED code: this will PASS because exception has HTTP 403
        assertThat(exception.getHttpStatus())
                .as("Access type mismatch is an authorization failure and should return 403 Forbidden, "
                        + "not 400 Bad Request (environment type: %s, subscription: %s, ACL: %s)",
                        environmentType, pair.subscriptionType(), pair.actualAccessType())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> environmentTypes() {
        return Arbitraries.of("DIRECT", "BROKERED");
    }

    /**
     * Generates (SubscriptionType, AccessType) pairs that are MISMATCHED.
     * EVENT requires SUBSCRIBE, DELAYED_REQUEST requires PROVIDE.
     * Any other combination is a mismatch.
     */
    @Provide
    Arbitrary<MismatchedPair> mismatchedPairs() {
        return Arbitraries.of(
                // EVENT subscription with non-SUBSCRIBE ACL types
                new MismatchedPair(SubscriptionType.EVENT, AccessType.QUERY),
                new MismatchedPair(SubscriptionType.EVENT, AccessType.PROVIDE),
                new MismatchedPair(SubscriptionType.EVENT, AccessType.UPDATE),
                new MismatchedPair(SubscriptionType.EVENT, AccessType.DELETE),
                new MismatchedPair(SubscriptionType.EVENT, AccessType.CREATE),
                // DELAYED_REQUEST subscription with non-PROVIDE ACL types
                new MismatchedPair(SubscriptionType.DELAYED_REQUEST, AccessType.QUERY),
                new MismatchedPair(SubscriptionType.DELAYED_REQUEST, AccessType.SUBSCRIBE),
                new MismatchedPair(SubscriptionType.DELAYED_REQUEST, AccessType.UPDATE),
                new MismatchedPair(SubscriptionType.DELAYED_REQUEST, AccessType.DELETE),
                new MismatchedPair(SubscriptionType.DELAYED_REQUEST, AccessType.CREATE)
        );
    }

    // ========== Helpers ==========

    record MismatchedPair(SubscriptionType subscriptionType, AccessType actualAccessType) {
        @Override
        public String toString() {
            return String.format("MismatchedPair{subscription=%s, acl=%s}", subscriptionType, actualAccessType);
        }
    }

    /**
     * Creates an AclEntryRef using reflection (protected constructor).
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
