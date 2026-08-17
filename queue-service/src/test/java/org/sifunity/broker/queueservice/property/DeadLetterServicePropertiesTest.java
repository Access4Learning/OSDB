// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.DeadLetterService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for DeadLetterService with stream-based DLQ.
 *
 * <p>Note: These are legacy property tests updated for compilation with the new
 * StreamMessageRepository API. Task 14 will create comprehensive new property tests
 * covering DLQ operations (Properties 8, 9, 10).</p>
 */
class DeadLetterServicePropertiesTest {

    @Provide
    Arbitrary<UUID> queueIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<UUID> messageIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * Property: moveToDeadLetter delegates to StreamMessageRepository.
     */
    @Property(tries = 50)
    @Label("moveToDeadLetter delegates to StreamMessageRepository")
    void moveToDeadLetterDelegates(
            @ForAll("queueIds") UUID queueId,
            @ForAll("messageIds") UUID messageId) {

        QueueRepository queueRepository = mock(QueueRepository.class);
        StreamMessageRepository streamMessageRepository = mock(StreamMessageRepository.class);
        StorageProvider storageProvider = mock(StorageProvider.class);

        DeadLetterService service = new DeadLetterService(queueRepository, streamMessageRepository, storageProvider);

        String streamKey = "stream-key";
        String dlqKey = "dlq-key";
        String groupName = "group:" + queueId;
        String streamId = "1234-0";
        String storageKey = "some/key.json";

        service.moveToDeadLetter(streamKey, dlqKey, groupName, streamId, messageId, storageKey);

        verify(streamMessageRepository).moveToDeadLetter(
                eq(streamKey), eq(dlqKey), eq(groupName), eq(streamId),
                eq(messageId), eq(storageKey), isNull());
    }

    /**
     * Property: getDeadLetterMessages returns empty for empty DLQ.
     */
    @Property(tries = 50)
    @Label("getDeadLetterMessages returns empty for empty DLQ")
    void getDeadLetterMessagesReturnsEmptyForEmptyDLQ(@ForAll("queueIds") UUID queueId) {
        QueueRepository queueRepository = mock(QueueRepository.class);
        StreamMessageRepository streamMessageRepository = mock(StreamMessageRepository.class);
        StorageProvider storageProvider = mock(StorageProvider.class);

        when(streamMessageRepository.dlqStreamKey(anyString(), anyString(), any(), any()))
                .thenReturn("dlq-key");
        when(streamMessageRepository.readDeadLetterMessages(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());

        DeadLetterService service = new DeadLetterService(queueRepository, streamMessageRepository, storageProvider);

        var result = service.getDeadLetterMessages(queueId, "domain", "solution", UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}
