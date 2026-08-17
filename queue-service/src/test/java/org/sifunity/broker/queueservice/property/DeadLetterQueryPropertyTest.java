// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeadLetterService;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 9: DLQ query returns messages with payloads.
 *
 * <p>Validates: Requirements 5.3</p>
 */
class DeadLetterQueryPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, Object, Object> createDlqRecord(UUID messageId, String storageKey) {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        RecordId recordId = RecordId.of(System.currentTimeMillis() + "-0");
        when(record.getId()).thenReturn(recordId);
        Map<Object, Object> fields = new HashMap<>();
        fields.put(StreamMessageRepository.FIELD_MESSAGE_ID, messageId.toString());
        fields.put(StreamMessageRepository.FIELD_STORAGE_KEY, storageKey);
        fields.put(StreamMessageRepository.FIELD_ENQUEUED_AT, Instant.now().toString());
        when(record.getValue()).thenReturn(fields);
        return record;
    }

    /**
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 9: DLQ query")
    void dlqQueryReturnsMessagesWithPayloads(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId,
            @ForAll @IntRange(min = 1, max = 5) int dlqMessageCount) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        StorageProvider sp = mock(StorageProvider.class);

        String dlqKey = "dlq-key";
        when(smr.resolveDlqStreamKey("d", "s", environmentId, queueId)).thenReturn(dlqKey);

        List<MapRecord<String, Object, Object>> dlqRecords = new ArrayList<>();
        for (int i = 0; i < dlqMessageCount; i++) {
            UUID msgId = UUID.randomUUID();
            String storageKey = "storage-" + msgId;
            dlqRecords.add(createDlqRecord(msgId, storageKey));
            when(sp.retrieve(storageKey)).thenReturn(Optional.of("{\"data\":\"payload-" + i + "\"}"));
        }

        when(smr.readDeadLetterMessages(eq(dlqKey), eq(0L), eq(1000L))).thenReturn(dlqRecords);

        DeadLetterService dlService = new DeadLetterService(qr, smr, sp);
        List<MessageMetadata> result = dlService.getDeadLetterMessages(queueId, "d", "s", environmentId);

        assertThat(result).hasSize(dlqMessageCount);
        for (MessageMetadata meta : result) {
            assertThat(meta.getMessageId()).isNotNull();
            assertThat(meta.getPayload()).isNotNull();
            assertThat(meta.getStorageKey()).isNotNull();
        }
    }
}
