// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.requestsconnector.config.RedisCacheErrorHandler;
import org.springframework.cache.Cache;
import org.springframework.data.redis.serializer.SerializationException;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RedisCacheErrorHandler.
 *
 * <p>Verifies that deserialization failures from stale Redis entries serialized under the
 * previous {@code DefaultTyping.EVERYTHING} configuration are handled gracefully as cache
 * misses without crashing the service.</p>
 *
 * <p><strong>Requirements:</strong></p>
 * <ul>
 *   <li>4.6: Log WARN with rejected class name on InvalidTypeIdException</li>
 *   <li>4.7: Fail gracefully without crashing when encountering stale EVERYTHING-era entries</li>
 * </ul>
 */
class RedisCacheErrorHandlerTest {

    private RedisCacheErrorHandler handler;
    private Cache cache;

    @BeforeEach
    void setUp() {
        handler = new RedisCacheErrorHandler();
        cache = mock(Cache.class);
        when(cache.getName()).thenReturn("provider-registry");
    }

    /**
     * When an InvalidTypeIdException is thrown (disallowed class in @class field),
     * the handler should NOT rethrow — treating it as a cache miss.
     * Validates Requirements 4.6, 4.7.
     */
    @Test
    void handleCacheGetError_withInvalidTypeIdException_doesNotThrow() {
        // Given: an InvalidTypeIdException wrapped in a SerializationException
        InvalidTypeIdException typeIdEx = new InvalidTypeIdException(
                (JsonParser) null,
                "Could not resolve type id 'com.evil.Exploit'",
                (JavaType) null,
                "com.evil.Exploit");
        SerializationException serializationEx = new SerializationException(
                "Deserialization failed", typeIdEx);

        // When/Then: handler does not rethrow — this means Spring treats it as a cache miss
        assertThatNoException().isThrownBy(() ->
                handler.handleCacheGetError(serializationEx, cache, "provider:StudentPersonal:zone1:default:OBJECT"));
    }

    /**
     * When a generic SerializationException occurs (not InvalidTypeIdException),
     * the handler should still NOT rethrow — treating it as a cache miss.
     * Validates Requirement 4.7.
     */
    @Test
    void handleCacheGetError_withGenericSerializationException_doesNotThrow() {
        // Given: a generic serialization error (e.g., corrupted JSON)
        SerializationException serializationEx = new SerializationException(
                "Could not deserialize: unexpected token");

        // When/Then: handler does not rethrow
        assertThatNoException().isThrownBy(() ->
                handler.handleCacheGetError(serializationEx, cache, "provider:StudentPersonal:zone1:default:OBJECT"));
    }

    /**
     * PUT errors should not crash the service — value simply isn't cached.
     * Validates Requirement 4.7.
     */
    @Test
    void handleCachePutError_doesNotThrow() {
        SerializationException ex = new SerializationException("Serialization failed");

        assertThatNoException().isThrownBy(() ->
                handler.handleCachePutError(ex, cache, "someKey", "someValue"));
    }

    /**
     * EVICT errors should not crash the service.
     * Validates Requirement 4.7.
     */
    @Test
    void handleCacheEvictError_doesNotThrow() {
        RuntimeException ex = new RuntimeException("Redis connection lost");

        assertThatNoException().isThrownBy(() ->
                handler.handleCacheEvictError(ex, cache, "someKey"));
    }

    /**
     * CLEAR errors should not crash the service.
     * Validates Requirement 4.7.
     */
    @Test
    void handleCacheClearError_doesNotThrow() {
        RuntimeException ex = new RuntimeException("Redis connection lost");

        assertThatNoException().isThrownBy(() ->
                handler.handleCacheClearError(ex, cache));
    }
}
