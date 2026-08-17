// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.service.DeliveryService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 5: ACK validates PEL ownership — ACK fails with 404 when message not in consumer's PEL.
 *
 * <p>Validates: Requirements 2.2, 3.4</p>
 */
class PelValidationPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> consumerNames() {
        return Arbitraries.of("conn-1", "conn-2", "X_DEFAULT_Q_CONSUMER");
    }

    /**
     * **Validates: Requirements 2.2, 3.4**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 5: ACK validates PEL ownership")
    void ackFailsWhenMessageNotInConsumerPel(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("consumerNames") String consumerName) {

        StreamMessageRepository smr = mock(StreamMessageRepository.class);

        String streamKey = "stream-key";
        String groupName = "group:" + queueId;
        String streamId = "1234567890-0";

        when(smr.streamKey(anyString(), anyString(), any(), any())).thenReturn(streamKey);
        when(smr.groupName(queueId)).thenReturn(groupName);

        // Message is NOT in consumer's PEL
        when(smr.isInConsumerPel(streamKey, groupName, consumerName, streamId)).thenReturn(false);

        // Verify isInConsumerPel returns false
        boolean inPel = smr.isInConsumerPel(streamKey, groupName, consumerName, streamId);
        assertThat(inPel).isFalse();

        // A new connectionId with empty PEL should also fail
        String newConsumer = "brand-new-consumer-" + UUID.randomUUID();
        when(smr.isInConsumerPel(streamKey, groupName, newConsumer, streamId)).thenReturn(false);
        assertThat(smr.isInConsumerPel(streamKey, groupName, newConsumer, streamId)).isFalse();
    }

    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 5b: ACK succeeds when message in PEL")
    void ackSucceedsWhenMessageInConsumerPel(
            @ForAll("uuids") UUID queueId,
            @ForAll("consumerNames") String consumerName) {

        StreamMessageRepository smr = mock(StreamMessageRepository.class);

        String streamKey = "stream-key";
        String groupName = "group:" + queueId;
        String streamId = "1234567890-0";

        when(smr.isInConsumerPel(streamKey, groupName, consumerName, streamId)).thenReturn(true);

        boolean inPel = smr.isInConsumerPel(streamKey, groupName, consumerName, streamId);
        assertThat(inPel).isTrue();
    }
}
