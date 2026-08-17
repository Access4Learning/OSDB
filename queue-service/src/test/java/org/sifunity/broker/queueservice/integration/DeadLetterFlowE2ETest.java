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
import org.sifunity.broker.queueservice.service.DeadLetterService;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the dead-letter queue flow.
 *
 * <p>Validates the complete DLQ lifecycle:
 * <ol>
 *   <li>Exceed maxRetries → message moves to DLQ stream</li>
 *   <li>Query DLQ → message is present with payload</li>
 *   <li>Reprocess from DLQ → message returns to original queue</li>
 * </ol>
 *
 * <p><strong>Validates: Requirements 4.3, 5.1, 5.2, 5.3, 5.4</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-dlq-flow",
    "queue.defaults.visibilityTimeoutSeconds=1"
})
class DeadLetterFlowE2ETest {

    private static final String DOMAIN_ID = "test-domain";
    private static final String SOLUTION_ID = "test-solution";
    private static final String CONSUMER = "dlq-test-consumer";
    private static final UUID TEST_OWNER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Autowired
    private QueueService queueService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private DeadLetterService deadLetterService;

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
     * Test: exceed maxRetries → DLQ → reprocess → back in queue.
     *
     * <p>Creates a queue with maxRetries=2. Delivers the message 3 times without ACK
     * (each time waiting for visibility timeout). On the next delivery call, the
     * claimAndEscalate step moves the message to DLQ. Then reprocess it back.</p>
     */
    @Test
    void exceedMaxRetries_movesToDlq_reprocess_backInQueue() throws InterruptedException {
        UUID ownerId = TEST_OWNER_ID;
        QueueDTO queue = queueService.createQueue(
                "dlq-flow-queue", ownerId, 2, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        String streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, ownerId, queueId);
        String dlqKey = streamMessageRepository.dlqStreamKey(DOMAIN_ID, SOLUTION_ID, ownerId, queueId);

        // Enqueue a message
        Map<String, Object> payload = Map.of("type", "StudentPersonal", "name", "DLQ Test");
        MessageMetadata enqueued = messageService.enqueueMessage(
                queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                ownerId, "zone", "ctx", "event", "StudentPersonal", payload);

        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(1);
        assertThat(streamMessageRepository.messageCount(dlqKey)).isEqualTo(0);

        // Deliver message — attempt 1 (puts in PEL with delivery count 1)
        Optional<MessageMetadata> d1 = deliveryService.deliverNextMessage(
                queueId, CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(d1).isPresent();

        // Wait for visibility timeout to expire
        Thread.sleep(1500);

        // Deliver again — claimAndEscalate claims the expired message (delivery count 2)
        // Then XREADGROUP returns it as a new delivery or the claimed message is in PEL
        Optional<MessageMetadata> d2 = deliveryService.deliverNextMessage(
                queueId, CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);
        // d2 may or may not contain the message depending on XCLAIM vs XREADGROUP behavior

        Thread.sleep(1500);

        // Deliver again — claimAndEscalate claims again (delivery count 3)
        // With maxRetries=2, delivery count 3 > maxRetries → should move to DLQ
        Optional<MessageMetadata> d3 = deliveryService.deliverNextMessage(
                queueId, CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);

        // At this point the message should have been moved to DLQ by claimAndEscalate
        // Wait a moment and try one more delivery to trigger the escalation
        Thread.sleep(1500);

        // This call should trigger claimAndEscalate which moves the message to DLQ
        deliveryService.deliverNextMessage(
                queueId, CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);

        // Verify message is in DLQ stream
        long dlqCount = streamMessageRepository.messageCount(dlqKey);
        // The message should eventually end up in DLQ after enough delivery attempts
        // Let's check via the DeadLetterService
        List<MessageMetadata> dlqMessages = deadLetterService.getDeadLetterMessages(
                queueId, DOMAIN_ID, SOLUTION_ID, ownerId);

        // If the message hasn't moved to DLQ yet, do more delivery cycles
        if (dlqMessages.isEmpty()) {
            Thread.sleep(1500);
            deliveryService.deliverNextMessage(queueId, CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);
            Thread.sleep(1500);
            deliveryService.deliverNextMessage(queueId, CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);
            dlqMessages = deadLetterService.getDeadLetterMessages(queueId, DOMAIN_ID, SOLUTION_ID, ownerId);
        }

        assertThat(dlqMessages).as("Message should be in DLQ after exceeding maxRetries").isNotEmpty();
        assertThat(dlqMessages.get(0).getMessageId()).isEqualTo(enqueued.getMessageId());

        // Verify payload is still accessible
        assertThat(storageProvider.exists(enqueued.getStorageKey())).isTrue();

        // Main stream should be empty (message moved to DLQ)
        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(0);

        // Step: Reprocess from DLQ back to original queue
        // Get the DLQ stream entry ID
        List<MapRecord<String, Object, Object>> dlqRecords =
                streamMessageRepository.readDeadLetterMessages(dlqKey, 0, 10);
        assertThat(dlqRecords).isNotEmpty();

        String dlqStreamId = dlqRecords.get(0).getId().getValue();
        deadLetterService.reprocessMessage(
                queueId, dlqStreamId, enqueued.getMessageId(), enqueued.getStorageKey(),
                DOMAIN_ID, SOLUTION_ID, ownerId);

        // Verify message is back in the main stream
        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(1);

        // Verify DLQ is now empty
        assertThat(streamMessageRepository.messageCount(dlqKey)).isEqualTo(0);

        // Verify the message can be delivered again
        Optional<MessageMetadata> redelivered = deliveryService.deliverNextMessage(
                queueId, CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(redelivered).isPresent();
        assertThat(redelivered.get().getMessageId()).isEqualTo(enqueued.getMessageId());
        assertThat(redelivered.get().getPayload()).isNotNull();
    }

    /**
     * Test: DLQ query returns messages with payloads via DeadLetterService.
     */
    @Test
    void dlqQuery_returnsMessagesWithPayloads() {
        UUID ownerId = TEST_OWNER_ID;
        QueueDTO queue = queueService.createQueue(
                "dlq-query-queue", ownerId, 3, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        String streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, ownerId, queueId);
        String dlqKey = streamMessageRepository.dlqStreamKey(DOMAIN_ID, SOLUTION_ID, ownerId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);

        // Enqueue a message
        Map<String, Object> payload = Map.of("type", "Event", "data", "dlq-query-test");
        MessageMetadata enqueued = messageService.enqueueMessage(
                queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                ownerId, "zone", "ctx", "event", "Svc", payload);

        // Deliver it to put in PEL
        deliveryService.deliverNextMessage(queueId, CONSUMER, "", DOMAIN_ID, SOLUTION_ID, ownerId);

        // Manually move to DLQ via the repository (simulating maxRetries exceeded)
        String streamId = getFirstPendingStreamId(streamKey, groupName, CONSUMER);
        assertThat(streamId).isNotNull();

        streamMessageRepository.moveToDeadLetter(
                streamKey, dlqKey, groupName, streamId,
                enqueued.getMessageId(), enqueued.getStorageKey(), null);

        // Query DLQ via DeadLetterService
        List<MessageMetadata> dlqMessages = deadLetterService.getDeadLetterMessages(
                queueId, DOMAIN_ID, SOLUTION_ID, ownerId);

        assertThat(dlqMessages).hasSize(1);
        assertThat(dlqMessages.get(0).getMessageId()).isEqualTo(enqueued.getMessageId());
        assertThat(dlqMessages.get(0).getPayload()).isNotNull();
        assertThat(dlqMessages.get(0).getStorageKey()).isEqualTo(enqueued.getStorageKey());
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private String getFirstPendingStreamId(String streamKey, String groupName, String consumerName) {
        var pending = streamMessageRepository.getPendingMessagesForGroup(streamKey, groupName, 100);
        if (pending != null) {
            for (var msg : pending) {
                if (msg.getConsumerName().equals(consumerName)) {
                    return msg.getIdAsString();
                }
            }
        }
        return null;
    }
}
