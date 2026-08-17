// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.NotBlank;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.QueueService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for QueueService with XLEN-based messageCount and SIF 3.7 fields.
 */
class QueueServicePropertiesTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    /**
     * Property: Queue creation returns valid DTO with SIF 3.7 fields.
     */
    @Property(tries = 50)
    @Label("Queue creation returns valid DTO")
    void queueCreationReturnsValidDTO(
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
            saved.setPolling(q.getPolling() != null ? q.getPolling() : "IMMEDIATE");
            saved.setMaxConcurrentConnections(q.getMaxConcurrentConnections() != null ? q.getMaxConcurrentConnections() : 1);
            saved.setMaxRetries(q.getMaxRetries() != null ? q.getMaxRetries() : 3);
            saved.setRetryDelayMs(q.getRetryDelayMs() != null ? q.getRetryDelayMs() : 5000L);
            saved.setCreated(Instant.now());
            return saved;
        });
        when(smr.messageCount(anyString())).thenReturn(0L);
        when(smr.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");

        QueueService svc = new QueueService(qr, smr, rcs, sp, "");

        QueueDTO result = svc.createQueue(name, ownerId, null, null);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo(name);
        assertThat(result.ownerId()).isEqualTo(ownerId);
        assertThat(result.messageCount()).isEqualTo(0L);
    }

    /**
     * Property: Queue query returns correct messageCount via XLEN.
     */
    @Property(tries = 50)
    @Label("Queue query returns correct messageCount via XLEN")
    void queueQueryReturnsCorrectMessageCount(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        Queue queue = new Queue();
        queue.setId(queueId);
        queue.setName("test");
        queue.setOwnerId(ownerId);
        queue.setPolling("IMMEDIATE");
        queue.setMaxConcurrentConnections(1);
        queue.setCreated(Instant.now());
        queue.setMaxRetries(3);
        queue.setRetryDelayMs(5000L);

        when(qr.findById(queueId)).thenReturn(Optional.of(queue));
        when(smr.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
        when(smr.messageCount("key")).thenReturn(42L);

        QueueService svc = new QueueService(qr, smr, rcs, sp, "");

        QueueDTO result = svc.getQueue(queueId);

        assertThat(result.messageCount()).isEqualTo(42L);
    }
}
