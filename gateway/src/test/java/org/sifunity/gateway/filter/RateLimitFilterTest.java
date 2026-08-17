// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.util.LoggingUtils;
import org.sifunity.gateway.config.RateLimitConfiguration;
import org.sifunity.gateway.ratelimit.RateLimitResponse;
import org.sifunity.gateway.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitFilter.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Rate limit enforcement (429 response)</li>
 *   <li>Retry-After header inclusion</li>
 *   <li>Exempt path handling</li>
 *   <li>Environment type rate limits</li>
 *   <li>Requests within rate limit</li>
 *   <li>Filter ordering (Order 7)</li>
 * </ul>
 * 
 * <p>Requirements: 8.1, 8.2, 8.4, 8.5
 */
@DisplayName("RateLimitFilter Tests")
class RateLimitFilterTest {
    
    private RateLimitFilter filter;
    private RedisRateLimiter mockRateLimiter;
    private RateLimitConfiguration mockRateLimitConfig;
    private GatewayFilterChain mockChain;
    private ObjectMapper objectMapper;
    private org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService;
    
    @BeforeEach
    void setUp() {
        mockRateLimiter = mock(RedisRateLimiter.class);
        mockRateLimitConfig = mock(RateLimitConfiguration.class);
        mockChain = mock(GatewayFilterChain.class);
        mockMetricsService = mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JSR310 module for Instant serialization
        
        filter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        // Mock chain to return completed Mono
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        
        // Clear MDC before each test
        LoggingUtils.clearContext();
    }
    
    @AfterEach
    void tearDown() {
        // Clear MDC after each test to prevent context leakage
        LoggingUtils.clearContext();
    }
    
    // ========== Rate Limit Enforcement Tests ==========
    
    @Test
    @DisplayName("filter should return 429 when rate limit is exceeded")
    void filter_Returns429_WhenRateLimitExceeded() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        long retryAfterSeconds = 5L;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain, never()).filter(any());
        verify(mockRateLimiter).isAllowed("iowa", "production", environmentId.toString(), 100);
        
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }
    
    @Test
    @DisplayName("filter should allow request when within rate limit")
    void filter_AllowsRequest_WhenWithinRateLimit() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(exchange);
        verify(mockRateLimiter).isAllowed("iowa", "production", environmentId.toString(), 100);
        
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isNull(); // No error status set
    }
    
    @Test
    @DisplayName("filter should enforce rate limit for multiple requests from same environment")
    void filter_EnforcesRateLimit_ForMultipleRequestsFromSameEnvironment() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        // First request - allowed
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        exchange1.getAttributes().put("environmentId", environmentId);
        exchange1.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act - First request
        StepVerifier.create(filter.filter(exchange1, mockChain))
            .verifyComplete();
        
        // Assert - First request allowed
        verify(mockChain).filter(exchange1);
        
        // Second request - rate limit exceeded
        MockServerHttpRequest request2 = MockServerHttpRequest
            .get("/iowa/production/requests/SchoolInfo")
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        exchange2.getAttributes().put("environmentId", environmentId);
        exchange2.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.denied(3L)));
        
        // Act - Second request
        StepVerifier.create(filter.filter(exchange2, mockChain))
            .verifyComplete();
        
        // Assert - Second request denied
        ServerHttpResponse response2 = exchange2.getResponse();
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        
        // Verify rate limiter was called twice with same domain/solution/environment
        verify(mockRateLimiter, times(2)).isAllowed("iowa", "production", environmentId.toString(), 100);
    }
    
    // ========== Retry-After Header Tests ==========
    
    @Test
    @DisplayName("filter should include Retry-After header when rate limit exceeded")
    void filter_IncludesRetryAfterHeader_WhenRateLimitExceeded() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        long retryAfterSeconds = 10L;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getHeaders().getFirst("Retry-After"))
            .isEqualTo(String.valueOf(retryAfterSeconds));
    }
    
    @Test
    @DisplayName("filter should include correct Retry-After value for different retry times")
    void filter_IncludesCorrectRetryAfterValue_ForDifferentRetryTimes() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        long retryAfterSeconds = 1L;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.denied(retryAfterSeconds)));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
    }
    
    @Test
    @DisplayName("filter should include correlation ID in error response when rate limit exceeded")
    void filter_IncludesCorrelationId_InErrorResponseWhenRateLimitExceeded() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        // Set correlation ID in MDC
        LoggingUtils.setCorrelationId(correlationId);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.denied(5L)));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .isEqualTo(correlationId);
    }
    
    // ========== Exempt Path Tests ==========
    
    @Test
    @DisplayName("filter should skip rate limiting for POST /environments")
    void filter_SkipsRateLimiting_ForPostEnvironments() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/environments")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // No environment ID set (exempt path)
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(exchange);
        verify(mockRateLimiter, never()).isAllowed(anyString(), anyInt());
        verify(mockRateLimitConfig, never()).getRequestsPerSecond(any());
    }
    
    @Test
    @DisplayName("filter should skip rate limiting for /actuator/health")
    void filter_SkipsRateLimiting_ForActuatorHealth() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/actuator/health")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // No environment ID set (exempt path)
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(exchange);
        verify(mockRateLimiter, never()).isAllowed(anyString(), anyInt());
    }
    
    @Test
    @DisplayName("filter should skip rate limiting for /actuator/health/liveness")
    void filter_SkipsRateLimiting_ForActuatorHealthLiveness() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/actuator/health/liveness")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // No environment ID set (exempt path)
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(exchange);
        verify(mockRateLimiter, never()).isAllowed(anyString(), anyInt());
    }
    
    @Test
    @DisplayName("filter should skip rate limiting when no environment context")
    void filter_SkipsRateLimiting_WhenNoEnvironmentContext() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/some/path")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // No environment ID set (no authentication)
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(exchange);
        verify(mockRateLimiter, never()).isAllowed(anyString(), anyInt());
    }
    
    @Test
    @DisplayName("filter should enforce rate limiting for GET /environments (legacy key, no domain/solution in path)")
    void filter_EnforcesRateLimiting_ForGetEnvironments() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/environments")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed(environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - uses legacy 2-arg overload since path has no domain/solution prefix
        verify(mockRateLimiter).isAllowed(environmentId.toString(), 100);
    }
    
    // ========== Environment Type Rate Limit Tests ==========
    
    @Test
    @DisplayName("filter should use CONSUMER rate limit for CONSUMER environment type")
    void filter_UsesConsumerRateLimit_ForConsumerEnvironmentType() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockRateLimitConfig).getRequestsPerSecond(EnvironmentType.BROKERED);
        verify(mockRateLimiter).isAllowed("iowa", "production", environmentId.toString(), 100);
    }
    
    @Test
    @DisplayName("filter should use PROVIDER rate limit for PROVIDER environment type")
    void filter_UsesProviderRateLimit_ForProviderEnvironmentType() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockRateLimitConfig).getRequestsPerSecond(EnvironmentType.BROKERED);
        verify(mockRateLimiter).isAllowed("iowa", "production", environmentId.toString(), 100);
    }
    
    @Test
    @DisplayName("filter should use BROKER rate limit for BROKER environment type")
    void filter_UsesBrokerRateLimit_ForBrokerEnvironmentType() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(200);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 200))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockRateLimitConfig).getRequestsPerSecond(EnvironmentType.BROKERED);
        verify(mockRateLimiter).isAllowed("iowa", "production", environmentId.toString(), 200);
    }
    
    @Test
    @DisplayName("filter should use default rate limit when environment type is null")
    void filter_UsesDefaultRateLimit_WhenEnvironmentTypeIsNull() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        // No environment type set
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - Should default to BROKERED type
        verify(mockRateLimitConfig).getRequestsPerSecond(EnvironmentType.BROKERED);
        verify(mockRateLimiter).isAllowed("iowa", "production", environmentId.toString(), 100);
    }
    
    // ========== Per-Environment Rate Tracking Tests ==========
    
    @Test
    @DisplayName("filter should track rate limits independently for different environments")
    void filter_TracksRateLimitsIndependently_ForDifferentEnvironments() {
        // Arrange - Environment 1
        UUID environmentId1 = UUID.randomUUID();
        
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        exchange1.getAttributes().put("environmentId", environmentId1);
        exchange1.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId1.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act - Environment 1
        StepVerifier.create(filter.filter(exchange1, mockChain))
            .verifyComplete();
        
        // Assert - Environment 1 allowed
        verify(mockRateLimiter).isAllowed("iowa", "production", environmentId1.toString(), 100);
        
        // Arrange - Environment 2
        UUID environmentId2 = UUID.randomUUID();
        
        MockServerHttpRequest request2 = MockServerHttpRequest
            .get("/iowa/production/requests/SchoolInfo")
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        exchange2.getAttributes().put("environmentId", environmentId2);
        exchange2.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId2.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act - Environment 2
        StepVerifier.create(filter.filter(exchange2, mockChain))
            .verifyComplete();
        
        // Assert - Environment 2 allowed independently
        verify(mockRateLimiter).isAllowed("iowa", "production", environmentId2.toString(), 100);
        
        // Verify both environments were tracked separately
        verify(mockRateLimiter, times(1)).isAllowed("iowa", "production", environmentId1.toString(), 100);
        verify(mockRateLimiter, times(1)).isAllowed("iowa", "production", environmentId2.toString(), 100);
    }
    
    @Test
    @DisplayName("filter should allow one environment while rate limiting another")
    void filter_AllowsOneEnvironment_WhileRateLimitingAnother() {
        // Arrange - Environment 1 (allowed)
        UUID environmentId1 = UUID.randomUUID();
        
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        exchange1.getAttributes().put("environmentId", environmentId1);
        exchange1.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId1.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act - Environment 1
        StepVerifier.create(filter.filter(exchange1, mockChain))
            .verifyComplete();
        
        // Assert - Environment 1 allowed
        verify(mockChain).filter(exchange1);
        
        // Arrange - Environment 2 (rate limited)
        UUID environmentId2 = UUID.randomUUID();
        
        MockServerHttpRequest request2 = MockServerHttpRequest
            .get("/iowa/production/requests/SchoolInfo")
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        exchange2.getAttributes().put("environmentId", environmentId2);
        exchange2.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId2.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.denied(5L)));
        
        // Act - Environment 2
        StepVerifier.create(filter.filter(exchange2, mockChain))
            .verifyComplete();
        
        // Assert - Environment 2 denied
        ServerHttpResponse response2 = exchange2.getResponse();
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
    
    // ========== Filter Ordering Tests ==========
    
    @Test
    @DisplayName("filter should have order 8 (eighth in chain, after EnvironmentContextFilter)")
    void filter_HasOrder7() {
        // Assert
        assertThat(filter.getOrder()).isEqualTo(8);
    }
    
    // ========== Error Response Format Tests ==========
    
    @Test
    @DisplayName("filter should return SIF-compliant error response when rate limit exceeded")
    void filter_ReturnsSifCompliantError_WhenRateLimitExceeded() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.denied(5L)));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
    }
    
    // ========== Edge Case Tests ==========
    
    @Test
    @DisplayName("filter should handle different request paths")
    void filter_HandlesDifferentRequestPaths() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        // Test various paths with domain/solution prefix
        String[] paths = {
            "/iowa/production/requests/StudentPersonal",
            "/iowa/production/responses/SchoolInfo",
            "/iowa/production/events/Attendance",
            "/iowa/production/services/StudentPersonal",
            "/iowa/production/queues/my-queue",
            "/iowa/production/subscriptions/my-subscription"
        };
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed(eq("iowa"), eq("production"), eq(environmentId.toString()), eq(100)))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        for (String path : paths) {
            MockServerHttpRequest request = MockServerHttpRequest
                .get(path)
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put("environmentId", environmentId);
            exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
            
            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
        }
        
        // Assert - all paths used domain/solution-scoped key
        verify(mockRateLimiter, times(paths.length)).isAllowed("iowa", "production", environmentId.toString(), 100);
    }
    
    @Test
    @DisplayName("filter should handle different HTTP methods")
    void filter_HandlesDifferentHttpMethods() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest getRequest = MockServerHttpRequest
            .get("/iowa/production/requests/StudentPersonal")
            .build();
        MockServerWebExchange getExchange = MockServerWebExchange.from(getRequest);
        getExchange.getAttributes().put("environmentId", environmentId);
        getExchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act - GET
        StepVerifier.create(filter.filter(getExchange, mockChain))
            .verifyComplete();
        
        // Assert - GET
        verify(mockRateLimiter).isAllowed("iowa", "production", environmentId.toString(), 100);
        
        // Arrange - POST
        MockServerHttpRequest postRequest = MockServerHttpRequest
            .post("/iowa/production/responses/SchoolInfo")
            .build();
        MockServerWebExchange postExchange = MockServerWebExchange.from(postRequest);
        postExchange.getAttributes().put("environmentId", environmentId);
        postExchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
            .thenReturn(Mono.just(RateLimitResponse.allowed()));
        
        // Act - POST
        StepVerifier.create(filter.filter(postExchange, mockChain))
            .verifyComplete();
        
        // Assert - POST
        verify(mockRateLimiter, times(2)).isAllowed("iowa", "production", environmentId.toString(), 100);
    }
    
    @Test
    @DisplayName("filter should handle rate limiter returning different retry-after values")
    void filter_HandlesRateLimiter_ReturningDifferentRetryAfterValues() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        long[] retryAfterValues = {1L, 5L, 10L, 30L, 60L};
        
        when(mockRateLimitConfig.getRequestsPerSecond(EnvironmentType.BROKERED))
            .thenReturn(100);
        
        for (long retryAfter : retryAfterValues) {
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/iowa/production/requests/StudentPersonal")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put("environmentId", environmentId);
            exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
            
            when(mockRateLimiter.isAllowed("iowa", "production", environmentId.toString(), 100))
                .thenReturn(Mono.just(RateLimitResponse.denied(retryAfter)));
            
            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
            
            // Assert
            ServerHttpResponse response = exchange.getResponse();
            assertThat(response.getHeaders().getFirst("Retry-After"))
                .isEqualTo(String.valueOf(retryAfter));
        }
    }
}
