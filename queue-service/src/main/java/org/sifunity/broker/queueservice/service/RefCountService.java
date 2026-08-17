// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.service;

import org.sifunity.broker.queueservice.redis.RedisKeyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages reference counts for shared message payloads using Redis.
 *
 * <p>When an event is fanned out to multiple subscriber queues, the payload is stored
 * once and a reference count tracks how many queues reference it. On acknowledge,
 * the count is decremented atomically; when it reaches zero the caller deletes the payload.
 *
 * <p>Key format: {@code domain:{domainId}:solution:{solutionId}:ref:{storageKey}}
 *
 * @see org.sifunity.broker.queueservice.redis.StreamMessageRepository
 */
@Service
public class RefCountService {

    private static final Logger log = LoggerFactory.getLogger(RefCountService.class);

    /**
     * Lua script for atomic decrement-and-conditional-delete.
     * Returns the new count after DECR. If the count drops to zero or below,
     * the key is deleted so it doesn't linger in Redis.
     */
    private static final String DECREMENT_SCRIPT =
            "local count = redis.call('DECR', KEYS[1])\n" +
            "if count <= 0 then\n" +
            "    redis.call('DEL', KEYS[1])\n" +
            "end\n" +
            "return count";

    private static final DefaultRedisScript<Long> DECREMENT_AND_GET_SCRIPT;

    static {
        DECREMENT_AND_GET_SCRIPT = new DefaultRedisScript<>();
        DECREMENT_AND_GET_SCRIPT.setScriptText(DECREMENT_SCRIPT);
        DECREMENT_AND_GET_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyManager keyManager;

    public RefCountService(StringRedisTemplate redisTemplate, RedisKeyManager keyManager) {
        this.redisTemplate = redisTemplate;
        this.keyManager = keyManager;
    }

    // ── Key generation ──────────────────────────────────────────────────

    /**
     * Build the ref count key for a shared payload.
     *
     * @param domainId   human-readable domain identifier
     * @param solutionId human-readable solution identifier
     * @param storageKey the storage key of the payload
     * @return Redis key in the format {@code domain:{domainId}:solution:{solutionId}:ref:{storageKey}}
     */
    public String refCountKey(String domainId, String solutionId, String storageKey) {
        return keyManager.refCountKey(domainId, solutionId, storageKey);
    }

    // ── Increment ───────────────────────────────────────────────────────

    /**
     * Atomically increment the reference count for a payload.
     * Called when a message referencing this payload is enqueued to a queue.
     *
     * @param refCountKey the Redis key (from {@link #refCountKey})
     * @return the new reference count after increment
     */
    public long increment(String refCountKey) {
        Long count = redisTemplate.opsForValue().increment(refCountKey);
        log.debug("Incremented ref count for key={} to {}", refCountKey, count);
        return count != null ? count : 0L;
    }

    // ── Decrement ───────────────────────────────────────────────────────

    /**
     * Atomically decrement the reference count and delete the key if it reaches zero.
     * Called when a message is acknowledged and payload cleanup is triggered.
     *
     * <p>Uses a Lua script to ensure the decrement and conditional delete are atomic,
     * preventing race conditions between concurrent acknowledgments across different queues.
     *
     * @param refCountKey the Redis key (from {@link #refCountKey})
     * @return the new reference count after decrement; 0 means the caller should delete the payload
     */
    public long decrementAndGet(String refCountKey) {
        Long count = redisTemplate.execute(
                DECREMENT_AND_GET_SCRIPT,
                Collections.singletonList(refCountKey)
        );
        long result = count != null ? count : 0L;
        log.debug("Decremented ref count for key={} to {}", refCountKey, result);
        return result;
    }

    // ── Batch Decrement ─────────────────────────────────────────────────

    /**
     * Decrement multiple ref count keys in a single Redis pipeline.
     * Each key is decremented atomically via the Lua script (atomic decrement + conditional delete).
     *
     * <p>Uses {@code redisTemplate.executePipelined()} to issue multiple EVALSHA calls
     * in one round-trip, reducing network overhead for batch cleanup operations.
     *
     * @param refKeys list of ref count keys to decrement
     * @return map of refKey → new count after decrement (null results treated as 0)
     */
    public Map<String, Long> decrementBatch(List<String> refKeys) {
        if (refKeys == null || refKeys.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String refKey : refKeys) {
                    operations.execute(DECREMENT_AND_GET_SCRIPT, Collections.singletonList(refKey));
                }
                return null; // pipeline collects results automatically
            }
        });

        Map<String, Long> resultMap = new LinkedHashMap<>(refKeys.size());
        for (int i = 0; i < refKeys.size(); i++) {
            Object raw = (i < results.size()) ? results.get(i) : null;
            long count = (raw instanceof Long l) ? l : 0L;
            resultMap.put(refKeys.get(i), count);
        }

        log.debug("Batch decremented {} ref count keys", refKeys.size());
        return resultMap;
    }
}
