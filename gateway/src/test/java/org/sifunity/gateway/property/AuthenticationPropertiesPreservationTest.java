// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import net.jqwik.api.*;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Tests for Authentication Properties Behavior
 * 
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on unfixed code.</strong>
 * These tests establish the baseline behavior that must be preserved after the fix.
 * 
 * <p><strong>Purpose:</strong>
 * These property-based tests verify that authentication properties validation
 * continues to work correctly for valid inputs after fixing the malformed header
 * rejection bug. They test:
 * <ul>
 *   <li>Valid authorization headers are correctly parsed</li>
 *   <li>Authentication failure propagation works correctly</li>
 *   <li>Protected endpoint authentication enforcement works correctly</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior:</strong>
 * All tests in this class should PASS on the unfixed code, confirming that the
 * authentication properties validation for valid inputs is correct and should be preserved.
 * 
 * <p><strong>Validates: Requirement 3.3</strong>
 * 
 * @see AuthenticationPropertiesTest
 * @see SifAuthenticationUtils
 */
class AuthenticationPropertiesPreservationTest {
    
    /**
     * Property 2.1: Preservation - Valid Authorization Header Parsing
     * 
     * <p><strong>Validates: Requirement 3.3</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that valid authorization headers in the format
     * "SIF_HMACSHA256 Base64(sessionToken:hash)" are correctly parsed and all
     * components are extracted properly.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Valid authorization headers are correctly parsed</li>
     *   <li>SessionToken and hash components are extracted correctly</li>
     *   <li>Extracted components match the original values</li>
     *   <li>Parsing is consistent across all valid header formats</li>
     * </ul>
     * 
     * @param sessionToken randomly generated session token
     * @param hash randomly generated hash
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Preservation: Valid Authorization Header Parsing")
    void validAuthorizationHeaderParsing(
            @ForAll("validSessionTokens") String sessionToken,
            @ForAll("validHashes") String hash) {
        
        // Arrange: Construct a valid Authorization header
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        // Act: Parse the authorization header
        SifAuthenticationUtils.AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(authHeader);
        
        // Assert: Verify all components are extracted correctly
        assertThat(parsed)
            .as("Parsed AuthHeader should not be null")
            .isNotNull();
        
        assertThat(parsed.sessionToken())
            .as("SessionToken should match the original value")
            .isEqualTo(sessionToken);
        
        assertThat(parsed.hash())
            .as("Hash should match the original value")
            .isEqualTo(hash);
        
        // Verify the components are not blank (they should contain actual data)
        assertThat(parsed.sessionToken())
            .as("SessionToken should not be blank")
            .isNotBlank();
        
        assertThat(parsed.hash())
            .as("Hash should not be blank")
            .isNotBlank();
        
        // Verify format is preserved - reconstructing should give the same header
        String reconstructedCredentials = parsed.sessionToken() + ":" + parsed.hash();
        String reconstructedBase64 = java.util.Base64.getEncoder()
            .encodeToString(reconstructedCredentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String reconstructed = "SIF_HMACSHA256 " + reconstructedBase64;
        
        assertThat(reconstructed)
            .as("Reconstructed header should match the original")
            .isEqualTo(authHeader);
    }

    
    /**
     * Property 2.2: Preservation - Authentication Failure Propagation (401)
     * 
     * <p><strong>Validates: Requirement 3.3</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that when the Environment Service returns HTTP 401,
     * the Gateway correctly propagates the authentication failure by throwing
     * InvalidTokenException with appropriate error details.
     * 
     * <p>This property verifies:
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
    @Property(tries = 50)
    @Label("Feature: gateway, Preservation: Authentication Failure Propagation (401)")
    void authenticationFailurePropagation401(
            @ForAll("validSessionTokens") String sessionToken,
            @ForAll("validTimestamps") String timestamp,
            @ForAll("validHashes") String hash,
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
     * Property 2.3: Preservation - Authentication Failure Propagation (403)
     * 
     * <p><strong>Validates: Requirement 3.3</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that when the Environment Service returns HTTP 403,
     * the Gateway correctly propagates the authentication failure by throwing
     * InvalidTokenException. 403 responses can occur when tokens are expired or revoked.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>403 responses from Environment Service are correctly detected</li>
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
    @Property(tries = 50)
    @Label("Feature: gateway, Preservation: Authentication Failure Propagation (403)")
    void authenticationFailurePropagation403(
            @ForAll("validSessionTokens") String sessionToken,
            @ForAll("validTimestamps") String timestamp,
            @ForAll("validHashes") String hash,
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
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates valid session tokens (UUID-like strings).
     * Session tokens are typically UUIDs in the SIF Unity Broker.
     * These are well-formed tokens without null bytes or control characters.
     */
    @Provide
    Arbitrary<String> validSessionTokens() {
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
     * Generates valid ISO-8601 timestamps.
     * Timestamps are used in HMAC signature computation.
     * These are well-formed timestamps without null bytes or control characters.
     */
    @Provide
    Arbitrary<String> validTimestamps() {
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
     * Generates valid HMAC-SHA256 hashes (Base64-encoded strings).
     * HMAC hashes are 44 characters in Base64 encoding.
     * These are well-formed hashes without null bytes or control characters.
     */
    @Provide
    Arbitrary<String> validHashes() {
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
}
