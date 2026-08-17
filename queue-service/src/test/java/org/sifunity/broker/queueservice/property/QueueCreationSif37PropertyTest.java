// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.QueueService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 11: Queue creation SIF 3.7 fields — created queue has all fields with correct defaults.
 *
 * <p>Validates: Requirements 6.1, 6.3</p>
 */
class QueueCreationSif37PropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * **Validates: Requirements 6.1, 6.3**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 11: Queue creation SIF 3.7 fields")
    void createdQueueHasAllSif37FieldsWithCorrectDefaults(
            @ForAll @NotBlank String name,
            @ForAll("uuids") UUID ownerId,
            @ForAll @IntRange(min = 1, max = 10) int maxConcurrentConnections) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        Instant beforeCreate = Instant.now();

        when(qr.save(any(Queue.class))).thenAnswer(inv -> {
            Queue q = inv.getArgument(0);
            Queue saved = new Queue();
            saved.setId(UUID.randomUUID());
            saved.setName(q.getName());
            saved.setOwnerId(q.getOwnerId());
            saved.setPolling(q.getPolling() != null ? q.getPolling() : "IMMEDIATE");
            saved.setQueueUri(q.getQueueUri());
            saved.setMaxConcurrentConnections(q.getMaxConcurrentConnections() != null ? q.getMaxConcurrentConnections() : 1);
            saved.setMaxRetries(q.getMaxRetries() != null ? q.getMaxRetries() : 3);
            saved.setRetryDelayMs(q.getRetryDelayMs() != null ? q.getRetryDelayMs() : 5000L);
            saved.setCreated(Instant.now());
            return saved;
        });
        when(smr.messageCount(anyString())).thenReturn(0L);
        when(smr.streamKey(anyString(), anyString(), any(), any())).thenReturn("key");

        QueueService svc = new QueueService(qr, smr, rcs, sp, "http://localhost");

        QueueDTO result = svc.createQueue(name, ownerId, null, null,
                maxConcurrentConnections, "domain", "solution");

        // All SIF 3.7 fields present
        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo(name);
        assertThat(result.ownerId()).isEqualTo(ownerId);
        assertThat(result.polling()).isEqualTo("IMMEDIATE");
        assertThat(result.maxConcurrentConnections()).isEqualTo(maxConcurrentConnections);
        assertThat(result.created()).isNotNull();
        assertThat(result.created()).isAfterOrEqualTo(beforeCreate);
        assertThat(result.messageCount()).isEqualTo(0L);
        // maxRetries/retryDelayMs are internal fields — populated in the DTO but suppressed
        // from JSON serialization via @JsonIgnore on the accessor (SIF 3.7 wire compliance).
        assertThat(result.maxRetries()).isEqualTo(3);
        assertThat(result.retryDelayMs()).isEqualTo(5000L);
    }

    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 11b: Default maxConcurrentConnections is 1")
    void defaultMaxConcurrentConnectionsIsOne(
            @ForAll @NotBlank String name,
            @ForAll("uuids") UUID ownerId) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        when(qr.save(any(Queue.class))).thenAnswer(inv -> {
            Queue q = inv.getArgument(0);
            Queue saved = new Queue();
            saved.setId(UUID.randomUUID());
            saved.setName(q.getName());
            saved.setOwnerId(q.getOwnerId());
            saved.setPolling("IMMEDIATE");
            saved.setMaxConcurrentConnections(q.getMaxConcurrentConnections());
            saved.setMaxRetries(q.getMaxRetries());
            saved.setRetryDelayMs(q.getRetryDelayMs());
            saved.setCreated(Instant.now());
            return saved;
        });
        when(smr.messageCount(anyString())).thenReturn(0L);
        when(smr.streamKey(anyString(), anyString(), any(), any())).thenReturn("key");

        QueueService svc = new QueueService(qr, smr, rcs, sp, "");

        // null maxConcurrentConnections should default to 1
        QueueDTO result = svc.createQueue(name, ownerId, null, null, null, "d", "s");

        assertThat(result.maxConcurrentConnections()).isEqualTo(1);
    }
}
