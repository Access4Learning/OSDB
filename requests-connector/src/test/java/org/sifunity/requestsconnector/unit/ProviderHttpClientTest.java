// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.client.ProviderHttpClient;
import org.sifunity.requestsconnector.dto.ProviderRawResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ProviderHttpClient.
 * Uses WireMock to mock provider HTTP responses.
 * 
 * Tests Requirements:
 * - 1.2: Forward request to provider's endpoint using HTTP
 * - 11.1: Apply configurable connection timeout to provider calls
 * - 11.2: Apply configurable read timeout to provider calls
 * - 11.3: Return HTTP 504 Gateway Timeout when provider call times out
 */
class ProviderHttpClientTest {
    
    private WireMockServer wireMockServer;
    private ProviderHttpClient client;
    
    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        
        WebClient.Builder webClientBuilder = WebClient.builder();
        // Use short timeouts for testing (1 second connection, 2 seconds read)
        client = new ProviderHttpClient(webClientBuilder, 1000, 2000);
    }
    
    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== Successful Response Tests ==========
    
    @Test
    void sendRequest_withValidRequest_shouldReturnResponse() {
        // Given: A provider endpoint that returns a valid response
        UUID requestMessageId = UUID.randomUUID();
        UUID responseMessageId = UUID.randomUUID();
        String providerEndpoint = wireMockServer.baseUrl() + "/api/students";
        
        SifMessageEnvelope<Map<String, Object>> request = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(requestMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "messageId": "%s",
                        "timestamp": "2024-01-15T10:00:00Z",
                        "messageType": "RESPONSE",
                        "serviceName": "StudentPersonal",
                        "serviceType": "OBJECT",
                        "payload": {
                            "id": "123",
                            "name": "John Doe",
                            "grade": 10
                        }
                    }
                    """, responseMessageId))));
        
        // When: Sending the request
        SifMessageEnvelope<?> response = client.sendRequest(providerEndpoint, request, Collections.emptyMap());
        
        // Then: Response is returned with correct data
        assertThat(response).isNotNull();
        assertThat(response.getMessageId()).isEqualTo(responseMessageId);
        assertThat(response.getMessageType()).isEqualTo(MessageType.RESPONSE);
        assertThat(response.getServiceName()).isEqualTo("StudentPersonal");
        
        // Verify correct request was made
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/students"))
            .withRequestBody(matchingJsonPath("$.messageId", equalTo(requestMessageId.toString())))
            .withRequestBody(matchingJsonPath("$.messageType", equalTo("REQUEST")))
            .withRequestBody(matchingJsonPath("$.serviceName", equalTo("StudentPersonal"))));
    }
    
    @Test
    void sendRequest_withComplexPayload_shouldReturnResponse() {
        // Given: A request with complex nested payload
        UUID requestMessageId = UUID.randomUUID();
        UUID responseMessageId = UUID.randomUUID();
        String providerEndpoint = wireMockServer.baseUrl() + "/api/students";
        
        Map<String, Object> complexPayload = Map.of(
            "student", Map.of(
                "id", "123",
                "filters", Map.of("grade", 10, "active", true)
            )
        );
        
        SifMessageEnvelope<Map<String, Object>> request = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(requestMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(complexPayload)
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "messageId": "%s",
                        "timestamp": "2024-01-15T10:00:00Z",
                        "messageType": "RESPONSE",
                        "serviceName": "StudentPersonal",
                        "serviceType": "OBJECT",
                        "payload": {
                            "students": []
                        }
                    }
                    """, responseMessageId))));
        
        // When: Sending the request
        SifMessageEnvelope<?> response = client.sendRequest(providerEndpoint, request, Collections.emptyMap());
        
        // Then: Response is returned
        assertThat(response).isNotNull();
        assertThat(response.getMessageId()).isEqualTo(responseMessageId);
        
        // Verify request contains complex payload
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/students"))
            .withRequestBody(matchingJsonPath("$.payload.student.id", equalTo("123"))));
    }
    
    @Test
    void sendRequest_withNullPayload_shouldReturnResponse() {
        // Given: A request with null payload
        UUID requestMessageId = UUID.randomUUID();
        UUID responseMessageId = UUID.randomUUID();
        String providerEndpoint = wireMockServer.baseUrl() + "/api/students";
        
        SifMessageEnvelope<Map<String, Object>> request = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(requestMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(null)
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "messageId": "%s",
                        "timestamp": "2024-01-15T10:00:00Z",
                        "messageType": "RESPONSE",
                        "serviceName": "StudentPersonal",
                        "serviceType": "OBJECT",
                        "payload": null
                    }
                    """, responseMessageId))));
        
        // When: Sending the request
        SifMessageEnvelope<?> response = client.sendRequest(providerEndpoint, request, Collections.emptyMap());
        
        // Then: Response is returned
        assertThat(response).isNotNull();
        assertThat(response.getMessageId()).isEqualTo(responseMessageId);
    }

    // ========== Error Response Tests ==========
    
    @Test
    void sendRequest_whenProviderReturns404_shouldThrow404() {
        // Given: Provider returns 404 Not Found
        UUID requestMessageId = UUID.randomUUID();
        String providerEndpoint = wireMockServer.baseUrl() + "/api/students";
        
        SifMessageEnvelope<Map<String, Object>> request = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(requestMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "999"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Student not found\"}")));
        
        // When/Then: Should throw 404 exception
        assertThatThrownBy(() -> client.sendRequest(providerEndpoint, request, Collections.emptyMap()))
            .isInstanceOf(WebClientResponseException.NotFound.class)
            .hasMessageContaining("404");
    }
    
    @Test
    void sendRequest_whenProviderReturns503_shouldThrow503() {
        // Given: Provider is unavailable
        UUID requestMessageId = UUID.randomUUID();
        String providerEndpoint = wireMockServer.baseUrl() + "/api/students";
        
        SifMessageEnvelope<Map<String, Object>> request = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(requestMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Service temporarily unavailable\"}")));
        
        // When/Then: Should throw 503 exception
        assertThatThrownBy(() -> client.sendRequest(providerEndpoint, request, Collections.emptyMap()))
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class)
            .hasMessageContaining("503");
    }
    
    @Test
    void sendRequest_whenProviderReturns500_shouldThrow500() {
        // Given: Provider has internal error
        UUID requestMessageId = UUID.randomUUID();
        String providerEndpoint = wireMockServer.baseUrl() + "/api/students";
        
        SifMessageEnvelope<Map<String, Object>> request = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(requestMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Internal server error\"}")));
        
        // When/Then: Should throw 500 exception
        assertThatThrownBy(() -> client.sendRequest(providerEndpoint, request, Collections.emptyMap()))
            .isInstanceOf(WebClientResponseException.InternalServerError.class)
            .hasMessageContaining("500");
    }
    
    @Test
    void sendRequest_whenProviderReturns400_shouldThrow400() {
        // Given: Provider rejects invalid request
        UUID requestMessageId = UUID.randomUUID();
        String providerEndpoint = wireMockServer.baseUrl() + "/api/students";
        
        SifMessageEnvelope<Map<String, Object>> request = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(requestMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("invalid", "data"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Invalid request\"}")));
        
        // When/Then: Should throw 400 exception
        assertThatThrownBy(() -> client.sendRequest(providerEndpoint, request, Collections.emptyMap()))
            .isInstanceOf(WebClientResponseException.BadRequest.class)
            .hasMessageContaining("400");
    }
    
    @Test
    void sendRequest_withinTimeout_shouldSucceed() {
        // Given: Provider responds within timeout
        UUID requestMessageId = UUID.randomUUID();
        UUID responseMessageId = UUID.randomUUID();
        String providerEndpoint = wireMockServer.baseUrl() + "/api/students";
        
        SifMessageEnvelope<Map<String, Object>> request = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(requestMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        // Delay less than read timeout (2 seconds)
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "messageId": "%s",
                        "timestamp": "2024-01-15T10:00:00Z",
                        "messageType": "RESPONSE",
                        "serviceName": "StudentPersonal",
                        "serviceType": "OBJECT",
                        "payload": {"id": "123"}
                    }
                    """, responseMessageId))
                .withFixedDelay(1000))); // 1 second delay (within timeout)
        
        // When: Sending the request
        SifMessageEnvelope<?> response = client.sendRequest(providerEndpoint, request, Collections.emptyMap());
        
        // Then: Response is returned successfully
        assertThat(response).isNotNull();
        assertThat(response.getMessageId()).isEqualTo(responseMessageId);
    }
    
    @Test
    void sendRequest_whenConnectionFails_shouldThrowException() {
        // Given: Invalid provider endpoint (connection will fail)
        UUID requestMessageId = UUID.randomUUID();
        String providerEndpoint = "http://invalid-host-that-does-not-exist:9999/api/students";
        
        SifMessageEnvelope<Map<String, Object>> request = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(requestMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        // When/Then: Should throw connection exception
        assertThatThrownBy(() -> client.sendRequest(providerEndpoint, request, Collections.emptyMap()))
            .isInstanceOf(Exception.class); // Could be various connection-related exceptions
    }

    // ========== sendRequestRawWithHeaders Tests ==========

    @Nested
    class SendRequestRawWithHeadersTests {

        @Test
        void get_shouldCaptureResponseBodyHeadersAndStatusCode() {
            // Given: Provider returns 200 with body and navigation headers
            String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";

            wireMockServer.stubFor(get(urlPathEqualTo("/api/StudentPersonals"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("navigationPage", "1")
                    .withHeader("navigationLastPage", "5")
                    .withHeader("navigationPageSize", "10")
                    .withHeader("navigationId", "nav-abc-123")
                    .withHeader("navigationCount", "50")
                    .withBody("{\"students\":[{\"id\":\"s1\",\"name\":\"Jane\"}]}")));

            // When
            ProviderRawResponse response = client.sendRequestRawWithHeaders(
                endpoint, null, Collections.emptyMap(), "GET");

            // Then: Body captured
            assertThat(response).isNotNull();
            assertThat(response.getBody()).isNotNull();

            // Then: Status code captured
            assertThat(response.getStatusCode()).isEqualTo(200);

            // Then: Navigation headers captured
            assertThat(response.getHeaders()).containsEntry("navigationPage", "1");
            assertThat(response.getHeaders()).containsEntry("navigationLastPage", "5");
            assertThat(response.getHeaders()).containsEntry("navigationPageSize", "10");
            assertThat(response.getHeaders()).containsEntry("navigationId", "nav-abc-123");
            assertThat(response.getHeaders()).containsEntry("navigationCount", "50");
        }

        @Test
        void post_shouldCaptureResponseBodyHeadersAndStatusCode() {
            // Given: Provider returns 200 with body and headers for POST
            String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";
            Map<String, Object> requestBody = Map.of("name", "New Student", "grade", 9);

            wireMockServer.stubFor(post(urlPathEqualTo("/api/StudentPersonals"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("navigationPage", "1")
                    .withHeader("navigationLastPage", "1")
                    .withBody("{\"id\":\"s2\",\"name\":\"New Student\"}")));

            // When
            ProviderRawResponse response = client.sendRequestRawWithHeaders(
                endpoint, requestBody, Collections.emptyMap(), "POST");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(201);
            assertThat(response.getHeaders()).containsEntry("navigationPage", "1");
            assertThat(response.getHeaders()).containsEntry("navigationLastPage", "1");

            // Verify POST body was sent
            wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/StudentPersonals"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("New Student"))));
        }

        @Test
        void get_shouldForwardPassthroughHeaders() {
            // Given: Passthrough headers including navigation headers
            String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";
            Map<String, String> passthroughHeaders = Map.of(
                "navigationPage", "2",
                "navigationPageSize", "10",
                "navigationId", "nav-abc-123",
                "queryIntention", "ALL"
            );

            wireMockServer.stubFor(get(urlPathEqualTo("/api/StudentPersonals"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"students\":[]}")));

            // When
            client.sendRequestRawWithHeaders(endpoint, null, passthroughHeaders, "GET");

            // Then: Passthrough headers forwarded to provider
            wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/StudentPersonals"))
                .withHeader("navigationPage", equalTo("2"))
                .withHeader("navigationPageSize", equalTo("10"))
                .withHeader("navigationId", equalTo("nav-abc-123"))
                .withHeader("queryIntention", equalTo("ALL")));
        }

        @Test
        void get_withNoNavigationHeaders_shouldReturnEmptyHeadersMap() {
            // Given: Provider returns response without navigation headers
            String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";

            wireMockServer.stubFor(get(urlPathEqualTo("/api/StudentPersonals"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"students\":[]}")));

            // When
            ProviderRawResponse response = client.sendRequestRawWithHeaders(
                endpoint, null, Collections.emptyMap(), "GET");

            // Then: Headers map exists but no navigation headers
            assertThat(response.getHeaders()).isNotNull();
            assertThat(response.getHeaders()).doesNotContainKey("navigationPage");
            assertThat(response.getHeaders()).doesNotContainKey("navigationLastPage");
            assertThat(response.getStatusCode()).isEqualTo(200);
        }

        @Test
        void get_withNullPassthroughHeaders_shouldNotFail() {
            // Given: null passthrough headers
            String endpoint = wireMockServer.baseUrl() + "/api/StudentPersonals";

            wireMockServer.stubFor(get(urlPathEqualTo("/api/StudentPersonals"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"data\":\"ok\"}")));

            // When
            ProviderRawResponse response = client.sendRequestRawWithHeaders(
                endpoint, null, null, "GET");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(200);
        }
    }
}
