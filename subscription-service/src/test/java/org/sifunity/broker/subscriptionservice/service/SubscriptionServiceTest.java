// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.common.exception.SubscriptionNotFoundException;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.domain.AclEntryRef;
import org.sifunity.broker.subscriptionservice.domain.ContextRef;
import org.sifunity.broker.subscriptionservice.domain.ContextServiceRef;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.domain.ZoneRef;
import org.sifunity.broker.subscriptionservice.exception.AccessTypeMismatchException;
import org.sifunity.broker.subscriptionservice.exception.AclEntryNotFoundException;
import org.sifunity.broker.subscriptionservice.exception.AclEntryNotHeldException;
import org.sifunity.broker.subscriptionservice.exception.DuplicateSubscriptionException;
import org.sifunity.broker.subscriptionservice.repository.AclEntryLookupRepository;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService Unit Tests")
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository repository;
    @Mock private QueueServiceClient queueServiceClient;
    @Mock private AclEntryLookupRepository aclEntryLookupRepository;
    @InjectMocks private SubscriptionService subscriptionService;

    private UUID queueId;
    private UUID subscriptionId;
    private UUID aclEntryId;
    private String serviceName;
    private UUID contextUuid;
    private String zoneId;
    private String contextId;
    private String domainId;
    private String solutionId;

    @BeforeEach
    void setUp() {
        queueId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        aclEntryId = UUID.randomUUID();
        serviceName = "StudentPersonal";
        contextUuid = UUID.randomUUID();
        zoneId = "test-zone";
        contextId = "test-context";
        domainId = "test-domain";
        solutionId = "test-solution";
    }

    @Nested
    @DisplayName("Create Subscription Tests")
    class CreateSubscriptionTests {

        @Test
        @DisplayName("Should create EVENT subscription with SUBSCRIBE ACL entry via aclEntryId")
        void shouldCreateEventSubscriptionWithSubscribeAclEntry() {
            AclEntryRef aclEntry = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.SUBSCRIBE, AccessLevel.APPROVED, zoneId, contextId);
            SubscriptionDTO inputDto = new SubscriptionDTO(
                    null, queueId, aclEntryId, null, null, null, SubscriptionType.EVENT, null);

            Subscription savedSubscription = createSubscriptionEntity(aclEntryId);
            savedSubscription.setId(subscriptionId);
            savedSubscription.setCreatedAt(Instant.now());

            when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));
            when(queueServiceClient.queueExists(queueId)).thenReturn(true);
            when(repository.existsByAclEntryIdAndQueueIdAndType(aclEntryId, queueId, SubscriptionType.EVENT))
                    .thenReturn(false);
            when(repository.save(any(Subscription.class))).thenReturn(savedSubscription);

            SubscriptionDTO result = subscriptionService.createSubscription(domainId, solutionId, inputDto);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(subscriptionId);
            assertThat(result.queueId()).isEqualTo(queueId);
            assertThat(result.aclEntryId()).isEqualTo(aclEntryId);
            assertThat(result.serviceName()).isEqualTo(serviceName);
            assertThat(result.zoneId()).isEqualTo(zoneId);
            assertThat(result.contextId()).isEqualTo(contextId);
            assertThat(result.messageType()).isEqualTo(SubscriptionType.EVENT);
            assertThat(result.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("Should create DELAYED_REQUEST subscription with PROVIDE ACL entry via aclEntryId")
        void shouldCreateDelayedRequestSubscriptionWithProvideAclEntry() {
            AclEntryRef aclEntry = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.PROVIDE, AccessLevel.APPROVED, zoneId, contextId);
            SubscriptionDTO inputDto = new SubscriptionDTO(
                    null, queueId, aclEntryId, null, null, null, SubscriptionType.DELAYED_REQUEST, null);

            Subscription savedSubscription = createSubscriptionEntity(aclEntryId);
            savedSubscription.setId(subscriptionId);
            savedSubscription.setType(SubscriptionType.DELAYED_REQUEST);
            savedSubscription.setCreatedAt(Instant.now());

            when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));
            when(queueServiceClient.queueExists(queueId)).thenReturn(true);
            when(repository.existsByAclEntryIdAndQueueIdAndType(aclEntryId, queueId, SubscriptionType.DELAYED_REQUEST))
                    .thenReturn(false);
            when(repository.save(any(Subscription.class))).thenReturn(savedSubscription);

            SubscriptionDTO result = subscriptionService.createSubscription(domainId, solutionId, inputDto);

            assertThat(result).isNotNull();
            assertThat(result.messageType()).isEqualTo(SubscriptionType.DELAYED_REQUEST);
            assertThat(result.aclEntryId()).isEqualTo(aclEntryId);
            assertThat(result.serviceName()).isEqualTo(serviceName);
        }

        @Test
        @DisplayName("Should throw AccessTypeMismatchException when EVENT subscription references PROVIDE ACL entry")
        void shouldThrowAccessTypeMismatchForEventWithProvideAcl() {
            AclEntryRef aclEntry = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.PROVIDE, AccessLevel.APPROVED, zoneId, contextId);
            SubscriptionDTO inputDto = new SubscriptionDTO(
                    null, queueId, aclEntryId, null, null, null, SubscriptionType.EVENT, null);

            when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));

            assertThatThrownBy(() -> subscriptionService.createSubscription(domainId, solutionId, inputDto))
                    .isInstanceOf(AccessTypeMismatchException.class)
                    .hasMessageContaining("EVENT").hasMessageContaining("PROVIDE").hasMessageContaining("SUBSCRIBE");
            verify(queueServiceClient, never()).queueExists(any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessTypeMismatchException when DELAYED_REQUEST subscription references SUBSCRIBE ACL entry")
        void shouldThrowAccessTypeMismatchForDelayedRequestWithSubscribeAcl() {
            AclEntryRef aclEntry = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.SUBSCRIBE, AccessLevel.APPROVED, zoneId, contextId);
            SubscriptionDTO inputDto = new SubscriptionDTO(
                    null, queueId, aclEntryId, null, null, null, SubscriptionType.DELAYED_REQUEST, null);

            when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));

            assertThatThrownBy(() -> subscriptionService.createSubscription(domainId, solutionId, inputDto))
                    .isInstanceOf(AccessTypeMismatchException.class)
                    .hasMessageContaining("DELAYED_REQUEST").hasMessageContaining("SUBSCRIBE").hasMessageContaining("PROVIDE");
            verify(queueServiceClient, never()).queueExists(any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AclEntryNotFoundException when aclEntryId does not exist")
        void shouldThrowAclEntryNotFoundForNonExistentAclEntryId() {
            UUID nonExistentId = UUID.randomUUID();
            SubscriptionDTO inputDto = new SubscriptionDTO(
                    null, queueId, nonExistentId, null, null, null, SubscriptionType.EVENT, null);

            when(aclEntryLookupRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.createSubscription(domainId, solutionId, inputDto))
                    .isInstanceOf(AclEntryNotFoundException.class)
                    .hasMessageContaining("ACL entry not found: " + nonExistentId);
            verify(queueServiceClient, never()).queueExists(any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should create subscription via serviceName/zoneId/contextId resolution")
        void shouldCreateSubscriptionViaServiceNameResolution() {
            AclEntryRef aclEntry = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.SUBSCRIBE, AccessLevel.APPROVED, zoneId, contextId);
            SubscriptionDTO inputDto = new SubscriptionDTO(
                    null, queueId, null, serviceName, zoneId, contextId, SubscriptionType.EVENT, null);

            Subscription savedSubscription = createSubscriptionEntity(aclEntryId);
            savedSubscription.setId(subscriptionId);
            savedSubscription.setCreatedAt(Instant.now());

            when(aclEntryLookupRepository.findByServiceNameAndZoneContextAndAccessType(
                    serviceName, domainId, solutionId, zoneId, contextId, AccessType.SUBSCRIBE))
                    .thenReturn(Optional.of(aclEntry));
            when(queueServiceClient.queueExists(queueId)).thenReturn(true);
            when(repository.existsByAclEntryIdAndQueueIdAndType(aclEntryId, queueId, SubscriptionType.EVENT))
                    .thenReturn(false);
            when(repository.save(any(Subscription.class))).thenReturn(savedSubscription);

            SubscriptionDTO result = subscriptionService.createSubscription(domainId, solutionId, inputDto);

            assertThat(result).isNotNull();
            assertThat(result.aclEntryId()).isEqualTo(aclEntryId);
            assertThat(result.serviceName()).isEqualTo(serviceName);
            assertThat(result.zoneId()).isEqualTo(zoneId);
            assertThat(result.contextId()).isEqualTo(contextId);
            verify(aclEntryLookupRepository).findByServiceNameAndZoneContextAndAccessType(
                    serviceName, domainId, solutionId, zoneId, contextId, AccessType.SUBSCRIBE);
            verify(aclEntryLookupRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw AclEntryNotHeldException when serviceName/zoneId/contextId has no matching ACL")
        void shouldThrowAclEntryNotHeldForNoMatchingServiceNameResolution() {
            SubscriptionDTO inputDto = new SubscriptionDTO(
                    null, queueId, null, serviceName, zoneId, contextId, SubscriptionType.EVENT, null);

            when(aclEntryLookupRepository.findByServiceNameAndZoneContextAndAccessType(
                    serviceName, domainId, solutionId, zoneId, contextId, AccessType.SUBSCRIBE))
                    .thenReturn(Optional.empty());

            // D7 (2.16): "the caller holds no matching grant" is an authorization outcome, so it now
            // throws AclEntryNotHeldException (-> 403) rather than AclEntryNotFoundException (-> 400).
            // The latter is reserved for a caller-supplied aclEntryId that does not resolve.
            assertThatThrownBy(() -> subscriptionService.createSubscription(domainId, solutionId, inputDto))
                    .isInstanceOf(AclEntryNotHeldException.class)
                    .hasMessageContaining("No matching ACL entry found");
            verify(queueServiceClient, never()).queueExists(any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw QueueNotFoundException when queue does not exist")
        void shouldThrowQueueNotFoundExceptionWhenQueueDoesNotExist() {
            AclEntryRef aclEntry = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.SUBSCRIBE, AccessLevel.APPROVED, zoneId, contextId);
            SubscriptionDTO inputDto = new SubscriptionDTO(
                    null, queueId, aclEntryId, null, null, null, SubscriptionType.EVENT, null);

            when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));
            when(queueServiceClient.queueExists(queueId)).thenReturn(false);

            assertThatThrownBy(() -> subscriptionService.createSubscription(domainId, solutionId, inputDto))
                    .isInstanceOf(QueueNotFoundException.class)
                    .hasMessageContaining("Queue not found: " + queueId);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw DuplicateSubscriptionException when subscription already exists")
        void shouldThrowDuplicateSubscriptionExceptionForDuplicates() {
            AclEntryRef aclEntry = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.SUBSCRIBE, AccessLevel.APPROVED, zoneId, contextId);
            SubscriptionDTO inputDto = new SubscriptionDTO(
                    null, queueId, aclEntryId, null, null, null, SubscriptionType.EVENT, null);

            when(aclEntryLookupRepository.findById(aclEntryId)).thenReturn(Optional.of(aclEntry));
            when(queueServiceClient.queueExists(queueId)).thenReturn(true);
            when(repository.existsByAclEntryIdAndQueueIdAndType(aclEntryId, queueId, SubscriptionType.EVENT))
                    .thenReturn(true);

            assertThatThrownBy(() -> subscriptionService.createSubscription(domainId, solutionId, inputDto))
                    .isInstanceOf(DuplicateSubscriptionException.class)
                    .hasMessageContaining("Subscription already exists");
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Find EVENT Subscriptions Tests")
    class FindEventSubscriptionsTests {

        @Test
        @DisplayName("Should return subscriptions from JOIN query when contextUuid is provided")
        void shouldReturnSubscriptionsFromJoinQueryForEventSubscriptions() {
            AclEntryRef exactAcl = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.SUBSCRIBE, AccessLevel.APPROVED, zoneId, contextId);
            Subscription exactSub = createSubscriptionEntity(aclEntryId);
            exactSub.setId(UUID.randomUUID());

            List<Object[]> joinResults = Collections.singletonList(
                    new Object[]{exactSub, exactAcl});

            when(repository.findEventSubscriptions(serviceName, contextUuid))
                    .thenReturn(joinResults);

            List<SubscriptionDTO> results = subscriptionService.findEventSubscriptions(serviceName, contextUuid);

            assertThat(results).hasSize(1);
            assertThat(results).extracting(SubscriptionDTO::zoneId)
                    .containsExactly(zoneId);
            assertThat(results).extracting(SubscriptionDTO::serviceName)
                    .containsOnly(serviceName);
        }

        @Test
        @DisplayName("Should return empty list when no EVENT subscriptions match")
        void shouldReturnEmptyListWhenNoEventSubscriptionsMatch() {
            when(repository.findEventSubscriptions(serviceName, contextUuid))
                    .thenReturn(Collections.emptyList());
            List<SubscriptionDTO> results = subscriptionService.findEventSubscriptions(serviceName, contextUuid);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Find DELAYED_REQUEST Subscription Tests")
    class FindDelayedRequestSubscriptionTests {

        @Test
        @DisplayName("Should return DELAYED_REQUEST subscription from JOIN query")
        void shouldReturnDelayedRequestSubscriptionFromJoinQuery() {
            AclEntryRef aclEntry = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.PROVIDE, AccessLevel.APPROVED, zoneId, contextId);
            Subscription subscription = createSubscriptionEntity(aclEntryId);
            subscription.setId(subscriptionId);
            subscription.setType(SubscriptionType.DELAYED_REQUEST);

            when(repository.findDelayedRequestSubscription(serviceName, contextUuid))
                    .thenReturn(Collections.singletonList(new Object[]{subscription, aclEntry}));

            SubscriptionDTO result = subscriptionService.findDelayedRequestSubscription(serviceName, contextUuid);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(subscriptionId);
            assertThat(result.messageType()).isEqualTo(SubscriptionType.DELAYED_REQUEST);
            assertThat(result.serviceName()).isEqualTo(serviceName);
            assertThat(result.zoneId()).isEqualTo(zoneId);
            assertThat(result.contextId()).isEqualTo(contextId);
        }

        @Test
        @DisplayName("Should throw SubscriptionNotFoundException when no DELAYED_REQUEST subscription exists")
        void shouldThrowSubscriptionNotFoundExceptionWhenNoDelayedRequestExists() {
            when(repository.findDelayedRequestSubscription(serviceName, contextUuid))
                    .thenReturn(Collections.emptyList());
            assertThatThrownBy(() -> subscriptionService.findDelayedRequestSubscription(serviceName, contextUuid))
                    .isInstanceOf(SubscriptionNotFoundException.class)
                    .hasMessageContaining("No DELAYED_REQUEST subscription found");
        }
    }

    @Nested
    @DisplayName("Get Subscription Tests")
    class GetSubscriptionTests {

        @Test
        @DisplayName("Should return subscription with ACL data from JOIN query")
        void shouldReturnSubscriptionWithAclDataFromJoinQuery() {
            AclEntryRef aclEntry = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.SUBSCRIBE, AccessLevel.APPROVED, zoneId, contextId);
            Subscription subscription = createSubscriptionEntity(aclEntryId);
            subscription.setId(subscriptionId);

            when(repository.findByIdWithAcl(subscriptionId))
                    .thenReturn(Collections.singletonList(new Object[]{subscription, aclEntry}));

            SubscriptionDTO result = subscriptionService.getSubscription(subscriptionId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(subscriptionId);
            assertThat(result.queueId()).isEqualTo(queueId);
            assertThat(result.aclEntryId()).isEqualTo(aclEntryId);
            assertThat(result.serviceName()).isEqualTo(serviceName);
            assertThat(result.zoneId()).isEqualTo(zoneId);
            assertThat(result.contextId()).isEqualTo(contextId);
        }

        @Test
        @DisplayName("Should throw SubscriptionNotFoundException when ID does not exist")
        void shouldThrowSubscriptionNotFoundExceptionWhenIdDoesNotExist() {
            when(repository.findByIdWithAcl(subscriptionId)).thenReturn(Collections.emptyList());
            assertThatThrownBy(() -> subscriptionService.getSubscription(subscriptionId))
                    .isInstanceOf(SubscriptionNotFoundException.class)
                    .hasMessageContaining("Subscription not found: " + subscriptionId);
        }
    }

    @Nested
    @DisplayName("Find Subscriptions By Queue Tests")
    class FindSubscriptionsByQueueTests {

        @Test
        @DisplayName("Should return all subscriptions for a queue from JOIN query")
        void shouldReturnAllSubscriptionsForQueueFromJoinQuery() {
            AclEntryRef aclEntry1 = createAclEntryRef(aclEntryId, serviceName, contextUuid,
                    AccessType.SUBSCRIBE, AccessLevel.APPROVED, zoneId, contextId);
            Subscription sub1 = createSubscriptionEntity(aclEntryId);
            sub1.setId(UUID.randomUUID());
            sub1.setType(SubscriptionType.EVENT);

            UUID aclEntryId2 = UUID.randomUUID();
            AclEntryRef aclEntry2 = createAclEntryRef(aclEntryId2, serviceName, contextUuid,
                    AccessType.PROVIDE, AccessLevel.APPROVED, zoneId, contextId);
            Subscription sub2 = createSubscriptionEntity(aclEntryId2);
            sub2.setId(UUID.randomUUID());
            sub2.setType(SubscriptionType.DELAYED_REQUEST);

            when(repository.findByQueueIdWithAcl(queueId)).thenReturn(Arrays.asList(
                    new Object[]{sub1, aclEntry1}, new Object[]{sub2, aclEntry2}));

            List<SubscriptionDTO> results = subscriptionService.findSubscriptionsByQueue(queueId);

            assertThat(results).hasSize(2);
            assertThat(results).extracting(SubscriptionDTO::queueId).containsOnly(queueId);
            assertThat(results).extracting(SubscriptionDTO::messageType)
                    .containsExactlyInAnyOrder(SubscriptionType.EVENT, SubscriptionType.DELAYED_REQUEST);
        }

        @Test
        @DisplayName("Should return empty list when queue has no subscriptions")
        void shouldReturnEmptyListWhenQueueHasNoSubscriptions() {
            when(repository.findByQueueIdWithAcl(queueId)).thenReturn(Collections.emptyList());
            List<SubscriptionDTO> results = subscriptionService.findSubscriptionsByQueue(queueId);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Delete Subscription Tests")
    class DeleteSubscriptionTests {

        @Test
        void shouldDeleteSubscriptionWhenIdExists() {
            when(repository.existsById(subscriptionId)).thenReturn(true);
            doNothing().when(repository).deleteById(subscriptionId);
            subscriptionService.deleteSubscription(subscriptionId);
            verify(repository).existsById(subscriptionId);
            verify(repository).deleteById(subscriptionId);
        }

        @Test
        void shouldThrowSubscriptionNotFoundExceptionWhenDeletingNonExistentId() {
            when(repository.existsById(subscriptionId)).thenReturn(false);
            assertThatThrownBy(() -> subscriptionService.deleteSubscription(subscriptionId))
                    .isInstanceOf(SubscriptionNotFoundException.class);
            verify(repository, never()).deleteById(any());
        }
    }

    // ========== Helpers ==========

    private Subscription createSubscriptionEntity(UUID aclEntryId) {
        Subscription subscription = new Subscription();
        subscription.setAclEntryId(aclEntryId);
        subscription.setQueueId(queueId);
        subscription.setType(SubscriptionType.EVENT);
        subscription.setCreatedAt(Instant.now());
        return subscription;
    }

    private AclEntryRef createAclEntryRef(UUID id, String serviceName, UUID contextUuid,
                                           AccessType accessType, AccessLevel accessLevel,
                                           String zoneIdStr, String contextIdStr) {
        try {
            AclEntryRef ref = newInstance(AclEntryRef.class);
            setField(ref, "id", id);
            setField(ref, "accessType", accessType);
            setField(ref, "accessLevel", accessLevel);

            // Build ContextServiceRef with serviceName and contextUuid
            ContextServiceRef contextService =
                newInstance(ContextServiceRef.class);
            UUID contextServiceId = UUID.randomUUID();
            setField(contextService, "id", contextServiceId);
            setField(contextService, "serviceName", serviceName);
            setField(contextService, "contextUuid", contextUuid);
            setField(contextService, "serviceType", "OBJECT");

            if (contextUuid != null && zoneIdStr != null) {
                ZoneRef zone = newInstance(ZoneRef.class);
                setField(zone, "id", UUID.randomUUID());
                setField(zone, "zoneId", zoneIdStr);

                ContextRef context = newInstance(ContextRef.class);
                setField(context, "id", contextUuid);
                setField(context, "contextId", contextIdStr);
                setField(context, "zone", zone);

                setField(contextService, "context", context);
            }

            setField(ref, "contextServiceUuid", contextServiceId);
            setField(ref, "contextService", contextService);

            return ref;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create AclEntryRef via reflection", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T newInstance(Class<T> clazz) throws Exception {
        var constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
