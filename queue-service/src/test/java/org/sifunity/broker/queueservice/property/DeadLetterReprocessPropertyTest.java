// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeadLetterService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 10: DLQ reprocess round-trip — reprocess moves message back to main stream.
 *
 * <p>Validates: Requirements 5.4</p>
 */
class DeadLetterReprocessPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> alphaStrings() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    /**
     * **Validates: Requirements 5.4**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 10: DLQ reprocess round-trip")
    void reprocessMovesMessageBackToMainStream(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID messageId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("alphaStrings") String domainId,
            @ForAll("alphaStrings") String solutionId) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        StorageProvider sp = mock(StorageProvider.class);

        String streamKey = "stream-key";
        String dlqKey = "dlq-key";
        String groupName = "group:" + queueId;
        String dlqStreamId = "9999999-0";
        String storageKey = "storage-" + messageId;

        when(smr.streamKey(domainId, solutionId, environmentId, queueId)).thenReturn(streamKey);
        when(smr.dlqStreamKey(domainId, solutionId, environmentId, queueId)).thenReturn(dlqKey);
        when(smr.groupName(queueId)).thenReturn(groupName);

        DeadLetterService dlService = new DeadLetterService(qr, smr, sp);
        dlService.reprocessMessage(queueId, dlqStreamId, messageId, storageKey,
                domainId, solutionId, environmentId);

        // Verify XADD back to original stream + XDEL from DLQ
        verify(smr).reprocessFromDeadLetter(
                eq(dlqKey), eq(streamKey), eq(groupName),
                eq(dlqStreamId), eq(messageId), eq(storageKey), isNull());
    }
}
