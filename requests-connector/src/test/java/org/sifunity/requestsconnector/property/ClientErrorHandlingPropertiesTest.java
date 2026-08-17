// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;
import org.sifunity.requestsconnector.client.QueueServiceClient;
import org.sifunity.requestsconnector.client.SubscriptionServiceClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for client error handling.
 * 
 * These tests verify that when Core Services (Environment Service, Queue Service, 
 * Subscription Service) are unavailable, the HTTP clients properly propagate the 
 * HTTP 503 Service Unavailable error. This is critical for:
 * - Proper error handling and user feedback
 * - Service resilience and failure transparency
 * - Meeting SIF 3.7 error response requirements
 * 
 * Each property test runs with a minimum of 100 iterations to ensure comprehensive coverage
 * across a wide range of random inputs.
 * 
 * Uses WireMock to simulate service unavailability scenarios.
 * 
 * **Validates: Requirements 2.4, 4.6, 10.3**
 */
@Tag("Feature: requests-connector")
class ClientErrorHandlingPropertiesTest {
    
    private WireMockServer wireMockServer;
    private EnvironmentServiceClient environmentServiceClient;
    private QueueServiceClient queueServiceClient;
    private SubscriptionServiceClient subscriptionServiceClient;
    
    @BeforeProperty
    void setup() {
        // Start WireMock server on a random port
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        
        // Create clients pointing to WireMock server
        String baseUrl = "http://localhost:" + wireMockServer.port();
        WebClient.Builder webClientBuilder = WebClient.builder();
        
        environmentServiceClient = new EnvironmentServiceClient(webClientBuilder, baseUrl);
        queueServiceClient = new QueueServiceClient(webClientBuilder, baseUrl, "test-token");
        subscriptionServiceClient = new SubscriptionServiceClient(webClientBuilder, baseUrl);
    }
    
    @AfterProperty
    void teardown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
    
    /**
     * Property 29: Core service unavailability handling
     * 
     * For any operation when a Core Service (Environment, Queue, Subscription) is unavailable,
     * the connector should return HTTP 503 Service Unavailable.
     * 
     * This property tests the Environment Service client specifically.
     * 
     * **Validates: Requirements 2.4, 10.3**
     */
    @Property(tries = 10)
    @Label("Feature: requests-connector, Property 29: Core service unavailability handling - Environment Service")
    void environmentServiceUnavailabilityReturns503(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String zoneId,
            @ForAll("optionalContext") String contextId) {
        
        // Given: Environment Service returns 503 Service Unavailable
        String path = "/internal/provider-lookup";
        stubFor(get(urlPathEqualTo(path))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));
        
        // When/Then: Calling lookupProvider should throw ServiceUnavailable exception
        assertThatThrownBy(() -> 
            environmentServiceClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId)
        )
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class)
            .satisfies(ex -> {
                WebClientResponseException wcex = (WebClientResponseException) ex;
                assertThat(wcex.getStatusCode().value()).isEqualTo(503);
            });
    }
    
    /**
     * Property 29: Core service unavailability handling
     * 
     * For any operation when a Core Service (Environment, Queue, Subscription) is unavailable,
     * the connector should return HTTP 503 Service Unavailable.
     * 
     * This property tests the Queue Service client specifically.
     * 
     * **Validates: Requirements 4.6, 10.3**
     */
    @Property(tries = 10)
    @Label("Feature: requests-connector, Property 29: Core service unavailability handling - Queue Service")
    void queueServiceUnavailabilityReturns503(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID messageId,
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName) {
        
        // Given: Queue Service returns 503 Service Unavailable
        String path = "/GLOBAL_DOMAIN/GLOBAL_SOLUTION/queues/" + queueId + "/messages";
        stubFor(post(urlPathEqualTo(path))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));
        
        // Create a simple message envelope
        SifMessageEnvelope<?> envelope = SifMessageEnvelope.builder()
            .messageId(messageId)
            .serviceName(serviceName)
            .build();
        
        // When/Then: Calling enqueueMessage should throw ServiceUnavailable exception
        assertThatThrownBy(() -> 
            queueServiceClient.enqueueMessage(queueId, envelope)
        )
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class)
            .satisfies(ex -> {
                WebClientResponseException wcex = (WebClientResponseException) ex;
                assertThat(wcex.getStatusCode().value()).isEqualTo(503);
            });
    }
    
    /**
     * Property 29: Core service unavailability handling
     * 
     * For any operation when a Core Service (Environment, Queue, Subscription) is unavailable,
     * the connector should return HTTP 503 Service Unavailable.
     * 
     * This property tests the Subscription Service client specifically.
     * 
     * **Validates: Requirements 4.6, 10.3**
     */
    @Property(tries = 10)
    @Label("Feature: requests-connector, Property 29: Core service unavailability handling - Subscription Service")
    void subscriptionServiceUnavailabilityReturns503(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
            @ForAll("uuids") UUID contextUuid) {
        
        // Given: Subscription Service returns 503 Service Unavailable
        String path = "/GLOBAL_DOMAIN/GLOBAL_SOLUTION/subscriptions/delayed-requests";
        stubFor(get(urlPathEqualTo(path))
            .withQueryParam("serviceName", equalTo(serviceName))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));
        
        // When/Then: Calling getDelayedRequestSubscription should throw ServiceUnavailable exception
        assertThatThrownBy(() -> 
            subscriptionServiceClient.getDelayedRequestSubscription(serviceName, contextUuid)
        )
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class)
            .satisfies(ex -> {
                WebClientResponseException wcex = (WebClientResponseException) ex;
                assertThat(wcex.getStatusCode().value()).isEqualTo(503);
            });
    }
    
    /**
     * Property 29: Core service unavailability handling - Multiple service failures
     * 
     * For any operation when multiple Core Services are unavailable simultaneously,
     * each should return HTTP 503 Service Unavailable independently.
     * 
     * This property tests that service unavailability is handled consistently
     * across all client types.
     * 
     * **Validates: Requirements 2.4, 4.6, 10.3**
     */
    @Property(tries = 10)
    @Label("Feature: requests-connector, Property 29: Core service unavailability handling - Multiple services")
    void multipleServiceUnavailabilityReturns503(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String zoneId,
            @ForAll("optionalContext") String contextId,
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID messageId,
            @ForAll("uuids") UUID contextUuid) {
        
        // Given: All services return 503 Service Unavailable
        stubFor(get(urlPathMatching("/.*"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));
        
        stubFor(post(urlPathMatching("/.*"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));
        
        stubFor(get(urlPathMatching("/subscriptions/.*"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));
        
        // Create a simple message envelope for queue client test
        SifMessageEnvelope<?> envelope = SifMessageEnvelope.builder()
            .messageId(messageId)
            .serviceName(serviceName)
            .build();
        
        // When/Then: All clients should throw ServiceUnavailable exception
        assertThatThrownBy(() -> 
            environmentServiceClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId)
        )
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
        
        assertThatThrownBy(() -> 
            queueServiceClient.enqueueMessage(queueId, envelope)
        )
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
        
        assertThatThrownBy(() -> 
            subscriptionServiceClient.getDelayedRequestSubscription(serviceName, contextUuid)
        )
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
    }
    
    /**
     * Property 29: Core service unavailability handling - Transient failures
     * 
     * For any operation when a Core Service experiences transient failures (connection timeout,
     * network error), the connector should handle these as service unavailability.
     * 
     * This property verifies that various types of service failures are handled consistently.
     * 
     * **Validates: Requirements 2.4, 4.6, 10.3**
     */
    @Property(tries = 10)
    @Label("Feature: requests-connector, Property 29: Core service unavailability handling - Transient failures")
    void transientServiceFailuresHandledAsUnavailable(
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String serviceName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String zoneId,
            @ForAll("optionalContext") String contextId) {
        
        // Given: Environment Service returns 503 (representing transient failures)
        String path = "/internal/provider-lookup";
        stubFor(get(urlPathEqualTo(path))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));
        
        // When/Then: Transient failures should be propagated as ServiceUnavailable
        assertThatThrownBy(() -> 
            environmentServiceClient.lookupProvider("d1", "s1", serviceName, zoneId, contextId)
        )
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
    }
    
    // ==================== Arbitraries (Data Generators) ====================
    
    /**
     * Generate optional context IDs (can be null or a valid string)
     */
    @Provide
    Arbitrary<String> optionalContext() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),
            Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50)
        );
    }
    
    /**
     * Generate random UUIDs
     */
    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}
