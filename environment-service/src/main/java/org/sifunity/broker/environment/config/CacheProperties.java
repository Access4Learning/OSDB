// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Caffeine in-process caches in the environment-service.
 *
 * <p>Binds configuration from application.yml under the prefix "broker.cache"
 * to provide type-safe, per-cache TTL and max-size settings.</p>
 *
 * <p>Configuration structure:</p>
 * <pre>
 * broker:
 *   cache:
 *     auth-environment:
 *       ttl-seconds: 60
 *       max-size: 10000
 *     auth-shared-secret:
 *       ttl-seconds: 60
 *       max-size: 1000
 *     auth-solution:
 *       ttl-seconds: 60
 *       max-size: 5000
 *     auth-domain:
 *       ttl-seconds: 60
 *       max-size: 5000
 *     acl-permissions:
 *       ttl-seconds: 60
 *       max-size: 5000
 * </pre>
 *
 * @see CacheConfig
 */
@ConfigurationProperties(prefix = "broker.cache")
@Data
public class CacheProperties {

    /** Cache spec for the auth-environment cache (Environment by sessionToken). */
    private CacheSpec authEnvironment = new CacheSpec(60, 10_000);

    /** Cache spec for the auth-shared-secret cache (shared secret by environmentId). */
    private CacheSpec authSharedSecret = new CacheSpec(60, 1_000);

    /** Cache spec for the auth-solution cache (Solution by solutionUuid). */
    private CacheSpec authSolution = new CacheSpec(60, 5_000);

    /** Cache spec for the auth-domain cache (Domain by domainUuid). */
    private CacheSpec authDomain = new CacheSpec(60, 5_000);

    /** Cache spec for the acl-permissions cache (PermissionCheckResponse by composite key). */
    private CacheSpec aclPermissions = new CacheSpec(60, 5_000);

    /**
     * Cache spec for the acl-any-grant cache (any-approved-grant entitlement checks used by the
     * response-destination gate).
     *
     * <p>TTL deliberately matches {@code acl-permissions} and is deliberately <em>short</em>: ACL
     * grants are revocable, unlike queue ownership, so a stale positive would keep admitting
     * response submissions after a grant is withdrawn. Entries are also evicted eagerly on every
     * ACL mutation (see {@code AclService}); the TTL is the backstop, not the primary mechanism.</p>
     */
    private CacheSpec aclAnyGrant = new CacheSpec(60, 5_000);

    /**
     * Per-cache configuration specifying TTL and maximum entry count.
     */
    @Data
    public static class CacheSpec {

        /** Time-to-live in seconds (expire-after-write). */
        private int ttlSeconds;

        /** Maximum number of entries before eviction. */
        private int maxSize;

        public CacheSpec() {
        }

        public CacheSpec(int ttlSeconds, int maxSize) {
            this.ttlSeconds = ttlSeconds;
            this.maxSize = maxSize;
        }
    }
}
