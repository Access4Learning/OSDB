// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.service.QueueService;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.sifunity.broker.queueservice.storage.StorageProvider;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 14: queueUri format — queueUri matches expected URI pattern.
 *
 * <p>Validates: Requirements 6.7</p>
 */
class QueueUriFormatPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> alphaStrings() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> baseUrls() {
        return Arbitraries.of("http://localhost:8080", "https://broker.example.com", "");
    }

    /**
     * **Validates: Requirements 6.7**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 14: queueUri format")
    void queueUriMatchesExpectedPattern(
            @ForAll @NotBlank String name,
            @ForAll("uuids") UUID ownerId,
            @ForAll("alphaStrings") String domainId,
            @ForAll("alphaStrings") String solutionId,
            @ForAll("baseUrls") String baseUrl) {

        QueueRepository qr = mock(QueueRepository.class);
        StreamMessageRepository smr = mock(StreamMessageRepository.class);
        RefCountService rcs = mock(RefCountService.class);
        StorageProvider sp = mock(StorageProvider.class);

        when(qr.save(any(Queue.class))).thenAnswer(inv -> {
            Queue q = inv.getArgument(0);
            Queue saved = new Queue();
            saved.setId(UUID.randomUUID());
            saved.setName(q.getName());
            saved.setOwnerId(q.getOwnerId());
            saved.setPolling("IMMEDIATE");
            saved.setQueueUri(q.getQueueUri());
            saved.setMaxConcurrentConnections(q.getMaxConcurrentConnections());
            saved.setMaxRetries(q.getMaxRetries());
            saved.setRetryDelayMs(q.getRetryDelayMs());
            saved.setCreated(Instant.now());
            return saved;
        });
        when(smr.messageCount(anyString())).thenReturn(0L);
        when(smr.streamKey(anyString(), anyString(), any(), any())).thenReturn("key");

        QueueService svc = new QueueService(qr, smr, rcs, sp, baseUrl);

        QueueDTO result = svc.createQueue(name, ownerId, null, null, null, domainId, solutionId);

        String queueUri = result.queueUri();
        assertThat(queueUri).isNotNull();

        // URI should contain domainId, solutionId, queueId, and end with /messages
        assertThat(queueUri).contains(domainId);
        assertThat(queueUri).contains(solutionId);
        assertThat(queueUri).contains("/queues/");
        assertThat(queueUri).endsWith("/messages");

        // Pattern: {baseUrl}/{domainId}/{solutionId}/queues/{queueId}/messages
        String uuidPattern = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        if (baseUrl != null && !baseUrl.isEmpty()) {
            assertThat(queueUri).startsWith(baseUrl + "/");
        }
        assertThat(queueUri).matches(".*" + domainId + "/" + solutionId + "/queues/" + uuidPattern + "/messages");
    }
}
