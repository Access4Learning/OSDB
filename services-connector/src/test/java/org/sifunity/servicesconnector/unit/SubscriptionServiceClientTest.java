// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.servicesconnector.client.SubscriptionServiceClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SubscriptionServiceClient.
 * Uses WireMock to mock Subscription Service HTTP responses.
 *
 * <p>Tests Requirements:</p>
 * <ul>
 *   <li>5.2: Query Subscription Service for provider's DELAYED_REQUEST subscription</li>
 * </ul>
 */
@DisplayName("SubscriptionServiceClient")
class SubscriptionServiceClientTest {

    private WireMockServer wireMockServer;
    private SubscriptionServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        WebClient.Builder webClientBuilder = WebClient.builder();
        client = new SubscriptionServiceClient(webClientBuilder, wireMockServer.baseUrl());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("getDelayedRequestSubscription - successful lookup returns SubscriptionDTO")
    void getDelayedRequestSubscription_success_shouldReturnSubscription() {
        // Given
        String domainId = "domain1";
        String solutionId = "solution1";
        String serviceName = "DataExport";
        UUID contextUuid = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo(
                "/" + domainId + "/" + solutionId + "/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("contextUuid", equalTo(contextUuid.toString()))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "id": "%s",
                        "queueId": "%s",
                        "serviceName": "%s",
                        "serviceType": "FUNCTIONAL",
                        "zoneId": "zone1",
                        "contextId": "ctx1",
                        "messageType": "DELAYED_REQUEST",
                        "createdAt": "2024-01-15T10:00:00Z"
                    }
                    """, subscriptionId, queueId, serviceName))));

        // When
        SubscriptionDTO result = client.getDelayedRequestSubscription(
                domainId, solutionId, serviceName, contextUuid);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(subscriptionId);
        assertThat(result.queueId()).isEqualTo(queueId);
        assertThat(result.serviceName()).isEqualTo(serviceName);
    }

    @Test
    @DisplayName("getDelayedRequestSubscription - 404 response returns null")
    void getDelayedRequestSubscription_notFound_shouldReturnNull() {
        // Given
        String domainId = "domain1";
        String solutionId = "solution1";
        String serviceName = "NonExistentService";
        UUID contextUuid = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo(
                "/" + domainId + "/" + solutionId + "/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("contextUuid", equalTo(contextUuid.toString()))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Subscription not found\"}")));

        // When
        SubscriptionDTO result = client.getDelayedRequestSubscription(
                domainId, solutionId, serviceName, contextUuid);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getDelayedRequestSubscription - other exception is rethrown")
    void getDelayedRequestSubscription_serverError_shouldRethrow() {
        // Given
        String domainId = "domain1";
        String solutionId = "solution1";
        String serviceName = "DataExport";
        UUID contextUuid = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo(
                "/" + domainId + "/" + solutionId + "/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("contextUuid", equalTo(contextUuid.toString()))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Internal error\"}")));

        // When/Then
        assertThatThrownBy(() -> client.getDelayedRequestSubscription(
                domainId, solutionId, serviceName, contextUuid))
            .isInstanceOf(WebClientResponseException.class);
    }

    @Test
    @DisplayName("getDelayedRequestSubscription - null contextUuid omits query param")
    void getDelayedRequestSubscription_nullContext_shouldOmitParam() {
        // Given
        String domainId = "domain1";
        String solutionId = "solution1";
        String serviceName = "DataExport";
        UUID subscriptionId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo(
                "/" + domainId + "/" + solutionId + "/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "id": "%s",
                        "queueId": "%s",
                        "serviceName": "%s",
                        "serviceType": "FUNCTIONAL",
                        "zoneId": null,
                        "contextId": null,
                        "messageType": "DELAYED_REQUEST",
                        "createdAt": "2024-01-15T10:00:00Z"
                    }
                    """, subscriptionId, queueId, serviceName))));

        // When
        SubscriptionDTO result = client.getDelayedRequestSubscription(
                domainId, solutionId, serviceName, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.queueId()).isEqualTo(queueId);

        // Verify contextUuid query param is not included
        wireMockServer.verify(getRequestedFor(
                urlPathEqualTo("/" + domainId + "/" + solutionId + "/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withoutQueryParam("contextUuid"));
    }
}
