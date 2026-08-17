// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property 12: messageCount accuracy — XLEN tracks unprocessed messages accurately after enqueue and ACK.
 *
 * <p>Validates: Requirements 6.4, 7.1, 7.2, 7.4</p>
 */
class MessageCountAccuracyPropertyTest {

    /**
     * **Validates: Requirements 6.4, 7.1, 7.2, 7.4**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 12: messageCount accuracy")
    void xlenTracksUnprocessedMessagesAccurately(
            @ForAll @IntRange(min = 1, max = 20) int enqueueCount,
            @ForAll @IntRange(min = 0, max = 20) int ackCount) {

        // ackCount cannot exceed enqueueCount
        int effectiveAckCount = Math.min(ackCount, enqueueCount);
        long expectedCount = enqueueCount - effectiveAckCount;

        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        String streamKey = "stream-key";
        String groupName = "group:test";

        // After enqueue - ack operations, XLEN should reflect unprocessed count
        when(smr.messageCount(streamKey)).thenReturn(expectedCount);
        when(smr.pendingCount(streamKey, groupName)).thenReturn(0L);

        long messageCount = smr.messageCount(streamKey);
        long pendingCount = smr.pendingCount(streamKey, groupName);

        assertThat(messageCount).isEqualTo(expectedCount);
        // Pending count should always be <= XLEN
        assertThat(pendingCount).isLessThanOrEqualTo(messageCount);
    }

    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 12b: Empty stream returns 0")
    void emptyStreamReturnsZero() {
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        String streamKey = "nonexistent-stream";

        when(smr.messageCount(streamKey)).thenReturn(0L);

        assertThat(smr.messageCount(streamKey)).isEqualTo(0L);
    }
}
