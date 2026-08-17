// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Redis configuration for the queue service.
 *
 * <p>The queue service uses {@code StringRedisTemplate} (auto-configured by Spring Boot)
 * for all Redis operations via {@code StreamMessageRepository} and {@code RefCountService}.
 * No custom {@code RedisTemplate} bean is needed.</p>
 *
 * <p>This configuration is enabled by default but can be disabled by setting
 * the property {@code queue-service.redis.enabled=false}, which is useful
 * for unit tests that don't require Redis.</p>
 */
@Configuration
@ConditionalOnProperty(name = "queue-service.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {
    // StringRedisTemplate is auto-configured by Spring Boot.
    // StreamMessageRepository and RefCountService inject it directly.
}
