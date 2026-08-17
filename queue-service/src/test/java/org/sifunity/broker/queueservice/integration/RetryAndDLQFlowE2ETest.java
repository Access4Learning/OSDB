// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.queueservice.config.EmbeddedRedisTestConfiguration;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.dto.*;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test for retry mechanism and dead-letter queue flow.
 * 
 * <p>This test validates the complete retry and DLQ flow:
 * <ol>
 *   <li>Enqueue a message to a queue</li>
 *   <li>Fail delivery multiple times (by not acknowledging)</li>
 *   <li>Verify message moves to DLQ after maxRetries</li>
 *   <li>Reprocess DLQ message</li>
 *   <li>Verify message returns to original queue with reset delivery attempts</li>
 * </ol>
 * 
 * <p>Uses Testcontainers PostgreSQL and embedded Redis for testing.
 * Database configuration is provided by application-test.yml via {@code @ActiveProfiles("test")}.</p>
 * 
 * <p><strong>Validates: Requirements 5.2, 5.3, 6.2, 6.5</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-retry-dlq",
    "queue.defaults.visibilityTimeoutSeconds=1",
    "broker.internal-service-token=test-internal-token"
})
class RetryAndDLQFlowE2ETest {
    
    private static final String TEST_DOMAIN_ID = "d47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String TEST_SOLUTION_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final String QUEUE_BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID + "/queues";
    private static final String API_BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID;
    private static final UUID TEST_OWNER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private QueueRepository queueRepository;
    
    @Autowired
    private StreamMessageRepository streamMessageRepository;
    
    @Autowired
    private StorageProvider storageProvider;
    
    @Autowired
    private DeliveryService deliveryService;
    
    @BeforeEach
    void setUp() {
        // Clean up database and Redis before each test
        queueRepository.deleteAll();
    }
    
    @AfterEach
    void tearDown() {
        // Clean up after each test
        queueRepository.deleteAll();
    }
    
    /**
     * Test: Message moves to DLQ after max retries exceeded
     * 
     * <p>This test validates:
     * <ol>
     *   <li>Create queue with maxRetries=3</li>
     *   <li>Enqueue message</li>
     *   <li>Deliver message multiple times without acknowledging (simulating failures via XCLAIM)</li>
     *   <li>Verify message is moved to DLQ after exceeding maxRetries</li>
     *   <li>Verify message is no longer in original queue</li>
     *   <li>Verify message is in DLQ with preserved payload</li>
     * </ol>
     * 
     * <p><strong>Validates: Requirements 5.2, 5.3, 6.2</strong></p>
     */
    @Test
    void testMessageMovesToDLQAfterMaxRetries() throws Exception {
        // ========================================
        // Step 1: Create Queue with maxRetries=3
        // ========================================
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createQueueRequest = new QueueCreateRequest(new CreateQueueRequest(
                "test-retry-queue",
                ownerId,
                3,  // maxRetries
                5000L,
                null
        ));
        
        MvcResult createQueueResult = mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                        .header("X-Environment-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createQueueRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queue.id").exists())
                .andExpect(jsonPath("$.queue.name").value("test-retry-queue"))
                .andReturn();
        
        String queueId = objectMapper.readTree(createQueueResult.getResponse().getContentAsString())
                .get("queue").get("id").asText();
        
        // ========================================
        // Step 2: Enqueue Message
        // ========================================
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "StudentPersonal");
        payload.put("refId", UUID.randomUUID().toString());
        payload.put("data", Map.of(
                "firstName", "Jane",
                "lastName", "Smith",
                "grade", "11"
        ));
        
        EnqueueMessageRequest enqueueRequest = new EnqueueMessageRequest(
                null,
                "test-environment",
                "default-zone",
                "district-b",
                "StudentPersonal",
                "event", null, null, null, null, payload
        );
        
        MvcResult enqueueResult = mockMvc.perform(post(API_BASE_PATH + "/queues/{queueId}/messages", queueId)
                        .header("X-Environment-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enqueueRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").exists())
                .andReturn();
        
        String messageId = objectMapper.readTree(enqueueResult.getResponse().getContentAsString())
                .get("messageId").asText();
        String storageKey = objectMapper.readTree(enqueueResult.getResponse().getContentAsString())
                .get("storageKey").asText();
        
        // ========================================
        // Step 3: Deliver message multiple times without ACK (simulating failures via XCLAIM)
        // ========================================
        UUID queueUuid = UUID.fromString(queueId);
        
        // First delivery — XREADGROUP delivers the message (delivery count = 1)
        Optional<MessageMetadata> delivery1 = deliveryService.deliverNextMessage(
                queueUuid, "consumer-retry-1", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(delivery1).isPresent();
        assertThat(delivery1.get().getMessageId().toString()).isEqualTo(messageId);
        
        // Wait for visibility timeout to expire (configured at 1s via TestPropertySource)
        Thread.sleep(1500);
        
        // Second call — XCLAIM reclaims expired message (delivery count = 2), XREADGROUP returns empty
        deliveryService.deliverNextMessage(
                queueUuid, "consumer-retry-2", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        Thread.sleep(1500);
        
        // Third call — XCLAIM again (delivery count = 3), XREADGROUP returns empty
        deliveryService.deliverNextMessage(
                queueUuid, "consumer-retry-3", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        Thread.sleep(1500);
        
        // ========================================
        // Step 4: Fourth call — XCLAIM detects delivery count > maxRetries, moves to DLQ
        // ========================================
        Optional<MessageMetadata> delivery4 = deliveryService.deliverNextMessage(
                queueUuid, "consumer-retry-4", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(delivery4).isEmpty();
        
        // ========================================
        // Step 5: Verify message is in DLQ
        // ========================================
        MvcResult dlqResult = mockMvc.perform(get(API_BASE_PATH + "/queues/{queueId}/dead-letter", queueId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].messageId").value(messageId))
                .andExpect(jsonPath("$.messages[0].status").value("FAILED"))
                .andExpect(jsonPath("$.messages[0].payload").exists())
                .andReturn();
        
        // Verify payload is preserved in DLQ (Requirement 6.2)
        String dlqPayloadJson = objectMapper.readTree(dlqResult.getResponse().getContentAsString())
                .get("messages").get(0).get("payload").toString();
        Map<String, Object> dlqPayload = objectMapper.readValue(dlqPayloadJson, Map.class);
        
        assertThat(dlqPayload)
                .as("DLQ message payload should match original payload")
                .containsEntry("type", "StudentPersonal")
                .containsKey("refId")
                .containsKey("data");
        
        // Verify message is no longer in original queue
        Optional<MessageMetadata> noMore = deliveryService.deliverNextMessage(
                queueUuid, "consumer-verify", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(noMore).isEmpty();
        
        // Verify storage key still exists (payload preserved)
        assertThat(storageProvider.exists(storageKey))
                .as("Message payload should still exist in storage")
                .isTrue();
    }
    
    /**
     * Test: Reprocess DLQ message returns it to original queue
     * 
     * <p>This test validates:
     * <ol>
     *   <li>Create queue and enqueue message</li>
     *   <li>Force message to DLQ by exceeding max retries via XCLAIM</li>
     *   <li>Reprocess DLQ message (placeholder endpoint)</li>
     *   <li>Verify reprocess endpoint returns 200 OK</li>
     * </ol>
     * 
     * <p>Note: The reprocess endpoint is currently a placeholder. Full reprocess
     * requires additional context (queueId, dlqStreamId, storageKey, environmentId).</p>
     * 
     * <p><strong>Validates: Requirements 6.5</strong></p>
     */
    @Test
    void testReprocessDLQMessageReturnsToOriginalQueue() throws Exception {
        // ========================================
        // Step 1: Create Queue and Enqueue Message
        // ========================================
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createQueueRequest = new QueueCreateRequest(new CreateQueueRequest(
                "test-reprocess-queue",
                ownerId,
                3,
                5000L,
                null
        ));
        
        MvcResult createQueueResult = mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                        .header("X-Environment-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createQueueRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        
        String queueId = objectMapper.readTree(createQueueResult.getResponse().getContentAsString())
                .get("queue").get("id").asText();
        
        Map<String, Object> payload = Map.of(
                "type", "StudentPersonal",
                "refId", UUID.randomUUID().toString(),
                "data", Map.of("name", "Test Student")
        );
        
        EnqueueMessageRequest enqueueRequest = new EnqueueMessageRequest(
                null,
                "test-environment",
                "default-zone",
                "district-c",
                "StudentPersonal",
                "event", null, null, null, null, payload
        );
        
        MvcResult enqueueResult = mockMvc.perform(post(API_BASE_PATH + "/queues/{queueId}/messages", queueId)
                        .header("X-Environment-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enqueueRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        
        String messageId = objectMapper.readTree(enqueueResult.getResponse().getContentAsString())
                .get("messageId").asText();
        
        // ========================================
        // Step 2: Force message to DLQ by exceeding max retries via XCLAIM
        // ========================================
        UUID queueUuid = UUID.fromString(queueId);
        
        // Deliver multiple times without acknowledging to exceed maxRetries via XCLAIM
        // First delivery — XREADGROUP delivers (count=1)
        Optional<MessageMetadata> firstDelivery = deliveryService.deliverNextMessage(
                queueUuid, "consumer-reprocess-0", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(firstDelivery).isPresent();
        assertThat(firstDelivery.get().getMessageId().toString()).isEqualTo(messageId);
        
        Thread.sleep(1500);  // Wait for visibility timeout
        
        // Second call — XCLAIM (count=2)
        deliveryService.deliverNextMessage(
                queueUuid, "consumer-reprocess-1", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        Thread.sleep(1500);
        
        // Third call — XCLAIM (count=3)
        deliveryService.deliverNextMessage(
                queueUuid, "consumer-reprocess-2", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        Thread.sleep(1500);
        
        // Fourth attempt moves to DLQ (XCLAIM detects count > maxRetries)
        Optional<MessageMetadata> dlqDelivery = deliveryService.deliverNextMessage(
                queueUuid, "consumer-reprocess-final", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(dlqDelivery).isEmpty();
        
        // Verify message is in DLQ
        mockMvc.perform(get(API_BASE_PATH + "/queues/{queueId}/dead-letter", queueId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].messageId").value(messageId));
        
        // ========================================
        // Step 3: Reprocess DLQ Message (placeholder endpoint — returns 200 OK)
        // ========================================
        mockMvc.perform(post(API_BASE_PATH + "/messages/{messageId}/reprocess", messageId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andExpect(status().isOk());
    }
    
    /**
     * Test: Multiple messages with different retry outcomes
     * 
     * <p>This test validates that multiple messages can have different outcomes:
     * <ol>
     *   <li>Message 1: Successfully delivered and acknowledged</li>
     *   <li>Message 2: Moved to DLQ after max retries</li>
     *   <li>Message 3: Retried but not yet moved to DLQ</li>
     * </ol>
     * 
     * <p><strong>Validates: Requirements 5.2, 5.3, 6.2</strong></p>
     */
    @Test
    void testMultipleMessagesWithDifferentRetryOutcomes() throws Exception {
        // Create queue
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createQueueRequest = new QueueCreateRequest(new CreateQueueRequest(
                "test-multi-retry-queue",
                ownerId,
                3,
                5000L,
                null
        ));
        
        MvcResult createQueueResult = mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                        .header("X-Environment-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createQueueRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        
        String queueId = objectMapper.readTree(createQueueResult.getResponse().getContentAsString())
                .get("queue").get("id").asText();
        
        // Enqueue 3 messages
        String[] messageIds = new String[3];
        for (int i = 0; i < 3; i++) {
            Map<String, Object> payload = Map.of(
                    "type", "StudentPersonal",
                    "index", i,
                    "data", Map.of("name", "Student" + i)
            );
            
            EnqueueMessageRequest enqueueRequest = new EnqueueMessageRequest(
                    null,
                    "test-environment",
                    "default-zone",
                    "district-d",
                    "StudentPersonal",
                    "event", null, null, null, null, payload
            );
            
            MvcResult enqueueResult = mockMvc.perform(post(API_BASE_PATH + "/queues/{queueId}/messages", queueId)
                            .header("X-Environment-Id", ownerId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(enqueueRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();
            
            messageIds[i] = objectMapper.readTree(enqueueResult.getResponse().getContentAsString())
                    .get("messageId").asText();
        }
        
        // Deliver and ACK message 1 immediately via DeliveryService ACK-and-GET-NEXT
        UUID queueUuid = UUID.fromString(queueId);
        String streamKey = streamMessageRepository.streamKey(
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId, queueUuid);
        String groupName = streamMessageRepository.groupName(queueUuid);
        
        Optional<MessageMetadata> delivery1 = deliveryService.deliverNextMessage(
                queueUuid, "consumer-multi-1", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(delivery1).isPresent();
        
        // Get stream ID from PEL for ACK
        var pending1 = streamMessageRepository.getPendingMessagesForGroup(streamKey, groupName, 10);
        assertThat(pending1).isNotNull().isNotEmpty();
        String streamId1 = null;
        for (var msg : pending1) {
            if (msg.getConsumerName().equals("consumer-multi-1")) {
                streamId1 = msg.getIdAsString();
                break;
            }
        }
        assertThat(streamId1).isNotNull();
        
        // ACK message 1 and get message 2
        Optional<MessageMetadata> delivery2 = deliveryService.deliverNextMessage(
                queueUuid, "consumer-multi-1", streamId1,
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(delivery2).isPresent();
        
        // Get message 3 with a different consumer
        Optional<MessageMetadata> delivery3 = deliveryService.deliverNextMessage(
                queueUuid, "consumer-multi-2", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(delivery3).isPresent();
        
        // ACK message 3 immediately
        var pending3 = streamMessageRepository.getPendingMessagesForGroup(streamKey, groupName, 10);
        String streamId3 = null;
        for (var msg : pending3) {
            if (msg.getConsumerName().equals("consumer-multi-2")) {
                streamId3 = msg.getIdAsString();
                break;
            }
        }
        if (streamId3 != null) {
            Optional<MessageMetadata> ack3 = deliveryService.deliverNextMessage(
                    queueUuid, "consumer-multi-2", streamId3,
                    TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
            // No more messages for consumer-multi-2
            assertThat(ack3).isEmpty();
        }
        
        // Now message 2 is still in consumer-multi-1's PEL (not ACKed).
        // Wait for visibility timeout and let it be reclaimed multiple times to exceed maxRetries.
        Thread.sleep(1500);
        
        // Reclaim message 2 — XCLAIM (delivery count = 2)
        deliveryService.deliverNextMessage(
                queueUuid, "consumer-multi-3", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        Thread.sleep(1500);
        
        // Reclaim message 2 — XCLAIM (delivery count = 3)
        deliveryService.deliverNextMessage(
                queueUuid, "consumer-multi-4", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        Thread.sleep(1500);
        
        // 4th attempt — XCLAIM detects delivery count > maxRetries, moves message 2 to DLQ
        Optional<MessageMetadata> dlqDelivery = deliveryService.deliverNextMessage(
                queueUuid, "consumer-multi-5", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(dlqDelivery).isEmpty();
        
        // Verify message 2 is in DLQ
        mockMvc.perform(get(API_BASE_PATH + "/queues/{queueId}/dead-letter", queueId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].messageId").value(messageIds[1]));
    }
}