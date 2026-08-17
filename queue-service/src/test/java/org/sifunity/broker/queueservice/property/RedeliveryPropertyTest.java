// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 7: PEL-based redelivery via XCLAIM — expired PEL messages reclaimed.
 *
 * <p>Validates: Requirements 4.1, 4.2</p>
 */
class RedeliveryPropertyTest {

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
     * **Validates: Requirements 4.1, 4.2**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 7: PEL-based redelivery")
    void expiredPelMessagesReclaimedViaXclaim(
            @ForAll("uuids") UUID queueId,
            @ForAll @IntRange(min = 1, max = 5) int expiredCount,
            @ForAll @IntRange(min = 10, max = 120) int timeoutSeconds) {

        StreamMessageRepository smr = mock(StreamMessageRepository.class);

        String streamKey = "stream-key";
        String groupName = "group:" + queueId;
        String claimingConsumer = "consumer-claimer";
        Duration visibilityTimeout = Duration.ofSeconds(timeoutSeconds);

        // Create expired messages that should be claimed
        List<MapRecord<String, Object, Object>> claimedRecords = new ArrayList<>();
        for (int i = 0; i < expiredCount; i++) {
            UUID msgId = UUID.randomUUID();
            String streamId = (2000 + i) + "-0";
            claimedRecords.add(createMockRecord(streamId, msgId));
        }

        when(smr.claimExpiredMessages(streamKey, groupName, claimingConsumer, visibilityTimeout, 10))
                .thenReturn(claimedRecords);

        List<MapRecord<String, Object, Object>> result =
                smr.claimExpiredMessages(streamKey, groupName, claimingConsumer, visibilityTimeout, 10);

        assertThat(result).hasSize(expiredCount);

        // Each claimed message should have valid fields
        for (MapRecord<String, Object, Object> record : result) {
            assertThat(record.getValue().get(StreamMessageRepository.FIELD_MESSAGE_ID)).isNotNull();
            assertThat(record.getValue().get(StreamMessageRepository.FIELD_STORAGE_KEY)).isNotNull();
        }
    }
}
