// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.gateway.filter.CorrelationIdFilter;
import org.sifunity.gateway.filter.EnvironmentContextFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for context injection properties in the API Gateway.
 * 
 * <p>These tests verify universal correctness properties across randomized inputs
 * to ensure the Gateway correctly handles correlation ID generation and preservation,
 * as well as environment context header injection in all scenarios.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Property 12: Environment ID header injection</li>
 *   <li>Property 13: Environment name header injection</li>
 *   <li>Property 14: Correlation ID generation</li>
 *   <li>Property 15: Correlation ID preservation</li>
 *   <li>Property 16: Header injection completeness</li>
 * </ul>
 * 
 * <p>Each property test runs a minimum of 100 iterations with randomized test data.
 * 
 * @see CorrelationIdFilter
 * @see EnvironmentContextFilter
 */
class ContextInjectionPropertiesTest {
    
    /**
     * Property 12: Environment ID header injection
     * 
     * **Validates: Requirements 4.1**
     * 
     * <p>For any authenticated and authorized request, the Gateway should inject
     * the X-Environment-Id header with the environment UUID.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Authenticated requests with environment context get X-Environment-Id injected</li>
     *   <li>The injected environment ID matches the authenticated environment UUID</li>
     *   <li>The environment ID is properly formatted as a UUID string</li>
     *   <li>This behavior is consistent across all request paths and methods</li>
     * </ul>
     * 
     * @param requestPath randomly generated request path
     * @param httpMethod randomly generated HTTP method
     * @param environmentId randomly generated environment UUID
     * @param environmentName randomly generated environment name
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 12: Environment ID header injection")
    void environmentIdHeaderInjection(
            @ForAll("requestPaths") String requestPath,
            @ForAll("httpMethods") HttpMethod httpMethod,
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentNames") String environmentName) {
        
        // Arrange: Create filter and mock chain
        EnvironmentContextFilter filter = new EnvironmentContextFilter("test-internal-token");
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Create request (authenticated request scenario)
        MockServerHttpRequest request = MockServerHttpRequest
                .method(httpMethod, requestPath)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Simulate authenticated request by setting environment context in exchange attributes
        // (normally set by AuthenticationFilter)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Mock chain to capture the mutated exchange
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange mutatedExchange = invocation.getArgument(0);
                    ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                    
                    // Verify X-Environment-Id header was injected
                    String injectedEnvironmentId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID);
                    
                    // Property: X-Environment-Id must be present
                    assertThat(injectedEnvironmentId)
                            .as("X-Environment-Id header should be injected for authenticated request")
                            .isNotNull()
                            .isNotBlank();
                    
                    // Property: X-Environment-Id must match the authenticated environment UUID
                    assertThat(injectedEnvironmentId)
                            .as("X-Environment-Id should match the authenticated environment UUID")
                            .isEqualTo(environmentId.toString());
                    
                    // Property: X-Environment-Id must be a valid UUID format
                    assertThat(injectedEnvironmentId)
                            .as("X-Environment-Id should be a valid UUID format")
                            .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
                    
                    // Verify it can be parsed as a UUID without exception
                    try {
                        UUID parsedUuid = UUID.fromString(injectedEnvironmentId);
                        assertThat(parsedUuid)
                                .as("Parsed UUID should match original environment ID")
                                .isEqualTo(environmentId);
                    } catch (IllegalArgumentException e) {
                        throw new AssertionError("Injected X-Environment-Id is not a valid UUID: " + injectedEnvironmentId, e);
                    }
                    
                    return Mono.empty();
                });
        
        // Act & Assert: Execute filter and verify
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }
    
    /**
     * Property 12 (variant): Environment ID header injection skipped for exempt paths
     * 
     * **Validates: Requirements 4.1**
     * 
     * <p>For any request without environment context (exempt paths like POST /environments
     * or /actuator/health), the Gateway should skip environment header injection.
     * 
     * @param requestPath randomly generated request path
     * @param httpMethod randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 12: Environment ID header injection")
    void environmentIdHeaderInjection_SkipForExemptPaths(
            @ForAll("requestPaths") String requestPath,
            @ForAll("httpMethods") HttpMethod httpMethod) {
        
        // Arrange: Create filter and mock chain
        EnvironmentContextFilter filter = new EnvironmentContextFilter("test-internal-token");
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Create request WITHOUT environment context (exempt path scenario)
        MockServerHttpRequest request = MockServerHttpRequest
                .method(httpMethod, requestPath)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Do NOT set environment context attributes (simulating exempt path)
        // exchange.getAttributes() remains empty
        
        // Mock chain to capture the mutated exchange
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange mutatedExchange = invocation.getArgument(0);
                    ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                    
                    // Verify X-Environment-Id header was NOT injected
                    String injectedEnvironmentId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID);
                    
                    // Property: X-Environment-Id should be absent for exempt paths
                    assertThat(injectedEnvironmentId)
                            .as("X-Environment-Id header should not be injected for exempt paths")
                            .isNull();
                    
                    return Mono.empty();
                });
        
        // Act & Assert: Execute filter and verify
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }
    
    /**
     * Property 13: Environment name header injection
     * 
     * **Validates: Requirements 4.2**
     * 
     * <p>For any authenticated and authorized request, the Gateway should inject
     * the X-Environment-Name header with the application name.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Authenticated requests with environment context get X-Environment-Name injected</li>
     *   <li>The injected environment name matches the authenticated application name</li>
     *   <li>The environment name is properly formatted as a non-blank string</li>
     *   <li>This behavior is consistent across all request paths and methods</li>
     * </ul>
     * 
     * @param requestPath randomly generated request path
     * @param httpMethod randomly generated HTTP method
     * @param environmentId randomly generated environment UUID
     * @param environmentName randomly generated environment name
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 13: Environment name header injection")
    void environmentNameHeaderInjection(
            @ForAll("requestPaths") String requestPath,
            @ForAll("httpMethods") HttpMethod httpMethod,
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentNames") String environmentName) {
        
        // Arrange: Create filter and mock chain
        EnvironmentContextFilter filter = new EnvironmentContextFilter("test-internal-token");
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Create request (authenticated request scenario)
        MockServerHttpRequest request = MockServerHttpRequest
                .method(httpMethod, requestPath)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Simulate authenticated request by setting environment context in exchange attributes
        // (normally set by AuthenticationFilter)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Mock chain to capture the mutated exchange
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange mutatedExchange = invocation.getArgument(0);
                    ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                    
                    // Verify X-Environment-Name header was injected
                    String injectedEnvironmentName = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_NAME);
                    
                    // Property: X-Environment-Name must be present
                    assertThat(injectedEnvironmentName)
                            .as("X-Environment-Name header should be injected for authenticated request")
                            .isNotNull()
                            .isNotBlank();
                    
                    // Property: X-Environment-Name must match the authenticated application name
                    assertThat(injectedEnvironmentName)
                            .as("X-Environment-Name should match the authenticated application name")
                            .isEqualTo(environmentName);
                    
                    return Mono.empty();
                });
        
        // Act & Assert: Execute filter and verify
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }
    
    /**
     * Property 13 (variant): Environment name header injection skipped for exempt paths
     * 
     * **Validates: Requirements 4.2**
     * 
     * <p>For any request without environment context (exempt paths like POST /environments
     * or /actuator/health), the Gateway should skip environment header injection.
     * 
     * @param requestPath randomly generated request path
     * @param httpMethod randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 13: Environment name header injection")
    void environmentNameHeaderInjection_SkipForExemptPaths(
            @ForAll("requestPaths") String requestPath,
            @ForAll("httpMethods") HttpMethod httpMethod) {
        
        // Arrange: Create filter and mock chain
        EnvironmentContextFilter filter = new EnvironmentContextFilter("test-internal-token");
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Create request WITHOUT environment context (exempt path scenario)
        MockServerHttpRequest request = MockServerHttpRequest
                .method(httpMethod, requestPath)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Do NOT set environment context attributes (simulating exempt path)
        // exchange.getAttributes() remains empty
        
        // Mock chain to capture the mutated exchange
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange mutatedExchange = invocation.getArgument(0);
                    ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                    
                    // Verify X-Environment-Name header was NOT injected
                    String injectedEnvironmentName = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_NAME);
                    
                    // Property: X-Environment-Name should be absent for exempt paths
                    assertThat(injectedEnvironmentName)
                            .as("X-Environment-Name header should not be injected for exempt paths")
                            .isNull();
                    
                    return Mono.empty();
                });
        
        // Act & Assert: Execute filter and verify
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }
    
    /**
     * Property 14: Correlation ID generation
     * 
     * **Validates: Requirements 4.3, 9.1**
     * 
     * <p>For any request without an X-Correlation-Id header, the Gateway should generate
     * a new UUID correlation ID and inject it into the request.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Requests without correlation ID get a new UUID generated</li>
     *   <li>The generated correlation ID is a valid UUID format</li>
     *   <li>The correlation ID is injected into request headers for backend services</li>
     *   <li>This behavior is consistent across all request paths and methods</li>
     * </ul>
     * 
     * @param requestPath randomly generated request path
     * @param httpMethod randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 14: Correlation ID generation")
    void correlationIdGeneration(
            @ForAll("requestPaths") String requestPath,
            @ForAll("httpMethods") HttpMethod httpMethod) {
        
        // Arrange: Create filter and mock chain
        CorrelationIdFilter filter = new CorrelationIdFilter();
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Create request WITHOUT correlation ID header
        MockServerHttpRequest request = MockServerHttpRequest
                .method(httpMethod, requestPath)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Mock chain to capture the mutated exchange
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange mutatedExchange = invocation.getArgument(0);
                    ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                    
                    // Verify correlation ID was generated and injected
                    String correlationId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
                    
                    // Property: Correlation ID must be present
                    assertThat(correlationId)
                            .as("Correlation ID should be generated for request without header")
                            .isNotNull()
                            .isNotBlank();
                    
                    // Property: Correlation ID must be a valid UUID
                    assertThat(correlationId)
                            .as("Generated correlation ID should be a valid UUID")
                            .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
                    
                    // Verify it can be parsed as a UUID without exception
                    try {
                        UUID.fromString(correlationId);
                    } catch (IllegalArgumentException e) {
                        throw new AssertionError("Generated correlation ID is not a valid UUID: " + correlationId, e);
                    }
                    
                    return Mono.empty();
                });
        
        // Act & Assert: Execute filter and verify
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }
    
    /**
     * Property 14 (variant): Correlation ID generation for blank headers
     * 
     * **Validates: Requirements 4.3, 9.1**
     * 
     * <p>For any request with a blank or whitespace-only X-Correlation-Id header,
     * the Gateway should generate a new UUID correlation ID (treating blank as absent).
     * 
     * @param requestPath randomly generated request path
     * @param httpMethod randomly generated HTTP method
     * @param blankValue randomly generated blank/whitespace value
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 14: Correlation ID generation")
    void correlationIdGeneration_BlankHeader(
            @ForAll("requestPaths") String requestPath,
            @ForAll("httpMethods") HttpMethod httpMethod,
            @ForAll("blankValues") String blankValue) {
        
        // Arrange: Create filter and mock chain
        CorrelationIdFilter filter = new CorrelationIdFilter();
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Create request WITH blank correlation ID header
        MockServerHttpRequest request = MockServerHttpRequest
                .method(httpMethod, requestPath)
                .header(SifHeaders.X_CORRELATION_ID, blankValue)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Mock chain to capture the mutated exchange
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange mutatedExchange = invocation.getArgument(0);
                    ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                    
                    // Verify correlation ID was generated and injected
                    String correlationId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
                    
                    // Property: Correlation ID must be present (not blank)
                    assertThat(correlationId)
                            .as("Correlation ID should be generated when header is blank")
                            .isNotNull()
                            .isNotBlank();
                    
                    // Property: Correlation ID must be a valid UUID
                    assertThat(correlationId)
                            .as("Generated correlation ID should be a valid UUID")
                            .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
                    
                    // Verify it can be parsed as a UUID without exception
                    try {
                        UUID.fromString(correlationId);
                    } catch (IllegalArgumentException e) {
                        throw new AssertionError("Generated correlation ID is not a valid UUID: " + correlationId, e);
                    }
                    
                    return Mono.empty();
                });
        
        // Act & Assert: Execute filter and verify
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }
    
    /**
     * Property 15: Correlation ID preservation
     * 
     * **Validates: Requirements 4.4, 9.2**
     * 
     * <p>For any request with an existing X-Correlation-Id header, the Gateway should
     * preserve the correlation ID without modification.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Existing correlation IDs are preserved exactly as provided</li>
     *   <li>Both UUID and non-UUID correlation IDs are preserved</li>
     *   <li>The correlation ID is not regenerated when already present</li>
     *   <li>This behavior is consistent across all request paths and methods</li>
     * </ul>
     * 
     * @param requestPath randomly generated request path
     * @param httpMethod randomly generated HTTP method
     * @param existingCorrelationId randomly generated existing correlation ID
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 15: Correlation ID preservation")
    void correlationIdPreservation(
            @ForAll("requestPaths") String requestPath,
            @ForAll("httpMethods") HttpMethod httpMethod,
            @ForAll("correlationIds") String existingCorrelationId) {
        
        // Arrange: Create filter and mock chain
        CorrelationIdFilter filter = new CorrelationIdFilter();
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Create request WITH existing correlation ID header
        MockServerHttpRequest request = MockServerHttpRequest
                .method(httpMethod, requestPath)
                .header(SifHeaders.X_CORRELATION_ID, existingCorrelationId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Mock chain to capture the mutated exchange
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange mutatedExchange = invocation.getArgument(0);
                    ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
                    
                    // Verify correlation ID was preserved
                    String correlationId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
                    
                    // Property: Correlation ID must match the original exactly
                    assertThat(correlationId)
                            .as("Correlation ID should be preserved without modification")
                            .isEqualTo(existingCorrelationId);
                    
                    return Mono.empty();
                });
        
        // Act & Assert: Execute filter and verify
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }
    
    /**
     * Property 16: Header injection completeness
     * 
     * **Validates: Requirements 4.6**
     * 
     * <p>For any authenticated and authorized request routed to a backend service,
     * the request should contain ALL three context headers: X-Environment-Id,
     * X-Environment-Name, and X-Correlation-Id.
     * 
     * <p>This is an integration-style property test that validates the complete
     * filter chain behavior where both CorrelationIdFilter and EnvironmentContextFilter
     * have run, ensuring all context headers are injected before routing to backend services.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>All three context headers are present in the final request</li>
     *   <li>X-Environment-Id contains the authenticated environment UUID</li>
     *   <li>X-Environment-Name contains the authenticated application name</li>
     *   <li>X-Correlation-Id contains a valid correlation ID (generated or preserved)</li>
     *   <li>This behavior is consistent across all request paths and methods</li>
     *   <li>The complete filter chain works correctly end-to-end</li>
     * </ul>
     * 
     * @param requestPath randomly generated request path
     * @param httpMethod randomly generated HTTP method
     * @param environmentId randomly generated environment UUID
     * @param environmentName randomly generated environment name
     * @param hasExistingCorrelationId whether request has existing correlation ID
     * @param existingCorrelationId randomly generated existing correlation ID (if present)
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 16: Header injection completeness")
    void headerInjectionCompleteness(
            @ForAll("requestPaths") String requestPath,
            @ForAll("httpMethods") HttpMethod httpMethod,
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("environmentNames") String environmentName,
            @ForAll boolean hasExistingCorrelationId,
            @ForAll("correlationIds") String existingCorrelationId) {
        
        // Arrange: Create both filters to simulate the complete filter chain
        CorrelationIdFilter correlationIdFilter = new CorrelationIdFilter();
        EnvironmentContextFilter environmentContextFilter = new EnvironmentContextFilter("test-internal-token");
        
        // Create mock chain that will be used by the final filter
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Create request (authenticated and authorized request scenario)
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest
                .method(httpMethod, requestPath);
        
        // Conditionally add existing correlation ID
        if (hasExistingCorrelationId) {
            requestBuilder.header(SifHeaders.X_CORRELATION_ID, existingCorrelationId);
        }
        
        MockServerHttpRequest request = requestBuilder.build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Simulate authenticated and authorized request by setting environment context
        // (normally set by AuthenticationFilter and verified by AuthorizationFilter)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Mock chain to capture the final mutated exchange after all filters
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange finalExchange = invocation.getArgument(0);
                    ServerHttpRequest finalRequest = finalExchange.getRequest();
                    
                    // ===== Property: ALL THREE context headers must be present =====
                    
                    // Verify X-Environment-Id header is present
                    String injectedEnvironmentId = finalRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID);
                    assertThat(injectedEnvironmentId)
                            .as("X-Environment-Id header must be present in authenticated request")
                            .isNotNull()
                            .isNotBlank();
                    
                    // Verify X-Environment-Name header is present
                    String injectedEnvironmentName = finalRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_NAME);
                    assertThat(injectedEnvironmentName)
                            .as("X-Environment-Name header must be present in authenticated request")
                            .isNotNull()
                            .isNotBlank();
                    
                    // Verify X-Correlation-Id header is present
                    String injectedCorrelationId = finalRequest.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
                    assertThat(injectedCorrelationId)
                            .as("X-Correlation-Id header must be present in all requests")
                            .isNotNull()
                            .isNotBlank();
                    
                    // ===== Property: Header values must be correct =====
                    
                    // Verify X-Environment-Id matches the authenticated environment UUID
                    assertThat(injectedEnvironmentId)
                            .as("X-Environment-Id must match authenticated environment UUID")
                            .isEqualTo(environmentId.toString());
                    
                    // Verify X-Environment-Id is a valid UUID format
                    assertThat(injectedEnvironmentId)
                            .as("X-Environment-Id must be a valid UUID format")
                            .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
                    
                    // Verify X-Environment-Name matches the authenticated application name
                    assertThat(injectedEnvironmentName)
                            .as("X-Environment-Name must match authenticated application name")
                            .isEqualTo(environmentName);
                    
                    // Verify X-Correlation-Id is correct (preserved if existed, generated if not)
                    if (hasExistingCorrelationId) {
                        // If correlation ID existed, it should be preserved
                        assertThat(injectedCorrelationId)
                                .as("X-Correlation-Id must be preserved when already present")
                                .isEqualTo(existingCorrelationId);
                    } else {
                        // If correlation ID didn't exist, it should be a valid UUID
                        assertThat(injectedCorrelationId)
                                .as("Generated X-Correlation-Id must be a valid UUID")
                                .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
                        
                        // Verify it can be parsed as a UUID
                        try {
                            UUID.fromString(injectedCorrelationId);
                        } catch (IllegalArgumentException e) {
                            throw new AssertionError("Generated correlation ID is not a valid UUID: " + injectedCorrelationId, e);
                        }
                    }
                    
                    // ===== Property: All headers must be available for backend services =====
                    // This is implicitly verified by the presence checks above, but we can
                    // add an explicit assertion for clarity
                    assertThat(finalRequest.getHeaders())
                            .as("Request headers must contain all three context headers")
                            .containsKeys(
                                    SifHeaders.X_ENVIRONMENT_ID,
                                    SifHeaders.X_ENVIRONMENT_NAME,
                                    SifHeaders.X_CORRELATION_ID
                            );
                    
                    return Mono.empty();
                });
        
        // Act: Execute the complete filter chain
        // First, run CorrelationIdFilter
        Mono<Void> filterChain = correlationIdFilter.filter(exchange, new GatewayFilterChain() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchangeAfterCorrelation) {
                // Then, run EnvironmentContextFilter
                return environmentContextFilter.filter(exchangeAfterCorrelation, mockChain);
            }
        });
        
        // Assert: Verify the complete filter chain executes successfully
        StepVerifier.create(filterChain)
                .verifyComplete();
    }
    
    /**
     * Property 16 (variant): Header injection completeness skipped for exempt paths
     * 
     * **Validates: Requirements 4.6**
     * 
     * <p>For any request without environment context (exempt paths like POST /environments
     * or /actuator/health), only the X-Correlation-Id header should be present, while
     * environment headers (X-Environment-Id, X-Environment-Name) should be absent.
     * 
     * <p>This verifies that exempt paths still get correlation IDs for tracing, but
     * do not get environment context headers since they are not authenticated.
     * 
     * @param requestPath randomly generated request path
     * @param httpMethod randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 16: Header injection completeness")
    void headerInjectionCompleteness_ExemptPaths(
            @ForAll("requestPaths") String requestPath,
            @ForAll("httpMethods") HttpMethod httpMethod) {
        
        // Arrange: Create both filters to simulate the complete filter chain
        CorrelationIdFilter correlationIdFilter = new CorrelationIdFilter();
        EnvironmentContextFilter environmentContextFilter = new EnvironmentContextFilter("test-internal-token");
        
        // Create mock chain that will be used by the final filter
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        
        // Create request WITHOUT environment context (exempt path scenario)
        MockServerHttpRequest request = MockServerHttpRequest
                .method(httpMethod, requestPath)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Do NOT set environment context attributes (simulating exempt path)
        // exchange.getAttributes() remains empty
        
        // Mock chain to capture the final mutated exchange after all filters
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange finalExchange = invocation.getArgument(0);
                    ServerHttpRequest finalRequest = finalExchange.getRequest();
                    
                    // ===== Property: Only correlation ID should be present for exempt paths =====
                    
                    // Verify X-Correlation-Id header IS present (always injected)
                    String injectedCorrelationId = finalRequest.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
                    assertThat(injectedCorrelationId)
                            .as("X-Correlation-Id header must be present even for exempt paths")
                            .isNotNull()
                            .isNotBlank();
                    
                    // Verify X-Correlation-Id is a valid UUID
                    assertThat(injectedCorrelationId)
                            .as("Generated X-Correlation-Id must be a valid UUID")
                            .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
                    
                    // Verify X-Environment-Id header is NOT present (exempt path)
                    String injectedEnvironmentId = finalRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID);
                    assertThat(injectedEnvironmentId)
                            .as("X-Environment-Id header must not be present for exempt paths")
                            .isNull();
                    
                    // Verify X-Environment-Name header is NOT present (exempt path)
                    String injectedEnvironmentName = finalRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_NAME);
                    assertThat(injectedEnvironmentName)
                            .as("X-Environment-Name header must not be present for exempt paths")
                            .isNull();
                    
                    return Mono.empty();
                });
        
        // Act: Execute the complete filter chain
        // First, run CorrelationIdFilter
        Mono<Void> filterChain = correlationIdFilter.filter(exchange, new GatewayFilterChain() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchangeAfterCorrelation) {
                // Then, run EnvironmentContextFilter
                return environmentContextFilter.filter(exchangeAfterCorrelation, mockChain);
            }
        });
        
        // Assert: Verify the complete filter chain executes successfully
        StepVerifier.create(filterChain)
                .verifyComplete();
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates random request paths.
     * Covers various API endpoints in the SIF Unity Broker.
     */
    @Provide
    Arbitrary<String> requestPaths() {
        // Generate paths for different services
        Arbitrary<String> environmentPaths = Arbitraries.of(
                "/environments",
                "/environments/123e4567-e89b-12d3-a456-426614174000",
                "/acl-entries",
                "/providers",
                "/zones"
        );
        
        Arbitrary<String> connectorPaths = Arbitraries.of(
                "/requests/StudentPersonal",
                "/responses/StudentPersonal",
                "/events/StudentPersonal",
                "/services/StudentPersonal"
        );
        
        Arbitrary<String> corePaths = Arbitraries.of(
                "/queues",
                "/subscriptions",
                "/jobs"
        );
        
        Arbitrary<String> healthPaths = Arbitraries.of(
                "/actuator/health",
                "/actuator/metrics"
        );
        
        // Combine all path types
        return Arbitraries.oneOf(environmentPaths, connectorPaths, corePaths, healthPaths);
    }
    
    /**
     * Generates random HTTP methods.
     * Covers all standard HTTP methods used in the Gateway.
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
     * Generates random correlation IDs.
     * Includes both UUID and non-UUID formats to test preservation.
     */
    @Provide
    Arbitrary<String> correlationIds() {
        // Generate UUID-format correlation IDs
        Arbitrary<String> uuidCorrelationIds = Arbitraries.randomValue(random -> 
                UUID.randomUUID().toString()
        );
        
        // Generate custom non-UUID correlation IDs
        Arbitrary<String> customCorrelationIds = Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_')
                .ofMinLength(10)
                .ofMaxLength(50);
        
        // Combine both types (80% UUID, 20% custom)
        return Arbitraries.frequencyOf(
                Tuple.of(8, uuidCorrelationIds),
                Tuple.of(2, customCorrelationIds)
        );
    }
    
    /**
     * Generates blank or whitespace-only values.
     * Used to test that blank correlation IDs are treated as absent.
     */
    @Provide
    Arbitrary<String> blankValues() {
        return Arbitraries.of(
                "",           // Empty string
                " ",          // Single space
                "  ",         // Multiple spaces
                "\t",         // Tab
                "\n",         // Newline
                "   \t  ",    // Mixed whitespace
                "\t\n\t"      // Mixed whitespace with newlines
        );
    }
    
    /**
     * Generates random environment UUIDs.
     * Used to test environment ID header injection.
     */
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    /**
     * Generates random environment names (application names).
     * Covers various naming patterns used in SIF environments.
     */
    @Provide
    Arbitrary<String> environmentNames() {
        // Generate realistic application names
        Arbitrary<String> simpleNames = Arbitraries.of(
                "TestApp",
                "StudentIS",
                "SchoolPortal",
                "LearningManagementSystem",
                "DataWarehouse"
        );
        
        // Generate names with numbers and special characters
        Arbitrary<String> complexNames = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15),
                Arbitraries.integers().between(1, 999)
        ).as((name, number) -> name + "-" + number);
        
        // Generate names with organization prefixes
        Arbitrary<String> prefixedNames = Combinators.combine(
                Arbitraries.of("ACME", "EDU", "SCHOOL", "DISTRICT"),
                Arbitraries.of("SIS", "Portal", "Gateway", "Service")
        ).as((prefix, suffix) -> prefix + "_" + suffix);
        
        // Combine all name types
        return Arbitraries.oneOf(simpleNames, complexNames, prefixedNames);
    }
}
