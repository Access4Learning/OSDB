// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.client.ResponsesConnectorClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ResponsesConnectorClient.
 * Uses WireMock to mock Responses Connector HTTP responses.
 * 
 * Tests Requirements:
 * - 9.1: Call Responses Connector API when provider returns response
 * - 9.2: Include consumer's queueId in the request
 * - 9.3: Include original requestId for correlation
 * - 9.4: Include provider's response payload
 */
class ResponsesConnectorClientTest {
    
    private WireMockServer wireMockServer;
    private ResponsesConnectorClient client;
    
    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        
        WebClient.Builder webClientBuilder = WebClient.builder();
        client = new ResponsesConnectorClient(wireMockServer.baseUrl(), "test-internal-token");
    }
    
    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== Successful Response Tests ==========
    
    @Test
    void routeResponse_withValidRequest_shouldSucceed() {
        // Given: A valid response to route
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        UUID consumerQueueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID responseMessageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> response = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(responseMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123", "name", "John Doe"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/internal/responses"))
            .withHeader("X-Environment-Id", equalTo(environmentId.toString()))
            .withHeader("X-Environment-Name", equalTo(environmentName))
            .withHeader("queueId", equalTo(consumerQueueId.toString()))
            .withHeader("requestId", equalTo(requestId.toString()))
            .willReturn(aResponse()
                .withStatus(200)));
        
        // When: Routing the response
        // Then: Should not throw exception
        assertThatCode(() -> client.routeResponse(environmentId, environmentName, "test-domain", "test-solution", consumerQueueId, requestId, response, "test-path"))
            .doesNotThrowAnyException();
        
        // Verify correct request was made
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/internal/responses"))
            .withHeader("X-Environment-Id", equalTo(environmentId.toString()))
            .withHeader("X-Environment-Name", equalTo(environmentName))
            .withHeader("queueId", equalTo(consumerQueueId.toString()))
            .withHeader("requestId", equalTo(requestId.toString())));
    }
    
    @Test
    void routeResponse_withComplexPayload_shouldSucceed() {
        // Given: A response with complex nested payload
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        UUID consumerQueueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID responseMessageId = UUID.randomUUID();
        
        Map<String, Object> complexPayload = Map.of(
            "student", Map.of(
                "id", "123",
                "name", "John Doe",
                "grades", Map.of("math", 95, "science", 88),
                "courses", java.util.List.of("Math", "Science", "English")
            )
        );
        
        SifMessageEnvelope<Map<String, Object>> response = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(responseMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(complexPayload)
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/internal/responses"))
            .withHeader("X-Environment-Id", equalTo(environmentId.toString()))
            .withHeader("X-Environment-Name", equalTo(environmentName))
            .withHeader("queueId", equalTo(consumerQueueId.toString()))
            .withHeader("requestId", equalTo(requestId.toString()))
            .willReturn(aResponse()
                .withStatus(200)));
        
        // When: Routing the response
        // Then: Should not throw exception
        assertThatCode(() -> client.routeResponse(environmentId, environmentName, "test-domain", "test-solution", consumerQueueId, requestId, response, "test-path"))
            .doesNotThrowAnyException();
        
        // Verify request contains complex payload
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/internal/responses"))
            .withRequestBody(matchingJsonPath("$.payload.student.id", equalTo("123")))
            .withRequestBody(matchingJsonPath("$.payload.student.name", equalTo("John Doe"))));
    }
    
    @Test
    void routeResponse_withNullPayload_shouldSucceed() {
        // Given: A response with null payload
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        UUID consumerQueueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID responseMessageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> response = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(responseMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(null)
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/internal/responses"))
            .withHeader("X-Environment-Id", equalTo(environmentId.toString()))
            .withHeader("X-Environment-Name", equalTo(environmentName))
            .withHeader("queueId", equalTo(consumerQueueId.toString()))
            .withHeader("requestId", equalTo(requestId.toString()))
            .willReturn(aResponse()
                .withStatus(200)));
        
        // When: Routing the response
        // Then: Should not throw exception
        assertThatCode(() -> client.routeResponse(environmentId, environmentName, "test-domain", "test-solution", consumerQueueId, requestId, response, "test-path"))
            .doesNotThrowAnyException();
    }
    
    @Test
    void routeErrorResponse_withValidRequest_shouldSucceed() {
        // Given: An error message to route
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        UUID consumerQueueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String errorMessage = "Provider call failed after 3 retries: Connection timeout";
        
        wireMockServer.stubFor(post(urlPathEqualTo("/internal/responses/error"))
            .withHeader("X-Environment-Id", equalTo(environmentId.toString()))
            .withHeader("X-Environment-Name", equalTo(environmentName))
            .withHeader("queueId", equalTo(consumerQueueId.toString()))
            .withHeader("requestId", equalTo(requestId.toString()))
            .willReturn(aResponse()
                .withStatus(200)));
        
        // When: Routing the error response
        // Then: Should not throw exception
        assertThatCode(() -> client.routeErrorResponse(environmentId, environmentName, "test-domain", "test-solution", consumerQueueId, requestId, "StudentPersonal", errorMessage))
            .doesNotThrowAnyException();
        
        // Verify correct request was made
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/internal/responses/error"))
            .withHeader("X-Environment-Id", equalTo(environmentId.toString()))
            .withHeader("X-Environment-Name", equalTo(environmentName))
            .withHeader("queueId", equalTo(consumerQueueId.toString()))
            .withHeader("requestId", equalTo(requestId.toString()))
            // Field names must follow SifErrorResponse: "code" is the HTTP status integer and
            // "subCode" the identifier. This previously sent the identifier as "code", leaving the
            // receiver with neither a status nor a subCode.
            .withRequestBody(matchingJsonPath("$.error.code", equalTo("502")))
            .withRequestBody(matchingJsonPath("$.error.subCode", equalTo("PROVIDER_ERROR")))
            .withRequestBody(matchingJsonPath("$.error.scope", equalTo("PROVIDER")))
            .withRequestBody(matchingJsonPath("$.error.message", equalTo(errorMessage))));
    }

    // ========== Error Response Tests ==========
    
    @Test
    void routeResponse_whenServiceUnavailable_shouldThrow503() {
        // Given: Responses Connector is unavailable
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        UUID consumerQueueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID responseMessageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> response = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(responseMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/internal/responses"))
            .withHeader("X-Environment-Id", equalTo(environmentId.toString()))
            .withHeader("X-Environment-Name", equalTo(environmentName))
            .withHeader("queueId", equalTo(consumerQueueId.toString()))
            .withHeader("requestId", equalTo(requestId.toString()))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Service temporarily unavailable\"}")));
        
        // When/Then: Should throw 503 exception
        assertThatThrownBy(() -> client.routeResponse(environmentId, environmentName, "test-domain", "test-solution", consumerQueueId, requestId, response, "test-path"))
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class)
            .hasMessageContaining("503");
    }
    
    @Test
    void routeResponse_whenQueueNotFound_shouldThrow404() {
        // Given: Consumer queue does not exist
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        UUID consumerQueueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID responseMessageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> response = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(responseMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/internal/responses"))
            .withHeader("X-Environment-Id", equalTo(environmentId.toString()))
            .withHeader("X-Environment-Name", equalTo(environmentName))
            .withHeader("queueId", equalTo(consumerQueueId.toString()))
            .withHeader("requestId", equalTo(requestId.toString()))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Queue not found\"}")));
        
        // When/Then: Should throw 404 exception
        assertThatThrownBy(() -> client.routeResponse(environmentId, environmentName, "test-domain", "test-solution", consumerQueueId, requestId, response, "test-path"))
            .isInstanceOf(WebClientResponseException.NotFound.class)
            .hasMessageContaining("404");
    }
    
    @Test
    void routeResponse_whenInternalServerError_shouldThrow500() {
        // Given: Responses Connector has internal error
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        UUID consumerQueueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID responseMessageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> response = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(responseMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/internal/responses"))
            .withHeader("X-Environment-Id", equalTo(environmentId.toString()))
            .withHeader("X-Environment-Name", equalTo(environmentName))
            .withHeader("queueId", equalTo(consumerQueueId.toString()))
            .withHeader("requestId", equalTo(requestId.toString()))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Internal server error\"}")));
        
        // When/Then: Should throw 500 exception
        assertThatThrownBy(() -> client.routeResponse(environmentId, environmentName, "test-domain", "test-solution", consumerQueueId, requestId, response, "test-path"))
            .isInstanceOf(WebClientResponseException.InternalServerError.class)
            .hasMessageContaining("500");
    }
    
    @Test
    void routeErrorResponse_whenServiceUnavailable_shouldThrow503() {
        // Given: Responses Connector is unavailable
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        UUID consumerQueueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String errorMessage = "Provider call failed";
        
        wireMockServer.stubFor(post(urlPathEqualTo("/internal/responses/error"))
            .withHeader("X-Environment-Id", equalTo(environmentId.toString()))
            .withHeader("X-Environment-Name", equalTo(environmentName))
            .withHeader("queueId", equalTo(consumerQueueId.toString()))
            .withHeader("requestId", equalTo(requestId.toString()))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Service temporarily unavailable\"}")));
        
        // When/Then: Should throw 503 exception
        assertThatThrownBy(() -> client.routeErrorResponse(environmentId, environmentName, "test-domain", "test-solution", consumerQueueId, requestId, "TestService", errorMessage))
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class)
            .hasMessageContaining("503");
    }

    }
