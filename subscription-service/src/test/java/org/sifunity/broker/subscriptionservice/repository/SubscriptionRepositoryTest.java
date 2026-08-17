// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.subscriptionservice.config.TestConfig;
import org.sifunity.broker.subscriptionservice.domain.AclEntryRef;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.health.QueueServiceHealthIndicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SubscriptionRepository with ACL entry binding.
 *
 * <p>Tests verify the new JPQL JOIN queries that resolve ACL entry fields
 * at query time, as well as the updated duplicate checking and basic queries.</p>
 *
 * <p>Uses Testcontainers PostgreSQL with Hibernate create-drop DDL.
 * ACL entries are inserted via native SQL since AclEntryRef is @Immutable.</p>
 *
 * <p>Validates: Requirements 2.1, 6.1, 6.2, 6.3, 8.1, 8.2, 8.3, 8.4</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:tc:postgresql:15-alpine:///subrepotest",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver"
})
class SubscriptionRepositoryTest {

    @Autowired
    private SubscriptionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @MockBean
    private QueueServiceHealthIndicator queueServiceHealthIndicator;

    @MockBean
    private org.springframework.web.client.RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        entityManager.getEntityManager()
            .createNativeQuery("DELETE FROM acl_entries")
            .executeUpdate();
        entityManager.getEntityManager()
            .createNativeQuery("DELETE FROM context_services")
            .executeUpdate();
        entityManager.flush();
    }

    // ========================================
    // existsByAclEntryIdAndQueueIdAndType
    // ========================================

    @Test
    void existsByAclEntryIdAndQueueIdAndType_returnsTrueWhenExists() {
        UUID contextUuid = createContext();
        UUID aclEntryId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        UUID queueId = UUID.randomUUID();

        Subscription sub = createSubscription(aclEntryId, queueId, SubscriptionType.EVENT);
        repository.saveAndFlush(sub);

        assertThat(repository.existsByAclEntryIdAndQueueIdAndType(aclEntryId, queueId, SubscriptionType.EVENT))
                .isTrue();
    }

    @Test
    void existsByAclEntryIdAndQueueIdAndType_returnsFalseWhenNotExists() {
        assertThat(repository.existsByAclEntryIdAndQueueIdAndType(
                UUID.randomUUID(), UUID.randomUUID(), SubscriptionType.EVENT))
                .isFalse();
    }

    // ========================================
    // findEventSubscriptions
    // ========================================

    @Test
    void findEventSubscriptions_returnsExactContextMatchOnly() {
        UUID contextUuid = createContext();
        UUID otherContextUuid = createContext();

        // Exact context match
        UUID aclExact = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        // Different context — should NOT match
        UUID aclOther = insertAclEntry("StudentPersonal", otherContextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        repository.saveAndFlush(createSubscription(aclExact, UUID.randomUUID(), SubscriptionType.EVENT));
        repository.saveAndFlush(createSubscription(aclOther, UUID.randomUUID(), SubscriptionType.EVENT));

        List<Object[]> results = repository.findEventSubscriptions(
                "StudentPersonal", contextUuid);

        assertThat(results).hasSize(1);

        Subscription returnedSub = (Subscription) results.get(0)[0];
        assertThat(returnedSub.getAclEntryId()).isEqualTo(aclExact);

        // Verify AclEntryRef is populated
        AclEntryRef acl = (AclEntryRef) results.get(0)[1];
        assertThat(acl.getServiceName()).isEqualTo("StudentPersonal");
    }

    @Test
    void findEventSubscriptions_excludesDelayedRequestType() {
        UUID contextUuid = createContext();
        UUID aclId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        repository.saveAndFlush(createSubscription(aclId, UUID.randomUUID(), SubscriptionType.DELAYED_REQUEST));

        List<Object[]> results = repository.findEventSubscriptions(
                "StudentPersonal", contextUuid);

        assertThat(results).isEmpty();
    }

    // ========================================
    // findDelayedRequestSubscription
    // ========================================

    @Test
    void findDelayedRequestSubscription_returnsExactContextMatchOnly() {
        UUID contextUuid = createContext();
        UUID otherContextUuid = createContext();

        UUID aclExact = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.PROVIDE, AccessLevel.APPROVED);
        UUID aclOther = insertAclEntry("StudentPersonal", otherContextUuid,
                AccessType.PROVIDE, AccessLevel.APPROVED);

        repository.saveAndFlush(createSubscription(aclExact, UUID.randomUUID(), SubscriptionType.DELAYED_REQUEST));
        repository.saveAndFlush(createSubscription(aclOther, UUID.randomUUID(), SubscriptionType.DELAYED_REQUEST));

        List<Object[]> results = repository.findDelayedRequestSubscription(
                "StudentPersonal", contextUuid);

        // Only exact match — different context should NOT be returned
        assertThat(results).hasSize(1);
        Subscription sub = (Subscription) results.get(0)[0];
        assertThat(sub.getAclEntryId()).isEqualTo(aclExact);

        AclEntryRef acl = (AclEntryRef) results.get(0)[1];
        assertThat(acl.getContextUuid()).isEqualTo(contextUuid);
    }

    @Test
    void findDelayedRequestSubscription_excludesEventType() {
        UUID contextUuid = createContext();
        UUID aclId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.PROVIDE, AccessLevel.APPROVED);

        repository.saveAndFlush(createSubscription(aclId, UUID.randomUUID(), SubscriptionType.EVENT));

        List<Object[]> results = repository.findDelayedRequestSubscription(
                "StudentPersonal", contextUuid);

        assertThat(results).isEmpty();
    }

    // ========================================
    // findByIdWithAcl
    // ========================================

    @Test
    void findByIdWithAcl_returnsSubscriptionWithAclData() {
        UUID contextUuid = createContext();
        UUID aclId = insertAclEntry("SchoolInfo", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        UUID queueId = UUID.randomUUID();

        Subscription sub = createSubscription(aclId, queueId, SubscriptionType.EVENT);
        Subscription saved = repository.saveAndFlush(sub);

        List<Object[]> results = repository.findByIdWithAcl(saved.getId());

        assertThat(results).hasSize(1);
        Subscription returnedSub = (Subscription) results.get(0)[0];
        AclEntryRef returnedAcl = (AclEntryRef) results.get(0)[1];

        assertThat(returnedSub.getId()).isEqualTo(saved.getId());
        assertThat(returnedSub.getQueueId()).isEqualTo(queueId);
        assertThat(returnedSub.getAclEntryId()).isEqualTo(aclId);
        assertThat(returnedAcl.getServiceName()).isEqualTo("SchoolInfo");
        assertThat(returnedAcl.getContextUuid()).isEqualTo(contextUuid);
    }

    @Test
    void findByIdWithAcl_returnsEmptyForNonExistentId() {
        List<Object[]> results = repository.findByIdWithAcl(UUID.randomUUID());
        assertThat(results).isEmpty();
    }

    // ========================================
    // findByQueueIdWithAcl
    // ========================================

    @Test
    void findByQueueIdWithAcl_returnsAllSubscriptionsForQueueWithAclData() {
        UUID queueId = UUID.randomUUID();
        UUID ctx1 = createContext();
        UUID ctx2 = createContext();

        UUID acl1 = insertAclEntry("StudentPersonal", ctx1,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        UUID acl2 = insertAclEntry("SchoolInfo", ctx2,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        repository.saveAndFlush(createSubscription(acl1, queueId, SubscriptionType.EVENT));
        repository.saveAndFlush(createSubscription(acl2, queueId, SubscriptionType.EVENT));

        // Different queue — should not appear
        UUID otherQueue = UUID.randomUUID();
        UUID ctx3 = createContext();
        UUID acl3 = insertAclEntry("StaffPersonal", ctx3,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        repository.saveAndFlush(createSubscription(acl3, otherQueue, SubscriptionType.EVENT));

        List<Object[]> results = repository.findByQueueIdWithAcl(queueId);

        assertThat(results).hasSize(2);
        for (Object[] row : results) {
            Subscription s = (Subscription) row[0];
            AclEntryRef a = (AclEntryRef) row[1];
            assertThat(s.getQueueId()).isEqualTo(queueId);
            assertThat(a.getServiceName()).isNotNull();
        }
    }

    // ========================================
    // findByQueueId (unchanged basic query)
    // ========================================

    @Test
    void findByQueueId_returnsAllSubscriptionsForQueue() {
        UUID queueId = UUID.randomUUID();
        UUID ctx1 = createContext();
        UUID ctx2 = createContext();

        UUID acl1 = insertAclEntry("Svc1", ctx1, AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        UUID acl2 = insertAclEntry("Svc2", ctx2, AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        repository.saveAndFlush(createSubscription(acl1, queueId, SubscriptionType.EVENT));
        repository.saveAndFlush(createSubscription(acl2, queueId, SubscriptionType.EVENT));

        UUID otherQueue = UUID.randomUUID();
        UUID ctx3 = createContext();
        UUID acl3 = insertAclEntry("Svc3", ctx3, AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        repository.saveAndFlush(createSubscription(acl3, otherQueue, SubscriptionType.EVENT));

        List<Subscription> results = repository.findByQueueId(queueId);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Subscription::getQueueId).containsOnly(queueId);
    }

    @Test
    void findByQueueId_returnsEmptyWhenNoSubscriptions() {
        List<Subscription> results = repository.findByQueueId(UUID.randomUUID());
        assertThat(results).isEmpty();
    }

    // ========================================
    // Helpers
    // ========================================

    /**
     * Inserts a context_service and then an ACL entry via native SQL (AclEntryRef is @Immutable).
     * The acl_entries table now references context_services via context_service_uuid.
     * Returns the generated ACL entry UUID.
     */
    private UUID insertAclEntry(String serviceName, UUID contextUuid,
                                AccessType accessType, AccessLevel accessLevel) {
        // Insert context_service record first
        UUID contextServiceId = UUID.randomUUID();
        entityManager.getEntityManager()
            .createNativeQuery(
                "INSERT INTO context_services (id, context_uuid, service_name, service_type, created_at) " +
                "VALUES (:id, :ctx, :sn, :st, NOW())")
            .setParameter("id", contextServiceId)
            .setParameter("ctx", contextUuid)
            .setParameter("sn", serviceName)
            .setParameter("st", "OBJECT")
            .executeUpdate();

        // Insert acl_entry referencing the context_service
        UUID id = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        entityManager.getEntityManager()
            .createNativeQuery(
                "INSERT INTO acl_entries (id, environment_id, context_service_uuid, access_type, access_level) " +
                "VALUES (:id, :envId, :csId, :at, :al)")
            .setParameter("id", id)
            .setParameter("envId", environmentId)
            .setParameter("csId", contextServiceId)
            .setParameter("at", accessType.name())
            .setParameter("al", accessLevel.name())
            .executeUpdate();
        entityManager.flush();
        return id;
    }

    /**
     * Creates a context (with required zone parent) in the database and returns its UUID.
     * Needed because Hibernate create-drop enforces FK constraints on context_uuid.
     */
    private UUID createContext() {
        UUID solutionUuid = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        entityManager.getEntityManager()
            .createNativeQuery("INSERT INTO zones (id, solution_uuid, zone_id) VALUES (:id, :solutionUuid, :zoneId)")
            .setParameter("id", zoneId)
            .setParameter("solutionUuid", solutionUuid)
            .setParameter("zoneId", "zone-" + zoneId)
            .executeUpdate();

        UUID contextId = UUID.randomUUID();
        entityManager.getEntityManager()
            .createNativeQuery("INSERT INTO contexts (id, zone_uuid, context_id) VALUES (:id, :zoneUuid, :contextId)")
            .setParameter("id", contextId)
            .setParameter("zoneUuid", zoneId)
            .setParameter("contextId", "ctx-" + contextId)
            .executeUpdate();
        entityManager.flush();
        return contextId;
    }

    private Subscription createSubscription(UUID aclEntryId, UUID queueId, SubscriptionType type) {
        Subscription subscription = new Subscription();
        subscription.setAclEntryId(aclEntryId);
        subscription.setQueueId(queueId);
        subscription.setType(type);
        return subscription;
    }
}
