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
import org.sifunity.requestsconnector.client.QueueServiceClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for QueueServiceClient.
 * Uses WireMock to mock Queue Service HTTP responses.
 * 
 * Tests Requirements:
 * - 4.4: Enqueue request message to provider's queue via Queue Service API
 */
class QueueServiceClientTest {
    
    private WireMockServer wireMockServer;
    private QueueServiceClient client;
    
    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        
        WebClient.Builder webClientBuilder = WebClient.builder();
        client = new QueueServiceClient(webClientBuilder, wireMockServer.baseUrl(), "test-token");
    }
    
    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== Successful Response Tests ==========
    
    @Test
    void enqueueMessage_withValidRequest_shouldReturnMessageId() {
        // Given: A valid message envelope and queue
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID originalMessageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(originalMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "messageId": "%s",
                        "queueId": "%s",
                        "enqueuedAt": "2024-01-15T10:00:00Z"
                    }
                    """, messageId, queueId))));
        
        // When: Enqueuing the message
        UUID result = client.enqueueMessage(queueId, envelope);
        
        // Then: Message ID is returned
        assertThat(result).isEqualTo(messageId);
        
        // Verify correct request was made
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/queues/" + queueId + "/messages"))
            .withRequestBody(matchingJsonPath("$.messageId", equalTo(originalMessageId.toString())))
            .withRequestBody(matchingJsonPath("$.serviceName", equalTo("StudentPersonal")))
            .withRequestBody(matchingJsonPath("$.messageType", equalTo("REQUEST"))));
    }
    
    @Test
    void enqueueMessage_withComplexPayload_shouldEnqueueSuccessfully() {
        // Given: A message with complex nested payload
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID originalMessageId = UUID.randomUUID();
        
        Map<String, Object> complexPayload = Map.of(
            "student", Map.of(
                "id", "123",
                "name", "John Doe",
                "grades", Map.of("math", 95, "science", 88)
            )
        );
        
        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(originalMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(complexPayload)
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "messageId": "%s",
                        "queueId": "%s",
                        "enqueuedAt": "2024-01-15T10:00:00Z"
                    }
                    """, messageId, queueId))));
        
        // When: Enqueuing the message
        UUID result = client.enqueueMessage(queueId, envelope);
        
        // Then: Message ID is returned
        assertThat(result).isEqualTo(messageId);
        
        // Verify request contains complex payload
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/queues/" + queueId + "/messages"))
            .withRequestBody(matchingJsonPath("$.payload.student.id", equalTo("123")))
            .withRequestBody(matchingJsonPath("$.payload.student.name", equalTo("John Doe"))));
    }
    
    @Test
    void enqueueMessage_withNullPayload_shouldEnqueueSuccessfully() {
        // Given: A message with null payload
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID originalMessageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(originalMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(null)
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "messageId": "%s",
                        "queueId": "%s",
                        "enqueuedAt": "2024-01-15T10:00:00Z"
                    }
                    """, messageId, queueId))));
        
        // When: Enqueuing the message
        UUID result = client.enqueueMessage(queueId, envelope);
        
        // Then: Message ID is returned
        assertThat(result).isEqualTo(messageId);
    }

    // ========== Error Response Tests ==========
    
    @Test
    void enqueueMessage_whenQueueNotFound_shouldThrow404() {
        // Given: Queue does not exist
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(messageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Queue not found\"}")));
        
        // When/Then: Should throw 404 exception
        assertThatThrownBy(() -> client.enqueueMessage(queueId, envelope))
            .isInstanceOf(WebClientResponseException.NotFound.class)
            .hasMessageContaining("404");
    }
    
    @Test
    void enqueueMessage_whenServiceUnavailable_shouldThrow503() {
        // Given: Queue Service is unavailable
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(messageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Service temporarily unavailable\"}")));
        
        // When/Then: Should throw 503 exception
        assertThatThrownBy(() -> client.enqueueMessage(queueId, envelope))
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class)
            .hasMessageContaining("503");
    }
    
    @Test
    void enqueueMessage_whenBadRequest_shouldThrow400() {
        // Given: Invalid message format
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(messageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Invalid message format\"}")));
        
        // When/Then: Should throw 400 exception
        assertThatThrownBy(() -> client.enqueueMessage(queueId, envelope))
            .isInstanceOf(WebClientResponseException.BadRequest.class)
            .hasMessageContaining("400");
    }
    
    @Test
    void enqueueMessage_whenInternalServerError_shouldThrow500() {
        // Given: Queue Service has internal error
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(messageId)
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("id", "123"))
            .build();
        
        wireMockServer.stubFor(post(urlPathEqualTo("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Internal server error\"}")));
        
        // When/Then: Should throw 500 exception
        assertThatThrownBy(() -> client.enqueueMessage(queueId, envelope))
            .isInstanceOf(WebClientResponseException.InternalServerError.class)
            .hasMessageContaining("500");
    }
}


