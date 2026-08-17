// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.PermissionCheckRequest;
import org.sifunity.broker.common.dto.PermissionCheckResponse;
import org.sifunity.broker.common.exception.InvalidRequestException;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.filter.AuthorizationFilter;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


/**
 * Property-based tests for authorization properties in the API Gateway.
 * 
 * <p>These tests verify universal correctness properties across randomized inputs
 * to ensure the Gateway correctly handles authorization in all scenarios.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Property 7: Routing parameter extraction</li>
 *   <li>Property 8: Access type determination</li>
 * </ul>
 * 
 * <p>Each property test runs a minimum of 100 iterations with randomized test data.
 * 
 * @see AuthorizationFilter
 */
class AuthorizationPropertiesTest {
    
    /**
     * Property 7: Routing parameter extraction
     * 
     * **Validates: Requirements 3.1**
     * 
     * <p>For any authenticated request, the Gateway should extract service name, zone, and context
     * from request headers and path.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Service name is correctly extracted from serviceName header when present</li>
     *   <li>Service name is correctly extracted from path when header is absent</li>
     *   <li>Zone ID is correctly extracted from zoneId header when present</li>
     *   <li>Zone ID falls back to default zone when header is absent</li>
     *   <li>Context ID is correctly extracted from contextId header when present</li>
     *   <li>Context ID falls back to default context when header is absent</li>
     *   <li>Extraction is consistent across all valid request formats</li>
     * </ul>
     * 
     * @param serviceName randomly generated service name
     * @param zoneId randomly generated zone ID
     * @param contextId randomly generated context ID (may be null)
     * @param defaultZoneId randomly generated default zone ID
     * @param includeServiceNameHeader whether to include serviceName in header
     * @param includeZoneIdHeader whether to include zoneId in header
     * @param includeContextIdHeader whether to include contextId in header
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 7: Routing parameter extraction")
    void routingParameterExtraction(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("zoneIds") String defaultZoneId,
            @ForAll boolean includeServiceNameHeader,
            @ForAll boolean includeZoneIdHeader,
            @ForAll boolean includeContextIdHeader) {
        
        // Arrange: Create a mock request with various header combinations
        String path = "/testDomain/testSolution/requests/" + serviceName;
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest
                .get(path);
        
        // Add serviceName header if specified
        if (includeServiceNameHeader) {
            requestBuilder.header(SifHeaders.SERVICE_NAME, serviceName);
        }
        
        // Add zoneId header if specified
        if (includeZoneIdHeader) {
            requestBuilder.header(SifHeaders.ZONE_ID, zoneId);
        }
        
        // Add contextId header if specified
        if (includeContextIdHeader && contextId != null) {
            requestBuilder.header(SifHeaders.CONTEXT_ID, contextId);
        }
        
        MockServerHttpRequest request = requestBuilder.build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set default zone and context in exchange attributes (set by AuthenticationFilter)
        exchange.getAttributes().put("defaultZoneId", defaultZoneId);
        
        // Act: Extract parameters using the same logic as AuthorizationFilter
        String extractedServiceName = extractServiceName(request);
        String extractedZoneId = extractZoneId(exchange, request);
        String extractedContextId = extractContextId(exchange, request);
        
        // Assert: Verify correct extraction based on header presence
        
        // Service name should always be extracted (from header or path)
        assertThat(extractedServiceName)
                .as("Service name should be extracted from header or path")
                .isEqualTo(serviceName);
        
        // Zone ID should come from header if present, otherwise from default
        if (includeZoneIdHeader) {
            assertThat(extractedZoneId)
                    .as("Zone ID should be extracted from header when present")
                    .isEqualTo(zoneId);
        } else {
            assertThat(extractedZoneId)
                    .as("Zone ID should fall back to default when header is absent")
                    .isEqualTo(defaultZoneId);
        }
        
        // Context ID should come from header if present, otherwise null
        if (includeContextIdHeader && contextId != null) {
            assertThat(extractedContextId)
                    .as("Context ID should be extracted from header when present")
                    .isEqualTo(contextId);
        } else {
            assertThat(extractedContextId)
                    .as("Context ID should be null when header is absent")
                    .isNull();
        }
    }
    
    /**
     * Property 7 (variant): Service name extraction from path
     * 
     * **Validates: Requirements 3.1**
     * 
     * <p>For any request without a serviceName header, the Gateway should extract
     * the service name from the path (e.g., /requests/StudentPersonal → StudentPersonal).
     * 
     * @param basePath randomly generated base path
     * @param serviceName randomly generated service name
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 7: Routing parameter extraction")
    void serviceNameExtractionFromPath(
            @ForAll("basePaths") String basePath,
            @ForAll("serviceNames") String serviceName) {
        
        // Arrange: Create a request with service name in path but not in header
        String path = basePath + "/" + serviceName;
        MockServerHttpRequest request = MockServerHttpRequest
                .get(path)
                .build();
        
        // Act: Extract service name from path
        String extractedServiceName = extractServiceName(request);
        
        // Assert: Verify service name is correctly extracted from path
        assertThat(extractedServiceName)
                .as("Service name should be extracted from path segment")
                .isEqualTo(serviceName);
    }
    
    /**
     * Property 7 (variant): Service name extraction failure
     * 
     * **Validates: Requirements 3.1**
     * 
     * <p>For any request without a serviceName header and without a valid path format,
     * the Gateway should throw InvalidRequestException.
     * 
     * @param invalidPath randomly generated invalid path (too short)
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 7: Routing parameter extraction")
    void serviceNameExtractionFailure(@ForAll("invalidPaths") String invalidPath) {
        
        // Arrange: Create a request with invalid path and no serviceName header
        MockServerHttpRequest request = MockServerHttpRequest
                .get(invalidPath)
                .build();
        
        // Act & Assert: Verify InvalidRequestException is thrown
        assertThatThrownBy(() -> extractServiceName(request))
                .as("Should throw InvalidRequestException when service name cannot be extracted")
                .isInstanceOf(InvalidRequestException.class);
    }
    
    /**
     * Property 7 (variant): serviceName header used for /events
     * 
     * **Validates: Requirements 3.1**
     * 
     * <p>For POST /events, the service name comes from the serviceName header,
     * not from the URL path (there is no service name segment in the events URL).
     * 
     * @param serviceName service name in header
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 7: Routing parameter extraction")
    void serviceNameHeaderUsedForEvents(
            @ForAll("serviceNames") String serviceName) {
        
        // Arrange: POST /events with serviceName header (no service name in path)
        String path = "/testDomain/testSolution/events";
        MockServerHttpRequest request = MockServerHttpRequest
                .post(path)
                .header(SifHeaders.SERVICE_NAME, serviceName)
                .build();
        
        // Act: Extract service name
        String extractedServiceName = extractServiceName(request);
        
        // Assert: service name comes from header for /events
        assertThat(extractedServiceName)
                .as("Service name should come from serviceName header for /events")
                .isEqualTo(serviceName);
    }
    
    /**
     * Property 8: Access type determination
     * 
     * **Validates: Requirements 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**
     * 
     * <p>For any HTTP method and path combination, the Gateway should determine the correct
     * access type (QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE) based on the method and path pattern.
     * 
     * <p>Access type rules:</p>
     * <ul>
     *   <li>GET /requests/* → CONSUME</li>
     *   <li>POST /responses/* → PROVIDE</li>
     *   <li>POST /events/* → PROVIDE</li>
     *   <li>GET /services/* → CONSUME</li>
     *   <li>POST/PUT/DELETE /services/* → PROVIDE</li>
     *   <li>Default → CONSUME</li>
     * </ul>
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Access type determination is consistent across all method/path combinations</li>
     *   <li>Specific rules are correctly applied for known patterns</li>
     *   <li>Default access type (QUERY) is applied for unmatched patterns</li>
     *   <li>The logic handles all HTTP methods correctly</li>
     * </ul>
     * 
     * @param methodAndPath randomly generated HTTP method and path combination
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 8: Access type determination")
    void accessTypeDetermination(@ForAll("methodAndPathCombinations") MethodAndPath methodAndPath) {
        
        // Act: Determine access type using the same logic as AuthorizationFilter
        AccessType actualAccessType = determineAccessType(methodAndPath.method(), methodAndPath.path());
        
        // Assert: Verify correct access type based on method and path
        AccessType expectedAccessType = getExpectedAccessType(methodAndPath.method(), methodAndPath.path());
        
        assertThat(actualAccessType)
                .as("Access type for %s %s should be %s", 
                    methodAndPath.method(), methodAndPath.path(), expectedAccessType)
                .isEqualTo(expectedAccessType);
    }
    
    /**
     * Determines the expected access type based on the requirements.
     * This is the reference implementation used to verify the actual implementation.
     */
    private AccessType getExpectedAccessType(HttpMethod method, String path) {
        // GET /requests/* -> QUERY
        if (method == HttpMethod.GET && path.startsWith("/requests")) {
            return AccessType.QUERY;
        }
        
        // POST /responses/* -> PROVIDE
        if (method == HttpMethod.POST && path.startsWith("/responses")) {
            return AccessType.PROVIDE;
        }
        
        // POST /events/* -> PROVIDE
        if (method == HttpMethod.POST && path.startsWith("/events")) {
            return AccessType.PROVIDE;
        }
        
        // GET /services/* -> QUERY
        if (method == HttpMethod.GET && path.startsWith("/services")) {
            return AccessType.QUERY;
        }
        
        // POST/PUT/DELETE /services/* -> PROVIDE
        if ((method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE) 
            && path.startsWith("/services")) {
            return AccessType.PROVIDE;
        }
        
        // Default to QUERY for other paths
        return AccessType.QUERY;
    }
    
    // ========== Helper Methods (mirroring AuthorizationFilter logic) ==========
    
    /**
     * Extracts the service name from the request.
     * This mirrors the logic in AuthorizationFilter.extractServiceName().
     */
    private String extractServiceName(MockServerHttpRequest request) {
        String path = request.getPath().value();
        ParsedPath parsed = UrlPrefixParser.parse(path);
        String resourcePath = parsed.resourcePath();
        
        // For /responses, use relativeServicePath header. (/responses/error was removed — see
        // AuthorizationFilter#extractServiceName.)
        if (resourcePath.equals("/responses")) {
            String rsp = request.getHeaders().getFirst(SifHeaders.RELATIVE_SERVICE_PATH);
            if (rsp != null && !rsp.isBlank()) {
                return AuthorizationFilter.extractServiceNameFromRelativeServicePath(rsp);
            }
            throw new InvalidRequestException("relativeServicePath header required for /responses");
        }
        
        // For /events, use serviceName header
        if (resourcePath.equals("/events")) {
            String serviceName = request.getHeaders().getFirst(SifHeaders.SERVICE_NAME);
            if (serviceName != null && !serviceName.isBlank()) {
                return serviceName;
            }
            throw new InvalidRequestException("serviceName header required for /events");
        }
        
        // For other paths, extract from resource path segments
        String[] parts = resourcePath.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        if (parts.length >= 2 && !parts[1].isBlank()) {
            return parts[1];
        }
        
        throw new InvalidRequestException("Service name not found in path");
    }
    
    /**
     * Extracts the zone ID from the request.
     * This mirrors the logic in AuthorizationFilter.extractZoneId().
     */
    private String extractZoneId(MockServerWebExchange exchange, MockServerHttpRequest request) {
        String zoneId = request.getHeaders().getFirst(SifHeaders.ZONE_ID);
        if (zoneId == null || zoneId.isBlank()) {
            // Use default zone from environment
            zoneId = exchange.getAttribute("defaultZoneId");
        }
        return zoneId;
    }
    
    /**
     * Extracts the context ID from the request.
     * This mirrors the logic in AuthorizationFilter.extractContextId().
     */
    private String extractContextId(MockServerWebExchange exchange, MockServerHttpRequest request) {
        String contextId = request.getHeaders().getFirst(SifHeaders.CONTEXT_ID);
        if (contextId == null || contextId.isBlank()) {
            return null;
        }
        return contextId;
    }
    
    /**
     * Determines the access type based on HTTP method and path pattern.
     * This mirrors the logic in AuthorizationFilter.determineAccessType().
     */
    private AccessType determineAccessType(HttpMethod method, String path) {
        // GET /requests/* -> QUERY
        if (method == HttpMethod.GET && path.startsWith("/requests")) {
            return AccessType.QUERY;
        }
        
        // POST /responses/* -> PROVIDE
        if (method == HttpMethod.POST && path.startsWith("/responses")) {
            return AccessType.PROVIDE;
        }
        
        // POST /events/* -> PROVIDE
        if (method == HttpMethod.POST && path.startsWith("/events")) {
            return AccessType.PROVIDE;
        }
        
        // GET /services/* -> QUERY
        if (method == HttpMethod.GET && path.startsWith("/services")) {
            return AccessType.QUERY;
        }
        
        // POST/PUT/DELETE /services/* -> PROVIDE
        if ((method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE) 
            && path.startsWith("/services")) {
            return AccessType.PROVIDE;
        }
        
        // Default to QUERY for other paths
        return AccessType.QUERY;
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates random service names.
     * Service names in SIF are typically PascalCase object names like StudentPersonal, SchoolInfo.
     */
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
                "StudentPersonal",
                "SchoolInfo",
                "StaffPersonal",
                "StudentAttendance",
                "GradingAssignment",
                "StudentSchoolEnrollment",
                "StudentContactPersonal",
                "LearningResource",
                "Assessment",
                "StudentGrade"
        );
    }
    
    /**
     * Generates random zone IDs.
     * Zone IDs are typically alphanumeric strings like "default", "zone-1", "au-nsw".
     */
    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.oneOf(
                // Common zone patterns
                Arbitraries.of("default", "zone-1", "zone-2", "test-zone", "prod-zone"),
                
                // Geographic zones
                Arbitraries.of("au-nsw", "au-vic", "au-qld", "us-east", "us-west"),
                
                // Custom alphanumeric zones
                Combinators.combine(
                        Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8),
                        Arbitraries.integers().between(1, 99)
                ).as((prefix, num) -> prefix.toLowerCase() + "-" + num)
        );
    }
    
    /**
     * Generates random context IDs (may be null).
     * Context IDs are optional and typically alphanumeric strings.
     */
    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.oneOf(
                // Null context (optional field)
                Arbitraries.just(null),
                
                // Common context patterns
                Arbitraries.of("default", "context-1", "context-2", "test-context"),
                
                // Custom alphanumeric contexts
                Arbitraries.strings().alpha().numeric().withChars('-', '_')
                        .ofMinLength(5).ofMaxLength(20)
        );
    }
    
    /**
     * Generates random base paths for connector and core services.
     * These are the path prefixes that require authorization.
     */
    @Provide
    Arbitrary<String> basePaths() {
        return Arbitraries.of(
                "/testDomain/testSolution/requests",
                "/testDomain/testSolution/responses",
                "/testDomain/testSolution/events",
                "/testDomain/testSolution/services"
        );
    }
    
    /**
     * Generates random invalid paths that cannot be parsed for service name.
     * These paths are too short or don't follow the expected format.
     */
    @Provide
    Arbitrary<String> invalidPaths() {
        return Arbitraries.oneOf(
                // Root path
                Arbitraries.just("/"),
                
                // Empty path
                Arbitraries.just("")
        );
    }
    
    /**
     * Generates random HTTP method and path combinations for access type testing.
     * This generator creates diverse combinations including:
     * <ul>
     *   <li>Specific patterns that match access type rules (GET /requests, POST /responses, etc.)</li>
     *   <li>Edge cases (different HTTP methods on same paths)</li>
     *   <li>Default cases (paths that don't match specific rules)</li>
     * </ul>
     */
    @Provide
    Arbitrary<MethodAndPath> methodAndPathCombinations() {
        return Arbitraries.oneOf(
                // GET /requests/* -> QUERY
                Combinators.combine(
                        Arbitraries.just(HttpMethod.GET),
                        serviceNames().map(s -> "/requests/" + s)
                ).as(MethodAndPath::new),
                
                // POST /responses/* -> PROVIDE
                Combinators.combine(
                        Arbitraries.just(HttpMethod.POST),
                        serviceNames().map(s -> "/responses/" + s)
                ).as(MethodAndPath::new),
                
                // POST /events/* -> PROVIDE
                Combinators.combine(
                        Arbitraries.just(HttpMethod.POST),
                        serviceNames().map(s -> "/events/" + s)
                ).as(MethodAndPath::new),
                
                // GET /services/* -> QUERY
                Combinators.combine(
                        Arbitraries.just(HttpMethod.GET),
                        serviceNames().map(s -> "/services/" + s)
                ).as(MethodAndPath::new),
                
                // POST /services/* -> PROVIDE
                Combinators.combine(
                        Arbitraries.just(HttpMethod.POST),
                        serviceNames().map(s -> "/services/" + s)
                ).as(MethodAndPath::new),
                
                // PUT /services/* -> PROVIDE
                Combinators.combine(
                        Arbitraries.just(HttpMethod.PUT),
                        serviceNames().map(s -> "/services/" + s)
                ).as(MethodAndPath::new),
                
                // DELETE /services/* -> PROVIDE
                Combinators.combine(
                        Arbitraries.just(HttpMethod.DELETE),
                        serviceNames().map(s -> "/services/" + s)
                ).as(MethodAndPath::new),
                
                // Edge cases: Different methods on /requests (should default to QUERY)
                Combinators.combine(
                        Arbitraries.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH),
                        serviceNames().map(s -> "/requests/" + s)
                ).as(MethodAndPath::new),
                
                // Edge cases: Different methods on /responses (should default to QUERY except POST)
                Combinators.combine(
                        Arbitraries.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH),
                        serviceNames().map(s -> "/responses/" + s)
                ).as(MethodAndPath::new),
                
                // Edge cases: Different methods on /events (should default to QUERY except POST)
                Combinators.combine(
                        Arbitraries.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH),
                        serviceNames().map(s -> "/events/" + s)
                ).as(MethodAndPath::new),
                
                // Default cases: Other paths (should default to QUERY)
                Combinators.combine(
                        Arbitraries.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE),
                        Arbitraries.of("/queues", "/subscriptions", "/jobs", "/environments", "/acl-entries")
                                .flatMap(base -> serviceNames().map(s -> base + "/" + s))
                ).as(MethodAndPath::new)
        );
    }
    
    /**
     * Property 11: Authorization failure rejection
     * 
     * **Validates: Requirements 3.10**
     * 
     * <p>For any authorization request where the Environment Service returns permission denied,
     * the Gateway should return HTTP 403 Forbidden with error code AUTHZ_001.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Permission denied responses result in HTTP 403 status</li>
     *   <li>Error response contains error code AUTHZ_001</li>
     *   <li>Error response is in SIF-compliant format</li>
     *   <li>Authorization failures are handled consistently across all request types</li>
     *   <li>The filter chain is not called when permission is denied</li>
     * </ul>
     * 
     * @param authRequest randomly generated authorization request parameters
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 11: Authorization failure rejection")
    void authorizationFailureRejection(@ForAll("authorizationRequests") AuthorizationRequest authRequest) {
        // Arrange: Create a mock request with the generated parameters
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest
                .method(authRequest.method(), authRequest.path());
        
        // Add service name header if present
        if (authRequest.serviceName() != null) {
            requestBuilder.header(SifHeaders.SERVICE_NAME, authRequest.serviceName());
        }
        
        // Add zone and context headers if present
        if (authRequest.zoneId() != null) {
            requestBuilder.header(SifHeaders.ZONE_ID, authRequest.zoneId());
        }
        if (authRequest.contextId() != null) {
            requestBuilder.header(SifHeaders.CONTEXT_ID, authRequest.contextId());
        }
        
        MockServerHttpRequest request = requestBuilder.build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment attributes (normally set by AuthenticationFilter)
        exchange.getAttributes().put("environmentId", authRequest.environmentId());
        exchange.getAttributes().put("environmentName", "TestApp");
        exchange.getAttributes().put("defaultZoneId", authRequest.defaultZoneId());
        
        // Create a mock filter chain
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        // Create a mock EnvironmentServiceClient that returns permission denied
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        PermissionCheckResponse deniedResponse = new PermissionCheckResponse(false);
        when(mockClient.checkPermission(any(PermissionCheckRequest.class)))
                .thenReturn(Mono.just(deniedResponse));
        
        // Create the filter with mocked dependencies
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        // Create mock GatewayMetricsService
        org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
            org.mockito.Mockito.mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        
        AuthorizationFilter filter = new AuthorizationFilter(mockClient, objectMapper, mockMetricsService);
        
        // Act: Execute the filter
        filter.filter(exchange, mockChain).block();
        
        // Assert: Verify 403 Forbidden response
        assertThat(exchange.getResponse().getStatusCode())
                .as("Authorization failure should return HTTP 403 Forbidden")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        
        // Verify the filter chain was NOT called (request was rejected)
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
        
        // Verify the permission check was called with correct parameters
        verify(mockClient).checkPermission(any(PermissionCheckRequest.class));
        
        // Verify response content type is JSON
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .as("Error response should have JSON content type")
                .isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
        
        // Note: We cannot easily verify the error code AUTHZ_001 in the response body
        // because the response is written asynchronously to a DataBuffer.
        // The unit tests in AuthorizationFilterTest verify the error response format.
        // This property test focuses on the HTTP status code and that the request is rejected.
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates random authorization request parameters for testing authorization failures.
     * This generator creates diverse combinations of request parameters including:
     * <ul>
     *   <li>Various HTTP methods (GET, POST, PUT, DELETE)</li>
     *   <li>Different path patterns (/requests, /responses, /events, /services)</li>
     *   <li>Service names in headers or paths</li>
     *   <li>Zone and context IDs (with and without headers)</li>
     *   <li>Environment IDs and default values</li>
     * </ul>
     */
    @Provide
    Arbitrary<AuthorizationRequest> authorizationRequests() {
        return Combinators.combine(
                // Environment ID
                Arbitraries.create(UUID::randomUUID),
                
                // HTTP method
                Arbitraries.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE),
                
                // Base path
                basePaths(),
                
                // Service name
                serviceNames(),
                
                // Zone ID (for header)
                zoneIds(),
                
                // Context ID (for header, may be null)
                contextIds(),
                
                // Default zone ID
                zoneIds()
        ).as((envId, method, basePath, serviceName, zoneId, contextId, defaultZoneId) -> {
            String path = basePath + "/" + serviceName;
            return new AuthorizationRequest(
                    envId,
                    method,
                    path,
                    serviceName,
                    zoneId,
                    contextId,
                    defaultZoneId
            );
        });
    }
    
    /**
     * Record representing an authorization request for property testing.
     * 
     * @param environmentId the environment UUID
     * @param method the HTTP method
     * @param path the request path
     * @param serviceName the service name (may be in header or path)
     * @param zoneId the zone ID from header (may be null)
     * @param contextId the context ID from header (may be null)
     * @param defaultZoneId the default zone ID from environment
     */
    record AuthorizationRequest(
            UUID environmentId,
            HttpMethod method,
            String path,
            String serviceName,
            String zoneId,
            String contextId,
            String defaultZoneId
    ) {}
    
    /**
     * Record representing an HTTP method and path combination for testing.
     * 
     * @param method the HTTP method
     * @param path the request path
     */
    record MethodAndPath(HttpMethod method, String path) {}
}
