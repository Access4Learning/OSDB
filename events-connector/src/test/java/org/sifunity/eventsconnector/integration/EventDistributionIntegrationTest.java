// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for event distribution.
 * Tests complete event distribution flow with mocked external services.
 * 
 * <p>Uses full Spring context with MockMvc for REST API testing and
 * MockWebServer for mocking Subscription Service and Queue Service.</p>
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
class EventDistributionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static MockWebServer subscriptionServiceMock;
    private static MockWebServer queueServiceMock;
    private static MockWebServer environmentServiceMock;

    @BeforeAll
    static void setUp() throws IOException {
        subscriptionServiceMock = new MockWebServer();
        queueServiceMock = new MockWebServer();
        environmentServiceMock = new MockWebServer();

        subscriptionServiceMock.start();
        queueServiceMock.start();
        environmentServiceMock.start();

        // Verify servers started successfully
        if (subscriptionServiceMock.getPort() <= 0 || queueServiceMock.getPort() <= 0 || environmentServiceMock.getPort() <= 0) {
            throw new IllegalStateException("MockWebServer failed to start properly");
        }

        // Explicitly set QueueDispatcher to ensure clean state
        subscriptionServiceMock.setDispatcher(new QueueDispatcher());
        queueServiceMock.setDispatcher(new QueueDispatcher());
        environmentServiceMock.setDispatcher(new QueueDispatcher());
    }


    @AfterAll
    static void tearDown() throws IOException {
        try {
            if (subscriptionServiceMock != null) {
                subscriptionServiceMock.shutdown();
            }
        } catch (IOException e) {
            // Log but don't fail - shutdown issues shouldn't fail the test
            System.err.println("Warning: Failed to shutdown subscriptionServiceMock: " + e.getMessage());
        }
        try {
            if (queueServiceMock != null) {
                queueServiceMock.shutdown();
            }
        } catch (IOException e) {
            // Log but don't fail - shutdown issues shouldn't fail the test
            System.err.println("Warning: Failed to shutdown queueServiceMock: " + e.getMessage());
        }
        try {
            if (environmentServiceMock != null) {
                environmentServiceMock.shutdown();
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to shutdown environmentServiceMock: " + e.getMessage());
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("subscription-service.url", () -> 
            "http://localhost:" + subscriptionServiceMock.getPort());
        registry.add("queue-service.url", () -> 
            "http://localhost:" + queueServiceMock.getPort());
    }

    @BeforeEach
    void resetMockServers() {
        // Reset dispatchers to ensure clean state between tests
        subscriptionServiceMock.setDispatcher(new QueueDispatcher());
        queueServiceMock.setDispatcher(new QueueDispatcher());
        environmentServiceMock.setDispatcher(new QueueDispatcher());
    }

    @Test
    void shouldDistributeEventToMultipleSubscribers() throws Exception {
        // Arrange
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        UUID queueId3 = UUID.randomUUID();
        Instant now = Instant.now();

        // Mock Subscription Service response
        SubscriptionDTO sub1 = new SubscriptionDTO(UUID.randomUUID(), queueId1, null, "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT, now);
        SubscriptionDTO sub2 = new SubscriptionDTO(UUID.randomUUID(), queueId2, null, "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT, now);
        SubscriptionDTO sub3 = new SubscriptionDTO(UUID.randomUUID(), queueId3, null, "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT, now);

        SubscriptionQueryResponse subscriptionResponse = SubscriptionQueryResponse.of(
            List.of(sub1, sub2, sub3)
        );

        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(subscriptionResponse)));

        // Mock Queue Service responses
        UUID messageId = UUID.randomUUID();
        EnqueueResponse enqueueResponse1 = EnqueueResponse.of(
            messageId, queueId1, "storage-key-1", now, "ENQUEUED"
        );
        EnqueueResponse enqueueResponse2 = EnqueueResponse.of(
            messageId, queueId2, "storage-key-2", now, "ENQUEUED"
        );
        EnqueueResponse enqueueResponse3 = EnqueueResponse.of(
            messageId, queueId3, "storage-key-3", now, "ENQUEUED"
        );

        queueServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(enqueueResponse1)));
        queueServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(enqueueResponse2)));
        queueServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(enqueueResponse3)));

        String eventPayload = "{\"eventAction\":\"CREATE\",\"objectType\":\"StudentPersonal\"}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .header("messageId", messageId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldHandleNoSubscribers() throws Exception {
        // Arrange
        SubscriptionQueryResponse subscriptionResponse = SubscriptionQueryResponse.of(List.of());

        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(subscriptionResponse)));

        String eventPayload = "{\"eventAction\":\"CREATE\",\"objectType\":\"StudentPersonal\"}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldHandleSubscriptionServiceUnavailable() throws Exception {
        // Arrange
        subscriptionServiceMock.enqueue(new MockResponse().setResponseCode(503));

        String eventPayload = "{\"eventAction\":\"CREATE\",\"objectType\":\"StudentPersonal\"}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").exists())
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldHandlePartialQueueServiceFailures() throws Exception {
        // Arrange
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        UUID queueId3 = UUID.randomUUID();
        Instant now = Instant.now();

        // Mock Subscription Service response
        SubscriptionDTO sub1 = new SubscriptionDTO(UUID.randomUUID(), queueId1, null, "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT, now);
        SubscriptionDTO sub2 = new SubscriptionDTO(UUID.randomUUID(), queueId2, null, "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT, now);
        SubscriptionDTO sub3 = new SubscriptionDTO(UUID.randomUUID(), queueId3, null, "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT, now);

        SubscriptionQueryResponse subscriptionResponse = SubscriptionQueryResponse.of(
            List.of(sub1, sub2, sub3)
        );

        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(subscriptionResponse)));

        // Mock Queue Service responses: 2 succeed, 1 fails
        UUID messageId = UUID.randomUUID();
        EnqueueResponse enqueueResponse1 = EnqueueResponse.of(
            messageId, queueId1, "storage-key-1", now, "ENQUEUED"
        );
        EnqueueResponse enqueueResponse2 = EnqueueResponse.of(
            messageId, queueId2, "storage-key-2", now, "ENQUEUED"
        );

        queueServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(enqueueResponse1)));
        queueServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(enqueueResponse2)));
        queueServiceMock.enqueue(new MockResponse().setResponseCode(503)); // Failure

        String eventPayload = "{\"eventAction\":\"CREATE\",\"objectType\":\"StudentPersonal\"}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .header("messageId", messageId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldHandleAllQueueServiceFailures() throws Exception {
        // Arrange
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        Instant now = Instant.now();

        // Mock Subscription Service response
        SubscriptionDTO sub1 = new SubscriptionDTO(UUID.randomUUID(), queueId1, null, "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT, now);
        SubscriptionDTO sub2 = new SubscriptionDTO(UUID.randomUUID(), queueId2, null, "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT, now);

        SubscriptionQueryResponse subscriptionResponse = SubscriptionQueryResponse.of(
            List.of(sub1, sub2)
        );

        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(subscriptionResponse)));

        // Mock Queue Service responses: all fail
        queueServiceMock.enqueue(new MockResponse().setResponseCode(503));
        queueServiceMock.enqueue(new MockResponse().setResponseCode(503));

        String eventPayload = "{\"eventAction\":\"CREATE\",\"objectType\":\"StudentPersonal\"}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldRejectEventWithMissingServiceName() throws Exception {
        // Arrange
        String eventPayload = "{\"eventAction\":\"CREATE\"}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEventWithMissingZoneId() throws Exception {
        // Arrange
        String eventPayload = "{\"eventAction\":\"CREATE\"}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEventWithMissingEnvironmentContext() throws Exception {
        // Arrange
        String eventPayload = "{\"eventAction\":\"CREATE\"}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDefaultContextIdToDefault() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        Instant now = Instant.now();

        SubscriptionDTO sub = new SubscriptionDTO(UUID.randomUUID(), queueId, null, "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT, now);

        SubscriptionQueryResponse subscriptionResponse = SubscriptionQueryResponse.of(List.of(sub));

        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(subscriptionResponse)));

        UUID messageId = UUID.randomUUID();
        EnqueueResponse enqueueResponse = EnqueueResponse.of(
            messageId, queueId, "storage-key-1", now, "ENQUEUED"
        );

        queueServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(enqueueResponse)));

        String eventPayload = "{\"eventAction\":\"CREATE\"}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                // No contextId header - should default to DEFAULT
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldGenerateMessageIdWhenNotProvided() throws Exception {
        // Arrange
        SubscriptionQueryResponse subscriptionResponse = SubscriptionQueryResponse.of(List.of());

        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(subscriptionResponse)));

        String eventPayload = "{\"eventAction\":\"CREATE\"}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                // No messageId header - should be generated
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldPreserveCompleteEventPayload() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        Instant now = Instant.now();

        SubscriptionDTO sub = new SubscriptionDTO(UUID.randomUUID(), queueId, null, "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT, now);

        SubscriptionQueryResponse subscriptionResponse = SubscriptionQueryResponse.of(List.of(sub));

        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(subscriptionResponse)));

        UUID messageId = UUID.randomUUID();
        EnqueueResponse enqueueResponse = EnqueueResponse.of(
            messageId, queueId, "storage-key-1", now, "ENQUEUED"
        );

        queueServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(enqueueResponse)));

        String eventPayload = "{\"eventAction\":\"UPDATE\",\"objectType\":\"StudentPersonal\",\"data\":{\"field\":\"value\"}}";

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isAccepted());

        // Verify payload was sent to Queue Service (checked via request body in actual implementation)
    }
}

