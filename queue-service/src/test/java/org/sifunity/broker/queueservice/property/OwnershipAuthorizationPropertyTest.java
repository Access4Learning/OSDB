// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.controller.QueueController;
import org.sifunity.broker.queueservice.service.QueueCleanupService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 7: Non-owner authorization rejection.
 *
 * <p>For any purge or delete request where the X-Environment-Id header value
 * does not match the queue's ownerId and the X-Environment-Fingerprint is not
 * "broker-services", the Queue_Service SHALL return HTTP 403 Forbidden without
 * modifying any queue state.</p>
 *
 * <p><b>Validates: Requirements 8.4, 9.1</b></p>
 */
@Tag("Feature: queue-purge-cascade-delete, Property 7: Non-owner authorization rejection")
class OwnershipAuthorizationPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> nonBrokerFingerprints() {
        return Arbitraries.of("app-service", "web-client", "test-env", null, "", "other-service")
                .filter(f -> !"broker-services".equals(f));
    }

    /**
     * **Validates: Requirements 8.4, 9.1**
     */
    @Property(tries = 100)
    @Label("Feature: queue-purge-cascade-delete, Property 7: Non-owner authorization rejection")
    void nonOwnerPurgeReturns403WithoutStateModification(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId,
            @ForAll("uuids") UUID requesterId,
            @ForAll("nonBrokerFingerprints") String fingerprint) {

        // Ensure requester is NOT the owner
        while (requesterId.equals(ownerId)) {
            requesterId = UUID.randomUUID();
        }

        QueueService queueService = mock(QueueService.class);
        QueueCleanupService cleanupService = mock(QueueCleanupService.class);

        QueueDTO queueDTO = new QueueDTO(
                queueId, "test-queue", ownerId, "IMMEDIATE",
                "uri", 1, Instant.now(), null, null, 3, 5000L, 0L);

        when(queueService.getQueue(queueId)).thenReturn(queueDTO);

        QueueController controller = new QueueController(queueService, cleanupService, "test-internal-token");

        // Test purge endpoint
        ResponseEntity<?> purgeResponse = controller.purgeQueue(
                "domain", "solution", queueId,
                requesterId.toString(), fingerprint, null);

        assertThat(purgeResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Test delete endpoint
        ResponseEntity<?> deleteResponse = controller.deleteQueue(
                "domain", "solution", queueId,
                requesterId.toString(), fingerprint, null);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Verify: no state modification occurred
        verify(cleanupService, never()).submitPurge(any(), anyString(), anyString(), any());
        verify(cleanupService, never()).submitDelete(any(), anyString(), anyString(), any());
    }
}
