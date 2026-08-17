// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.servicesconnector.client.QueueServiceClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for QueueServiceClient.
 * Uses WireMock to mock Queue Service HTTP responses.
 *
 * <p>Tests Requirements:</p>
 * <ul>
 *   <li>5.4: Enqueue message to provider queue</li>
 * </ul>
 */
@DisplayName("QueueServiceClient")
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

    @Test
    @DisplayName("enqueueMessage - successful enqueue returns messageId")
    void enqueueMessage_success_shouldReturnMessageId() {
        // Given
        String domainId = "domain1";
        String solutionId = "solution1";
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
                .messageId(messageId)
                .messageType(MessageType.REQUEST)
                .serviceName("DataExport")
                .serviceType(ServiceType.FUNCTIONAL)
                .zoneId("zone1")
                .contextId("ctx1")
                .requestId(requestId)
                .queueId(queueId)
                .payload("{\"data\":\"test\"}")
                .headers(Map.of("X-Custom", "value"))
                .build();

        UUID returnedMessageId = UUID.randomUUID();

        wireMockServer.stubFor(post(urlPathEqualTo(
                "/" + domainId + "/" + solutionId + "/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("{\"messageId\":\"%s\"}", returnedMessageId))));

        // When
        UUID result = client.enqueueMessage(domainId, solutionId, queueId, envelope);

        // Then
        assertThat(result).isEqualTo(returnedMessageId);
    }

    @Test
    @DisplayName("enqueueMessage - builds correct request body with all fields")
    void enqueueMessage_shouldBuildCorrectRequestBody() {
        // Given
        String domainId = "domain1";
        String solutionId = "solution1";
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
                .messageId(messageId)
                .messageType(MessageType.REQUEST)
                .serviceName("StudentPersonal")
                .serviceType(ServiceType.OBJECT)
                .zoneId("production")
                .contextId("school1")
                .requestId(requestId)
                .queueId(queueId)
                .payload("{\"student\":\"data\"}")
                .headers(Map.of("X-Forwarded", "true"))
                .build();

        UUID returnedMessageId = UUID.randomUUID();

        wireMockServer.stubFor(post(urlPathEqualTo(
                "/" + domainId + "/" + solutionId + "/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("{\"messageId\":\"%s\"}", returnedMessageId))));

        // When
        client.enqueueMessage(domainId, solutionId, queueId, envelope);

        // Then - verify request body contains expected fields
        wireMockServer.verify(postRequestedFor(
                urlPathEqualTo("/" + domainId + "/" + solutionId + "/queues/" + queueId + "/messages"))
            .withRequestBody(containing("\"messageId\""))
            .withRequestBody(containing("\"serviceName\":\"StudentPersonal\""))
            .withRequestBody(containing("\"messageType\":\"REQUEST\""))
            .withRequestBody(containing("\"serviceType\":\"OBJECT\""))
            .withRequestBody(containing("\"zone\":\"production\""))
            .withRequestBody(containing("\"context\":\"school1\""))
            .withRequestBody(containing("\"requestId\":\"" + requestId + "\""))
            .withRequestBody(containing("\"queueId\":\"" + queueId + "\""))
            .withRequestBody(containing("\"headers\""))
            .withRequestBody(containing("\"X-Forwarded\"")));
    }

    @Test
    @DisplayName("enqueueMessage - exception during enqueue is rethrown")
    void enqueueMessage_exception_shouldRethrow() {
        // Given
        String domainId = "domain1";
        String solutionId = "solution1";
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
                .messageId(messageId)
                .messageType(MessageType.REQUEST)
                .serviceName("DataExport")
                .serviceType(ServiceType.FUNCTIONAL)
                .zoneId("zone1")
                .contextId("ctx1")
                .payload("{}")
                .build();

        wireMockServer.stubFor(post(urlPathEqualTo(
                "/" + domainId + "/" + solutionId + "/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Queue service unavailable\"}")));

        // When/Then
        assertThatThrownBy(() -> client.enqueueMessage(domainId, solutionId, queueId, envelope))
                .isInstanceOf(WebClientResponseException.class);
    }

    @Test
    @DisplayName("enqueueMessage - null zoneId defaults to 'default'")
    void enqueueMessage_nullZoneId_shouldDefaultToDefault() {
        // Given
        String domainId = "domain1";
        String solutionId = "solution1";
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
                .messageId(messageId)
                .messageType(MessageType.REQUEST)
                .serviceName("DataExport")
                .serviceType(ServiceType.FUNCTIONAL)
                .zoneId(null)
                .contextId(null)
                .payload("{}")
                .build();

        UUID returnedMessageId = UUID.randomUUID();

        wireMockServer.stubFor(post(urlPathEqualTo(
                "/" + domainId + "/" + solutionId + "/queues/" + queueId + "/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("{\"messageId\":\"%s\"}", returnedMessageId))));

        // When
        UUID result = client.enqueueMessage(domainId, solutionId, queueId, envelope);

        // Then
        assertThat(result).isEqualTo(returnedMessageId);
        wireMockServer.verify(postRequestedFor(
                urlPathEqualTo("/" + domainId + "/" + solutionId + "/queues/" + queueId + "/messages"))
            .withRequestBody(containing("\"zone\":\"default\""))
            .withRequestBody(containing("\"context\":\"default\"")));
    }
}
