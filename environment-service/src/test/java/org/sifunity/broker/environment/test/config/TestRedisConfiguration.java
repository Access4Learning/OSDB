// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.test.config;

import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

/**
 * Test configuration that provides a mock {@link StringRedisTemplate} bean
 * for the "test" profile.
 *
 * <p>This prevents {@code @SpringBootTest} tests from requiring a running Redis
 * instance. The mock returns empty results for all hash operations.</p>
 */
@Configuration
@Profile("test")
public class TestRedisConfiguration {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(mockTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(anyString())).thenReturn(Collections.emptyMap());
        return mockTemplate;
    }
}
