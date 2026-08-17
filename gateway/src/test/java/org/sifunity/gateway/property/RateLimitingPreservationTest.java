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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Preservation Property Tests for Rate Limiting Behavior
 * 
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on unfixed code.</strong>
 * These tests establish the baseline behavior that must be preserved after the fix.
 * 
 * <p><strong>Purpose:</strong>
 * These property-based tests verify that rate limiting continues to work correctly
 * for allowed requests after fixing the rate limiting bugs. They test:
 * <ul>
 *   <li>Requests within rate limits continue to process successfully</li>
 *   <li>Endpoints without rate limiting configured continue to allow unlimited requests</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior:</strong>
 * All tests in this class should PASS on the unfixed code, confirming that the
 * successful request processing is correct and should be preserved.
 * 
 * <p><strong>Validates: Requirements 3.7, 3.8</strong>
 * 
 * @see RateLimitingBugConditionExplorationTest
 */
@PropertyDefaults(tries = 50)
class RateLimitingPreservationTest {
    
    private final ObjectMapper objectMapper = createObjectMapper();
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
    
    // ========== Property 2: Preservation - Rate Limit Allowed Requests ==========
    
    /**
     * Property 2.1: Preservation - Requests Within Rate Limits Process Successfully
     * 
     * <p><strong>Validates: Requirement 3.7</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that when requests are within the rate limit for an environment,
     * they continue to be processed successfully. The filter should allow the request to proceed
     * through the filter chain without blocking or returning an error response.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Filter chain is called for requests within rate limits</li>
     *   <li>No error status is set for allowed requests</li>
     *   <li>No rate limit headers (Retry-After) are added for allowed requests</li>
     *   <li>Request proceeds normally through the gateway</li>
     * </ul>
     * 
     * @param environmentId randomly generated environment ID
     * @param environmentType randomly generated environment type
     * @param correlationId randomly generated correlation ID
     * @param path randomly generated request path
     * @param method randomly generated HTTP method
     */
    @Property
    @Label("Feature: gateway, Preservation: Requests within rate limits process successfully")
    void requestsWithinRateLimit_ProcessSuccessfully(
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("environmentTypes") EnvironmentType environmentType,
        @ForAll("correlationIds") String correlationId,
        @ForAll("requestPaths") String path,
        @ForAll("httpMethods") HttpMethod method
    ) {
        // Skip exempt paths (they bypass rate limiting)
        if (isExemptPath(path, method)) {
            return;
        }
        
        // Arrange
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
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
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context (set by AuthenticationFilter in real flow)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", environmentType);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        // Track if filter chain was called
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain mockChain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert: Verify the request was allowed to proceed
        assertThat(chainCalled.get())
            .as("Filter chain should be called for requests within rate limit")
            .isTrue();
        
        // Verify no error status was set
        ServerHttpResponse response = exchange.getResponse();
        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        assertThat(statusCode)
            .as("No error status should be set for requests within rate limit")
            .isNull();
        
        // Verify no Retry-After header was added
        String retryAfterHeader = response.getHeaders().getFirst("Retry-After");
        assertThat(retryAfterHeader)
            .as("Retry-After header should not be present for allowed requests")
            .isNull();
        
        LoggingUtils.clearContext();
    }
    
    /**
     * Property 2.2: Preservation - Endpoints Without Rate Limiting Allow Unlimited Requests
     * 
     * <p><strong>Validates: Requirement 3.8</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that endpoints without rate limiting configured (no environment context)
     * continue to allow unlimited requests. These are typically public endpoints like health checks
     * or environment registration that should not be rate limited.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Requests without environment context skip rate limiting</li>
     *   <li>Filter chain is called for requests without environment context</li>
     *   <li>No rate limit checks are performed for exempt endpoints</li>
     *   <li>Request proceeds normally without rate limiting</li>
     * </ul>
     * 
     * @param correlationId randomly generated correlation ID
     * @param path randomly generated request path
     * @param method randomly generated HTTP method
     */
    @Property
    @Label("Feature: gateway, Preservation: Endpoints without rate limiting allow unlimited requests")
    void endpointsWithoutRateLimiting_AllowUnlimitedRequests(
        @ForAll("correlationIds") String correlationId,
        @ForAll("requestPaths") String path,
        @ForAll("httpMethods") HttpMethod method
    ) {
        // Arrange
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Create mock request WITHOUT environment context (simulates public endpoint)
        MockServerHttpRequest request = MockServerHttpRequest
            .method(method, path)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // DO NOT set environment context - this simulates a public endpoint
        // exchange.getAttributes().put("environmentId", environmentId); // NOT SET
        
        LoggingUtils.setCorrelationId(correlationId);
        
        // Track if filter chain was called
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain mockChain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert: Verify the request was allowed to proceed without rate limiting
        assertThat(chainCalled.get())
            .as("Filter chain should be called for endpoints without rate limiting")
            .isTrue();
        
        // Verify no error status was set
        ServerHttpResponse response = exchange.getResponse();
        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        assertThat(statusCode)
            .as("No error status should be set for endpoints without rate limiting")
            .isNull();
        
        // Verify no Retry-After header was added
        String retryAfterHeader = response.getHeaders().getFirst("Retry-After");
        assertThat(retryAfterHeader)
            .as("Retry-After header should not be present for endpoints without rate limiting")
            .isNull();
        
        LoggingUtils.clearContext();
    }
    
    /**
     * Property 2.3: Preservation - Exempt Paths Bypass Rate Limiting
     * 
     * <p><strong>Validates: Requirement 3.8</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that explicitly exempt paths (like POST /environments for registration
     * and /actuator/health for health checks) continue to bypass rate limiting even when they have
     * environment context. These paths are critical for system operation and should never be rate limited.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>POST /environments bypasses rate limiting</li>
     *   <li>/actuator/health bypasses rate limiting</li>
     *   <li>Filter chain is called for exempt paths</li>
     *   <li>No rate limit checks are performed for exempt paths</li>
     * </ul>
     * 
     * @param environmentId randomly generated environment ID
     * @param environmentType randomly generated environment type
     * @param correlationId randomly generated correlation ID
     * @param exemptPath randomly generated exempt path
     */
    @Property
    @Label("Feature: gateway, Preservation: Exempt paths bypass rate limiting")
    void exemptPaths_BypassRateLimiting(
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("environmentTypes") EnvironmentType environmentType,
        @ForAll("correlationIds") String correlationId,
        @ForAll("exemptPaths") ExemptPathConfig exemptPath
    ) {
        // Arrange
        RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
        RateLimitConfiguration mockRateLimitConfig = mock(RateLimitConfiguration.class);
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        RateLimitFilter filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Create mock request for exempt path
        MockServerHttpRequest request = MockServerHttpRequest
            .method(exemptPath.method, exemptPath.path)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context (even with context, exempt paths should bypass rate limiting)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", environmentType);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        // Track if filter chain was called
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain mockChain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert: Verify the request was allowed to proceed without rate limiting
        assertThat(chainCalled.get())
            .as("Filter chain should be called for exempt paths")
            .isTrue();
        
        // Verify no error status was set
        ServerHttpResponse response = exchange.getResponse();
        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        assertThat(statusCode)
            .as("No error status should be set for exempt paths")
            .isNull();
        
        // Verify no Retry-After header was added
        String retryAfterHeader = response.getHeaders().getFirst("Retry-After");
        assertThat(retryAfterHeader)
            .as("Retry-After header should not be present for exempt paths")
            .isNull();
        
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
    
    // ========== Helper Classes ==========
    
    /**
     * Configuration for exempt paths (paths that bypass rate limiting)
     */
    private static class ExemptPathConfig {
        final String path;
        final HttpMethod method;
        
        ExemptPathConfig(String path, HttpMethod method) {
            this.path = path;
            this.method = method;
        }
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
    Arbitrary<ExemptPathConfig> exemptPaths() {
        return Arbitraries.oneOf(
            // POST /environments (registration endpoint)
            Arbitraries.just(new ExemptPathConfig("/environments", HttpMethod.POST)),
            
            // Health check endpoints
            Arbitraries.of(
                new ExemptPathConfig("/actuator/health", HttpMethod.GET),
                new ExemptPathConfig("/actuator/health/liveness", HttpMethod.GET),
                new ExemptPathConfig("/actuator/health/readiness", HttpMethod.GET)
            )
        );
    }
}
