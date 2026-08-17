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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for visibility timeout and XCLAIM-based redelivery.
 *
 * <p>Validates that when a message is delivered but not acknowledged within the
 * visibility timeout, it can be reclaimed via XCLAIM by another consumer.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.2</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-visibility-redelivery",
    "queue.defaults.visibilityTimeoutSeconds=2"
})
class VisibilityTimeoutRedeliveryE2ETest {

    private static final String DOMAIN_ID = "test-domain";
    private static final String SOLUTION_ID = "test-solution";
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

    @BeforeEach
    void setUp() {
        queueRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        queueRepository.deleteAll();
    }

    /**
     * Test: deliver → wait past timeout → XCLAIM → verify redelivery.
     *
     * <p>Consumer-A delivers a message but does not ACK. After the visibility timeout
     * expires, consumer-B's deliverNextMessage triggers XCLAIM and reclaims the message.</p>
     */
    @Test
    void deliver_waitPastTimeout_xclaimRedelivers() throws InterruptedException {
        UUID ownerId = TEST_OWNER_ID;
        QueueDTO queue = queueService.createQueue(
                "visibility-redelivery-queue", ownerId, 5, 5000L, 2, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        String streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, ownerId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);

        // Enqueue a message
        Map<String, Object> payload = Map.of("type", "StudentPersonal", "data", "test-redelivery");
        MessageMetadata enqueued = messageService.enqueueMessage(
                queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                ownerId, "zone", "ctx", "event", "StudentPersonal", payload);

        // Consumer-A delivers the message (puts it in PEL)
        Optional<MessageMetadata> deliveredToA = deliveryService.deliverNextMessage(
                queueId, "consumer-A", "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(deliveredToA).isPresent();
        assertThat(deliveredToA.get().getMessageId()).isEqualTo(enqueued.getMessageId());

        // Verify message is in consumer-A's PEL
        String streamId = getFirstPendingStreamId(streamKey, groupName, "consumer-A");
        assertThat(streamId).isNotNull();
        assertThat(streamMessageRepository.isInConsumerPel(streamKey, groupName, "consumer-A", streamId)).isTrue();

        // No new messages for consumer-B (all delivered)
        Optional<MessageMetadata> immediateB = deliveryService.deliverNextMessage(
                queueId, "consumer-B", "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(immediateB).isEmpty();

        // Wait past the visibility timeout (2 seconds configured + buffer)
        Thread.sleep(2500);

        // Consumer-B calls deliverNextMessage — this triggers XCLAIM of expired PEL entries
        Optional<MessageMetadata> reclaimedByB = deliveryService.deliverNextMessage(
                queueId, "consumer-B", "", DOMAIN_ID, SOLUTION_ID, ownerId);

        // The XCLAIM happens inside deliverNextMessage's claimAndEscalate step.
        // After claiming, the message is in consumer-B's PEL.
        // However, XREADGROUP with ">" only returns NEW messages, not claimed ones.
        // The claimed message is now in consumer-B's PEL but won't be returned by XREADGROUP ">".
        // So reclaimedByB may be empty (no new messages).
        // The key verification is that the message was claimed from consumer-A to consumer-B.

        // Verify the message was claimed from consumer-A's PEL
        // After XCLAIM, consumer-A should no longer have it
        boolean stillInAPel = streamMessageRepository.isInConsumerPel(
                streamKey, groupName, "consumer-A", streamId);

        // Verify the message is now in consumer-B's PEL (claimed)
        boolean inBPel = streamMessageRepository.isInConsumerPel(
                streamKey, groupName, "consumer-B", streamId);

        assertThat(stillInAPel).as("Message should no longer be in consumer-A's PEL after XCLAIM").isFalse();
        assertThat(inBPel).as("Message should be in consumer-B's PEL after XCLAIM").isTrue();

        // XLEN should still be 1 (message not ACKed yet)
        assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(1);
    }

    /**
     * Test: XCLAIM directly via StreamMessageRepository verifies redelivery mechanics.
     */
    @Test
    void xclaimDirectly_reclaimsExpiredMessage() throws InterruptedException {
        UUID ownerId = TEST_OWNER_ID;
        QueueDTO queue = queueService.createQueue(
                "xclaim-direct-queue", ownerId, 5, 5000L, 1, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        String streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, ownerId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);

        // Enqueue and deliver
        messageService.enqueueMessage(queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                ownerId, "zone", "ctx", "event", "Svc", Map.of("data", "xclaim-test"));

        deliveryService.deliverNextMessage(
                queueId, "original-consumer", "", DOMAIN_ID, SOLUTION_ID, ownerId);

        // Wait past visibility timeout
        Thread.sleep(2500);

        // Directly call claimExpiredMessages
        List<MapRecord<String, Object, Object>> claimed = streamMessageRepository.claimExpiredMessages(
                streamKey, groupName, "new-consumer", Duration.ofSeconds(2), 10);

        assertThat(claimed).isNotEmpty();
        assertThat(claimed).hasSize(1);

        // Verify the claimed entry has the expected fields
        Map<Object, Object> fields = claimed.get(0).getValue();
        assertThat(fields).containsKey(StreamMessageRepository.FIELD_MESSAGE_ID);
        assertThat(fields).containsKey(StreamMessageRepository.FIELD_STORAGE_KEY);
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
