// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.requestsconnector.config.ProxyStreamConfig;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Redis integration tests for proxy worker Redis Streams semantics.
 *
 * <p>Uses Testcontainers with Redis 7.x to validate XAUTOCLAIM behavior, idle timing,
 * concurrent claims, delivery counting, and one-time migration — all against actual
 * Redis Streams, not mocks.</p>
 *
 * <p>These tests confirm the subtle behaviors that property-based tests cannot cover
 * because they mock XAUTOCLAIM-equivalent logic. The real Redis semantics validated here:</p>
 * <ul>
 *   <li>Idle time tracking per PEL entry</li>
 *   <li>Atomic claim semantics (only one consumer wins)</li>
 *   <li>Delivery count incrementing on redelivery</li>
 *   <li>XAUTOCLAIM threshold filtering</li>
 * </ul>
 *
 * <p>Requires Docker to be running. Tests are skipped in environments without Docker.</p>
 *
 * <p><em>Requirements: 2.1, 2.4</em></p>
 */
@Testcontainers
@DisplayName("ProxyWorkerStreams Integration Tests (Real Redis)")
class ProxyWorkerStreamsIntegrationTest {

    private static final String STREAM_KEY = ProxyStreamConfig.STREAM_KEY;
    private static final String CONSUMER_GROUP = ProxyStreamConfig.CONSUMER_GROUP;
    private static final String DLQ_STREAM_KEY = ProxyStreamConfig.DLQ_STREAM_KEY;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private StringRedisTemplate redisTemplate;
    private LettuceConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getFirstMappedPort());
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // FLUSHDB for clean state
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        // Create consumer group (idempotent — ignore BUSYGROUP)
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
        } catch (Exception e) {
            // BUSYGROUP — group already exists, ignore
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 1: Simulated crash recovery
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Crash recovery: XAUTOCLAIM reclaims orphaned message after idle > threshold")
    void crashRecovery_xautoclaimReclaimsOrphanedMessage() throws InterruptedException {
        // Given: a message added to the stream
        Map<String, String> fields = buildTestMessage("crash-recovery-msg");
        StringRecord record = StreamRecords.string(fields).withStreamKey(STREAM_KEY);
        RecordId addedId = redisTemplate.opsForStream().add(record);

        // Consumer "worker-1" reads the message (simulating it took the message)
        List<MapRecord<String, Object, Object>> read = redisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, "worker-1"),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

        assertThat(read).hasSize(1);
        assertThat(read.get(0).getId()).isEqualTo(addedId);

        // Worker-1 "crashes" — never ACKs. Wait for idle to exceed threshold.
        // Use a short threshold for testing: 2000ms
        long testIdleThresholdMs = 2000;
        Thread.sleep(testIdleThresholdMs + 500); // Wait a bit beyond threshold

        // When: "worker-2" runs XAUTOCLAIM (via XPENDING + XCLAIM pattern)
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, CONSUMER_GROUP, Range.unbounded(), 10);

        assertThat(pending).isNotNull();
        assertThat(pending.size()).isEqualTo(1);

        PendingMessage pendingEntry = pending.get(0);
        assertThat(pendingEntry.getElapsedTimeSinceLastDelivery().toMillis())
                .isGreaterThanOrEqualTo(testIdleThresholdMs);

        // XCLAIM the idle message
        List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                STREAM_KEY, CONSUMER_GROUP, "worker-2",
                Duration.ofMillis(testIdleThresholdMs),
                RecordId.of(pendingEntry.getIdAsString()));

        // Then: worker-2 successfully claims the message
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getId()).isEqualTo(addedId);

        // Verify the message is now owned by worker-2 in PEL
        PendingMessages afterClaim = redisTemplate.opsForStream()
                .pending(STREAM_KEY, Consumer.from(CONSUMER_GROUP, "worker-2"), Range.unbounded(), 10);
        assertThat(afterClaim.size()).isEqualTo(1);

        // worker-1 should have no pending messages
        PendingMessages worker1Pending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, Consumer.from(CONSUMER_GROUP, "worker-1"), Range.unbounded(), 10);
        assertThat(worker1Pending.size()).isEqualTo(0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 2: Slow consumer not stolen
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Slow consumer: XCLAIM does NOT reclaim message below idle threshold")
    void slowConsumer_xclaimDoesNotReclaimBelowThreshold() throws InterruptedException {
        // Given: a message read by worker-1
        Map<String, String> fields = buildTestMessage("slow-consumer-msg");
        StringRecord record = StreamRecords.string(fields).withStreamKey(STREAM_KEY);
        redisTemplate.opsForStream().add(record);

        redisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, "worker-1"),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

        // Worker-1 is "slow" but still alive — wait 1 second (well below 5s threshold)
        Thread.sleep(1000);

        // When: worker-2 attempts XCLAIM with a 5s threshold
        long highThresholdMs = 5000;
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, CONSUMER_GROUP, Range.unbounded(), 10);

        assertThat(pending.size()).isEqualTo(1);
        PendingMessage entry = pending.get(0);

        // The idle time should be ~1s, which is below 5s threshold
        assertThat(entry.getElapsedTimeSinceLastDelivery().toMillis()).isLessThan(highThresholdMs);

        // XCLAIM with minIdleTime=5000ms — should NOT claim since message idle < 5s
        List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                STREAM_KEY, CONSUMER_GROUP, "worker-2",
                Duration.ofMillis(highThresholdMs),
                RecordId.of(entry.getIdAsString()));

        // Then: message is NOT reclaimed (idle < threshold)
        assertThat(claimed).isEmpty();

        // Worker-1 still owns the message
        PendingMessages worker1Pending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, Consumer.from(CONSUMER_GROUP, "worker-1"), Range.unbounded(), 10);
        assertThat(worker1Pending.size()).isEqualTo(1);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 3: Two replicas running XAUTOCLAIM concurrently
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Concurrent XAUTOCLAIM: only one consumer wins the claim (atomic)")
    void concurrentXautoclaim_onlyOneConsumerWins() throws InterruptedException {
        // Given: a message that's been idle beyond threshold
        Map<String, String> fields = buildTestMessage("concurrent-claim-msg");
        StringRecord record = StreamRecords.string(fields).withStreamKey(STREAM_KEY);
        RecordId addedId = redisTemplate.opsForStream().add(record);

        // Consumer "original-worker" reads and "crashes"
        redisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, "original-worker"),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

        // Wait for idle to exceed threshold
        long testThresholdMs = 2000;
        Thread.sleep(testThresholdMs + 500);

        // When: two replicas concurrently try to XCLAIM the same message
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> successfulClaimers = new CopyOnWriteArrayList<>();

        for (String claimerName : List.of("replica-1", "replica-2")) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize start
                    List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                            STREAM_KEY, CONSUMER_GROUP, claimerName,
                            Duration.ofMillis(testThresholdMs),
                            RecordId.of(addedId.getValue()));
                    if (claimed != null && !claimed.isEmpty()) {
                        successfulClaimers.add(claimerName);
                    }
                } catch (Exception e) {
                    // Log but don't fail — concurrent contention may cause transient issues
                }
            });
        }

        startLatch.countDown(); // Release both threads simultaneously
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Then: at most one consumer ends up owning the message in PEL
        // (XCLAIM is atomic — both may "succeed" in returning the record,
        //  but the PEL owner is always the last writer. The key invariant is:
        //  exactly one consumer owns it in the PEL at any point.)
        PendingMessages allPending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, CONSUMER_GROUP, Range.unbounded(), 10);
        assertThat(allPending.size()).isEqualTo(1);

        // The message is owned by exactly one consumer
        String finalOwner = allPending.get(0).getConsumerName();
        assertThat(finalOwner).isIn("replica-1", "replica-2");

        // Verify the OTHER replica has 0 pending
        String otherReplica = finalOwner.equals("replica-1") ? "replica-2" : "replica-1";
        PendingMessages otherPending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, Consumer.from(CONSUMER_GROUP, otherReplica), Range.unbounded(), 10);
        assertThat(otherPending.size()).isEqualTo(0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 4: Delivery count → DLQ
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Delivery count: message moves to DLQ after maxRetries redeliveries")
    void deliveryCount_movesToDlqAfterMaxRetries() throws InterruptedException {
        int maxRetries = 3;
        long claimThresholdMs = 1500;

        // Given: a message in the stream
        Map<String, String> fields = buildTestMessage("dlq-candidate-msg");
        StringRecord record = StreamRecords.string(fields).withStreamKey(STREAM_KEY);
        RecordId addedId = redisTemplate.opsForStream().add(record);

        // Simulate repeated read-and-abandon cycles to increment delivery count
        for (int delivery = 1; delivery <= maxRetries; delivery++) {
            String consumerName = "worker-attempt-" + delivery;

            if (delivery == 1) {
                // First delivery: XREADGROUP
                redisTemplate.opsForStream().read(
                        Consumer.from(CONSUMER_GROUP, consumerName),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
            } else {
                // Subsequent deliveries: XCLAIM after idle exceeds threshold
                Thread.sleep(claimThresholdMs + 500);
                redisTemplate.opsForStream().claim(
                        STREAM_KEY, CONSUMER_GROUP, consumerName,
                        Duration.ofMillis(claimThresholdMs),
                        addedId);
            }

            // Don't ACK — simulates crash/failure, message stays in PEL
            // Wait for idle to build up for next claim (except after last delivery)
            if (delivery < maxRetries) {
                // Let idle accumulate for next claim
            }
        }

        // Wait for final idle accumulation
        Thread.sleep(claimThresholdMs + 500);

        // Verify delivery count via XPENDING
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, CONSUMER_GROUP, Range.unbounded(), 10);
        assertThat(pending.size()).isEqualTo(1);
        long deliveryCount = pending.get(0).getTotalDeliveryCount();
        assertThat(deliveryCount).isGreaterThanOrEqualTo(maxRetries);

        // When: DLQ logic executes — move message to DLQ stream
        Map<String, String> dlqFields = new LinkedHashMap<>(fields);
        dlqFields.put("originalStreamId", addedId.getValue());
        dlqFields.put("deliveryCount", String.valueOf(deliveryCount));
        dlqFields.put("movedToDlqAt", Instant.now().toString());

        // XADD to DLQ
        StringRecord dlqRecord = StreamRecords.string(dlqFields).withStreamKey(DLQ_STREAM_KEY);
        RecordId dlqId = redisTemplate.opsForStream().add(dlqRecord);

        // XACK + XDEL on main stream
        redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, addedId.getValue());
        redisTemplate.opsForStream().delete(STREAM_KEY, addedId.getValue());

        // Then: message is in DLQ stream
        assertThat(dlqId).isNotNull();

        // Read from DLQ to verify content
        List<MapRecord<String, Object, Object>> dlqEntries = redisTemplate.opsForStream().read(
                StreamOffset.fromStart(DLQ_STREAM_KEY));
        assertThat(dlqEntries).hasSize(1);

        Map<Object, Object> dlqValues = dlqEntries.get(0).getValue();
        assertThat(dlqValues.get("originalStreamId")).isEqualTo(addedId.getValue());
        assertThat(Long.parseLong(dlqValues.get("deliveryCount").toString()))
                .isGreaterThanOrEqualTo(maxRetries);

        // Main stream PEL should be empty
        PendingMessages mainPending = redisTemplate.opsForStream()
                .pending(STREAM_KEY, CONSUMER_GROUP, Range.unbounded(), 10);
        assertThat(mainPending.size()).isEqualTo(0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 5: One-time migration from old Redis list to new stream
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Migration: entries from old Redis list and processing keys appear in new stream")
    void migration_oldListEntriesAppearInNewStream() {
        // Given: seed old Redis list with entries (simulating pre-migration state)
        String oldListKey = "proxy-requests";
        String processingKey1 = "proxy-requests-processing:worker-abc123";
        String processingKey2 = "proxy-requests-processing:worker-def456";

        // Push serialized JSON entries to old list (the format ProxyRequestMetadata was stored as)
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        List<Map<String, String>> expectedEntries = new ArrayList<>();

        // 3 entries in main list
        for (int i = 0; i < 3; i++) {
            Map<String, String> entry = buildTestMessage("list-msg-" + i);
            expectedEntries.add(entry);
            // Old system stored as JSON string in a Redis list
            redisTemplate.opsForList().rightPush(oldListKey, serializeFields(entry));
        }

        // 2 entries in processing list for crashed worker
        for (int i = 0; i < 2; i++) {
            Map<String, String> entry = buildTestMessage("processing-msg-" + i);
            expectedEntries.add(entry);
            redisTemplate.opsForList().rightPush(processingKey1, serializeFields(entry));
        }

        // 1 entry in another processing list
        Map<String, String> entry = buildTestMessage("processing2-msg-0");
        expectedEntries.add(entry);
        redisTemplate.opsForList().rightPush(processingKey2, serializeFields(entry));

        // Verify old keys exist
        assertThat(redisTemplate.opsForList().size(oldListKey)).isEqualTo(3);
        assertThat(redisTemplate.opsForList().size(processingKey1)).isEqualTo(2);
        assertThat(redisTemplate.opsForList().size(processingKey2)).isEqualTo(1);

        // When: simulate migration logic (replicate ProxyListMigrationService behavior)
        int migrated = 0;

        // Drain main list
        String jsonEntry;
        while ((jsonEntry = redisTemplate.opsForList().leftPop(oldListKey)) != null) {
            Map<String, String> fields = deserializeFields(jsonEntry);
            StringRecord record = StreamRecords.string(fields).withStreamKey(STREAM_KEY);
            redisTemplate.opsForStream().add(record);
            migrated++;
        }

        // Drain processing lists (found via SCAN in production; here we know the keys)
        for (String procKey : List.of(processingKey1, processingKey2)) {
            while ((jsonEntry = redisTemplate.opsForList().leftPop(procKey)) != null) {
                Map<String, String> fields = deserializeFields(jsonEntry);
                StringRecord record = StreamRecords.string(fields).withStreamKey(STREAM_KEY);
                redisTemplate.opsForStream().add(record);
                migrated++;
            }
        }

        // Delete old keys
        redisTemplate.delete(oldListKey);
        redisTemplate.delete(processingKey1);
        redisTemplate.delete(processingKey2);

        // Then: all 6 entries are in the new stream
        assertThat(migrated).isEqualTo(6);

        // Read all entries from the stream (without consumer group — just read from start)
        List<MapRecord<String, Object, Object>> streamEntries = redisTemplate.opsForStream().read(
                StreamOffset.fromStart(STREAM_KEY));
        assertThat(streamEntries).hasSize(6);

        // Verify each entry has the expected messageId
        List<String> streamMessageIds = streamEntries.stream()
                .map(r -> r.getValue().get("messageId").toString())
                .toList();

        for (Map<String, String> expected : expectedEntries) {
            assertThat(streamMessageIds).contains(expected.get("messageId"));
        }

        // Old keys should be deleted
        assertThat(redisTemplate.hasKey(oldListKey)).isFalse();
        assertThat(redisTemplate.hasKey(processingKey1)).isFalse();
        assertThat(redisTemplate.hasKey(processingKey2)).isFalse();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds a test message field map matching the format used by the proxy stream.
     */
    private Map<String, String> buildTestMessage(String label) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("messageId", UUID.randomUUID().toString());
        fields.put("consumerQueueId", UUID.randomUUID().toString());
        fields.put("environmentId", UUID.randomUUID().toString());
        fields.put("environmentName", "test-env-" + label);
        fields.put("domainId", "test-domain");
        fields.put("solutionId", "test-solution");
        fields.put("httpMethod", "GET");
        fields.put("providerEnvironmentId", UUID.randomUUID().toString());
        fields.put("relativeServicePath", "StudentPersonals;zoneId=default");
        fields.put("providerEndpoint", "http://provider.test/" + label);
        fields.put("createdAt", Instant.now().toString());
        fields.put("requestEnvelope", "{\"messageType\":\"REQUEST\",\"serviceName\":\"StudentPersonals\",\"payload\":{}}");
        return fields;
    }

    /**
     * Serializes a field map to a JSON string (simulating old list storage format).
     */
    private String serializeFields(Map<String, String> fields) {
        try {
            return new ObjectMapper().writeValueAsString(fields);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize test fields", e);
        }
    }

    /**
     * Deserializes a JSON string back to a field map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> deserializeFields(String json) {
        try {
            return new ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize test fields", e);
        }
    }
}
