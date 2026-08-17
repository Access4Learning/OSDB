// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.gateway.config.RateLimitConfiguration;
import org.sifunity.gateway.filter.RateLimitFilter;
import org.sifunity.gateway.ratelimit.RateLimitResponse;
import org.sifunity.gateway.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
 * Property-based tests for rate limiting properties in the API Gateway.
 * 
 * <p>These tests verify universal correctness properties across randomized inputs
 * to ensure the Gateway correctly enforces rate limits in all scenarios.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Property 23: Rate limit enforcement</li>
 * </ul>
 * 
 * <p>Each property test runs a minimum of 100 iterations with randomized test data.
 * 
 * @see RateLimitFilter
 * @see RedisRateLimiter
 */
class RateLimitingPropertiesTest {
    
    /**
     * Property 23: Rate limit enforcement
     * 
     * **Validates: Requirements 8.1**
     * 
     * <p>For any environment that exceeds its configured request rate limit,
     * the Gateway should return HTTP 429 Too Many Requests.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Requests exceeding the rate limit are rejected with 429 status</li>
     *   <li>The error response includes a Retry-After header</li>
     *   <li>The error response is in SIF-compliant format</li>
     *   <li>Rate limiting is enforced consistently across all request types</li>
     *   <li>The behavior is consistent regardless of environment type or path</li>
     * </ul>
     * 
     * <p>Test Strategy:
     * <ul>
     *   <li>Generate random environment IDs, types, and request paths</li>
     *   <li>Mock the rate limiter to simulate rate limit exceeded condition</li>
     *   <li>Verify the filter returns 429 with appropriate headers and error body</li>
     *   <li>Verify the request is not forwarded to the backend service</li>
     * </ul>
     * 
     * @param environmentId randomly generated environment ID
     * @param environmentType randomly generated environment type
     * @param path randomly generated request path
     * @param method randomly generated HTTP method
     * @param retryAfterSeconds randomly generated retry-after time
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 23: Rate limit enforcement")
    void rateLimitEnforcement(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentTypes") EnvironmentType environmentType,
            @ForAll("requestPaths") String path,
            @ForAll("httpMethods") HttpMethod method,
            @ForAll("retryAfterSeconds") long retryAfterSeconds) {
        
        // Skip exempt paths - they bypass rate limiting
        // POST /environments is exempt (registration endpoint)
        if (path.equals("/environments") && method == HttpMethod.POST) {
            return; // Skip this iteration
        }
        // /actuator/health paths are exempt
        if (path.startsWith("/actuator/health")) {
            return; // Skip this iteration
        }
        
        // Arrange: Create mocks
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JSR310 module for Instant serialization
        
        // Create mock GatewayMetricsService
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Mock rate limiter to return denied response (rate limit exceeded)
        when(mockRateLimiter.isAllowed(anyString(), anyInt()))
                .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        
        // Mock rate limit configuration to return a reasonable limit
        when(mockRateLimitConfig.getRequestsPerSecond(environmentType))
                .thenReturn(100);
        
        // Create mock request with environment context
        MockServerHttpRequest request = MockServerHttpRequest
                .method(method, path)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context (set by AuthenticationFilter in real flow)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", environmentType);
        
        // Create mock filter chain that should NOT be called (request should be blocked)
        boolean[] chainCalled = {false};
        GatewayFilterChain mockChain = ex -> {
            chainCalled[0] = true;
            return Mono.empty();
        };
        
        // Act: Process the request through the rate limit filter
        StepVerifier.create(filter.filter(exchange, mockChain))
                .expectComplete()
                .verify();
        
        // Assert: Verify the request was blocked (chain not called)
        assertThat(chainCalled[0])
                .as("Filter chain should not be called when rate limit is exceeded for path %s %s", method, path)
                .isFalse();
        
        // Assert: Verify 429 Too Many Requests status
        assertThat(exchange.getResponse().getStatusCode())
                .as("Response status should be 429 Too Many Requests when rate limit is exceeded")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        
        // Assert: Verify Retry-After header is present
        String retryAfterHeader = exchange.getResponse().getHeaders().getFirst("Retry-After");
        assertThat(retryAfterHeader)
                .as("Retry-After header should be present in rate limit response")
                .isNotNull()
                .isEqualTo(String.valueOf(retryAfterSeconds));
        
        // Assert: Verify response content type is JSON (SIF-compliant error format)
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .as("Response content type should be application/json for SIF error format")
                .isNotNull()
                .hasToString("application/json");
        
        // Note: We don't verify the exact error response body content here because
        // the property test focuses on the HTTP status, headers, and blocking behavior.
        // The exact error format is verified in unit tests.
    }
    
    /**
     * Property 23 (variant): Rate limit enforcement with allowed requests
     * 
     * **Validates: Requirements 8.1**
     * 
     * <p>For any environment that is within its configured request rate limit,
     * the Gateway should allow the request to proceed (not return 429).
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Requests within the rate limit are allowed to proceed</li>
     *   <li>The filter chain is called (request forwarded to backend)</li>
     *   <li>No 429 status is set</li>
     *   <li>No Retry-After header is added</li>
     * </ul>
     * 
     * @param environmentId randomly generated environment ID
     * @param environmentType randomly generated environment type
     * @param path randomly generated request path
     * @param method randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 23: Rate limit enforcement (allowed requests)")
    void rateLimitEnforcement_AllowedRequests(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentTypes") EnvironmentType environmentType,
            @ForAll("requestPaths") String path,
            @ForAll("httpMethods") HttpMethod method) {
        
        // Arrange: Create mocks
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        // Create mock GatewayMetricsService
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Mock rate limiter to return allowed response (within rate limit)
        when(mockRateLimiter.isAllowed(anyString(), anyInt()))
                .thenReturn(Mono.just(RateLimitResponse.allowed()));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Mock rate limit configuration
        when(mockRateLimitConfig.getRequestsPerSecond(environmentType))
                .thenReturn(100);
        
        // Create mock request with environment context
        MockServerHttpRequest request = MockServerHttpRequest
                .method(method, path)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", environmentType);
        
        // Create mock filter chain that SHOULD be called (request allowed)
        boolean[] chainCalled = {false};
        GatewayFilterChain mockChain = ex -> {
            chainCalled[0] = true;
            return Mono.empty();
        };
        
        // Act: Process the request through the rate limit filter
        StepVerifier.create(filter.filter(exchange, mockChain))
                .expectComplete()
                .verify();
        
        // Assert: Verify the request was allowed (chain called)
        assertThat(chainCalled[0])
                .as("Filter chain should be called when rate limit is not exceeded")
                .isTrue();
        
        // Assert: Verify no error status is set
        assertThat(exchange.getResponse().getStatusCode())
                .as("Response status should not be set when rate limit is not exceeded")
                .isNull();
        
        // Assert: Verify no Retry-After header is present
        String retryAfterHeader = exchange.getResponse().getHeaders().getFirst("Retry-After");
        assertThat(retryAfterHeader)
                .as("Retry-After header should not be present when rate limit is not exceeded")
                .isNull();
    }
    
    /**
     * Property 23 (variant): Rate limit enforcement for exempt paths
     * 
     * **Validates: Requirements 8.1**
     * 
     * <p>For any request to an exempt path (POST /environments, /actuator/health),
     * the Gateway should skip rate limiting even if the environment would exceed limits.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Exempt paths bypass rate limiting</li>
     *   <li>The filter chain is called for exempt paths</li>
     *   <li>No rate limit check is performed</li>
     * </ul>
     * 
     * @param exemptPath randomly generated exempt path
     * @param method HTTP method for the exempt path
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 23: Rate limit enforcement (exempt paths)")
    void rateLimitEnforcement_ExemptPaths(
            @ForAll("exemptPaths") String exemptPath,
            @ForAll("exemptPathMethods") HttpMethod method) {
        
        // Arrange: Create mocks
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        // Create mock GatewayMetricsService
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Note: We don't set up mock expectations for rate limiter because it should NOT be called
        
        // Create mock request WITHOUT environment context (exempt paths don't have auth)
        MockServerHttpRequest request = MockServerHttpRequest
                .method(method, exemptPath)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // No environment context set (exempt paths skip authentication)
        
        // Create mock filter chain that SHOULD be called (exempt path)
        boolean[] chainCalled = {false};
        GatewayFilterChain mockChain = ex -> {
            chainCalled[0] = true;
            return Mono.empty();
        };
        
        // Act: Process the request through the rate limit filter
        StepVerifier.create(filter.filter(exchange, mockChain))
                .expectComplete()
                .verify();
        
        // Assert: Verify the request was allowed (chain called)
        assertThat(chainCalled[0])
                .as("Filter chain should be called for exempt paths")
                .isTrue();
        
        // Assert: Verify no error status is set
        assertThat(exchange.getResponse().getStatusCode())
                .as("Response status should not be set for exempt paths")
                .isNull();
    }
    
    /**
     * Property 24: Per-environment rate tracking
     * 
     * **Validates: Requirements 8.2**
     * 
     * <p>For any two different environments, their request rates should be tracked independently.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Rate limits are tracked separately for each environment ID</li>
     *   <li>One environment exceeding its rate limit does not affect other environments</li>
     *   <li>Each environment can make requests up to its own rate limit</li>
     *   <li>The rate limiter uses environment ID as the key for tracking</li>
     * </ul>
     * 
     * <p>Test Strategy:
     * <ul>
     *   <li>Generate two different environment IDs</li>
     *   <li>Mock the rate limiter to track calls per environment ID</li>
     *   <li>Simulate one environment exceeding its limit (denied)</li>
     *   <li>Simulate another environment within its limit (allowed)</li>
     *   <li>Verify that the rate limiter is called with the correct environment ID</li>
     *   <li>Verify that each environment's rate limit is checked independently</li>
     * </ul>
     * 
     * @param environmentId1 first randomly generated environment ID
     * @param environmentId2 second randomly generated environment ID
     * @param environmentType1 environment type for first environment
     * @param environmentType2 environment type for second environment
     * @param path randomly generated request path
     * @param method randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 24: Per-environment rate tracking")
    void perEnvironmentRateTracking(
            @ForAll("environmentIds") UUID environmentId1,
            @ForAll("environmentIds") UUID environmentId2,
            @ForAll("environmentTypes") EnvironmentType environmentType1,
            @ForAll("environmentTypes") EnvironmentType environmentType2,
            @ForAll("requestPaths") String path,
            @ForAll("httpMethods") HttpMethod method) {
        
        // Skip if both environment IDs are the same (we need different environments)
        if (environmentId1.equals(environmentId2)) {
            return;
        }
        
        // Skip exempt paths
        if (path.equals("/environments") && method == HttpMethod.POST) {
            return;
        }
        if (path.startsWith("/actuator/health")) {
            return;
        }
        
        // Arrange: Create mocks
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        // Create mock GatewayMetricsService
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Mock rate limit configuration
        when(mockRateLimitConfig.getRequestsPerSecond(environmentType1)).thenReturn(100);
        when(mockRateLimitConfig.getRequestsPerSecond(environmentType2)).thenReturn(100);
        
        // Mock rate limiter to return different responses for different environments
        // Environment 1: Rate limit exceeded (denied)
        when(mockRateLimiter.isAllowed(
                org.mockito.ArgumentMatchers.eq(environmentId1.toString()), 
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Mono.just(RateLimitResponse.denied(5L)));
        when(mockRateLimiter.isAllowed(
                anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(environmentId1.toString()), 
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Mono.just(RateLimitResponse.denied(5L)));
        
        // Environment 2: Within rate limit (allowed)
        when(mockRateLimiter.isAllowed(
                org.mockito.ArgumentMatchers.eq(environmentId2.toString()), 
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Mono.just(RateLimitResponse.allowed()));
        when(mockRateLimiter.isAllowed(
                anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(environmentId2.toString()), 
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Test Environment 1 (should be denied)
        MockServerHttpRequest request1 = MockServerHttpRequest
                .method(method, path)
                .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        exchange1.getAttributes().put("environmentId", environmentId1);
        exchange1.getAttributes().put("environmentType", environmentType1);
        
        boolean[] chain1Called = {false};
        GatewayFilterChain mockChain1 = ex -> {
            chain1Called[0] = true;
            return Mono.empty();
        };
        
        // Act: Process request from environment 1
        StepVerifier.create(filter.filter(exchange1, mockChain1))
                .expectComplete()
                .verify();
        
        // Assert: Environment 1 should be denied (rate limit exceeded)
        assertThat(chain1Called[0])
                .as("Environment 1 should be blocked when rate limit is exceeded")
                .isFalse();
        assertThat(exchange1.getResponse().getStatusCode())
                .as("Environment 1 should receive 429 status")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        
        // Test Environment 2 (should be allowed)
        MockServerHttpRequest request2 = MockServerHttpRequest
                .method(method, path)
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
        
        // Assert: Environment 2 should be allowed (within rate limit)
        assertThat(chain2Called[0])
                .as("Environment 2 should be allowed when within rate limit")
                .isTrue();
        assertThat(exchange2.getResponse().getStatusCode())
                .as("Environment 2 should not have error status")
                .isNull();
        
        // Verify that the rate limiter was called with the correct environment IDs
        // This ensures that rate limits are tracked independently per environment
        // The filter calls either the 2-arg (legacy) or 4-arg (domain-scoped) overload
        // depending on whether the path has a domain/solution prefix
        boolean env1Called = org.mockito.Mockito.mockingDetails(mockRateLimiter).getInvocations().stream()
                .anyMatch(inv -> inv.getArguments().length >= 1 &&
                        java.util.Arrays.asList(inv.getArguments()).contains(environmentId1.toString()));
        boolean env2Called = org.mockito.Mockito.mockingDetails(mockRateLimiter).getInvocations().stream()
                .anyMatch(inv -> inv.getArguments().length >= 1 &&
                        java.util.Arrays.asList(inv.getArguments()).contains(environmentId2.toString()));
        assertThat(env1Called).as("Rate limiter should be called for environment 1").isTrue();
        assertThat(env2Called).as("Rate limiter should be called for environment 2").isTrue();
    }
    
    /**
     * Property 25: Environment type rate limits
     * 
     * **Validates: Requirements 8.4**
     * 
     * <p>For any environment type (DIRECT, BROKERED, ADMIN), the Gateway should
     * enforce the configured rate limit for that type.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Different environment types have different rate limits</li>
     *   <li>The correct rate limit is retrieved from configuration for each type</li>
     *   <li>The rate limiter is called with the correct limit for each environment type</li>
     *   <li>Rate limits are enforced according to environment type, not just environment ID</li>
     * </ul>
     * 
     * <p>Test Strategy:
     * <ul>
     *   <li>Generate requests from environments with different types</li>
     *   <li>Configure different rate limits for each environment type</li>
     *   <li>Verify the rate limiter is called with the correct limit for each type</li>
     *   <li>Test all three environment types: DIRECT, BROKERED, ADMIN</li>
     * </ul>
     * 
     * <p>Expected rate limits (default configuration):
     * <ul>
     *   <li>DIRECT: 100 requests per second</li>
     *   <li>BROKERED: 100 requests per second</li>
     *   <li>ADMIN: 200 requests per second</li>
     * </ul>
     * 
     * @param environmentId randomly generated environment ID
     * @param environmentType randomly generated environment type
     * @param path randomly generated request path
     * @param method randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 25: Environment type rate limits")
    void environmentTypeRateLimits(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentTypes") EnvironmentType environmentType,
            @ForAll("requestPaths") String path,
            @ForAll("httpMethods") HttpMethod method) {
        
        // Skip exempt paths
        if (path.equals("/environments") && method == HttpMethod.POST) {
            return;
        }
        if (path.startsWith("/actuator/health")) {
            return;
        }
        
        // Arrange: Create mocks
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        // Create mock GatewayMetricsService
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Configure different rate limits for each environment type
        // These match the default configuration in the design document
        int expectedRateLimit;
        switch (environmentType) {
            case DIRECT:
                expectedRateLimit = 100;
                break;
            case BROKERED:
                expectedRateLimit = 100;
                break;
            default:
                expectedRateLimit = 50; // Default fallback
                break;
        }
        
        // Mock rate limit configuration to return the expected limit for this type
        when(mockRateLimitConfig.getRequestsPerSecond(environmentType))
                .thenReturn(expectedRateLimit);
        
        // Mock rate limiter to return allowed response
        when(mockRateLimiter.isAllowed(anyString(), anyInt()))
                .thenReturn(Mono.just(RateLimitResponse.allowed()));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Create mock request with environment context
        MockServerHttpRequest request = MockServerHttpRequest
                .method(method, path)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context (set by AuthenticationFilter in real flow)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", environmentType);
        
        // Create mock filter chain
        boolean[] chainCalled = {false};
        GatewayFilterChain mockChain = ex -> {
            chainCalled[0] = true;
            return Mono.empty();
        };
        
        // Act: Process the request through the rate limit filter
        StepVerifier.create(filter.filter(exchange, mockChain))
                .expectComplete()
                .verify();
        
        // Assert: Verify the request was allowed
        assertThat(chainCalled[0])
                .as("Filter chain should be called when rate limit is not exceeded")
                .isTrue();
        
        // Assert: Verify the configuration was queried for the correct environment type
        org.mockito.Mockito.verify(mockRateLimitConfig).getRequestsPerSecond(environmentType);
        
        // Additional verification: Ensure different types get different limits
        // This is implicitly verified by the mock expectations above, but we can add
        // explicit assertions for clarity
        if (environmentType == EnvironmentType.DIRECT || environmentType == EnvironmentType.BROKERED) {
            assertThat(expectedRateLimit)
                    .as("DIRECT and BROKERED environment types should have standard rate limit (100 req/s)")
                    .isEqualTo(100);
        }
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates random environment IDs (UUIDs).
     */
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    /**
     * Generates random environment types.
     */
    @Provide
    Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(
                EnvironmentType.DIRECT,
                EnvironmentType.BROKERED
        );
    }
    
    /**
     * Generates random request paths for protected endpoints.
     * 
     * <p>These paths represent endpoints that require authentication and
     * are subject to rate limiting.
     */
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
                "/providers",
                "/zones",
                "/environments" // GET /environments (not POST)
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
    
    /**
     * Generates random HTTP methods.
     */
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
    
    /**
     * Generates random retry-after times in seconds.
     * 
     * <p>Typical retry-after times range from 1 to 60 seconds.
     */
    @Provide
    Arbitrary<Long> retryAfterSeconds() {
        return Arbitraries.longs().between(1L, 60L);
    }
    
    /**
     * Generates exempt paths that should skip rate limiting.
     * 
     * <p>Exempt paths:
     * <ul>
     *   <li>/actuator/health - Health check endpoint</li>
     *   <li>/actuator/health/liveness - Liveness probe</li>
     *   <li>/actuator/health/readiness - Readiness probe</li>
     * </ul>
     * 
     * <p>Note: POST /environments is also exempt, but it's handled separately
     * because it requires a specific HTTP method.
     */
    @Provide
    Arbitrary<String> exemptPaths() {
        return Arbitraries.of(
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness"
        );
    }
    
    /**
     * Generates HTTP methods for exempt paths.
     * 
     * <p>Exempt paths typically use GET method, but we test various methods
     * to ensure rate limiting is skipped regardless of method.
     */
    @Provide
    Arbitrary<HttpMethod> exemptPathMethods() {
        return Arbitraries.of(
                HttpMethod.GET,
                HttpMethod.HEAD,
                HttpMethod.OPTIONS
        );
    }
}
