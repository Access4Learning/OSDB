// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.eventsconnector.client.QueueServiceClient;
import org.sifunity.eventsconnector.dto.EnqueueResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for QueueServiceClient enqueue round-trip.
 *
 * <p>Validates that for any valid SifMessageEnvelope and queue UUID,
 * the client correctly deserializes the server's EnqueueResponse.</p>
 *
 * <p>Uses jqwik for property-based testing with MockWebServer.</p>
 */
// Feature: restclient-netty-removal, Property 1: Enqueue request round-trip
@Tag("Feature: restclient-netty-removal")
class QueueServiceClientPropertyTest {

    private MockWebServer mockWebServer;
    private QueueServiceClient client;
    private ObjectMapper objectMapper;

    @BeforeProperty
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        RestClient restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        // No-op retry: maxAttempts=1 means no retries
        Retry retry = RetryRegistry.of(
                RetryConfig.custom()
                        .maxAttempts(1)
                        .build()
        ).retry("test-retry");

        // Circuit breaker in disabled state (always allows calls through)
        CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .build()
        ).circuitBreaker("test-cb");

        client = new QueueServiceClient(
                restClient,
                retry,
                circuitBreaker,
                mockWebServer.url("/").toString(),
                "test-token"
        );
    }

    @AfterProperty
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    /**
     * Property 1: Enqueue request round-trip
     *
     * **Validates: Requirements 3.1**
     *
     * For any valid SifMessageEnvelope and any queue UUID, when
     * QueueServiceClient.enqueueMessage is called and the server returns
     * a 200 response with a JSON EnqueueResponse, the client shall return
     * a deserialized EnqueueResponse object directly with the correct
     * storageKey and queueId values matching the server response.
     */
    @Property(tries = 100)
    @Tag("Property 1: Enqueue request round-trip")
    void enqueueMessageRoundTrip(
            @ForAll("queueIds") UUID queueId,
            @ForAll("storageKeys") String storageKey,
            @ForAll("messageTypes") MessageType messageType,
            @ForAll("serviceTypes") ServiceType serviceType
    ) throws Exception {
        // Arrange: build a random envelope
        UUID messageId = UUID.randomUUID();
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
                .messageId(messageId)
                .timestamp(Instant.now())
                .messageType(messageType)
                .serviceName("TestService")
                .serviceType(serviceType)
                .zoneId("zone-1")
                .contextId("DEFAULT")
                .payload("test-payload")
                .build();

        // Build the expected server response
        UUID responseMessageId = UUID.randomUUID();
        Instant enqueuedAt = Instant.now();
        String responseJson = objectMapper.writeValueAsString(
                EnqueueResponse.of(responseMessageId, queueId, storageKey, enqueuedAt, "ENQUEUED")
        );

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        // Act
        EnqueueResponse response = client.enqueueMessage("domain-1", "solution-1", queueId, envelope);

        // Assert: deserialized response matches server response
        assertThat(response).isNotNull();
        assertThat(response.queueId()).isEqualTo(queueId);
        assertThat(response.storageKey()).isEqualTo(storageKey);
        assertThat(response.messageId()).isEqualTo(responseMessageId);
        assertThat(response.status()).isEqualTo("ENQUEUED");
    }

    @Provide
    Arbitrary<UUID> queueIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> storageKeys() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_')
                .ofMinLength(5)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<MessageType> messageTypes() {
        return Arbitraries.of(MessageType.values());
    }

    @Provide
    Arbitrary<ServiceType> serviceTypes() {
        return Arbitraries.of(ServiceType.values());
    }
}
