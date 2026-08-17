// SPDX-License-Identifier: MIT
package org.sifunity.gateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-based rate limiter using a sliding window algorithm.
 * 
 * <p>This rate limiter tracks request counts per environment ID using Redis sorted sets.
 * The sliding window algorithm ensures accurate rate limiting by removing old entries
 * and counting only requests within the current time window.</p>
 * 
 * <p>Implementation details:</p>
 * <ul>
 *   <li>Uses Redis sorted sets with timestamps as scores</li>
 *   <li>Window size is 1 second</li>
 *   <li>Automatically cleans up old entries to prevent memory leaks</li>
 *   <li>Returns retry-after time when rate limit is exceeded</li>
 * </ul>
 * 
 * <p>Thread-safe and supports concurrent requests through Redis atomic operations.</p>
 * 
 * @see RateLimitResponse
 */
@Component("environmentRateLimiter")
public class RedisRateLimiter {
    
    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "rate_limit:";
    private static final long WINDOW_SIZE_SECONDS = 1L;
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    /**
     * Constructs a RedisRateLimiter with the specified Redis template.
     * 
     * @param redisTemplate the reactive Redis template for Redis operations
     */
    public RedisRateLimiter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Checks if a request is allowed using domain/solution-scoped rate limit key.
     *
     * <p>Uses the new key format: {@code domain:{d}:solution:{s}:function:rate_limit:env:{e}}</p>
     *
     * @param domainId the domain ID
     * @param solutionId the solution ID
     * @param environmentId the environment ID
     * @param requestsPerSecond the maximum number of requests allowed per second
     * @return a Mono emitting a RateLimitResponse
     */
    public Mono<RateLimitResponse> isAllowed(String domainId, String solutionId,
                                              String environmentId, int requestsPerSecond) {
        String key = "domain:" + domainId + ":solution:" + solutionId
                + ":function:rate_limit:env:" + environmentId;
        return doIsAllowed(key, requestsPerSecond);
    }

    /**
     * Checks if a request is allowed based on the rate limit for the environment.
     *
     * <p>This is the legacy overload that uses the old key format: {@code rate_limit:{environmentId}}.
     * Retained for backward compatibility with paths that lack domain/solution context.</p>
     *
     * @param environmentId the environment ID to check rate limit for
     * @param requestsPerSecond the maximum number of requests allowed per second
     * @return a Mono emitting a RateLimitResponse with isAllowed and retryAfterSeconds
     */
    public Mono<RateLimitResponse> isAllowed(String environmentId, int requestsPerSecond) {
        String key = KEY_PREFIX + environmentId;
        return doIsAllowed(key, requestsPerSecond);
    }

    /**
     * Core sliding window rate limiting logic.
     * 
     * <p>This method implements a sliding window rate limiting algorithm:</p>
     * <ol>
     *   <li>Remove entries older than the window (1 second)</li>
     *   <li>Count remaining entries in the window</li>
     *   <li>If count is below limit, add current request and allow</li>
     *   <li>If count exceeds limit, deny and calculate retry-after time</li>
     * </ol>
     * 
     * <p>The method is reactive and non-blocking, returning a Mono that emits
     * a RateLimitResponse indicating whether the request is allowed and when
     * to retry if denied.</p>
     * 
     * @param key the Redis key to use for rate limiting
     * @param requestsPerSecond the maximum number of requests allowed per second
     * @return a Mono emitting a RateLimitResponse with isAllowed and retryAfterSeconds
     */
    private Mono<RateLimitResponse> doIsAllowed(String key, int requestsPerSecond) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - (WINDOW_SIZE_SECONDS * 1000);
        
        Range<Double> oldEntriesRange = Range.closed(0.0, (double) windowStart);
        Range<Double> windowRange = Range.closed((double) windowStart, (double) now);
        
        return redisTemplate.opsForZSet()
            // Remove old entries outside the sliding window
            .removeRangeByScore(key, oldEntriesRange)
            .flatMap(removed -> redisTemplate.opsForZSet().count(key, windowRange))
            .flatMap(count -> {
                if (count < requestsPerSecond) {
                    // Allow the request and add it to the window
                    return redisTemplate.opsForZSet()
                        .add(key, String.valueOf(now), now)
                        .flatMap(added -> redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SIZE_SECONDS + 1)))
                        .thenReturn(RateLimitResponse.allowed());
                } else {
                    // Rate limit exceeded, calculate retry-after time
                    return calculateRetryAfter(key, windowRange, requestsPerSecond)
                        .map(RateLimitResponse::denied);
                }
            })
            .doOnError(error -> log.error("Rate limit check failed for key {}: {}", 
                key, error.getMessage()))
            .onErrorReturn(RateLimitResponse.allowed()); // Fail open on Redis errors
    }
    
    /**
     * Calculates the retry-after time in seconds.
     * 
     * <p>This method finds the oldest entry in the current window and calculates
     * how long until that entry expires (moves outside the window). This gives
     * the client an accurate time to wait before retrying.</p>
     * 
     * @param key the Redis key for the environment's rate limit data
     * @param windowRange the range representing the current sliding window
     * @param requestsPerSecond the rate limit
     * @return a Mono emitting the retry-after time in seconds
     */
    private Mono<Long> calculateRetryAfter(String key, Range<Double> windowRange, int requestsPerSecond) {
        long now = Instant.now().toEpochMilli();
        
        // Get the oldest entry in the window
        return redisTemplate.opsForZSet()
            .rangeByScore(key, windowRange, Limit.limit().count(1))
            .next()
            .map(oldestEntry -> {
                // Parse the timestamp from the entry
                long oldestTimestamp = Long.parseLong(oldestEntry);
                // Calculate when this entry will expire (1 second after it was added)
                long expiryTime = oldestTimestamp + (WINDOW_SIZE_SECONDS * 1000);
                // Calculate seconds until expiry
                long retryAfterMillis = expiryTime - now;
                return Math.max(1, (retryAfterMillis + 999) / 1000); // Round up to nearest second
            })
            .defaultIfEmpty(1L); // Default to 1 second if calculation fails
    }
}
