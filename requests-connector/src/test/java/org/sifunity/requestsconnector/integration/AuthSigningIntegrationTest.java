// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.extension.ProviderRequestContext;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.requestsconnector.auth.AuthSigningInterceptor;
import org.sifunity.requestsconnector.auth.ProviderCredentialService;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying the full auth signing flow:
 * AuthSigningInterceptor → ProviderCredentialService → WireMock (simulating environment-service).
 *
 * <p>Tests real HTTP interaction between credential service and environment-service (via WireMock),
 * then verifies the interceptor produces correct auth headers.</p>
 *
 * <p>Validates: Requirements 3.1, 3.2, 3.3, 4.1, 4.2, 5.1, 5.2, 6.1, 6.2, 8.1, 8.3</p>
 */
class AuthSigningIntegrationTest {

    private WireMockServer wireMockServer;
    private AuthSigningInterceptor interceptor;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        // Real ProviderCredentialService pointing at WireMock
        ProviderCredentialService credentialService = new ProviderCredentialService(
                WebClient.builder(),
                wireMockServer.baseUrl(),
                "test-internal-token",
                Duration.ofMinutes(5),
                1000);

        // Real AuthSigningInterceptor
        interceptor = new AuthSigningInterceptor(credentialService);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== Success Scenario ==========

    @Test
    void signsRequestWithCorrectAuthorizationHeaderFormat() {
        // Arrange
        UUID envId = UUID.randomUUID();
        String sessionToken = "test-session-token-abc123";
        String sharedSecret = "super-secret-key-xyz789";

        stubCredentialEndpoints(envId, sessionToken, sharedSecret);

        Map<String, String> headers = new HashMap<>();
        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/api", headers);

        // Act
        interceptor.beforeProviderRequest(context);

        // Assert — authorization header present and starts with SIF_HMACSHA256
        assertThat(headers).containsKey("authorization");
        String authHeader = headers.get("authorization");
        assertThat(authHeader).startsWith("SIF_HMACSHA256 ");
    }

    @Test
    void authorizationHeaderRoundTripsCorrectly() {
        // Arrange
        UUID envId = UUID.randomUUID();
        String sessionToken = "round-trip-token";
        String sharedSecret = "round-trip-secret";

        stubCredentialEndpoints(envId, sessionToken, sharedSecret);

        Map<String, String> headers = new HashMap<>();
        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/api", headers);

        // Act
        interceptor.beforeProviderRequest(context);

        // Assert — parse header and verify round-trip
        String authHeader = headers.get("authorization");
        SifAuthenticationUtils.AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(authHeader);

        // Session token should match
        assertThat(parsed.sessionToken()).isEqualTo(sessionToken);

        // Recompute HMAC with parsed session token and timestamp header → should match
        String timestamp = headers.get("timestamp");
        String recomputedHmac = SifAuthenticationUtils.computeHmac(
                parsed.sessionToken(), timestamp, sharedSecret);
        assertThat(parsed.hash()).isEqualTo(recomputedHmac);
    }

    @Test
    void timestampHeaderIsValidIso8601Instant() {
        // Arrange
        UUID envId = UUID.randomUUID();
        String sessionToken = "timestamp-test-token";
        String sharedSecret = "timestamp-test-secret";

        stubCredentialEndpoints(envId, sessionToken, sharedSecret);

        Map<String, String> headers = new HashMap<>();
        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/api", headers);

        Instant before = Instant.now();

        // Act
        interceptor.beforeProviderRequest(context);

        Instant after = Instant.now();

        // Assert — timestamp is valid ISO-8601 and within execution window
        assertThat(headers).containsKey("timestamp");
        String timestamp = headers.get("timestamp");
        Instant parsedTimestamp = Instant.parse(timestamp);
        assertThat(parsedTimestamp).isAfterOrEqualTo(before);
        assertThat(parsedTimestamp).isBeforeOrEqualTo(after);
    }

    @Test
    void credentialServiceMakesHttpCallsToWireMock() {
        // Arrange
        UUID envId = UUID.randomUUID();
        String sessionToken = "verify-http-token";
        String sharedSecret = "verify-http-secret";

        stubCredentialEndpoints(envId, sessionToken, sharedSecret);

        Map<String, String> headers = new HashMap<>();
        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/api", headers);

        // Act
        interceptor.beforeProviderRequest(context);

        // Assert — WireMock received the expected calls
        wireMockServer.verify(getRequestedFor(
                urlPathEqualTo("/internal/environments/" + envId + "/session-token")));
        wireMockServer.verify(getRequestedFor(
                urlPathEqualTo("/internal/environments/" + envId + "/shared-secret")));
    }

    // ========== Failure Scenario ==========

    @Test
    void skipsSigningWhenSessionTokenEndpointReturns404() {
        // Arrange
        UUID envId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse().withStatus(404)));

        Map<String, String> headers = new HashMap<>();
        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/api", headers);

        // Act
        interceptor.beforeProviderRequest(context);

        // Assert — no auth headers added
        assertThat(headers).doesNotContainKey("authorization");
        assertThat(headers).doesNotContainKey("timestamp");
    }

    @Test
    void skipsSigningWhenSharedSecretEndpointReturns404() {
        // Arrange
        UUID envId = UUID.randomUUID();
        String sessionToken = "token-but-no-secret";

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(sessionToken)));

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/shared-secret"))
                .willReturn(aResponse().withStatus(404)));

        Map<String, String> headers = new HashMap<>();
        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/api", headers);

        // Act
        interceptor.beforeProviderRequest(context);

        // Assert — no auth headers added
        assertThat(headers).doesNotContainKey("authorization");
        assertThat(headers).doesNotContainKey("timestamp");
    }

    @Test
    void skipsSigningWhenBothEndpointsReturn404() {
        // Arrange
        UUID envId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse().withStatus(404)));
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/shared-secret"))
                .willReturn(aResponse().withStatus(404)));

        Map<String, String> headers = new HashMap<>();
        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/api", headers);

        // Act
        interceptor.beforeProviderRequest(context);

        // Assert — no auth headers added
        assertThat(headers).doesNotContainKey("authorization");
        assertThat(headers).doesNotContainKey("timestamp");
    }

    @Test
    void existingHeadersPreservedWhenSigningSkipped() {
        // Arrange
        UUID envId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse().withStatus(404)));

        Map<String, String> headers = new HashMap<>();
        headers.put("x-custom-header", "custom-value");
        headers.put("Content-Type", "application/json");

        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/api", headers);

        // Act
        interceptor.beforeProviderRequest(context);

        // Assert — original headers remain, no auth headers added
        assertThat(headers).containsEntry("x-custom-header", "custom-value");
        assertThat(headers).containsEntry("Content-Type", "application/json");
        assertThat(headers).doesNotContainKey("authorization");
        assertThat(headers).doesNotContainKey("timestamp");
    }

    // ========== Helpers ==========

    private void stubCredentialEndpoints(UUID envId, String sessionToken, String sharedSecret) {
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(sessionToken)));

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/shared-secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(sharedSecret)));
    }
}
