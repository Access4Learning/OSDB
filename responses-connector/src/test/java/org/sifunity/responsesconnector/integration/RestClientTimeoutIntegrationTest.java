// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for RestClient timeout configuration.
 * Verifies that timeouts are properly applied and enforced for Queue Service calls.
 *
 * Tests Requirements:
 * - 11.1: Configurable connection timeout for Queue Service calls
 * - 11.2: Configurable read timeout for Queue Service calls
 * - 11.3: Timeout handling
 */
class RestClientTimeoutIntegrationTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private RestClient createRestClient(int connectTimeoutMs, int readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(wireMockServer.baseUrl())
                .build();
    }

    @Test
    void restClient_shouldTimeoutOnSlowResponse() {
        wireMockServer.stubFor(post(urlPathMatching("/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"messageId\":\"123e4567-e89b-12d3-a456-426614174000\",\"status\":\"ENQUEUED\"}")
                .withFixedDelay(3000)));

        RestClient restClient = createRestClient(5000, 2000);

        assertThatThrownBy(() ->
            restClient.post()
                .uri("/queues/123e4567-e89b-12d3-a456-426614174000/messages")
                .body("{\"messageId\":\"msg-123\",\"payload\":\"test\"}")
                .retrieve()
                .body(String.class)
        ).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void restClient_shouldSucceedOnFastResponse() {
        wireMockServer.stubFor(post(urlPathMatching("/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"messageId\":\"123e4567-e89b-12d3-a456-426614174000\",\"status\":\"ENQUEUED\"}")
                .withFixedDelay(100)));

        RestClient restClient = createRestClient(5000, 10000);

        String response = restClient.post()
            .uri("/queues/123e4567-e89b-12d3-a456-426614174000/messages")
            .body("{\"messageId\":\"msg-123\",\"payload\":\"test\"}")
            .retrieve()
            .body(String.class);

        assertThat(response).contains("messageId");
        assertThat(response).contains("ENQUEUED");
    }

    @Test
    void restClient_shouldHandleConnectionTimeout() {
        // Non-routable IP to trigger connection timeout
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1000))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(1000));

        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("http://192.0.2.1:9999")
                .build();

        assertThatThrownBy(() ->
            restClient.post()
                .uri("/queues/123e4567-e89b-12d3-a456-426614174000/messages")
                .body("{\"messageId\":\"msg-123\",\"payload\":\"test\"}")
                .retrieve()
                .body(String.class)
        ).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void restClient_shouldRespectReadTimeoutBoundary() {
        wireMockServer.stubFor(post(urlPathMatching("/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"messageId\":\"123e4567-e89b-12d3-a456-426614174000\",\"status\":\"ENQUEUED\"}")
                .withFixedDelay(1900)));

        RestClient restClient = createRestClient(5000, 5000);

        String response = restClient.post()
            .uri("/queues/123e4567-e89b-12d3-a456-426614174000/messages")
            .body("{\"messageId\":\"msg-123\",\"payload\":\"test\"}")
            .retrieve()
            .body(String.class);

        assertThat(response).contains("messageId");
    }

    @Test
    void restClient_shouldSupportCustomHeaders() {
        wireMockServer.stubFor(post(urlPathMatching("/queues/.*/messages"))
            .withHeader("X-Request-Id", equalTo("test-request-123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"messageId\":\"123e4567-e89b-12d3-a456-426614174000\",\"status\":\"ENQUEUED\"}")));

        RestClient restClient = createRestClient(5000, 10000);

        String response = restClient.post()
            .uri("/queues/123e4567-e89b-12d3-a456-426614174000/messages")
            .header("X-Request-Id", "test-request-123")
            .body("{\"messageId\":\"msg-123\",\"payload\":\"test\"}")
            .retrieve()
            .body(String.class);

        assertThat(response).contains("messageId");
        wireMockServer.verify(postRequestedFor(urlPathMatching("/queues/.*/messages"))
            .withHeader("X-Request-Id", equalTo("test-request-123")));
    }
}
