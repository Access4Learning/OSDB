package org.sifunity.broker.queueservice.unit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sifunity.broker.queueservice.redis.RedisKeyManager;
import org.sifunity.broker.queueservice.service.CascadeCleanupService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CascadeCleanupService.
 * Verifies SCAN patterns, UNLINK calls, and StorageProvider interactions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CascadeCleanupService Unit Tests")
class CascadeCleanupServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StorageProvider storageProvider;
    @Mock private RedisConnectionFactory connectionFactory;
    @Mock private RedisConnection redisConnection;
    @Mock private RedisKeyCommands keyCommands;

    private RedisKeyManager keyManager;
    private MeterRegistry meterRegistry;
    private CascadeCleanupService cascadeCleanupService;

    @BeforeEach
    void setUp() {
        keyManager = new RedisKeyManager();
        meterRegistry = new SimpleMeterRegistry();

        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.keyCommands()).thenReturn(keyCommands);

        cascadeCleanupService = new CascadeCleanupService(
                redisTemplate, storageProvider, keyManager, meterRegistry, 2);

        ReflectionTestUtils.setField(cascadeCleanupService, "scanBatchSize", 1000);
        ReflectionTestUtils.setField(cascadeCleanupService, "interBatchDelayMs", 0L);
    }

    @Nested
    @DisplayName("submitDomainCleanup")
    class DomainCleanupTests {

        @Test
        @DisplayName("Should SCAN with correct domain patterns and call deleteByPrefix")
        void shouldScanWithCorrectDomainPatterns() throws Exception {
            String domainId = "state-a-doe";

            Cursor<byte[]> newPatternCursor = mockCursorWithKeys(
                    List.of("domain:state-a-doe:solution:prod:function:msg_queue:env:e1:queue:q1".getBytes()));
            Cursor<byte[]> legacyPatternCursor = mockCursorWithKeys(
                    List.of("function:msg_queue:domain:state-a-doe:solution:prod:environment:e1:queue:q1".getBytes()));

            when(keyCommands.scan(any(ScanOptions.class)))
                    .thenReturn(newPatternCursor)
                    .thenReturn(legacyPatternCursor);

            when(keyCommands.unlink(any(byte[][].class))).thenReturn(1L);

            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(inv -> { latch.countDown(); return 5L; })
                    .when(storageProvider).deleteByPrefix("state-a-doe/");

            cascadeCleanupService.submitDomainCleanup(domainId);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            // Allow cleanup thread to finish recording metrics after deleteByPrefix returns
            Thread.sleep(100);

            // Verify SCAN was called twice (new + legacy patterns)
            verify(keyCommands, times(2)).scan(any(ScanOptions.class));

            // Verify UNLINK was called for discovered keys
            verify(keyCommands, times(2)).unlink(any(byte[][].class));

            // Verify storage deleteByPrefix called with domain prefix
            verify(storageProvider).deleteByPrefix("state-a-doe/");

            // Verify metrics recorded
            assertThat(meterRegistry.find("cascade.cleanup.duration")
                    .tag("scope", "domain").timer()).isNotNull();
            assertThat(meterRegistry.find("cascade.cleanup.keys.deleted")
                    .tag("scope", "domain").counter()).isNotNull();
            assertThat(meterRegistry.find("cascade.cleanup.storage.deleted")
                    .tag("scope", "domain").counter()).isNotNull();
        }

        @Test
        @DisplayName("Should handle empty SCAN results gracefully")
        void shouldHandleEmptyScanResults() throws Exception {
            String domainId = "empty-domain";

            Cursor<byte[]> emptyCursor1 = mockCursorWithKeys(Collections.emptyList());
            Cursor<byte[]> emptyCursor2 = mockCursorWithKeys(Collections.emptyList());

            when(keyCommands.scan(any(ScanOptions.class)))
                    .thenReturn(emptyCursor1)
                    .thenReturn(emptyCursor2);

            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(inv -> { latch.countDown(); return 0L; })
                    .when(storageProvider).deleteByPrefix("empty-domain/");

            cascadeCleanupService.submitDomainCleanup(domainId);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            // UNLINK should never be called for empty results
            verify(keyCommands, never()).unlink(any(byte[][].class));
            verify(storageProvider).deleteByPrefix("empty-domain/");
        }

        @Test
        @DisplayName("Should continue cleanup if SCAN throws exception")
        void shouldContinueIfScanThrows() throws Exception {
            String domainId = "error-domain";

            when(keyCommands.scan(any(ScanOptions.class)))
                    .thenThrow(new RuntimeException("Redis connection lost"));

            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(inv -> { latch.countDown(); return 0L; })
                    .when(storageProvider).deleteByPrefix("error-domain/");

            cascadeCleanupService.submitDomainCleanup(domainId);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            // Storage cleanup should still be attempted
            verify(storageProvider).deleteByPrefix("error-domain/");
        }
    }

    @Nested
    @DisplayName("submitSolutionCleanup")
    class SolutionCleanupTests {

        @Test
        @DisplayName("Should SCAN with correct solution patterns and call deleteByPrefix")
        void shouldScanWithCorrectSolutionPatterns() throws Exception {
            String domainId = "state-a-doe";
            String solutionId = "prod";

            Cursor<byte[]> newPatternCursor = mockCursorWithKeys(
                    List.of("domain:state-a-doe:solution:prod:function:msg_queue:env:e1:queue:q1".getBytes()));
            Cursor<byte[]> legacyPatternCursor = mockCursorWithKeys(Collections.emptyList());

            when(keyCommands.scan(any(ScanOptions.class)))
                    .thenReturn(newPatternCursor)
                    .thenReturn(legacyPatternCursor);

            when(keyCommands.unlink(any(byte[][].class))).thenReturn(1L);

            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(inv -> { latch.countDown(); return 3L; })
                    .when(storageProvider).deleteByPrefix("state-a-doe/prod/");

            cascadeCleanupService.submitSolutionCleanup(domainId, solutionId);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            // Allow cleanup thread to finish recording metrics after deleteByPrefix returns
            Thread.sleep(100);

            // Verify SCAN called twice (new + legacy)
            verify(keyCommands, times(2)).scan(any(ScanOptions.class));

            // Verify UNLINK called for the one key found
            verify(keyCommands, times(1)).unlink(any(byte[][].class));

            // Verify storage deleteByPrefix with solution prefix
            verify(storageProvider).deleteByPrefix("state-a-doe/prod/");

            // Verify metrics
            assertThat(meterRegistry.find("cascade.cleanup.duration")
                    .tag("scope", "solution").timer()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Batch processing")
    class BatchProcessingTests {

        @Test
        @DisplayName("Should batch UNLINK calls when keys exceed batch size")
        void shouldBatchUnlinkCalls() throws Exception {
            ReflectionTestUtils.setField(cascadeCleanupService, "scanBatchSize", 2);

            String domainId = "big-domain";

            // Return 3 keys — should trigger 2 UNLINK calls (batch of 2 + batch of 1)
            List<byte[]> keys = List.of(
                    "domain:big-domain:key1".getBytes(),
                    "domain:big-domain:key2".getBytes(),
                    "domain:big-domain:key3".getBytes());
            Cursor<byte[]> cursor = mockCursorWithKeys(keys);
            Cursor<byte[]> legacyCursor = mockCursorWithKeys(Collections.emptyList());

            when(keyCommands.scan(any(ScanOptions.class)))
                    .thenReturn(cursor)
                    .thenReturn(legacyCursor);

            when(keyCommands.unlink(any(byte[][].class))).thenReturn(2L).thenReturn(1L);

            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(inv -> { latch.countDown(); return 0L; })
                    .when(storageProvider).deleteByPrefix("big-domain/");

            cascadeCleanupService.submitDomainCleanup(domainId);

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            // Should have 2 UNLINK calls for the new pattern (batch of 2 + remainder of 1)
            verify(keyCommands, times(2)).unlink(any(byte[][].class));
        }
    }

    /**
     * Helper to create a mock Cursor that iterates over the given keys.
     */
    @SuppressWarnings("unchecked")
    private Cursor<byte[]> mockCursorWithKeys(List<byte[]> keys) {
        Cursor<byte[]> cursor = mock(Cursor.class);
        Iterator<byte[]> iterator = keys.iterator();

        when(cursor.hasNext()).thenAnswer(inv -> iterator.hasNext());
        when(cursor.next()).thenAnswer(inv -> iterator.next());

        return cursor;
    }
}
