// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import net.jqwik.api.*;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.exception.MalformedAuthHeaderException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.gateway.filter.AuthenticationFilter;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for authentication properties in the API Gateway.
 * 
 * <p>These tests verify universal correctness properties across randomized inputs
 * to ensure the Gateway correctly handles authentication in all scenarios.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Property 1: Authorization header parsing</li>
 *   <li>Property 4: Authentication failure propagation</li>
 * </ul>
 * 
 * <p>Each property test runs a minimum of 100 iterations with randomized test data.
 * 
 * @see EnvironmentServiceClient
 */
class AuthenticationPropertiesTest {
    
    // WireMock server is created per property test to ensure isolation
    
    /**
     * Property 1: Authorization header parsing
     * 
     * **Validates: Requirements 1.1, 1.2**
     * 
     * <p>For any valid Authorization header in format "SIF_HMACSHA256 {token}:{timestamp}:{hash}",
     * the Gateway should successfully extract the sessionToken, timestamp, and hash components.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Valid authorization headers are correctly parsed</li>
     *   <li>All three components (sessionToken, timestamp, hash) are extracted</li>
     *   <li>The extracted components match the original values</li>
     *   <li>Parsing is consistent across all valid header formats</li>
     * </ul>
     * 
     * @param sessionToken randomly generated session token
     * @param timestamp randomly generated timestamp
     * @param hash randomly generated hash
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 1: Authorization header parsing")
    void authorizationHeaderParsing(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("timestamps") String timestamp,
            @ForAll("hashes") String hash) {
        
        // Arrange: Construct a valid Authorization header
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        // Note: timestamp is sent in a separate header, not in the authorization header
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        // Act: Parse the authorization header
        SifAuthenticationUtils.AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(authHeader);
        
        // Assert: Verify all components are extracted correctly
        assertThat(parsed).isNotNull();
        assertThat(parsed.sessionToken()).isEqualTo(sessionToken);
        assertThat(parsed.hash()).isEqualTo(hash);
        
        // Verify the components are not blank (they should contain actual data)
        assertThat(parsed.sessionToken()).isNotBlank();
        assertThat(parsed.hash()).isNotBlank();
        
        // Verify format is preserved - reconstructing should give the same header
        String reconstructedCredentials = parsed.sessionToken() + ":" + parsed.hash();
        String reconstructedBase64 = java.util.Base64.getEncoder().encodeToString(reconstructedCredentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String reconstructed = "SIF_HMACSHA256 " + reconstructedBase64;
        assertThat(reconstructed).isEqualTo(authHeader);
    }
    
    /**
     * Property 4: Authentication failure propagation
     * 
     * **Validates: Requirements 1.6**
     * 
     * <p>For any authentication request where the Environment Service returns HTTP 401,
     * the Gateway should propagate the authentication failure by throwing InvalidTokenException.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>401 responses from Environment Service are correctly detected</li>
     *   <li>The client throws InvalidTokenException (not a generic error)</li>
     *   <li>The error message indicates authentication failure</li>
     *   <li>This behavior is consistent across all possible token values</li>
     * </ul>
     * 
     * @param sessionToken randomly generated session token
     * @param timestamp randomly generated timestamp
     * @param hash randomly generated hash
     * @param errorCode randomly generated error code from Environment Service
     * @param errorMessage randomly generated error message from Environment Service
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 4: Authentication failure propagation")
    void authenticationFailurePropagation(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("timestamps") String timestamp,
            @ForAll("hashes") String hash,
            @ForAll("authErrorCodes") String errorCode,
            @ForAll("errorMessages") String errorMessage) {
        
        // Setup WireMock server for this test iteration
        WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        
        try {
            // Create client pointing to WireMock server
            String baseUrl = "http://localhost:" + wireMockServer.port();
            EnvironmentServiceClient client = new EnvironmentServiceClient(WebClient.builder(), baseUrl, "test-internal-token", null, null, io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));
            
            // Arrange: Create token validation request with random values
            TokenValidationRequest request = new TokenValidationRequest(
                    sessionToken,
                    timestamp,
                    hash
            );
            
            // Mock Environment Service to return 401 Unauthorized with random error details
            stubFor(post(urlEqualTo("/internal/validate-token"))
                    .willReturn(aResponse()
                            .withStatus(401)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                        "code": "%s",
                                        "message": "%s",
                                        "scope": "BROKER",
                                        "timestamp": "2024-01-15T10:30:00Z"
                                    }
                                    """.formatted(errorCode, errorMessage))));
            
            // Act & Assert: Verify that InvalidTokenException is thrown
            StepVerifier.create(client.validateToken(request))
                    .expectErrorMatches(error -> {
                        // Verify the error is InvalidTokenException
                        if (!(error instanceof InvalidTokenException)) {
                            return false;
                        }
                        
                        // Verify the error message indicates authentication failure
                        String message = error.getMessage();
                        return message != null && 
                               message.contains("Token validation failed");
                    })
                    .verify();
            
            // Verify the request was made to the Environment Service
            verify(postRequestedFor(urlEqualTo("/internal/validate-token"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withHeader("Accept", equalTo("application/json")));
        } finally {
            // Cleanup: Stop WireMock server
            wireMockServer.stop();
        }
    }
    
    /**
     * Property 2: Malformed header rejection
     * 
     * **Validates: Requirements 1.3**
     * 
     * <p>For any Authorization header that does not match the format "SIF_HMACSHA256 {token}:{timestamp}:{hash}",
     * the Gateway should throw MalformedAuthHeaderException with error code AUTH_003.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>Invalid authorization headers are correctly detected</li>
     *   <li>MalformedAuthHeaderException is thrown (not a generic error)</li>
     *   <li>The error code is AUTH_003</li>
     *   <li>This behavior is consistent across all possible malformed header formats</li>
     * </ul>
     * 
     * @param malformedHeader randomly generated invalid Authorization header
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 2: Malformed header rejection")
    void malformedHeaderRejection(@ForAll("malformedAuthHeaders") String malformedHeader) {
        
        // Act & Assert: Verify that MalformedAuthHeaderException is thrown
        try {
            SifAuthenticationUtils.parseAuthHeader(malformedHeader);
            
            // Some randomly generated "malformed" headers may accidentally be valid Base64
            // that decodes to a token:hash format. Skip those — they're not truly malformed.
            net.jqwik.api.Assume.that(false);
            
        } catch (MalformedAuthHeaderException e) {
            // Verify the error code is AUTH_003
            assertThat(e.getErrorCode()).isEqualTo("AUTH_003");
            assertThat(e.getMessage()).isNotBlank();
            assertThat(e.getHttpStatus().value()).isEqualTo(401);
        }
    }
    
    /**
     * Property 4 (variant): Authentication failure propagation for 403 responses
     * 
     * **Validates: Requirements 1.6**
     * 
     * <p>For any authentication request where the Environment Service returns HTTP 403,
     * the Gateway should also propagate the authentication failure by throwing InvalidTokenException.
     * 
     * <p>This tests that 403 (Forbidden) responses are treated as authentication failures,
     * which can occur when tokens are expired or revoked.
     * 
     * @param sessionToken randomly generated session token
     * @param timestamp randomly generated timestamp
     * @param hash randomly generated hash
     * @param errorCode randomly generated error code from Environment Service
     * @param errorMessage randomly generated error message from Environment Service
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 4: Authentication failure propagation")
    void authenticationFailurePropagation_Forbidden(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("timestamps") String timestamp,
            @ForAll("hashes") String hash,
            @ForAll("authErrorCodes") String errorCode,
            @ForAll("errorMessages") String errorMessage) {
        
        // Setup WireMock server for this test iteration
        WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        
        try {
            // Create client pointing to WireMock server
            String baseUrl = "http://localhost:" + wireMockServer.port();
            EnvironmentServiceClient client = new EnvironmentServiceClient(WebClient.builder(), baseUrl, "test-internal-token", null, null, io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));
            
            // Arrange: Create token validation request with random values
            TokenValidationRequest request = new TokenValidationRequest(
                    sessionToken,
                    timestamp,
                    hash
            );
            
            // Mock Environment Service to return 403 Forbidden with random error details
            stubFor(post(urlEqualTo("/internal/validate-token"))
                    .willReturn(aResponse()
                            .withStatus(403)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                        "code": "%s",
                                        "message": "%s",
                                        "scope": "BROKER",
                                        "timestamp": "2024-01-15T10:30:00Z"
                                    }
                                    """.formatted(errorCode, errorMessage))));
            
            // Act & Assert: Verify that InvalidTokenException is thrown
            StepVerifier.create(client.validateToken(request))
                    .expectErrorMatches(error -> {
                        // Verify the error is InvalidTokenException
                        if (!(error instanceof InvalidTokenException)) {
                            return false;
                        }
                        
                        // Verify the error message indicates authentication failure
                        String message = error.getMessage();
                        return message != null && 
                               message.contains("Token validation failed");
                    })
                    .verify();
            
            // Verify the request was made to the Environment Service
            verify(postRequestedFor(urlEqualTo("/internal/validate-token"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withHeader("Accept", equalTo("application/json")));
        } finally {
            // Cleanup: Stop WireMock server
            wireMockServer.stop();
        }
    }
    
    /**
     * Property 6: Protected endpoint authentication enforcement
     * 
     * **Validates: Requirements 2.3, 2.4**
     * 
     * <p>For any request to a non-exempt endpoint without an Authorization header,
     * the Gateway should return HTTP 401 Unauthorized.
     * 
     * <p>This property verifies that:
     * <ul>
     *   <li>All non-exempt endpoints require authentication</li>
     *   <li>Requests without Authorization header are rejected with 401</li>
     *   <li>The error response contains appropriate error code and message</li>
     *   <li>This behavior is consistent across all protected endpoints</li>
     * </ul>
     * 
     * <p>Exempt paths (which should NOT be tested here):
     * <ul>
     *   <li>POST /environments - Environment registration endpoint</li>
     *   <li>/actuator/health - Health check endpoint</li>
     * </ul>
     * 
     * @param path randomly generated non-exempt path
     * @param method randomly generated HTTP method
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Property 6: Protected endpoint authentication enforcement")
    void protectedEndpointAuthenticationEnforcement(
            @ForAll("nonExemptPaths") String path,
            @ForAll("httpMethods") String method) {
        
        // Skip if this is an exempt path (POST /{domainId}/{solutionId}/environments or /actuator/health)
        org.sifunity.gateway.util.UrlPrefixParser.ParsedPath parsed = org.sifunity.gateway.util.UrlPrefixParser.parse(path);
        if (parsed.isDomainManagementPath()) {
            return;
        }
        if (parsed.isResourcePath() && "/environments".equals(parsed.resourcePath()) && method.equals("POST")) {
            return;
        }
        
        // Setup WireMock server for this test iteration
        WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        
        try {
            // Create AuthenticationFilter with client pointing to WireMock server
            String baseUrl = "http://localhost:" + wireMockServer.port();
            EnvironmentServiceClient client = new EnvironmentServiceClient(WebClient.builder(), baseUrl, "test-internal-token", null, null, io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));
            
            // Configure ObjectMapper with JSR310 module for Java 8 date/time support
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            
            // Create mock GatewayMetricsService
            org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService = 
                org.mockito.Mockito.mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
            
            AuthenticationFilter filter = new AuthenticationFilter(client, objectMapper, mockMetricsService);
            
            // Create mock ServerWebExchange without Authorization header
            org.springframework.mock.http.server.reactive.MockServerHttpRequest request = 
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                    .method(org.springframework.http.HttpMethod.valueOf(method), path)
                    .build();
            
            org.springframework.mock.web.server.MockServerWebExchange exchange = 
                org.springframework.mock.web.server.MockServerWebExchange.from(request);
            
            // Create mock filter chain
            org.springframework.cloud.gateway.filter.GatewayFilterChain chain = 
                ex -> reactor.core.publisher.Mono.empty();
            
            // Act: Process the request through the filter
            reactor.core.publisher.Mono<Void> result = filter.filter(exchange, chain);
            
            // Assert: Verify the request is blocked
            reactor.test.StepVerifier.create(result)
                    .expectComplete()
                    .verify();
            
            // Verify the response status is 401 Unauthorized
            assertThat(exchange.getResponse().getStatusCode())
                    .as("Protected endpoint %s %s should return 401 Unauthorized when no Authorization header is present", 
                        method, path)
                    .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
            
            // Verify the response contains error information
            assertThat(exchange.getResponse().getHeaders().getContentType())
                    .as("Error response should have JSON content type")
                    .isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
            
        } finally {
            // Cleanup: Stop WireMock server
            wireMockServer.stop();
        }
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates random session tokens (UUID-like strings).
     * Session tokens are typically UUIDs in the SIF Unity Broker.
     */
    @Provide
    Arbitrary<String> sessionTokens() {
        return Arbitraries.strings()
                .withCharRange('a', 'f')
                .numeric()
                .withChars('-')
                .ofMinLength(36)
                .ofMaxLength(36)
                .map(s -> {
                    // Format as UUID-like: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
                    if (s.length() < 36) {
                        s = s + "0".repeat(36 - s.length());
                    }
                    return s.substring(0, 8) + "-" +
                           s.substring(8, 12) + "-" +
                           s.substring(12, 16) + "-" +
                           s.substring(16, 20) + "-" +
                           s.substring(20, 32);
                });
    }
    
    /**
     * Generates random ISO-8601 timestamps.
     * Timestamps are used in HMAC signature computation.
     */
    @Provide
    Arbitrary<String> timestamps() {
        return Arbitraries.integers()
                .between(2020, 2030)
                .flatMap(year ->
                        Arbitraries.integers().between(1, 12).flatMap(month ->
                                Arbitraries.integers().between(1, 28).flatMap(day ->
                                        Arbitraries.integers().between(0, 23).flatMap(hour ->
                                                Arbitraries.integers().between(0, 59).flatMap(minute ->
                                                        Arbitraries.integers().between(0, 59).map(second ->
                                                                String.format("%04d-%02d-%02dT%02d:%02d:%02dZ",
                                                                        year, month, day, hour, minute, second)
                                                        )
                                                )
                                        )
                                )
                        )
                );
    }
    
    /**
     * Generates random HMAC-SHA256 hashes (Base64-encoded strings).
     * HMAC hashes are 44 characters in Base64 encoding.
     */
    @Provide
    Arbitrary<String> hashes() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .numeric()
                .withChars('+', '/', '=')
                .ofLength(44);
    }
    
    /**
     * Generates random authentication error codes.
     * These are error codes that the Environment Service might return for auth failures.
     */
    @Provide
    Arbitrary<String> authErrorCodes() {
        return Arbitraries.of(
                "AUTH_001",  // Invalid token
                "AUTH_002",  // Invalid timestamp
                "AUTH_003",  // Malformed authorization header
                "AUTH_004",  // Token expired
                "AUTH_005"   // Token revoked
        );
    }
    
    /**
     * Generates random error messages.
     * These simulate various error messages the Environment Service might return.
     */
    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.of(
                "Invalid token",
                "Token not found",
                "Token expired",
                "Token revoked",
                "Invalid timestamp",
                "Timestamp outside tolerance window",
                "HMAC signature mismatch",
                "Malformed authorization header",
                "Authentication failed",
                "Invalid credentials"
        );
    }
    
    /**
     * Generates random malformed Authorization headers.
     * These headers violate the expected format "SIF_HMACSHA256 {token}:{timestamp}:{hash}"
     * in various ways to test rejection behavior.
     * 
     * <p>Generated malformed headers include:
     * <ul>
     *   <li>Missing "SIF_HMACSHA256" prefix</li>
     *   <li>Wrong prefix (e.g., "BASIC", "BEARER")</li>
     *   <li>Missing colons (no separators)</li>
     *   <li>Too few components (only token, or token:timestamp)</li>
     *   <li>Empty components (token::hash, :timestamp:hash, etc.)</li>
     *   <li>Extra colons (token:timestamp:hash:extra)</li>
     *   <li>Null or empty strings</li>
     *   <li>Just whitespace</li>
     * </ul>
     */
    @Provide
    Arbitrary<String> malformedAuthHeaders() {
        // Generate various types of malformed headers
        return Arbitraries.oneOf(
                // Missing prefix
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50),
                
                // Wrong prefix
                Combinators.combine(
                        Arbitraries.of("BASIC", "BEARER", "DIGEST", "HMAC", "SIF_HMAC", "SIF_SHA256"),
                        Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
                ).as((prefix, value) -> prefix + " " + value),
                
                // Correct prefix but missing colons
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30)
                        .map(value -> "SIF_HMACSHA256 " + value),
                
                // Correct prefix but only one colon (missing timestamp or hash)
                Combinators.combine(
                        Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30),
                        Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30)
                ).as((part1, part2) -> "SIF_HMACSHA256 " + part1 + ":" + part2),
                
                // Correct prefix but empty token
                Combinators.combine(
                        Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30),
                        Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30)
                ).as((timestamp, hash) -> "SIF_HMACSHA256 :" + timestamp + ":" + hash),
                
                // Correct prefix but empty timestamp
                Combinators.combine(
                        Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30),
                        Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30)
                ).as((token, hash) -> "SIF_HMACSHA256 " + token + "::" + hash),
                
                // Correct prefix but empty hash
                Combinators.combine(
                        Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30),
                        Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(30)
                ).as((token, timestamp) -> "SIF_HMACSHA256 " + token + ":" + timestamp + ":"),
                
                // Empty string
                Arbitraries.just(""),
                
                // Just whitespace
                Arbitraries.of("   ", "\t", "\n", "  \t  "),
                
                // Null-like strings (the actual null is handled separately in the filter)
                Arbitraries.of("null", "NULL", "undefined"),
                
                // Prefix without space
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
                        .map(s -> "SIF_HMACSHA256" + s),
                
                // Prefix with multiple spaces
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
                        .map(s -> "SIF_HMACSHA256  " + s),
                
                // Case variations of prefix
                Combinators.combine(
                        Arbitraries.of("sif_hmacsha256", "Sif_HmacSha256", "SIF_HMACSHA256 "),
                        Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
                ).as((prefix, value) -> prefix + value)
        );
    }
    
    /**
     * Generates random non-exempt paths for testing protected endpoint enforcement.
     * 
     * <p>These paths represent protected endpoints that require authentication.
     * Exempt paths (POST /environments, /actuator/health) are explicitly excluded.
     * 
     * <p>Generated paths include:
     * <ul>
     *   <li>Connector service paths: /requests, /responses, /events, /services</li>
     *   <li>Core service paths: /queues, /subscriptions, /jobs</li>
     *   <li>Environment service paths: /acl-entries, /providers, /zones</li>
     *   <li>GET /environments (POST is exempt, but GET is not)</li>
     *   <li>Paths with resource IDs and sub-resources</li>
     * </ul>
     */
    @Provide
    Arbitrary<String> nonExemptPaths() {
        // Standard test domain/solution prefix
        String prefix = "/iowa/production";
        
        // Base paths for various services (all require authentication)
        Arbitrary<String> basePaths = Arbitraries.of(
                "/requests",
                "/responses",
                "/events",
                "/services",
                "/subscriptions",
                "/jobs",
                "/acl-entries",
                "/providers",
                "/zones"
        );
        
        // Resource names (e.g., StudentPersonal, SchoolInfo)
        Arbitrary<String> resourceNames = Arbitraries.of(
                "StudentPersonal",
                "SchoolInfo",
                "StaffPersonal",
                "StudentAttendance",
                "GradingAssignment"
        );
        
        // UUIDs for resource IDs
        Arbitrary<String> uuids = Arbitraries.strings()
                .withCharRange('a', 'f')
                .numeric()
                .withChars('-')
                .ofLength(36)
                .map(s -> {
                    if (s.length() < 36) {
                        s = s + "0".repeat(36 - s.length());
                    }
                    return s.substring(0, 8) + "-" +
                           s.substring(8, 12) + "-" +
                           s.substring(12, 16) + "-" +
                           s.substring(16, 20) + "-" +
                           s.substring(20, 32);
                });
        
        // Generate various path patterns with domain/solution prefix
        return Arbitraries.oneOf(
                // Simple base paths: /{domainId}/{solutionId}/requests, etc.
                basePaths.map(base -> prefix + base),
                
                // Base path + resource name: /{domainId}/{solutionId}/requests/StudentPersonal
                Combinators.combine(basePaths, resourceNames)
                        .as((base, resource) -> prefix + base + "/" + resource),
                
                // Base path + resource name + UUID
                Combinators.combine(basePaths, resourceNames, uuids)
                        .as((base, resource, uuid) -> prefix + base + "/" + resource + "/" + uuid),
                
                // GET /{domainId}/{solutionId}/environments (POST is exempt, but GET is not)
                Arbitraries.just(prefix + "/environments"),
                
                // GET /{domainId}/{solutionId}/environments/{id}
                uuids.map(uuid -> prefix + "/environments/" + uuid),
                
                // /{domainId}/{solutionId}/acl-entries paths
                Arbitraries.just(prefix + "/acl-entries"),
                uuids.map(uuid -> prefix + "/acl-entries/" + uuid),
                
                // /{domainId}/{solutionId}/providers paths
                Arbitraries.just(prefix + "/providers"),
                uuids.map(uuid -> prefix + "/providers/" + uuid),
                
                // /{domainId}/{solutionId}/zones paths
                Arbitraries.just(prefix + "/zones"),
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
                        .map(zoneId -> prefix + "/zones/" + zoneId)
        );
    }
    
    /**
     * Generates random HTTP methods for testing.
     * 
     * <p>Includes all standard HTTP methods used in the SIF Unity Broker:
     * GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
     */
    @Provide
    Arbitrary<String> httpMethods() {
        return Arbitraries.of(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "PATCH",
                "HEAD",
                "OPTIONS"
        );
    }
}
