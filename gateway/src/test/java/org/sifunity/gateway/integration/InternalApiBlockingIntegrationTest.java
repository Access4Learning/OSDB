// SPDX-License-Identifier: MIT
package org.sifunity.gateway.integration;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration test for internal API blocking.
 * 
 * <p>This test validates that requests to internal APIs (/internal/*) are blocked
 * with HTTP 403 Forbidden, regardless of authentication status.</p>
 * 
 * <p>Internal APIs should only be accessible within the cluster, not through
 * the Gateway from external clients.</p>
 * 
 * <p>Requirements: 6.1, 6.2</p>
 */
class InternalApiBlockingIntegrationTest extends BaseIntegrationTest {
    
    @Test
    void internalValidateToken_shouldBeBlocked() {
        // Arrange - Try to access internal validate-token endpoint
        
        // Act & Assert
        webTestClient.post()
            .uri("/internal/validate-token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isForbidden();
        
        // Verify the request was NOT routed to Environment Service
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
    
    @Test
    void internalCheckPermission_shouldBeBlocked() {
        // Arrange - Try to access internal check-permission endpoint
        
        // Act & Assert
        webTestClient.post()
            .uri("/internal/check-permission")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isForbidden();
        
        // Verify the request was NOT routed to Environment Service
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/check-permission")));
    }
    
    @Test
    void internalApiWithAuthentication_shouldStillBeBlocked() throws Exception {
        // Arrange - Even with valid authentication, internal APIs should be blocked
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
            null,
            null,
            null
        );
        
        // Mock Environment Service (though it shouldn't be called)
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(validationResponse))));
        
        // Act & Assert
        webTestClient.post()
            .uri("/internal/validate-token")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isForbidden();
        
        // Verify the request was NOT routed (blocked before authentication)
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
    
    @Test
    void internalApiSubpaths_shouldBeBlocked() {
        // Arrange - Test various internal API paths
        
        // Act & Assert
        webTestClient.get()
            .uri("/internal/some-endpoint")
            .exchange()
            .expectStatus().isForbidden();
        
        webTestClient.post()
            .uri("/internal/another/nested/endpoint")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isForbidden();
        
        webTestClient.put()
            .uri("/internal/update")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isForbidden();
        
        webTestClient.delete()
            .uri("/internal/delete")
            .exchange()
            .expectStatus().isForbidden();
    }
    
    @Test
    void publicEndpoints_shouldNotBeBlocked() {
        // Arrange - Verify that non-internal endpoints are not affected
        
        // Act & Assert - POST /{domainId}/{solutionId}/environments should work (exempt from auth)
        webTestClient.post()
            .uri("/iowa/production/environments")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no backend, but not 403
        
        // Act & Assert - Health check should work
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }
    
    @Test
    void pathsStartingWithInternal_shouldBeBlocked() {
        // Arrange - Test that the blocking is based on path prefix
        
        // Act & Assert
        webTestClient.get()
            .uri("/internal")
            .exchange()
            .expectStatus().isForbidden();
        
        webTestClient.get()
            .uri("/internal/")
            .exchange()
            .expectStatus().isForbidden();
    }
}
