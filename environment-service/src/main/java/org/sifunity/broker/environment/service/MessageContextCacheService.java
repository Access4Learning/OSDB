// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.sifunity.broker.environment.dto.MessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Reads message context from the Redis cache for alert correlation.
 *
 * <p>The gateway's {@code MessageContextCacheFilter} writes a Redis hash for every
 * authenticated request that carries a {@code messageId} header. This service reads
 * those hashes so the alerts service can populate server-side extended elements
 * (sourceEnvironmentId, zoneId, contextId) when creating an alert with a
 * {@code messageID}.</p>
 *
 * <p>Key pattern: {@code domain:{domainId}:solution:{solutionId}:messageId:{messageId}}</p>
 *
 * @see MessageContext
 */
@Service
public class MessageContextCacheService {

    private static final Logger log = LoggerFactory.getLogger(MessageContextCacheService.class);

    private static final String KEY_TEMPLATE = "domain:%s:solution:%s:messageId:%s";
    private static final String FIELD_SOURCE_ENVIRONMENT_ID = "sourceEnvironmentId";
    private static final String FIELD_ZONE_ID = "zoneId";
    private static final String FIELD_CONTEXT_ID = "contextId";

    private final StringRedisTemplate redisTemplate;

    public MessageContextCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Looks up the message context cached by the gateway for the given message.
     *
     * @param domainId   the domain UUID from the request path
     * @param solutionId the solution UUID from the request path
     * @param messageId  the message ID from the alert creation request body
     * @return the cached message context, or {@link Optional#empty()} if the key
     *         does not exist or the hash is empty
     */
    public Optional<MessageContext> getMessageContext(String domainId, String solutionId, String messageId) {
        String key = String.format(KEY_TEMPLATE, domainId, solutionId, messageId);

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries == null || entries.isEmpty()) {
            log.debug("No message context found for key: {}", key);
            return Optional.empty();
        }

        String sourceEnvironmentId = (String) entries.get(FIELD_SOURCE_ENVIRONMENT_ID);
        String zoneId = (String) entries.get(FIELD_ZONE_ID);
        String contextId = (String) entries.get(FIELD_CONTEXT_ID);

        return Optional.of(new MessageContext(sourceEnvironmentId, zoneId, contextId));
    }
}
