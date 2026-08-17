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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for connectionId-based consumer concurrency.
 *
 * <p>Validates that multiple consumers with distinct connectionIds receive
 * distinct messages from the same queue via XREADGROUP consumer groups.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-concurrency"
})
class ConnectionIdConcurrencyE2ETest {

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
     * Test: Two consumers with distinct connectionIds receive distinct messages.
     *
     * <p>Enqueue 4 messages, then have consumer-A and consumer-B each call
     * deliverNextMessage. Each should get a unique message — no overlap.</p>
     */
    @Test
    void distinctConnectionIds_receiveDistinctMessages() {
        UUID ownerId = TEST_OWNER_ID;
        QueueDTO queue = queueService.createQueue(
                "concurrency-queue", ownerId, 3, 5000L, 2, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        // Enqueue 4 messages
        List<UUID> enqueuedIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> payload = Map.of("index", i, "data", "msg-" + i);
            MessageMetadata meta = messageService.enqueueMessage(
                    queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                    ownerId, "zone", "ctx", "event", "TestService", payload);
            enqueuedIds.add(meta.getMessageId());
        }

        // Consumer A delivers 2 messages
        Set<UUID> consumerAMessages = new HashSet<>();
        Optional<MessageMetadata> a1 = deliveryService.deliverNextMessage(
                queueId, "consumer-A", "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(a1).isPresent();
        consumerAMessages.add(a1.get().getMessageId());

        Optional<MessageMetadata> a2 = deliveryService.deliverNextMessage(
                queueId, "consumer-A", "", DOMAIN_ID, SOLUTION_ID, ownerId);
        // consumer-A already has a1 in PEL; calling with empty deleteMessageId gets next new message
        // Note: XREADGROUP with ">" gives the next undelivered message
        // But consumer-A already got a1 without ACKing. The second call should get a new message.
        // Actually, XREADGROUP with ">" always returns the next NEW message not yet delivered to ANY consumer in the group.
        // So consumer-A's second call gets the next undelivered message.

        // Consumer B delivers 2 messages
        Set<UUID> consumerBMessages = new HashSet<>();
        Optional<MessageMetadata> b1 = deliveryService.deliverNextMessage(
                queueId, "consumer-B", "", DOMAIN_ID, SOLUTION_ID, ownerId);
        Optional<MessageMetadata> b2 = deliveryService.deliverNextMessage(
                queueId, "consumer-B", "", DOMAIN_ID, SOLUTION_ID, ownerId);

        // Collect all delivered message IDs
        if (a2.isPresent()) consumerAMessages.add(a2.get().getMessageId());
        if (b1.isPresent()) consumerBMessages.add(b1.get().getMessageId());
        if (b2.isPresent()) consumerBMessages.add(b2.get().getMessageId());

        // Verify no overlap between consumer A and consumer B
        Set<UUID> allDelivered = new HashSet<>(consumerAMessages);
        allDelivered.addAll(consumerBMessages);

        assertThat(consumerAMessages)
                .as("Consumer A and Consumer B should receive distinct messages")
                .doesNotContainAnyElementsOf(consumerBMessages);

        // All delivered messages should be from the enqueued set
        assertThat(allDelivered).isSubsetOf(new HashSet<>(enqueuedIds));
    }

    /**
     * Test: Three consumers each get one message from a 3-message queue.
     */
    @Test
    void threeConsumers_eachGetsOneMessage() {
        UUID ownerId = TEST_OWNER_ID;
        QueueDTO queue = queueService.createQueue(
                "three-consumer-queue", ownerId, 3, 5000L, 3, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        // Enqueue 3 messages
        Set<UUID> enqueuedIds = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> payload = Map.of("index", i);
            MessageMetadata meta = messageService.enqueueMessage(
                    queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                    ownerId, "zone", "ctx", "event", "Svc", payload);
            enqueuedIds.add(meta.getMessageId());
        }

        // Each consumer gets one message
        Set<UUID> deliveredIds = new HashSet<>();
        for (String consumer : List.of("conn-1", "conn-2", "conn-3")) {
            Optional<MessageMetadata> msg = deliveryService.deliverNextMessage(
                    queueId, consumer, "", DOMAIN_ID, SOLUTION_ID, ownerId);
            assertThat(msg).as("Consumer %s should receive a message", consumer).isPresent();
            deliveredIds.add(msg.get().getMessageId());
        }

        // All 3 messages should be distinct
        assertThat(deliveredIds).hasSize(3);
        assertThat(deliveredIds).isEqualTo(enqueuedIds);

        // No more messages available for a 4th consumer
        Optional<MessageMetadata> empty = deliveryService.deliverNextMessage(
                queueId, "conn-4", "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(empty).isEmpty();
    }

    /**
     * Test: PEL tracks messages per consumer independently.
     */
    @Test
    void pelTracksMessagesPerConsumerIndependently() {
        UUID ownerId = TEST_OWNER_ID;
        QueueDTO queue = queueService.createQueue(
                "pel-tracking-queue", ownerId, 3, 5000L, 2, DOMAIN_ID, SOLUTION_ID);
        UUID queueId = queue.id();

        String streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, ownerId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);

        // Enqueue 2 messages
        messageService.enqueueMessage(queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                ownerId, "zone", "ctx", "event", "Svc", Map.of("idx", 0));
        messageService.enqueueMessage(queueId, null, DOMAIN_ID, SOLUTION_ID, ownerId,
                ownerId, "zone", "ctx", "event", "Svc", Map.of("idx", 1));

        // Consumer A gets message 1
        Optional<MessageMetadata> a1 = deliveryService.deliverNextMessage(
                queueId, "consumer-A", "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(a1).isPresent();

        // Consumer B gets message 2
        Optional<MessageMetadata> b1 = deliveryService.deliverNextMessage(
                queueId, "consumer-B", "", DOMAIN_ID, SOLUTION_ID, ownerId);
        assertThat(b1).isPresent();

        // Verify each consumer's PEL contains only their message
        String a1StreamId = getFirstPendingStreamId(streamKey, groupName, "consumer-A");
        String b1StreamId = getFirstPendingStreamId(streamKey, groupName, "consumer-B");

        assertThat(a1StreamId).isNotNull();
        assertThat(b1StreamId).isNotNull();
        assertThat(a1StreamId).isNotEqualTo(b1StreamId);

        // Consumer A's message should be in A's PEL, not B's
        assertThat(streamMessageRepository.isInConsumerPel(streamKey, groupName, "consumer-A", a1StreamId)).isTrue();
        assertThat(streamMessageRepository.isInConsumerPel(streamKey, groupName, "consumer-B", a1StreamId)).isFalse();

        // Consumer B's message should be in B's PEL, not A's
        assertThat(streamMessageRepository.isInConsumerPel(streamKey, groupName, "consumer-B", b1StreamId)).isTrue();
        assertThat(streamMessageRepository.isInConsumerPel(streamKey, groupName, "consumer-A", b1StreamId)).isFalse();
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
