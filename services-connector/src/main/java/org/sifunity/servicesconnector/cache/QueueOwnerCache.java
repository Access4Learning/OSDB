// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.cache;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.servicesconnector.client.QueueServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache for queue-owner lookups used by the DELAYED-request ownership gate.
 *
 * <p>Safe to cache because <strong>queue ownership is immutable</strong> — see the
 * requests-connector {@code QueueOwnerCache} javadoc for the full rationale.</p>
 *
 * <p><strong>Rule 1 — never cache a negative or unresolved result.</strong> Only positively
 * resolved owners are written to Redis. An empty lookup is returned to the caller without
 * being cached, so a transient {@code queue-service} failure cannot pin a legitimately-owned
 * queue into repeated 404s for the TTL.</p>
 *
 * <p><strong>Rule 2 — a cache miss means fetch, never deny.</strong> The deny decision belongs
 * solely to {@link org.sifunity.servicesconnector.service.ConsumerQueueOwnershipValidator}.</p>
 *
 * <p>This module caches via {@code RedisTemplate} directly (see {@link ProviderRegistryCache}),
 * so this class follows that existing cache-aside idiom rather than introducing Caffeine or the
 * Spring Cache abstraction. Values are plain UUID strings, so {@link StringRedisTemplate} is
 * sufficient and no new serializer bean is required.</p>
 *
 * <p><strong>Requirements:</strong> 2.1, 2.3, 3.1</p>
 */
@Component
@Slf4j
public class QueueOwnerCache {

    private final QueueServiceClient queueServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final Duration cacheTtl;

    public QueueOwnerCache(
            QueueServiceClient queueServiceClient,
            StringRedisTemplate redisTemplate,
            @Value("${provider.cache.ttl:300}") long cacheTtlSeconds) {
        this.queueServiceClient = queueServiceClient;
        this.redisTemplate = redisTemplate;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
        log.info("QueueOwnerCache initialized with TTL: {} seconds", cacheTtlSeconds);
    }

    /**
     * Resolves the owning environment of a queue, caching only positive results.
     *
     * @param domainId   the domain ID
     * @param solutionId the solution ID
     * @param queueId    the queue UUID
     * @return Optional containing the owner's environment UUID, or empty when unresolvable
     */
    public Optional<UUID> getQueueOwner(String domainId, String solutionId, UUID queueId) {
        String cacheKey = buildCacheKey(domainId, solutionId, queueId);

        // Cache hit — a malformed stored value degrades to a miss, never to a deny.
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Queue owner cache hit: {}", cacheKey);
                return Optional.of(UUID.fromString(cached));
            }
        } catch (Exception e) {
            log.warn("Queue owner cache read failed, treating as miss: key={}, error={}",
                    cacheKey, e.getMessage());
        }

        log.debug("Queue owner cache miss: {}", cacheKey);
        Optional<UUID> owner = queueServiceClient.findQueueOwner(domainId, solutionId, queueId);

        // Rule 1: cache positives only.
        owner.ifPresent(ownerId -> {
            try {
                redisTemplate.opsForValue().set(cacheKey, ownerId.toString(), cacheTtl);
                log.debug("Cached queue owner: {} with TTL: {} seconds", cacheKey, cacheTtl.getSeconds());
            } catch (Exception e) {
                log.warn("Queue owner cache write failed: key={}, error={}", cacheKey, e.getMessage());
            }
        });

        return owner;
    }

    private String buildCacheKey(String domainId, String solutionId, UUID queueId) {
        return String.format("queue-owner:%s:%s:%s", domainId, solutionId, queueId);
    }
}
