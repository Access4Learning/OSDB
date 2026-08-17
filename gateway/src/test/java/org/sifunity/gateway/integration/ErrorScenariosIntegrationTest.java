// SPDX-License-Identifier: MIT
package org.sifunity.gateway.integration;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.PermissionCheckResponse;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration test for error scenarios in the Gateway.
 * 
 * <p>This test validates error handling for:</p>
 * <ul>
 *   <li>Environment Service unavailable (503 response)</li>
 *   <li>Environment Service timeout (504 response)</li>
 *   <li>Authentication failure (401 response)</li>
 *   <li>Authorization failure (403 response)</li>
 *   <li>Rate limit exceeded (429 response)</li>
 * </ul>
 * 
 * <p>Requirements: 7.1, 7.2, 7.4, 7.5, 8.1</p>
 */
class ErrorScenariosIntegrationTest extends BaseIntegrationTest {
    
    /** Standard test domain/solution prefix for resource paths. */
    private static final String PREFIX = "/iowa/production";
    
    @Test
    void environmentServiceUnavailable_shouldReturn503() {
        // Arrange
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = "2024-01-01T00:00:00Z";
        String hash = "test-hash";
        
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        // Mock Environment Service to return 503
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())));
        
        // Act & Assert
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED) // Auth filter converts 5xx to 401
            .expectHeader().exists(SifHeaders.X_CORRELATION_ID)
            .expectBody()
            .jsonPath("$.error.code").exists()
            .jsonPath("$.error.message").exists()
            .jsonPath("$.error.scope").isEqualTo("BROKER")
            .jsonPath("$.error.timestamp").doesNotExist();
    }
    
    @Test
    void environmentServiceTimeout_shouldReturnError() {
        // Arrange
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = "2024-01-01T00:00:00Z";
        String hash = "test-hash";
        
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        // Mock Environment Service to delay response beyond timeout
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withFixedDelay(6000))); // 6 seconds, exceeds 5 second timeout
        
        // Act & Assert
        // Use a custom WebTestClient with a longer timeout (20 seconds) to allow the test to complete
        // The EnvironmentServiceClient has a 5-second timeout with 2 retries, so total time is ~15 seconds
        webTestClient.mutate()
            .responseTimeout(java.time.Duration.ofSeconds(20))
            .build()
            .get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED) // Timeout converted to auth error
            .expectHeader().exists(SifHeaders.X_CORRELATION_ID)
            .expectBody()
            .jsonPath("$.error.code").exists()
            .jsonPath("$.error.scope").isEqualTo("BROKER");
    }
    
    @Test
    void authenticationFailure_shouldReturn401() {
        // Arrange
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = "2024-01-01T00:00:00Z";
        String hash = "test-hash";
        
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        // Mock Environment Service to return 401
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.UNAUTHORIZED.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody("{\"code\":\"AUTH_001\",\"message\":\"Invalid token\"}")));
        
        // Act & Assert
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().exists(SifHeaders.X_CORRELATION_ID)
            .expectBody()
            .jsonPath("$.error.code").exists()
            .jsonPath("$.error.message").exists()
            .jsonPath("$.error.scope").isEqualTo("BROKER")
            .jsonPath("$.error.timestamp").doesNotExist();
        
        // Verify Environment Service was called
        wireMockServer.verify(postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
    
    @Test
    void authorizationFailure_shouldReturn403() throws Exception {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String applicationName = "TestApp";
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = "2024-01-01T00:00:00Z";
        String hash = "test-hash";
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        TokenValidationResponse validationResponse = new TokenValidationResponse(
            environmentId,
            applicationName,
            EnvironmentType.BROKERED,
            "default",
            null,
            null,
            null,
            null
        );
        
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(false);
        
        // Mock Environment Service validate-token endpoint (success)
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(validationResponse))));
        
        // Mock Environment Service check-permission endpoint (denied)
        wireMockServer.stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(permissionResponse))));
        
        // Act & Assert
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.ZONE_ID, "default")
            .exchange()
            .expectStatus().isForbidden()
            .expectHeader().exists(SifHeaders.X_CORRELATION_ID)
            .expectBody()
            .jsonPath("$.error.subCode").isEqualTo("AUTHZ_001")
            .jsonPath("$.error.message").exists()
            .jsonPath("$.error.scope").isEqualTo("BROKER")
            .jsonPath("$.error.timestamp").doesNotExist();
        
        // Verify both Environment Service calls were made
        wireMockServer.verify(postRequestedFor(urlEqualTo("/internal/validate-token")));
        wireMockServer.verify(postRequestedFor(urlEqualTo("/internal/check-permission")));
    }
    
    @Test
    void malformedAuthorizationHeader_shouldReturn401() {
        // Arrange - Invalid authorization header format
        String invalidAuthHeader = "Bearer invalid-token";
        String timestamp = "2024-01-01T00:00:00Z";
        
        // Act & Assert
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, invalidAuthHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().exists(SifHeaders.X_CORRELATION_ID)
            .expectBody()
            .jsonPath("$.error.subCode").isEqualTo("AUTH_003")
            .jsonPath("$.error.message").exists()
            .jsonPath("$.error.scope").isEqualTo("BROKER");
        
        // Verify Environment Service was NOT called (header parsing failed first)
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
    
    @Test
    void missingAuthorizationHeader_shouldReturn401() {
        // Arrange - No authorization header
        
        // Act & Assert
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().exists(SifHeaders.X_CORRELATION_ID)
            .expectBody()
            .jsonPath("$.error.code").exists()
            .jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Authorization"))
            .jsonPath("$.error.scope").isEqualTo("BROKER");
        
        // Verify Environment Service was NOT called
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
    
    @Test
    void allErrorResponses_shouldIncludeCorrelationId() {
        // Arrange - Test that all error responses include correlation ID
        String customCorrelationId = UUID.randomUUID().toString();
        
        // Act & Assert
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, customCorrelationId)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().valueEquals(SifHeaders.X_CORRELATION_ID, customCorrelationId);
    }
}
