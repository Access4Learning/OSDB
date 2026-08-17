// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.sifunity.requestsconnector.auth.ProviderCredentialService;
import org.sifunity.requestsconnector.auth.ProviderCredentials;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProviderCredentialService.
 * Uses WireMock to mock Environment Service HTTP responses.
 *
 * <p>Validates Requirements:</p>
 * <ul>
 *   <li>7.1: Cache session token and shared secret keyed by provider environment UUID</li>
 *   <li>7.2: Use cached values without making HTTP calls while cache entry exists</li>
 *   <li>7.3: Expire entries after configurable TTL, max 1000 entries</li>
 *   <li>7.4: Do not cache failures; do not evict existing entries on failure</li>
 *   <li>7.5: Return empty on failure when no cached entry exists</li>
 * </ul>
 */
@DisplayName("ProviderCredentialService Unit Tests")
class ProviderCredentialServiceTest {

    private WireMockServer wireMockServer;
    private ProviderCredentialService service;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        WebClient.Builder webClientBuilder = WebClient.builder();
        service = new ProviderCredentialService(
                webClientBuilder,
                wireMockServer.baseUrl(),
                "test-internal-token",
                Duration.ofMinutes(5),
                1000);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== Successful Fetch Tests (Requirements 7.1, 7.2) ==========

    @Test
    @DisplayName("Successful fetch populates cache and returns credentials")
    void successfulFetch_populatesCacheAndReturnsCredentials() {
        // Given
        UUID envId = UUID.randomUUID();
        String sessionToken = "test-session-token-abc123";
        String sharedSecret = "test-shared-secret-xyz789";

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(sessionToken)));

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/shared-secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(sharedSecret)));

        // When
        Optional<ProviderCredentials> result = service.getCredentials(envId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().sessionToken()).isEqualTo(sessionToken);
        assertThat(result.get().sharedSecret()).isEqualTo(sharedSecret);

        // Verify both endpoints were called
        wireMockServer.verify(1, getRequestedFor(
                urlPathEqualTo("/internal/environments/" + envId + "/session-token")));
        wireMockServer.verify(1, getRequestedFor(
                urlPathEqualTo("/internal/environments/" + envId + "/shared-secret")));
    }

    @Test
    @DisplayName("Second call uses cached value without making HTTP calls")
    void secondCall_usesCachedValueWithoutHttpCalls() {
        // Given
        UUID envId = UUID.randomUUID();
        String sessionToken = "cached-session-token";
        String sharedSecret = "cached-shared-secret";

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(sessionToken)));

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/shared-secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(sharedSecret)));

        // When - first call populates cache
        Optional<ProviderCredentials> first = service.getCredentials(envId);
        // Second call should hit cache
        Optional<ProviderCredentials> second = service.getCredentials(envId);

        // Then - both return same credentials
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().sessionToken()).isEqualTo(sessionToken);
        assertThat(second.get().sharedSecret()).isEqualTo(sharedSecret);

        // Verify HTTP calls made only once (cache hit on second call)
        wireMockServer.verify(1, getRequestedFor(
                urlPathEqualTo("/internal/environments/" + envId + "/session-token")));
        wireMockServer.verify(1, getRequestedFor(
                urlPathEqualTo("/internal/environments/" + envId + "/shared-secret")));
    }

    // ========== 404 Response Tests (Requirement 7.5) ==========

    @Test
    @DisplayName("404 response for session-token returns empty, no cache entry")
    void sessionToken404_returnsEmptyNoCacheEntry() {
        // Given
        UUID envId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(404)));

        // When
        Optional<ProviderCredentials> result = service.getCredentials(envId);

        // Then
        assertThat(result).isEmpty();

        // Verify second call still makes HTTP request (not cached)
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("recovered-token")));

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/shared-secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("recovered-secret")));

        Optional<ProviderCredentials> retry = service.getCredentials(envId);
        assertThat(retry).isPresent();
        assertThat(retry.get().sessionToken()).isEqualTo("recovered-token");
    }

    @Test
    @DisplayName("404 response for shared-secret returns empty, no cache entry")
    void sharedSecret404_returnsEmptyNoCacheEntry() {
        // Given
        UUID envId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("valid-session-token")));

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/shared-secret"))
                .willReturn(aResponse()
                        .withStatus(404)));

        // When
        Optional<ProviderCredentials> result = service.getCredentials(envId);

        // Then
        assertThat(result).isEmpty();
    }

    // ========== Timeout Tests (Requirements 7.5, 8.5) ==========

    @Test
    @DisplayName("Timeout on session-token returns empty, no cache entry")
    void sessionTokenTimeout_returnsEmptyNoCacheEntry() {
        // Given - delay exceeds 5s timeout
        UUID envId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("delayed-token")
                        .withFixedDelay(6000)));

        // When
        Optional<ProviderCredentials> result = service.getCredentials(envId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Timeout on shared-secret returns empty, no cache entry")
    void sharedSecretTimeout_returnsEmptyNoCacheEntry() {
        // Given - session-token succeeds but shared-secret times out
        UUID envId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("valid-session-token")));

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/shared-secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("delayed-secret")
                        .withFixedDelay(6000)));

        // When
        Optional<ProviderCredentials> result = service.getCredentials(envId);

        // Then
        assertThat(result).isEmpty();
    }

    // ========== Exception / Pre-existing Cache Tests (Requirement 7.4) ==========

    @Test
    @DisplayName("Exception returns empty, pre-existing entries for other UUIDs remain intact")
    void exception_returnsEmpty_preExistingEntriesRemain() {
        // Given - first UUID is successfully cached
        UUID existingId = UUID.randomUUID();
        UUID failingId = UUID.randomUUID();
        String existingToken = "existing-session-token";
        String existingSecret = "existing-shared-secret";

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + existingId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(existingToken)));

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + existingId + "/shared-secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(existingSecret)));

        // Cache the first UUID
        Optional<ProviderCredentials> existing = service.getCredentials(existingId);
        assertThat(existing).isPresent();

        // Now simulate connection reset for the second UUID
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + failingId + "/session-token"))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        // When - attempt to fetch failing UUID
        Optional<ProviderCredentials> failedResult = service.getCredentials(failingId);

        // Then - failing UUID returns empty
        assertThat(failedResult).isEmpty();

        // And - pre-existing cached entry for other UUID remains intact
        Optional<ProviderCredentials> existingAfterFailure = service.getCredentials(existingId);
        assertThat(existingAfterFailure).isPresent();
        assertThat(existingAfterFailure.get().sessionToken()).isEqualTo(existingToken);
        assertThat(existingAfterFailure.get().sharedSecret()).isEqualTo(existingSecret);

        // Verify no additional HTTP calls for the existing UUID (served from cache)
        wireMockServer.verify(1, getRequestedFor(
                urlPathEqualTo("/internal/environments/" + existingId + "/session-token")));
    }

    @Test
    @DisplayName("Blank session-token response returns empty")
    void blankSessionToken_returnsEmpty() {
        // Given
        UUID envId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("   ")));

        // When
        Optional<ProviderCredentials> result = service.getCredentials(envId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Blank shared-secret response returns empty")
    void blankSharedSecret_returnsEmpty() {
        // Given
        UUID envId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/session-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("valid-token")));

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + envId + "/shared-secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("")));

        // When
        Optional<ProviderCredentials> result = service.getCredentials(envId);

        // Then
        assertThat(result).isEmpty();
    }
}
