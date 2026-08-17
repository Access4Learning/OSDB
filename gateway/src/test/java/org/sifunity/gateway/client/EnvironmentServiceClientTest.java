// SPDX-License-Identifier: MIT
package org.sifunity.gateway.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.dto.PermissionCheckRequest;
import org.sifunity.broker.common.dto.PermissionCheckResponse;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.sifunity.broker.common.exception.InsufficientPermissionsException;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Unit tests for EnvironmentServiceClient.
 * Uses WireMock to simulate Environment Service responses.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Successful token validation</li>
 *   <li>Successful permission check</li>
 *   <li>Error handling (401, 403, 500)</li>
 *   <li>Timeout handling</li>
 *   <li>Retry behavior on transient failures</li>
 * </ul>
 * 
 * <p>Requirements: 1.4, 3.8
 */
@DisplayName("EnvironmentServiceClient Tests")
class EnvironmentServiceClientTest {
    
    private WireMockServer wireMockServer;
    private EnvironmentServiceClient client;
    
    @BeforeEach
    void setUp() {
        // Start WireMock server on a random port
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        
        // Create client pointing to WireMock server
        String baseUrl = "http://localhost:" + wireMockServer.port();
        client = new EnvironmentServiceClient(WebClient.builder(), baseUrl, "test-internal-token", null, null, io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));
    }
    
    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }
    
    // ========== Token Validation Tests ==========
    
    @Test
    @DisplayName("validateToken should return environment details on successful validation")
    void validateToken_Success() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = "2024-01-15T10:30:00Z";
        String hash = "abc123hash";
        
        TokenValidationRequest request = new TokenValidationRequest(
            sessionToken,
            timestamp,
            hash
        );
        
        // Mock successful response
        stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "environmentId": "%s",
                        "applicationName": "TestApp",
                        "type": "BROKERED",
                        "defaultZoneId": "default"
                    }
                    """.formatted(environmentId))));
        
        // Act & Assert
        StepVerifier.create(client.validateToken(request))
            .expectNextMatches(response ->
                response.environmentId().equals(environmentId) &&
                response.applicationName().equals("TestApp") &&
                response.type() == EnvironmentType.BROKERED &&
                response.defaultZoneId().equals("default")
            )
            .verifyComplete();
        
        // Verify the request was made
        verify(postRequestedFor(urlEqualTo("/internal/validate-token"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json")));
    }
    
    @Test
    @DisplayName("validateToken should throw InvalidTokenException on 401 response")
    void validateToken_Unauthorized() {
        // Arrange
        TokenValidationRequest request = new TokenValidationRequest(
            "invalid-token",
            "2024-01-15T10:30:00Z",
            "invalid-hash"
        );
        
        // Mock 401 response
        stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "AUTH_001",
                        "message": "Invalid token",
                        "scope": "BROKER"
                    }
                    """)));
        
        // Act & Assert
        StepVerifier.create(client.validateToken(request))
            .expectErrorMatches(error ->
                error instanceof InvalidTokenException &&
                error.getMessage().contains("Token validation failed")
            )
            .verify();
    }
    
    @Test
    @DisplayName("validateToken should throw InvalidTokenException on 403 response")
    void validateToken_Forbidden() {
        // Arrange
        TokenValidationRequest request = new TokenValidationRequest(
            "forbidden-token",
            "2024-01-15T10:30:00Z",
            "some-hash"
        );
        
        // Mock 403 response
        stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "AUTH_003",
                        "message": "Token expired",
                        "scope": "BROKER"
                    }
                    """)));
        
        // Act & Assert
        StepVerifier.create(client.validateToken(request))
            .expectErrorMatches(error ->
                error instanceof InvalidTokenException &&
                error.getMessage().contains("Token validation failed")
            )
            .verify();
    }
    
    @Test
    @DisplayName("validateToken should throw ServiceUnavailableException on 500 response")
    void validateToken_ServerError() {
        // Arrange
        TokenValidationRequest request = new TokenValidationRequest(
            "some-token",
            "2024-01-15T10:30:00Z",
            "some-hash"
        );
        
        // Mock 500 response - all retries will fail
        stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "SERVICE_002",
                        "message": "Internal server error",
                        "scope": "BROKER"
                    }
                    """)));
        
        // Act & Assert - After retries are exhausted, the error is wrapped
        StepVerifier.create(client.validateToken(request))
            .expectErrorMatches(error -> {
                // Check if it's a retry exhausted error with ServiceUnavailableException as cause
                if (error.getMessage() != null && error.getMessage().contains("Retries exhausted")) {
                    Throwable cause = error.getCause();
                    return cause instanceof ServiceUnavailableException &&
                           cause.getMessage().contains("Token validation service unavailable");
                }
                return false;
            })
            .verify();
    }
    
    @Test
    @DisplayName("validateToken should throw ServiceUnavailableException on 503 response")
    void validateToken_ServiceUnavailable() {
        // Arrange
        TokenValidationRequest request = new TokenValidationRequest(
            "some-token",
            "2024-01-15T10:30:00Z",
            "some-hash"
        );
        
        // Mock 503 response - all retries will fail
        stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "SERVICE_002",
                        "message": "Service temporarily unavailable",
                        "scope": "BROKER"
                    }
                    """)));
        
        // Act & Assert - After retries are exhausted, the error is wrapped
        StepVerifier.create(client.validateToken(request))
            .expectErrorMatches(error -> {
                // Check if it's a retry exhausted error with ServiceUnavailableException as cause
                if (error.getMessage() != null && error.getMessage().contains("Retries exhausted")) {
                    Throwable cause = error.getCause();
                    return cause instanceof ServiceUnavailableException &&
                           cause.getMessage().contains("Token validation service unavailable");
                }
                return false;
            })
            .verify();
    }
    
    @Test
    @DisplayName("validateToken should timeout after 5 seconds")
    void validateToken_Timeout() {
        // Arrange
        TokenValidationRequest request = new TokenValidationRequest(
            "some-token",
            "2024-01-15T10:30:00Z",
            "some-hash"
        );
        
        // Mock delayed response (6 seconds, exceeding 5 second timeout)
        stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "environmentId": "00000000-0000-0000-0000-000000000000",
                        "applicationName": "TestApp",
                        "type": "BROKERED",
                        "defaultZoneId": "default"
                    }
                    """)
                .withFixedDelay(6000)));
        
        // Act & Assert - Timeout will occur, then retries will also timeout
        StepVerifier.create(client.validateToken(request))
            .expectErrorMatches(error -> {
                // After retries, the timeout is wrapped
                if (error.getMessage() != null && error.getMessage().contains("Retries exhausted")) {
                    Throwable cause = error.getCause();
                    return cause instanceof java.util.concurrent.TimeoutException;
                }
                return error instanceof java.util.concurrent.TimeoutException;
            })
            .verify(Duration.ofSeconds(20)); // Allow enough time for initial + 2 retries
    }
    
    @Test
    @DisplayName("validateToken should retry on transient failures (500 errors)")
    void validateToken_RetryOnTransientFailure() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        TokenValidationRequest request = new TokenValidationRequest(
            "some-token",
            "2024-01-15T10:30:00Z",
            "some-hash"
        );
        
        // Mock: First two calls fail with 500, third succeeds
        stubFor(post(urlEqualTo("/internal/validate-token"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "SERVICE_002",
                        "message": "Temporary error",
                        "scope": "BROKER"
                    }
                    """))
            .willSetStateTo("First Retry"));
        
        stubFor(post(urlEqualTo("/internal/validate-token"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("First Retry")
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "SERVICE_002",
                        "message": "Temporary error",
                        "scope": "BROKER"
                    }
                    """))
            .willSetStateTo("Second Retry"));
        
        stubFor(post(urlEqualTo("/internal/validate-token"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("Second Retry")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "environmentId": "%s",
                        "applicationName": "TestApp",
                        "type": "BROKERED",
                        "defaultZoneId": "default"
                    }
                    """.formatted(environmentId))));
        
        // Act & Assert
        StepVerifier.create(client.validateToken(request))
            .expectNextMatches(response ->
                response.environmentId().equals(environmentId) &&
                response.applicationName().equals("TestApp")
            )
            .verifyComplete();
        
        // Verify 3 requests were made (initial + 2 retries)
        verify(3, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
    
    @Test
    @DisplayName("validateToken should NOT retry on 401 errors (client errors)")
    void validateToken_NoRetryOn401() {
        // Arrange
        TokenValidationRequest request = new TokenValidationRequest(
            "invalid-token",
            "2024-01-15T10:30:00Z",
            "invalid-hash"
        );
        
        // Mock 401 response
        stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "AUTH_001",
                        "message": "Invalid token",
                        "scope": "BROKER"
                    }
                    """)));
        
        // Act & Assert
        StepVerifier.create(client.validateToken(request))
            .expectError(InvalidTokenException.class)
            .verify();
        
        // Verify only 1 request was made (no retries)
        verify(1, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
    
    // ========== Permission Check Tests ==========
    
    @Test
    @DisplayName("checkPermission should return granted=true on successful check")
    void checkPermission_Success_Granted() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId,
            "StudentPersonal",
            "default",
            "DEFAULT",
            AccessType.QUERY
        );
        
        // Mock successful response with permission granted
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "granted": true
                    }
                    """)));
        
        // Act & Assert
        StepVerifier.create(client.checkPermission(request))
            .expectNextMatches(response -> response.granted())
            .verifyComplete();
        
        // Verify the request was made
        verify(postRequestedFor(urlEqualTo("/internal/check-permission"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json")));
    }
    
    @Test
    @DisplayName("checkPermission should return granted=false when permission denied")
    void checkPermission_Success_Denied() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId,
            "StudentPersonal",
            "default",
            "DEFAULT",
            AccessType.PROVIDE
        );
        
        // Mock successful response with permission denied
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "granted": false
                    }
                    """)));
        
        // Act & Assert
        StepVerifier.create(client.checkPermission(request))
            .expectNextMatches(response -> !response.granted())
            .verifyComplete();
    }
    
    @Test
    @DisplayName("checkPermission should throw InsufficientPermissionsException on 403 response")
    void checkPermission_Forbidden() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId,
            "StudentPersonal",
            "default",
            "DEFAULT",
            AccessType.QUERY
        );
        
        // Mock 403 response
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "AUTHZ_001",
                        "message": "Permission denied",
                        "scope": "BROKER"
                    }
                    """)));
        
        // Act & Assert
        StepVerifier.create(client.checkPermission(request))
            .expectErrorMatches(error ->
                error instanceof InsufficientPermissionsException &&
                error.getMessage().contains("Permission denied for service: StudentPersonal")
            )
            .verify();
    }
    
    @Test
    @DisplayName("checkPermission should throw InsufficientPermissionsException on 401 response")
    void checkPermission_Unauthorized() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId,
            "StudentPersonal",
            "default",
            "DEFAULT",
            AccessType.QUERY
        );
        
        // Mock 401 response (environment not found or invalid)
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "AUTH_001",
                        "message": "Environment not found",
                        "scope": "BROKER"
                    }
                    """)));
        
        // Act & Assert
        StepVerifier.create(client.checkPermission(request))
            .expectErrorMatches(error ->
                error instanceof InsufficientPermissionsException &&
                error.getMessage().contains("Permission denied for service: StudentPersonal")
            )
            .verify();
    }
    
    @Test
    @DisplayName("checkPermission should throw ServiceUnavailableException on 500 response")
    void checkPermission_ServerError() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId,
            "StudentPersonal",
            "default",
            "DEFAULT",
            AccessType.QUERY
        );
        
        // Mock 500 response - all retries will fail
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "SERVICE_002",
                        "message": "Internal server error",
                        "scope": "BROKER"
                    }
                    """)));
        
        // Act & Assert - After retries are exhausted, the error is wrapped
        StepVerifier.create(client.checkPermission(request))
            .expectErrorMatches(error -> {
                // Check if it's a retry exhausted error with ServiceUnavailableException as cause
                if (error.getMessage() != null && error.getMessage().contains("Retries exhausted")) {
                    Throwable cause = error.getCause();
                    return cause instanceof ServiceUnavailableException &&
                           cause.getMessage().contains("Permission check service unavailable");
                }
                return false;
            })
            .verify();
    }
    
    @Test
    @DisplayName("checkPermission should throw ServiceUnavailableException on 503 response")
    void checkPermission_ServiceUnavailable() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId,
            "StudentPersonal",
            "default",
            "DEFAULT",
            AccessType.QUERY
        );
        
        // Mock 503 response - all retries will fail
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "SERVICE_002",
                        "message": "Service temporarily unavailable",
                        "scope": "BROKER"
                    }
                    """)));
        
        // Act & Assert - After retries are exhausted, the error is wrapped
        StepVerifier.create(client.checkPermission(request))
            .expectErrorMatches(error -> {
                // Check if it's a retry exhausted error with ServiceUnavailableException as cause
                if (error.getMessage() != null && error.getMessage().contains("Retries exhausted")) {
                    Throwable cause = error.getCause();
                    return cause instanceof ServiceUnavailableException &&
                           cause.getMessage().contains("Permission check service unavailable");
                }
                return false;
            })
            .verify();
    }
    
    @Test
    @DisplayName("checkPermission should timeout after 5 seconds")
    void checkPermission_Timeout() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId,
            "StudentPersonal",
            "default",
            "DEFAULT",
            AccessType.QUERY
        );
        
        // Mock delayed response (6 seconds, exceeding 5 second timeout)
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "granted": true
                    }
                    """)
                .withFixedDelay(6000)));
        
        // Act & Assert - Timeout will occur, then retries will also timeout
        StepVerifier.create(client.checkPermission(request))
            .expectErrorMatches(error -> {
                // After retries, the timeout is wrapped
                if (error.getMessage() != null && error.getMessage().contains("Retries exhausted")) {
                    Throwable cause = error.getCause();
                    return cause instanceof java.util.concurrent.TimeoutException;
                }
                return error instanceof java.util.concurrent.TimeoutException;
            })
            .verify(Duration.ofSeconds(20)); // Allow enough time for initial + 2 retries
    }
    
    @Test
    @DisplayName("checkPermission should retry on transient failures (500 errors)")
    void checkPermission_RetryOnTransientFailure() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId,
            "StudentPersonal",
            "default",
            "DEFAULT",
            AccessType.QUERY
        );
        
        // Mock: First two calls fail with 500, third succeeds
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .inScenario("Permission Retry Scenario")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "SERVICE_002",
                        "message": "Temporary error",
                        "scope": "BROKER"
                    }
                    """))
            .willSetStateTo("First Retry"));
        
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .inScenario("Permission Retry Scenario")
            .whenScenarioStateIs("First Retry")
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "SERVICE_002",
                        "message": "Temporary error",
                        "scope": "BROKER"
                    }
                    """))
            .willSetStateTo("Second Retry"));
        
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .inScenario("Permission Retry Scenario")
            .whenScenarioStateIs("Second Retry")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "granted": true
                    }
                    """)));
        
        // Act & Assert
        StepVerifier.create(client.checkPermission(request))
            .expectNextMatches(response -> response.granted())
            .verifyComplete();
        
        // Verify 3 requests were made (initial + 2 retries)
        verify(3, postRequestedFor(urlEqualTo("/internal/check-permission")));
    }
    
    @Test
    @DisplayName("checkPermission should NOT retry on 403 errors (client errors)")
    void checkPermission_NoRetryOn403() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId,
            "StudentPersonal",
            "default",
            "DEFAULT",
            AccessType.QUERY
        );
        
        // Mock 403 response
        stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "AUTHZ_001",
                        "message": "Permission denied",
                        "scope": "BROKER"
                    }
                    """)));
        
        // Act & Assert
        StepVerifier.create(client.checkPermission(request))
            .expectError(InsufficientPermissionsException.class)
            .verify();
        
        // Verify only 1 request was made (no retries)
        verify(1, postRequestedFor(urlEqualTo("/internal/check-permission")));
    }
}
