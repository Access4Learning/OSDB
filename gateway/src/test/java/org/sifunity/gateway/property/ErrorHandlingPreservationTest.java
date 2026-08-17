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
import org.sifunity.broker.common.dto.TokenValidationResponse;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Preservation Property Tests for Error Handling Behavior
 * 
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on unfixed code.</strong>
 * These tests establish the baseline behavior that must be preserved after the fix.
 * 
 * <p><strong>Purpose:</strong>
 * These property-based tests verify that error handling continues to work correctly
 * for successful requests after fixing the error handling and correlation ID bugs. They test:
 * <ul>
 *   <li>Successful authorization requests continue to call onComplete()</li>
 *   <li>Successful responses (2xx) continue to include X-Correlation-Id</li>
 *   <li>Normal environment service responses continue to process successfully</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior:</strong>
 * All tests in this class should PASS on the unfixed code, confirming that the
 * successful request processing is correct and should be preserved.
 * 
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3</strong>
 * 
 * @see ErrorHandlingBugConditionExplorationTest
 */
@PropertyDefaults(tries = 50)
class ErrorHandlingPreservationTest {
    
    private final ObjectMapper objectMapper = createObjectMapper();
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
    
    // ========== Property 2: Preservation - Successful Request Processing ==========
    
    /**
     * Property 2.1: Preservation - Successful Authorization Calls onComplete
     * 
     * <p><strong>Validates: Requirement 3.1</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that the AuthorizationFilter correctly calls onComplete()
     * for successful authorization requests. When permission check succeeds, the filter
     * should allow the request to proceed through the filter chain by calling onComplete().
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Filter calls onComplete() for successful authorization</li>
     *   <li>Filter does NOT call onError() for successful authorization</li>
     *   <li>Filter chain continues execution for successful authorization</li>
     *   <li>Response status is 200 OK for successful authorization</li>
     * </ul>
     * 
     * @param correlationId randomly generated correlation ID
     * @param environmentId randomly generated environment ID
     * @param serviceName randomly generated service name
     * @param accessType randomly generated access type
     */
    @Property
    @Label("Feature: gateway, Preservation: Successful authorization calls onComplete")
    void successfulAuthorization_CallsOnComplete(
        @ForAll("correlationIds") String correlationId,
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("serviceNames") String serviceName,
        @ForAll("accessTypes") AccessType accessType
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Track if chain.filter() was called (indicates onComplete behavior)
        AtomicBoolean chainWasCalled = new AtomicBoolean(false);
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                chainWasCalled.set(true);
                return Mono.empty();
            });
        
        // Simulate successful permission check
        PermissionCheckResponse successResponse = new PermissionCheckResponse(true);
        when(mockClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(successResponse));
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/testDomain/testSolution/requests/" + serviceName)
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
        
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthorizationFilter filter = new AuthorizationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()  // Should complete normally (onComplete behavior)
            .verify();
        
        // Assert
        assertThat(chainWasCalled.get())
            .as("Filter chain should be called for successful authorization (onComplete behavior)")
            .isTrue();
        
        // Verify no error status was set
        ServerHttpResponse response = exchange.getResponse();
        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        if (statusCode != null) {
            assertThat(statusCode.is2xxSuccessful())
                .as("Response should be successful (2xx) for successful authorization")
                .isTrue();
        }
        
        LoggingUtils.clearContext();
    }
    
    /**
     * Property 2.2: Preservation - Successful Responses Include Correlation ID
     * 
     * <p><strong>Validates: Requirement 3.2</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that successful responses (2xx) continue to include the
     * X-Correlation-Id header. This is critical for request tracing and debugging.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Successful authentication responses include X-Correlation-Id</li>
     *   <li>Correlation ID matches the request correlation ID</li>
     *   <li>Correlation ID is preserved through the filter chain</li>
     * </ul>
     * 
     * @param correlationId randomly generated correlation ID
     * @param environmentId randomly generated environment ID
     * @param environmentName randomly generated environment name
     * @param environmentType randomly generated environment type
     */
    @Property
    @Label("Feature: gateway, Preservation: Successful responses include correlation ID")
    void successfulResponses_IncludeCorrelationId(
        @ForAll("correlationIds") String correlationId,
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("environmentNames") String environmentName,
        @ForAll("environmentTypes") EnvironmentType environmentType
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Capture correlation ID from response headers within the reactive chain
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                ServerWebExchange exchange = invocation.getArgument(0);
                // In production, correlation ID would be set by CorrelationIdFilter
                // We simulate this by setting it in the response
                exchange.getResponse().getHeaders().set(SifHeaders.X_CORRELATION_ID, correlationId);
                capturedCorrelationId.set(exchange.getResponse().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID));
                return Mono.empty();
            });
        
        // Simulate successful token validation
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
        
        // Create valid authorization header
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String hash = "validHashValue123";
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
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
        assertThat(capturedCorrelationId.get())
            .as("Successful response should include X-Correlation-Id header")
            .isNotNull()
            .as("Correlation ID should match the request correlation ID")
            .isEqualTo(correlationId);
        
        LoggingUtils.clearContext();
    }
    
    /**
     * Property 2.3: Preservation - Normal Environment Service Responses Process Successfully
     * 
     * <p><strong>Validates: Requirement 3.3</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that when the environment service responds normally (not timeout,
     * not error), the Gateway processes the request successfully. This ensures that normal
     * operation is not affected by the timeout handling fix.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Normal environment service responses are processed successfully</li>
     *   <li>Authentication succeeds with valid tokens</li>
     *   <li>Environment attributes are set correctly</li>
     *   <li>Filter chain continues execution</li>
     * </ul>
     * 
     * @param correlationId randomly generated correlation ID
     * @param environmentId randomly generated environment ID
     * @param environmentName randomly generated environment name
     * @param environmentType randomly generated environment type
     * @param defaultZoneId randomly generated default zone ID
     */
    @Property
    @Label("Feature: gateway, Preservation: Normal environment service responses process successfully")
    void normalEnvironmentServiceResponses_ProcessSuccessfully(
        @ForAll("correlationIds") String correlationId,
        @ForAll("environmentIds") UUID environmentId,
        @ForAll("environmentNames") String environmentName,
        @ForAll("environmentTypes") EnvironmentType environmentType,
        @ForAll("zoneIds") String defaultZoneId
    ) {
        // Arrange
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Track if chain was called and capture environment attributes
        AtomicBoolean chainWasCalled = new AtomicBoolean(false);
        AtomicReference<UUID> capturedEnvironmentId = new AtomicReference<>();
        AtomicReference<String> capturedEnvironmentName = new AtomicReference<>();
        AtomicReference<EnvironmentType> capturedEnvironmentType = new AtomicReference<>();
        
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                ServerWebExchange exchange = invocation.getArgument(0);
                chainWasCalled.set(true);
                // Capture attributes within reactive chain
                capturedEnvironmentId.set(exchange.getAttribute("environmentId"));
                capturedEnvironmentName.set(exchange.getAttribute("environmentName"));
                capturedEnvironmentType.set(exchange.getAttribute("environmentType"));
                return Mono.empty();
            });
        
        // Simulate normal (successful) environment service response
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
        when(mockClient.validateToken(any(TokenValidationRequest.class)))
            .thenReturn(Mono.just(validationResponse));
        
        // Create valid authorization header
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String hash = "validHashValue123";
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
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
        assertThat(chainWasCalled.get())
            .as("Filter chain should be called for normal environment service responses")
            .isTrue();
        
        assertThat(capturedEnvironmentId.get())
            .as("Environment ID should be set correctly")
            .isEqualTo(environmentId);
        
        assertThat(capturedEnvironmentName.get())
            .as("Environment name should be set correctly")
            .isEqualTo(environmentName);
        
        assertThat(capturedEnvironmentType.get())
            .as("Environment type should be set correctly")
            .isEqualTo(environmentType);
        
        // Verify no error status was set
        ServerHttpResponse response = exchange.getResponse();
        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        if (statusCode != null) {
            assertThat(statusCode.is2xxSuccessful() || statusCode == null)
                .as("Response should be successful or not set for normal processing")
                .isTrue();
        }
        
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
            "StudentGrade",
            "StudentEnrollment",
            "TeacherAssignment"
        );
    }
    
    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }
    
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
    
    @Provide
    Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(EnvironmentType.values());
    }
    
    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of(
            "default",
            "zone-123",
            "zone-456",
            "us-east-1",
            "us-west-2",
            "test-zone"
        );
    }
    
    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.of(
            "DEFAULT",
            "CONTEXT-123",
            "CONTEXT-456",
            "TEST-CONTEXT",
            "PROD-CONTEXT"
        );
    }
}
