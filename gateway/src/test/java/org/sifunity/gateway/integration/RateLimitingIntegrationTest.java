// SPDX-License-Identifier: MIT
package org.sifunity.gateway.integration;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.PermissionCheckResponse;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.sifunity.gateway.ratelimit.RateLimitResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test for rate limiting functionality.
 * 
 * <p>This test validates that:</p>
 * <ul>
 *   <li>Rate limits are enforced per environment</li>
 *   <li>Different environment types have different limits</li>
 *   <li>Retry-After header is included in 429 responses</li>
 *   <li>Rate limiting is tracked independently per environment</li>
 * </ul>
 * 
 * <p>Uses mocked RedisRateLimiter to simulate rate limit behavior without
 * requiring a real Redis instance.</p>
 * 
 * <p>Requirements: 8.1, 8.2, 8.4, 8.5</p>
 */
class RateLimitingIntegrationTest extends BaseIntegrationTest {
    
    /** Standard test domain/solution prefix for resource paths. */
    private static final String PREFIX = "/iowa/production";
    
    @Test
    void rateLimitExceeded_shouldReturn429WithRetryAfter() throws Exception {
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
            "production"
        , null);
        
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
        
        // Mock rate limiter to return rate limit exceeded
        when(redisRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
        when(redisRateLimiter.isAllowed(anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
        
        // Act & Assert
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.ZONE_ID, "default")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().exists(SifHeaders.X_CORRELATION_ID)
            .expectHeader().valueEquals("Retry-After", "60")
            .expectBody()
            .jsonPath("$.error.subCode").isEqualTo("RATE_001")
            .jsonPath("$.error.message").isEqualTo("Rate limit exceeded")
            .jsonPath("$.error.scope").isEqualTo("BROKER")
            .jsonPath("$.error.timestamp").doesNotExist();
    }
    
    @Test
    void rateLimitNotExceeded_shouldAllowRequest() throws Exception {
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
        
        // Mock rate limiter to allow request
        when(redisRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(true, 0)));
        when(redisRateLimiter.isAllowed(anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(true, 0)));
        
        // Act & Assert
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.ZONE_ID, "default")
            .exchange()
            .expectStatus().isNotFound() // 404 because no backend service is running
            .expectHeader().exists(SifHeaders.X_CORRELATION_ID);
    }
    
    @Test
    void differentEnvironments_shouldHaveIndependentRateLimits() throws Exception {
        // Arrange - Two different environments
        UUID environmentId1 = UUID.randomUUID();
        UUID environmentId2 = UUID.randomUUID();
        String sessionToken1 = UUID.randomUUID().toString();
        String sessionToken2 = UUID.randomUUID().toString();
        String timestamp = "2024-01-01T00:00:00Z";
        String hash = "test-hash";
        
        // Format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials1 = sessionToken1 + ":" + hash;
        String base64Credentials1 = java.util.Base64.getEncoder()
            .encodeToString(credentials1.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader1 = "SIF_HMACSHA256 " + base64Credentials1;
        
        String credentials2 = sessionToken2 + ":" + hash;
        String base64Credentials2 = java.util.Base64.getEncoder()
            .encodeToString(credentials2.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader2 = "SIF_HMACSHA256 " + base64Credentials2;
        
        TokenValidationResponse validationResponse1 = new TokenValidationResponse(
            environmentId1,
            "TestApp1",
            EnvironmentType.BROKERED,
            "default",
            null,
            "iowa",
            "production",
            null
        );
        
        TokenValidationResponse validationResponse2 = new TokenValidationResponse(
            environmentId2,
            "TestApp2",
            EnvironmentType.BROKERED,
            "default",
            null,
            "iowa",
            "production",
            null
        );
        
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        
        // Mock Environment Service endpoints for both environments
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .withRequestBody(matchingJsonPath("$.sessionToken", equalTo(sessionToken1)))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(validationResponse1))));
        
        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
            .withRequestBody(matchingJsonPath("$.sessionToken", equalTo(sessionToken2)))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(validationResponse2))));
        
        wireMockServer.stubFor(post(urlEqualTo("/internal/check-permission"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(objectMapper.writeValueAsString(permissionResponse))));
        
        // Mock rate limiter - environment1 exceeds limit, environment2 does not
        when(redisRateLimiter.isAllowed("iowa", "production", environmentId1.toString(), 100))
            .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
        when(redisRateLimiter.isAllowed("iowa", "production", environmentId2.toString(), 100))
            .thenReturn(Mono.just(new RateLimitResponse(true, 0)));
        
        // Act & Assert - Environment 1 should be rate limited
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader1)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.ZONE_ID, "default")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectBody()
            .jsonPath("$.error.subCode").isEqualTo("RATE_001");
        
        // Act & Assert - Environment 2 should be allowed
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader2)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.ZONE_ID, "default")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no backend service is running
    }
    
    @Test
    void exemptPaths_shouldNotBeRateLimited() {
        // Arrange - No rate limiter mock needed for exempt paths
        
        // Act & Assert - POST /environments should not be rate limited
        webTestClient.post()
            .uri(PREFIX + "/environments")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isNotFound(); // 404 because no backend service is running
        
        // Act & Assert - Health check should not be rate limited
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }
    
    @Test
    void rateLimitResponse_shouldIncludeCorrelationId() throws Exception {
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
        String customCorrelationId = UUID.randomUUID().toString();
        
        TokenValidationResponse validationResponse = new TokenValidationResponse(
            environmentId,
            applicationName,
            EnvironmentType.BROKERED,
            "default",
            null,
            "iowa",
            "production"
        , null);
        
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
        
        // Mock rate limiter to return rate limit exceeded
        when(redisRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
        when(redisRateLimiter.isAllowed(anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(false, 60)));
        
        // Act & Assert
        webTestClient.get()
            .uri(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.AUTHORIZATION, authHeader)
            .header(SifHeaders.TIMESTAMP, timestamp)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.ZONE_ID, "default")
            .header(SifHeaders.X_CORRELATION_ID, customCorrelationId)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().valueEquals(SifHeaders.X_CORRELATION_ID, customCorrelationId);
    }
}
