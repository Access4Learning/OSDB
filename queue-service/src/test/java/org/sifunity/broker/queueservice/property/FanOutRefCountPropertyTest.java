// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.redis.RedisKeyManager;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 17: Fan-out ref count — fan-out to N queues sets ref count to N.
 *
 * <p>Validates: Requirements 12.1, 12.2</p>
 */
class FanOutRefCountPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * **Validates: Requirements 12.1, 12.2**
     */
    @SuppressWarnings("unchecked")
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 17: Fan-out ref count")
    void fanOutToNQueuesSetsRefCountToN(
            @ForAll @IntRange(min = 1, max = 10) int subscriberCount) {

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        RefCountService rcs = new RefCountService(redisTemplate, new RedisKeyManager());

        String storageKey = "storage-" + UUID.randomUUID();
        String refKey = rcs.refCountKey("domain", "solution", storageKey);

        // Simulate N increments returning 1, 2, ..., N
        long[] counts = new long[subscriberCount];
        for (int i = 0; i < subscriberCount; i++) {
            counts[i] = i + 1;
        }

        // Set up sequential returns
        if (subscriberCount == 1) {
            when(valueOps.increment(refKey)).thenReturn(1L);
        } else {
            Long[] boxedCounts = new Long[subscriberCount];
            for (int i = 0; i < subscriberCount; i++) {
                boxedCounts[i] = (long) (i + 1);
            }
            Long first = boxedCounts[0];
            Long[] rest = new Long[subscriberCount - 1];
            System.arraycopy(boxedCounts, 1, rest, 0, subscriberCount - 1);
            when(valueOps.increment(refKey)).thenReturn(first, rest);
        }

        // Simulate fan-out: increment ref count for each subscriber queue
        long finalCount = 0;
        for (int i = 0; i < subscriberCount; i++) {
            finalCount = rcs.increment(refKey);
        }

        // After N increments, the final count should be N
        assertThat(finalCount).isEqualTo(subscriberCount);

        // Verify increment was called exactly N times
        verify(valueOps, times(subscriberCount)).increment(refKey);
    }
}
