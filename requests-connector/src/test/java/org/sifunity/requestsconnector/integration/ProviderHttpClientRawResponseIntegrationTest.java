// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.requestsconnector.client.ProviderHttpClient;
import org.sifunity.requestsconnector.dto.ProviderRawResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ProviderHttpClient#sendRequestRawWithHeaders}.
 *
 * <p>Tests full HTTP round-trip through WebClient → WireMock → response parsing
 * into {@link ProviderRawResponse}, verifying body, all response headers
 * (including SIF navigation headers), and status code are captured correctly.</p>
 *
 * <p>Validates: Requirements 4.1, 4.2, 4.3</p>
 */
class ProviderHttpClientRawResponseIntegrationTest {

    private WireMockServer wireMockServer;
    private ProviderHttpClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        client = new ProviderHttpClient(WebClient.builder(), 1000, 2000);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== Body Capture ==========

    @Test
    void capturesJsonBodyCorrectly() {
        String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";

        wireMockServer.stubFor(get(urlPathEqualTo("/api/StudentPersonals"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"students\":[{\"id\":\"s1\",\"name\":\"Jane\"}]}")));

        ProviderRawResponse response = client.sendRequestRawWithHeaders(
            endpoint, null, Collections.emptyMap(), "GET");

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("students");
    }

    // ========== Header Capture ==========

    @Test
    void capturesAllFiveNavigationHeadersSimultaneously() {
        String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";

        wireMockServer.stubFor(get(urlPathEqualTo("/api/StudentPersonals"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withHeader("navigationPage", "2")
                .withHeader("navigationLastPage", "10")
                .withHeader("navigationPageSize", "25")
                .withHeader("navigationId", "nav-cursor-xyz-789")
                .withHeader("navigationCount", "250")
                .withBody("{\"students\":[]}")));

        ProviderRawResponse response = client.sendRequestRawWithHeaders(
            endpoint, null, Collections.emptyMap(), "GET");

        assertThat(response.getHeaders()).containsEntry("navigationPage", "2");
        assertThat(response.getHeaders()).containsEntry("navigationLastPage", "10");
        assertThat(response.getHeaders()).containsEntry("navigationPageSize", "25");
        assertThat(response.getHeaders()).containsEntry("navigationId", "nav-cursor-xyz-789");
        assertThat(response.getHeaders()).containsEntry("navigationCount", "250");
    }

    @Test
    void capturesNonStandardCustomHeaders() {
        String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";

        wireMockServer.stubFor(get(urlPathEqualTo("/api/StudentPersonals"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withHeader("X-Provider-Version", "3.7")
                .withHeader("X-Request-Cost", "42")
                .withHeader("X-Custom-Trace-Id", "trace-abc-123")
                .withBody("{\"data\":\"ok\"}")));

        ProviderRawResponse response = client.sendRequestRawWithHeaders(
            endpoint, null, Collections.emptyMap(), "GET");

        assertThat(response.getHeaders()).containsEntry("X-Provider-Version", "3.7");
        assertThat(response.getHeaders()).containsEntry("X-Request-Cost", "42");
        assertThat(response.getHeaders()).containsEntry("X-Custom-Trace-Id", "trace-abc-123");
    }

    @Test
    void capturesNavigationAndCustomHeadersTogether() {
        String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";

        wireMockServer.stubFor(get(urlPathEqualTo("/api/StudentPersonals"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withHeader("navigationPage", "1")
                .withHeader("navigationLastPage", "3")
                .withHeader("X-Provider-Version", "3.7")
                .withBody("{\"students\":[{\"id\":\"s1\"}]}")));

        ProviderRawResponse response = client.sendRequestRawWithHeaders(
            endpoint, null, Collections.emptyMap(), "GET");

        assertThat(response.getHeaders())
            .containsEntry("navigationPage", "1")
            .containsEntry("navigationLastPage", "3")
            .containsEntry("X-Provider-Version", "3.7")
            .containsKey("Content-Type");
    }

    // ========== Status Code Capture ==========

    @Test
    void capturesStatus200() {
        String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";

        wireMockServer.stubFor(get(urlPathEqualTo("/api/StudentPersonals"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"students\":[]}")));

        ProviderRawResponse response = client.sendRequestRawWithHeaders(
            endpoint, null, Collections.emptyMap(), "GET");

        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test
    void capturesStatus201() {
        String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";

        wireMockServer.stubFor(post(urlPathEqualTo("/api/StudentPersonals"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"new-student-1\"}")));

        ProviderRawResponse response = client.sendRequestRawWithHeaders(
            endpoint, Map.of("name", "New Student"), Collections.emptyMap(), "POST");

        assertThat(response.getStatusCode()).isEqualTo(201);
    }

    // ========== GET Without Body ==========

    @Test
    void getRequestWithNullBodySucceeds() {
        String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";

        wireMockServer.stubFor(get(urlPathEqualTo("/api/StudentPersonals"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withHeader("navigationPage", "1")
                .withHeader("navigationLastPage", "1")
                .withBody("{\"students\":[{\"id\":\"s1\"}]}")));

        ProviderRawResponse response = client.sendRequestRawWithHeaders(
            endpoint, null, Collections.emptyMap(), "GET");

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders()).containsEntry("navigationPage", "1");

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/StudentPersonals")));
    }

    // ========== POST With Body ==========

    @Test
    void postRequestSendsBodyAndCapturesResponse() {
        String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";
        Map<String, Object> requestBody = Map.of("name", "Alice", "grade", 11);

        wireMockServer.stubFor(post(urlPathEqualTo("/api/StudentPersonals"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withHeader("navigationPage", "1")
                .withHeader("navigationLastPage", "1")
                .withBody("{\"id\":\"s-new\",\"name\":\"Alice\",\"grade\":11}")));

        ProviderRawResponse response = client.sendRequestRawWithHeaders(
            endpoint, requestBody, Collections.emptyMap(), "POST");

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders()).containsEntry("navigationPage", "1");

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/StudentPersonals"))
            .withRequestBody(matchingJsonPath("$.name", equalTo("Alice")))
            .withRequestBody(matchingJsonPath("$.grade", equalTo("11"))));
    }
}
