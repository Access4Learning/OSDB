// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Configuration for the proxy requests Redis Stream and consumer group.
 *
 * <p>Defines stream keys, consumer group name, and DLQ stream used by the
 * proxy worker's Redis Streams-based message processing pipeline.</p>
 *
 * <p>On startup, ensures the consumer group exists on the proxy requests stream
 * (XGROUP CREATE with MKSTREAM). The operation is idempotent — if the group
 * already exists, the BUSYGROUP error is silently ignored.</p>
 *
 * <p><strong>Configuration properties:</strong></p>
 * <ul>
 *   <li>{@code proxy.worker.idle-threshold-ms} — idle time (ms) before a pending message
 *       is considered orphaned and eligible for XAUTOCLAIM. Default 120000 (120s).
 *       Must exceed provider read timeout (30s) + retries + buffer to avoid reclaiming
 *       messages from live workers during normal slow requests.</li>
 *   <li>{@code proxy.worker.max-retries} — maximum delivery attempts before a message
 *       is moved to the dead-letter stream. Default 3.</li>
 * </ul>
 *
 * <p>Requirements: 2.1</p>
 */
@Configuration
@Slf4j
public class ProxyStreamConfig {

    /** Redis Stream key for proxy requests (replaces the old Redis list). */
    public static final String STREAM_KEY = "proxy-requests-stream";

    /** Consumer group name for proxy workers. */
    public static final String CONSUMER_GROUP = "proxy-workers";

    /** Dead-letter queue stream for messages exceeding max delivery count. */
    public static final String DLQ_STREAM_KEY = "proxy-requests-dlq-stream";

    private final long idleThresholdMs;
    private final int maxRetries;

    public ProxyStreamConfig(
            @Value("${proxy.worker.idle-threshold-ms:120000}") long idleThresholdMs,
            @Value("${proxy.worker.max-retries:3}") int maxRetries) {
        this.idleThresholdMs = idleThresholdMs;
        this.maxRetries = maxRetries;
    }

    /**
     * Returns the idle threshold in milliseconds. Messages pending longer than this
     * are considered orphaned and eligible for XAUTOCLAIM reclaim.
     *
     * <p>Default: 120000ms (120s) — provides ~4x buffer over the 30s provider read timeout.
     * A threshold of 30s would collide with the provider timeout, causing duplicate delivery
     * during normal slow requests.</p>
     */
    public long getIdleThresholdMs() {
        return idleThresholdMs;
    }

    /**
     * Returns the maximum number of delivery attempts before a message is moved to DLQ.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Ensures the consumer group exists on the proxy requests stream.
     *
     * <p>Uses XGROUP CREATE with MKSTREAM (via ReadOffset "0") so the stream is created
     * if it does not yet exist. If the consumer group already exists, the BUSYGROUP
     * error from Redis is caught and silently ignored — making this operation idempotent
     * across pod restarts and rolling deploys.</p>
     *
     * @param stringRedisTemplate the StringRedisTemplate bean (auto-configured by Spring Boot)
     * @return the stream key (for bean dependency wiring; value itself is the constant)
     */
    @Bean
    public String proxyStreamConsumerGroup(StringRedisTemplate stringRedisTemplate) {
        try {
            stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
            log.info("Created consumer group '{}' on stream '{}'", CONSUMER_GROUP, STREAM_KEY);
        } catch (RedisSystemException ex) {
            if (isBusyGroupError(ex)) {
                log.debug("Consumer group '{}' already exists on stream '{}'", CONSUMER_GROUP, STREAM_KEY);
            } else {
                throw ex;
            }
        }
        return STREAM_KEY;
    }

    private boolean isBusyGroupError(RedisSystemException ex) {
        String message = getRootMessage(ex);
        return message != null && message.contains("BUSYGROUP");
    }

    private String getRootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
