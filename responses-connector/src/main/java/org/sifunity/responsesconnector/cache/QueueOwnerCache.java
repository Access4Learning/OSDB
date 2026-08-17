// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.responsesconnector.client.QueueServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Caches queue → owning-environment lookups for the response destination gate.
 *
 * <h2>Why a generous TTL is safe here</h2>
 *
 * <p><strong>Queue ownership is immutable.</strong> {@code Queue.setOwnerId(...)} is called only from
 * {@code QueueService.createQueue(...)}; there is no ownership-transfer path. A cached positive
 * therefore cannot become <em>wrong</em> — only obsolete, if the queue is later deleted, and a stale
 * positive then grants admission only: delivery still fails downstream because the queue is gone.
 * No cross-service invalidation is required.</p>
 *
 * <p>This is the deliberate counterpart to the <em>entitlement</em> lookup, which is cached for only
 * ~30s in {@code environment-service} because ACL grants are revocable. Copying this generous TTL
 * onto that cache would let a revoked grant keep admitting responses; copying that short TTL here
 * would just waste lookups on data that cannot change. The asymmetry is the point.</p>
 *
 * <h2>Two rules this cache obeys</h2>
 * <ol>
 *   <li><strong>Cache positives only.</strong> The loader returns {@code null} when the owner cannot
 *       be resolved, and Caffeine does not store a null-valued mapping — so an unresolved lookup is
 *       returned to the caller without being cached. Caching "unresolved" would let one transient
 *       {@code queue-service} blip pin a legitimately-owned queue into denials for the whole TTL,
 *       converting a momentary outage into a sticky one.</li>
 *   <li><strong>A cache miss means fetch, never deny.</strong> This class never decides; the deny
 *       decision belongs solely to {@link
 *       org.sifunity.responsesconnector.service.ResponseDestinationValidator}, acting on a completed
 *       lookup.</li>
 * </ol>
 *
 * <p>Uses Caffeine directly, matching {@code LegacyTagLookupService} — this module has neither Redis
 * nor a Spring {@code CacheManager}, so the annotation-driven idiom used by {@code requests-connector}
 * is not available here.</p>
 *
 * <p><strong>Requirements:</strong> 2.9</p>
 */
@Component
@Slf4j
public class QueueOwnerCache {

    private final QueueServiceClient queueServiceClient;
    private final Cache<String, UUID> cache;

    public QueueOwnerCache(
            QueueServiceClient queueServiceClient,
            @Value("${sif.queue-owner.cache-ttl:5m}") Duration cacheTtl,
            @Value("${sif.queue-owner.cache-max-size:10000}") long maxSize) {
        this.queueServiceClient = queueServiceClient;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(maxSize)
                .build();
        log.info("QueueOwnerCache initialized: cacheTtl={}, maxSize={}", cacheTtl, maxSize);
    }

    /**
     * Resolves the owning environment of a queue, caching only positive results.
     *
     * @param domainId   the domain ID
     * @param solutionId the solution ID
     * @param queueId    the queue UUID
     * @return the owner's environment UUID, or {@code null} when unresolvable
     */
    public UUID getQueueOwner(String domainId, String solutionId, UUID queueId) {
        String key = domainId + ":" + solutionId + ":" + queueId;
        // Caffeine does not store a null-valued mapping, so this caches positives only.
        return cache.get(key, k -> {
            log.debug("Queue owner cache miss — querying queue-service: domainId={}, solutionId={}, queueId={}",
                    domainId, solutionId, queueId);
            return queueServiceClient.getQueueOwnerEnvironmentId(domainId, solutionId, queueId);
        });
    }
}
