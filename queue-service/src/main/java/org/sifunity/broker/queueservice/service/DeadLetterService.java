// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.service;

import org.sifunity.broker.common.exception.MessageNotFoundException;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.MessageStatus;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing dead-letter queue operations using Redis Streams.
 *
 * <p>The DLQ is a separate Redis Stream (key suffix {@code :dlq}) rather than a sorted set.
 * This service handles:
 * <ul>
 *   <li>Moving failed messages to the DLQ stream (XADD to DLQ, XACK + XDEL on original)</li>
 *   <li>Querying messages in the DLQ stream (XRANGE)</li>
 *   <li>Reprocessing messages from the DLQ (XADD back to original, XDEL from DLQ)</li>
 * </ul>
 *
 * @see StreamMessageRepository
 */
@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final QueueRepository queueRepository;
    private final StreamMessageRepository streamMessageRepository;
    private final StorageProvider storageProvider;

    public DeadLetterService(QueueRepository queueRepository,
                             StreamMessageRepository streamMessageRepository,
                             StorageProvider storageProvider) {
        this.queueRepository = queueRepository;
        this.streamMessageRepository = streamMessageRepository;
        this.storageProvider = storageProvider;
    }

    /**
     * Moves a message to the dead-letter stream after max retries exceeded.
     *
     * <p>Performs: XADD to DLQ stream, then XACK + XDEL on the original stream.
     *
     * @param streamKey  the original queue's stream key
     * @param dlqKey     the DLQ stream key
     * @param groupName  the consumer group name
     * @param streamId   the stream entry ID of the message to move
     * @param messageId  the message UUID
     * @param storageKey the storage key for the message payload
     */
    public void moveToDeadLetter(String streamKey, String dlqKey, String groupName,
                                 String streamId, UUID messageId, String storageKey) {
        log.debug("Moving message to DLQ stream: messageId={}, streamId={}", messageId, streamId);

        try {
            streamMessageRepository.moveToDeadLetter(
                    streamKey, dlqKey, groupName, streamId, messageId, storageKey, null);
            log.info("Moved message to DLQ: messageId={}, dlqKey={}", messageId, dlqKey);
        } catch (Exception e) {
            log.error("Failed to move message to DLQ: messageId={}, error={}", messageId, e.getMessage(), e);
            throw new RuntimeException("Failed to move message to dead-letter stream: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves messages from the dead-letter stream and their payloads.
     *
     * <p>Reads entries from the DLQ stream via XRANGE and retrieves each payload
     * from the StorageProvider.
     *
     * @param queueId       the queue ID (used to look up domain/solution/environment context)
     * @param domainId      human-readable domain identifier
     * @param solutionId    human-readable solution identifier
     * @param environmentId the environment UUID
     * @return list of message metadata with payloads
     */
    public List<MessageMetadata> getDeadLetterMessages(UUID queueId,
                                                        String domainId, String solutionId,
                                                        UUID environmentId) {
        log.debug("Retrieving DLQ messages: queueId={}", queueId);

        String dlqKey = streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, environmentId, queueId);

        List<MapRecord<String, Object, Object>> records =
                streamMessageRepository.readDeadLetterMessages(dlqKey, 0, 1000);

        log.debug("Found {} entries in DLQ stream", records.size());

        List<MessageMetadata> result = new ArrayList<>();
        for (MapRecord<String, Object, Object> record : records) {
            try {
                Map<Object, Object> fields = record.getValue();
                String messageIdStr = fieldAsString(fields, StreamMessageRepository.FIELD_MESSAGE_ID);
                String storageKey = fieldAsString(fields, StreamMessageRepository.FIELD_STORAGE_KEY);
                String enqueuedAtStr = fieldAsString(fields, StreamMessageRepository.FIELD_ENQUEUED_AT);

                UUID messageId = UUID.fromString(messageIdStr);
                Instant enqueuedAt = enqueuedAtStr != null ? Instant.parse(enqueuedAtStr) : null;

                // Retrieve payload from storage
                Object payload = null;
                if (storageKey != null) {
                    try {
                        payload = storageProvider.retrieve(storageKey).orElse(null);
                    } catch (Exception e) {
                        log.warn("Failed to retrieve DLQ payload: messageId={}, storageKey={}, error={}",
                                messageId, storageKey, e.getMessage());
                    }
                }

                MessageMetadata metadata = MessageMetadata.builder()
                        .messageId(messageId)
                        .queueId(queueId)
                        .storageKey(storageKey)
                        .status(MessageStatus.FAILED)
                        .enqueuedAt(enqueuedAt)
                        .payload(payload)
                        .build();

                result.add(metadata);
            } catch (Exception e) {
                log.error("Failed to parse DLQ entry: streamId={}, error={}",
                        record.getId().getValue(), e.getMessage(), e);
            }
        }

        log.info("Retrieved {} DLQ messages for queue: {}", result.size(), queueId);
        return result;
    }

    /**
     * Reprocesses a message from the DLQ back to the original queue stream.
     *
     * <p>Performs: XADD back to the original stream, then XDEL from the DLQ stream.
     *
     * @param queueId       the original queue ID
     * @param dlqStreamId   the stream entry ID in the DLQ to reprocess
     * @param messageId     the message UUID
     * @param storageKey    the storage key for the message payload
     * @param domainId      human-readable domain identifier
     * @param solutionId    human-readable solution identifier
     * @param environmentId the environment UUID
     */
    public void reprocessMessage(UUID queueId, String dlqStreamId, UUID messageId,
                                 String storageKey,
                                 String domainId, String solutionId, UUID environmentId) {
        log.debug("Reprocessing DLQ message: messageId={}, dlqStreamId={}", messageId, dlqStreamId);

        String streamKey = streamMessageRepository.streamKey(domainId, solutionId, environmentId, queueId);
        String dlqKey = streamMessageRepository.dlqStreamKey(domainId, solutionId, environmentId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);

        streamMessageRepository.reprocessFromDeadLetter(
                dlqKey, streamKey, groupName, dlqStreamId, messageId, storageKey, null);

        log.info("Reprocessed message from DLQ: messageId={}, queueId={}", messageId, queueId);
    }

    private String fieldAsString(Map<Object, Object> fields, Object key) {
        Object val = fields.get(key);
        return val != null ? val.toString() : null;
    }
}
