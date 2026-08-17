// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.queueservice.config.EmbeddedRedisTestConfiguration;
import org.sifunity.broker.queueservice.redis.StreamBatch;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StreamMessageRepository batch operations:
 * getStorageKeysBatch and deleteEntries.
 *
 * Uses a real Redis instance (Testcontainers) since these operations
 * rely on XRANGE and XDEL behavior that cannot be meaningfully mocked.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-batch"
})
@DisplayName("StreamMessageRepository Batch Operations")
class StreamMessageRepositoryBatchTest {

    private static final String DOMAIN_ID = "test-domain";
    private static final String SOLUTION_ID = "test-solution";
    private static final UUID ENVIRONMENT_ID = UUID.randomUUID();
    private static final UUID QUEUE_ID = UUID.randomUUID();

    @Autowired
    private StreamMessageRepository streamMessageRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String streamKey;
    private String groupName;

    @BeforeEach
    void setUp() {
        streamKey = streamMessageRepository.streamKey(DOMAIN_ID, SOLUTION_ID, ENVIRONMENT_ID, QUEUE_ID);
        groupName = streamMessageRepository.groupName(QUEUE_ID);
        // Clean up any existing stream
        redisTemplate.delete(streamKey);
    }

    @Nested
    @DisplayName("getStorageKeysBatch()")
    class GetStorageKeysBatchTests {

        @Test
        @DisplayName("should return empty batch for empty stream")
        void shouldReturnEmptyBatchForEmptyStream() {
            StreamBatch batch = streamMessageRepository.getStorageKeysBatch(streamKey, "-", 100);

            assertThat(batch.storageKeys()).isEmpty();
            assertThat(batch.entryIds()).isEmpty();
            assertThat(batch.hasMore()).isFalse();
        }

        @Test
        @DisplayName("should return empty batch for non-existent stream key")
        void shouldReturnEmptyBatchForNonExistentKey() {
            String nonExistentKey = "function:msg_queue:non-existent-stream";

            StreamBatch batch = streamMessageRepository.getStorageKeysBatch(nonExistentKey, "-", 100);

            assertThat(batch.storageKeys()).isEmpty();
            assertThat(batch.entryIds()).isEmpty();
            assertThat(batch.hasMore()).isFalse();
        }

        @Test
        @DisplayName("should return all entries when count exceeds stream size")
        void shouldReturnAllEntriesWhenCountExceedsSize() {
            // Enqueue 3 messages
            List<String> expectedKeys = enqueueMessages(3);

            StreamBatch batch = streamMessageRepository.getStorageKeysBatch(streamKey, "-", 100);

            assertThat(batch.storageKeys()).hasSize(3);
            assertThat(batch.storageKeys()).containsExactlyElementsOf(expectedKeys);
            assertThat(batch.entryIds()).hasSize(3);
            assertThat(batch.hasMore()).isFalse();
        }

        @Test
        @DisplayName("should set hasMore=true when batch is full")
        void shouldSetHasMoreWhenBatchIsFull() {
            // Enqueue 5 messages, request only 3
            enqueueMessages(5);

            StreamBatch batch = streamMessageRepository.getStorageKeysBatch(streamKey, "-", 3);

            assertThat(batch.storageKeys()).hasSize(3);
            assertThat(batch.entryIds()).hasSize(3);
            assertThat(batch.hasMore()).isTrue();
        }

        @Test
        @DisplayName("should advance cursor correctly across multiple batches")
        void shouldAdvanceCursorCorrectly() {
            // Enqueue 5 messages
            List<String> expectedKeys = enqueueMessages(5);

            // First batch: get 2
            StreamBatch batch1 = streamMessageRepository.getStorageKeysBatch(streamKey, "-", 2);
            assertThat(batch1.storageKeys()).hasSize(2);
            assertThat(batch1.storageKeys()).containsExactlyElementsOf(expectedKeys.subList(0, 2));
            assertThat(batch1.hasMore()).isTrue();

            // Second batch: use lastId from first batch as cursor
            StreamBatch batch2 = streamMessageRepository.getStorageKeysBatch(streamKey, batch1.lastId(), 2);
            assertThat(batch2.storageKeys()).hasSize(2);
            assertThat(batch2.storageKeys()).containsExactlyElementsOf(expectedKeys.subList(2, 4));
            assertThat(batch2.hasMore()).isTrue();

            // Third batch: should get remaining 1
            StreamBatch batch3 = streamMessageRepository.getStorageKeysBatch(streamKey, batch2.lastId(), 2);
            assertThat(batch3.storageKeys()).hasSize(1);
            assertThat(batch3.storageKeys()).containsExactlyElementsOf(expectedKeys.subList(4, 5));
            assertThat(batch3.hasMore()).isFalse();
        }

        @Test
        @DisplayName("should not return duplicate entries across batches")
        void shouldNotReturnDuplicatesAcrossBatches() {
            enqueueMessages(6);

            List<String> allEntryIds = new ArrayList<>();
            String cursor = "-";
            boolean hasMore = true;

            while (hasMore) {
                StreamBatch batch = streamMessageRepository.getStorageKeysBatch(streamKey, cursor, 2);
                allEntryIds.addAll(batch.entryIds());
                cursor = batch.lastId();
                hasMore = batch.hasMore();
            }

            // All entry IDs should be unique
            assertThat(allEntryIds).hasSize(6);
            assertThat(allEntryIds).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("should handle afterId with 0 as first batch indicator")
        void shouldHandleZeroAsFirstBatch() {
            enqueueMessages(2);

            StreamBatch batch = streamMessageRepository.getStorageKeysBatch(streamKey, "0", 100);

            assertThat(batch.storageKeys()).hasSize(2);
            assertThat(batch.hasMore()).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteEntries()")
    class DeleteEntriesTests {

        @Test
        @DisplayName("should delete specified entries and return count")
        void shouldDeleteSpecifiedEntries() {
            enqueueMessages(3);

            // Get all entry IDs
            StreamBatch batch = streamMessageRepository.getStorageKeysBatch(streamKey, "-", 100);
            assertThat(batch.entryIds()).hasSize(3);

            // Delete first 2 entries
            List<String> toDelete = batch.entryIds().subList(0, 2);
            long deleted = streamMessageRepository.deleteEntries(streamKey, toDelete);

            assertThat(deleted).isEqualTo(2);
            // Stream should have 1 remaining
            assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(1);
        }

        @Test
        @DisplayName("should return 0 for empty entry list")
        void shouldReturnZeroForEmptyList() {
            long deleted = streamMessageRepository.deleteEntries(streamKey, List.of());

            assertThat(deleted).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 0 for null entry list")
        void shouldReturnZeroForNullList() {
            long deleted = streamMessageRepository.deleteEntries(streamKey, null);

            assertThat(deleted).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle deleting non-existent entry IDs gracefully")
        void shouldHandleNonExistentEntryIds() {
            enqueueMessages(2);

            // Try to delete IDs that don't exist
            long deleted = streamMessageRepository.deleteEntries(streamKey, List.of("9999999999-0", "9999999999-1"));

            assertThat(deleted).isEqualTo(0);
            // Original entries should still be there
            assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(2);
        }

        @Test
        @DisplayName("should delete all entries leaving stream empty")
        void shouldDeleteAllEntries() {
            enqueueMessages(5);

            StreamBatch batch = streamMessageRepository.getStorageKeysBatch(streamKey, "-", 100);
            long deleted = streamMessageRepository.deleteEntries(streamKey, batch.entryIds());

            assertThat(deleted).isEqualTo(5);
            assertThat(streamMessageRepository.messageCount(streamKey)).isEqualTo(0);
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    /**
     * Enqueue N messages and return their storage keys in order.
     */
    private List<String> enqueueMessages(int count) {
        List<String> storageKeys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String storageKey = "storage/payload-" + i + ".json";
            storageKeys.add(storageKey);
            streamMessageRepository.enqueue(
                    streamKey, groupName, UUID.randomUUID(),
                    storageKey, Instant.now()
            );
        }
        return storageKeys;
    }
}
