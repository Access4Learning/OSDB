// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Filter that asynchronously caches message context in Redis for alert correlation.
 *
 * <p>When an authenticated request contains a {@code messageId} header, this filter
 * writes a Redis hash with the message context (source environment, zone, context)
 * so that the Alerts service can later correlate alerts to their originating messages.</p>
 *
 * <p>This filter runs at Order 4 in the filter chain, after the ServiceTypeValidationFilter
 * (Order 3) which runs after the AuthenticationFilter (Order 2) that populates the
 * {@code environmentId} exchange attribute. The Redis write is fire-and-forget — it
 * never blocks the request chain.</p>
 *
 * <p>Redis key pattern: {@code domain:{domainId}:solution:{solutionId}:messageId:{messageId}}</p>
 *
 * <p>Requirements: 9.1, 9.2, 9.3, 9.4, 9.5</p>
 *
 * @see AuthenticationFilter
 */
@Component
public class MessageContextCacheFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MessageContextCacheFilter.class);

    private static final Duration TTL = Duration.ofHours(48);
    private static final String DEFAULT_CONTEXT = "DEFAULT";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * Constructs a MessageContextCacheFilter with the specified Redis template.
     *
     * @param redisTemplate the reactive Redis template for async hash writes
     */
    public MessageContextCacheFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip if no messageId header
        String messageId = exchange.getRequest().getHeaders().getFirst(SifHeaders.MESSAGE_ID);
        if (messageId == null || messageId.isBlank()) {
            return chain.filter(exchange);
        }

        // Skip if not authenticated (environmentId not set by AuthenticationFilter)
        UUID environmentId = exchange.getAttribute("environmentId");
        if (environmentId == null) {
            return chain.filter(exchange);
        }

        // Extract domainId and solutionId from URL path
        String path = exchange.getRequest().getPath().value();
        UrlPrefixParser.ParsedPath parsed = UrlPrefixParser.parse(path);
        String domainId = parsed.domainId();
        String solutionId = parsed.solutionId();

        // Skip if path doesn't contain domain/solution (e.g. /domains, /actuator)
        if (domainId == null || solutionId == null) {
            return chain.filter(exchange);
        }

        // Build Redis key
        String redisKey = "domain:" + domainId + ":solution:" + solutionId + ":messageId:" + messageId;

        // Build hash fields
        String zoneId = exchange.getRequest().getHeaders().getFirst(SifHeaders.ZONE_ID);
        String contextId = exchange.getRequest().getHeaders().getFirst(SifHeaders.CONTEXT_ID);
        if (contextId == null || contextId.isBlank()) {
            contextId = DEFAULT_CONTEXT;
        }

        Map<String, String> hashFields = Map.of(
                "sourceEnvironmentId", environmentId.toString(),
                "zoneId", zoneId != null ? zoneId : "",
                "contextId", contextId
        );

        // Fire-and-forget: write hash + set TTL, log warning on error, never block
        redisTemplate.opsForHash().putAll(redisKey, hashFields)
                .then(redisTemplate.expire(redisKey, TTL))
                .subscribe(
                        result -> log.debug("Cached message context for messageId={} key={}", messageId, redisKey),
                        error -> log.warn("Failed to cache message context for messageId={}: {}", messageId, error.getMessage())
                );

        // Continue filter chain immediately — don't wait for Redis write
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 4; // After ServiceTypeValidationFilter (Order 3)
    }
}
