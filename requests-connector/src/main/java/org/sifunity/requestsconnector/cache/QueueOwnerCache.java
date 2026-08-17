// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.cache;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.requestsconnector.client.QueueServiceClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Cache for queue-owner lookups used by the DELAYED-request ownership gate.
 *
 * <p>Caching an authorization input is normally hazardous, but it is safe here because
 * <strong>queue ownership is immutable</strong>: {@code Queue.setOwnerId(...)} is called only
 * from {@code QueueService.createQueue(...)} — there is no ownership-transfer path. A cached
 * positive entry therefore cannot become wrong; it can only become obsolete if the queue is
 * deleted, and a stale positive entry grants admission only (delivery still fails downstream
 * because the queue is gone). A bounded TTL is sufficient; no cross-service invalidation is
 * required.</p>
 *
 * <p><strong>Two rules this cache obeys:</strong></p>
 * <ol>
 *   <li><strong>Never cache a negative or unresolved result.</strong> Spring unwraps
 *       {@code Optional} before evaluating {@code unless}, so {@code Optional.empty()} presents
 *       as {@code null} and is not cached. Caching "empty" would let one transient
 *       {@code queue-service} blip pin a legitimately-owned queue into 404s for the whole TTL,
 *       converting a momentary outage into a sticky one.</li>
 *   <li><strong>A cache miss means fetch, never deny.</strong> The deny decision belongs solely
 *       to {@link org.sifunity.requestsconnector.service.ConsumerQueueOwnershipValidator},
 *       acting on a completed lookup. This composes with the existing
 *       {@code RedisCacheErrorHandler}, which already degrades deserialization failures to
 *       misses.</li>
 * </ol>
 *
 * <p>Follows the same Spring Cache abstraction idiom as {@link ProviderRegistryCache},
 * backed by the shared {@code RedisCacheManager}.</p>
 *
 * <p><strong>Requirements:</strong> 2.1, 2.3, 3.1</p>
 */
@Component
@Slf4j
public class QueueOwnerCache {

    private final QueueServiceClient queueServiceClient;

    public QueueOwnerCache(QueueServiceClient queueServiceClient) {
        this.queueServiceClient = queueServiceClient;
        log.info("QueueOwnerCache initialized with Spring Cache abstraction");
    }

    /**
     * Resolves the owning environment of a queue, caching only positive results.
     *
     * @param domainId   the domain ID
     * @param solutionId the solution ID
     * @param queueId    the queue UUID
     * @return Optional containing the owner's environment UUID, or empty when unresolvable
     */
    @Cacheable(
        value = "queue-owner",
        key = "'queue-owner:' + #domainId + ':' + #solutionId + ':' + #queueId",
        unless = "#result == null"
    )
    public Optional<UUID> getQueueOwner(String domainId, String solutionId, UUID queueId) {
        log.debug("Queue owner cache miss - querying queue-service: domainId={}, solutionId={}, queueId={}",
                domainId, solutionId, queueId);
        return queueServiceClient.findQueueOwner(domainId, solutionId, queueId);
    }
}
