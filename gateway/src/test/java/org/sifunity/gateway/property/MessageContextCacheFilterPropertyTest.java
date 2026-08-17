// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.gateway.filter.MessageContextCacheFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based test for gateway message context cache key construction.
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 *
 * <p>For any authenticated gateway request containing a messageId header with
 * domainId d, solutionId s, and messageId m, the MessageContextCacheFilter SHALL
 * write a Redis hash at key {@code domain:{d}:solution:{s}:messageId:{m}} with fields
 * sourceEnvironmentId (the authenticated environment's UUID), zoneId (from request
 * header), and contextId (from request header or "DEFAULT" if absent).</p>
 *
 * @see MessageContextCacheFilter
 */
class MessageContextCacheFilterPropertyTest {

    /**
     * Property 11: Gateway message context cache key construction.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     *
     * <p>For any random domainId, solutionId, messageId, environmentId, zoneId,
     * and contextId, the filter writes a Redis hash at the correct key with
     * correct field values.</p>
     */
    @SuppressWarnings("unchecked")
    @Property(tries = 3)
    @Tag("Feature: alerts-service, Property 11: Gateway message context cache key construction")
    @Label("Feature: alerts-service, Property 11: Gateway message context cache key construction")
    void cacheKeyConstructionWithAllHeaders(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("messageIds") String messageId,
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId) {

        // --- Arrange ---
        ReactiveRedisTemplate<String, String> mockRedisTemplate = mock(ReactiveRedisTemplate.class);
        ReactiveHashOperations<String, Object, Object> mockHashOps = mock(ReactiveHashOperations.class);

        when(mockRedisTemplate.opsForHash()).thenReturn(mockHashOps);
        when(mockHashOps.putAll(anyString(), any(Map.class))).thenReturn(Mono.just(true));
        when(mockRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        MessageContextCacheFilter filter = new MessageContextCacheFilter(mockRedisTemplate);

        // Build request with /{domainId}/{solutionId}/some-resource path
        String path = "/" + domainId + "/" + solutionId + "/alerts/alert";
        MockServerHttpRequest request = MockServerHttpRequest
                .post(path)
                .header(SifHeaders.MESSAGE_ID, messageId)
                .header(SifHeaders.ZONE_ID, zoneId)
                .header(SifHeaders.CONTEXT_ID, contextId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set authenticated environment (set by AuthenticationFilter)
        exchange.getAttributes().put("environmentId", environmentId);

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        // Verify correct Redis key
        String expectedKey = "domain:" + domainId + ":solution:" + solutionId + ":messageId:" + messageId;

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> fieldsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(mockHashOps).putAll(keyCaptor.capture(), fieldsCaptor.capture());

        assertThat(keyCaptor.getValue())
                .as("Redis key should follow pattern domain:{d}:solution:{s}:messageId:{m}")
                .isEqualTo(expectedKey);

        Map<String, String> capturedFields = fieldsCaptor.getValue();
        assertThat(capturedFields)
                .as("Hash should contain sourceEnvironmentId, zoneId, contextId")
                .containsEntry("sourceEnvironmentId", environmentId.toString())
                .containsEntry("zoneId", zoneId)
                .containsEntry("contextId", contextId);

        // Verify TTL set on the key
        verify(mockRedisTemplate).expire(eq(expectedKey), eq(Duration.ofHours(48)));

        // Verify chain was called (filter doesn't block)
        verify(mockChain).filter(exchange);
    }

    /**
     * Property 11 (variant): contextId defaults to "DEFAULT" when absent.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     *
     * <p>When the contextId header is absent, the filter should write "DEFAULT"
     * as the contextId field in the Redis hash.</p>
     */
    @SuppressWarnings("unchecked")
    @Property(tries = 3)
    @Tag("Feature: alerts-service, Property 11: Gateway message context cache key construction")
    @Label("Feature: alerts-service, Property 11: contextId defaults to DEFAULT when header absent")
    void contextIdDefaultsToDefaultWhenAbsent(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("messageIds") String messageId,
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("zoneIds") String zoneId) {

        // --- Arrange ---
        ReactiveRedisTemplate<String, String> mockRedisTemplate = mock(ReactiveRedisTemplate.class);
        ReactiveHashOperations<String, Object, Object> mockHashOps = mock(ReactiveHashOperations.class);

        when(mockRedisTemplate.opsForHash()).thenReturn(mockHashOps);
        when(mockHashOps.putAll(anyString(), any(Map.class))).thenReturn(Mono.just(true));
        when(mockRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        MessageContextCacheFilter filter = new MessageContextCacheFilter(mockRedisTemplate);

        // Build request WITHOUT contextId header
        String path = "/" + domainId + "/" + solutionId + "/requests/StudentPersonal";
        MockServerHttpRequest request = MockServerHttpRequest
                .get(path)
                .header(SifHeaders.MESSAGE_ID, messageId)
                .header(SifHeaders.ZONE_ID, zoneId)
                // No contextId header
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("environmentId", environmentId);

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any())).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        String expectedKey = "domain:" + domainId + ":solution:" + solutionId + ":messageId:" + messageId;

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> fieldsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(mockHashOps).putAll(keyCaptor.capture(), fieldsCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);

        Map<String, String> capturedFields = fieldsCaptor.getValue();
        assertThat(capturedFields)
                .containsEntry("sourceEnvironmentId", environmentId.toString())
                .containsEntry("zoneId", zoneId)
                .containsEntry("contextId", "DEFAULT");

        verify(mockRedisTemplate).expire(eq(expectedKey), eq(Duration.ofHours(48)));
        verify(mockChain).filter(exchange);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> messageIds() {
        return Arbitraries.oneOf(
                // UUID-style message IDs
                Arbitraries.randomValue(random -> UUID.randomUUID().toString()),
                // Prefixed message IDs
                Arbitraries.strings()
                        .alpha()
                        .numeric()
                        .withChars('-', '_')
                        .ofMinLength(3)
                        .ofMaxLength(50)
        );
    }

    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of(
                "environment-global",
                "district-1",
                "school-zone-a",
                "test-zone"
        );
    }

    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.of(
                "DEFAULT",
                "context-1",
                "test-context",
                "custom-ctx"
        );
    }
}
