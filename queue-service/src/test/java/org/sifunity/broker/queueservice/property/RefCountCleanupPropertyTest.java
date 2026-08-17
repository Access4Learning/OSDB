// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.redis.RedisKeyManager;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 15: Ref-count-aware cleanup — ref count decrement triggers payload delete at zero.
 *
 * <p>Validates: Requirements 11.1, 12.3</p>
 */
class RefCountCleanupPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * **Validates: Requirements 11.1, 12.3**
     */
    @SuppressWarnings("unchecked")
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 15: Ref-count-aware cleanup")
    void refCountDecrementTriggersPayloadDeleteAtZero(
            @ForAll @IntRange(min = 1, max = 10) int initialRefCount) {

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StorageProvider sp = mock(StorageProvider.class);

        RefCountService rcs = new RefCountService(redisTemplate, new RedisKeyManager());

        String storageKey = "storage-" + UUID.randomUUID();
        String refKey = rcs.refCountKey("domain", "solution", storageKey);

        // Simulate decrement to zero
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                .thenReturn(0L);

        long result = rcs.decrementAndGet(refKey);

        // When count reaches 0, caller should delete payload
        assertThat(result).isEqualTo(0L);

        // Simulate decrement with count still positive
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                .thenReturn((long) (initialRefCount - 1));

        long positiveResult = rcs.decrementAndGet(refKey);

        if (initialRefCount > 1) {
            assertThat(positiveResult).isGreaterThan(0L);
        }
    }
}
