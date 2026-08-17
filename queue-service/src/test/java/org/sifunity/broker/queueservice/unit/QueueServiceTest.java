// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.QueueService;
import org.sifunity.broker.queueservice.service.QueueService.QueueStatistics;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueueService with XLEN-based messageCount and SIF 3.7 fields.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueService Unit Tests")
class QueueServiceTest {

    @Mock private QueueRepository queueRepository;
    @Mock private StreamMessageRepository streamMessageRepository;
    @Mock private RefCountService refCountService;
    @Mock private StorageProvider storageProvider;

    private QueueService queueService;

    private UUID queueId;
    private UUID ownerId;
    private Queue queue;

    @BeforeEach
    void setUp() {
        queueId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        queue = Queue.builder()
                .id(queueId)
                .name("test-queue")
                .ownerId(ownerId)
                .polling("IMMEDIATE")
                .maxConcurrentConnections(1)
                .created(Instant.now())
                .maxRetries(3)
                .retryDelayMs(5000L)
                .build();

        queueService = new QueueService(
                queueRepository, streamMessageRepository, refCountService, storageProvider, "");
    }

    @Nested
    @DisplayName("Create Queue Tests")
    class CreateQueueTests {

        @Test
        @DisplayName("Should create queue with SIF 3.7 fields")
        void shouldCreateQueueWithSif37Fields() {
            when(queueRepository.save(any(Queue.class))).thenReturn(queue);
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.messageCount(anyString())).thenReturn(0L);

            QueueDTO result = queueService.createQueue("test-queue", ownerId, 3, 5000L, 2, "domain", "solution");

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("test-queue");
            assertThat(result.ownerId()).isEqualTo(ownerId);
            assertThat(result.messageCount()).isEqualTo(0L);

            verify(queueRepository, atLeastOnce()).save(any(Queue.class));
        }

        @Test
        @DisplayName("Should create queue with default values")
        void shouldCreateQueueWithDefaults() {
            when(queueRepository.save(any(Queue.class))).thenReturn(queue);
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.messageCount(anyString())).thenReturn(0L);

            QueueDTO result = queueService.createQueue("test-queue", ownerId, null, null);

            assertThat(result).isNotNull();
            // maxRetries/retryDelayMs are internal fields, nulled in the SIF 3.7 DTO wire format.
            // Verify the defaults were applied to the persisted entity instead. (createQueue saves
            // twice — once to obtain the id, once to set the queueUri — so assert the first-saved entity.)
            ArgumentCaptor<Queue> queueCaptor = ArgumentCaptor.forClass(Queue.class);
            verify(queueRepository, atLeastOnce()).save(queueCaptor.capture());
            Queue persisted = queueCaptor.getAllValues().get(0);
            assertThat(persisted.getMaxRetries()).isEqualTo(3);
            assertThat(persisted.getRetryDelayMs()).isEqualTo(5000L);
        }
    }

    @Nested
    @DisplayName("Get Queue Tests")
    class GetQueueTests {

        @Test
        @DisplayName("Should get queue with XLEN-based messageCount")
        void shouldGetQueueWithXlenMessageCount() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(queue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.messageCount("stream-key")).thenReturn(5L);

            QueueDTO result = queueService.getQueue(queueId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(queueId);
            assertThat(result.name()).isEqualTo("test-queue");
            assertThat(result.messageCount()).isEqualTo(5L);
        }

        @Test
        @DisplayName("Should throw when queue not found")
        void shouldThrowWhenQueueNotFound() {
            UUID nonExistentId = UUID.randomUUID();
            when(queueRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> queueService.getQueue(nonExistentId))
                    .isInstanceOf(QueueNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Delete Queue Tests")
    class DeleteQueueTests {

        @Test
        @DisplayName("Should delete queue and stream resources")
        void shouldDeleteQueueAndStreams() {
            UUID envId = ownerId;
            String streamKey = "stream-key";
            String dlqKey = "stream-key:dlq";
            String groupName = "group:" + queueId;

            when(queueRepository.findById(queueId)).thenReturn(Optional.of(queue));
            when(streamMessageRepository.streamKey("domain", "solution", envId, queueId)).thenReturn(streamKey);
            when(streamMessageRepository.dlqStreamKey("domain", "solution", envId, queueId)).thenReturn(dlqKey);
            when(streamMessageRepository.groupName(queueId)).thenReturn(groupName);
            when(streamMessageRepository.getAllStorageKeys(anyString())).thenReturn(Collections.emptyList());

            queueService.deleteQueue(queueId, "domain", "solution", envId);

            verify(streamMessageRepository).destroyConsumerGroup(streamKey, groupName);
            verify(streamMessageRepository).deleteStream(streamKey);
            verify(streamMessageRepository).destroyConsumerGroup(dlqKey, groupName);
            verify(streamMessageRepository).deleteStream(dlqKey);
            verify(queueRepository).delete(queue);
        }

        @Test
        @DisplayName("Should throw when deleting non-existent queue")
        void shouldThrowWhenDeletingNonExistent() {
            UUID nonExistentId = UUID.randomUUID();
            when(queueRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> queueService.deleteQueue(nonExistentId))
                    .isInstanceOf(QueueNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Get Queue Statistics Tests")
    class GetQueueStatisticsTests {

        @Test
        @DisplayName("Should get statistics using XLEN and XPENDING")
        void shouldGetStatistics() {
            UUID envId = ownerId;
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(queue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("dlq-key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.messageCount("stream-key")).thenReturn(10L);
            when(streamMessageRepository.pendingCount("stream-key", "group:" + queueId)).thenReturn(3L);
            when(streamMessageRepository.messageCount("dlq-key")).thenReturn(2L);

            QueueStatistics result = queueService.getQueueStatistics(queueId, "domain", "solution", envId);

            assertThat(result.queueId()).isEqualTo(queueId);
            assertThat(result.pendingCount()).isEqualTo(10L);
            assertThat(result.deliveredCount()).isEqualTo(3L);
            assertThat(result.deadLetterCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Should return zero counts for empty queue")
        void shouldReturnZeroCounts() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(queue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("dlq-key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.messageCount(anyString())).thenReturn(0L);
            when(streamMessageRepository.pendingCount(anyString(), anyString())).thenReturn(0L);

            QueueStatistics result = queueService.getQueueStatistics(queueId, "", "", ownerId);

            assertThat(result.pendingCount()).isEqualTo(0L);
            assertThat(result.deliveredCount()).isEqualTo(0L);
            assertThat(result.deadLetterCount()).isEqualTo(0L);
        }
    }
}
