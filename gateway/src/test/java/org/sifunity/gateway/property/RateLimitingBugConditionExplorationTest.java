// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.util.LoggingUtils;
import org.sifunity.gateway.config.RateLimitConfiguration;
import org.sifunity.gateway.filter.RateLimitFilter;
import org.sifunity.gateway.ratelimit.RateLimitResponse;
import org.sifunity.gateway.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug Condition Exploration Tests for Rate Limiting Responses.
 * 
 * <p><strong>CRITICAL</strong>: These tests MUST FAIL on unfixed code - failure confirms the bugs exist.</p>
 * 
 * <p><strong>DO NOT attempt to fix the tests or the code when they fail.</strong></p>
 * 
 * <p><strong>NOTE</strong>: These tests encode the expected behavior - they will validate the fixes 
 * when they pass after implementation.</p>
 * 
 * <p><strong>GOAL</strong>: Surface counterexamples that demonstrate the bugs exist.</p>
 * 
 * <p>Bug Conditions Being Explored:
 * <ul>
 *   <li>1.10: Rate limit exceeded returns 404 instead of 429</li>
 *   <li>1.11: Rate limit exceeded response missing Retry-After header</li>
 *   <li>1.12: Rate limit exceeded response missing X-Correlation-Id header</li>
 *   <li>1.13: Different environments do not maintain independent rate limits</li>
 * </ul>
 * 
 * <p><strong>Validates: Requirements 1.10, 1.11, 1.12, 1.13</strong></p>
 */
@PropertyDefaults(tries = 50)
class RateLimitingBugConditionExplorationTest {
    
    private final ObjectMapper objectMapper = createObjectMapper();
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
    
    // ========== Property 1: Bug Condition - Rate Limiting Responses ==========
    
    /**
     * Property 1: Bug Condition - Rate Limit Exceeded Returns 429
     * 
     * <p>Tests that when rate limit is exceeded, the Gateway returns 429 TOO_MANY_REQUESTS 
     * (not 404 NOT_FOUND).</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because response is 404 instead of 429.</p>
     * 
     * <p><strong>Validates: Requirements 1.10, 2.10</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - Rate limit exceeded returns 429")
    void rateLimitExceeded_Returns429Status(
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("environmentTypes") EnvironmentType environmentType,
        @ForAll("correlationIds") String correlationId,
        @ForAll("requestPaths") String path,
        @ForAll("httpMethods") HttpMethod method,
        @ForAll("retryAfterSeconds") long retryAfterSeconds
    ) {
        // Skip exempt paths
        if (isExemptPath(path, method)) {
            return;
        }
        
        // Arrange
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Mock rate limiter to return denied response (rate limit exceeded)
        when(mockRateLimiter.isAllowed(anyString(), anyInt()))
            .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        
        // Mock rate limit configuration
        when(mockRateLimitConfig.getRequestsPerSecond(environmentType))
            .thenReturn(100);
        
        // Create mock request with environment context
        MockServerHttpRequest request = MockServerHttpRequest
            .method(method, path)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context (set by AuthenticationFilter in real flow)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", environmentType);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        // Create mock filter chain that should NOT be called
        boolean[] chainCalled = {false};
        GatewayFilterChain mockChain = ex -> {
            chainCalled[0] = true;
            return Mono.empty();
        };
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert: Verify the request was blocked
        assertThat(chainCalled[0])
            .as("Filter chain should not be called when rate limit is exceeded")
            .isFalse();
        
        // BUG: This assertion will FAIL on unfixed code - status is 404 instead of 429
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode())
            .as("Rate limit exceeded MUST return 429 TOO_MANY_REQUESTS (not 404)")
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        
        LoggingUtils.clearContext();
    }
    
    /**
     * Property 1: Bug Condition - Rate Limit Response Includes Retry-After Header
     * 
     * <p>Tests that when rate limit is exceeded, the response includes Retry-After header.</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because Retry-After header is missing 
     * (response is 404 instead of 429).</p>
     * 
     * <p><strong>Validates: Requirements 1.11, 2.11</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - Rate limit response includes Retry-After header")
    void rateLimitExceeded_IncludesRetryAfterHeader(
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("environmentTypes") EnvironmentType environmentType,
        @ForAll("correlationIds") String correlationId,
        @ForAll("requestPaths") String path,
        @ForAll("httpMethods") HttpMethod method,
        @ForAll("retryAfterSeconds") long retryAfterSeconds
    ) {
        // Skip exempt paths
        if (isExemptPath(path, method)) {
            return;
        }
        
        // Arrange
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Mock rate limiter to return denied response
        when(mockRateLimiter.isAllowed(anyString(), anyInt()))
            .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        
        when(mockRateLimitConfig.getRequestsPerSecond(environmentType))
            .thenReturn(100);
        
        // Create mock request
        MockServerHttpRequest request = MockServerHttpRequest
            .method(method, path)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", environmentType);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        GatewayFilterChain mockChain = ex -> Mono.empty();
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        
        // BUG: This assertion will FAIL on unfixed code - Retry-After header is missing
        String retryAfterHeader = response.getHeaders().getFirst("Retry-After");
        assertThat(retryAfterHeader)
            .as("Rate limit response MUST include Retry-After header")
            .isNotNull()
            .isEqualTo(String.valueOf(retryAfterSeconds));
        
        LoggingUtils.clearContext();
    }
    
    /**
     * Property 1: Bug Condition - Rate Limit Response Includes X-Correlation-Id Header
     * 
     * <p>Tests that when rate limit is exceeded, the response includes X-Correlation-Id header.</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because X-Correlation-Id header is missing 
     * (response is 404 instead of 429).</p>
     * 
     * <p><strong>Validates: Requirements 1.12, 2.12</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - Rate limit response includes X-Correlation-Id header")
    void rateLimitExceeded_IncludesCorrelationIdHeader(
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("environmentTypes") EnvironmentType environmentType,
        @ForAll("correlationIds") String correlationId,
        @ForAll("requestPaths") String path,
        @ForAll("httpMethods") HttpMethod method,
        @ForAll("retryAfterSeconds") long retryAfterSeconds
    ) {
        // Skip exempt paths
        if (isExemptPath(path, method)) {
            return;
        }
        
        // Arrange
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Mock rate limiter to return denied response
        when(mockRateLimiter.isAllowed(anyString(), anyInt()))
            .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        
        when(mockRateLimitConfig.getRequestsPerSecond(environmentType))
            .thenReturn(100);
        
        // Create mock request
        MockServerHttpRequest request = MockServerHttpRequest
            .method(method, path)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", environmentType);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        GatewayFilterChain mockChain = ex -> Mono.empty();
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        
        // BUG: This assertion will FAIL on unfixed code - X-Correlation-Id header is missing
        String correlationIdHeader = response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
        assertThat(correlationIdHeader)
            .as("Rate limit response MUST include X-Correlation-Id header")
            .isNotNull()
            .isEqualTo(correlationId);
        
        LoggingUtils.clearContext();
    }
    
    /**
     * Property 1: Bug Condition - Independent Rate Limits Per Environment
     * 
     * <p>Tests that different environments maintain independent rate limits.</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because rate limits are not tracked 
     * independently per environment (mock configuration issue or rate limiter key issue).</p>
     * 
     * <p><strong>Validates: Requirements 1.13, 2.13</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - Independent rate limits per environment")
    void differentEnvironments_MaintainIndependentRateLimits(
        @ForAll("environmentIds") UUID environmentId1,
        @ForAll("environmentIds") UUID environmentId2,
        @ForAll("environmentTypes") EnvironmentType environmentType1,
        @ForAll("environmentTypes") EnvironmentType environmentType2,
        @ForAll("correlationIds") String correlationId,
        @ForAll("requestPaths") String path,
        @ForAll("httpMethods") HttpMethod method
    ) {
        // Skip if both environment IDs are the same
        if (environmentId1.equals(environmentId2)) {
            return;
        }
        
        // Skip exempt paths
        if (isExemptPath(path, method)) {
            return;
        }
        
        // Arrange
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Mock rate limit configuration
        when(mockRateLimitConfig.getRequestsPerSecond(environmentType1)).thenReturn(100);
        when(mockRateLimitConfig.getRequestsPerSecond(environmentType2)).thenReturn(100);
        
        // Mock rate limiter to return different responses for different environments
        // Environment 1: Rate limit exceeded (denied)
        when(mockRateLimiter.isAllowed(org.mockito.ArgumentMatchers.eq(environmentId1.toString()), anyInt()))
            .thenReturn(Mono.just(RateLimitResponse.denied(5L)));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(environmentId1.toString()), anyInt()))
            .thenReturn(Mono.just(RateLimitResponse.denied(5L)));
        
        // Environment 2: Within rate limit (allowed)
        when(mockRateLimiter.isAllowed(org.mockito.ArgumentMatchers.eq(environmentId2.toString()), anyInt()))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(environmentId2.toString()), anyInt()))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Test Environment 1 (should be denied)
        MockServerHttpRequest request1 = MockServerHttpRequest
            .method(method, path)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        exchange1.getAttributes().put("environmentId", environmentId1);
        exchange1.getAttributes().put("environmentType", environmentType1);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        boolean[] chain1Called = {false};
        GatewayFilterChain mockChain1 = ex -> {
            chain1Called[0] = true;
            return Mono.empty();
        };
        
        // Act: Process request from environment 1
        StepVerifier.create(filter.filter(exchange1, mockChain1))
            .expectComplete()
            .verify();
        
        // Assert: Environment 1 should be denied
        assertThat(chain1Called[0])
            .as("Environment 1 should be blocked when rate limit is exceeded")
            .isFalse();
        
        // BUG: This assertion will FAIL on unfixed code if rate limits are not independent
        assertThat(exchange1.getResponse().getStatusCode())
            .as("Environment 1 should receive 429 status")
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        
        // Test Environment 2 (should be allowed)
        MockServerHttpRequest request2 = MockServerHttpRequest
            .method(method, path)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        exchange2.getAttributes().put("environmentId", environmentId2);
        exchange2.getAttributes().put("environmentType", environmentType2);
        
        boolean[] chain2Called = {false};
        GatewayFilterChain mockChain2 = ex -> {
            chain2Called[0] = true;
            return Mono.empty();
        };
        
        // Act: Process request from environment 2
        StepVerifier.create(filter.filter(exchange2, mockChain2))
            .expectComplete()
            .verify();
        
        // Assert: Environment 2 should be allowed (independent rate limit)
        assertThat(chain2Called[0])
            .as("Environment 2 should be allowed when within rate limit (independent tracking)")
            .isTrue();
        
        assertThat(exchange2.getResponse().getStatusCode())
            .as("Environment 2 should not have error status (independent rate limit)")
            .isNull();
        
        // Verify that the rate limiter was called with the correct environment IDs
        // This ensures rate limits are tracked independently per environment
        boolean env1Called = org.mockito.Mockito.mockingDetails(mockRateLimiter).getInvocations().stream()
                .anyMatch(inv -> inv.getArguments().length >= 1 &&
                        java.util.Arrays.asList(inv.getArguments()).contains(environmentId1.toString()));
        boolean env2Called = org.mockito.Mockito.mockingDetails(mockRateLimiter).getInvocations().stream()
                .anyMatch(inv -> inv.getArguments().length >= 1 &&
                        java.util.Arrays.asList(inv.getArguments()).contains(environmentId2.toString()));
        assertThat(env1Called).as("Rate limiter should be called for environment 1").isTrue();
        assertThat(env2Called).as("Rate limiter should be called for environment 2").isTrue();
        
        LoggingUtils.clearContext();
    }
    
    // ========== Helper Methods ==========
    
    private boolean isExemptPath(String path, HttpMethod method) {
        // POST /environments is exempt (registration)
        if (path.equals("/environments") && method == HttpMethod.POST) {
            return true;
        }
        // Health checks are exempt
        if (path.startsWith("/actuator/health")) {
            return true;
        }
        return false;
    }
    
    // ========== Arbitraries (Test Data Generators) ==========
    
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(
            EnvironmentType.DIRECT,
            EnvironmentType.BROKERED
        );
    }
    
    @Provide
    Arbitrary<String> correlationIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID().toString());
    }
    
    @Provide
    Arbitrary<String> requestPaths() {
        // Base paths for various services
        Arbitrary<String> basePaths = Arbitraries.of(
            "/requests",
            "/responses",
            "/events",
            "/services",
            "/queues",
            "/subscriptions",
            "/jobs",
            "/acl-entries",
            "/environments"
        );
        
        // Resource names
        Arbitrary<String> resourceNames = Arbitraries.of(
            "StudentPersonal",
            "SchoolInfo",
            "StaffPersonal",
            "StudentAttendance",
            "GradingAssignment"
        );
        
        // Generate various path patterns
        return Arbitraries.oneOf(
            // Simple base paths
            basePaths,
            
            // Base path + resource name
            Combinators.combine(basePaths, resourceNames)
                .as((base, resource) -> base + "/" + resource),
            
            // Base path + resource name + UUID
            Combinators.combine(basePaths, resourceNames, environmentIds())
                .as((base, resource, uuid) -> base + "/" + resource + "/" + uuid)
        );
    }
    
    @Provide
    Arbitrary<HttpMethod> httpMethods() {
        return Arbitraries.of(
            HttpMethod.GET,
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.DELETE,
            HttpMethod.PATCH
        );
    }
    
    @Provide
    Arbitrary<Long> retryAfterSeconds() {
        return Arbitraries.longs().between(1L, 60L);
    }
}
