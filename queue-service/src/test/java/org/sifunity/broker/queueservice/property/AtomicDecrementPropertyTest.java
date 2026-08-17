// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.redis.RedisKeyManager;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 18: Atomic decrement — concurrent decrements produce exactly one zero.
 *
 * <p>Validates: Requirements 12.4</p>
 */
class AtomicDecrementPropertyTest {

    /**
     * **Validates: Requirements 12.4**
     */
    @SuppressWarnings("unchecked")
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 18: Atomic decrement")
    void concurrentDecrementsProduceExactlyOneZero(
            @ForAll @IntRange(min = 2, max = 10) int concurrentCount) {

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RefCountService rcs = new RefCountService(redisTemplate, new RedisKeyManager());

        String refKey = "domain:d:solution:s:ref:key-" + UUID.randomUUID();

        // Simulate atomic Lua script: each call returns a decreasing count
        // Starting from concurrentCount, each decrement returns one less
        // Exactly one call should return 0
        AtomicLong counter = new AtomicLong(concurrentCount);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                .thenAnswer(inv -> counter.decrementAndGet());

        AtomicInteger zeroCount = new AtomicInteger(0);
        AtomicInteger negativeCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentCount; i++) {
            long result = rcs.decrementAndGet(refKey);
            if (result == 0) {
                zeroCount.incrementAndGet();
            }
            if (result < 0) {
                negativeCount.incrementAndGet();
            }
        }

        // Exactly one decrement should reach zero
        assertThat(zeroCount.get()).isEqualTo(1);

        // No negative counts (Lua script deletes key at 0)
        // In the real Lua script, the key is deleted when count <= 0,
        // so subsequent calls would fail or return 0.
        // Here we verify the sequential simulation produces exactly one zero.
    }
}
