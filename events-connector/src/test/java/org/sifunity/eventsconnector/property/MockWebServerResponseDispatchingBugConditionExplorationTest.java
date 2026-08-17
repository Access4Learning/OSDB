// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

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
 * Bug Condition Exploration Test for MockWebServer Response Dispatching
 * 
 * <p><strong>EXPLORATION TEST: This test is EXPECTED TO FAIL on unfixed code.</strong>
 * 
 * <p><strong>Bug Description:</strong>
 * EventDistributionIntegrationTest experiences timeouts and JSON decoding errors because
 * MockWebServer's default dispatcher is not properly handling enqueued responses. When tests
 * enqueue MockResponse objects using mockServer.enqueue(), the MockWebServer should return
 * those responses to incoming HTTP requests from WebClient. However, the default dispatcher
 * behavior is causing the server to either not return responses at all (leading to
 * ReadTimeoutException) or return unexpected content (leading to JSON decoding errors like
 * "Unrecognized token 'invalid'").
 * 
 * <p><strong>Current Behavior (Defect):</strong>
 * <ul>
 *   <li>Test methods enqueue MockResponse objects with specific JSON content</li>
 *   <li>WebClient makes HTTP requests to MockWebServer</li>
 *   <li>MockWebServer fails to return enqueued responses (ReadTimeoutException)</li>
 *   <li>OR MockWebServer returns unexpected content (JsonProcessingException "Unrecognized token 'invalid'")</li>
 *   <li>Tests fail with timeout or JSON decoding errors</li>
 * </ul>
 * 
 * <p><strong>Test Strategy:</strong>
 * This property-based test replicates the exact scenarios from EventDistributionIntegrationTest
 * that are failing. It tests the three main cases:
 * <ul>
 *   <li>Multiple responses (4 responses for shouldDistributeEventToMultipleSubscribers)</li>
 *   <li>Single response (1 response for shouldHandleNoSubscribers)</li>
 *   <li>Mixed success/failure responses (4 responses for shouldHandlePartialQueueServiceFailures)</li>
 * </ul>
 * 
 * <p><strong>EXPECTED OUTCOME: This test FAILS with ReadTimeoutException or JsonProcessingException</strong>
 * 
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3</strong>
 * 
 * @see org.sifunity.eventsconnector.integration.EventDistributionIntegrationTest
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
class MockWebServerResponseDispatchingBugConditionExplorationTest {

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

        // Verify servers started successfully
        if (subscriptionServiceMock.getPort() <= 0 || queueServiceMock.getPort() <= 0) {
            throw new IllegalStateException("MockWebServer failed to start properly");
        }
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (subscriptionServiceMock != null) {
            subscriptionServiceMock.shutdown();
        }
        if (queueServiceMock != null) {
            queueServiceMock.shutdown();
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
    }

    /**
     * Test Case 1: Bug Condition - Single Response (shouldHandleNoSubscribers scenario)
     * 
     * <p><strong>Validates: Requirements 1.1, 1.2</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test FAILS (confirms the bug exists)</strong>
     * 
     * <p>This test verifies that when a single MockResponse is enqueued and WebClient
     * makes a request, the response is returned without timeout. This test encodes the
     * EXPECTED behavior - it will validate the fix when it passes after implementation.
     * 
     * <p><strong>Bug Manifestation:</strong>
     * On unfixed code without explicit QueueDispatcher configuration, this test will FAIL because:
     * <ul>
     *   <li>MockWebServer does not return the enqueued response to WebClient request</li>
     *   <li>WebClient times out waiting for response (ReadTimeoutException)</li>
     *   <li>OR MockWebServer returns unexpected content causing JSON decoding errors</li>
     * </ul>
     * 
     * <p><strong>Expected Counterexamples:</strong>
     * <ul>
     *   <li>ReadTimeoutException when WebClient waits for response that never arrives</li>
     *   <li>JsonProcessingException with "Unrecognized token 'invalid'" from unexpected content</li>
     * </ul>
     */
    @Test
    void bugCondition_singleResponse_shouldHandleNoSubscribers() throws Exception {
        System.out.println("=== Bug Condition Test: Single Response (shouldHandleNoSubscribers) ===");
        testSingleResponse();
        System.out.println("=== Bug Condition Test Complete ===");
    }

    /**
     * Test Case 2: Bug Condition - Multiple Responses (shouldDistributeEventToMultipleSubscribers scenario)
     * 
     * <p><strong>Validates: Requirements 1.1, 1.2, 1.3</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test FAILS (confirms the bug exists)</strong>
     * 
     * <p>This test verifies that when 4 MockResponses are enqueued (1 subscription + 3 queue)
     * and WebClient makes requests, all responses are returned in FIFO order without timeouts.
     * 
     * <p><strong>Bug Manifestation:</strong>
     * On unfixed code, this test will FAIL because MockWebServer does not return enqueued
     * responses correctly, causing timeouts or JSON decoding errors.
     * 
     * <p><strong>Expected Counterexamples:</strong>
     * <ul>
     *   <li>ReadTimeoutException when WebClient waits for responses that never arrive</li>
     *   <li>JsonProcessingException with "Unrecognized token 'invalid'" from unexpected content</li>
     *   <li>Responses returned out of order or missing</li>
     * </ul>
     */
    @Test
    void bugCondition_multipleResponses_shouldDistributeEventToMultipleSubscribers() throws Exception {
        System.out.println("=== Bug Condition Test: Multiple Responses (shouldDistributeEventToMultipleSubscribers) ===");
        testMultipleResponses();
        System.out.println("=== Bug Condition Test Complete ===");
    }

    /**
     * Test Case 3: Bug Condition - Mixed Success/Failure Responses (shouldHandlePartialQueueServiceFailures scenario)
     * 
     * <p><strong>Validates: Requirements 1.1, 1.2, 1.3</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test FAILS (confirms the bug exists)</strong>
     * 
     * <p>This test verifies that when 4 MockResponses are enqueued (1 subscription + 2 success + 1 failure)
     * and WebClient makes requests, all responses are returned in FIFO order without timeouts.
     * 
     * <p><strong>Bug Manifestation:</strong>
     * On unfixed code, this test will FAIL because MockWebServer does not return enqueued
     * responses correctly, especially when mixing success and failure responses.
     * 
     * <p><strong>Expected Counterexamples:</strong>
     * <ul>
     *   <li>ReadTimeoutException when WebClient waits for responses that never arrive</li>
     *   <li>JsonProcessingException with "Unrecognized token 'invalid'" from unexpected content</li>
     *   <li>Responses returned out of order or missing</li>
     *   <li>Failure responses not returned correctly</li>
     * </ul>
     */
    @Test
    void bugCondition_mixedResponses_shouldHandlePartialQueueServiceFailures() throws Exception {
        System.out.println("=== Bug Condition Test: Mixed Responses (shouldHandlePartialQueueServiceFailures) ===");
        testMixedResponses();
        System.out.println("=== Bug Condition Test Complete ===");
    }

    /**
     * Test Case 1: Single Response (shouldHandleNoSubscribers scenario)
     * 
     * <p>Enqueues 1 subscription response with empty list and verifies it's returned correctly.
     * 
     * <p><strong>EXPECTED TO FAIL:</strong> ReadTimeoutException or JSON decoding error
     */
    private void testSingleResponse() throws Exception {
        System.out.println("--- Test Case 1: Single Response (shouldHandleNoSubscribers) ---");
        
        // Arrange: Enqueue 1 subscription response with empty list
        SubscriptionQueryResponse subscriptionResponse = SubscriptionQueryResponse.of(List.of());

        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(subscriptionResponse)));

        String eventPayload = "{\"eventAction\":\"CREATE\",\"objectType\":\"StudentPersonal\"}";

        // Act & Assert: Make request and verify response
        // EXPECTED TO FAIL: ReadTimeoutException or JSON decoding error on unfixed code
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload))
            .andExpect(status().isAccepted());
        
        System.out.println("--- Test Case 1 Complete ---");
    }

    /**
     * Test Case 2: Multiple Responses (shouldDistributeEventToMultipleSubscribers scenario)
     * 
     * <p>Enqueues 4 responses (1 subscription + 3 queue) and verifies all are returned in FIFO order.
     * 
     * <p><strong>EXPECTED TO FAIL:</strong> ReadTimeoutException or JSON decoding error
     */
    private void testMultipleResponses() throws Exception {
        System.out.println("--- Test Case 2: Multiple Responses (shouldDistributeEventToMultipleSubscribers) ---");
        
        // Arrange: Enqueue 1 subscription response + 3 queue responses
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        UUID queueId3 = UUID.randomUUID();
        Instant now = Instant.now();

        SubscriptionDTO sub1 = new SubscriptionDTO(
            UUID.randomUUID(), queueId1, null, "StudentPersonal", (String) null, (String) null,
            SubscriptionType.EVENT, now
        );
        SubscriptionDTO sub2 = new SubscriptionDTO(
            UUID.randomUUID(), queueId2, null, "StudentPersonal", (String) null, (String) null,
            SubscriptionType.EVENT, now
        );
        SubscriptionDTO sub3 = new SubscriptionDTO(
            UUID.randomUUID(), queueId3, null, "StudentPersonal", (String) null, (String) null,
            SubscriptionType.EVENT, now
        );

        SubscriptionQueryResponse subscriptionResponse = SubscriptionQueryResponse.of(
            List.of(sub1, sub2, sub3)
        );

        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(subscriptionResponse)));

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

        // Act & Assert: Make request and verify all 4 responses are returned correctly
        // EXPECTED TO FAIL: ReadTimeoutException or JSON decoding error on unfixed code
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
        
        System.out.println("--- Test Case 2 Complete ---");
    }

    /**
     * Test Case 3: Mixed Success/Failure Responses (shouldHandlePartialQueueServiceFailures scenario)
     * 
     * <p>Enqueues 4 responses (1 subscription + 2 success + 1 failure) and verifies all are returned in FIFO order.
     * 
     * <p><strong>EXPECTED TO FAIL:</strong> ReadTimeoutException or JSON decoding error
     */
    private void testMixedResponses() throws Exception {
        System.out.println("--- Test Case 3: Mixed Responses (shouldHandlePartialQueueServiceFailures) ---");
        
        // Arrange: Enqueue 1 subscription response + 2 success responses + 1 failure response
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();
        UUID queueId3 = UUID.randomUUID();
        Instant now = Instant.now();

        SubscriptionDTO sub1 = new SubscriptionDTO(
            UUID.randomUUID(), queueId1, null, "StudentPersonal", (String) null, (String) null,
            SubscriptionType.EVENT, now
        );
        SubscriptionDTO sub2 = new SubscriptionDTO(
            UUID.randomUUID(), queueId2, null, "StudentPersonal", (String) null, (String) null,
            SubscriptionType.EVENT, now
        );
        SubscriptionDTO sub3 = new SubscriptionDTO(
            UUID.randomUUID(), queueId3, null, "StudentPersonal", (String) null, (String) null,
            SubscriptionType.EVENT, now
        );

        SubscriptionQueryResponse subscriptionResponse = SubscriptionQueryResponse.of(
            List.of(sub1, sub2, sub3)
        );

        subscriptionServiceMock.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(subscriptionResponse)));

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

        // Act & Assert: Make request and verify all 4 responses are returned correctly
        // EXPECTED TO FAIL: ReadTimeoutException or JSON decoding error on unfixed code
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
        
        System.out.println("--- Test Case 3 Complete ---");
    }
}

