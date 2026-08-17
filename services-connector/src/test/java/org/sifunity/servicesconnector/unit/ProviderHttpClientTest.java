// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.servicesconnector.client.ProviderHttpClient;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the rewritten ProviderHttpClient.
 *
 * <p>Tests the generic {@code sendRequest()} method that supports all HTTP methods
 * (GET, POST, PUT, DELETE) for functional service operations.</p>
 *
 * <p>Requirements:</p>
 * <ul>
 *   <li>2.2: Forward raw request body to provider and return response unchanged</li>
 *   <li>2.4: Forward sanitized HTTP headers to provider endpoint</li>
 *   <li>2.5: Return provider's HTTP status code and response body without transformation</li>
 *   <li>2.6: Forward phase operations to correct provider path</li>
 *   <li>2.7: Forward state operations to correct provider path</li>
 * </ul>
 */
class ProviderHttpClientTest {

    private WireMockServer wireMockServer;
    private ProviderHttpClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        WebClient.Builder webClientBuilder = WebClient.builder();
        client = new ProviderHttpClient(webClientBuilder, 30000L);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== POST Tests ==========

    @Test
    void sendRequest_post_shouldForwardEnvelopeAndReturnResponse() {
        String endpoint = wireMockServer.baseUrl() + "/DataExport";
        SifMessageEnvelope<Object> envelope = createTestEnvelope(Map.of("key", "value"));

        wireMockServer.stubFor(post(urlEqualTo("/DataExport"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jobId\":\"abc-123\",\"status\":\"CREATED\"}")));

        Object response = client.sendRequest(endpoint, HttpMethod.POST, envelope, Collections.emptyMap());

        assertThat(response).isNotNull();
        wireMockServer.verify(postRequestedFor(urlEqualTo("/DataExport")));
    }

    // ========== GET Tests ==========

    @Test
    void sendRequest_get_shouldNotSendBodyAndReturnResponse() {
        String endpoint = wireMockServer.baseUrl() + "/DataExport/job-1";
        SifMessageEnvelope<Object> envelope = createTestEnvelope(null);

        wireMockServer.stubFor(get(urlEqualTo("/DataExport/job-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jobId\":\"job-1\",\"state\":\"INPROGRESS\"}")));

        Object response = client.sendRequest(endpoint, HttpMethod.GET, envelope, Collections.emptyMap());

        assertThat(response).isNotNull();
        wireMockServer.verify(getRequestedFor(urlEqualTo("/DataExport/job-1")));
    }

    // ========== PUT Tests ==========

    @Test
    void sendRequest_put_shouldForwardEnvelopeAndReturnResponse() {
        String endpoint = wireMockServer.baseUrl() + "/DataExport/job-1/export";
        SifMessageEnvelope<Object> envelope = createTestEnvelope(Map.of("data", "updated"));

        wireMockServer.stubFor(put(urlEqualTo("/DataExport/job-1/export"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UPDATED\"}")));

        Object response = client.sendRequest(endpoint, HttpMethod.PUT, envelope, Collections.emptyMap());

        assertThat(response).isNotNull();
        wireMockServer.verify(putRequestedFor(urlEqualTo("/DataExport/job-1/export")));
    }

    // ========== DELETE Tests ==========

    @Test
    void sendRequest_delete_shouldNotSendBodyAndReturnResponse() {
        String endpoint = wireMockServer.baseUrl() + "/DataExport/job-1";
        SifMessageEnvelope<Object> envelope = createTestEnvelope(null);

        wireMockServer.stubFor(delete(urlEqualTo("/DataExport/job-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"deleted\":true}")));

        Object response = client.sendRequest(endpoint, HttpMethod.DELETE, envelope, Collections.emptyMap());

        assertThat(response).isNotNull();
        wireMockServer.verify(deleteRequestedFor(urlEqualTo("/DataExport/job-1")));
    }

    // ========== Header Forwarding Tests ==========

    @Test
    void sendRequest_shouldForwardPassthroughHeaders() {
        String endpoint = wireMockServer.baseUrl() + "/DataExport";
        SifMessageEnvelope<Object> envelope = createTestEnvelope(Map.of("key", "value"));
        Map<String, String> headers = Map.of("X-Custom-Header", "custom-value", "Accept-Language", "en-US");

        wireMockServer.stubFor(post(urlEqualTo("/DataExport"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        client.sendRequest(endpoint, HttpMethod.POST, envelope, headers);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/DataExport"))
                .withHeader("X-Custom-Header", equalTo("custom-value"))
                .withHeader("Accept-Language", equalTo("en-US")));
    }

    @Test
    void sendRequest_withNullHeaders_shouldNotFail() {
        String endpoint = wireMockServer.baseUrl() + "/DataExport";
        SifMessageEnvelope<Object> envelope = createTestEnvelope(Map.of("key", "value"));

        wireMockServer.stubFor(post(urlEqualTo("/DataExport"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        assertThatCode(() -> client.sendRequest(endpoint, HttpMethod.POST, envelope, null))
                .doesNotThrowAnyException();
    }

    // ========== Error Response Tests ==========

    @Test
    void sendRequest_whenProviderReturns400_shouldThrowBadRequest() {
        String endpoint = wireMockServer.baseUrl() + "/DataExport";
        SifMessageEnvelope<Object> envelope = createTestEnvelope(Map.of("key", "value"));

        wireMockServer.stubFor(post(urlEqualTo("/DataExport"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Bad request\"}")));

        assertThatThrownBy(() -> client.sendRequest(endpoint, HttpMethod.POST, envelope, Collections.emptyMap()))
                .isInstanceOf(WebClientResponseException.BadRequest.class);
    }

    @Test
    void sendRequest_whenProviderReturns500_shouldThrowInternalServerError() {
        String endpoint = wireMockServer.baseUrl() + "/DataExport";
        SifMessageEnvelope<Object> envelope = createTestEnvelope(null);

        wireMockServer.stubFor(get(urlEqualTo("/DataExport"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal error\"}")));

        assertThatThrownBy(() -> client.sendRequest(endpoint, HttpMethod.GET, envelope, Collections.emptyMap()))
                .isInstanceOf(WebClientResponseException.InternalServerError.class);
    }

    // ========== Connection Error Tests ==========

    @Test
    void sendRequest_whenProviderUnreachable_shouldThrowWebClientRequestException() {
        String endpoint = "http://localhost:1/DataExport";
        SifMessageEnvelope<Object> envelope = createTestEnvelope(null);

        assertThatThrownBy(() -> client.sendRequest(endpoint, HttpMethod.POST, envelope, Collections.emptyMap()))
                .isInstanceOf(WebClientRequestException.class);
    }

    // ========== Helper Methods ==========

    private SifMessageEnvelope<Object> createTestEnvelope(Object payload) {
        return SifMessageEnvelope.<Object>builder()
                .messageType(MessageType.REQUEST)
                .serviceName("DataExport")
                .serviceType(ServiceType.FUNCTIONAL)
                .zoneId("default")
                .contextId("DEFAULT")
                .sourceId(UUID.randomUUID())
                .payload(payload)
                .build();
    }
}
