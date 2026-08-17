// SPDX-License-Identifier: MIT
package org.sifunity.gateway.integration;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.SifHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration test for authentication exemptions.
 * 
 * <p>This test validates that specific endpoints can be accessed without authentication:</p>
 * <ul>
 *   <li>POST /environments - Environment registration endpoint</li>
 *   <li>/actuator/health - Health check endpoint</li>
 * </ul>
 * 
 * <p>Requirements: 2.1, 2.2</p>
 */
class AuthenticationExemptionsIntegrationTest extends BaseIntegrationTest {
    
    /** Standard test domain/solution prefix for resource paths. */
    private static final String PREFIX = "/iowa/production";
    
    @Test
    void postEnvironments_shouldWorkWithoutAuthentication() {
        // Arrange - No authentication header provided
        String requestBody = """
            {
                "applicationName": "TestApp",
                "type": "BROKERED"
            }
            """;
        
        // Mock the backend environment service to return a response
        wireMockServer.stubFor(post(urlPathMatching("/iowa/production/environments"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.CREATED.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody("{\"id\":\"test-id\"}")));
        
        // Act & Assert
        webTestClient.post()
            .uri(PREFIX + "/environments")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isCreated(); // 201 because backend service is mocked
        
        // Verify Environment Service was NOT called for authentication
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/validate-token")));
        
        // Verify the request was routed to the backend
        wireMockServer.verify(1, postRequestedFor(urlPathMatching("/iowa/production/environments")));
    }
    
    @Test
    void getEnvironments_shouldRequireAuthentication() {
        // Arrange - No authentication header provided
        
        // Act & Assert
        webTestClient.get()
            .uri(PREFIX + "/environments")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().exists(SifHeaders.X_CORRELATION_ID)
            .expectBody()
            .jsonPath("$.error.subCode").isEqualTo("AUTH_001")
            .jsonPath("$.error.message").exists()
            .jsonPath("$.error.scope").isEqualTo("BROKER");
        
        // Verify Environment Service was NOT called
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
    
    @Test
    void actuatorHealth_shouldWorkWithoutAuthentication() {
        // Arrange - No authentication header provided

        // Act & Assert - Health check should work without authentication
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk() // Health endpoint returns 200 OK
            .expectBody()
            .jsonPath("$.status").exists();

        // Verify Environment Service was NOT called for authentication
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }

    
    @Test
    void actuatorHealthLiveness_shouldWorkWithoutAuthentication() {
        // Arrange - No authentication header provided
        
        // Act & Assert
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").exists();
        
        // Verify Environment Service was NOT called for authentication
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
    
    @Test
    void actuatorHealthReadiness_shouldWorkWithoutAuthentication() {
        // Arrange - No authentication header provided
        
        // Act & Assert
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").exists();
        
        // Verify Environment Service was NOT called for authentication
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
    
    @Test
    void otherEndpoints_shouldRequireAuthentication() {
        // Arrange - No authentication header provided
        
        // Act & Assert - Test various protected endpoints with domain/solution prefix
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.error.code").exists()
            .jsonPath("$.error.scope").isEqualTo("BROKER");
        
        webTestClient.post()
            .uri(PREFIX + "/responses/StudentPersonal")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isUnauthorized();
        
        webTestClient.get()
            .uri(PREFIX + "/queues")
            .exchange()
            .expectStatus().isUnauthorized();
        
        // Verify Environment Service was NOT called (authentication failed before that)
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/internal/validate-token")));
    }
}
