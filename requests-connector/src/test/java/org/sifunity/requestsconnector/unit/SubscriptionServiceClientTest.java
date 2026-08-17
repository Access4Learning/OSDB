// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.requestsconnector.client.SubscriptionServiceClient;
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
 * Tests Requirements:
 * - 4.1: Query Subscription Service for provider's request subscription
 * - 4.2: Extract provider's queueId from subscription
 * - 4.3: Return null if provider has no request subscription (404)
 */
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

    // ========== Successful Response Tests ==========
    
    @Test
    void getDelayedRequestSubscription_withContextUuid_shouldReturnSubscription() {
        // Given: A DELAYED_REQUEST subscription exists
        UUID subscriptionId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        
        wireMockServer.stubFor(get(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/subscriptions/delayed-requests"))
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
                        "zoneId": "test-zone",
                        "contextId": "test-context",
                        "messageType": "DELAYED_REQUEST",
                        "createdAt": "2024-01-15T10:00:00Z"
                    }
                    """, subscriptionId, queueId, serviceName))));
        
        // When: Querying for the subscription
        SubscriptionDTO result = client.getDelayedRequestSubscription(serviceName, contextUuid);
        
        // Then: Subscription is returned with correct data
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(subscriptionId);
        assertThat(result.queueId()).isEqualTo(queueId);
        assertThat(result.serviceName()).isEqualTo(serviceName);
        assertThat(result.zoneId()).isEqualTo("test-zone");
        assertThat(result.contextId()).isEqualTo("test-context");
        
        // Verify correct request was made
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("contextUuid", equalTo(contextUuid.toString())));
    }
    
    @Test
    void getDelayedRequestSubscription_withNullContextUuid_shouldNotIncludeContextParam() {
        // Given: A subscription without context (wildcard)
        UUID subscriptionId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        
        wireMockServer.stubFor(get(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "id": "%s",
                        "queueId": "%s",
                        "serviceName": "%s",
                        "zoneId": null,
                        "contextId": null,
                        "messageType": "DELAYED_REQUEST",
                        "createdAt": "2024-01-15T10:00:00Z"
                    }
                    """, subscriptionId, queueId, serviceName))));
        
        // When: Querying with null contextUuid
        SubscriptionDTO result = client.getDelayedRequestSubscription(serviceName, null);
        
        // Then: Subscription is returned without zoneId/contextId
        assertThat(result).isNotNull();
        assertThat(result.queueId()).isEqualTo(queueId);
        assertThat(result.serviceName()).isEqualTo(serviceName);
        assertThat(result.zoneId()).isNull();
        assertThat(result.contextId()).isNull();
        
        // Verify contextUuid query param is not included
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withoutQueryParam("contextUuid"));
    }

    // ========== Error Response Tests ==========
    
    @Test
    void getDelayedRequestSubscription_whenNotFound_shouldReturnNull() {
        // Given: No subscription exists for the provider
        String serviceName = "NonExistentService";
        UUID contextUuid = UUID.randomUUID();
        
        wireMockServer.stubFor(get(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("contextUuid", equalTo(contextUuid.toString()))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Subscription not found\"}")));
        
        // When: Querying for non-existent subscription
        SubscriptionDTO result = client.getDelayedRequestSubscription(serviceName, contextUuid);
        
        // Then: Should return null (not throw exception)
        assertThat(result).isNull();
    }
    
    @Test
    void getDelayedRequestSubscription_whenServiceUnavailable_shouldThrow503() {
        // Given: Subscription Service is unavailable
        String serviceName = "StudentPersonal";
        UUID contextUuid = UUID.randomUUID();
        
        wireMockServer.stubFor(get(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("contextUuid", equalTo(contextUuid.toString()))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Service temporarily unavailable\"}")));
        
        // When/Then: Should throw 503 exception
        assertThatThrownBy(() -> client.getDelayedRequestSubscription(serviceName, contextUuid))
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class)
            .hasMessageContaining("503");
    }
    
    @Test
    void getDelayedRequestSubscription_whenInternalServerError_shouldThrow500() {
        // Given: Subscription Service has internal error
        String serviceName = "StudentPersonal";
        UUID contextUuid = UUID.randomUUID();
        
        wireMockServer.stubFor(get(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("contextUuid", equalTo(contextUuid.toString()))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Internal server error\"}")));
        
        // When/Then: Should throw 500 exception
        assertThatThrownBy(() -> client.getDelayedRequestSubscription(serviceName, contextUuid))
            .isInstanceOf(WebClientResponseException.InternalServerError.class)
            .hasMessageContaining("500");
    }
    
    @Test
    void getDelayedRequestSubscription_whenBadRequest_shouldThrow400() {
        // Given: Invalid query parameters
        String serviceName = "StudentPersonal";
        UUID contextUuid = UUID.randomUUID();
        
        wireMockServer.stubFor(get(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/subscriptions/delayed-requests"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("contextUuid", equalTo(contextUuid.toString()))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Invalid query parameters\"}")));
        
        // When/Then: Should throw 400 exception
        assertThatThrownBy(() -> client.getDelayedRequestSubscription(serviceName, contextUuid))
            .isInstanceOf(WebClientResponseException.BadRequest.class)
            .hasMessageContaining("400");
    }
}
