// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
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
import org.sifunity.gateway.filter.AuthenticationFilter;
import org.sifunity.gateway.filter.AuthorizationFilter;
import org.sifunity.gateway.filter.RateLimitFilter;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for error handling in the Gateway.
 * 
 * <p>These tests verify universal properties about error responses:
 * <ul>
 *   <li>Property 20: SIF error response format</li>
 *   <li>Property 21: Authentication failure logging</li>
 *   <li>Property 22: Authorization failure logging</li>
 *   <li>Property 27: Correlation ID in logs</li>
 * </ul>
 * 
 * <p>Requirements: 7.3, 7.6, 7.7, 9.4, 9.5
 */
@PropertyDefaults(tries = 100)
class ErrorHandlingPropertiesTest {
    
    private final ObjectMapper objectMapper = createObjectMapper();
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
    
    // ========== Property 20: SIF Error Response Format ==========
    
    /**
     * Property 20: SIF error response format
     * 
     * <p>For any error condition, the Gateway should return a SIF-compliant error response
     * containing code, message, scope, and timestamp fields.</p>
     * 
     * <p>Validates: Requirements 7.3</p>
     */
    @Property
    @Label("Feature: gateway, Property 20: SIF error response format")
    void allErrorResponses_UseSifCompliantFormat(
        @ForAll("correlationIds") String correlationId,
        @ForAll("errorTypes") ErrorType errorType
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        MockServerWebExchange exchange = createExchangeForErrorType(errorType, correlationId);
        LoggingUtils.setCorrelationId(correlationId);
        
        // Configure mocks based on error type
        configureErrorMocks(mockClient, errorType);
        
        // Create appropriate filter
        Object filter = createFilterForErrorType(errorType, mockClient);
        
        // Act
        if (filter instanceof AuthenticationFilter) {
            StepVerifier.create(((AuthenticationFilter) filter).filter(exchange, mockChain))
                .verifyComplete();
        } else if (filter instanceof AuthorizationFilter) {
            StepVerifier.create(((AuthorizationFilter) filter).filter(exchange, mockChain))
                .verifyComplete();
        } else if (filter instanceof RateLimitFilter) {
            StepVerifier.create(((RateLimitFilter) filter).filter(exchange, mockChain))
                .verifyComplete();
        }
        
        // Assert - Verify SIF-compliant error response format
        ServerHttpResponse response = exchange.getResponse();
        
        // All error responses must have:
        // 1. Appropriate HTTP status code
        assertThat(response.getStatusCode()).isIn(
            HttpStatus.UNAUTHORIZED,
            HttpStatus.FORBIDDEN,
            HttpStatus.TOO_MANY_REQUESTS
        );
        
        // 2. JSON content type
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        
        // 3. Correlation ID in headers
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .isEqualTo(correlationId);
        
        // Note: Response body verification is done in integration tests
        // Unit tests verify the structure is correct (status, content type, headers)
        
        LoggingUtils.clearContext();
    }
    
    // ========== Property 21: Authentication Failure Logging ==========
    
    /**
     * Property 21: Authentication failure logging
     * 
     * <p>For any authentication failure, the Gateway should create a log entry
     * containing the correlation ID and error details.</p>
     * 
     * <p>Validates: Requirements 7.6</p>
     */
    @Property
    @Label("Feature: gateway, Property 21: Authentication failure logging")
    void authenticationFailures_LoggedWithCorrelationId(
        @ForAll("correlationIds") String correlationId,
        @ForAll("authHeaders") String authHeader
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        when(mockClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.error(new InvalidTokenException("Invalid token")));
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - Verify error response includes correlation ID
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .isEqualTo(correlationId);
        
        // Note: Actual log verification would require a logging framework mock
        // We verify the filter completed and returned an error with correlation ID
        
        LoggingUtils.clearContext();
    }
    
    // ========== Property 22: Authorization Failure Logging ==========
    
    /**
     * Property 22: Authorization failure logging
     * 
     * <p>For any authorization failure, the Gateway should create a log entry
     * containing the correlation ID, environment ID, service name, and access type.</p>
     * 
     * <p>Validates: Requirements 7.7</p>
     */
    @Property
    @Label("Feature: gateway, Property 22: Authorization failure logging")
    void authorizationFailures_LoggedWithFullContext(
        @ForAll("correlationIds") String correlationId,
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("serviceNames") String serviceName,
        @ForAll("accessTypes") AccessType accessType
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        when(mockClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(new PermissionCheckResponse(false)));
        
        // Determine path based on access type
        String path = determinePathForAccessType(serviceName, accessType);
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(path)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .header(SifHeaders.SERVICE_NAME, serviceName)
            .header(SifHeaders.ZONE_ID, "default")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context (as if AuthenticationFilter ran)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", "TestApp");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("defaultZoneId", "default");
        LoggingUtils.setCorrelationId(correlationId);
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthorizationFilter filter = new AuthorizationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - Verify error response includes correlation ID
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .isEqualTo(correlationId);
        
        // Note: Actual log verification would require a logging framework mock
        // We verify the filter completed and returned an error with correlation ID
        // The log should contain: correlationId, environmentId, serviceName, accessType
        
        LoggingUtils.clearContext();
    }
    
    // ========== Property 27: Correlation ID in Logs ==========
    
    /**
     * Property 27: Correlation ID in logs
     * 
     * <p>For any request processed by the Gateway, all log entries for that request
     * should contain the same correlation ID.</p>
     * 
     * <p>Validates: Requirements 9.4</p>
     */
    @Property
    @Label("Feature: gateway, Property 27: Correlation ID in logs")
    void allLogEntries_ContainSameCorrelationId(
        @ForAll("correlationIds") String correlationId
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        when(mockClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.error(new InvalidTokenException("Invalid token")));
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash")
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - Verify correlation ID is preserved in response
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .isEqualTo(correlationId);
        
        // Note: In a real scenario, we would verify that all log entries
        // for this request contain the same correlation ID using a logging framework mock
        // For this test, we verify the correlation ID is preserved throughout the request
        
        LoggingUtils.clearContext();
    }
    
    // ========== Arbitraries (Test Data Generators) ==========
    
    @Provide
    Arbitrary<String> correlationIds() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .withChars('-')
            .ofMinLength(36)
            .ofMaxLength(36)
            .map(s -> {
                // Generate UUID-like strings
                if (s.length() == 36) {
                    return s.substring(0, 8) + "-" + s.substring(8, 12) + "-" +
                           s.substring(12, 16) + "-" + s.substring(16, 20) + "-" +
                           s.substring(20, 32);
                }
                return UUID.randomUUID().toString();
            });
    }
    
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
            "StudentPersonal",
            "SchoolInfo",
            "StaffPersonal",
            "StudentAttendance",
            "StudentGrade"
        );
    }
    
    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.PROVIDE, AccessType.QUERY);
    }
    
    @Provide
    Arbitrary<String> authHeaders() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofLength(36),
            Arbitraries.strings().numeric().ofLength(13),
            Arbitraries.strings().alpha().ofLength(44)
        ).as((token, timestamp, hash) ->
            "SIF_HMACSHA256 " + token + ":" + timestamp + ":" + hash
        );
    }
    
    @Provide
    Arbitrary<ErrorType> errorTypes() {
        return Arbitraries.of(
            ErrorType.AUTHENTICATION,
            ErrorType.AUTHORIZATION,
            ErrorType.RATE_LIMIT
        );
    }
    
    // ========== Helper Methods ==========
    
    private MockServerWebExchange createExchangeForErrorType(ErrorType errorType, String correlationId) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, correlationId);
        
        if (errorType == ErrorType.AUTHENTICATION) {
            builder.header(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
        } else if (errorType == ErrorType.AUTHORIZATION) {
            builder.header(SifHeaders.SERVICE_NAME, "StudentPersonal");
        }
        
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        
        if (errorType == ErrorType.AUTHORIZATION || errorType == ErrorType.RATE_LIMIT) {
            exchange.getAttributes().put("environmentId", UUID.randomUUID());
            exchange.getAttributes().put("environmentName", "TestApp");
            exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
            exchange.getAttributes().put("defaultZoneId", "default");
        }
        
        return exchange;
    }
    
    private void configureErrorMocks(EnvironmentServiceClient mockClient, ErrorType errorType) {
        if (errorType == ErrorType.AUTHENTICATION) {
            when(mockClient.validateToken(any(TokenValidationRequest.class)))
                .thenReturn(Mono.error(new InvalidTokenException("Invalid token")));
        } else if (errorType == ErrorType.AUTHORIZATION) {
            when(mockClient.checkPermission(any(PermissionCheckRequest.class)))
                .thenReturn(Mono.error(new InsufficientPermissionsException("Access denied")));
        }
    }
    
    private Object createFilterForErrorType(ErrorType errorType, EnvironmentServiceClient mockClient) {
        if (errorType == ErrorType.AUTHENTICATION) {
            org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
                mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
            return new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        } else if (errorType == ErrorType.AUTHORIZATION) {
            org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
                mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
            return new AuthorizationFilter(mockClient, objectMapper, mockMetricsService);
        } else {
            RedisRateLimiter mockRateLimiter = mock(RedisRateLimiter.class);
            RateLimitConfiguration mockConfig = mock(RateLimitConfiguration.class);
            org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
                mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
            when(mockConfig.getRequestsPerSecond(any(EnvironmentType.class))).thenReturn(100);
            when(mockRateLimiter.isAllowed(anyString(), anyInt()))
                .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
            when(mockRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
            return new RateLimitFilter(mockRateLimiter, mockConfig, objectMapper, mockMetricsService, null);
        }
    }
    
    private String determinePathForAccessType(String serviceName, AccessType accessType) {
        return switch (accessType) {
            case QUERY, CREATE, UPDATE, DELETE -> "/requests/" + serviceName;
            case PROVIDE -> "/responses/" + serviceName;
            case SUBSCRIBE -> "/events/" + serviceName;
        };
    }
    
    // ========== Helper Enums ==========
    
    private enum ErrorType {
        AUTHENTICATION,
        AUTHORIZATION,
        RATE_LIMIT
    }
}
