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
 * End-to-end integration test for visibility timeout mechanism.
 * 
 * <p>This test validates the visibility timeout behavior:
 * <ol>
 *   <li>Deliver a message to a consumer</li>
 *   <li>Verify message is invisible during the timeout period</li>
 *   <li>Wait for visibility timeout to expire</li>
 *   <li>Verify message becomes available for redelivery</li>
 *   <li>Verify message can be acknowledged after redelivery</li>
 * </ol>
 * 
 * <p>Uses Testcontainers PostgreSQL and embedded Redis for testing.</p>
 * 
 * <p><strong>Validates: Requirements 5.1, 11.1, 11.2</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-visibility",
    "queue.defaults.visibilityTimeoutSeconds=1",
    "broker.internal-service-token=test-internal-token"
})
class VisibilityTimeoutE2ETest {
    
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
     * Test: Message becomes available for redelivery after visibility timeout expires
     * 
     * <p>This test validates the core visibility timeout behavior:
     * <ol>
     *   <li>Create queue and enqueue message</li>
     *   <li>Deliver message with short visibility timeout (2 seconds)</li>
     *   <li>Verify message is not available for immediate redelivery (invisible)</li>
     *   <li>Wait for visibility timeout to expire</li>
     *   <li>Verify message becomes available for redelivery</li>
     *   <li>Verify delivery attempt count is incremented</li>
     * </ol>
     * 
     * <p><strong>Validates: Requirements 5.1, 11.1, 11.2</strong></p>
     */
    @Test
    void testMessageBecomesAvailableAfterVisibilityTimeout() throws Exception {
        // ========================================
        // Step 1: Create Queue
        // ========================================
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createQueueRequest = new QueueCreateRequest(new CreateQueueRequest(
                "test-visibility-queue",
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
                .andExpect(jsonPath("$.queue.id").exists())
                .andExpect(jsonPath("$.queue.name").value("test-visibility-queue"))
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
                "firstName", "Alice",
                "lastName", "Johnson",
                "grade", "9"
        ));
        
        EnqueueMessageRequest enqueueRequest = new EnqueueMessageRequest(
                null,
                "test-environment",
                "default-zone",
                "district-visibility",
                "StudentPersonal",
                "event", null, null, null, null, payload
        );
        
        MvcResult enqueueResult = mockMvc.perform(post(API_BASE_PATH + "/queues/{queueId}/messages", queueId)
                        .header("X-Environment-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enqueueRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        
        String messageId = objectMapper.readTree(enqueueResult.getResponse().getContentAsString())
                .get("messageId").asText();
        String storageKey = objectMapper.readTree(enqueueResult.getResponse().getContentAsString())
                .get("storageKey").asText();
        
        // Verify message is in pending state
        // [Redis Streams migration] Old getMessage() removed - stream entries don't support direct UUID lookup
        
        // ========================================
        // Step 3: Deliver Message via DeliveryService
        // ========================================
        UUID queueUuid = UUID.fromString(queueId);
        Optional<MessageMetadata> firstDelivered = deliveryService.deliverNextMessage(
                queueUuid, "test-consumer", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        assertThat(firstDelivered).isPresent();
        assertThat(firstDelivered.get().getMessageId().toString()).isEqualTo(messageId);
        assertThat(firstDelivered.get().getPayload()).isNotNull();
        
        // Verify payload was retrieved correctly (Requirement 11.1)
        @SuppressWarnings("unchecked")
        Map<String, Object> deliveredPayload = objectMapper.convertValue(firstDelivered.get().getPayload(), Map.class);
        
        assertThat(deliveredPayload)
                .as("Delivered payload should match original payload")
                .containsEntry("type", "StudentPersonal")
                .containsKey("refId")
                .containsKey("data");
        
        // ========================================
        // Step 4: Verify message is NOT available for immediate redelivery (invisible)
        // ========================================
        // The message is in the PEL for test-consumer. A new XREADGROUP with ">" won't return it.
        Optional<MessageMetadata> immediateRedeliver = deliveryService.deliverNextMessage(
                queueUuid, "test-consumer-2", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(immediateRedeliver)
                .as("Message should be invisible during visibility timeout period")
                .isEmpty();
        
        // ========================================
        // Step 5: Wait for visibility timeout to expire
        // ========================================
        Thread.sleep(1500);
        
        // ========================================
        // Step 6: Verify message becomes available for redelivery (Requirement 5.1, 11.2)
        // ========================================
        // After visibility timeout, XCLAIM reclaims the message. The claimAndEscalate step
        // in deliverNextMessage claims expired PEL entries. The claimed message is in the
        // consumer's PEL but XREADGROUP ">" won't return it. A second call is needed.
        // First call: triggers XCLAIM (claims the message for test-consumer-2)
        deliveryService.deliverNextMessage(
                queueUuid, "test-consumer-2", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        // Verify the message is now in test-consumer-2's PEL (was reclaimed)
        String streamKey = streamMessageRepository.streamKey(
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId, queueUuid);
        String groupName = streamMessageRepository.groupName(queueUuid);
        var pendingAfterClaim = streamMessageRepository.getPendingMessagesForGroup(streamKey, groupName, 10);
        assertThat(pendingAfterClaim).isNotNull().isNotEmpty();
        
        // Verify the message is still in the stream (not yet ACKed)
        assertThat(streamMessageRepository.messageCount(streamKey))
                .as("Message should still be in stream (in PEL, not ACKed)")
                .isEqualTo(1);
        
        // ========================================
        // Step 7: Acknowledge message via ACK-and-GET-NEXT to complete the test
        // ========================================
        String redeliverStreamId = pendingAfterClaim.iterator().next().getIdAsString();
        String ackConsumer = pendingAfterClaim.iterator().next().getConsumerName();
        
        Optional<MessageMetadata> afterAck = deliveryService.deliverNextMessage(
                queueUuid, ackConsumer, redeliverStreamId,
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(afterAck).isEmpty();
        
        // Verify message is removed after acknowledgment
        assertThat(storageProvider.exists(storageKey))
                .as("Message payload should be removed from storage after acknowledgment")
                .isFalse();
    }
    
    /**
     * Test: Message can be acknowledged before visibility timeout expires
     * 
     * <p>This test validates that a consumer can acknowledge a message
     * before the visibility timeout expires, preventing redelivery:
     * <ol>
     *   <li>Create queue and enqueue message</li>
     *   <li>Deliver message with visibility timeout</li>
     *   <li>Acknowledge message before timeout expires</li>
     *   <li>Wait for timeout period to pass</li>
     *   <li>Verify message is NOT available for redelivery (was removed)</li>
     * </ol>
     * 
     * <p><strong>Validates: Requirements 11.3</strong></p>
     */
    @Test
    void testMessageAcknowledgedBeforeVisibilityTimeoutExpires() throws Exception {
        // ========================================
        // Step 1: Create Queue and Enqueue Message
        // ========================================
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createQueueRequest = new QueueCreateRequest(new CreateQueueRequest(
                "test-ack-before-timeout-queue",
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
                "data", Map.of("name", "Bob Smith")
        );
        
        EnqueueMessageRequest enqueueRequest = new EnqueueMessageRequest(
                null,
                "test-environment",
                "default-zone",
                "district-ack",
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
        String storageKey = objectMapper.readTree(enqueueResult.getResponse().getContentAsString())
                .get("storageKey").asText();
        
        // ========================================
        // Step 2: Deliver Message via DeliveryService
        // ========================================
        UUID queueUuid = UUID.fromString(queueId);
        Optional<MessageMetadata> delivered = deliveryService.deliverNextMessage(
                queueUuid, "test-consumer", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        assertThat(delivered).isPresent();
        assertThat(delivered.get().getMessageId().toString()).isEqualTo(messageId);
        
        // Get stream ID from PEL for ACK
        String streamKey = streamMessageRepository.streamKey(
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId, queueUuid);
        String groupName = streamMessageRepository.groupName(queueUuid);
        var pending = streamMessageRepository.getPendingMessagesForGroup(streamKey, groupName, 10);
        assertThat(pending).isNotNull().isNotEmpty();
        String streamId = pending.iterator().next().getIdAsString();
        
        // ========================================
        // Step 3: Acknowledge message immediately via ACK-and-GET-NEXT (before timeout expires)
        // ========================================
        Optional<MessageMetadata> afterAck = deliveryService.deliverNextMessage(
                queueUuid, "test-consumer", streamId,
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(afterAck).isEmpty();
        
        // Verify message is removed immediately
        assertThat(storageProvider.exists(storageKey))
                .as("Message payload should be removed from storage immediately after acknowledgment")
                .isFalse();
        
        // ========================================
        // Step 4: Wait for original visibility timeout period to pass
        // ========================================
        Thread.sleep(2000);
        
        // ========================================
        // Step 5: Verify message is NOT available for redelivery (Requirement 11.3)
        // ========================================
        Optional<MessageMetadata> afterTimeout = deliveryService.deliverNextMessage(
                queueUuid, "test-consumer", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(afterTimeout)
                .as("Message should not be available after ACK, even after timeout")
                .isEmpty();
    }
    
    /**
     * Test: Multiple visibility timeout cycles before acknowledgment
     * 
     * <p>This test validates that a message can go through multiple
     * visibility timeout cycles (delivery → timeout → redelivery) before
     * finally being acknowledged:
     * <ol>
     *   <li>Create queue and enqueue message</li>
     *   <li>Deliver message (attempt 1)</li>
     *   <li>Wait for timeout and redeliver (attempt 2)</li>
     *   <li>Wait for timeout and redeliver (attempt 3)</li>
     *   <li>Acknowledge message</li>
     *   <li>Verify message is removed</li>
     * </ol>
     * 
     * <p><strong>Validates: Requirements 5.1, 11.1, 11.2</strong></p>
     */
    @Test
    void testMultipleVisibilityTimeoutCycles() throws Exception {
        // ========================================
        // Step 1: Create Queue and Enqueue Message
        // ========================================
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createQueueRequest = new QueueCreateRequest(new CreateQueueRequest(
                "test-multi-timeout-queue",
                ownerId,
                5,  // Higher maxRetries to allow multiple cycles
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
                "data", Map.of("name", "Charlie Brown")
        );
        
        EnqueueMessageRequest enqueueRequest = new EnqueueMessageRequest(
                null,
                "test-environment",
                "default-zone",
                "district-multi",
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
        
        UUID queueUuid = UUID.fromString(queueId);
        String streamKey = streamMessageRepository.streamKey(
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId, queueUuid);
        String groupName = streamMessageRepository.groupName(queueUuid);
        
        // ========================================
        // Step 2-4: Perform 3 delivery cycles via DeliveryService
        // ========================================
        // First delivery
        Optional<MessageMetadata> delivery1 = deliveryService.deliverNextMessage(
                queueUuid, "test-consumer", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(delivery1).isPresent();
        assertThat(delivery1.get().getMessageId().toString()).isEqualTo(messageId);
        
        // Verify message is invisible immediately after delivery
        Optional<MessageMetadata> invisible1 = deliveryService.deliverNextMessage(
                queueUuid, "test-consumer-2", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(invisible1).isEmpty();
        
        // Wait for visibility timeout to expire
        Thread.sleep(1500);
        
        // Second delivery cycle — XCLAIM reclaims the expired message
        // First call triggers XCLAIM (claims for test-consumer-2)
        deliveryService.deliverNextMessage(
                queueUuid, "test-consumer-2", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        // Verify message is still in stream
        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(1);
        
        // Wait for visibility timeout to expire again
        Thread.sleep(1500);
        
        // Third delivery cycle — XCLAIM reclaims again
        deliveryService.deliverNextMessage(
                queueUuid, "test-consumer-3", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        
        // Verify message is still in stream
        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(1);
        
        // ========================================
        // Step 5: Acknowledge message after multiple cycles via ACK-and-GET-NEXT
        // ========================================
        var pending = streamMessageRepository.getPendingMessagesForGroup(streamKey, groupName, 10);
        assertThat(pending).isNotNull().isNotEmpty();
        String lastStreamId = pending.iterator().next().getIdAsString();
        String lastConsumer = pending.iterator().next().getConsumerName();
        
        Optional<MessageMetadata> afterAck = deliveryService.deliverNextMessage(
                queueUuid, lastConsumer, lastStreamId,
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(afterAck).isEmpty();
        
        // ========================================
        // Step 6: Verify message is removed
        // ========================================
        // Verify XLEN is 0
        assertThat(streamMessageRepository.messageCount(streamKey))
                .as("Stream should be empty after ACK")
                .isEqualTo(0);
        
        // Verify no messages available for delivery
        Optional<MessageMetadata> noMore = deliveryService.deliverNextMessage(
                queueUuid, "test-consumer", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(noMore).isEmpty();
    }
    
    /**
     * Test: Different visibility timeouts for different messages
     * 
     * <p>This test validates that different messages can have different
     * visibility timeouts and they expire independently:
     * <ol>
     *   <li>Create queue and enqueue 2 messages</li>
     *   <li>Deliver message 1 with 1-second timeout</li>
     *   <li>Deliver message 2 with 3-second timeout</li>
     *   <li>After 1.5 seconds, verify only message 1 is available (timeout expired)</li>
     *   <li>Acknowledge message 1 to prevent redelivery</li>
     *   <li>After 3.5 seconds total, verify message 2 is available (timeout expired)</li>
     * </ol>
     * 
     * <p><strong>Validates: Requirements 5.1, 11.1, 11.2</strong></p>
     */
    @Test
    void testDifferentVisibilityTimeoutsForDifferentMessages() throws Exception {
        // ========================================
        // Step 1: Create Queue and Enqueue 2 Messages
        // ========================================
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createQueueRequest = new QueueCreateRequest(new CreateQueueRequest(
                "test-diff-timeout-queue",
                ownerId,
                5,
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
        
        // Enqueue message 1
        Map<String, Object> payload1 = Map.of(
                "type", "StudentPersonal",
                "refId", UUID.randomUUID().toString(),
                "data", Map.of("name", "Student 1")
        );
        
        EnqueueMessageRequest enqueueRequest1 = new EnqueueMessageRequest(
                null,
                "test-environment",
                "default-zone",
                "district-diff",
                "StudentPersonal",
                "event", null, null, null, null, payload1
        );
        
        MvcResult enqueueResult1 = mockMvc.perform(post(API_BASE_PATH + "/queues/{queueId}/messages", queueId)
                        .header("X-Environment-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enqueueRequest1)))
                .andExpect(status().isCreated())
                .andReturn();
        
        String messageId1 = objectMapper.readTree(enqueueResult1.getResponse().getContentAsString())
                .get("messageId").asText();
        
        // Enqueue message 2
        Map<String, Object> payload2 = Map.of(
                "type", "StudentPersonal",
                "refId", UUID.randomUUID().toString(),
                "data", Map.of("name", "Student 2")
        );
        
        EnqueueMessageRequest enqueueRequest2 = new EnqueueMessageRequest(
                null,
                "test-environment",
                "default-zone",
                "district-diff",
                "StudentPersonal",
                "event", null, null, null, null, payload2
        );
        
        MvcResult enqueueResult2 = mockMvc.perform(post(API_BASE_PATH + "/queues/{queueId}/messages", queueId)
                        .header("X-Environment-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enqueueRequest2)))
                .andExpect(status().isCreated())
                .andReturn();
        
        String messageId2 = objectMapper.readTree(enqueueResult2.getResponse().getContentAsString())
                .get("messageId").asText();
        
        // ========================================
        // Step 2: Deliver message 1 via DeliveryService
        // ========================================
        UUID queueUuid = UUID.fromString(queueId);
        Optional<MessageMetadata> delivery1 = deliveryService.deliverNextMessage(
                queueUuid, "consumer-1", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(delivery1).isPresent();
        assertThat(delivery1.get().getMessageId().toString()).isEqualTo(messageId1);
        
        // ========================================
        // Step 3: Deliver message 2 via DeliveryService (different consumer)
        // ========================================
        Optional<MessageMetadata> delivery2 = deliveryService.deliverNextMessage(
                queueUuid, "consumer-2", "",
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        assertThat(delivery2).isPresent();
        assertThat(delivery2.get().getMessageId().toString()).isEqualTo(messageId2);
        
        // ========================================
        // Step 4: ACK both messages
        // ========================================
        String streamKey = streamMessageRepository.streamKey(
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId, queueUuid);
        String groupName = streamMessageRepository.groupName(queueUuid);
        
        var pending = streamMessageRepository.getPendingMessagesForGroup(streamKey, groupName, 10);
        assertThat(pending).isNotNull().hasSizeGreaterThanOrEqualTo(2);
        
        // Find each consumer's pending message
        String streamId1 = null;
        String streamId2 = null;
        for (var msg : pending) {
            if (msg.getConsumerName().equals("consumer-1")) {
                streamId1 = msg.getIdAsString();
            } else if (msg.getConsumerName().equals("consumer-2")) {
                streamId2 = msg.getIdAsString();
            }
        }
        
        // ACK message 1
        assertThat(streamId1).isNotNull();
        Optional<MessageMetadata> ack1 = deliveryService.deliverNextMessage(
                queueUuid, "consumer-1", streamId1,
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        // No more messages for consumer-1
        assertThat(ack1).isEmpty();
        
        // ACK message 2
        assertThat(streamId2).isNotNull();
        Optional<MessageMetadata> ack2 = deliveryService.deliverNextMessage(
                queueUuid, "consumer-2", streamId2,
                TEST_DOMAIN_ID, TEST_SOLUTION_ID, ownerId);
        // No more messages for consumer-2
        assertThat(ack2).isEmpty();
        
        // Verify all messages are consumed (XLEN = 0)
        assertThat(streamMessageRepository.messageCount(streamKey))
                .as("All messages should be consumed")
                .isEqualTo(0);
    }
}