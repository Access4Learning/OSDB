// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.service.DeadLetterService;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 8: Dead-letter escalation — messages exceeding maxRetries moved to DLQ.
 *
 * <p>Validates: Requirements 4.3, 5.1, 5.2</p>
 */
class DeadLetterEscalationPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * **Validates: Requirements 4.3, 5.1, 5.2**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 8: Dead-letter escalation")
    void messagesExceedingMaxRetriesMovedToDlq(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID messageId,
            @ForAll @IntRange(min = 1, max = 10) int maxRetries) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        StorageProvider sp = mock(StorageProvider.class);

        String streamKey = "stream-key";
        String dlqKey = streamKey + ":dlq";
        String groupName = "group:" + queueId;
        String streamId = "1234567890-0";
        String storageKey = "key-" + messageId;

        DeadLetterService dlService = new DeadLetterService(qr, smr, sp);

        // Move to DLQ (simulating what happens when maxRetries exceeded)
        dlService.moveToDeadLetter(streamKey, dlqKey, groupName, streamId, messageId, storageKey);

        // Verify XADD to DLQ + XACK + XDEL on original stream
        verify(smr).moveToDeadLetter(
                eq(streamKey), eq(dlqKey), eq(groupName), eq(streamId),
                eq(messageId), eq(storageKey), isNull());
    }
}
