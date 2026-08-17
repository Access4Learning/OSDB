// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.queueservice.config.RedisConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RedisConfig.
 *
 * <p>After the Redis Streams migration, RedisConfig is a minimal configuration class.
 * StringRedisTemplate is auto-configured by Spring Boot and injected directly into
 * StreamMessageRepository and RefCountService.</p>
 */
class RedisConfigTest {

    @Test
    void testRedisConfig_IsInstantiable() {
        RedisConfig config = new RedisConfig();
        assertThat(config).isNotNull();
    }
}
