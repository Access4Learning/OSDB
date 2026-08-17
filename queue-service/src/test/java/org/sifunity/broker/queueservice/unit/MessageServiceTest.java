// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.common.exception.StorageException;
import org.sifunity.broker.common.util.TracingUtils;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.MessageStatus;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.metrics.QueueMetricsService;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageService with stream-based enqueue and ref count increment.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService Unit Tests")
class MessageServiceTest {

    @Mock private QueueRepository queueRepository;
    @Mock private StreamMessageRepository streamMessageRepository;
    @Mock private RefCountService refCountService;
    @Mock private StorageProvider storageProvider;
    @Mock private TracingUtils tracingUtils;
    @Mock private QueueMetricsService queueMetricsService;

    private MessageService messageService;

    private Queue testQueue;
    private UUID queueId;
    private UUID messageId;
    private UUID environmentId;
    private String domainId = "test-domain";
    private String solutionId = "test-solution";

    @BeforeEach
    void setUp() {
        queueId = UUID.randomUUID();
        messageId = UUID.randomUUID();
        environmentId = UUID.randomUUID();

        testQueue = new Queue();
        testQueue.setId(queueId);
        testQueue.setName("test-queue");
        testQueue.setOwnerId(environmentId);
        testQueue.setMaxRetries(3);
        testQueue.setRetryDelayMs(5000L);

        messageService = new MessageService(
                queueRepository, streamMessageRepository, refCountService,
                storageProvider, tracingUtils, new com.fasterxml.jackson.databind.ObjectMapper(), "",
                queueMetricsService);

        lenient().when(tracingUtils.traceOperation(anyString(), anyMap(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
    }

    @Nested
    @DisplayName("enqueueMessage() Tests")
    class EnqueueMessageTests {

        @Test
        @DisplayName("Should enqueue message with provided ID")
        void shouldEnqueueWithProvidedId() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.streamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.enqueue(anyString(), anyString(), any(), anyString(), any(), any()))
                    .thenReturn("1234567890-0");
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.increment(anyString())).thenReturn(1L);
            when(queueRepository.save(any(Queue.class))).thenReturn(testQueue);

            MessageMetadata result = messageService.enqueueMessage(
                    queueId, messageId, domainId, solutionId, environmentId,
                    environmentId, "zone", "context", "event", "StudentPersonal", "{\"data\":\"test\"}");

            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo(messageId);
            assertThat(result.getQueueId()).isEqualTo(queueId);
            assertThat(result.getStatus()).isEqualTo(MessageStatus.PENDING);
            assertThat(result.getDeliveryAttempts()).isEqualTo(0);
            assertThat(result.getStorageKey()).isNotNull();

            verify(storageProvider).store(anyString(), any());
            verify(streamMessageRepository).enqueue(anyString(), anyString(), eq(messageId), anyString(), any(), any());
            verify(refCountService).increment(anyString());
        }

        @Test
        @DisplayName("Should generate message ID when not provided")
        void shouldGenerateIdWhenNull() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.streamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.enqueue(anyString(), anyString(), any(), anyString(), any(), any()))
                    .thenReturn("1234567890-0");
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.increment(anyString())).thenReturn(1L);
            when(queueRepository.save(any(Queue.class))).thenReturn(testQueue);

            MessageMetadata result = messageService.enqueueMessage(
                    queueId, null, domainId, solutionId, environmentId,
                    environmentId, "zone", "context", "event", "StudentPersonal", "{\"data\":\"test\"}");

            assertThat(result.getMessageId()).isNotNull();
        }

        @Test
        @DisplayName("Should throw when queue not found")
        void shouldThrowWhenQueueNotFound() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.enqueueMessage(
                    queueId, messageId, domainId, solutionId, environmentId,
                    environmentId, "zone", "context", "event", "StudentPersonal", "{\"data\":\"test\"}"))
                    .isInstanceOf(QueueNotFoundException.class);

            verifyNoInteractions(storageProvider);
            verifyNoInteractions(streamMessageRepository);
        }

        @Test
        @DisplayName("Should throw when storage fails")
        void shouldThrowWhenStorageFails() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.increment(anyString())).thenReturn(1L);
            doThrow(new RuntimeException("Storage failure"))
                    .when(storageProvider).store(anyString(), any());

            assertThatThrownBy(() -> messageService.enqueueMessage(
                    queueId, messageId, domainId, solutionId, environmentId,
                    environmentId, "zone", "context", "event", "StudentPersonal", "{\"data\":\"test\"}"))
                    .isInstanceOf(StorageException.class);
        }

        @Test
        @DisplayName("Should update lastModified on queue after enqueue")
        void shouldUpdateLastModified() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.streamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.enqueue(anyString(), anyString(), any(), anyString(), any(), any()))
                    .thenReturn("1234567890-0");
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.increment(anyString())).thenReturn(1L);
            when(queueRepository.save(any(Queue.class))).thenReturn(testQueue);

            messageService.enqueueMessage(
                    queueId, messageId, domainId, solutionId, environmentId,
                    environmentId, "zone", "context", "event", "StudentPersonal", "{\"data\":\"test\"}");

            // save called for lastModified update
            verify(queueRepository).save(testQueue);
            assertThat(testQueue.getLastModified()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Fan-Out (Ref Count) Tests")
    class FanOutTests {

        @Test
        @DisplayName("Should skip payload write when refCount.increment() returns > 1 (fan-out)")
        void refCountGreaterThanOne_skipsPayloadWrite() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.streamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.enqueue(anyString(), anyString(), any(), anyString(), any(), any()))
                    .thenReturn("1234567890-0");
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.increment(anyString())).thenReturn(2L); // > 1 → fan-out
            when(queueRepository.save(any(Queue.class))).thenReturn(testQueue);

            MessageMetadata result = messageService.enqueueMessage(
                    queueId, messageId, domainId, solutionId, environmentId,
                    environmentId, "zone", "context", "event", "StudentPersonal", "{\"data\":\"test\"}");

            assertThat(result).isNotNull();
            // storageProvider.store() should NOT be called
            verify(storageProvider, never()).store(anyString(), any());
            // Stream entry should still be created
            verify(streamMessageRepository).enqueue(anyString(), anyString(), eq(messageId), anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("Stream Entry Failure Tests")
    class StreamEntryFailureTests {

        @Test
        @DisplayName("Should cleanup (decrement + conditional delete) when stream entry creation fails")
        void streamEntryFails_cleansUp() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.streamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.increment(anyString())).thenReturn(1L);
            when(streamMessageRepository.enqueue(anyString(), anyString(), any(), anyString(), any(), any()))
                    .thenThrow(new RuntimeException("Redis connection failed"));
            when(refCountService.decrementAndGet(anyString())).thenReturn(0L);

            assertThatThrownBy(() -> messageService.enqueueMessage(
                    queueId, messageId, domainId, solutionId, environmentId,
                    environmentId, "zone", "context", "event", "StudentPersonal", "{\"data\":\"test\"}"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to enqueue message to stream");

            // Verify cleanup: decrement + delete payload (since remaining == 0)
            verify(refCountService).decrementAndGet("ref-key");
            verify(storageProvider).delete(anyString());
        }

        @Test
        @DisplayName("Should still throw when stream entry fails AND cleanup throws")
        void streamEntryFails_cleanupAlsoFails_stillThrows() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.streamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.increment(anyString())).thenReturn(1L);
            when(streamMessageRepository.enqueue(anyString(), anyString(), any(), anyString(), any(), any()))
                    .thenThrow(new RuntimeException("Redis connection failed"));
            when(refCountService.decrementAndGet(anyString()))
                    .thenThrow(new RuntimeException("Redis also down"));

            assertThatThrownBy(() -> messageService.enqueueMessage(
                    queueId, messageId, domainId, solutionId, environmentId,
                    environmentId, "zone", "context", "event", "StudentPersonal", "{\"data\":\"test\"}"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to enqueue message to stream");
        }
    }

    @Nested
    @DisplayName("SIF Headers Serialization Tests")
    class SifHeadersSerializationTests {

        @Test
        @DisplayName("Should gracefully handle SIF headers serialization failure (warn, no throw)")
        void serializationFailure_graceful() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.streamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.increment(anyString())).thenReturn(1L);
            when(queueRepository.save(any(Queue.class))).thenReturn(testQueue);

            // Use a custom ObjectMapper that will fail on serialization
            com.fasterxml.jackson.databind.ObjectMapper brokenMapper =
                    mock(com.fasterxml.jackson.databind.ObjectMapper.class);
            try {
                when(brokenMapper.writeValueAsString(any()))
                        .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Serialization error") {});
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // won't happen in mock setup
            }

            MessageService serviceWithBrokenMapper = new MessageService(
                    queueRepository, streamMessageRepository, refCountService,
                    storageProvider, tracingUtils, brokenMapper, "",
                    queueMetricsService);

            // enqueue should pass null for headersJson when serialization fails
            when(streamMessageRepository.enqueue(anyString(), anyString(), any(), anyString(), any(), isNull()))
                    .thenReturn("1234567890-0");

            java.util.Map<String, String> headers = java.util.Map.of("key", "value");

            MessageMetadata result = serviceWithBrokenMapper.enqueueMessage(
                    queueId, messageId, domainId, solutionId, environmentId,
                    environmentId, "zone", "context", "event", "StudentPersonal", "{\"data\":\"test\"}",
                    headers);

            assertThat(result).isNotNull();
            // Stream entry created with null headersJson
            verify(streamMessageRepository).enqueue(anyString(), anyString(), eq(messageId), anyString(), any(), isNull());
        }
    }

    @Nested
    @DisplayName("Metrics Recording Tests")
    class MetricsRecordingTests {

        @Test
        @DisplayName("Should not throw when metrics recording fails (graceful)")
        void metricsException_graceful() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.streamKey(anyString(), anyString(), any(), any())).thenReturn("stream-key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.enqueue(anyString(), anyString(), any(), anyString(), any(), any()))
                    .thenReturn("1234567890-0");
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.increment(anyString())).thenReturn(1L);
            when(queueRepository.save(any(Queue.class))).thenReturn(testQueue);

            doThrow(new RuntimeException("Metrics failure"))
                    .when(queueMetricsService).recordEnqueue(anyString(), anyString());

            // Should not throw despite metrics failure
            MessageMetadata result = messageService.enqueueMessage(
                    queueId, messageId, domainId, solutionId, environmentId,
                    environmentId, "zone", "context", "event", "StudentPersonal", "{\"data\":\"test\"}");

            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo(messageId);
        }
    }
}
