// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.MessageStatus;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeadLetterService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeadLetterService with stream-based DLQ operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeadLetterService Unit Tests")
class DeadLetterServiceTest {

    @Mock private QueueRepository queueRepository;
    @Mock private StreamMessageRepository streamMessageRepository;
    @Mock private StorageProvider storageProvider;

    @InjectMocks
    private DeadLetterService deadLetterService;

    private UUID queueId;
    private UUID ownerId;
    private UUID environmentId;
    private String domainId = "test-domain";
    private String solutionId = "test-solution";

    @BeforeEach
    void setUp() {
        queueId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        environmentId = ownerId;
    }

    @Nested
    @DisplayName("moveToDeadLetter() Tests")
    class MoveToDeadLetterTests {

        @Test
        @DisplayName("Should move message to DLQ stream")
        void shouldMoveMessageToDLQ() {
            String streamKey = "stream-key";
            String dlqKey = "stream-key:dlq";
            String groupName = "group:" + queueId;
            String streamId = "1234567890-0";
            UUID messageId = UUID.randomUUID();
            String storageKey = "some/key.json";

            deadLetterService.moveToDeadLetter(streamKey, dlqKey, groupName, streamId, messageId, storageKey);

            verify(streamMessageRepository).moveToDeadLetter(
                    eq(streamKey), eq(dlqKey), eq(groupName), eq(streamId),
                    eq(messageId), eq(storageKey), isNull());
        }

        @Test
        @DisplayName("Should throw when Redis fails")
        void shouldThrowWhenRedisFails() {
            doThrow(new RuntimeException("Redis failure"))
                    .when(streamMessageRepository).moveToDeadLetter(any(), any(), any(), any(), any(), any(), any());

            assertThatThrownBy(() -> deadLetterService.moveToDeadLetter(
                    "key", "dlq", "group", "1-0", UUID.randomUUID(), "storage"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("getDeadLetterMessages() Tests")
    class GetDeadLetterMessagesTests {

        @Test
        @DisplayName("Should retrieve DLQ messages with payloads")
        void shouldRetrieveDLQMessages() {
            UUID messageId = UUID.randomUUID();
            String storageKey = "some/key.json";
            String dlqKey = "dlq-key";

            when(streamMessageRepository.resolveDlqStreamKey(domainId, solutionId, environmentId, queueId))
                    .thenReturn(dlqKey);

            Map<Object, Object> fields = new LinkedHashMap<>();
            fields.put("messageId", messageId.toString());
            fields.put("storageKey", storageKey);
            fields.put("enqueuedAt", Instant.now().toString());

            @SuppressWarnings("unchecked")
            MapRecord<String, Object, Object> record = MapRecord.create(dlqKey, fields)
                    .withId(RecordId.of("1-0"));

            when(streamMessageRepository.readDeadLetterMessages(dlqKey, 0, 1000))
                    .thenReturn(List.of(record));
            when(storageProvider.retrieve(storageKey)).thenReturn(Optional.of("{\"data\":\"test\"}"));

            List<MessageMetadata> result = deadLetterService.getDeadLetterMessages(
                    queueId, domainId, solutionId, environmentId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMessageId()).isEqualTo(messageId);
            assertThat(result.get(0).getStatus()).isEqualTo(MessageStatus.FAILED);
            assertThat(result.get(0).getPayload()).isNotNull();
        }

        @Test
        @DisplayName("Should return empty list when DLQ is empty")
        void shouldReturnEmptyWhenDLQEmpty() {
            when(streamMessageRepository.resolveDlqStreamKey(anyString(), anyString(), any(), any()))
                    .thenReturn("dlq-key");
            when(streamMessageRepository.readDeadLetterMessages(anyString(), anyLong(), anyLong()))
                    .thenReturn(Collections.emptyList());

            List<MessageMetadata> result = deadLetterService.getDeadLetterMessages(
                    queueId, domainId, solutionId, environmentId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("reprocessMessage() Tests")
    class ReprocessMessageTests {

        @Test
        @DisplayName("Should reprocess message from DLQ back to main stream")
        void shouldReprocessMessage() {
            UUID messageId = UUID.randomUUID();
            String storageKey = "some/key.json";
            String dlqStreamId = "1-0";

            when(streamMessageRepository.streamKey(domainId, solutionId, environmentId, queueId))
                    .thenReturn("stream-key");
            when(streamMessageRepository.dlqStreamKey(domainId, solutionId, environmentId, queueId))
                    .thenReturn("dlq-key");
            when(streamMessageRepository.groupName(queueId)).thenReturn("group:" + queueId);

            deadLetterService.reprocessMessage(queueId, dlqStreamId, messageId, storageKey,
                    domainId, solutionId, environmentId);

            verify(streamMessageRepository).reprocessFromDeadLetter(
                    eq("dlq-key"), eq("stream-key"), eq("group:" + queueId),
                    eq(dlqStreamId), eq(messageId), eq(storageKey), isNull());
        }
    }
}
