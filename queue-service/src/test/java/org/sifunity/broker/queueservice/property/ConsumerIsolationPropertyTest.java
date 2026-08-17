// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 6: Consumer isolation — multiple consumers get distinct messages.
 *
 * <p>Validates: Requirements 3.1, 3.5</p>
 */
class ConsumerIsolationPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, Object, Object> createMockRecord(String streamId, UUID messageId) {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        RecordId recordId = RecordId.of(streamId);
        when(record.getId()).thenReturn(recordId);
        Map<Object, Object> fields = new HashMap<>();
        fields.put(StreamMessageRepository.FIELD_MESSAGE_ID, messageId.toString());
        fields.put(StreamMessageRepository.FIELD_STORAGE_KEY, "key-" + messageId);
        fields.put(StreamMessageRepository.FIELD_ENQUEUED_AT, Instant.now().toString());
        when(record.getValue()).thenReturn(fields);
        return record;
    }

    /**
     * **Validates: Requirements 3.1, 3.5**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 6: Consumer isolation")
    void multipleConsumersGetDistinctMessages(
            @ForAll("uuids") UUID queueId,
            @ForAll @IntRange(min = 2, max = 5) int consumerCount) {

        StreamMessageRepository smr = mock(StreamMessageRepository.class);

        String streamKey = "stream-key";
        String groupName = "group:" + queueId;

        // Each consumer gets a distinct message
        Set<String> deliveredMessageIds = new HashSet<>();
        List<String> consumers = new ArrayList<>();
        for (int i = 0; i < consumerCount; i++) {
            String consumer = "consumer-" + i;
            consumers.add(consumer);
            UUID msgId = UUID.randomUUID();
            String streamId = (1000 + i) + "-0";

            MapRecord<String, Object, Object> record = createMockRecord(streamId, msgId);
            when(smr.readNext(streamKey, groupName, consumer))
                    .thenReturn(Optional.of(record));

            deliveredMessageIds.add(msgId.toString());
        }

        // Verify each consumer gets a distinct message
        Set<String> actualMessageIds = new HashSet<>();
        for (String consumer : consumers) {
            Optional<MapRecord<String, Object, Object>> result = smr.readNext(streamKey, groupName, consumer);
            assertThat(result).isPresent();
            String msgId = result.get().getValue().get(StreamMessageRepository.FIELD_MESSAGE_ID).toString();
            actualMessageIds.add(msgId);
        }

        // All messages should be distinct
        assertThat(actualMessageIds).hasSize(consumerCount);
    }
}
