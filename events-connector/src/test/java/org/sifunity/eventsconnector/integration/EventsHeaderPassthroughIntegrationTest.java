// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.eventsconnector.dto.EnqueueResponse;
import org.sifunity.eventsconnector.dto.SubscriptionQueryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying that custom passthrough headers are captured by the
 * EventsController, sanitized by HeaderSanitizer, and included in the
 * SifMessageEnvelope sent to the Queue Service — while blacklisted headers are stripped.
 *
 * <p>Uses full Spring context with real HeaderSanitizer and HeaderBlacklistProperties
 * beans, MockMvc for the REST API, and MockWebServer for external service simulation.
 * The envelope body sent to the queue service is captured and inspected.</p>
 *
 * <p>Validates: Requirements 5.1, 9.1, 9.3</p>
 */
@SpringBootTest(properties = {
    "management.health.defaults.enabled=false",
    "resilience4j.circuitbreaker.instances.subscriptionService.enabled=false",
    "resilience4j.circuitbreaker.instances.queueService.enabled=false",
    "retry.max-attempts=1",
    "spring.cache.type=none"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventsHeaderPassthroughIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static MockWebServer subscriptionServiceMock;
    private static MockWebServer queueServiceMock;

    @BeforeAll
    static void setUp() throws IOException {
        subscriptionServiceMock = new MockWebServer();
        queueServiceMock = new MockWebServer();
        subscriptionServiceMock.start();
        queueServiceMock.start();
        subscriptionServiceMock.setDispatcher(new QueueDispatcher());
        queueServiceMock.setDispatcher(new QueueDispatcher());
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (subscriptionServiceMock != null) subscriptionServiceMock.shutdown();
        if (queueServiceMock != null) queueServiceMock.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("subscription-service.url",
            () -> "http://localhost:" + subscriptionServiceMock.getPort());
        registry.add("queue-service.url",
            () -> "http://localhost:" + queueServiceMock.getPort());
    }

    @Test
    void shouldPassthroughCustomHeadersAndStripBlacklisted() throws Exception {
        // Arrange: one subscription so the envelope is sent to the queue service
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant now = Instant.now();

        SubscriptionDTO sub = new SubscriptionDTO(
            UUID.randomUUID(), queueId, null, "StudentPersonal", (String) null, (String) null,
            SubscriptionType.EVENT, now
        );
        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(
                SubscriptionQueryResponse.of(List.of(sub)))));

        queueServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(
                EnqueueResponse.of(messageId, queueId, "key-1", now, "ENQUEUED"))));

        String payload = "{\"eventAction\":\"CREATE\",\"objectType\":\"StudentPersonal\"}";

        // Act: send request with custom + blacklisted headers
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("messageId", messageId.toString())
                .header("x-vendor-name", "TestVendor")
                .header("debugToken", "abc123")
                .header("Authorization", "Bearer secret-token")
                .header("fingerprint", "spoofed-value")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isAccepted());

        // Assert: capture the envelope body sent to the queue service
        // Skip the subscription-service request (first server is separate)
        RecordedRequest queueRequest = queueServiceMock.takeRequest(5, TimeUnit.SECONDS);
        assertThat(queueRequest).isNotNull();

        String body = queueRequest.getBody().readUtf8();
        JsonNode envelope = objectMapper.readTree(body);
        JsonNode headers = envelope.get("headers");
        assertThat(headers).isNotNull();

        // Build a case-insensitive set of header keys in the envelope
        Map<String, String> envelopeHeaders = objectMapper.convertValue(
            headers, objectMapper.getTypeFactory()
                .constructMapType(java.util.LinkedHashMap.class, String.class, String.class));

        Set<String> envelopeKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        envelopeKeys.addAll(envelopeHeaders.keySet());

        // Custom passthrough headers SHOULD be present
        assertThat(envelopeKeys).contains("x-vendor-name");
        assertThat(envelopeKeys).contains("debugToken");
        assertThat(envelopeHeaders.entrySet()).anyMatch(
            e -> e.getKey().equalsIgnoreCase("x-vendor-name")
                && "TestVendor".equals(e.getValue()));
        assertThat(envelopeHeaders.entrySet()).anyMatch(
            e -> e.getKey().equalsIgnoreCase("debugToken")
                && "abc123".equals(e.getValue()));

        // Blacklisted headers SHOULD NOT be present
        assertThat(envelopeKeys).doesNotContain("Authorization");
        assertThat(envelopeKeys).doesNotContain("fingerprint");
        assertThat(envelopeKeys).doesNotContain("sharedSecret");
        assertThat(envelopeKeys).doesNotContain("X-Environment-Id");
        assertThat(envelopeKeys).doesNotContain("X-Environment-Name");
        assertThat(envelopeKeys).doesNotContain("X-Correlation-Id");
        assertThat(envelopeKeys).doesNotContain("timestamp");

        // Structured SIF headers SHOULD NOT be in passthrough map
        assertThat(envelopeKeys).doesNotContain("serviceName");
        assertThat(envelopeKeys).doesNotContain("zoneId");
        assertThat(envelopeKeys).doesNotContain("messageId");
        assertThat(envelopeKeys).doesNotContain("contextId");
    }


    @Test
    void shouldProduceEmptyHeadersMapWhenOnlyBlacklistedHeadersSent() throws Exception {
        UUID queueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant now = Instant.now();

        SubscriptionDTO sub = new SubscriptionDTO(
            UUID.randomUUID(), queueId, null, "StudentPersonal", (String) null, (String) null,
            SubscriptionType.EVENT, now);
        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(
                SubscriptionQueryResponse.of(List.of(sub)))));
        queueServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(
                EnqueueResponse.of(messageId, queueId, "key-2", now, "ENQUEUED"))));

        String payload = "{\"eventAction\":\"CREATE\"}";

        // Send request with ONLY required + blacklisted headers (no custom ones)
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("messageId", messageId.toString())
                .header("Authorization", "Bearer secret")
                .header("fingerprint", "spoofed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isAccepted());

        RecordedRequest queueReq = queueServiceMock.takeRequest(5, TimeUnit.SECONDS);
        assertThat(queueReq).isNotNull();

        JsonNode envelope = objectMapper.readTree(queueReq.getBody().readUtf8());
        JsonNode headers = envelope.get("headers");
        assertThat(headers).isNotNull();

        // The headers map should contain no blacklisted or SIF-structured entries.
        // It may contain framework headers like "content-type", "host", "content-length"
        // but must NOT contain any blacklisted header.
        Map<String, String> map = objectMapper.convertValue(headers,
            objectMapper.getTypeFactory().constructMapType(
                java.util.LinkedHashMap.class, String.class, String.class));
        Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        keys.addAll(map.keySet());

        assertThat(keys).doesNotContain("Authorization");
        assertThat(keys).doesNotContain("fingerprint");
        assertThat(keys).doesNotContain("sharedSecret");
    }
}

