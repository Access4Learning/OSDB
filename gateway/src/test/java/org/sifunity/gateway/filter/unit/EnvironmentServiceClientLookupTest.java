// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.dto.EnvironmentLookupResponse;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Unit tests for EnvironmentServiceClient lookup methods (lookupEnvironmentByFingerprint
 * and lookupEnvironmentById).
 * Uses WireMock to simulate Environment Service responses.
 *
 * <p>Test Coverage:
 * <ul>
 *   <li>Successful lookup by fingerprint</li>
 *   <li>Successful lookup by ID</li>
 *   <li>404 → empty Mono</li>
 *   <li>5xx → ServiceUnavailableException (after retries exhausted)</li>
 *   <li>Timeout → retry behavior</li>
 *   <li>No retry on 4xx client errors</li>
 * </ul>
 *
 * <p>Requirements: 7.4, 9.1
 */
@DisplayName("EnvironmentServiceClient Lookup Methods Tests")
class EnvironmentServiceClientLookupTest {

    private WireMockServer wireMockServer;
    private EnvironmentServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        String baseUrl = "http://localhost:" + wireMockServer.port();
        client = new EnvironmentServiceClient(WebClient.builder(), baseUrl, "test-internal-token", null, null, io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== lookupEnvironmentByFingerprint Tests ==========

    @Nested
    @DisplayName("lookupEnvironmentByFingerprint")
    class LookupByFingerprint {

        @Test
        @DisplayName("should return environment details on successful lookup")
        void successfulLookup() {
            UUID envId = UUID.randomUUID();
            UUID solutionId = UUID.randomUUID();

            stubFor(get(urlPathEqualTo("/internal/environments/by-fingerprint"))
                .withQueryParam("fingerprint", equalTo("broker-services"))
                .withQueryParam("domainId", equalTo("d1"))
                .withQueryParam("solutionId", equalTo(solutionId.toString()))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                            "id": "%s",
                            "applicationName": "internal-broker-services-%s",
                            "fingerprint": "broker-services",
                            "solutionUuid": "%s"
                        }
                        """.formatted(envId, solutionId, solutionId))));

            StepVerifier.create(client.lookupEnvironmentByFingerprint("broker-services", "d1", solutionId.toString()))
                .expectNextMatches(response ->
                    response.id().equals(envId) &&
                    response.applicationName().equals("internal-broker-services-" + solutionId) &&
                    response.fingerprint().equals("broker-services") &&
                    response.solutionUuid().equals(solutionId)
                )
                .verifyComplete();

            verify(getRequestedFor(urlPathEqualTo("/internal/environments/by-fingerprint"))
                .withQueryParam("fingerprint", equalTo("broker-services"))
                .withQueryParam("domainId", equalTo("d1"))
                .withQueryParam("solutionId", equalTo(solutionId.toString())));
        }

        @Test
        @DisplayName("should return empty Mono on 404 response")
        void notFound_ReturnsEmpty() {
            UUID solutionId = UUID.randomUUID();

            stubFor(get(urlPathEqualTo("/internal/environments/by-fingerprint"))
                .willReturn(aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

            StepVerifier.create(client.lookupEnvironmentByFingerprint("broker-services", "d1", solutionId.toString()))
                .verifyComplete();

            verify(1, getRequestedFor(urlPathEqualTo("/internal/environments/by-fingerprint")));
        }

        @Test
        @DisplayName("should throw ServiceUnavailableException on 500 response after retries")
        void serverError_ThrowsServiceUnavailable() {
            UUID solutionId = UUID.randomUUID();

            stubFor(get(urlPathEqualTo("/internal/environments/by-fingerprint"))
                .willReturn(aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                            "code": "SERVICE_002",
                            "message": "Internal server error"
                        }
                        """)));

            StepVerifier.create(client.lookupEnvironmentByFingerprint("broker-services", "d1", solutionId.toString()))
                .expectErrorMatches(error -> {
                    if (error.getMessage() != null && error.getMessage().contains("Retries exhausted")) {
                        Throwable cause = error.getCause();
                        return cause instanceof ServiceUnavailableException &&
                               cause.getMessage().contains("Environment lookup by fingerprint service unavailable");
                    }
                    return false;
                })
                .verify();

            // Initial + 2 retries = 3 requests
            verify(3, getRequestedFor(urlPathEqualTo("/internal/environments/by-fingerprint")));
        }

        @Test
        @DisplayName("should timeout after 5 seconds and retry")
        void timeout_RetriesAndFails() {
            UUID solutionId = UUID.randomUUID();

            stubFor(get(urlPathEqualTo("/internal/environments/by-fingerprint"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                            "id": "00000000-0000-0000-0000-000000000000",
                            "applicationName": "test",
                            "fingerprint": "broker-services",
                            "solutionUuid": "%s"
                        }
                        """.formatted(solutionId))
                    .withFixedDelay(6000)));

            StepVerifier.create(client.lookupEnvironmentByFingerprint("broker-services", "d1", solutionId.toString()))
                .expectErrorMatches(error -> {
                    if (error.getMessage() != null && error.getMessage().contains("Retries exhausted")) {
                        Throwable cause = error.getCause();
                        return cause instanceof java.util.concurrent.TimeoutException;
                    }
                    return error instanceof java.util.concurrent.TimeoutException;
                })
                .verify(Duration.ofSeconds(20));
        }

        @Test
        @DisplayName("should retry on transient 500 and succeed on subsequent attempt")
        void retryOnTransientFailure_ThenSucceeds() {
            UUID envId = UUID.randomUUID();
            UUID solutionId = UUID.randomUUID();

            // First call fails with 500, second succeeds
            stubFor(get(urlPathEqualTo("/internal/environments/by-fingerprint"))
                .inScenario("Fingerprint Retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}"))
                .willSetStateTo("Retried"));

            stubFor(get(urlPathEqualTo("/internal/environments/by-fingerprint"))
                .inScenario("Fingerprint Retry")
                .whenScenarioStateIs("Retried")
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                            "id": "%s",
                            "applicationName": "broker-svc",
                            "fingerprint": "broker-services",
                            "solutionUuid": "%s"
                        }
                        """.formatted(envId, solutionId))));

            StepVerifier.create(client.lookupEnvironmentByFingerprint("broker-services", "d1", solutionId.toString()))
                .expectNextMatches(response ->
                    response.id().equals(envId) &&
                    response.fingerprint().equals("broker-services")
                )
                .verifyComplete();

            verify(2, getRequestedFor(urlPathEqualTo("/internal/environments/by-fingerprint")));
        }

        @Test
        @DisplayName("should NOT retry on 404 (not found is not transient)")
        void noRetryOn404() {
            UUID solutionId = UUID.randomUUID();

            stubFor(get(urlPathEqualTo("/internal/environments/by-fingerprint"))
                .willReturn(aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

            StepVerifier.create(client.lookupEnvironmentByFingerprint("broker-services", "d1", solutionId.toString()))
                .verifyComplete();

            // Only 1 request — no retries on 404
            verify(1, getRequestedFor(urlPathEqualTo("/internal/environments/by-fingerprint")));
        }
    }

    // ========== lookupEnvironmentById Tests ==========

    @Nested
    @DisplayName("lookupEnvironmentById")
    class LookupById {

        @Test
        @DisplayName("should return environment details on successful lookup")
        void successfulLookup() {
            UUID envId = UUID.randomUUID();
            UUID solutionId = UUID.randomUUID();

            stubFor(get(urlEqualTo("/internal/environments/" + envId))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                            "id": "%s",
                            "applicationName": "internal-broker-services-%s",
                            "fingerprint": "broker-services",
                            "solutionUuid": "%s"
                        }
                        """.formatted(envId, solutionId, solutionId))));

            StepVerifier.create(client.lookupEnvironmentById(envId))
                .expectNextMatches(response ->
                    response.id().equals(envId) &&
                    response.applicationName().equals("internal-broker-services-" + solutionId) &&
                    response.fingerprint().equals("broker-services") &&
                    response.solutionUuid().equals(solutionId)
                )
                .verifyComplete();

            verify(getRequestedFor(urlEqualTo("/internal/environments/" + envId)));
        }

        @Test
        @DisplayName("should return empty Mono on 404 response")
        void notFound_ReturnsEmpty() {
            UUID envId = UUID.randomUUID();

            stubFor(get(urlEqualTo("/internal/environments/" + envId))
                .willReturn(aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

            StepVerifier.create(client.lookupEnvironmentById(envId))
                .verifyComplete();

            verify(1, getRequestedFor(urlEqualTo("/internal/environments/" + envId)));
        }

        @Test
        @DisplayName("should throw ServiceUnavailableException on 500 response after retries")
        void serverError_ThrowsServiceUnavailable() {
            UUID envId = UUID.randomUUID();

            stubFor(get(urlEqualTo("/internal/environments/" + envId))
                .willReturn(aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                            "code": "SERVICE_002",
                            "message": "Internal server error"
                        }
                        """)));

            StepVerifier.create(client.lookupEnvironmentById(envId))
                .expectErrorMatches(error -> {
                    if (error.getMessage() != null && error.getMessage().contains("Retries exhausted")) {
                        Throwable cause = error.getCause();
                        return cause instanceof ServiceUnavailableException &&
                               cause.getMessage().contains("Environment lookup by ID service unavailable");
                    }
                    return false;
                })
                .verify();

            // Initial + 2 retries = 3 requests
            verify(3, getRequestedFor(urlEqualTo("/internal/environments/" + envId)));
        }

        @Test
        @DisplayName("should timeout after 5 seconds and retry")
        void timeout_RetriesAndFails() {
            UUID envId = UUID.randomUUID();

            stubFor(get(urlEqualTo("/internal/environments/" + envId))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                            "id": "%s",
                            "applicationName": "test",
                            "fingerprint": "broker-services",
                            "solutionUuid": "00000000-0000-0000-0000-000000000000"
                        }
                        """.formatted(envId))
                    .withFixedDelay(6000)));

            StepVerifier.create(client.lookupEnvironmentById(envId))
                .expectErrorMatches(error -> {
                    if (error.getMessage() != null && error.getMessage().contains("Retries exhausted")) {
                        Throwable cause = error.getCause();
                        return cause instanceof java.util.concurrent.TimeoutException;
                    }
                    return error instanceof java.util.concurrent.TimeoutException;
                })
                .verify(Duration.ofSeconds(20));
        }

        @Test
        @DisplayName("should retry on transient 503 and succeed on subsequent attempt")
        void retryOnTransientFailure_ThenSucceeds() {
            UUID envId = UUID.randomUUID();
            UUID solutionId = UUID.randomUUID();

            // First call fails with 503, second succeeds
            stubFor(get(urlEqualTo("/internal/environments/" + envId))
                .inScenario("ID Retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                    .withStatus(503)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}"))
                .willSetStateTo("Retried"));

            stubFor(get(urlEqualTo("/internal/environments/" + envId))
                .inScenario("ID Retry")
                .whenScenarioStateIs("Retried")
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                            "id": "%s",
                            "applicationName": "broker-svc",
                            "fingerprint": "broker-services",
                            "solutionUuid": "%s"
                        }
                        """.formatted(envId, solutionId))));

            StepVerifier.create(client.lookupEnvironmentById(envId))
                .expectNextMatches(response ->
                    response.id().equals(envId) &&
                    response.fingerprint().equals("broker-services")
                )
                .verifyComplete();

            verify(2, getRequestedFor(urlEqualTo("/internal/environments/" + envId)));
        }

        @Test
        @DisplayName("should NOT retry on 404 (not found is not transient)")
        void noRetryOn404() {
            UUID envId = UUID.randomUUID();

            stubFor(get(urlEqualTo("/internal/environments/" + envId))
                .willReturn(aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

            StepVerifier.create(client.lookupEnvironmentById(envId))
                .verifyComplete();

            // Only 1 request — no retries on 404
            verify(1, getRequestedFor(urlEqualTo("/internal/environments/" + envId)));
        }
    }
}
