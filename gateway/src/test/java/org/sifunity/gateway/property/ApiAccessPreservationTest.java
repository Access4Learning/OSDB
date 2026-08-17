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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Preservation Property Tests for API Access Behavior
 * 
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on unfixed code.</strong>
 * These tests establish the baseline behavior that must be preserved after the fix.
 * 
 * <p><strong>Purpose:</strong>
 * These property-based tests verify that public API access continues to work correctly
 * after fixing the internal API blocking issues. They test:
 * <ul>
 *   <li>Public API endpoints continue to be processed normally</li>
 *   <li>Authenticated requests to non-internal endpoints continue to allow access</li>
 *   <li>Unauthenticated requests to public endpoints continue to allow access</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior:</strong>
 * All tests in this class should PASS on the unfixed code, confirming that the
 * public API access behavior is correct and should be preserved.
 * 
 * <p><strong>Validates: Requirements 3.4, 3.5, 3.6</strong>
 * 
 * @see InternalApiBlockingBugConditionExplorationTest
 */
@PropertyDefaults(tries = 50)
class ApiAccessPreservationTest {
    
    private final ObjectMapper objectMapper = createObjectMapper();
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
    
    // ========== Property 2: Preservation - Public API Access ==========
    
    /**
     * Property 2.1: Preservation - Public API Endpoints Processed Normally
     * 
     * <p><strong>Validates: Requirement 3.4</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that requests to public API endpoints (non-internal paths)
     * are processed normally by the authentication filter. Public endpoints include:
     * <ul>
     *   <li>/requests/* (SIF object requests)</li>
     *   <li>/environments (environment registration)</li>
     *   <li>/actuator/health (health check)</li>
 * </ul>
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Public API endpoints are not blocked</li>
     *   <li>Authentication filter processes public endpoints normally</li>
     *   <li>Filter chain is called for public endpoints</li>
     * </ul>
     * 
     * @param publicEndpoint randomly generated public endpoint configuration
     */
    @Property
    @Label("Feature: gateway, Preservation: Public API Endpoints Processed Normally")
    void publicApiEndpointsProcessedNormally(
        @ForAll("publicEndpoints") PublicEndpointConfig publicEndpoint
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        AtomicBoolean chainWasCalled = new AtomicBoolean(false);
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                chainWasCalled.set(true);
                return Mono.empty();
            });
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Create request to public endpoint (exempt paths don't need auth)
        MockServerHttpRequest.BaseBuilder<?> requestBuilder;
        if (publicEndpoint.method == HttpMethod.POST) {
            requestBuilder = MockServerHttpRequest.post(publicEndpoint.path);
        } else {
            requestBuilder = MockServerHttpRequest.get(publicEndpoint.path);
        }
        
        MockServerHttpRequest request = requestBuilder
            .header(SifHeaders.X_CORRELATION_ID, UUID.randomUUID().toString())
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert: Public endpoints should be processed (chain called)
        assertThat(chainWasCalled.get())
            .as("Public endpoint " + publicEndpoint.path + " should be processed (chain called)")
            .isTrue();
        
        // Public endpoints should not be blocked with 403
        assertThat(exchange.getResponse().getStatusCode())
            .as("Public endpoint " + publicEndpoint.path + " should not be blocked with 403")
            .isNotEqualTo(HttpStatus.FORBIDDEN);
    }
    
    /**
     * Property 2.2: Preservation - Authenticated Requests to Non-Internal Endpoints Allowed
     * 
     * <p><strong>Validates: Requirement 3.5</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that authenticated requests to non-internal endpoints
     * are allowed and processed normally. The authentication filter validates the token
     * and sets exchange attributes for downstream filters.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Authenticated requests to non-internal endpoints are allowed</li>
     *   <li>Authentication filter validates tokens for non-internal endpoints</li>
     *   <li>Filter chain is called after successful authentication</li>
     *   <li>Exchange attributes are set correctly</li>
     * </ul>
     * 
     * @param nonInternalEndpoint randomly generated non-internal endpoint
     * @param environmentId randomly generated environment ID
     * @param environmentName randomly generated environment name
     * @param environmentType randomly generated environment type
     */
    @Property
    @Label("Feature: gateway, Preservation: Authenticated Requests to Non-Internal Endpoints Allowed")
    void authenticatedRequestsToNonInternalEndpointsAllowed(
        @ForAll("nonInternalEndpoints") String nonInternalEndpoint,
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("environmentNames") String environmentName,
        @ForAll("environmentTypes") EnvironmentType environmentType
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        AtomicBoolean chainWasCalled = new AtomicBoolean(false);
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                chainWasCalled.set(true);
                return Mono.empty();
            });
        
        // Mock successful authentication response
        TokenValidationResponse validationResponse = new TokenValidationResponse(
            environmentId,
            environmentName,
            environmentType,
            "default",
            null,
            null,
            null,
            null
        );
        when(mockClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.just(validationResponse));
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Create authenticated request to non-internal endpoint
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String hash = "abc123hashvalue";
        
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(nonInternalEndpoint)
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.X_CORRELATION_ID, UUID.randomUUID().toString())
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert: Authenticated requests to non-internal endpoints should be allowed
        assertThat(chainWasCalled.get())
            .as("Authenticated request to " + nonInternalEndpoint + " should be allowed (chain called)")
            .isTrue();
        
        // Should not be blocked with 403
        assertThat(exchange.getResponse().getStatusCode())
            .as("Authenticated request to " + nonInternalEndpoint + " should not be blocked with 403")
            .isNotEqualTo(HttpStatus.FORBIDDEN);
    }
    
    /**
     * Property 2.3: Preservation - Unauthenticated Requests to Public Endpoints Allowed
     * 
     * <p><strong>Validates: Requirement 3.6</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that unauthenticated requests to public endpoints
     * (exempt paths) are allowed and processed without requiring authentication.
     * Public endpoints include:
     * <ul>
     *   <li>POST /environments (environment registration)</li>
     *   <li>GET /actuator/health (health check)</li>
     * </ul>
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Unauthenticated requests to public endpoints are allowed</li>
     *   <li>Authentication filter bypasses authentication for public endpoints</li>
     *   <li>Filter chain is called for public endpoints</li>
     *   <li>No authentication attributes are set for public endpoints</li>
     * </ul>
     * 
     * @param publicEndpoint randomly generated public endpoint configuration
     */
    @Property
    @Label("Feature: gateway, Preservation: Unauthenticated Requests to Public Endpoints Allowed")
    void unauthenticatedRequestsToPublicEndpointsAllowed(
        @ForAll("publicEndpoints") PublicEndpointConfig publicEndpoint
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        AtomicBoolean chainWasCalled = new AtomicBoolean(false);
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                chainWasCalled.set(true);
                return Mono.empty();
            });
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Create unauthenticated request to public endpoint (no Authorization header)
        MockServerHttpRequest.BaseBuilder<?> requestBuilder;
        if (publicEndpoint.method == HttpMethod.POST) {
            requestBuilder = MockServerHttpRequest.post(publicEndpoint.path);
        } else {
            requestBuilder = MockServerHttpRequest.get(publicEndpoint.path);
        }
        
        MockServerHttpRequest request = requestBuilder
            .header(SifHeaders.X_CORRELATION_ID, UUID.randomUUID().toString())
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();
        
        // Assert: Unauthenticated requests to public endpoints should be allowed
        assertThat(chainWasCalled.get())
            .as("Unauthenticated request to public endpoint " + publicEndpoint.path + " should be allowed (chain called)")
            .isTrue();
        
        // Should not be blocked with 403 or 401
        assertThat(exchange.getResponse().getStatusCode())
            .as("Unauthenticated request to public endpoint " + publicEndpoint.path + " should not be blocked")
            .isNotIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
    }
    
    // ========== Arbitraries (Test Data Generators) ==========
    
    /**
     * Generates public endpoint configurations (exempt paths).
     * These are endpoints that don't require authentication.
     */
    @Provide
    Arbitrary<PublicEndpointConfig> publicEndpoints() {
        return Arbitraries.of(
            new PublicEndpointConfig("/environments", HttpMethod.POST),
            new PublicEndpointConfig("/actuator/health", HttpMethod.GET),
            new PublicEndpointConfig("/actuator/health/liveness", HttpMethod.GET),
            new PublicEndpointConfig("/actuator/health/readiness", HttpMethod.GET)
        );
    }
    
    /**
     * Generates non-internal endpoint paths.
     * These are regular API endpoints that are not under /internal/*.
     */
    @Provide
    Arbitrary<String> nonInternalEndpoints() {
        return Arbitraries.of(
            "/requests/StudentPersonal",
            "/requests/SchoolInfo",
            "/requests/StaffPersonal",
            "/requests/StudentAttendance",
            "/requests/StudentGrade",
            "/api/data",
            "/api/resources",
            "/public/info",
            "/services/query",
            "/data/fetch"
        );
    }
    
    /**
     * Generates random environment IDs (UUIDs).
     */
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    /**
     * Generates random environment names.
     */
    @Provide
    Arbitrary<String> environmentNames() {
        return Arbitraries.of(
            "TestApp",
            "MyApplication",
            "ProviderApp",
            "ConsumerApp",
            "BrokerApp",
            "IntegrationTestApp",
            "DevelopmentApp",
            "ProductionApp"
        );
    }
    
    /**
     * Generates random environment types.
     */
    @Provide
    Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(
            EnvironmentType.BROKERED,
            EnvironmentType.BROKERED,
            EnvironmentType.BROKERED
        );
    }
    
    // ========== Test Data Classes ==========
    
    /**
     * Configuration for public endpoint testing.
     */
    static class PublicEndpointConfig {
        final String path;
        final HttpMethod method;
        
        PublicEndpointConfig(String path, HttpMethod method) {
            this.path = path;
            this.method = method;
        }
    }
}
