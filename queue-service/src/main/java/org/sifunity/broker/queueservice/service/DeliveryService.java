// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.MessageStatus;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.dto.PendingQueueMessageResponse;
import org.sifunity.broker.queueservice.metrics.QueueMetricsService;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.storage.FileSystemStorageProvider;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for delivering messages from queues to consumers using Redis Streams.
 *
 * <p>Implements the SIF §9.3.1 "Get Next and Pop" pattern:
 * <ol>
 *   <li>If deleteMessageId is non-empty: XACK + XDEL → ref count decrement → payload cleanup</li>
 *   <li>XCLAIM expired messages → move messages exceeding maxRetries to DLQ</li>
 *   <li>Check consumer PEL (readPending with offset 0) — re-deliver if pending message exists</li>
 *   <li>If no pending: XREADGROUP with {@code >} for next new message</li>
 *   <li>Retrieve payload from StorageProvider</li>
 *   <li>Update lastAccessed on Queue entity</li>
 * </ol>
 *
 * <p>Key SIF compliance: repeated queries without deleteMessageId return the SAME message.
 *
 * <p>Integrates with:
 * <ul>
 *   <li>{@link StreamMessageRepository} — all Redis Streams operations</li>
 *   <li>{@link RefCountService} — ref count decrement on acknowledge</li>
 *   <li>{@link StorageProvider} — payload retrieval and deletion</li>
 *   <li>{@link QueueRepository} — Queue entity updates (lastAccessed)</li>
 * </ul>
 *
 * @see StreamMessageRepository
 * @see RefCountService
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final QueueRepository queueRepository;
    private final StreamMessageRepository streamMessageRepository;
    private final RefCountService refCountService;
    private final StorageProvider storageProvider;
    private final TracingUtilsBase tracingUtils;
    private final ObjectMapper objectMapper;
    private final int visibilityTimeoutSeconds;
    private final QueueMetricsService queueMetricsService;

    /**
     * Constructs a DeliveryService with required dependencies.
     *
     * @param queueRepository          repository for queue operations
     * @param streamMessageRepository  Redis Streams message repository
     * @param refCountService          service for ref count decrement on acknowledge
     * @param storageProvider          provider for message payload storage
     * @param tracingUtils             utility for creating custom spans
     * @param objectMapper             Jackson ObjectMapper for JSON deserialization
     * @param visibilityTimeoutSeconds configured visibility timeout in seconds
     * @param queueMetricsService      service for recording queue metrics
     */
    public DeliveryService(QueueRepository queueRepository,
                           StreamMessageRepository streamMessageRepository,
                           RefCountService refCountService,
                           StorageProvider storageProvider,
                           TracingUtilsBase tracingUtils,
                           ObjectMapper objectMapper,
                           @Value("${queue.defaults.visibilityTimeoutSeconds:30}") int visibilityTimeoutSeconds,
                           QueueMetricsService queueMetricsService) {
        this.queueRepository = queueRepository;
        this.streamMessageRepository = streamMessageRepository;
        this.refCountService = refCountService;
        this.storageProvider = storageProvider;
        this.tracingUtils = tracingUtils;
        this.objectMapper = objectMapper;
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
        this.queueMetricsService = queueMetricsService;
    }

    /**
     * Delivers the next message from a queue using the SIF §9.3.1 "Get Next and Pop" pattern.
     *
     * <p>SIF semantics:
     * <ul>
     *   <li><b>Query (no deleteMessageId)</b>: Return the oldest undelivered message.
     *       Repeated calls return the SAME message until it is explicitly acknowledged.</li>
     *   <li><b>Query (with deleteMessageId)</b>: ACK+XDEL the specified message,
     *       then return the next undelivered message.</li>
     * </ul>
     *
     * <p>Implementation:
     * <ol>
     *   <li>If {@code deleteMessageId} is non-empty: XACK + XDEL → ref count decrement → payload cleanup</li>
     *   <li>XCLAIM expired PEL entries → move messages exceeding maxRetries to DLQ</li>
     *   <li>Check consumer's PEL for already-delivered-but-unacknowledged messages (readPending with offset 0).
     *       If found, re-deliver the same message (idempotent — no advancement).</li>
     *   <li>If PEL is empty, XREADGROUP with {@code >} for the next new message.</li>
     *   <li>Retrieve payload from StorageProvider</li>
     *   <li>Update lastAccessed on Queue entity</li>
     * </ol>
     *
     * @param queueId         the queue ID
     * @param consumerName    the consumer name (connectionId or X_DEFAULT_Q_CONSUMER)
     * @param deleteMessageId the stream ID of the message to acknowledge (empty for first call)
     * @param domainId        human-readable domain identifier
     * @param solutionId      human-readable solution identifier
     * @param environmentId   the environment UUID
     * @return Optional containing the delivered message metadata with payload, or empty if no messages
     * @throws QueueNotFoundException if the queue does not exist
     */
    @Timed(value = "sif.queue.dequeue.duration",
           description = "Time to dequeue and deliver a message",
           percentiles = {0.5, 0.95, 0.99})
    public Optional<MessageMetadata> deliverNextMessage(UUID queueId, String consumerName,
                                                         String deleteMessageId,
                                                         String domainId, String solutionId,
                                                         UUID environmentId) {
        log.debug("deliverNextMessage: queueId={}, consumer={}, deleteMessageId={}",
                queueId, consumerName, deleteMessageId == null || deleteMessageId.isEmpty() ? "(empty)" : deleteMessageId);

        // Validate queue exists
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> {
                    log.error("Queue not found: queueId={}", queueId);
                    return new QueueNotFoundException("Queue not found: " + queueId);
                });

        String streamKey = streamMessageRepository.resolveStreamKey(domainId, solutionId, environmentId, queueId);
        String dlqKey = streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, environmentId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);

        Map<String, String> spanAttributes = new HashMap<>();
        spanAttributes.put("queue.id", queueId.toString());
        spanAttributes.put("queue.name", queue.getName());
        spanAttributes.put("consumer.name", consumerName);

        return tracingUtils.traceOperation("queue.deliver_next_message", spanAttributes, () -> {

            // Step 1: Acknowledge previous message if deleteMessageId is provided
            if (deleteMessageId != null && !deleteMessageId.isEmpty()) {
                acknowledgeMessage(streamKey, dlqKey, groupName, consumerName,
                        deleteMessageId, domainId, solutionId, queue);
            }

            // Step 2: Claim expired PEL entries and escalate to DLQ if needed
            claimAndEscalate(streamKey, dlqKey, groupName, consumerName, queue);

            // Step 3: Check PEL first — if consumer has pending (delivered but unacked) messages,
            // re-deliver the oldest one. This ensures repeated calls without deleteMessageId
            // return the SAME message per SIF §9.3.1.
            Optional<MapRecord<String, Object, Object>> record =
                    streamMessageRepository.readPending(streamKey, groupName, consumerName);

            if (record.isPresent()) {
                log.debug("Re-delivering pending message from PEL: queueId={}, consumer={}", queueId, consumerName);
            } else {
                // Step 4: No pending messages — read next NEW message via XREADGROUP >
                record = streamMessageRepository.readNext(streamKey, groupName, consumerName);
            }

            if (record.isEmpty()) {
                log.debug("No messages available: queueId={}, consumer={}", queueId, consumerName);
                return Optional.empty();
            }

            MapRecord<String, Object, Object> entry = record.get();
            Map<Object, Object> fields = entry.getValue();

            String messageIdStr = fieldAsString(fields, StreamMessageRepository.FIELD_MESSAGE_ID);
            String storageKey = fieldAsString(fields, StreamMessageRepository.FIELD_STORAGE_KEY);
            String enqueuedAtStr = fieldAsString(fields, StreamMessageRepository.FIELD_ENQUEUED_AT);

            UUID messageId = UUID.fromString(messageIdStr);
            Instant enqueuedAt = Instant.parse(enqueuedAtStr);

            // Extract SIF headers from stream entry
            String headersJson = fieldAsString(fields, "sifHeaders");
            Map<String, String> sifHeaders = null;
            if (headersJson != null && !headersJson.isEmpty()) {
                try {
                    sifHeaders = objectMapper.readValue(
                            headersJson, new TypeReference<Map<String, String>>() {});
                } catch (Exception e) {
                    log.warn("Failed to deserialize SIF headers: {}", e.getMessage());
                }
            }

            // Step 5: Retrieve payload from StorageProvider
            Object payload;
            try {
                payload = storageProvider.retrieve(storageKey).orElse(null);
                if (payload == null) {
                    log.error("Payload not found in storage (skipping): messageId={}, storageKey={}",
                            messageId, storageKey);
                    return Optional.empty();
                }
            } catch (Exception e) {
                log.error("Failed to retrieve payload (skipping): messageId={}, storageKey={}, error={}",
                        messageId, storageKey, e.getMessage(), e);
                return Optional.empty();
            }

            // Step 6: Update lastAccessed on Queue entity
            updateLastAccessed(queue);

            MessageMetadata metadata = MessageMetadata.builder()
                    .messageId(messageId)
                    .queueId(queueId)
                    .storageKey(storageKey)
                    .status(MessageStatus.DELIVERED)
                    .enqueuedAt(enqueuedAt)
                    .deliveredAt(Instant.now())
                    .deliveryAttempts(1)
                    .payload(payload)
                    .streamId(entry.getId().getValue())
                    .sifHeaders(sifHeaders)
                    .build();

            // Record dequeue and delivery latency metrics
            try {
                // Derive messageType from SIF headers if available
                String messageType = sifHeaders != null ? sifHeaders.get("messageType") : null;
                queueMetricsService.recordDequeue(queue.getName(), messageType);

                // Compute delivery latency from enqueue timestamp
                if (enqueuedAt != null && metadata.getDeliveredAt() != null) {
                    long latencyMs = Duration.between(enqueuedAt, metadata.getDeliveredAt()).toMillis();
                    queueMetricsService.recordDeliveryLatency(queue.getName(), latencyMs);
                }
            } catch (Exception e) {
                log.warn("Failed to record delivery metrics: {}", e.getMessage());
            }

            log.info("Delivered message: queueId={}, messageId={}, consumer={}, streamId={}",
                    queueId, messageId, consumerName, entry.getId().getValue());

            return Optional.of(metadata);
        });
    }

    // ── Acknowledge (Step 1) ────────────────────────────────────────────

    /**
     * Acknowledges a previously delivered message: XACK + XDEL + ref count decrement + payload cleanup.
     *
     * @param streamKey       the Redis stream key
     * @param dlqKey          the DLQ stream key (unused here but kept for consistency)
     * @param groupName       the consumer group name
     * @param consumerName    the consumer name
     * @param streamId        the stream entry ID to acknowledge
     * @param domainId        human-readable domain identifier
     * @param solutionId      human-readable solution identifier
     * @param queue           the Queue entity (for configuration)
     */
    private void acknowledgeMessage(String streamKey, String dlqKey, String groupName,
                                    String consumerName, String streamId,
                                    String domainId, String solutionId, Queue queue) {
        log.debug("Acknowledging message: streamKey={}, streamId={}, consumer={}", streamKey, streamId, consumerName);

        // PEL validation is done in the controller layer before calling this method.
        // Here we proceed with XACK + XDEL.

        // Look up the stream entry to get storageKey before deleting
        // We need the storageKey for ref count decrement and payload cleanup.
        // Since the message is in the consumer's PEL, we can read it by ID.
        String storageKey = getStorageKeyFromPel(streamKey, streamId);

        // XACK + XDEL
        boolean acked = streamMessageRepository.acknowledgeAndDelete(streamKey, groupName, consumerName, streamId);
        if (!acked) {
            log.warn("XACK returned 0 (message may not have been in PEL): streamId={}", streamId);
        }

        // Ref count decrement + conditional payload cleanup
        if (storageKey != null) {
            cleanupPayload(storageKey, domainId, solutionId);
        } else {
            log.warn("Could not determine storageKey for acknowledged message: streamId={}", streamId);
        }
    }

    /**
     * Retrieves the storageKey from a stream entry by reading it via XRANGE before deletion.
     */
    private String getStorageKeyFromPel(String streamKey, String streamId) {
        try {
            var records = streamMessageRepository.readRange(streamKey, streamId, streamId);
            if (records != null && !records.isEmpty()) {
                Map<Object, Object> fields = records.get(0).getValue();
                return fieldAsString(fields, StreamMessageRepository.FIELD_STORAGE_KEY);
            }
        } catch (Exception e) {
            log.warn("Failed to read storageKey from stream entry: streamId={}, error={}", streamId, e.getMessage());
        }
        return null;
    }

    // ── Payload Cleanup (Step 1b) ───────────────────────────────────────

    /**
     * Decrements the ref count for a storage key and deletes the payload if the count reaches zero.
     *
     * <p>Payload cleanup behavior:
     * <ul>
     *   <li>FileSystemStorageProvider: synchronous delete (fast, non-blocking)</li>
     *   <li>S3StorageProvider: asynchronous delete via CompletableFuture (fire-and-forget)</li>
     * </ul>
     *
     * <p>Cleanup is best-effort: failures are logged as warnings but do not fail the acknowledge.
     *
     * @param storageKey the storage key of the payload
     * @param domainId   human-readable domain identifier
     * @param solutionId human-readable solution identifier
     */
    private void cleanupPayload(String storageKey, String domainId, String solutionId) {
        try {
            String refKey = refCountService.refCountKey(domainId, solutionId, storageKey);
            long newCount = refCountService.decrementAndGet(refKey);

            if (newCount <= 0) {
                log.debug("Ref count reached zero, deleting payload: storageKey={}", storageKey);
                deletePayload(storageKey);
            } else {
                log.debug("Ref count still positive ({}), retaining payload: storageKey={}", newCount, storageKey);
            }
        } catch (Exception e) {
            log.warn("Payload cleanup failed (best-effort): storageKey={}, error={}", storageKey, e.getMessage(), e);
        }
    }

    /**
     * Deletes the payload from the storage provider.
     * Sync for filesystem, async for S3 — the StorageProvider implementations handle this internally.
     * Best-effort: failures are logged as warnings.
     */
    private void deletePayload(String storageKey) {
        if (storageProvider instanceof FileSystemStorageProvider) {
            // Filesystem: synchronous delete
            try {
                storageProvider.delete(storageKey);
                log.debug("Payload deleted (sync/filesystem): storageKey={}", storageKey);
            } catch (Exception e) {
                log.warn("Failed to delete payload from filesystem (best-effort): storageKey={}, error={}",
                        storageKey, e.getMessage(), e);
            }
        } else {
            // S3 or other: the S3StorageProvider.delete() already runs async via CompletableFuture.
            // We still wrap in try-catch for safety.
            try {
                storageProvider.delete(storageKey);
                log.debug("Payload delete initiated (async/S3): storageKey={}", storageKey);
            } catch (Exception e) {
                log.warn("Failed to initiate payload deletion (best-effort): storageKey={}, error={}",
                        storageKey, e.getMessage(), e);
            }
        }
    }

    // ── Claim & Escalate (Step 2) ───────────────────────────────────────

    /**
     * Claims expired PEL entries via XCLAIM and moves messages exceeding maxRetries to DLQ.
     */
    private void claimAndEscalate(String streamKey, String dlqKey, String groupName,
                                  String consumerName, Queue queue) {
        Duration visibilityTimeout = Duration.ofSeconds(visibilityTimeoutSeconds);
        int maxClaimCount = 10;

        List<MapRecord<String, Object, Object>> claimed =
                streamMessageRepository.claimExpiredMessages(streamKey, groupName, consumerName,
                        visibilityTimeout, maxClaimCount);

        if (claimed.isEmpty()) {
            return;
        }

        log.debug("Claimed {} expired messages for consumer={}", claimed.size(), consumerName);

        for (MapRecord<String, Object, Object> entry : claimed) {
            String entryStreamId = entry.getId().getValue();
            Map<Object, Object> fields = entry.getValue();

            // Skip empty records (stream entry was XDEL'd but still in PEL)
            if (fields == null || fields.isEmpty()) {
                log.debug("Skipping empty claimed entry (deleted from stream): streamId={}", entryStreamId);
                // XACK to remove from PEL
                streamMessageRepository.acknowledgeAndDelete(streamKey, groupName, consumerName, entryStreamId);
                continue;
            }

            String messageIdStr = fieldAsString(fields, StreamMessageRepository.FIELD_MESSAGE_ID);
            String storageKey = fieldAsString(fields, StreamMessageRepository.FIELD_STORAGE_KEY);

            // Skip if messageId is missing (corrupted entry)
            if (messageIdStr == null) {
                log.warn("Claimed entry has no messageId field, removing from PEL: streamId={}", entryStreamId);
                streamMessageRepository.acknowledgeAndDelete(streamKey, groupName, consumerName, entryStreamId);
                continue;
            }

            // Check delivery count from PEL (XCLAIM increments it)
            // We use XPENDING to get the delivery count for this specific entry
            long deliveryCount = getDeliveryCount(streamKey, groupName, entryStreamId);

            if (deliveryCount > queue.getMaxRetries()) {
                log.info("Message exceeded maxRetries ({}), moving to DLQ: messageId={}, deliveryCount={}",
                        queue.getMaxRetries(), messageIdStr, deliveryCount);

                UUID messageId = messageIdStr != null ? UUID.fromString(messageIdStr) : UUID.randomUUID();
                streamMessageRepository.moveToDeadLetter(
                        streamKey, dlqKey, groupName, entryStreamId,
                        messageId, storageKey != null ? storageKey : "", null);

                // Record DLQ metric
                try {
                    queueMetricsService.recordDlqMessage(queue.getName());
                } catch (Exception e) {
                    log.warn("Failed to record DLQ metric: {}", e.getMessage());
                }
            }
            // Otherwise, the claimed message is now assigned to this consumer and will be
            // delivered via XREADGROUP on the next call (it's in the consumer's PEL with ">").
            // Actually, XCLAIM messages are already in the PEL — they won't be returned by
            // XREADGROUP with ">". The consumer needs to read pending entries.
            // For simplicity, we leave them in the PEL; the next XREADGROUP with ">" gets new messages.
            // Claimed messages will be re-read if the consumer reads its pending list.
        }
    }

    /**
     * Gets the delivery count for a specific stream entry from the PEL.
     */
    private long getDeliveryCount(String streamKey, String groupName, String streamId) {
        try {
            var pendingMessages = streamMessageRepository.getPendingMessagesForGroup(streamKey, groupName, 100);
            if (pendingMessages != null) {
                for (var msg : pendingMessages) {
                    if (msg.getIdAsString().equals(streamId)) {
                        return msg.getTotalDeliveryCount();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get delivery count for streamId={}: {}", streamId, e.getMessage());
        }
        return 0;
    }

    // ── Update lastAccessed (Step 5) ────────────────────────────────────

    /**
     * Updates the lastAccessed timestamp on the Queue entity after successful delivery.
     */
    private void updateLastAccessed(Queue queue) {
        try {
            queue.setLastAccessed(Instant.now());
            queueRepository.save(queue);
            log.debug("Updated lastAccessed for queue: queueId={}", queue.getId());
        } catch (Exception e) {
            log.warn("Failed to update lastAccessed for queue: queueId={}, error={}",
                    queue.getId(), e.getMessage(), e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String fieldAsString(Map<Object, Object> fields, Object key) {
        Object val = fields.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * @deprecated Use {@link #deliverNextMessage} instead. This bridge method exists
     * for backward compatibility until the MessageController is updated (Task 10).
     */
    @Deprecated
    public List<MessageMetadata> deliverMessages(UUID queueId, int maxMessages, int visibilityTimeoutSeconds) {
        log.warn("deliverMessages() is deprecated — use deliverNextMessage() instead");
        return java.util.Collections.emptyList();
    }

    /**
     * Lists messages currently reserved in the queue consumer group's PEL,
     * independent of which consumer connection owns each message.
     */
    public List<PendingQueueMessageResponse> getPendingMessages(
            UUID queueId, String domainId, String solutionId,
            UUID environmentId, int limit) {
        String streamKey = streamMessageRepository.resolveStreamKey(
                domainId, solutionId, environmentId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);
        var pendingMessages = streamMessageRepository.getPendingMessagesForGroup(
                streamKey, groupName, limit);

        if (pendingMessages == null || pendingMessages.size() == 0) {
            return List.of();
        }

        List<PendingQueueMessageResponse> results = new ArrayList<>();
        for (var pending : pendingMessages) {
            var records = streamMessageRepository.readRange(
                    streamKey, pending.getIdAsString(), pending.getIdAsString());
            if (records.isEmpty()) {
                continue;
            }

            String messageId = fieldAsString(
                    records.get(0).getValue(), StreamMessageRepository.FIELD_MESSAGE_ID);
            if (messageId == null || messageId.isBlank()) {
                continue;
            }

            Duration idleTime = pending.getElapsedTimeSinceLastDelivery();
            results.add(new PendingQueueMessageResponse(
                    messageId,
                    pending.getIdAsString(),
                    pending.getConsumerName(),
                    idleTime != null ? idleTime.toMillis() : 0L,
                    pending.getTotalDeliveryCount()));
        }
        return results;
    }

    /**
     * Removes the globally oldest valid pending entry, regardless of consumer.
     * Returns empty when the queue has no reserved/in-process messages.
     */
    public Optional<PendingQueueMessageResponse> discardOldestPendingMessage(
            UUID queueId, String domainId, String solutionId, UUID environmentId) {
        for (PendingQueueMessageResponse pending :
                getPendingMessages(queueId, domainId, solutionId, environmentId, 1000)) {
            if (deleteMessage(queueId, pending.messageId(), domainId, solutionId, environmentId)) {
                return Optional.of(pending);
            }
        }
        return Optional.empty();
    }

    /**
     * Deletes a specific message from a queue by its SIF-level messageId (UUID).
     * Scans the entire stream for the entry, performs XACK + XDEL, decrements ref count,
     * and deletes payload if ref count reaches zero.
     *
     * @param queueId       the queue ID
     * @param messageId     the SIF-level message UUID string
     * @param domainId      human-readable domain identifier
     * @param solutionId    human-readable solution identifier
     * @param environmentId the environment UUID (queue owner)
     * @return true if the message was found and deleted, false if not found
     */
    public boolean deleteMessage(UUID queueId, String messageId, String domainId,
                                 String solutionId, UUID environmentId) {
        String streamKey = streamMessageRepository.resolveStreamKey(domainId, solutionId, environmentId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);

        // Scan stream for entry with matching messageId field
        var entry = streamMessageRepository.findEntryByMessageId(streamKey, messageId);
        if (entry.isEmpty()) {
            log.debug("deleteMessage: messageId={} not found in stream={}", messageId, streamKey);
            return false;
        }

        var record = entry.get();
        String streamId = record.getId().getValue();
        String storageKey = fieldAsString(record.getValue(), StreamMessageRepository.FIELD_STORAGE_KEY);

        // XACK (in case it was pending) + XDEL
        streamMessageRepository.acknowledgeAndDelete(streamKey, groupName, "deleteMessage", streamId);

        // Ref count decrement + conditional payload cleanup
        if (storageKey != null) {
            cleanupPayload(storageKey, domainId, solutionId);
        }

        log.info("Deleted message: queueId={}, messageId={}, streamId={}", queueId, messageId, streamId);
        return true;
    }

    /**
     * Checks whether a specific stream ID is in a consumer's PEL.
     * Exposed for use by the controller layer for PEL validation.
     *
     * @param domainId      human-readable domain identifier
     * @param solutionId    human-readable solution identifier
     * @param environmentId the environment UUID
     * @param queueId       the queue ID
     * @param consumerName  the consumer name
     * @param streamId      the stream entry ID to check
     * @return true if the message is in the consumer's PEL
     */
    public boolean isInConsumerPel(String domainId, String solutionId, UUID environmentId,
                                   UUID queueId, String consumerName, String streamId) {
        String streamKey = streamMessageRepository.resolveStreamKey(domainId, solutionId, environmentId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);
        return streamMessageRepository.isInConsumerPel(streamKey, groupName, consumerName, streamId);
    }

    /**
     * Resolves a messageId UUID to the corresponding Redis stream entry ID
     * by scanning the consumer's PEL. Returns null if not found.
     *
     * <p>Per SIF §9.3.1, the consumer passes back the messageId (UUID) as the
     * deleteMessageId. Internally we need the Redis stream entry ID for XACK/XDEL.
     *
     * @param domainId      human-readable domain identifier
     * @param solutionId    human-readable solution identifier
     * @param environmentId the environment UUID
     * @param queueId       the queue ID
     * @param consumerName  the consumer name
     * @param messageId     the message UUID to look up
     * @return the Redis stream entry ID, or null if not found in PEL
     */
    public String resolveStreamIdFromMessageId(String domainId, String solutionId, UUID environmentId,
                                                UUID queueId, String consumerName, String messageId) {
        String streamKey = streamMessageRepository.resolveStreamKey(domainId, solutionId, environmentId, queueId);
        String groupName = streamMessageRepository.groupName(queueId);
        return streamMessageRepository.findStreamIdByMessageId(streamKey, groupName, consumerName, messageId);
    }
}
