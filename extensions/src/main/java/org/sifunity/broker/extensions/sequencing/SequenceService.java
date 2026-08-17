package org.sifunity.broker.extensions.sequencing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Generates atomic, monotonically increasing sequence IDs scoped per
 * domain/solution pair using Redis {@code INCR}.
 *
 * <p>Bean is created by {@code SequencingAutoConfiguration} — no {@code @Service}
 * annotation. Redis exceptions propagate to the caller for graceful degradation.
 */
@Slf4j
@RequiredArgsConstructor
public class SequenceService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Returns the next sequence number for the given domain/solution pair.
     * Uses Redis INCR for atomic increment.
     *
     * @param domainId   the domain identifier
     * @param solutionId the solution identifier
     * @return the sequence ID (1-based, monotonically increasing)
     * @throws org.springframework.data.redis.RedisConnectionFailureException if Redis is unavailable
     */
    public long nextSequence(String domainId, String solutionId) {
        String key = buildKey(domainId, solutionId);
        Long value = redisTemplate.opsForValue().increment(key);
        log.debug("Generated sequence={} for key={}", value, key);
        return value != null ? value : 0L;
    }

    /**
     * Builds the Redis key for a domain/solution sequence counter.
     *
     * @param domainId   the domain identifier
     * @param solutionId the solution identifier
     * @return key in format {@code domain:{domainId}:solution:{solutionId}:messagesequence}
     */
    public static String buildKey(String domainId, String solutionId) {
        return "domain:" + domainId + ":solution:" + solutionId + ":messagesequence";
    }
}
