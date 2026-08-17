// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Redis Streams-based message repository replacing the sorted-set/hash implementation.
 * Uses XADD, XREADGROUP, XACK, XDEL, XCLAIM, XPENDING, XLEN, and XGROUP commands.
 *
 * <p>Stream entry fields per message:
 * <ul>
 *   <li>{@code messageId} — UUID string</li>
 *   <li>{@code storageKey} — payload location in S3/filesystem</li>
 *   <li>{@code enqueuedAt} — ISO-8601 timestamp</li>
 * </ul>
 */
@Repository
public class StreamMessageRepository {

    private static final Logger log = LoggerFactory.getLogger(StreamMessageRepository.class);

    public static final String FIELD_MESSAGE_ID = "messageId";
    public static final String FIELD_STORAGE_KEY = "storageKey";
    public static final String FIELD_ENQUEUED_AT = "enqueuedAt";

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyManager keyManager;

    public StreamMessageRepository(StringRedisTemplate redisTemplate, RedisKeyManager keyManager) {
        this.redisTemplate = redisTemplate;
        this.keyManager = keyManager;
    }

    // ── Key generation ──────────────────────────────────────────────────

    /**
     * Build the main stream key for a queue (new format).
     * Format: {@code domain:{domainId}:solution:{solutionId}:function:msg_queue:env:{environmentId}:queue:{queueId}}
     */
    public String streamKey(String domainId, String solutionId, UUID environmentId, UUID queueId) {
        return keyManager.streamKey(domainId, solutionId, environmentId, queueId);
    }

    /**
     * Build the dead-letter stream key for a queue (main key + {@code :dlq}).
     */
    public String dlqStreamKey(String domainId, String solutionId, UUID environmentId, UUID queueId) {
        return keyManager.dlqStreamKey(domainId, solutionId, environmentId, queueId);
    }

    /**
     * Consumer group name for a queue: {@code group:{queueId}}.
     */
    public String groupName(UUID queueId) {
        return keyManager.groupName(queueId);
    }

    /**
     * Resolve the stream key for reads during migration period.
     * Tries new-format key first; if it doesn't exist, falls back to legacy key.
     * Defaults to new format for creation if neither exists.
     */
    public String resolveStreamKey(String domainId, String solutionId, UUID environmentId, UUID queueId) {
        String newKey = keyManager.streamKey(domainId, solutionId, environmentId, queueId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(newKey))) {
            return newKey;
        }
        String legacyKey = keyManager.legacyStreamKey(domainId, solutionId, environmentId, queueId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(legacyKey))) {
            return legacyKey;
        }
        return newKey; // default to new format for creation
    }

    /**
     * Resolve the DLQ stream key for reads during migration period.
     * Tries new-format key first; if it doesn't exist, falls back to legacy key + :dlq.
     */
    public String resolveDlqStreamKey(String domainId, String solutionId, UUID environmentId, UUID queueId) {
        String newKey = keyManager.dlqStreamKey(domainId, solutionId, environmentId, queueId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(newKey))) {
            return newKey;
        }
        String legacyKey = keyManager.legacyStreamKey(domainId, solutionId, environmentId, queueId) + ":dlq";
        if (Boolean.TRUE.equals(redisTemplate.hasKey(legacyKey))) {
            return legacyKey;
        }
        return newKey; // default to new format
    }

    // ── Enqueue ─────────────────────────────────────────────────────────

    /**
     * Append a message to the stream via XADD and ensure the consumer group exists.
     *
     * @return the Redis-generated stream entry ID (e.g. "1234567890-0")
     */
    public String enqueue(String streamKey, String groupName, UUID messageId,
                          String storageKey, Instant enqueuedAt) {
        return enqueue(streamKey, groupName, messageId, storageKey, enqueuedAt, null);
    }

    public String enqueue(String streamKey, String groupName, UUID messageId,
                          String storageKey, Instant enqueuedAt, String headersJson) {
        ensureConsumerGroup(streamKey, groupName);

        Map<String, String> fields = new java.util.HashMap<>();
        fields.put(FIELD_MESSAGE_ID, messageId.toString());
        fields.put(FIELD_STORAGE_KEY, storageKey);
        fields.put(FIELD_ENQUEUED_AT, enqueuedAt.toString());
        if (headersJson != null) {
            fields.put("sifHeaders", headersJson);
        }

        StringRecord record = StreamRecords.string(fields).withStreamKey(streamKey);
        RecordId recordId = redisTemplate.opsForStream().add(record);

        log.debug("Enqueued message: streamKey={}, messageId={}, streamId={}",
                streamKey, messageId, recordId);
        return recordId != null ? recordId.getValue() : null;
    }

    // ── Deliver (XREADGROUP) ────────────────────────────────────────────

    /**
     * Read the next NEW (undelivered) message for a consumer via XREADGROUP with offset {@code >}.
     * On NOGROUP error, auto-creates the group and retries once.
     *
     * @return the stream record, or empty if no new messages available
     */
    public Optional<MapRecord<String, Object, Object>> readNext(String streamKey,
                                                                 String groupName,
                                                                 String consumerName) {
        try {
            return doReadNext(streamKey, groupName, consumerName);
        } catch (RedisSystemException ex) {
            if (isNoGroupError(ex)) {
                log.info("Consumer group not found, creating and retrying: streamKey={}, group={}",
                        streamKey, groupName);
                ensureConsumerGroup(streamKey, groupName);
                return doReadNext(streamKey, groupName, consumerName);
            }
            throw ex;
        }
    }

    /**
     * Read the oldest PENDING (already delivered but unacknowledged) message for a consumer
     * via XREADGROUP with offset {@code 0}. This re-delivers the same message without advancing.
     *
     * <p>Per SIF §9.3.1: repeated queries without deleteMessageId must return the same message.
     *
     * @return the oldest pending stream record, or empty if no pending messages
     */
    public Optional<MapRecord<String, Object, Object>> readPending(String streamKey,
                                                                    String groupName,
                                                                    String consumerName) {
        try {
            return doReadPending(streamKey, groupName, consumerName);
        } catch (RedisSystemException ex) {
            if (isNoGroupError(ex)) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<MapRecord<String, Object, Object>> doReadNext(String streamKey,
                                                                    String groupName,
                                                                    String consumerName) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(groupName, consumerName),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed())
        );

        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    @SuppressWarnings("unchecked")
    private Optional<MapRecord<String, Object, Object>> doReadPending(String streamKey,
                                                                       String groupName,
                                                                       String consumerName) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(groupName, consumerName),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(streamKey, ReadOffset.from("0"))
        );

        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    // ── Acknowledge + Delete ────────────────────────────────────────────

    /**
     * Acknowledge a message without deleting it (XACK only).
     * Used by purge to clear the PEL before XDEL.
     */
    public long acknowledge(String streamKey, String groupName, String streamId) {
        Long acknowledged = redisTemplate.opsForStream().acknowledge(streamKey, groupName, streamId);
        return acknowledged != null ? acknowledged : 0L;
    }

    /**
     * Acknowledge and delete a message from the stream (XACK + XDEL).
     *
     * @return true if the message was acknowledged
     */
    public boolean acknowledgeAndDelete(String streamKey, String groupName,
                                        String consumerName, String streamId) {
        Long acked = redisTemplate.opsForStream().acknowledge(streamKey, groupName, streamId);
        redisTemplate.opsForStream().delete(streamKey, streamId);

        boolean wasAcked = acked != null && acked > 0;
        log.debug("Acknowledge+delete: streamKey={}, streamId={}, acked={}",
                streamKey, streamId, wasAcked);
        return wasAcked;
    }

    // ── PEL inspection ──────────────────────────────────────────────────

    /**
     * Check whether a specific stream ID is in a consumer's PEL.
     * Uses XPENDING with consumer filter.
     */
    public boolean isInConsumerPel(String streamKey, String groupName,
                                   String consumerName, String streamId) {
        try {
            PendingMessages pendingMessages = redisTemplate.opsForStream()
                    .pending(streamKey, Consumer.from(groupName, consumerName),
                            Range.unbounded(), -1L);

            if (pendingMessages == null) {
                return false;
            }

            for (PendingMessage msg : pendingMessages) {
                if (msg.getIdAsString().equals(streamId)) {
                    return true;
                }
            }
            return false;
        } catch (RedisSystemException ex) {
            if (isNoGroupError(ex)) {
                return false;
            }
            throw ex;
        }
    }

    /**
     * Find the stream entry ID for a message with the given messageId UUID in a consumer's PEL.
     * Reads pending entries via XREADGROUP with offset 0, then checks the messageId field.
     *
     * @return the stream entry ID if found, or null if no matching pending message exists
     */
    public String findStreamIdByMessageId(String streamKey, String groupName,
                                          String consumerName, String messageId) {
        try {
            // Read ALL pending entries for this consumer (up to a reasonable limit)
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(groupName, consumerName),
                    StreamReadOptions.empty().count(100),
                    StreamOffset.create(streamKey, ReadOffset.from("0"))
            );

            if (records == null || records.isEmpty()) {
                return null;
            }

            for (MapRecord<String, Object, Object> record : records) {
                Object msgIdField = record.getValue().get(FIELD_MESSAGE_ID);
                if (msgIdField != null && msgIdField.toString().equals(messageId)) {
                    return record.getId().getValue();
                }
            }
            return null;
        } catch (RedisSystemException ex) {
            if (isNoGroupError(ex)) {
                return null;
            }
            throw ex;
        }
    }

    // ── Claim expired messages ──────────────────────────────────────────

    /**
     * Claim messages that have been idle longer than the visibility timeout.
     * Uses XPENDING to find idle entries, then XCLAIM to reassign them.
     *
     * @return list of claimed stream records
     */
    public List<MapRecord<String, Object, Object>> claimExpiredMessages(
            String streamKey, String groupName, String consumerName,
            Duration visibilityTimeout, int maxCount) {
        try {
            PendingMessages pendingMessages = redisTemplate.opsForStream()
                    .pending(streamKey, groupName, Range.unbounded(), maxCount);

            if (pendingMessages == null || pendingMessages.size() == 0) {
                return Collections.emptyList();
            }

            long idleThresholdMs = visibilityTimeout.toMillis();
            List<RecordId> expiredIds = new ArrayList<>();

            for (PendingMessage entry : pendingMessages) {
                Duration elapsed = entry.getElapsedTimeSinceLastDelivery();
                if (elapsed != null && elapsed.toMillis() >= idleThresholdMs) {
                    expiredIds.add(RecordId.of(entry.getIdAsString()));
                }
            }

            if (expiredIds.isEmpty()) {
                return Collections.emptyList();
            }

            // XCLAIM the expired messages
            List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                    streamKey, groupName, consumerName,
                    visibilityTimeout,
                    expiredIds.toArray(new RecordId[0])
            );

            log.debug("Claimed {} expired messages: streamKey={}, consumer={}",
                    claimed != null ? claimed.size() : 0, streamKey, consumerName);
            return claimed != null ? claimed : Collections.emptyList();
        } catch (RedisSystemException ex) {
            if (isNoGroupError(ex)) {
                return Collections.emptyList();
            }
            throw ex;
        }
    }

    // ── Dead-letter operations ──────────────────────────────────────────

    /**
     * Move a message to the dead-letter stream:
     * XADD to DLQ, then XACK + XDEL on the original stream.
     */
    public void moveToDeadLetter(String streamKey, String dlqKey, String groupName,
                                 String streamId, UUID messageId, String storageKey,
                                 Map<String, String> metadata) {
        Map<String, String> dlqFields = new LinkedHashMap<>();
        dlqFields.put(FIELD_MESSAGE_ID, messageId.toString());
        dlqFields.put(FIELD_STORAGE_KEY, storageKey);
        dlqFields.put(FIELD_ENQUEUED_AT, Instant.now().toString());
        if (metadata != null) {
            dlqFields.putAll(metadata);
        }

        StringRecord dlqRecord = StreamRecords.string(dlqFields).withStreamKey(dlqKey);
        redisTemplate.opsForStream().add(dlqRecord);

        // ACK + DEL on original stream
        redisTemplate.opsForStream().acknowledge(streamKey, groupName, streamId);
        redisTemplate.opsForStream().delete(streamKey, streamId);

        log.debug("Moved message to DLQ: messageId={}, from={}, to={}", messageId, streamKey, dlqKey);
    }

    /**
     * Read messages from the dead-letter stream via XRANGE.
     *
     * @param start 0-based offset (mapped to "-" for start of stream)
     * @param count maximum number of entries to return
     */
    public List<MapRecord<String, Object, Object>> readDeadLetterMessages(String dlqKey,
                                                                          long start, long count) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(
                dlqKey, Range.unbounded(), Limit.limit().count((int) count)
        );
        return records != null ? records : Collections.emptyList();
    }

    /**
     * Reprocess a message from the DLQ: XADD back to the original stream, XDEL from DLQ.
     */
    public void reprocessFromDeadLetter(String dlqKey, String streamKey, String groupName,
                                        String dlqStreamId, UUID messageId, String storageKey,
                                        Map<String, String> metadata) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_MESSAGE_ID, messageId.toString());
        fields.put(FIELD_STORAGE_KEY, storageKey);
        fields.put(FIELD_ENQUEUED_AT, Instant.now().toString());
        if (metadata != null) {
            fields.putAll(metadata);
        }

        StringRecord record = StreamRecords.string(fields).withStreamKey(streamKey);
        redisTemplate.opsForStream().add(record);

        // Remove from DLQ
        redisTemplate.opsForStream().delete(dlqKey, dlqStreamId);

        log.debug("Reprocessed message from DLQ: messageId={}, dlq={}, stream={}",
                messageId, dlqKey, streamKey);
    }

    // ── Count ───────────────────────────────────────────────────────────

    /**
     * Return the number of entries in the stream (XLEN).
     * Returns 0 if the key does not exist.
     */
    public long messageCount(String streamKey) {
        try {
            Long size = redisTemplate.opsForStream().size(streamKey);
            return size != null ? size : 0L;
        } catch (Exception ex) {
            log.debug("XLEN failed (key may not exist): streamKey={}", streamKey);
            return 0L;
        }
    }

    /**
     * Return the number of pending (delivered but unacknowledged) messages via XPENDING summary.
     */
    public long pendingCount(String streamKey, String groupName) {
        try {
            var summary = redisTemplate.opsForStream().pending(streamKey, groupName);
            return summary != null ? summary.getTotalPendingMessages() : 0L;
        } catch (RedisSystemException ex) {
            if (isNoGroupError(ex)) {
                return 0L;
            }
            throw ex;
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    /**
     * Create the consumer group on the stream if it does not already exist.
     * Uses XGROUP CREATE … MKSTREAM with offset 0 (read from beginning).
     * Ignores BUSYGROUP error (group already exists).
     */
    public void ensureConsumerGroup(String streamKey, String groupName) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), groupName);
            log.debug("Created consumer group: streamKey={}, group={}", streamKey, groupName);
        } catch (RedisSystemException ex) {
            if (isBusyGroupError(ex)) {
                log.trace("Consumer group already exists: streamKey={}, group={}", streamKey, groupName);
            } else {
                throw ex;
            }
        }
    }

    /**
     * Delete the stream key entirely.
     */
    public void deleteStream(String streamKey) {
        Boolean deleted = redisTemplate.delete(streamKey);
        log.debug("Deleted stream: streamKey={}, result={}", streamKey, deleted);
    }

    /**
     * Destroy the consumer group on the stream.
     */
    public void destroyConsumerGroup(String streamKey, String groupName) {
        try {
            redisTemplate.opsForStream().destroyGroup(streamKey, groupName);
            log.debug("Destroyed consumer group: streamKey={}, group={}", streamKey, groupName);
        } catch (RedisSystemException ex) {
            log.debug("Could not destroy consumer group (may not exist): streamKey={}, group={}",
                    streamKey, groupName);
        }
    }

    /**
     * Collect all {@code storageKey} field values from every entry in the stream via XRANGE.
     * Used during queue deletion to clean up payloads.
     */
    public List<String> getAllStorageKeys(String streamKey) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(
                streamKey, Range.unbounded()
        );

        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        return records.stream()
                .map(r -> {
                    Object val = r.getValue().get(FIELD_STORAGE_KEY);
                    return val != null ? val.toString() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Full-stream messageId lookup ───────────────────────────────────

    /**
     * Find a stream entry by its {@code messageId} field value, scanning the entire stream
     * (not just the PEL). Used when we need to locate a message regardless of whether it has
     * been delivered to a consumer or not.
     *
     * @param streamKey the stream key
     * @param messageId the SIF-level message UUID string to search for
     * @return the matching stream record, or empty if not found
     */
    public Optional<MapRecord<String, Object, Object>> findEntryByMessageId(String streamKey, String messageId) {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(
                    streamKey, Range.unbounded()
            );

            if (records == null || records.isEmpty()) {
                return Optional.empty();
            }

            for (MapRecord<String, Object, Object> record : records) {
                Object msgIdField = record.getValue().get(FIELD_MESSAGE_ID);
                if (msgIdField != null && msgIdField.toString().equals(messageId)) {
                    return Optional.of(record);
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.debug("findEntryByMessageId failed: streamKey={}, messageId={}", streamKey, messageId);
            return Optional.empty();
        }
    }

    // ── Range read ─────────────────────────────────────────────────────

    /**
     * Read stream entries within a specific ID range via XRANGE.
     * Useful for reading a single entry by ID (start == end).
     *
     * @param streamKey the stream key
     * @param startId   the start stream ID (inclusive)
     * @param endId     the end stream ID (inclusive)
     * @return list of stream records in the range
     */
    public List<MapRecord<String, Object, Object>> readRange(String streamKey,
                                                              String startId, String endId) {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(
                    streamKey, Range.closed(startId, endId)
            );
            return records != null ? records : Collections.emptyList();
        } catch (Exception ex) {
            log.debug("XRANGE failed: streamKey={}, range=[{}, {}]", streamKey, startId, endId);
            return Collections.emptyList();
        }
    }

    // ── PEL group-level inspection ──────────────────────────────────────

    /**
     * Return pending messages for the entire consumer group (not filtered by consumer).
     * Uses XPENDING with range and count.
     *
     * @param streamKey the stream key
     * @param groupName the consumer group name
     * @param count     maximum number of pending entries to return
     * @return list of pending messages
     */
    public PendingMessages getPendingMessagesForGroup(String streamKey, String groupName, int count) {
        try {
            return redisTemplate.opsForStream()
                    .pending(streamKey, groupName, Range.unbounded(), count);
        } catch (RedisSystemException ex) {
            if (isNoGroupError(ex)) {
                return null;
            }
            throw ex;
        }
    }

    // ── Cursor-based batch read ────────────────────────────────────────

    /**
     * Read a batch of entries from the stream using XRANGE with COUNT.
     * Returns storage keys, stream entry IDs, and cursor info for pagination.
     *
     * <p>Uses exclusive start by appending {@code \0} to the afterId to skip
     * the entry at the cursor position (already processed in previous batch).
     * Pass {@code "-"} or {@code "0"} for the first batch.
     *
     * @param streamKey the stream key
     * @param afterId   the exclusive start ID (use "-" or "0" for first batch)
     * @param count     max entries to read
     * @return batch result with storage keys, entry IDs, and cursor position
     */
    public StreamBatch getStorageKeysBatch(String streamKey, String afterId, int count) {
        // Determine the inclusive start for XRANGE.
        // For the first batch, use Range.unbounded() lower bound.
        // For subsequent batches, make the start exclusive by appending \0 to skip the last processed ID.
        String rangeStart;
        if ("-".equals(afterId) || "0".equals(afterId) || afterId == null || afterId.isEmpty()) {
            rangeStart = "-";
        } else {
            // Exclusive start: use the next possible ID after afterId
            rangeStart = incrementStreamId(afterId);
        }

        List<MapRecord<String, Object, Object>> records;
        try {
            if ("-".equals(rangeStart)) {
                records = redisTemplate.opsForStream().range(
                        streamKey, Range.unbounded(), Limit.limit().count(count)
                );
            } else {
                records = redisTemplate.opsForStream().range(
                        streamKey,
                        Range.rightUnbounded(Range.Bound.inclusive(rangeStart)),
                        Limit.limit().count(count)
                );
            }
        } catch (Exception ex) {
            log.debug("XRANGE batch failed (key may not exist): streamKey={}", streamKey);
            return new StreamBatch(Collections.emptyList(), Collections.emptyList(), afterId, false);
        }

        if (records == null || records.isEmpty()) {
            return new StreamBatch(Collections.emptyList(), Collections.emptyList(), afterId, false);
        }

        List<String> storageKeys = new ArrayList<>(records.size());
        List<String> entryIds = new ArrayList<>(records.size());
        String lastId = afterId;

        for (MapRecord<String, Object, Object> record : records) {
            String entryId = record.getId().getValue();
            entryIds.add(entryId);
            lastId = entryId;

            Object storageKeyVal = record.getValue().get(FIELD_STORAGE_KEY);
            storageKeys.add(storageKeyVal != null ? storageKeyVal.toString() : null);
        }

        // If we got exactly `count` records, there may be more
        boolean hasMore = records.size() == count;

        return new StreamBatch(storageKeys, entryIds, lastId, hasMore);
    }

    /**
     * Increment a Redis stream ID to produce the next exclusive start.
     * For IDs like "1234567890-0", increments the sequence to "1234567890-1".
     * For IDs without a sequence part, appends "-1".
     */
    private String incrementStreamId(String streamId) {
        int dashIndex = streamId.lastIndexOf('-');
        if (dashIndex < 0) {
            return streamId + "-1";
        }
        String timePart = streamId.substring(0, dashIndex);
        String seqPart = streamId.substring(dashIndex + 1);
        try {
            long seq = Long.parseLong(seqPart);
            return timePart + "-" + (seq + 1);
        } catch (NumberFormatException e) {
            // Fallback: append \0 for lexicographic exclusion
            return streamId + "\0";
        }
    }

    // ── Batch delete (XDEL) ─────────────────────────────────────────────

    /**
     * Delete specific entries from a stream by their IDs.
     * Uses XDEL which is O(1) per entry and does not block Redis.
     *
     * @param streamKey the stream key
     * @param entryIds  the stream entry IDs to delete
     * @return number of entries actually deleted
     */
    public long deleteEntries(String streamKey, List<String> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return 0L;
        }

        String[] ids = entryIds.toArray(new String[0]);
        Long deleted = redisTemplate.opsForStream().delete(streamKey, ids);

        log.debug("XDEL: streamKey={}, requested={}, deleted={}",
                streamKey, entryIds.size(), deleted);
        return deleted != null ? deleted : 0L;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private boolean isNoGroupError(RedisSystemException ex) {
        String message = getRootMessage(ex);
        return message != null && message.contains("NOGROUP");
    }

    private boolean isBusyGroupError(RedisSystemException ex) {
        String message = getRootMessage(ex);
        return message != null && message.contains("BUSYGROUP");
    }

    private String getRootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
