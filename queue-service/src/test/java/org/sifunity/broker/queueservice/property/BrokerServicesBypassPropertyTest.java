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
 * Property 8: Broker-services bypasses ownership check.
 *
 * <p>For any purge or delete request where the X-Environment-Fingerprint header
 * is "broker-services", the operation SHALL be allowed regardless of the
 * X-Environment-Id value.</p>
 *
 * <p><b>Validates: Requirements 9.2</b></p>
 */
@Tag("Feature: queue-purge-cascade-delete, Property 8: Broker-services bypasses ownership check")
class BrokerServicesBypassPropertyTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * **Validates: Requirements 9.2**
     */
    @Property(tries = 100)
    @Label("Feature: queue-purge-cascade-delete, Property 8: Broker-services bypasses ownership check")
    void brokerServicesFingerprintBypassesOwnershipCheck(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId,
            @ForAll("uuids") UUID requesterId) {

        // Ensure requester is NOT the owner (to prove bypass works)
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

        // Test purge with broker-services fingerprint + valid token
        ResponseEntity<?> purgeResponse = controller.purgeQueue(
                "domain", "solution", queueId,
                requesterId.toString(), "broker-services", "test-internal-token");

        assertThat(purgeResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Test delete with broker-services fingerprint + valid token
        ResponseEntity<?> deleteResponse = controller.deleteQueue(
                "domain", "solution", queueId,
                requesterId.toString(), "broker-services", "test-internal-token");

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Verify: operations were submitted with the queue OWNER's ID (not the requester's)
        // because the stream key must use the owner's environment ID
        verify(cleanupService).submitPurge(eq(queueId), eq("domain"), eq("solution"), eq(ownerId));
        verify(cleanupService).submitDelete(eq(queueId), eq("domain"), eq("solution"), eq(ownerId));
    }
}
