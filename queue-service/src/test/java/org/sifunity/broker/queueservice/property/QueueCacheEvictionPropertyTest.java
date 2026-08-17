// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.common.exception.QueueNotFoundException;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for queue cache eviction on deletion.
 *
 * <p><strong>Property 12: Queue deletion evicts queue cache</strong></p>
 * <p>For any queue that has been cached in {@code queue-metadata}, calling
 * {@code QueueService.deleteQueue(queueId)} SHALL evict the cache entry for
 * that queueId. A subsequent call to {@code getQueueForRouting(queueId)}
 * SHALL result in a cache miss (or {@code QueueNotFoundException}).</p>
 */
class QueueCacheEvictionPropertyTest {

    private QueueService queueService;
    private QueueRepository queueRepository;
    private StreamMessageRepository streamMessageRepository;
    private CacheManager cacheManager;

    @BeforeProperty
    void setUp() {
        queueRepository = mock(QueueRepository.class);
        streamMessageRepository = mock(StreamMessageRepository.class);
        RefCountService refCountService = mock(RefCountService.class);
        StorageProvider storageProvider = mock(StorageProvider.class);

        // Default stream mocks
        when(streamMessageRepository.messageCount(anyString())).thenReturn(0L);
        when(streamMessageRepository.streamKey(anyString(), anyString(), any(UUID.class), any(UUID.class)))
                .thenReturn("test-stream-key");
        when(streamMessageRepository.dlqStreamKey(anyString(), anyString(), any(UUID.class), any(UUID.class)))
                .thenReturn("test-dlq-key");
        when(streamMessageRepository.groupName(any(UUID.class)))
                .thenReturn("test-group");
        when(streamMessageRepository.getAllStorageKeys(anyString()))
                .thenReturn(Collections.emptyList());

        // Build real Caffeine cache
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

        // Spring context with caching proxy
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

    // ========== Property 12 ==========

    /**
     * Property 12: Queue deletion evicts queue cache
     *
     * <p><strong>Validates: Requirements 3.7, 5.3</strong></p>
     *
     * <p>Generates a random queueId, populates the {@code queue-metadata} cache
     * via {@code getQueueForRouting()}, calls {@code deleteQueue()}, then verifies
     * a subsequent {@code getQueueForRouting()} results in a cache miss
     * (repository queried again) or {@code QueueNotFoundException}.</p>
     */
    @Property(tries = 10)
    @Tag("Feature: caffeine-service-caching, Property 12: Queue deletion evicts queue cache")
    void queueDeletionEvictsQueueCache(@ForAll("queueIds") UUID queueId) {
        // Clear cache and reset mocks
        cacheManager.getCache("queue-metadata").clear();
        reset(queueRepository, streamMessageRepository);

        // Rebuild stream mocks after reset
        when(streamMessageRepository.messageCount(anyString())).thenReturn(0L);
        when(streamMessageRepository.streamKey(anyString(), anyString(), any(UUID.class), any(UUID.class)))
                .thenReturn("test-stream-key");
        when(streamMessageRepository.dlqStreamKey(anyString(), anyString(), any(UUID.class), any(UUID.class)))
                .thenReturn("test-dlq-key");
        when(streamMessageRepository.groupName(any(UUID.class)))
                .thenReturn("test-group");
        when(streamMessageRepository.getAllStorageKeys(anyString()))
                .thenReturn(Collections.emptyList());

        UUID ownerId = UUID.randomUUID();

        // Build Queue entity
        Queue queue = Queue.builder()
                .id(queueId)
                .name("test-queue-" + queueId.toString().substring(0, 8))
                .ownerId(ownerId)
                .polling("IMMEDIATE")
                .queueUri("test/queues/" + queueId + "/messages")
                .maxConcurrentConnections(1)
                .created(Instant.now())
                .maxRetries(3)
                .retryDelayMs(5000L)
                .build();

        // findById returns queue for initial cache population and for deleteQueue lookup
        when(queueRepository.findById(queueId)).thenReturn(Optional.of(queue));

        // Step 1: Populate cache via getQueueForRouting
        QueueDTO cached = queueService.getQueueForRouting(queueId);
        assertThat(cached).isNotNull();
        assertThat(cached.id()).isEqualTo(queueId);

        // Verify cache is populated (second call should NOT hit repo)
        QueueDTO cachedAgain = queueService.getQueueForRouting(queueId);
        assertThat(cachedAgain).isSameAs(cached);
        verify(queueRepository, times(1)).findById(queueId);

        // Step 2: Delete queue — should evict cache
        queueService.deleteQueue(queueId, "", "", ownerId);

        // Step 3: After deletion, findById returns empty — queue is gone
        when(queueRepository.findById(queueId)).thenReturn(Optional.empty());

        // Step 4: Subsequent getQueueForRouting must miss cache and hit repo,
        // which now returns empty → QueueNotFoundException
        assertThatThrownBy(() -> queueService.getQueueForRouting(queueId))
                .isInstanceOf(QueueNotFoundException.class);

        // Verify repo was called again after eviction (cache miss)
        // 1 for initial populate + 1 inside deleteQueue + 1 after eviction = 3
        verify(queueRepository, times(3)).findById(queueId);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UUID> queueIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}
