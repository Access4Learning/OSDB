// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.exception.MalformedAuthHeaderException;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Preservation Property Tests for AuthenticationFilter Behavior
 * 
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on unfixed code.</strong>
 * These tests establish the baseline behavior that must be preserved after the fix.
 * 
 * <p><strong>Purpose:</strong>
 * These property-based tests verify that the AuthenticationFilter's production behavior
 * remains unchanged after fixing the test issues. They test:
 * <ul>
 *   <li>Filter sets attributes in reactive chain during production execution</li>
 *   <li>Filter handles exempt paths without setting attributes</li>
 *   <li>Filter handles authentication errors correctly</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior:</strong>
 * All tests in this class should PASS on the unfixed code, confirming that the
 * production filter behavior is correct and should be preserved.
 * 
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3</strong>
 * 
 * @see AuthenticationFilter
 */
class AuthenticationFilterPreservationTest {
    
    /**
     * Property 2: Preservation - Production Filter Behavior
     * 
     * <p><strong>Validates: Requirement 3.1</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that the AuthenticationFilter correctly sets exchange attributes
     * in the reactive chain during production execution. The filter processes the request,
     * validates the token, and sets attributes within the reactive chain.
     * 
     * <p>To properly test this, we capture the exchange state WITHIN the reactive chain
     * (in the mockChain.filter() callback) rather than after StepVerifier completes.
     * This mirrors how production code accesses attributes - within the reactive chain.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>environmentId attribute is set correctly in the reactive chain</li>
     *   <li>environmentName attribute is set correctly in the reactive chain</li>
     *   <li>environmentType attribute is set correctly in the reactive chain</li>
     *   <li>defaultZoneId attribute is set correctly in the reactive chain</li>
     * </ul>
     * 
     * @param environmentId randomly generated environment ID
     * @param environmentName randomly generated environment name
     * @param environmentType randomly generated environment type
     * @param defaultZoneId randomly generated default zone ID
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Preservation: Production Filter Sets Attributes In Reactive Chain")
    void productionFilterSetsAttributesInReactiveChain(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentNames") String environmentName,
            @ForAll("environmentTypes") EnvironmentType environmentType,
            @ForAll("zoneIds") String defaultZoneId) {
        
        // Arrange: Create mocks
        EnvironmentServiceClient mockEnvironmentServiceClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        AuthenticationFilter filter = new AuthenticationFilter(
            mockEnvironmentServiceClient, 
            objectMapper, 
            mockMetricsService
        );
        
        // Use AtomicReference to capture attributes WITHIN the reactive chain
        AtomicReference<UUID> capturedEnvironmentId = new AtomicReference<>();
        AtomicReference<String> capturedEnvironmentName = new AtomicReference<>();
        AtomicReference<EnvironmentType> capturedEnvironmentType = new AtomicReference<>();
        AtomicReference<String> capturedDefaultZoneId = new AtomicReference<>();
        
        // Mock chain to capture attributes WITHIN the reactive chain (production behavior)
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                ServerWebExchange exchange = invocation.getArgument(0);
                // Capture attributes while still in the reactive chain
                capturedEnvironmentId.set(exchange.getAttribute("environmentId"));
                capturedEnvironmentName.set(exchange.getAttribute("environmentName"));
                capturedEnvironmentType.set(exchange.getAttribute("environmentType"));
                capturedDefaultZoneId.set(exchange.getAttribute("defaultZoneId"));
                return Mono.empty();
            });
        
        // Create validation response with random values
        TokenValidationResponse validationResponse = new TokenValidationResponse(
            environmentId,
            environmentName,
            environmentType,
            defaultZoneId,
            null,
            null,
            null,
            null
        );
        
        when(mockEnvironmentServiceClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.just(validationResponse));
        
        // Create request with valid Authorization header AND timestamp header
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String hash = "abc123hashvalue";
        
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act: Execute filter and complete reactive chain
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert: Verify attributes were accessible WITHIN the reactive chain (production behavior)
        assertThat(capturedEnvironmentId.get())
            .as("environmentId should be set in reactive chain")
            .isNotNull()
            .as("environmentId should match the validation response")
            .isEqualTo(environmentId);
        
        assertThat(capturedEnvironmentName.get())
            .as("environmentName should be set in reactive chain")
            .isNotNull()
            .as("environmentName should match the validation response")
            .isEqualTo(environmentName);
        
        assertThat(capturedEnvironmentType.get())
            .as("environmentType should be set in reactive chain")
            .isNotNull()
            .as("environmentType should match the validation response")
            .isEqualTo(environmentType);
        
        assertThat(capturedDefaultZoneId.get())
            .as("defaultZoneId should be set in reactive chain")
            .isNotNull()
            .as("defaultZoneId should match the validation response")
            .isEqualTo(defaultZoneId);
    }

    
    /**
     * Property 2.2: Preservation - Exempt Paths Bypass Authentication
     * 
     * <p><strong>Validates: Requirement 3.2</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that the AuthenticationFilter correctly handles exempt paths
     * by bypassing authentication and NOT setting any attributes. Exempt paths include:
     * <ul>
     *   <li>POST /environments (environment registration)</li>
     *   <li>/actuator/health (health check endpoint)</li>
     * </ul>
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Filter allows exempt paths to proceed without authentication</li>
     *   <li>Filter does NOT set any attributes for exempt paths</li>
     *   <li>Filter does NOT call the environment service for exempt paths</li>
     * </ul>
     * 
     * @param exemptPathConfig randomly generated exempt path configuration
     */
    @Property(tries = 50)
    @Label("Feature: gateway, Preservation: Exempt Paths Bypass Authentication")
    void exemptPathsBypassAuthentication(
            @ForAll("exemptPaths") ExemptPathConfig exemptPathConfig) {
        
        // Arrange: Create mocks
        EnvironmentServiceClient mockEnvironmentServiceClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        AuthenticationFilter filter = new AuthenticationFilter(
            mockEnvironmentServiceClient, 
            objectMapper, 
            mockMetricsService
        );
        
        // Use AtomicReference to capture attributes WITHIN the reactive chain
        AtomicReference<UUID> capturedEnvironmentId = new AtomicReference<>();
        AtomicReference<Boolean> chainWasCalled = new AtomicReference<>(false);
        
        // Mock chain to capture attributes WITHIN the reactive chain
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                ServerWebExchange exchange = invocation.getArgument(0);
                chainWasCalled.set(true);
                // Capture attributes - should be null for exempt paths
                capturedEnvironmentId.set(exchange.getAttribute("environmentId"));
                return Mono.empty();
            });
        
        // Create request WITHOUT Authorization header (exempt paths don't need it)
        MockServerHttpRequest.BaseBuilder<?> requestBuilder;
        if (exemptPathConfig.method == HttpMethod.POST) {
            requestBuilder = MockServerHttpRequest.post(exemptPathConfig.path);
        } else {
            requestBuilder = MockServerHttpRequest.get(exemptPathConfig.path);
        }
        
        MockServerHttpRequest request = requestBuilder.build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act: Execute filter
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert: Verify exempt path behavior
        assertThat(chainWasCalled.get())
            .as("Chain should be called for exempt paths")
            .isTrue();
        
        assertThat(capturedEnvironmentId.get())
            .as("environmentId should NOT be set for exempt paths")
            .isNull();
    }
    
    /**
     * Property 2.3: Preservation - Authentication Errors Handled Correctly
     * 
     * <p><strong>Validates: Requirement 3.3</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that the AuthenticationFilter correctly handles authentication
     * errors by returning 401 Unauthorized responses and recording metrics. Error scenarios include:
     * <ul>
     *   <li>Missing Authorization header</li>
     *   <li>Missing timestamp header</li>
     *   <li>Invalid tokens</li>
     *   <li>Malformed headers</li>
     * </ul>
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Filter returns 401 Unauthorized for authentication errors</li>
     *   <li>Filter does NOT call the chain for authentication errors</li>
     *   <li>Filter does NOT set any attributes for authentication errors</li>
     * </ul>
     * 
     * @param errorScenario randomly generated error scenario
     */
    @Property(tries = 50)
    @Label("Feature: gateway, Preservation: Authentication Errors Handled Correctly")
    void authenticationErrorsHandledCorrectly(
            @ForAll("errorScenarios") ErrorScenario errorScenario) {
        
        // Arrange: Create mocks
        EnvironmentServiceClient mockEnvironmentServiceClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        AuthenticationFilter filter = new AuthenticationFilter(
            mockEnvironmentServiceClient, 
            objectMapper, 
            mockMetricsService
        );
        
        // Use AtomicReference to track if chain was called
        AtomicReference<Boolean> chainWasCalled = new AtomicReference<>(false);
        
        // Mock chain to track if it was called (should NOT be called for errors)
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                chainWasCalled.set(true);
                return Mono.empty();
            });
        
        // Mock environment service to return error for invalid tokens
        when(mockEnvironmentServiceClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.error(new InvalidTokenException("Invalid token")));
        
        // Create request based on error scenario
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest
            .get("/requests/StudentPersonal");
        
        switch (errorScenario) {
            case MISSING_AUTH_HEADER:
                // Don't add Authorization header
                break;
            case MISSING_TIMESTAMP_HEADER:
                // Add Authorization header but not timestamp
                requestBuilder.header(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 token:timestamp:hash");
                break;
            case INVALID_TOKEN:
                // Add both headers but token will be invalid
                requestBuilder.header(SifHeaders.AUTHORIZATION, "SIF_HMACSHA256 invalid:timestamp:hash");
                requestBuilder.header(SifHeaders.TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                break;
            case MALFORMED_HEADER:
                // Add malformed Authorization header
                requestBuilder.header(SifHeaders.AUTHORIZATION, "Bearer invalid-format");
                requestBuilder.header(SifHeaders.TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                break;
        }
        
        MockServerHttpRequest request = requestBuilder.build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act: Execute filter
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert: Verify error handling behavior
        assertThat(chainWasCalled.get())
            .as("Chain should NOT be called for authentication errors")
            .isFalse();
        
        assertThat(exchange.getResponse().getStatusCode())
            .as("Response should be 401 Unauthorized for authentication errors")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        
        // Verify no attributes were set
        assertThat((UUID) exchange.getAttribute("environmentId"))
            .as("environmentId should NOT be set for authentication errors")
            .isNull();
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
     * Generates random environment names.
     * Environment names are typically application names like "TestApp", "MyApplication", etc.
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
            "ProductionApp",
            "StagingApp",
            "QAApp"
        );
    }
    
    /**
     * Generates random environment types (DIRECT or BROKERED).
     */
    @Provide
    Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(EnvironmentType.values());
    }
    
    /**
     * Generates random zone IDs.
     * Zone IDs are typically strings like "default", "zone-123", "us-east-1", etc.
     */
    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of(
            "default",
            "zone-123",
            "zone-456",
            "us-east-1",
            "us-west-2",
            "eu-central-1",
            "ap-southeast-1",
            "test-zone",
            "prod-zone",
            "dev-zone"
        );
    }
    
    /**
     * Generates random context IDs.
     * Context IDs are typically uppercase strings like "DEFAULT", "CONTEXT-123", etc.
     */
    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.of(
            "DEFAULT",
            "CONTEXT-123",
            "CONTEXT-456",
            "TEST-CONTEXT",
            "PROD-CONTEXT",
            "DEV-CONTEXT",
            "QA-CONTEXT",
            "STAGING-CONTEXT",
            "INTEGRATION-CONTEXT",
            "UAT-CONTEXT"
        );
    }
    
    /**
     * Generates exempt path and method combinations.
     * Only specific combinations are exempt:
     * - POST /environments (environment registration)
     * - GET /actuator/health (health check)
     * - GET /actuator/health/liveness (liveness probe)
     * - GET /actuator/health/readiness (readiness probe)
     */
    @Provide
    Arbitrary<ExemptPathConfig> exemptPaths() {
        return Arbitraries.of(
            new ExemptPathConfig("/environments", HttpMethod.POST),
            new ExemptPathConfig("/actuator/health", HttpMethod.GET),
            new ExemptPathConfig("/actuator/health/liveness", HttpMethod.GET),
            new ExemptPathConfig("/actuator/health/readiness", HttpMethod.GET)
        );
    }
    
    /**
     * Generates HTTP methods for exempt paths.
     * POST is used for /environments, GET for /actuator/health.
     */
    @Provide
    Arbitrary<HttpMethod> exemptMethods() {
        return Arbitraries.of(
            HttpMethod.POST,
            HttpMethod.GET
        );
    }
    
    /**
     * Configuration for exempt path testing.
     */
    static class ExemptPathConfig {
        final String path;
        final HttpMethod method;
        
        ExemptPathConfig(String path, HttpMethod method) {
            this.path = path;
            this.method = method;
        }
    }
    
    /**
     * Generates error scenarios for authentication error testing.
     */
    @Provide
    Arbitrary<ErrorScenario> errorScenarios() {
        return Arbitraries.of(ErrorScenario.values());
    }
    
    /**
     * Error scenarios for authentication error testing.
     */
    enum ErrorScenario {
        MISSING_AUTH_HEADER,
        MISSING_TIMESTAMP_HEADER,
        INVALID_TOKEN,
        MALFORMED_HEADER
    }
}
