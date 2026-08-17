// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.domain.Subscription;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for caching functionality in the Subscription Service.
 * 
 * <p>These tests verify that:</p>
 * <ul>
 *   <li>Query results are cached</li>
 *   <li>Cache is invalidated on subscription creation</li>
 *   <li>Cache is invalidated on subscription deletion</li>
 * </ul>
 * 
 * <p>Uses Testcontainers PostgreSQL with Flyway migrations. ACL entries are
 * inserted via JDBC since AclEntryRef is @Immutable.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Caching Integration Tests")
class CachingIntegrationTest {
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    
    @Autowired
    private CacheManager cacheManager;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @MockBean
    private QueueServiceClient queueServiceClient;

    private UUID contextUuid;

    @BeforeEach
    void setUp() {
        // Clear database (subscriptions first due to FK)
        subscriptionRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM acl_entries");
        jdbcTemplate.execute("DELETE FROM context_services");
        jdbcTemplate.execute("DELETE FROM environments");
        jdbcTemplate.execute("DELETE FROM solutions");
        jdbcTemplate.execute("DELETE FROM domains");
        
        // Clear all caches
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
        
        // Mock queue service to always return true
        when(queueServiceClient.queueExists(any(UUID.class))).thenReturn(true);

        // Create a shared context UUID for tests
        contextUuid = createContextForAcl();
    }
    
    @Test
    @DisplayName("findEventSubscriptions should cache results")
    void testFindEventSubscriptionsCachesResults() {
        // Given: An ACL entry and subscription exist
        UUID aclEntryId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        UUID queueId = UUID.randomUUID();

        Subscription subscription = new Subscription();
        subscription.setQueueId(queueId);
        subscription.setAclEntryId(aclEntryId);
        subscription.setType(SubscriptionType.EVENT);
        subscriptionRepository.save(subscription);
        
        // When: Query is executed first time
        List<SubscriptionDTO> firstResult = subscriptionService.findEventSubscriptions(
                "StudentPersonal", contextUuid);
        
        // Then: Result should be returned
        assertThat(firstResult).hasSize(1);
        assertThat(firstResult.get(0).serviceName()).isEqualTo("StudentPersonal");
        
        // When: Delete the subscription from database directly (bypassing service)
        subscriptionRepository.deleteAll();
        
        // And: Query is executed second time
        List<SubscriptionDTO> secondResult = subscriptionService.findEventSubscriptions(
                "StudentPersonal", contextUuid);
        
        // Then: Cached result should be returned (still has 1 item even though DB is empty)
        assertThat(secondResult).hasSize(1);
        assertThat(secondResult.get(0).serviceName()).isEqualTo("StudentPersonal");
        
        // Verify: Cache contains the result
        var cache = cacheManager.getCache("subscriptions");
        assertThat(cache).isNotNull();
        String cacheKey = "events:StudentPersonal:" + contextUuid;
        assertThat(cache.get(cacheKey)).isNotNull();
    }
    
    @Test
    @DisplayName("Cache should be invalidated on createSubscription")
    void testCacheInvalidatedOnCreate() {
        // Given: An ACL entry exists
        UUID aclEntryId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        UUID queueId1 = UUID.randomUUID();
        SubscriptionDTO dto1 = new SubscriptionDTO(
                null, queueId1, aclEntryId,
                null, null, null,
                SubscriptionType.EVENT, null);
        subscriptionService.createSubscription("test-domain", "test-solution", dto1);
        
        // Query to populate cache
        List<SubscriptionDTO> firstResult = subscriptionService.findEventSubscriptions(
                "StudentPersonal", contextUuid);
        assertThat(firstResult).hasSize(1);
        
        // When: Create a new subscription (should invalidate cache)
        UUID queueId2 = UUID.randomUUID();
        SubscriptionDTO dto2 = new SubscriptionDTO(
                null, queueId2, aclEntryId,
                null, null, null,
                SubscriptionType.EVENT, null);
        subscriptionService.createSubscription("test-domain", "test-solution", dto2);
        
        // Then: Query should return fresh data from database (2 subscriptions)
        List<SubscriptionDTO> secondResult = subscriptionService.findEventSubscriptions(
                "StudentPersonal", contextUuid);
        assertThat(secondResult).hasSize(2);
    }
    
    @Test
    @DisplayName("Cache should be invalidated on deleteSubscription")
    void testCacheInvalidatedOnDelete() {
        // Given: An ACL entry and two subscriptions exist
        UUID aclEntryId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        
        SubscriptionDTO dto1 = new SubscriptionDTO(
                null, queueId1, aclEntryId,
                null, null, null,
                SubscriptionType.EVENT, null);
        SubscriptionDTO created1 = subscriptionService.createSubscription("test-domain", "test-solution", dto1);
        
        SubscriptionDTO dto2 = new SubscriptionDTO(
                null, queueId2, aclEntryId,
                null, null, null,
                SubscriptionType.EVENT, null);
        subscriptionService.createSubscription("test-domain", "test-solution", dto2);
        
        // Query to populate cache
        List<SubscriptionDTO> firstResult = subscriptionService.findEventSubscriptions(
                "StudentPersonal", contextUuid);
        assertThat(firstResult).hasSize(2);
        
        // When: Delete one subscription (should invalidate cache)
        subscriptionService.deleteSubscription(created1.id());
        
        // Then: Query should return fresh data from database (1 subscription)
        List<SubscriptionDTO> secondResult = subscriptionService.findEventSubscriptions(
                "StudentPersonal", contextUuid);
        assertThat(secondResult).hasSize(1);
    }
    
    @Test
    @DisplayName("findDelayedRequestSubscription should cache results")
    void testFindDelayedRequestSubscriptionCachesResults() {
        // Given: An ACL entry and DELAYED_REQUEST subscription exist
        UUID aclEntryId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.PROVIDE, AccessLevel.APPROVED);
        UUID queueId = UUID.randomUUID();

        Subscription subscription = new Subscription();
        subscription.setQueueId(queueId);
        subscription.setAclEntryId(aclEntryId);
        subscription.setType(SubscriptionType.DELAYED_REQUEST);
        subscriptionRepository.save(subscription);
        
        // When: Query is executed first time
        SubscriptionDTO firstResult = subscriptionService.findDelayedRequestSubscription(
                "StudentPersonal", contextUuid);
        
        // Then: Result should be returned
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.serviceName()).isEqualTo("StudentPersonal");
        
        // When: Delete the subscription from database directly (bypassing service)
        subscriptionRepository.deleteAll();
        
        // And: Query is executed second time
        SubscriptionDTO secondResult = subscriptionService.findDelayedRequestSubscription(
                "StudentPersonal", contextUuid);
        
        // Then: Cached result should be returned
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.serviceName()).isEqualTo("StudentPersonal");
    }
    
    // ========================================
    // Helpers
    // ========================================

    /**
     * Creates a valid environment_id by inserting the required domain → solution → environment
     * hierarchy. Returns the environment UUID for use as a FK in acl_entries.
     */
    private UUID createEnvironmentForAcl() {
        UUID domainId = UUID.randomUUID();
        String domainIdStr = "test-domain-" + domainId;
        jdbcTemplate.update(
            "INSERT INTO domains (id, domain_id, description, created_at) VALUES (?, ?, ?, NOW())",
            domainId, domainIdStr, "Test domain"
        );

        UUID solutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO solutions (id, domain_uuid, solution_id, description, created_at) VALUES (?, ?, ?, ?, NOW())",
            solutionId, domainId, "test-solution-" + solutionId, "Test solution"
        );

        UUID environmentId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO environments (id, application_name, session_token, shared_secret, created_at, type, solution_uuid) " +
            "VALUES (?, ?, ?, ?, NOW(), 'DIRECT', ?)",
            environmentId, "test-app-" + environmentId, "token-" + environmentId, "secret", solutionId
        );

        return environmentId;
    }

    /**
     * Creates a context (with required domain → solution → zone hierarchy) and returns its UUID.
     */
    private UUID createContextForAcl() {
        UUID domainId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO domains (id, domain_id, description, created_at) VALUES (?, ?, ?, NOW())",
            domainId, "dom-" + domainId, "Test domain"
        );

        UUID solutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO solutions (id, domain_uuid, solution_id, description, created_at) VALUES (?, ?, ?, ?, NOW())",
            solutionId, domainId, "sol-" + solutionId, "Test solution"
        );

        UUID zoneId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO zones (id, solution_uuid, zone_id, description, created_at) VALUES (?, ?, ?, ?, NOW())",
            zoneId, solutionId, "zone-" + zoneId, "Test zone"
        );

        UUID ctxId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO contexts (id, zone_uuid, context_id, description, created_at) VALUES (?, ?, ?, ?, NOW())",
            ctxId, zoneId, "ctx-" + ctxId, "Test context"
        );

        return ctxId;
    }

    /**
     * Inserts a context_service and then an ACL entry via JDBC (AclEntryRef is @Immutable).
     * The acl_entries table now references context_services via context_service_uuid.
     */
    private UUID insertAclEntry(String serviceName, UUID contextUuid,
                                AccessType accessType, AccessLevel accessLevel) {
        // Insert context_service record first
        UUID contextServiceId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO context_services (id, context_uuid, service_name, service_type, created_at) " +
            "VALUES (?, ?, ?, ?, NOW())",
            contextServiceId, contextUuid, serviceName, "OBJECT"
        );

        // Insert acl_entry referencing the context_service
        UUID id = UUID.randomUUID();
        UUID environmentId = createEnvironmentForAcl();
        jdbcTemplate.update(
            "INSERT INTO acl_entries (id, environment_id, context_service_uuid, access_type, access_level) " +
            "VALUES (?, ?, ?, ?, ?)",
            id, environmentId, contextServiceId, accessType.name(), accessLevel.name()
        );
        return id;
    }
}
