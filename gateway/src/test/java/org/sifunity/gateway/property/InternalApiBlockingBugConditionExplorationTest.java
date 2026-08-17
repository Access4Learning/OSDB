// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.filter.AuthenticationFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug Condition Exploration Tests for Internal API Blocking.
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
 *   <li>1.5: /internal/validate-token without auth returns 401 instead of 403</li>
 *   <li>1.6: /internal/validate-token with valid auth makes request to backend instead of blocking with 403</li>
 *   <li>1.7: /internal/check-permission without auth returns 401 instead of 403</li>
 *   <li>1.8: /internal/check-permission/subpath without auth returns 401 instead of 403</li>
 *   <li>1.9: /internal/anything without auth returns 401 instead of 403</li>
 * </ul>
 * 
 * <p><strong>Validates: Requirements 1.5, 1.6, 1.7, 1.8, 1.9</strong></p>
 */
@PropertyDefaults(tries = 50)
class InternalApiBlockingBugConditionExplorationTest {
    
    private final ObjectMapper objectMapper = createObjectMapper();
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
    
    // ========== Property 1: Bug Condition - Internal API Blocking ==========
    
    /**
     * Property 1: Bug Condition - /internal/validate-token without auth returns 403
     * 
     * <p>Tests that requests to /internal/validate-token without authentication return 403 FORBIDDEN
     * immediately, not 401 UNAUTHORIZED from the authentication filter.</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because the request reaches the
     * authentication filter and returns 401 instead of being blocked with 403.</p>
     * 
     * <p><strong>Validates: Requirements 1.5, 2.5</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - /internal/validate-token without auth blocked with 403")
    void internalValidateToken_WithoutAuth_BlockedWith403(
        @ForAll("correlationIds") String correlationId
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        // Request to internal API without authentication
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/internal/validate-token")
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        
        // BUG: This assertion will FAIL on unfixed code - returns 401 instead of 403
        assertThat(response.getStatusCode())
            .as("/internal/validate-token without auth MUST return 403 FORBIDDEN (not 401)")
            .isEqualTo(HttpStatus.FORBIDDEN);
        
        // Verify the backend was NOT called (blocked before authentication)
        verify(mockClient, never()).validateToken(any(TokenValidationRequest.class));
    }
    
    /**
     * Property 1: Bug Condition - /internal/validate-token with auth blocked with 403
     * 
     * <p>Tests that requests to /internal/validate-token with valid authentication are still blocked
     * with 403 FORBIDDEN immediately, without making any requests to the backend service.</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because the request is processed by
     * authentication filter and makes a request to the backend instead of being blocked.</p>
     * 
     * <p><strong>Validates: Requirements 1.6, 2.6</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - /internal/validate-token with auth blocked with 403")
    void internalValidateToken_WithAuth_BlockedWith403_NoBackendCall(
        @ForAll("correlationIds") String correlationId,
        @ForAll("authHeaders") String authHeader
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        // Mock successful authentication response
        UUID environmentId = UUID.randomUUID();
        TokenValidationResponse validationResponse = new TokenValidationResponse(
            environmentId,
            "TestApp",
            EnvironmentType.BROKERED,
            "default",
            null,
            null,
            null,
            null
        );
        when(mockClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.just(validationResponse));
        
        // Request to internal API WITH authentication
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/internal/validate-token")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        
        // BUG: This assertion will FAIL on unfixed code - request is processed instead of blocked
        assertThat(response.getStatusCode())
            .as("/internal/validate-token with auth MUST return 403 FORBIDDEN immediately")
            .isEqualTo(HttpStatus.FORBIDDEN);
        
        // BUG: This assertion will FAIL on unfixed code - backend is called instead of blocked
        // Backend MUST NOT be called for internal API requests
        verify(mockClient, never()).validateToken(any(TokenValidationRequest.class));
    }
    
    /**
     * Property 1: Bug Condition - /internal/check-permission without auth blocked with 403
     * 
     * <p>Tests that requests to /internal/check-permission without authentication return 403 FORBIDDEN
     * immediately, not 401 UNAUTHORIZED from the authentication filter.</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because the request reaches the
     * authentication filter and returns 401 instead of being blocked with 403.</p>
     * 
     * <p><strong>Validates: Requirements 1.7, 2.7</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - /internal/check-permission without auth blocked with 403")
    void internalCheckPermission_WithoutAuth_BlockedWith403(
        @ForAll("correlationIds") String correlationId
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        // Request to internal API without authentication
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/internal/check-permission")
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        
        // BUG: This assertion will FAIL on unfixed code - returns 401 instead of 403
        assertThat(response.getStatusCode())
            .as("/internal/check-permission without auth MUST return 403 FORBIDDEN (not 401)")
            .isEqualTo(HttpStatus.FORBIDDEN);
        
        // Verify the backend was NOT called (blocked before authentication)
        verify(mockClient, never()).validateToken(any(TokenValidationRequest.class));
    }
    
    /**
     * Property 1: Bug Condition - /internal/check-permission/subpath without auth blocked with 403
     * 
     * <p>Tests that requests to internal API subpaths without authentication return 403 FORBIDDEN
     * immediately, not 401 UNAUTHORIZED from the authentication filter.</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because the request reaches the
     * authentication filter and returns 401 instead of being blocked with 403.</p>
     * 
     * <p><strong>Validates: Requirements 1.8, 2.8</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - /internal/check-permission/subpath without auth blocked with 403")
    void internalCheckPermissionSubpath_WithoutAuth_BlockedWith403(
        @ForAll("correlationIds") String correlationId,
        @ForAll("internalSubpaths") String subpath
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        // Request to internal API subpath without authentication
        String path = "/internal/check-permission" + subpath;
        MockServerHttpRequest request = MockServerHttpRequest
            .post(path)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        
        // BUG: This assertion will FAIL on unfixed code - returns 401 instead of 403
        assertThat(response.getStatusCode())
            .as("Internal API subpath " + path + " without auth MUST return 403 FORBIDDEN (not 401)")
            .isEqualTo(HttpStatus.FORBIDDEN);
        
        // Verify the backend was NOT called (blocked before authentication)
        verify(mockClient, never()).validateToken(any(TokenValidationRequest.class));
    }
    
    /**
     * Property 1: Bug Condition - /internal/anything without auth blocked with 403
     * 
     * <p>Tests that requests to any path starting with /internal without authentication return 403 FORBIDDEN
     * immediately, not 401 UNAUTHORIZED from the authentication filter.</p>
     * 
     * <p><strong>EXPECTED ON UNFIXED CODE</strong>: Test FAILS because the request reaches the
     * authentication filter and returns 401 instead of being blocked with 403.</p>
     * 
     * <p><strong>Validates: Requirements 1.9, 2.9</strong></p>
     */
    @Property
    @Label("Feature: gateway, Property 1: Bug Condition - /internal/anything without auth blocked with 403")
    void internalAnything_WithoutAuth_BlockedWith403(
        @ForAll("correlationIds") String correlationId,
        @ForAll("internalPaths") String internalPath
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        // Request to any internal API path without authentication
        MockServerHttpRequest request = MockServerHttpRequest
            .get(internalPath)
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        
        // BUG: This assertion will FAIL on unfixed code - returns 401 instead of 403
        assertThat(response.getStatusCode())
            .as("Internal API path " + internalPath + " without auth MUST return 403 FORBIDDEN (not 401)")
            .isEqualTo(HttpStatus.FORBIDDEN);
        
        // Verify the backend was NOT called (blocked before authentication)
        verify(mockClient, never()).validateToken(any(TokenValidationRequest.class));
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
    Arbitrary<String> internalSubpaths() {
        return Arbitraries.of(
            "/subpath",
            "/nested/path",
            "/deep/nested/path",
            "/123",
            "/resource/456"
        );
    }
    
    @Provide
    Arbitrary<String> internalPaths() {
        return Arbitraries.of(
            "/internal",
            "/internal/",
            "/internal/validate-token",
            "/internal/check-permission",
            "/internal/some-endpoint",
            "/internal/another/nested/endpoint",
            "/internal/update",
            "/internal/delete",
            "/internal/anything",
            "/internal/resource/123"
        );
    }
}
