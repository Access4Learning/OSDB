// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.responsesconnector.client.EnvironmentServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Caches "is this environment entitled to this object at all?" for the response destination gate.
 *
 * <h2>Why the TTL is short</h2>
 *
 * <p><strong>ACL grants are mutable.</strong> Unlike queue ownership, a grant can be revoked at any
 * time, and a stale positive here would keep admitting responses into a consumer's queue after its
 * entitlement was withdrawn. The TTL therefore mirrors the gateway's own permission cache
 * ({@code EnvironmentServiceClient.permissionCache}, {@code expireAfterWrite(30s)}) rather than the
 * generous TTL used by {@link QueueOwnerCache} for immutable ownership. <b>Do not align the two.</b></p>
 *
 * <p>This sits in front of {@code environment-service}'s own {@code acl-any-grant} cache. Both are
 * useful: this one removes a per-response HTTP round trip, while the server-side cache still absorbs
 * misses arriving from separate replicas of this service, each of which has its own copy of this
 * in-process cache.</p>
 *
 * <h2>Two rules this cache obeys</h2>
 * <ol>
 *   <li><strong>Cache positives only.</strong> {@code hasAnyGrant} fails closed, so a {@code false}
 *       may mean either a genuine denial or a transient environment-service failure. The two are
 *       indistinguishable here, and caching the negative would convert a momentary blip into a
 *       sticky outage for an entitled consumer. Only {@code true} is stored.</li>
 *   <li><strong>A cache miss means fetch, never deny.</strong> This class never decides; the deny
 *       decision belongs solely to {@link
 *       org.sifunity.responsesconnector.service.ResponseDestinationValidator}.</li>
 * </ol>
 *
 * <p><strong>Requirements:</strong> 2.9</p>
 */
@Component
@Slf4j
public class DestinationEntitlementCache {

    private final EnvironmentServiceClient environmentServiceClient;
    private final Cache<String, Boolean> cache;

    public DestinationEntitlementCache(
            EnvironmentServiceClient environmentServiceClient,
            @Value("${sif.destination-entitlement.cache-ttl:30s}") Duration cacheTtl,
            @Value("${sif.destination-entitlement.cache-max-size:10000}") long maxSize) {
        this.environmentServiceClient = environmentServiceClient;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(maxSize)
                .build();
        log.info("DestinationEntitlementCache initialized: cacheTtl={}, maxSize={}", cacheTtl, maxSize);
    }

    /**
     * Resolves whether the destination owner holds any approved grant, caching only positives.
     *
     * @param domainId    the domain ID
     * @param solutionId  the solution ID
     * @param ownerId     the environment owning the destination queue
     * @param serviceName the SIF object/service the response claims to carry
     * @param zoneId      the zone qualifying the service
     * @param contextId   the context qualifying the service
     * @return {@code true} if an approved grant exists
     */
    public boolean isEntitled(String domainId, String solutionId, UUID ownerId,
                              String serviceName, String zoneId, String contextId) {

        String key = String.join("|", domainId, solutionId, String.valueOf(ownerId),
                serviceName, String.valueOf(zoneId), String.valueOf(contextId));

        Boolean cached = cache.getIfPresent(key);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }

        boolean entitled = environmentServiceClient.hasAnyGrant(
                domainId, solutionId, ownerId, serviceName, zoneId, contextId);

        // Rule 1: positives only. A false is returned but never stored.
        if (entitled) {
            cache.put(key, Boolean.TRUE);
        }

        return entitled;
    }
}
