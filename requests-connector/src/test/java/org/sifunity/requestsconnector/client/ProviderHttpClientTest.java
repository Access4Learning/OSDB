// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.sifunity.requestsconnector.dto.ProviderRawResponse;
import org.sifunity.requestsconnector.exception.ProviderTimeoutException;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ProviderHttpClient.
 * Uses WireMock to test HTTP behavior including timeout mapping,
 * null body handling, null headers, and null httpMethod defaults.
 */
class ProviderHttpClientTest {

    private static WireMockServer wireMock;
    private ProviderHttpClient client;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        // Short timeouts for testing: 5s connection, 5s read
        client = new ProviderHttpClient(WebClient.builder(), 5000, 5000);
    }

    @Nested
    class SendRequestRawWithHeadersTests {

        @Test
        void getMethodWithNullBody_doesNotSendBody() {
            wireMock.stubFor(get(urlEqualTo("/api/students"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("navigationPage", "1")
                    .withBody("{\"students\":[]}")));

            ProviderRawResponse response = client.sendRequestRawWithHeaders(
                wireMock.baseUrl() + "/api/students",
                null,
                Map.of("Accept", "application/json"),
                "GET");

            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getHeaders()).containsKey("navigationPage");

            // Verify GET was used (not POST)
            wireMock.verify(getRequestedFor(urlEqualTo("/api/students"))
                .withHeader("Accept", equalTo("application/json")));
        }

        @Test
        void nullPassthroughHeaders_usesEmptyMap() {
            wireMock.stubFor(post(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\":\"123\"}")));

            ProviderRawResponse response = client.sendRequestRawWithHeaders(
                wireMock.baseUrl() + "/api/data",
                Map.of("name", "test"),
                null,
                "POST");

            assertThat(response.getStatusCode()).isEqualTo(201);
        }

        @Test
        void nullHttpMethod_defaultsToPost() {
            wireMock.stubFor(post(urlEqualTo("/api/default"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"ok\":true}")));

            ProviderRawResponse response = client.sendRequestRawWithHeaders(
                wireMock.baseUrl() + "/api/default",
                Map.of("payload", "data"),
                Collections.emptyMap(),
                null);

            assertThat(response.getStatusCode()).isEqualTo(200);
            wireMock.verify(postRequestedFor(urlEqualTo("/api/default")));
        }

        @Test
        void timeoutException_mapsToProviderTimeoutException() {
            // Delay longer than read timeout (5s)
            wireMock.stubFor(get(urlEqualTo("/api/slow"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withFixedDelay(10000)
                    .withBody("{}")));

            assertThatThrownBy(() -> client.sendRequestRawWithHeaders(
                wireMock.baseUrl() + "/api/slow",
                null,
                Collections.emptyMap(),
                "GET"))
                .isInstanceOf(ProviderTimeoutException.class)
                .hasMessageContaining("timed out");
        }

        @Test
        void nonTimeoutException_passesThrough() {
            // Use an invalid port to trigger connection error (not timeout)
            assertThatThrownBy(() -> client.sendRequestRawWithHeaders(
                "http://localhost:1/api/unreachable",
                null,
                Collections.emptyMap(),
                "GET"))
                .isNotInstanceOf(ProviderTimeoutException.class);
        }

        @Test
        void responseHeadersCaptured() {
            wireMock.stubFor(get(urlEqualTo("/api/paged"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("navigationPage", "1")
                    .withHeader("navigationLastPage", "5")
                    .withHeader("navigationId", "nav-abc")
                    .withBody("{\"data\":[]}")));

            ProviderRawResponse response = client.sendRequestRawWithHeaders(
                wireMock.baseUrl() + "/api/paged",
                null,
                Collections.emptyMap(),
                "GET");

            assertThat(response.getHeaders())
                .containsEntry("navigationPage", "1")
                .containsEntry("navigationLastPage", "5")
                .containsEntry("navigationId", "nav-abc");
        }
    }
}
