// SPDX-License-Identifier: MIT
package org.sifunity.cachetests.requestsconnector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.requestsconnector.cache.QueueOwnerCache;
import org.sifunity.requestsconnector.client.QueueServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the requests-connector queue-owner cache.
 *
 * <p>This module caches via the Spring Cache abstraction ({@code @Cacheable}), unlike
 * services-connector which uses {@code RedisTemplate} directly — so the two modules need
 * separate tests. The behaviour under test is the <em>annotation</em> semantics, which only
 * exist through the Spring proxy; calling the bean directly would bypass caching entirely and
 * prove nothing. Hence a real (but deliberately narrow) Spring context: {@code @EnableCaching}
 * plus an in-memory {@link ConcurrentMapCacheManager}, rather than {@code @SpringBootTest}.</p>
 *
 * <p><strong>Why this class lives outside {@code org.sifunity.requestsconnector}.</strong>
 * A {@code @Configuration} nested in a test class under the application's base package is
 * picked up by every {@code @SpringBootTest} component scan in this module. The
 * {@code cacheManager} bean below then collides with {@code RedisConfig.cacheManager} and
 * fails unrelated context loads with {@code BeanDefinitionOverrideException} — the
 * {@code integration-test-bean-conflicts} trap this repo has hit before.
 * {@code @TestConfiguration} did not prevent it in practice, so the config is instead placed in
 * a sibling package (<em>not</em> a subpackage of {@code org.sifunity.requestsconnector}), which
 * the component scan cannot reach. Do not move this class under the application package.</p>
 *
 * <p><strong>The rule that matters most — Rule 1, never cache a negative.</strong> The
 * {@link QueueOwnerCache} javadoc asserts that Spring unwraps {@code Optional} before
 * evaluating {@code unless}, so {@code Optional.empty()} presents as {@code null} and is not
 * cached. That is a subtle framework behaviour the whole fail-closed design leans on: if empty
 * results were cached, one transient queue-service blip would pin a legitimately-owned queue
 * into 404s for the entire TTL, converting a momentary outage into a sticky one. It is asserted
 * here rather than trusted.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.3, 3.1</strong></p>
 */
@SpringJUnitConfig(QueueOwnerCacheTest.CachingTestConfig.class)
@DisplayName("QueueOwnerCache (@Cacheable semantics)")
class QueueOwnerCacheTest {

    private static final String DOMAIN = "domain1";
    private static final String SOLUTION = "solution1";
    private static final String CACHE_NAME = "queue-owner";

    @Configuration
    @EnableCaching
    static class CachingTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CACHE_NAME);
        }

        @Bean
        QueueServiceClient queueServiceClient() {
            return mock(QueueServiceClient.class);
        }

        @Bean
        QueueOwnerCache queueOwnerCache(QueueServiceClient queueServiceClient) {
            return new QueueOwnerCache(queueServiceClient);
        }
    }

    @Autowired
    private QueueOwnerCache queueOwnerCache;

    @Autowired
    private QueueServiceClient queueServiceClient;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        reset(queueServiceClient);
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("A resolved owner is cached — the second lookup does not call queue-service")
    void resolvedOwnerIsCached() {
        UUID queueId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(queueServiceClient.findQueueOwner(DOMAIN, SOLUTION, queueId))
                .thenReturn(Optional.of(ownerId));

        assertThat(queueOwnerCache.getQueueOwner(DOMAIN, SOLUTION, queueId)).contains(ownerId);
        assertThat(queueOwnerCache.getQueueOwner(DOMAIN, SOLUTION, queueId)).contains(ownerId);

        // The second call must be served from cache, removing the per-request HTTP hop.
        verify(queueServiceClient, times(1)).findQueueOwner(DOMAIN, SOLUTION, queueId);
    }

    @Test
    @DisplayName("Rule 1: an empty lookup is NOT cached — a later success is still resolvable")
    void emptyLookupIsNotCached() {
        UUID queueId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        // First: queue-service cannot resolve the owner (transient failure or unknown queue).
        when(queueServiceClient.findQueueOwner(DOMAIN, SOLUTION, queueId))
                .thenReturn(Optional.empty());

        assertThat(queueOwnerCache.getQueueOwner(DOMAIN, SOLUTION, queueId)).isEmpty();

        // The empty result must NOT have been written to the cache.
        assertThat(cacheManager.getCache(CACHE_NAME).get(cacheKey(queueId)))
                .as("Rule 1: caching an empty result would pin a legitimately-owned queue into "
                        + "404s for the whole TTL")
                .isNull();

        // Second: queue-service recovers. A cached empty would make this unreachable.
        when(queueServiceClient.findQueueOwner(DOMAIN, SOLUTION, queueId))
                .thenReturn(Optional.of(ownerId));

        assertThat(queueOwnerCache.getQueueOwner(DOMAIN, SOLUTION, queueId))
                .as("a transient failure must not become sticky")
                .contains(ownerId);

        verify(queueServiceClient, times(2)).findQueueOwner(DOMAIN, SOLUTION, queueId);
    }

    @Test
    @DisplayName("Rule 2: a cache miss fetches — the cache never produces a deny on its own")
    void cacheMissFetches() {
        UUID queueId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(queueServiceClient.findQueueOwner(DOMAIN, SOLUTION, queueId))
                .thenReturn(Optional.of(ownerId));

        assertThat(queueOwnerCache.getQueueOwner(DOMAIN, SOLUTION, queueId)).contains(ownerId);

        verify(queueServiceClient).findQueueOwner(DOMAIN, SOLUTION, queueId);
    }

    @Test
    @DisplayName("Cache keys are scoped by domain, solution and queueId")
    void cacheKeysAreTenantScoped() {
        UUID queueId = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();

        when(queueServiceClient.findQueueOwner("domainA", "solutionA", queueId))
                .thenReturn(Optional.of(ownerA));
        when(queueServiceClient.findQueueOwner("domainB", "solutionB", queueId))
                .thenReturn(Optional.of(ownerB));

        assertThat(queueOwnerCache.getQueueOwner("domainA", "solutionA", queueId)).contains(ownerA);
        assertThat(queueOwnerCache.getQueueOwner("domainB", "solutionB", queueId)).contains(ownerB);

        // Both remain independently cached — the same queue UUID under a different tenant
        // must not collide in a shared cache.
        assertThat(queueOwnerCache.getQueueOwner("domainA", "solutionA", queueId)).contains(ownerA);
        assertThat(queueOwnerCache.getQueueOwner("domainB", "solutionB", queueId)).contains(ownerB);

        verify(queueServiceClient, times(1)).findQueueOwner("domainA", "solutionA", queueId);
        verify(queueServiceClient, times(1)).findQueueOwner("domainB", "solutionB", queueId);
        verify(queueServiceClient, never()).findQueueOwner(eq("domainA"), eq("solutionB"), any(UUID.class));
    }

    private static String cacheKey(UUID queueId) {
        return "queue-owner:" + DOMAIN + ":" + SOLUTION + ":" + queueId;
    }
}
