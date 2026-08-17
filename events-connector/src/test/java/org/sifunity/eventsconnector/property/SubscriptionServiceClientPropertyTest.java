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
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.eventsconnector.client.SubscriptionServiceClient;
import org.sifunity.eventsconnector.dto.SubscriptionQueryResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for SubscriptionServiceClient subscription query round-trip.
 *
 * <p>Validates that for any valid service name and any context UUID (including null),
 * the client correctly deserializes the server's SubscriptionQueryResponse and returns
 * a matching List of SubscriptionDTO.</p>
 *
 * <p>Uses jqwik for property-based testing with MockWebServer.</p>
 */
// Feature: restclient-netty-removal, Property 2: Subscription query round-trip
@Tag("Feature: restclient-netty-removal")
class SubscriptionServiceClientPropertyTest {

    private MockWebServer mockWebServer;
    private SubscriptionServiceClient client;
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

        client = new SubscriptionServiceClient(
                restClient,
                retry,
                circuitBreaker,
                mockWebServer.url("/").toString()
        );
    }

    @AfterProperty
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    /**
     * Property 2: Subscription query round-trip
     *
     * <b>Validates: Requirements 4.1</b>
     *
     * For any valid service name string and any context UUID (including null),
     * when SubscriptionServiceClient.querySubscriptions is called and the server
     * returns a 200 response with a JSON SubscriptionQueryResponse, the client
     * shall return a List&lt;SubscriptionDTO&gt; directly whose size and content
     * match the server response.
     */
    @Property(tries = 100)
    @Tag("Property 2: Subscription query round-trip")
    void subscriptionQueryRoundTrip(
            @ForAll("serviceNames") String serviceName,
            @ForAll("contextUuids") UUID contextUuid,
            @ForAll("subscriptionLists") List<SubscriptionDTO> subscriptions
    ) throws Exception {
        // Arrange: build the server response
        SubscriptionQueryResponse queryResponse = SubscriptionQueryResponse.of(subscriptions);
        String responseJson = objectMapper.writeValueAsString(queryResponse);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        // Act
        List<SubscriptionDTO> result = client.querySubscriptions(
                "domain-1", "solution-1", serviceName, contextUuid);

        // Assert: returned list size and content match the server response
        assertThat(result).isNotNull();
        assertThat(result).hasSize(subscriptions.size());

        for (int i = 0; i < subscriptions.size(); i++) {
            SubscriptionDTO expected = subscriptions.get(i);
            SubscriptionDTO actual = result.get(i);
            assertThat(actual.id()).isEqualTo(expected.id());
            assertThat(actual.queueId()).isEqualTo(expected.queueId());
            assertThat(actual.serviceName()).isEqualTo(expected.serviceName());
            assertThat(actual.serviceType()).isEqualTo(expected.serviceType());
            assertThat(actual.zoneId()).isEqualTo(expected.zoneId());
            assertThat(actual.contextId()).isEqualTo(expected.contextId());
            assertThat(actual.messageType()).isEqualTo(expected.messageType());
        }
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30);
    }

    @Provide
    Arbitrary<UUID> contextUuids() {
        return Arbitraries.of(UUID.randomUUID(), null);
    }

    @Provide
    Arbitrary<List<SubscriptionDTO>> subscriptionLists() {
        return subscriptionDtos().list().ofMinSize(0).ofMaxSize(10);
    }

    @Provide
    Arbitrary<SubscriptionDTO> subscriptionDtos() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> serviceTypes = Arbitraries.of("OBJECT", "UTILITY", "FUNCTIONAL");
        Arbitrary<String> zoneIds = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
        Arbitrary<String> contextIds = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
        Arbitrary<SubscriptionType> messageTypes = Arbitraries.of(SubscriptionType.values());
        Arbitrary<Instant> instants = Arbitraries.longs()
                .between(0, 2_000_000_000L)
                .map(Instant::ofEpochSecond);

        return Combinators.combine(uuids, uuids, names, serviceTypes, zoneIds, contextIds, messageTypes, instants)
                .as((id, queueId, svcName, svcType, zoneId, contextId, msgType, createdAt) ->
                        new SubscriptionDTO(id, queueId, null, svcName, svcType, zoneId, contextId, msgType, createdAt));
    }
}

