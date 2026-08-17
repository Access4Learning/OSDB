// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.config.CacheProperties;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.QueueService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for queue-metadata cache round-trip and getQueueDetails bypass.
 *
 * <p><strong>Property 6: Queue-metadata cache round-trip</strong></p>
 * <p>For any queueId UUID, calling {@code QueueService.getQueueForRouting(queueId)}
 * twice in succession (within TTL) SHALL return the same {@code QueueDTO}, and the
 * underlying {@code QueueRepository.findById()} SHALL be invoked exactly once.</p>
 *
 * <p><strong>Property 9: getQueueDetails always hits database</strong></p>
 * <p>For any queueId UUID, every call to {@code QueueService.getQueueDetails(queueId)}
 * SHALL invoke {@code QueueRepository.findById()}, regardless of whether the
 * queue-metadata cache contains an entry for that queueId.</p>
 */
class QueueCacheRoundTripPropertyTest {

    private QueueService queueService;
    private QueueRepository queueRepository;
    private StreamMessageRepository streamMessageRepository;
    private CacheManager cacheManager;

    @BeforeProperty
    void setUp() {
        // Create mocks for all QueueService dependencies
        queueRepository = mock(QueueRepository.class);
        streamMessageRepository = mock(StreamMessageRepository.class);
        RefCountService refCountService = mock(RefCountService.class);
        StorageProvider storageProvider = mock(StorageProvider.class);

        // StreamMessageRepository.messageCount() returns 0 for any stream key
        when(streamMessageRepository.messageCount(anyString())).thenReturn(0L);
        when(streamMessageRepository.streamKey(anyString(), anyString(), any(UUID.class), any(UUID.class)))
                .thenReturn("test-stream-key");

        // Build real CaffeineCacheManager with queue-metadata cache
        CacheProperties props = new CacheProperties();
        SimpleCacheManager simpleCacheManager = new SimpleCacheManager();
        simpleCacheManager.setCaches(List.of(
                new CaffeineCache("queue-metadata", Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(props.getQueueMetadata().getTtlSeconds()))
                        .maximumSize(props.getQueueMetadata().getMaxSize())
                        .recordStats()
                        .build())
        ));
        simpleCacheManager.afterPropertiesSet();
        cacheManager = simpleCacheManager;

        // Use Spring application context to get proxied QueueService with caching
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(CacheManager.class, () -> cacheManager);
        ctx.registerBean(QueueRepository.class, () -> queueRepository);
        ctx.registerBean(StreamMessageRepository.class, () -> streamMessageRepository);
        ctx.registerBean(RefCountService.class, () -> refCountService);
        ctx.registerBean(StorageProvider.class, () -> storageProvider);
        ctx.register(CachingEnablerConfig.class);
        ctx.registerBean(QueueService.class,
                () -> new QueueService(queueRepository, streamMessageRepository,
                        refCountService, storageProvider, ""));
        ctx.refresh();

        queueService = ctx.getBean(QueueService.class);
    }

    @Configuration
    @EnableCaching
    static class CachingEnablerConfig {
    }

    // ========== Property 6 ==========

    /**
     * Property 6: Queue-metadata cache round-trip
     *
     * <p><strong>Validates: Requirements 3.2, 3.3</strong></p>
     *
     * <p>Generates random queueId UUIDs, mocks {@code QueueRepository.findById()}
     * to return a Queue entity, calls {@code getQueueForRouting()} twice within TTL,
     * and verifies the repository is invoked exactly once while both calls return
     * the same QueueDTO.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 6: Queue-metadata cache round-trip")
    void queueMetadataCacheRoundTrip(@ForAll("queueIds") UUID queueId) {
        // Clear cache before each trial to isolate property runs
        cacheManager.getCache("queue-metadata").clear();
        reset(queueRepository);

        // Rebuild stream mock after reset
        when(streamMessageRepository.messageCount(anyString())).thenReturn(0L);
        when(streamMessageRepository.streamKey(anyString(), anyString(), any(UUID.class), any(UUID.class)))
                .thenReturn("test-stream-key");

        // Build a Queue entity for the mock to return
        Queue queue = Queue.builder()
                .id(queueId)
                .name("test-queue-" + queueId.toString().substring(0, 8))
                .ownerId(UUID.randomUUID())
                .polling("IMMEDIATE")
                .queueUri("test/queues/" + queueId + "/messages")
                .maxConcurrentConnections(1)
                .created(Instant.now())
                .maxRetries(3)
                .retryDelayMs(5000L)
                .build();

        when(queueRepository.findById(queueId))
                .thenReturn(Optional.of(queue));

        // First call — cache miss, hits repository
        QueueDTO first = queueService.getQueueForRouting(queueId);

        // Second call — cache hit, should NOT hit repository
        QueueDTO second = queueService.getQueueForRouting(queueId);

        // Repository invoked exactly once (first call only)
        verify(queueRepository, times(1)).findById(queueId);

        // Both calls return same QueueDTO instance
        assertThat(first).isSameAs(second);
        assertThat(first).isNotNull();
        assertThat(first.id()).isEqualTo(queueId);
    }

    // ========== Property 9 ==========

    /**
     * Property 9: getQueueDetails always hits database
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     *
     * <p>Generates a random queueId, pre-populates the queue-metadata cache via
     * {@code getQueueForRouting()}, then calls {@code getQueueDetails()} N times
     * and verifies {@code QueueRepository.findById()} invocation count equals N
     * (plus 1 for the initial routing call).</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 9: getQueueDetails always hits database")
    void getQueueDetailsAlwaysHitsDatabase(
            @ForAll("queueIds") UUID queueId,
            @ForAll @IntRange(min = 1, max = 5) int callCount) {
        // Clear cache before each trial to isolate property runs
        cacheManager.getCache("queue-metadata").clear();
        reset(queueRepository);

        // Rebuild stream mock after reset
        when(streamMessageRepository.messageCount(anyString())).thenReturn(0L);
        when(streamMessageRepository.streamKey(anyString(), anyString(), any(UUID.class), any(UUID.class)))
                .thenReturn("test-stream-key");

        // Build a Queue entity for the mock to return
        Queue queue = Queue.builder()
                .id(queueId)
                .name("test-queue-" + queueId.toString().substring(0, 8))
                .ownerId(UUID.randomUUID())
                .polling("IMMEDIATE")
                .queueUri("test/queues/" + queueId + "/messages")
                .maxConcurrentConnections(1)
                .created(Instant.now())
                .maxRetries(3)
                .retryDelayMs(5000L)
                .build();

        when(queueRepository.findById(queueId))
                .thenReturn(Optional.of(queue));

        // Pre-populate cache via getQueueForRouting()
        queueService.getQueueForRouting(queueId);

        // Reset invocation count so we only count getQueueDetails() calls
        clearInvocations(queueRepository);

        // Call getQueueDetails() N times — each must hit the database
        for (int i = 0; i < callCount; i++) {
            QueueDTO result = queueService.getQueueDetails(queueId);
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(queueId);
        }

        // Repository invocation count must equal N (every call hits DB)
        verify(queueRepository, times(callCount)).findById(queueId);
    }

    // ========== Arbitraries ==========

    /**
     * Generates random UUIDs for queueId keys.
     */
    @Provide
    Arbitrary<UUID> queueIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}
