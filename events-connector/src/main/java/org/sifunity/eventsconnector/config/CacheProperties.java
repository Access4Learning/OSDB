// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Caffeine in-process caches in the events-connector.
 *
 * <p>Binds configuration from application.yml under the prefix "broker.cache"
 * to provide type-safe, per-cache TTL and max-size settings.</p>
 *
 * <p>Configuration structure:</p>
 * <pre>
 * broker:
 *   cache:
 *     context-uuid:
 *       ttl-seconds: 300
 *       max-size: 2000
 *     subscription-query:
 *       ttl-seconds: 30
 *       max-size: 5000
 * </pre>
 *
 * @see CacheConfig
 */
@ConfigurationProperties(prefix = "broker.cache")
@Data
public class CacheProperties {

    /** Cache spec for the context-uuid cache (zone+context string to UUID resolution). */
    private CacheSpec contextUuid = new CacheSpec(300, 2_000);

    /** Cache spec for the subscription-query cache (subscription lookup by serviceName + contextUuid). */
    private CacheSpec subscriptionQuery = new CacheSpec(30, 5_000);

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
