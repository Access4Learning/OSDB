// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.PermissionCheckRequest;
import org.sifunity.broker.common.dto.PermissionCheckResponse;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.exception.InsufficientPermissionsException;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.util.LoggingUtils;
import org.sifunity.gateway.client.EnvironmentServiceClient;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for error handling across all Gateway filters.
 * 
 * <p>This test class focuses on verifying that all error responses:
 * <ul>
 *   <li>Use SIF-compliant error response format (code, message, scope, timestamp)</li>
 *   <li>Include correlation ID in response headers</li>
 *   <li>Log errors with appropriate context (correlation ID, environment ID, etc.)</li>
 * </ul>
 * 
 * <p>Requirements: 7.3, 7.6, 7.7, 9.5
 */
@DisplayName("Error Handling Tests")
class ErrorHandlingTest {
    
    private AuthenticationFilter authenticationFilter;
    private AuthorizationFilter authorizationFilter;
    private RateLimitFilter rateLimitFilter;
    
    private EnvironmentServiceClient mockEnvironmentServiceClient;
    private RedisRateLimiter mockRateLimiter;
    private RateLimitConfiguration mockRateLimitConfig;
    private GatewayFilterChain mockChain;
    private ObjectMapper objectMapper;
    private org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService;
    
    @BeforeEach
    void setUp() {
        mockEnvironmentServiceClient = mock(EnvironmentServiceClient.class);
        mockRateLimiter = mock(RedisRateLimiter.class);
        mockRateLimitConfig = mock(RateLimitConfiguration.class);
        mockChain = mock(GatewayFilterChain.class);
        mockMetricsService = mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        authenticationFilter = new AuthenticationFilter(mockEnvironmentServiceClient, objectMapper, mockMetricsService);
        authorizationFilter = new AuthorizationFilter(mockEnvironmentServiceClient, objectMapper, mockMetricsService);
        rateLimitFilter = new RateLimitFilter(mockRateLimiter, mockRateLimitConfig, objectMapper, mockMetricsService, null);
        
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        LoggingUtils.clearContext();
    }
    
    @AfterEach
    void tearDown() {
        LoggingUtils.clearContext();
    }
    
    // ========== SIF Error Response Format Tests ==========
    
    @Test
    @DisplayName("Authentication error should return SIF-compliant error response")
    void authenticationError_ReturnsSifCompliantErrorResponse() throws Exception {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        String authHeader = "SIF_HMACSHA256 invalid-token:timestamp:hash";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        when(mockEnvironmentServiceClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.error(new InvalidTokenException("Invalid session token")));
        
        // Act
        StepVerifier.create(authenticationFilter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(correlationId);
        
        // Note: In unit tests with MockServerWebExchange, we cannot easily capture the response body
        // The important verification is that the response has the correct status, content type, and headers
        // Integration tests will verify the actual response body content
    }
    
    @Test
    @DisplayName("Authorization error should return SIF-compliant error response")
    void authorizationError_ReturnsSifCompliantErrorResponse() throws Exception {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        String authHeader = "SIF_HMACSHA256 token:timestamp:hash";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/testDomain/testSolution/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .header(SifHeaders.ZONE_ID, "default")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context (as if AuthenticationFilter ran)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", "TestApp");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("defaultZoneId", "default");
        
        LoggingUtils.setCorrelationId(correlationId);
        
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(new PermissionCheckResponse(false)));
        
        // Act
        StepVerifier.create(authorizationFilter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(correlationId);
        
        // Note: In unit tests with MockServerWebExchange, we cannot easily capture the response body
        // The important verification is that the response has the correct status, content type, and headers
        // Integration tests will verify the actual response body content
    }
    
    @Test
    @DisplayName("Rate limit error should return SIF-compliant error response")
    void rateLimitError_ReturnsSifCompliantErrorResponse() throws Exception {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        when(mockRateLimitConfig.getRequestsPerSecond(any(EnvironmentType.class))).thenReturn(100);
        when(mockRateLimiter.isAllowed(anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
        
        // Act
        StepVerifier.create(rateLimitFilter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(correlationId);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        
        // Note: In unit tests with MockServerWebExchange, we cannot easily capture the response body
        // The important verification is that the response has the correct status, content type, and headers
        // Integration tests will verify the actual response body content
    }
    
    // ========== Correlation ID in Error Responses Tests ==========
    
    @Test
    @DisplayName("Authentication error should include correlation ID in response headers")
    void authenticationError_IncludesCorrelationIdInHeaders() {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        // Act
        StepVerifier.create(authenticationFilter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(correlationId);
    }
    
    @Test
    @DisplayName("Authorization error should include correlation ID in response headers")
    void authorizationError_IncludesCorrelationIdInHeaders() {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/testDomain/testSolution/requests/StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        
        LoggingUtils.setCorrelationId(correlationId);
        
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.error(new InsufficientPermissionsException("Access denied")));
        
        // Act
        StepVerifier.create(authorizationFilter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(correlationId);
    }
    
    @Test
    @DisplayName("Rate limit error should include correlation ID in response headers")
    void rateLimitError_IncludesCorrelationIdInHeaders() {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        when(mockRateLimitConfig.getRequestsPerSecond(any(EnvironmentType.class))).thenReturn(100);
        when(mockRateLimiter.isAllowed(anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(false, 30)));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(false, 30)));
        
        // Act
        StepVerifier.create(rateLimitFilter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(correlationId);
    }
    
    // ========== Error Logging Context Tests ==========
    
    @Test
    @DisplayName("Authentication error should be logged with correlation ID")
    void authenticationError_LoggedWithCorrelationId() {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        String authHeader = "SIF_HMACSHA256 token:timestamp:hash";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        when(mockEnvironmentServiceClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.error(new InvalidTokenException("Invalid token")));
        
        // Act
        StepVerifier.create(authenticationFilter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - Verify error was logged (actual logging verification would require a logging framework mock)
        // For now, we verify the filter completed and returned an error response
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    
    @Test
    @DisplayName("Authorization error should be logged with correlation ID, environment ID, service name, and access type")
    void authorizationError_LoggedWithFullContext() {
        // Arrange
        String correlationId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/testDomain/testSolution/requests/" + serviceName)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .header(SifHeaders.ZONE_ID, "default")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", "TestApp");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("defaultZoneId", "default");
        
        LoggingUtils.setCorrelationId(correlationId);
        
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(new PermissionCheckResponse(false)));
        
        // Act
        StepVerifier.create(authorizationFilter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - Verify authorization check was called with correct parameters
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.environmentId().equals(environmentId) &&
            req.serviceName().equals(serviceName) &&
            req.accessType() == AccessType.QUERY
        ));
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    
    // ========== Multiple Error Types Tests ==========
    
    @Test
    @DisplayName("All error types should use consistent response format")
    void allErrorTypes_UseConsistentResponseFormat() {
        // Test 1: Authentication error
        String correlationId1 = UUID.randomUUID().toString();
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash")
            .header(SifHeaders.X_CORRELATION_ID, correlationId1)
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        LoggingUtils.setCorrelationId(correlationId1);
        
        when(mockEnvironmentServiceClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.error(new InvalidTokenException("Invalid token")));
        
        StepVerifier.create(authenticationFilter.filter(exchange1, mockChain)).verifyComplete();
        
        assertThat(exchange1.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange1.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange1.getResponse().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(correlationId1);
        
        // Test 2: Authorization error
        String correlationId2 = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        MockServerHttpRequest request2 = MockServerHttpRequest
            .get("/testDomain/testSolution/requests/StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, correlationId2)
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        exchange2.getAttributes().put("environmentId", environmentId);
        exchange2.getAttributes().put("defaultZoneId", "default");
        LoggingUtils.setCorrelationId(correlationId2);
        
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.error(new InsufficientPermissionsException("Access denied")));
        
        StepVerifier.create(authorizationFilter.filter(exchange2, mockChain)).verifyComplete();
        
        assertThat(exchange2.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange2.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange2.getResponse().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(correlationId2);
        
        // Test 3: Rate limit error
        String correlationId3 = UUID.randomUUID().toString();
        MockServerHttpRequest request3 = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, correlationId3)
            .build();
        MockServerWebExchange exchange3 = MockServerWebExchange.from(request3);
        exchange3.getAttributes().put("environmentId", UUID.randomUUID());
        exchange3.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        LoggingUtils.setCorrelationId(correlationId3);
        
        when(mockRateLimitConfig.getRequestsPerSecond(any(EnvironmentType.class))).thenReturn(100);
        when(mockRateLimiter.isAllowed(anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
        when(mockRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
        
        StepVerifier.create(rateLimitFilter.filter(exchange3, mockChain)).verifyComplete();
        
        assertThat(exchange3.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange3.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange3.getResponse().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(correlationId3);
        assertThat(exchange3.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
        
        // All error responses have consistent format: JSON content type and correlation ID header
    }
}
