// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.responsesconnector.client.QueueServiceClient;
import org.sifunity.responsesconnector.exception.QueueNotFoundException;
import org.sifunity.responsesconnector.exception.QueueServiceUnavailableException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
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
 * - 9.1: Call Queue Service enqueue API with queueId and message envelope
 * - 9.2: Return success when Queue Service returns success
 * - 9.3: Throw QueueNotFoundException when Queue Service returns 404
 * - 9.4: Throw QueueServiceUnavailableException when Queue Service is unavailable
 * - 9.5: Throw QueueServiceUnavailableException when Queue Service call times out
 * - 12.1: Retry on transient errors (5xx)
 * - 12.2: Use exponential backoff for retries
 * - 12.3: Retry up to configurable maximum (default: 3 attempts)
 * - 12.4: Return 503 when all retries are exhausted
 * - 12.6: Do NOT retry for client errors (4xx)
 */
class QueueServiceClientTest {

    private WireMockServer wireMockServer;
    private QueueServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(wireMockServer.baseUrl())
                .build();

        client = new QueueServiceClient(restClient, wireMockServer.baseUrl(), 3, 100, "test-token");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== Successful Response Tests (Requirement 9.1, 9.2) ==========

    @Test
    void enqueueMessage_withValidRequest_shouldReturnMessageId() {
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID originalMessageId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageId(originalMessageId)
            .timestamp(Instant.now())
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(Map.of("id", "123", "name", "John Doe"))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {"messageId": "%s", "status": "ENQUEUED"}
                    """, messageId))));

        UUID result = client.enqueueMessage("test-domain", "test-solution", queueId, envelope);

        assertThat(result).isEqualTo(messageId);
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages")));
    }

    @Test
    void enqueueMessage_withComplexPayload_shouldEnqueueSuccessfully() {
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(Map.of("student", Map.of("id", "123", "name", "John Doe")))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {"messageId": "%s", "status": "ENQUEUED"}
                    """, messageId))));

        UUID result = client.enqueueMessage("test-domain", "test-solution", queueId, envelope);
        assertThat(result).isEqualTo(messageId);
    }

    @Test
    void enqueueMessage_withNullPayload_shouldEnqueueSuccessfully() {
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(null)
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {"messageId": "%s", "status": "ENQUEUED"}
                    """, messageId))));

        UUID result = client.enqueueMessage("test-domain", "test-solution", queueId, envelope);
        assertThat(result).isEqualTo(messageId);
    }

    // ========== Queue Not Found Tests (Requirement 9.3) ==========

    @Test
    void enqueueMessage_whenQueueNotFound_shouldThrowQueueNotFoundException() {
        UUID queueId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(Map.of("id", "123"))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Queue not found\"}")));

        assertThatThrownBy(() -> client.enqueueMessage("test-domain", "test-solution", queueId, envelope))
            .isInstanceOf(QueueNotFoundException.class)
            .hasMessageContaining("Queue not found: " + queueId);

        // No retries for 404
        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages")));
    }

    // ========== Service Unavailable Tests (Requirement 9.4, 12.4) ==========

    @Test
    void enqueueMessage_whenServiceUnavailable_shouldThrowQueueServiceUnavailableException() {
        UUID queueId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(Map.of("id", "123"))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Service temporarily unavailable\"}")));

        assertThatThrownBy(() -> client.enqueueMessage("test-domain", "test-solution", queueId, envelope))
            .isInstanceOf(QueueServiceUnavailableException.class)
            .hasMessageContaining("Queue Service unavailable");

        // 1 initial + 3 retries = 4 total
        wireMockServer.verify(4, postRequestedFor(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages")));
    }

    @Test
    void enqueueMessage_whenInternalServerError_shouldRetryAndThrow() {
        UUID queueId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(Map.of("id", "123"))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Internal server error\"}")));

        assertThatThrownBy(() -> client.enqueueMessage("test-domain", "test-solution", queueId, envelope))
            .isInstanceOf(QueueServiceUnavailableException.class)
            .hasMessageContaining("Queue Service unavailable");

        wireMockServer.verify(4, postRequestedFor(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages")));
    }

    // ========== Retry Logic Tests (Requirements 12.1, 12.2, 12.3) ==========

    @Test
    void enqueueMessage_whenTransientFailure_shouldRetryAndSucceed() {
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(Map.of("id", "123"))
            .build();

        // First two attempts fail with 503, third succeeds
        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(503).withBody("{\"message\":\"unavailable\"}"))
            .willSetStateTo("First Retry"));

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("First Retry")
            .willReturn(aResponse().withStatus(503).withBody("{\"message\":\"unavailable\"}"))
            .willSetStateTo("Second Retry"));

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("Second Retry")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {"messageId": "%s", "status": "ENQUEUED"}
                    """, messageId))));

        UUID result = client.enqueueMessage("test-domain", "test-solution", queueId, envelope);
        assertThat(result).isEqualTo(messageId);
        wireMockServer.verify(3, postRequestedFor(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages")));
    }

    @Test
    void enqueueMessage_whenExponentialBackoff_shouldIncreaseDelayBetweenRetries() {
        UUID queueId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(Map.of("id", "123"))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse().withStatus(503).withBody("{\"message\":\"unavailable\"}")));

        long startTime = System.currentTimeMillis();

        assertThatThrownBy(() -> client.enqueueMessage("test-domain", "test-solution", queueId, envelope))
            .isInstanceOf(QueueServiceUnavailableException.class);

        long duration = System.currentTimeMillis() - startTime;
        // 100ms + 200ms + 400ms = 700ms minimum
        assertThat(duration).isGreaterThanOrEqualTo(600);
        wireMockServer.verify(4, postRequestedFor(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages")));
    }

    // ========== No Retry for Client Errors Tests (Requirement 12.6) ==========

    @Test
    void enqueueMessage_whenBadRequest_shouldNotRetry() {
        UUID queueId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(Map.of("id", "123"))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Invalid message format\"}")));

        assertThatThrownBy(() -> client.enqueueMessage("test-domain", "test-solution", queueId, envelope))
            .isInstanceOf(QueueServiceUnavailableException.class)
            .hasMessageContaining("Queue Service error");

        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages")));
    }

    @Test
    void enqueueMessage_whenUnauthorized_shouldNotRetry() {
        UUID queueId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(Map.of("id", "123"))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Unauthorized\"}")));

        assertThatThrownBy(() -> client.enqueueMessage("test-domain", "test-solution", queueId, envelope))
            .isInstanceOf(QueueServiceUnavailableException.class);

        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages")));
    }

    // ========== Null Response Handling ==========

    @Test
    void enqueueMessage_whenNullResponse_shouldThrowQueueServiceUnavailableException() {
        UUID queueId = UUID.randomUUID();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(UUID.randomUUID().toString())
            .payload(Map.of("id", "123"))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("null")));

        assertThatThrownBy(() -> client.enqueueMessage("test-domain", "test-solution", queueId, envelope))
            .isInstanceOf(QueueServiceUnavailableException.class)
            .hasMessageContaining("Queue Service returned null response");
    }

    // ========== Request Correlation Tests ==========

    @Test
    void enqueueMessage_shouldIncludeRequestIdInEnvelope() {
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();

        SifMessageEnvelope<Map<String, Object>> envelope = SifMessageEnvelope.<Map<String, Object>>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .requestId(requestId)
            .payload(Map.of("id", "123"))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {"messageId": "%s", "status": "ENQUEUED"}
                    """, messageId))));

        UUID result = client.enqueueMessage("test-domain", "test-solution", queueId, envelope);
        assertThat(result).isEqualTo(messageId);

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/test-domain/test-solution/queues/" + queueId + "/messages"))
            .withRequestBody(matchingJsonPath("$.requestId", equalTo(requestId.toString()))));
    }
}
