// SPDX-License-Identifier: MIT
package org.sifunity.gateway.integration;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.PermissionCheckResponse;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration test for the full authentication and authorization flow.
 * 
 * <p>This test validates the complete request processing pipeline:</p>
 * <ol>
 *   <li>CorrelationIdFilter generates/preserves correlation ID</li>
 *   <li>AuthenticationFilter validates token via Environment Service</li>
 *   <li>AuthorizationFilter checks permissions via Environment Service</li>
 *   <li>EnvironmentContextFilter injects environment headers</li>
 *   <li>Request is routed to backend service</li>
 * </ol>
 * 
 * <p>Uses WireMock to mock the Environment Service responses.</p>
 * 
 * <p>Requirements: 1.1, 1.2, 1.4, 1.5, 3.8, 3.9, 4.1, 4.2, 4.3</p>
 */
class AuthenticationFlowIntegrationTest extends BaseIntegrationTest {
    
    /** Standard test domain/solution prefix for resource paths. */
    private static final String PREFIX = "/iowa/production";
    
    @Test
    void fullAuthenticationFlow_shouldInjectAllHeaders() throws Exception {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String applicationName = "TestApp";
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = "2024-01-01T00:00:00Z";
        String hash = "test-hash";
        
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        TokenValidationResponse validationResponse = new TokenValidationResponse(
            environmentId,
            applicationName,
            EnvironmentType.BROKERED,
            "default",
            null,
            "iowa",
            "production",
            null
        );
        
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        
        // Mock Environment Service validate-token endpoint
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(validationResponse))));
        
        // Mock Environment Service check-permission endpoint
        wireMockServer.stubFor(post(urlPathEqualTo("/internal/check-permission"))
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
            .expectStatus().isNotFound() // 404 because no backend service is running
            .expectHeader().exists(SifHeaders.X_CORRELATION_ID)
            .expectHeader().valueEquals(SifHeaders.X_ENVIRONMENT_ID, environmentId.toString())
            .expectHeader().valueEquals(SifHeaders.X_ENVIRONMENT_NAME, applicationName);
        
        // Verify Environment Service was called
        wireMockServer.verify(postRequestedFor(urlEqualTo("/internal/validate-token")));
        wireMockServer.verify(postRequestedFor(urlEqualTo("/internal/check-permission")));
    }
    
    @Test
    void fullAuthenticationFlow_shouldPreserveExistingCorrelationId() throws Exception {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String applicationName = "TestApp";
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = "2024-01-01T00:00:00Z";
        String hash = "test-hash";
        
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        String existingCorrelationId = UUID.randomUUID().toString();
        
        TokenValidationResponse validationResponse = new TokenValidationResponse(
            environmentId,
            applicationName,
            EnvironmentType.BROKERED,
            "default",
            null,
            "iowa",
            "production",
            null
        );
        
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        
        // Mock Environment Service endpoints
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(validationResponse))));
        
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
            .header(SifHeaders.X_CORRELATION_ID, existingCorrelationId)
            .exchange()
            .expectStatus().isNotFound() // 404 because no backend service is running
            .expectHeader().valueEquals(SifHeaders.X_CORRELATION_ID, existingCorrelationId);
    }
    
    @Test
    void fullAuthenticationFlow_shouldUseDefaultZoneWhenNotProvided() throws Exception {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String applicationName = "TestApp";
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = "2024-01-01T00:00:00Z";
        String hash = "test-hash";
        
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        String defaultZone = "default-zone";
        
        TokenValidationResponse validationResponse = new TokenValidationResponse(
            environmentId,
            applicationName,
            EnvironmentType.BROKERED,
            defaultZone,
            null,
            "iowa",
            "production",
            null
        );
        
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        
        // Mock Environment Service endpoints
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(validationResponse))));
        
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
            // No zone header provided - should use default
            .exchange()
            .expectStatus().isNotFound(); // 404 because no backend service is running
        
        // Verify the permission check was called with the default zone
        wireMockServer.verify(postRequestedFor(urlEqualTo("/internal/check-permission"))
            .withRequestBody(matchingJsonPath("$.zoneId", equalTo(defaultZone))));
    }
    
    @Test
    void fullAuthenticationFlow_shouldDetermineCorrectAccessType() throws Exception {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String applicationName = "TestApp";
        String sessionToken = UUID.randomUUID().toString();
        String timestamp = "2024-01-01T00:00:00Z";
        String hash = "test-hash";
        
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;
        
        TokenValidationResponse validationResponse = new TokenValidationResponse(
            environmentId,
            applicationName,
            EnvironmentType.BROKERED,
            "default",
            null,
            "iowa",
            "production",
            null
        );
        
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        
        // Mock Environment Service endpoints
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(validationResponse))));
        
        wireMockServer.stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(permissionResponse))));
        
        // Act & Assert - GET /requests should use CONSUME access type
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.ZONE_ID, "default")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no backend service is running
        
        // Verify the permission check was called with CONSUME access type
        wireMockServer.verify(postRequestedFor(urlEqualTo("/internal/check-permission"))
            .withRequestBody(matchingJsonPath("$.accessType", equalTo(AccessType.QUERY.name()))));
    }
}
