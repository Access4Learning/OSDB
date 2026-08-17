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
import org.sifunity.broker.common.util.TracingUtils;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.MessageStatus;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.metrics.QueueMetricsService;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeliveryService with Redis Streams (XREADGROUP-based delivery).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryService Unit Tests")
class DeliveryServiceTest {

    @Mock private QueueRepository queueRepository;
    @Mock private StreamMessageRepository streamMessageRepository;
    @Mock private RefCountService refCountService;
    @Mock private StorageProvider storageProvider;
    @Mock private TracingUtils tracingUtils;
    @Mock private QueueMetricsService queueMetricsService;

    private DeliveryService deliveryService;

    private Queue testQueue;
    private UUID queueId;
    private UUID ownerId;
    private String domainId = "test-domain";
    private String solutionId = "test-solution";
    private UUID environmentId;

    @BeforeEach
    void setUp() {
        queueId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        environmentId = ownerId;

        testQueue = new Queue();
        testQueue.setId(queueId);
        testQueue.setName("test-queue");
        testQueue.setOwnerId(ownerId);
        testQueue.setMaxRetries(3);
        testQueue.setRetryDelayMs(5000L);

        deliveryService = new DeliveryService(
                queueRepository, streamMessageRepository, refCountService,
                storageProvider, tracingUtils, new com.fasterxml.jackson.databind.ObjectMapper(), 30,
                queueMetricsService);

        lenient().when(tracingUtils.traceOperation(anyString(), anyMap(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
    }

    @Nested
    @DisplayName("deliverNextMessage() Tests")
    class DeliverNextMessageTests {

        @Test
        @DisplayName("Should deliver next message when queue has messages")
        void shouldDeliverNextMessage() {
            UUID messageId = UUID.randomUUID();
            String storageKey = "domain/solution/env/zone/ctx/event/svc/" + messageId + ".json";
            String streamKey = "function:msg_queue:domain:test-domain:solution:test-solution:environment:" + environmentId + ":queue:" + queueId;

            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.resolveStreamKey(domainId, solutionId, environmentId, queueId)).thenReturn(streamKey);
            when(streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, environmentId, queueId)).thenReturn(streamKey + ":dlq");
            when(streamMessageRepository.groupName(queueId)).thenReturn("group:" + queueId);
            when(streamMessageRepository.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());

            // readPending returns empty (no pending messages) → falls through to readNext
            when(streamMessageRepository.readPending(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("messageId", messageId.toString());
            fields.put("storageKey", storageKey);
            fields.put("enqueuedAt", Instant.now().toString());

            @SuppressWarnings("unchecked")
            MapRecord<String, Object, Object> record = MapRecord.create(streamKey, fields)
                    .withId(RecordId.of("1234567890-0"));

            when(streamMessageRepository.readNext(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(record));
            when(storageProvider.retrieve(storageKey)).thenReturn(Optional.of("{\"data\":\"test\"}"));
            when(queueRepository.save(any(Queue.class))).thenReturn(testQueue);

            Optional<MessageMetadata> result = deliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", "", domainId, solutionId, environmentId);

            assertThat(result).isPresent();
            assertThat(result.get().getMessageId()).isEqualTo(messageId);
            assertThat(result.get().getStatus()).isEqualTo(MessageStatus.DELIVERED);
            assertThat(result.get().getPayload()).isNotNull();
        }

        @Test
        @DisplayName("Should return empty when queue has no messages")
        void shouldReturnEmptyWhenNoMessages() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("key:dlq");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(streamMessageRepository.readPending(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(streamMessageRepository.readNext(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            Optional<MessageMetadata> result = deliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", "", domainId, solutionId, environmentId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should throw exception when queue not found")
        void shouldThrowExceptionWhenQueueNotFound() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", "", domainId, solutionId, environmentId))
                    .isInstanceOf(QueueNotFoundException.class);
        }

        @Test
        @DisplayName("Should acknowledge previous message when deleteMessageId provided")
        void shouldAcknowledgePreviousMessage() {
            String deleteStreamId = "1234567890-0";
            String storageKey = "some/storage/key.json";

            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("key:dlq");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);

            // Mock reading the entry to get storageKey before deletion
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("storageKey", storageKey);
            @SuppressWarnings("unchecked")
            MapRecord<String, Object, Object> rangeRecord = MapRecord.create("key", fields)
                    .withId(RecordId.of(deleteStreamId));
            when(streamMessageRepository.readRange(anyString(), eq(deleteStreamId), eq(deleteStreamId)))
                    .thenReturn(List.of(rangeRecord));

            when(streamMessageRepository.acknowledgeAndDelete(anyString(), anyString(), anyString(), eq(deleteStreamId)))
                    .thenReturn(true);
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.decrementAndGet(anyString())).thenReturn(0L);

            when(streamMessageRepository.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(streamMessageRepository.readPending(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(streamMessageRepository.readNext(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            deliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", deleteStreamId, domainId, solutionId, environmentId);

            verify(streamMessageRepository).acknowledgeAndDelete(anyString(), anyString(), anyString(), eq(deleteStreamId));
        }
    }

    @Nested
    @DisplayName("isInConsumerPel() Tests")
    class IsInConsumerPelTests {

        @Test
        @DisplayName("Should delegate PEL check to StreamMessageRepository")
        void shouldDelegatePelCheck() {
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.isInConsumerPel(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(true);

            boolean result = deliveryService.isInConsumerPel(
                    domainId, solutionId, environmentId, queueId, "consumer1", "1234-0");

            assertThat(result).isTrue();
            verify(streamMessageRepository).isInConsumerPel("key", "group:" + queueId, "consumer1", "1234-0");
        }
    }

    @Nested
    @DisplayName("claimAndEscalate() Tests (via deliverNextMessage)")
    class ClaimAndEscalateTests {

        @Test
        @DisplayName("Should move message to DLQ when delivery count exceeds maxRetries")
        void messageExceedsMaxRetries_movedToDlq() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("key:dlq");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);

            // Simulate claimed message with delivery count > maxRetries (3)
            UUID claimedMsgId = UUID.randomUUID();
            Map<Object, Object> claimedFields = new LinkedHashMap<>();
            claimedFields.put("messageId", claimedMsgId.toString());
            claimedFields.put("storageKey", "some/key.json");

            @SuppressWarnings("unchecked")
            MapRecord<String, Object, Object> claimedRecord = MapRecord.create("key", claimedFields)
                    .withId(RecordId.of("9999-0"));

            when(streamMessageRepository.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(List.of(claimedRecord));

            // Delivery count exceeds maxRetries
            org.springframework.data.redis.connection.stream.PendingMessage pendingMsg =
                    mock(org.springframework.data.redis.connection.stream.PendingMessage.class);
            when(pendingMsg.getIdAsString()).thenReturn("9999-0");
            when(pendingMsg.getTotalDeliveryCount()).thenReturn(5L);

            org.springframework.data.redis.connection.stream.PendingMessages pendingMessages =
                    mock(org.springframework.data.redis.connection.stream.PendingMessages.class);
            when(pendingMessages.iterator()).thenReturn(List.of(pendingMsg).iterator());
            // Make it iterable
            when(streamMessageRepository.getPendingMessagesForGroup(anyString(), anyString(), anyInt()))
                    .thenReturn(pendingMessages);

            when(streamMessageRepository.readPending(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(streamMessageRepository.readNext(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            deliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", "", domainId, solutionId, environmentId);

            verify(streamMessageRepository).moveToDeadLetter(
                    eq("key"), eq("key:dlq"), eq("group:" + queueId), eq("9999-0"),
                    eq(claimedMsgId), eq("some/key.json"), isNull());
        }

        @Test
        @DisplayName("Should retain message when delivery count is under maxRetries")
        void messageUnderMaxRetries_retained() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("key:dlq");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);

            UUID claimedMsgId = UUID.randomUUID();
            Map<Object, Object> claimedFields = new LinkedHashMap<>();
            claimedFields.put("messageId", claimedMsgId.toString());
            claimedFields.put("storageKey", "some/key.json");

            @SuppressWarnings("unchecked")
            MapRecord<String, Object, Object> claimedRecord = MapRecord.create("key", claimedFields)
                    .withId(RecordId.of("9999-0"));

            when(streamMessageRepository.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(List.of(claimedRecord));

            // Delivery count under maxRetries
            org.springframework.data.redis.connection.stream.PendingMessage pendingMsg =
                    mock(org.springframework.data.redis.connection.stream.PendingMessage.class);
            when(pendingMsg.getIdAsString()).thenReturn("9999-0");
            when(pendingMsg.getTotalDeliveryCount()).thenReturn(2L);

            org.springframework.data.redis.connection.stream.PendingMessages pendingMessages =
                    mock(org.springframework.data.redis.connection.stream.PendingMessages.class);
            when(pendingMessages.iterator()).thenReturn(List.of(pendingMsg).iterator());
            when(streamMessageRepository.getPendingMessagesForGroup(anyString(), anyString(), anyInt()))
                    .thenReturn(pendingMessages);

            when(streamMessageRepository.readPending(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(streamMessageRepository.readNext(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            deliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", "", domainId, solutionId, environmentId);

            verify(streamMessageRepository, never()).moveToDeadLetter(
                    anyString(), anyString(), anyString(), anyString(), any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Payload Retrieval Tests")
    class PayloadRetrievalTests {

        private void setupForPayloadTest(String storageKey) {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("key:dlq");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(streamMessageRepository.readPending(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            UUID messageId = UUID.randomUUID();
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("messageId", messageId.toString());
            fields.put("storageKey", storageKey);
            fields.put("enqueuedAt", Instant.now().toString());

            @SuppressWarnings("unchecked")
            MapRecord<String, Object, Object> record = MapRecord.create("key", fields)
                    .withId(RecordId.of("1234-0"));
            when(streamMessageRepository.readNext(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(record));
        }

        @Test
        @DisplayName("Should return Optional.empty() when payload retrieval returns null")
        void payloadReturnsNull_returnsEmpty() {
            String storageKey = "some/key.json";
            setupForPayloadTest(storageKey);
            when(storageProvider.retrieve(storageKey)).thenReturn(Optional.empty());

            Optional<MessageMetadata> result = deliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", "", domainId, solutionId, environmentId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return Optional.empty() when payload retrieval throws exception")
        void payloadThrowsException_returnsEmpty() {
            String storageKey = "some/key.json";
            setupForPayloadTest(storageKey);
            when(storageProvider.retrieve(storageKey)).thenThrow(new RuntimeException("Storage error"));

            Optional<MessageMetadata> result = deliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", "", domainId, solutionId, environmentId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("SIF Headers Deserialization Tests")
    class SifHeadersDeserializationTests {

        @Test
        @DisplayName("Should set sifHeaders to null when deserialization fails (graceful)")
        void deserializationFailure_sifHeadersNull() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("key:dlq");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(streamMessageRepository.readPending(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            UUID messageId = UUID.randomUUID();
            String storageKey = "some/key.json";
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("messageId", messageId.toString());
            fields.put("storageKey", storageKey);
            fields.put("enqueuedAt", Instant.now().toString());
            fields.put("sifHeaders", "this-is-not-valid-json{{{");

            @SuppressWarnings("unchecked")
            MapRecord<String, Object, Object> record = MapRecord.create("key", fields)
                    .withId(RecordId.of("1234-0"));
            when(streamMessageRepository.readNext(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(record));
            when(storageProvider.retrieve(storageKey)).thenReturn(Optional.of("{\"data\":1}"));
            when(queueRepository.save(any(Queue.class))).thenReturn(testQueue);

            Optional<MessageMetadata> result = deliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", "", domainId, solutionId, environmentId);

            assertThat(result).isPresent();
            assertThat(result.get().getSifHeaders()).isNull();
        }
    }

    @Nested
    @DisplayName("deletePayload() Tests (via acknowledge path)")
    class DeletePayloadTests {

        @Test
        @DisplayName("Should use synchronous delete for FileSystemStorageProvider")
        void fileSystemProvider_syncDelete() {
            // Create a DeliveryService with a FileSystemStorageProvider mock
            org.sifunity.broker.queueservice.storage.FileSystemStorageProvider fsProvider =
                    mock(org.sifunity.broker.queueservice.storage.FileSystemStorageProvider.class);

            DeliveryService fsDeliveryService = new DeliveryService(
                    queueRepository, streamMessageRepository, refCountService,
                    fsProvider, tracingUtils, new com.fasterxml.jackson.databind.ObjectMapper(), 30,
                    queueMetricsService);

            lenient().when(tracingUtils.traceOperation(anyString(), anyMap(), any(Supplier.class)))
                    .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());

            String deleteStreamId = "1234567890-0";
            String storageKey = "some/storage/key.json";

            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("key:dlq");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);

            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("storageKey", storageKey);
            @SuppressWarnings("unchecked")
            MapRecord<String, Object, Object> rangeRecord = MapRecord.create("key", fields)
                    .withId(RecordId.of(deleteStreamId));
            when(streamMessageRepository.readRange(anyString(), eq(deleteStreamId), eq(deleteStreamId)))
                    .thenReturn(List.of(rangeRecord));

            when(streamMessageRepository.acknowledgeAndDelete(anyString(), anyString(), anyString(), eq(deleteStreamId)))
                    .thenReturn(true);
            when(refCountService.refCountKey(anyString(), anyString(), anyString())).thenReturn("ref-key");
            when(refCountService.decrementAndGet(anyString())).thenReturn(0L);

            when(streamMessageRepository.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(streamMessageRepository.readPending(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(streamMessageRepository.readNext(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            fsDeliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", deleteStreamId, domainId, solutionId, environmentId);

            verify(fsProvider).delete(storageKey);
        }
    }

    @Nested
    @DisplayName("Metrics Recording Tests")
    class MetricsRecordingTests {

        @Test
        @DisplayName("Should not throw when metrics recording fails (graceful)")
        void metricsException_graceful() {
            when(queueRepository.findById(queueId)).thenReturn(Optional.of(testQueue));
            when(streamMessageRepository.resolveStreamKey(anyString(), anyString(), any(), any())).thenReturn("key");
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any())).thenReturn("key:dlq");
            when(streamMessageRepository.groupName(any())).thenReturn("group:" + queueId);
            when(streamMessageRepository.claimExpiredMessages(anyString(), anyString(), anyString(), any(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(streamMessageRepository.readPending(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            UUID messageId = UUID.randomUUID();
            String storageKey = "some/key.json";
            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("messageId", messageId.toString());
            fields.put("storageKey", storageKey);
            fields.put("enqueuedAt", Instant.now().toString());

            @SuppressWarnings("unchecked")
            MapRecord<String, Object, Object> record = MapRecord.create("key", fields)
                    .withId(RecordId.of("1234-0"));
            when(streamMessageRepository.readNext(anyString(), anyString(), anyString()))
                    .thenReturn(Optional.of(record));
            when(storageProvider.retrieve(storageKey)).thenReturn(Optional.of("{\"data\":1}"));
            when(queueRepository.save(any(Queue.class))).thenReturn(testQueue);

            // Metrics throws
            doThrow(new RuntimeException("Metrics failure"))
                    .when(queueMetricsService).recordDequeue(anyString(), any());

            Optional<MessageMetadata> result = deliveryService.deliverNextMessage(
                    queueId, "X_DEFAULT_Q_CONSUMER", "", domainId, solutionId, environmentId);

            // Should still succeed despite metrics failure
            assertThat(result).isPresent();
            assertThat(result.get().getMessageId()).isEqualTo(messageId);
        }
    }
}
