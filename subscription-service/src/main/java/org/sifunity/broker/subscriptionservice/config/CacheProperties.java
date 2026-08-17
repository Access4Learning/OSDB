// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Caffeine in-process caches in the subscription-service.
 *
 * <p>Binds configuration from application.yml under the prefix "broker.cache"
 * to provide type-safe, per-cache TTL and max-size settings.</p>
 *
 * <p>Configuration structure:</p>
 * <pre>
 * broker:
 *   cache:
 *     subscriptions:
 *       ttl-seconds: 300
 *       max-size: 5000
 * </pre>
 *
 * @see CacheConfig
 */
@ConfigurationProperties(prefix = "broker.cache")
@Data
public class CacheProperties {

    /** Cache spec for the subscriptions cache (subscription lookup results by serviceName + contextUuid + type). */
    private CacheSpec subscriptions = new CacheSpec(300, 5_000);

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
