// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

// Feature: acl-context-fk-migration, Property 9: DELAYED_REQUEST subscription query uses exact contextUuid matching

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.subscriptionservice.config.TestConfig;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.health.QueueServiceHealthIndicator;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test: DELAYED_REQUEST subscription query uses exact contextUuid matching.
 *
 * <p><strong>Property 9:</strong> For any (serviceName, contextUuid) pair, the
 * DELAYED_REQUEST subscription query should return only subscriptions where the
 * ACL entry's contextUuid matches exactly. Different contextUuid ACL entries
 * should NOT be included.</p>
 *
 * <p><strong>Validates: Requirements 9.2</strong></p>
 *
 * <p>Uses {@code @DataJpaTest} with Testcontainers PostgreSQL to verify the actual
 * JPQL query behavior against a real database.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:tc:postgresql:15-alpine:///subdelayedtest",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver"
})
@Tag("Feature: acl-context-fk-migration, Property 9: DELAYED_REQUEST subscription query uses exact contextUuid matching")
class DelayedRequestExactContextUuidPropertyTest {

    @Autowired
    private SubscriptionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @MockBean
    private QueueServiceHealthIndicator queueServiceHealthIndicator;

    @MockBean
    private org.springframework.web.client.RestTemplate restTemplate;

    private final Random random = new Random();

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

    /**
     * Property 9: DELAYED_REQUEST subscription query uses exact contextUuid matching.
     *
     * <p>For any (serviceName, contextUuid), the DELAYED_REQUEST query returns:
     * <ol>
     *   <li>Only subscriptions with exact contextUuid match</li>
     *   <li>Excludes subscriptions with a different contextUuid</li>
     * </ol></p>
     *
     * <p><strong>Validates: Requirements 9.2</strong></p>
     */
    @RepeatedTest(100)
    @Tag("acl-context-fk-migration-property-9")
    void delayedRequestQueryReturnsExactMatchOnly() {
        String serviceName = generateServiceName();
        UUID contextUuid = createContext();
        UUID otherContextUuid = createContext();
        UUID anotherContextUuid = createContext();

        // Insert three ACL entries: exact match, and two non-matching contexts
        UUID aclExact = insertAclEntry(serviceName, contextUuid,
                AccessType.PROVIDE, AccessLevel.APPROVED);
        UUID aclOther = insertAclEntry(serviceName, otherContextUuid,
                AccessType.PROVIDE, AccessLevel.APPROVED);
        UUID aclAnother = insertAclEntry(serviceName, anotherContextUuid,
                AccessType.PROVIDE, AccessLevel.APPROVED);

        // Create DELAYED_REQUEST subscriptions for all three
        UUID queueExact = UUID.randomUUID();
        UUID queueOther = UUID.randomUUID();
        UUID queueAnother = UUID.randomUUID();

        repository.saveAndFlush(createSubscription(aclExact, queueExact, SubscriptionType.DELAYED_REQUEST));
        repository.saveAndFlush(createSubscription(aclOther, queueOther, SubscriptionType.DELAYED_REQUEST));
        repository.saveAndFlush(createSubscription(aclAnother, queueAnother, SubscriptionType.DELAYED_REQUEST));

        // Query DELAYED_REQUEST subscriptions for (serviceName, contextUuid)
        List<Object[]> results = repository.findDelayedRequestSubscription(serviceName, contextUuid);

        // Should return exactly 1: only the exact match
        assertThat(results).hasSize(1);

        Subscription returnedSub = (Subscription) results.get(0)[0];
        assertThat(returnedSub.getAclEntryId())
                .as("DELAYED_REQUEST query should return only exact contextUuid match")
                .isEqualTo(aclExact);

        // Non-matching contexts should NOT be in results
        List<UUID> returnedAclIds = results.stream()
                .map(row -> ((Subscription) row[0]).getAclEntryId())
                .toList();

        assertThat(returnedAclIds)
                .as("DELAYED_REQUEST query should exclude non-matching contextUuid")
                .doesNotContain(aclOther, aclAnother);
    }

    // ========== Helpers ==========

    private String generateServiceName() {
        String[] bases = {"StudentPersonal", "SchoolInfo", "StaffPersonal",
                "Assessment", "Gradebook", "Attendance"};
        return bases[random.nextInt(bases.length)] + "-" + System.nanoTime();
    }

    /**
     * Inserts a context_service and then an ACL entry via native SQL (AclEntryRef is @Immutable).
     * The acl_entries table now references context_services via context_service_uuid.
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

    private Subscription createSubscription(UUID aclEntryId, UUID queueId, SubscriptionType type) {
        Subscription subscription = new Subscription();
        subscription.setAclEntryId(aclEntryId);
        subscription.setQueueId(queueId);
        subscription.setType(type);
        return subscription;
    }

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
}
