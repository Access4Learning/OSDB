// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.config;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Custom cache error handler that gracefully handles deserialization failures from stale Redis entries.
 *
 * <p>When the Redis ObjectMapper was changed from {@code DefaultTyping.EVERYTHING} to
 * {@code DefaultTyping.NON_FINAL} with a restricted allowlist, existing Redis entries
 * serialized with the old configuration may contain {@code @class} type identifiers
 * referencing classes outside the allowlist. When Spring Cache attempts to deserialize
 * these entries, an {@code InvalidTypeIdException} is thrown.</p>
 *
 * <p>This handler catches such deserialization failures and treats them as cache misses,
 * allowing the service to continue operating normally. The stale entries will expire
 * naturally via their TTL.</p>
 *
 * <p><strong>Behavior:</strong></p>
 * <ul>
 *   <li>On {@code InvalidTypeIdException}: logs WARN with the rejected class name, treats as cache miss</li>
 *   <li>On other serialization errors: logs WARN with details, treats as cache miss</li>
 *   <li>Put/evict errors: logged as WARN but do not crash the service</li>
 * </ul>
 *
 * <p><strong>Requirements:</strong></p>
 * <ul>
 *   <li>4.6: Log WARN with rejected class name on InvalidTypeIdException</li>
 *   <li>4.7: Fail gracefully without crashing when encountering stale EVERYTHING-era entries</li>
 * </ul>
 */
@Slf4j
public class RedisCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        InvalidTypeIdException typeIdException = findInvalidTypeIdException(exception);
        if (typeIdException != null) {
            log.warn("Rejected disallowed class during Redis cache deserialization: cache={}, key={}, rejectedClass={}. "
                    + "Treating as cache miss — stale entry will expire via TTL.",
                    cache.getName(), key, typeIdException.getTypeId());
        } else {
            log.warn("Redis cache GET error (treating as cache miss): cache={}, key={}, error={}",
                    cache.getName(), key, exception.getMessage());
        }
        // By not rethrowing, Spring treats this as a cache miss and invokes the underlying method
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Redis cache PUT error (value not cached): cache={}, key={}, error={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Redis cache EVICT error: cache={}, key={}, error={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Redis cache CLEAR error: cache={}, error={}",
                cache.getName(), exception.getMessage());
    }

    /**
     * Walks the exception cause chain looking for an InvalidTypeIdException.
     *
     * @param exception the runtime exception thrown by the cache
     * @return the InvalidTypeIdException if found, null otherwise
     */
    private InvalidTypeIdException findInvalidTypeIdException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof InvalidTypeIdException invalidTypeId) {
                return invalidTypeId;
            }
            current = current.getCause();
        }
        return null;
    }
}
