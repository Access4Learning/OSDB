// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.property;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import net.jqwik.api.*;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.responsesconnector.client.QueueServiceClient;
import org.sifunity.responsesconnector.exception.QueueServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-Based Tests for Resilience.
 * <p>
 * Validates correctness properties for resilience behavior using jqwik.
 * </p>
 *
 * <p><b>Properties Tested:</b></p>
 * <ul>
 *   <li>Property 15: Retry on transient failures</li>
 *   <li>Property 16: No retry on client errors</li>
 *   <li>Property 17: Timeout enforcement</li>
 * </ul>
 *
 * <p><b>Note:</b> WireMock server is created and torn down within each property test
 * because jqwik does not run @BeforeEach/@AfterEach for property tests.</p>
 */
class ResiliencePropertiesTest {

    private WireMockServer createWireMockServer() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    private QueueServiceClient createQueueServiceClient(WireMockServer wireMockServer, int maxRetries) {
        String baseUrl = "http://localhost:" + wireMockServer.port();
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();

        return new QueueServiceClient(restClient, baseUrl, maxRetries, 100, "test-token");
    }

    private QueueServiceClient createQueueServiceClientWithShortTimeout(WireMockServer wireMockServer, int maxRetries) {
        String baseUrl = "http://localhost:" + wireMockServer.port();
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(1000));
        factory.setReadTimeout(Duration.ofMillis(1000));

        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();

        return new QueueServiceClient(restClient, baseUrl, maxRetries, 100, "test-token");
    }

    @Property(tries = 20)
    @Label("Property 15: Retry on transient failures")
    void shouldRetryOnTransientFailures(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUID") UUID messageId,
            @ForAll("serverErrorCode") int errorCode) {

        WireMockServer wireMockServer = createWireMockServer();
        try {
            QueueServiceClient queueServiceClient = createQueueServiceClient(wireMockServer, 3);
            String endpoint = "/test-domain/test-solution/queues/" + queueId + "/messages";

            wireMockServer.stubFor(post(urlEqualTo(endpoint))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(errorCode)
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"error\": \"Server error\"}"))
                .willSetStateTo("First Retry"));

            wireMockServer.stubFor(post(urlEqualTo(endpoint))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("First Retry")
                .willReturn(aResponse().withStatus(errorCode)
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"error\": \"Server error\"}"))
                .willSetStateTo("Second Retry"));

            wireMockServer.stubFor(post(urlEqualTo(endpoint))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("Second Retry")
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"messageId\": \"" + messageId + "\", \"status\": \"ENQUEUED\"}")));

            SifMessageEnvelope<Object> envelope = createMinimalEnvelope(messageId);
            UUID result = queueServiceClient.enqueueMessage("test-domain", "test-solution", queueId, envelope);

            assertThat(result).isEqualTo(messageId);
            wireMockServer.verify(3, postRequestedFor(urlEqualTo(endpoint)));
        } finally {
            wireMockServer.stop();
        }
    }

    @Property(tries = 20)
    @Label("Property 15: Retry exhaustion on persistent transient failures")
    void shouldThrowExceptionAfterRetryExhaustion(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUID") UUID messageId,
            @ForAll("serverErrorCode") int errorCode) {

        WireMockServer wireMockServer = createWireMockServer();
        try {
            QueueServiceClient queueServiceClient = createQueueServiceClient(wireMockServer, 3);
            String endpoint = "/test-domain/test-solution/queues/" + queueId + "/messages";

            wireMockServer.stubFor(post(urlEqualTo(endpoint))
                .willReturn(aResponse().withStatus(errorCode)
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"error\": \"Server error\"}")));

            SifMessageEnvelope<Object> envelope = createMinimalEnvelope(messageId);

            assertThatThrownBy(() -> queueServiceClient.enqueueMessage("test-domain", "test-solution", queueId, envelope))
                .isInstanceOf(QueueServiceUnavailableException.class)
                .hasMessageContaining("Queue Service unavailable");

            wireMockServer.verify(4, postRequestedFor(urlEqualTo(endpoint)));
        } finally {
            wireMockServer.stop();
        }
    }

    @Property(tries = 20)
    @Label("Property 16: No retry on client errors")
    void shouldNotRetryOnClientErrors(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUID") UUID messageId,
            @ForAll("clientErrorCode") int errorCode) {

        WireMockServer wireMockServer = createWireMockServer();
        try {
            QueueServiceClient queueServiceClient = createQueueServiceClient(wireMockServer, 3);
            String endpoint = "/test-domain/test-solution/queues/" + queueId + "/messages";

            wireMockServer.stubFor(post(urlEqualTo(endpoint))
                .willReturn(aResponse().withStatus(errorCode)
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"error\": \"Client error\"}")));

            SifMessageEnvelope<Object> envelope = createMinimalEnvelope(messageId);

            assertThatThrownBy(() -> queueServiceClient.enqueueMessage("test-domain", "test-solution", queueId, envelope))
                .satisfiesAnyOf(
                    ex -> assertThat(ex).isInstanceOf(org.sifunity.responsesconnector.exception.QueueNotFoundException.class),
                    ex -> assertThat(ex).isInstanceOf(QueueServiceUnavailableException.class)
                );

            wireMockServer.verify(1, postRequestedFor(urlEqualTo(endpoint)));
        } finally {
            wireMockServer.stop();
        }
    }

    @Property(tries = 20)
    @Label("Property 16: No retry on 404 Not Found")
    void shouldNotRetryOn404NotFound(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUID") UUID messageId) {

        WireMockServer wireMockServer = createWireMockServer();
        try {
            QueueServiceClient queueServiceClient = createQueueServiceClient(wireMockServer, 3);
            String endpoint = "/test-domain/test-solution/queues/" + queueId + "/messages";

            wireMockServer.stubFor(post(urlEqualTo(endpoint))
                .willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"error\": \"Queue not found\"}")));

            SifMessageEnvelope<Object> envelope = createMinimalEnvelope(messageId);

            assertThatThrownBy(() -> queueServiceClient.enqueueMessage("test-domain", "test-solution", queueId, envelope))
                .isInstanceOf(org.sifunity.responsesconnector.exception.QueueNotFoundException.class)
                .hasMessageContaining("Queue not found")
                .hasMessageContaining(queueId.toString());

            wireMockServer.verify(1, postRequestedFor(urlEqualTo(endpoint)));
        } finally {
            wireMockServer.stop();
        }
    }

    @Property(tries = 5)
    @Label("Property 17: Timeout enforcement")
    void shouldEnforceTimeout(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUID") UUID messageId) {

        WireMockServer wireMockServer = createWireMockServer();
        try {
            QueueServiceClient clientWithShortTimeout = createQueueServiceClientWithShortTimeout(wireMockServer, 0);
            String endpoint = "/test-domain/test-solution/queues/" + queueId + "/messages";

            wireMockServer.stubFor(post(urlEqualTo(endpoint))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"messageId\": \"" + messageId + "\", \"status\": \"ENQUEUED\"}")
                    .withFixedDelay(2000)));

            SifMessageEnvelope<Object> envelope = createMinimalEnvelope(messageId);

            assertThatThrownBy(() -> clientWithShortTimeout.enqueueMessage("test-domain", "test-solution", queueId, envelope))
                .isInstanceOf(QueueServiceUnavailableException.class)
                .hasMessageContaining("Failed to enqueue message");

            wireMockServer.verify(1, postRequestedFor(urlEqualTo(endpoint)));
        } finally {
            wireMockServer.stop();
        }
    }

    @Property(tries = 5)
    @Label("Property 17: Connection timeout enforcement")
    void shouldEnforceConnectionTimeout(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUID") UUID messageId) {

        String unreachableUrl = "http://192.0.2.1:9999";
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(1000));
        factory.setReadTimeout(Duration.ofMillis(1000));

        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(unreachableUrl)
                .build();

        QueueServiceClient clientWithUnreachableHost = new QueueServiceClient(
            restClient, unreachableUrl, 0, 100, "test-token");

        SifMessageEnvelope<Object> envelope = createMinimalEnvelope(messageId);

        assertThatThrownBy(() -> clientWithUnreachableHost.enqueueMessage("test-domain", "test-solution", queueId, envelope))
            .isInstanceOf(QueueServiceUnavailableException.class);
    }

    // ========== Helper Methods ==========

    private SifMessageEnvelope<Object> createMinimalEnvelope(UUID messageId) {
        return SifMessageEnvelope.builder()
            .messageId(messageId)
            .requestId(UUID.randomUUID().toString())
            .sourceId(UUID.randomUUID())
            .serviceName("TestService")
            .serviceType(org.sifunity.broker.common.constant.ServiceType.OBJECT)
            .payload(java.util.Map.of("test", "data"))
            .build();
    }

    // ========== Arbitrary Providers ==========

    @Provide
    Arbitrary<UUID> validUUID() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<Integer> serverErrorCode() {
        return Arbitraries.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.BAD_GATEWAY.value(),
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.GATEWAY_TIMEOUT.value()
        );
    }

    @Provide
    Arbitrary<Integer> clientErrorCode() {
        return Arbitraries.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.CONFLICT.value(),
            HttpStatus.UNPROCESSABLE_ENTITY.value()
        );
    }
}
