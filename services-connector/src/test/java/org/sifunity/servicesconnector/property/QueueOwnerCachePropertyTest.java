// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import net.jqwik.api.*;
import org.sifunity.servicesconnector.cache.QueueOwnerCache;
import org.sifunity.servicesconnector.client.QueueServiceClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property tests for the queue-owner cache (services-connector, RedisTemplate idiom).
 *
 * <p>Verifies the two rules the cache must obey:</p>
 * <ol>
 *   <li>Never cache a negative or unresolved result</li>
 *   <li>A cache miss means fetch, never deny</li>
 * </ol>
 *
 * <p><strong>Validates: Requirements 2.1, 2.3, 3.1</strong></p>
 */
@Tag("delayed-queue-ownership-fix")
class QueueOwnerCachePropertyTest {

    /**
     * Property A: a cache hit short-circuits the HTTP lookup entirely.
     */
    @Property(tries = 20)
    @Label("Cache hit returns the owner without calling queue-service")
    void cacheHitSkipsHttpLookup(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId) {

        QueueServiceClient client = mock(QueueServiceClient.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(ownerId.toString());

        QueueOwnerCache cache = new QueueOwnerCache(client, redis, 300L);

        Optional<UUID> result = cache.getQueueOwner("domain1", "solution1", queueId);

        assertThat(result).contains(ownerId);
        verify(client, never()).findQueueOwner(anyString(), anyString(), any(UUID.class));
    }

    /**
     * Property B (Rule 1): an empty lookup is NOT written to the cache.
     *
     * <p>Caching "empty" would turn a momentary queue-service blip into a sticky
     * outage for a legitimately-owned queue.</p>
     */
    @Property(tries = 20)
    @Label("Rule 1: an empty/unresolved lookup is never cached")
    void emptyLookupIsNeverCached(@ForAll("uuids") UUID queueId) {

        QueueServiceClient client = mock(QueueServiceClient.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        when(client.findQueueOwner("domain1", "solution1", queueId)).thenReturn(Optional.empty());

        QueueOwnerCache cache = new QueueOwnerCache(client, redis, 300L);

        Optional<UUID> result = cache.getQueueOwner("domain1", "solution1", queueId);

        assertThat(result).isEmpty();
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * Property C (Rule 1, positive half): a resolved owner IS cached with a bounded TTL.
     */
    @Property(tries = 20)
    @Label("Rule 1: a positively-resolved owner is cached with a bounded TTL")
    void resolvedOwnerIsCached(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId) {

        QueueServiceClient client = mock(QueueServiceClient.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        when(client.findQueueOwner("domain1", "solution1", queueId)).thenReturn(Optional.of(ownerId));

        QueueOwnerCache cache = new QueueOwnerCache(client, redis, 300L);

        Optional<UUID> result = cache.getQueueOwner("domain1", "solution1", queueId);

        assertThat(result).contains(ownerId);
        verify(ops).set(contains(queueId.toString()), eq(ownerId.toString()), eq(Duration.ofSeconds(300)));
    }

    /**
     * Property D (Rule 2): a Redis read failure degrades to a miss and still fetches —
     * it never denies on its own.
     */
    @Property(tries = 20)
    @Label("Rule 2: a cache read failure degrades to a fetch, never to a deny")
    void cacheReadFailureDegradesToFetch(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId) {

        QueueServiceClient client = mock(QueueServiceClient.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(client.findQueueOwner("domain1", "solution1", queueId)).thenReturn(Optional.of(ownerId));

        QueueOwnerCache cache = new QueueOwnerCache(client, redis, 300L);

        Optional<UUID> result = cache.getQueueOwner("domain1", "solution1", queueId);

        assertThat(result)
                .as("A cache read failure must fall through to the HTTP lookup, not deny")
                .contains(ownerId);
        verify(client).findQueueOwner("domain1", "solution1", queueId);
    }

    /**
     * Property E: cache keys are tenant-scoped, so the same queue UUID under a different
     * domain/solution cannot collide in shared Redis.
     */
    @Property(tries = 20)
    @Label("Cache keys are tenant-scoped by domain and solution")
    void cacheKeysAreTenantScoped(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId) {

        QueueServiceClient client = mock(QueueServiceClient.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        when(client.findQueueOwner(anyString(), anyString(), eq(queueId))).thenReturn(Optional.of(ownerId));

        QueueOwnerCache cache = new QueueOwnerCache(client, redis, 300L);

        cache.getQueueOwner("domainA", "solutionA", queueId);
        cache.getQueueOwner("domainB", "solutionB", queueId);

        verify(ops).set(eq("queue-owner:domainA:solutionA:" + queueId), anyString(), any(Duration.class));
        verify(ops).set(eq("queue-owner:domainB:solutionB:" + queueId), anyString(), any(Duration.class));
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}
