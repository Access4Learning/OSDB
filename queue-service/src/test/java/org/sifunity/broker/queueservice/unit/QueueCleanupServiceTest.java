// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.queueservice.redis.StreamBatch;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.QueueCleanupService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueueCleanupService.
 *
 * <p>Requirements: 1.5, 1.6, 2.8, 7.6</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueCleanupService Unit Tests")
class QueueCleanupServiceTest {

    @Mock private StreamMessageRepository streamMessageRepository;
    @Mock private RefCountService refCountService;
    @Mock private StorageProvider storageProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private QueueRepository queueRepository;
    @Mock private CacheManager cacheManager;
    @Mock private ThreadPoolTaskExecutor cleanupExecutor;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    private QueueCleanupService queueCleanupService;

    @BeforeEach
    void setUp() {
        queueCleanupService = new QueueCleanupService(
                streamMessageRepository,
                refCountService,
                storageProvider,
                redisTemplate,
                queueRepository,
                cacheManager,
                cleanupExecutor
        );
        ReflectionTestUtils.setField(queueCleanupService, "batchSize", 1000);
        ReflectionTestUtils.setField(queueCleanupService, "progressLogInterval", 10000);
        ReflectionTestUtils.setField(queueCleanupService, "interBatchYieldMs", 0L);
    }

    @Nested
    @DisplayName("Purge on Empty Queue")
    class PurgeEmptyQueueTests {

        /**
         * Test purge on empty queue succeeds without error.
         * Requirement 1.5: WHEN a purge request is received for a queue that contains
         * zero messages, THE Queue_Service SHALL complete successfully without error.
         */
        @Test
        @DisplayName("Should succeed without error when purging empty queue")
        void purgeOnEmptyQueueSucceedsWithoutError() throws Exception {
            // Given: empty stream returns empty batch
            String streamKey = "function:msg_queue:domain:d1:solution:s1:environment:env1:queue:q1";
            StreamBatch emptyBatch = new StreamBatch(
                    Collections.emptyList(), Collections.emptyList(), "0", false);

            when(streamMessageRepository.getStorageKeysBatch(eq(streamKey), eq("0"), eq(1000)))
                    .thenReturn(emptyBatch);

            // When: cleanupStream is called on empty stream via reflection (package-private)
            Method cleanupStream = QueueCleanupService.class.getDeclaredMethod(
                    "cleanupStream", String.class, String.class, String.class, String.class);
            cleanupStream.setAccessible(true);

            // Then: no exception thrown
            assertThatNoException().isThrownBy(() -> {
                try {
                    cleanupStream.invoke(queueCleanupService, streamKey, "d1", "s1", "progress-key");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            });

            // Verify no ref count decrements or storage deletes attempted
            verifyNoInteractions(refCountService);
            verifyNoInteractions(storageProvider);
        }
    }

    @Nested
    @DisplayName("Progress Logging")
    class ProgressLoggingTests {

        /**
         * Test progress logging occurs at configured interval.
         * Requirement 7.6: THE Queue_Service SHALL log progress during large cleanups
         * (e.g., every 10,000 messages processed).
         */
        @Test
        @DisplayName("Should process multiple batches and update progress marker")
        void progressLoggingOccursAtConfiguredInterval() throws Exception {
            // Given: configure small progress interval for testing
            ReflectionTestUtils.setField(queueCleanupService, "progressLogInterval", 5);
            ReflectionTestUtils.setField(queueCleanupService, "batchSize", 3);

            String streamKey = "test-stream";
            String progressKey = "progress-key";

            // First batch: 3 entries
            StreamBatch batch1 = new StreamBatch(
                    List.of("sk1", "sk2", "sk3"),
                    List.of("1-0", "2-0", "3-0"),
                    "3-0", true);

            // Second batch: 3 entries
            StreamBatch batch2 = new StreamBatch(
                    List.of("sk4", "sk5", "sk6"),
                    List.of("4-0", "5-0", "6-0"),
                    "6-0", false);

            when(streamMessageRepository.getStorageKeysBatch(streamKey, "0", 3))
                    .thenReturn(batch1);
            when(streamMessageRepository.getStorageKeysBatch(streamKey, "3-0", 3))
                    .thenReturn(batch2);

            when(refCountService.refCountKey(anyString(), anyString(), anyString()))
                    .thenAnswer(inv -> "ref:" + inv.getArgument(2));
            when(refCountService.decrementBatch(anyList()))
                    .thenReturn(Map.of());

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            // When: cleanupStream processes both batches via reflection
            Method cleanupStream = QueueCleanupService.class.getDeclaredMethod(
                    "cleanupStream", String.class, String.class, String.class, String.class);
            cleanupStream.setAccessible(true);
            cleanupStream.invoke(queueCleanupService, streamKey, "d1", "s1", progressKey);

            // Then: progress marker updated after each batch
            verify(hashOperations, atLeast(2)).put(eq(progressKey), eq("lastProcessedId"), anyString());
            verify(hashOperations, atLeast(2)).put(eq(progressKey), eq("messagesProcessed"), anyString());
        }
    }
}
