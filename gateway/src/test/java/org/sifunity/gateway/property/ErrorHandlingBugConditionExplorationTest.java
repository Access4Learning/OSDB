// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.PermissionCheckRequest;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.sifunity.broker.common.util.LoggingUtils;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.filter.AuthenticationFilter;
import org.sifunity.gateway.filter.AuthorizationFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug Condition Exploration Tests for Error Handling with Correlation ID.
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
 *   <li>1.1: AuthorizationFilter encounters RuntimeException → calls onError() instead of onComplete()</li>
 *   <li>1.2: Authentication fails (401) → missing X-Correlation-Id header</li>
 *   <li>1.3: Environment service unavailable (503) → missing X-Correlation-Id header</li>
 *   <li>1.4: Environment service timeout → IllegalStateException "Timeout on blocking read"</li>
 * </ul>
 * 
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4</strong></p>
 */
@PropertyDefaults(tries = 50)
class ErrorHandlingBugConditionExplorationTest {
    
    private final ObjectMapper objectMapper = createObjectMapper();
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
    
    // ========== Property 1: Bug Condition - Error Handling with Correlation ID ==========
    
    /**
     * Property 1: Bug Condition - Error Handling with Correlation ID
     * 
     * <p>Tests that AuthorizationFilter properly handles generic RuntimeException by calling 
     * onComplete() with appropriate error response (not onError()).</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because filter calls onError() 
     * instead of onComplete().</p>
     * 
     * <p><strong>Validates: Requirements 1.1, 2.1</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - AuthorizationFilter handles RuntimeException with onComplete")
    void authorizationFilter_HandlesGenericRuntimeException_WithOnComplete(
        @ForAll("correlationIds") String correlationId,
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("serviceNames") String serviceName
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        // Simulate a generic RuntimeException (not authorization-specific)
        when(mockClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.error(new RuntimeException("Generic error")));
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/" + serviceName)
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
        
        // Act & Assert
        // The filter should call onComplete() with error response, not onError()
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()  // Should complete normally, not error
            .verify();
        
        // Verify error response was set
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode())
            .as("Should return HTTP 403 FORBIDDEN for generic errors in authorization context")
            .isEqualTo(HttpStatus.FORBIDDEN);
        
        // Verify correlation ID is included
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .as("Response should include X-Correlation-Id header")
            .isEqualTo(correlationId);
        
        LoggingUtils.clearContext();
    }
    
    /**
     * Property 1: Bug Condition - Authentication Failure Includes Correlation ID
     * 
     * <p>Tests that authentication failures (401) include X-Correlation-Id header in response.</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because 401 response is missing 
     * X-Correlation-Id header.</p>
     * 
     * <p><strong>Validates: Requirements 1.2, 2.2</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - Authentication failure includes correlation ID")
    void authenticationFailure_IncludesCorrelationIdHeader(
        @ForAll("correlationIds") String correlationId,
        @ForAll("authHeaders") String authHeader
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        // Simulate authentication failure
        when(mockClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.error(new org.sifunity.broker.common.exception.InvalidTokenException("Invalid token")));
        
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
            .expectComplete()
            .verify();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode())
            .as("Should return 401 UNAUTHORIZED")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        
        // BUG: This assertion will FAIL on unfixed code - correlation ID is missing
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .as("401 response MUST include X-Correlation-Id header")
            .isEqualTo(correlationId);
        
        LoggingUtils.clearContext();
    }
    
    /**
     * Property 1: Bug Condition - Service Unavailable Includes Correlation ID
     * 
     * <p>Tests that environment service unavailable (503) includes X-Correlation-Id header in response.</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because 503 response is missing 
     * X-Correlation-Id header.</p>
     * 
     * <p><strong>Validates: Requirements 1.3, 2.3</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - Service unavailable includes correlation ID")
    void serviceUnavailable_IncludesCorrelationIdHeader(
        @ForAll("correlationIds") String correlationId,
        @ForAll("authHeaders") String authHeader
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        // Simulate service unavailable
        when(mockClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.error(new ServiceUnavailableException("EnvironmentService", "Service unavailable")));
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode())
            .as("Should return 503 SERVICE_UNAVAILABLE")
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        
        // BUG: This assertion will FAIL on unfixed code - correlation ID is missing
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .as("503 response MUST include X-Correlation-Id header")
            .isEqualTo(correlationId);
        
        LoggingUtils.clearContext();
    }
    
    /**
     * Property 1: Bug Condition - Timeout Handled Gracefully
     * 
     * <p>Tests that environment service timeout is handled gracefully and returns appropriate 
     * error response with X-Correlation-Id (not IllegalStateException).</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because timeout throws 
     * IllegalStateException "Timeout on blocking read" instead of being handled gracefully.</p>
     * 
     * <p><strong>Validates: Requirements 1.4, 2.4</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - Timeout handled gracefully with correlation ID")
    void serviceTimeout_HandledGracefully_WithCorrelationId(
        @ForAll("correlationIds") String correlationId,
        @ForAll("authHeaders") String authHeader
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        // Simulate timeout - return the error immediately (not after 30 seconds)
        when(mockClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.error(new IllegalStateException("Timeout on blocking read")));
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act & Assert
        // BUG: On unfixed code, this will throw IllegalStateException instead of completing gracefully
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()  // Should complete, not error
            .verify(Duration.ofSeconds(5));
        
        // Verify error response was set
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode())
            .as("Should return error status code for timeout")
            .isIn(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT);
        
        // Verify correlation ID is included
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .as("Timeout response MUST include X-Correlation-Id header")
            .isEqualTo(correlationId);
        
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
    Arbitrary<String> authHeaders() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofLength(36),
            Arbitraries.strings().alpha().ofLength(44)
        ).as((token, hash) -> {
            String credentials = token + ":" + hash;
            String base64Credentials = java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "SIF_HMACSHA256 " + base64Credentials;
        });
    }
}
