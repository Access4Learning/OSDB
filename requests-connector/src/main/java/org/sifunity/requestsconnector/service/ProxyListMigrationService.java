// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.requestsconnector.config.ProxyStreamConfig;
import org.sifunity.requestsconnector.dto.ProxyRequestMetadata;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-time migration service that drains entries from the old Redis list-based
 * proxy request infrastructure into the new Redis Stream.
 *
 * <p>On startup ({@code @PostConstruct}), this service:</p>
 * <ol>
 *   <li>Drains all entries from the old {@code proxy-requests} list</li>
 *   <li>Uses SCAN to find {@code proxy-requests-processing:*} keys and drains those</li>
 *   <li>For each drained entry: serializes to field map and XADDs to {@code proxy-requests-stream}</li>
 *   <li>Deletes the old list keys after draining</li>
 * </ol>
 *
 * <p>Additionally handles rolling-deploy transition: for the first 60 seconds after startup,
 * a scheduled task checks the old {@code proxy-requests} list for entries that old pods may
 * have pushed after this pod's {@code @PostConstruct} ran. After 60s the dual-read window
 * closes and the old list is no longer polled.</p>
 *
 * <p>Requirements: 2.1</p>
 */
@Service
@Slf4j
public class ProxyListMigrationService {

    /** Old Redis list key used before the Streams migration. */
    private static final String OLD_LIST_KEY = "proxy-requests";

    /** Pattern for old per-worker processing list keys. */
    private static final String PROCESSING_KEY_PATTERN = "proxy-requests-processing:*";

    /** Duration in ms for the dual-read migration window (60 seconds). */
    private static final long MIGRATION_WINDOW_MS = 60_000L;

    private final RedisTemplate<String, ProxyRequestMetadata> proxyRequestMetadataRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean migrationWindowActive = new AtomicBoolean(false);
    private volatile long migrationWindowStartTime;

    public ProxyListMigrationService(
            RedisTemplate<String, ProxyRequestMetadata> proxyRequestMetadataRedisTemplate,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.proxyRequestMetadataRedisTemplate = proxyRequestMetadataRedisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs once on deploy. Drains old list and processing keys into the new stream,
     * then activates the 60s dual-read window for rolling-deploy safety.
     */
    @PostConstruct
    public void migrateOldListToStream() {
        log.info("ProxyListMigrationService: starting one-time migration from old Redis list to stream");

        int totalMigrated = 0;

        // Step 1: Drain the old proxy-requests list
        totalMigrated += drainList(OLD_LIST_KEY);

        // Step 2: SCAN for proxy-requests-processing:* keys and drain each
        List<String> processingKeys = scanForProcessingKeys();
        for (String key : processingKeys) {
            totalMigrated += drainList(key);
        }

        // Step 3: Delete old keys (list key + processing keys)
        deleteOldKeys(processingKeys);

        if (totalMigrated > 0) {
            log.info("ProxyListMigrationService: migration complete — {} entries migrated to stream '{}'",
                    totalMigrated, ProxyStreamConfig.STREAM_KEY);
        } else {
            log.info("ProxyListMigrationService: no old entries found, nothing to migrate");
        }

        // Step 4: Activate dual-read window for rolling-deploy transition
        migrationWindowStartTime = System.currentTimeMillis();
        migrationWindowActive.set(true);
        log.info("ProxyListMigrationService: dual-read migration window active for {}ms", MIGRATION_WINDOW_MS);
    }

    /**
     * Scheduled task that checks the old list during the 60s dual-read window.
     * Handles entries that old pods may push to the list after this pod's @PostConstruct.
     * Automatically deactivates after the migration window expires.
     */
    @Scheduled(fixedDelay = 2000)
    public void dualReadCheck() {
        if (!migrationWindowActive.get()) {
            return;
        }

        // Check if migration window has expired
        if (System.currentTimeMillis() - migrationWindowStartTime > MIGRATION_WINDOW_MS) {
            migrationWindowActive.set(false);
            // Final cleanup: delete old list key in case entries were pushed and drained
            deleteKeyIfExists(OLD_LIST_KEY);
            log.info("ProxyListMigrationService: dual-read migration window closed after {}ms", MIGRATION_WINDOW_MS);
            return;
        }

        // Drain any new entries from the old list
        int migrated = drainList(OLD_LIST_KEY);
        if (migrated > 0) {
            log.info("ProxyListMigrationService: dual-read migrated {} entries from old list", migrated);
        }
    }

    /**
     * Drains all entries from the given Redis list key into the proxy-requests-stream.
     *
     * @param listKey the Redis list key to drain
     * @return the number of entries migrated
     */
    private int drainList(String listKey) {
        int count = 0;
        try {
            ProxyRequestMetadata entry;
            while ((entry = proxyRequestMetadataRedisTemplate.opsForList().leftPop(listKey)) != null) {
                xaddToStream(entry);
                count++;
            }
        } catch (Exception e) {
            log.warn("ProxyListMigrationService: error draining list '{}': {}", listKey, e.getMessage());
        }
        return count;
    }

    /**
     * Uses SCAN (not KEYS) to find all {@code proxy-requests-processing:*} keys.
     * SCAN is non-blocking; KEYS would block single-threaded Redis.
     *
     * @return list of matching key names
     */
    private List<String> scanForProcessingKeys() {
        List<String> keys = new ArrayList<>();
        try {
            stringRedisTemplate.execute((RedisConnection connection) -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(PROCESSING_KEY_PATTERN)
                        .count(100)
                        .build();
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next()));
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("ProxyListMigrationService: error scanning for processing keys: {}", e.getMessage());
        }

        if (!keys.isEmpty()) {
            log.info("ProxyListMigrationService: found {} old processing list key(s) via SCAN", keys.size());
        }
        return keys;
    }

    /**
     * Serializes a {@link ProxyRequestMetadata} into a stream field map and XADDs it
     * to the proxy-requests-stream.
     *
     * @param metadata the entry to migrate
     */
    private void xaddToStream(ProxyRequestMetadata metadata) {
        Map<String, String> fields = buildStreamFieldMap(metadata);
        StringRecord record = StreamRecords.string(fields).withStreamKey(ProxyStreamConfig.STREAM_KEY);
        stringRedisTemplate.opsForStream().add(record);
    }

    /**
     * Builds a field map matching the format used by {@code DelayedRequestHandler.buildStreamFieldMap}.
     *
     * @param metadata the proxy request metadata to serialize
     * @return field map for the stream entry
     */
    private Map<String, String> buildStreamFieldMap(ProxyRequestMetadata metadata) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("messageId", metadata.getMessageId() != null ? metadata.getMessageId().toString() : "");
        fields.put("consumerQueueId", metadata.getConsumerQueueId() != null ? metadata.getConsumerQueueId().toString() : "");
        fields.put("environmentId", metadata.getEnvironmentId() != null ? metadata.getEnvironmentId().toString() : "");
        fields.put("environmentName", metadata.getEnvironmentName() != null ? metadata.getEnvironmentName() : "");
        fields.put("domainId", metadata.getDomainId() != null ? metadata.getDomainId() : "");
        fields.put("solutionId", metadata.getSolutionId() != null ? metadata.getSolutionId() : "");
        fields.put("httpMethod", metadata.getHttpMethod() != null ? metadata.getHttpMethod() : "");
        fields.put("providerEnvironmentId", metadata.getProviderEnvironmentId() != null ? metadata.getProviderEnvironmentId().toString() : "");
        fields.put("relativeServicePath", metadata.getRelativeServicePath() != null ? metadata.getRelativeServicePath() : "");
        fields.put("providerEndpoint", metadata.getProviderEndpoint() != null ? metadata.getProviderEndpoint() : "");
        fields.put("createdAt", metadata.getCreatedAt() != null ? metadata.getCreatedAt().toString() : Instant.now().toString());

        // Serialize requestEnvelope as JSON
        try {
            fields.put("requestEnvelope", objectMapper.writeValueAsString(metadata.getRequestEnvelope()));
        } catch (JsonProcessingException e) {
            log.warn("ProxyListMigrationService: failed to serialize requestEnvelope for messageId={}, using empty",
                    metadata.getMessageId());
            fields.put("requestEnvelope", "{}");
        }

        return fields;
    }

    /**
     * Deletes old list keys after migration is complete.
     *
     * @param processingKeys the processing list keys found by SCAN
     */
    private void deleteOldKeys(List<String> processingKeys) {
        deleteKeyIfExists(OLD_LIST_KEY);
        for (String key : processingKeys) {
            deleteKeyIfExists(key);
        }
    }

    /**
     * Deletes a key if it exists. Logs at debug level.
     */
    private void deleteKeyIfExists(String key) {
        try {
            Boolean deleted = stringRedisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("ProxyListMigrationService: deleted old key '{}'", key);
            }
        } catch (Exception e) {
            log.warn("ProxyListMigrationService: error deleting key '{}': {}", key, e.getMessage());
        }
    }

    /**
     * Returns whether the dual-read migration window is currently active.
     * Visible for testing.
     */
    boolean isMigrationWindowActive() {
        return migrationWindowActive.get();
    }
}
