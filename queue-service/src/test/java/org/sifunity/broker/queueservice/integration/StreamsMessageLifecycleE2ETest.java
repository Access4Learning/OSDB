// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.config.EmbeddedRedisTestConfiguration;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the complete Redis Streams message lifecycle.
 *
 * <p>Validates the full flow using the service layer directly:
 * <ol>
 *   <li>Create a queue via QueueService</li>
 *   <li>Enqueue a message via MessageService</li>
 *   <li>Deliver the message via DeliveryService</li>
 *   <li>ACK the message via DeliveryService (ACK-and-GET-NEXT pattern)</li>
 *   <li>Verify XLEN decreases after ACK</li>
 * </ol>
 *
 * <p><strong>Validates: Requirements 1.1, 2.1, 2.2, 7.1, 7.2</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-streams-lifecycle"
})
class StreamsMessageLifecycleE2ETest {

    private static final String DOMAIN_ID = "test-domain";
    private static final String SOLUTION_ID = "test-solution";
    private static final String DEFAULT_CONSUMER = "X_DEFAULT_Q_CONSUMER";
    private static final UUID TEST_OWNER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Autowired
    private QueueService queueService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private StreamMessageRepository streamMessageRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private StorageProvider storageProvider;

    @BeforeEach
    void setUp() {
        queueRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        queueRepository.deleteAll();
    }

    /**
     * Test: enqueue → deliver → ACK → verify XLEN decreases.
     *
     * <p>Validates that after a message is enqueued, XLEN is 1.
     * After delivery + ACK (via the ACK-and-GET-NEXT pattern), XLEN drops to 0.</p>
     */
    @Test
    void enqueue_deliver_ack_verifiesXlenDecreases() {
        // Step 1: Create queue
        UUID ownerId = TEST_OWNER_ID;
        QueueDTO queue = queueService.createQueue(
                "lifecycle-test-queue", ownerId, 3, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        String streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, ownerId, queueId);

        // Step 2: Enqueue a message
        Map<String, Object> payload = Map.of("type", "StudentPersonal", "data", Map.of("name", "Alice"));
        MessageMetadata enqueued = messageService.enqueueMessage(
                queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                ownerId, "default-zone", "default", "event", "StudentPersonal", payload);

        assertThat(enqueued.getMessageId()).isNotNull();
        assertThat(enqueued.getStorageKey()).isNotNull();

        // Verify XLEN is 1 after enqueue
        long xlenAfterEnqueue = streamMessageRepository.messageCount(streamKey);
        assertThat(xlenAfterEnqueue).isEqualTo(1);

        // Step 3: Deliver the message (first call, no deleteMessageId)
        Optional<MessageMetadata> delivered = deliveryService.deliverNextMessage(
                queueId, DEFAULT_CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);

        assertThat(delivered).isPresent();
        assertThat(delivered.get().getMessageId()).isEqualTo(enqueued.getMessageId());
        assertThat(delivered.get().getPayload()).isNotNull();

        // XLEN should still be 1 (message is in PEL, not yet ACKed)
        long xlenAfterDeliver = streamMessageRepository.messageCount(streamKey);
        assertThat(xlenAfterDeliver).isEqualTo(1);

        // Step 4: ACK the message via ACK-and-GET-NEXT (pass the stream ID as deleteMessageId)
        // The stream ID is now returned in MessageMetadata
        String streamId = delivered.get().getStreamId();
        assertThat(streamId).isNotNull();

        // ACK-and-GET-NEXT: acknowledge previous message and get next (should be empty)
        Optional<MessageMetadata> next = deliveryService.deliverNextMessage(
                queueId, DEFAULT_CONSUMER, streamId, DOMAIN_ID, SOLUTION_ID, ownerId);

        // No more messages
        assertThat(next).isEmpty();

        // Step 5: Verify XLEN decreased to 0
        long xlenAfterAck = streamMessageRepository.messageCount(streamKey);
        assertThat(xlenAfterAck).isEqualTo(0);

        // Verify payload was cleaned up from storage
        assertThat(storageProvider.exists(enqueued.getStorageKey())).isFalse();
    }

    /**
     * Test: Multiple messages — enqueue 3, deliver and ACK each, verify XLEN decreases step by step.
     */
    @Test
    void multipleMessages_enqueueDeliverAck_xlenDecreasesStepByStep() {
        UUID ownerId = TEST_OWNER_ID;
        QueueDTO queue = queueService.createQueue(
                "multi-lifecycle-queue", ownerId, 3, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        String streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, ownerId, queueId);

        // Enqueue 3 messages
        for (int i = 0; i < 3; i++) {
            Map<String, Object> payload = Map.of("index", i, "data", "msg-" + i);
            messageService.enqueueMessage(
                    queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                    ownerId, "zone", "ctx", "event", "TestService", payload);
        }

        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(3);

        // Deliver and ACK each message one by one
        String prevStreamId = "";
        for (int i = 0; i < 3; i++) {
            Optional<MessageMetadata> delivered = deliveryService.deliverNextMessage(
                    queueId, DEFAULT_CONSUMER, prevStreamId, DOMAIN_ID, SOLUTION_ID, ownerId);
            assertThat(delivered).isPresent();

            // Get the stream ID from the delivered metadata for the next ACK
            prevStreamId = delivered.get().getStreamId();
            assertThat(prevStreamId).isNotNull();
        }

        // ACK the last message (no more messages expected)
        Optional<MessageMetadata> last = deliveryService.deliverNextMessage(
                queueId, DEFAULT_CONSUMER, prevStreamId, DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(last).isEmpty();

        // All messages ACKed — XLEN should be 0
        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(0);
    }

    /**
     * Test: SIF §9.3.1 idempotent delivery — repeated queries without deleteMessageId
     * return the SAME message.
     */
    @Test
    void repeatedQueryWithoutDelete_returnsSameMessage() {
        UUID ownerId = TEST_OWNER_ID;
        QueueDTO queue = queueService.createQueue(
                "idempotent-test-queue", ownerId, 3, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        // Enqueue a message
        Map<String, Object> payload = Map.of("type", "StudentPersonal", "data", "idempotent-test");
        messageService.enqueueMessage(
                queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                ownerId, "zone", "ctx", "event", "TestService", payload);

        // First call — delivers the message
        Optional<MessageMetadata> first = deliveryService.deliverNextMessage(
                queueId, DEFAULT_CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(first).isPresent();
        UUID firstMessageId = first.get().getMessageId();
        String firstStreamId = first.get().getStreamId();

        // Second call (no deleteMessageId) — should return the SAME message
        Optional<MessageMetadata> second = deliveryService.deliverNextMessage(
                queueId, DEFAULT_CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(second).isPresent();
        assertThat(second.get().getMessageId()).isEqualTo(firstMessageId);
        assertThat(second.get().getStreamId()).isEqualTo(firstStreamId);

        // Third call (no deleteMessageId) — still the same message
        Optional<MessageMetadata> third = deliveryService.deliverNextMessage(
                queueId, DEFAULT_CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(third).isPresent();
        assertThat(third.get().getMessageId()).isEqualTo(firstMessageId);
    }

    // ── Helper ──────────────────────────────────────────────────────────

    /**
     * Retrieves the first pending stream ID for a consumer from the PEL.
     */
    private String getStreamIdFromPel(String streamKey, UUID queueId, String consumerName) {
        String groupName = streamMessageRepository.groupName(queueId);
        var pending = streamMessageRepository.getPendingMessagesForGroup(streamKey, groupName, 10);
        if (pending != null && pending.size() > 0) {
            for (var msg : pending) {
                if (msg.getConsumerName().equals(consumerName)) {
                    return msg.getIdAsString();
                }
            }
        }
        return null;
    }
}
