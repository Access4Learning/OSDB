// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.common.dto.SifCollection;
import org.sifunity.broker.queueservice.controller.QueueController;
import org.sifunity.broker.queueservice.service.QueueCleanupService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Preservation Property Tests for Security Enforcement - Queue Listing
 *
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on unfixed code.</strong>
 * They capture the baseline behavior that must remain unchanged after the security fix.
 *
 * <p><strong>Purpose:</strong>
 * Verifies that admin and broker-services environments continue to see all queues
 * for a solution when calling GET /queues, and that broker-services continues to have
 * unscoped access to the full queue list.
 *
 * <p><strong>Validates: Requirements 3.4, 3.5</strong>
 *
 * @see QueueController
 */
class SecurityEnforcementPreservationTest {

    /**
     * Property 2.5a: Preservation - Admin/broker-services GET queues returns full unscoped list
     *
     * <p><strong>Validates: Requirement 3.5</strong>
     *
     * <p>For all broker-services environments, GET /queues returns all queues
     * for the solution (unscoped). The QueueController checks if fingerprint == "broker-services"
     * and returns all queues via getAllQueues().
     *
     * @param queueId1 first queue's UUID
     * @param queueId2 second queue's UUID
     * @param ownerId1 first queue's owner
     * @param ownerId2 second queue's owner (different from first)
     */
    @Property(tries = 20)
    @Label("Preservation: broker-services GET queues returns all queues for solution")
    void brokerServicesGetQueuesReturnsAllQueues(
            @ForAll("uuids") UUID queueId1,
            @ForAll("uuids") UUID queueId2,
            @ForAll("uuids") UUID ownerId1,
            @ForAll("uuids") UUID ownerId2) {

        // Arrange
        QueueService queueService = mock(QueueService.class);
        QueueCleanupService cleanupService = mock(QueueCleanupService.class);
        QueueController controller = new QueueController(queueService, cleanupService, "test-internal-token");

        String domainId = "test-domain";
        String solutionId = "test-solution";
        UUID solutionUuid = UUID.randomUUID();

        List<QueueDTO> allQueues = List.of(
                new QueueDTO(queueId1, "queue-1", ownerId1, "IMMEDIATE",
                        "uri1", 1, Instant.now(), null, null, 3, 5000L, 0L),
                new QueueDTO(queueId2, "queue-2", ownerId2, "IMMEDIATE",
                        "uri2", 1, Instant.now(), null, null, 3, 5000L, 0L));

        when(queueService.getAllQueues(domainId, solutionId, solutionUuid)).thenReturn(allQueues);

        // Act: broker-services calls GET /queues with valid token
        ResponseEntity<?> response = controller.getQueues(
                domainId, solutionId,
                UUID.randomUUID().toString(),  // environmentId header (irrelevant for broker-services)
                "broker-services",              // fingerprint = broker-services
                solutionUuid.toString(),
                "test-internal-token");         // valid internal service token

        // Assert: 200 OK with all queues
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(SifCollection.class);

        verify(queueService).getAllQueues(domainId, solutionId, solutionUuid);
        verify(queueService, never()).getQueuesByOwner(any(), any(), any());
    }

    /**
     * Property 2.5b: Preservation - Global admin domain returns all queues
     *
     * <p><strong>Validates: Requirement 3.5</strong>
     *
     * <p>When the request path uses the global admin domain/solution (GLOBAL_DOMAIN/GLOBAL_SOLUTION),
     * the controller returns all queues regardless of environmentId or fingerprint.
     * This represents the admin code path where the caller is a global admin.
     *
     * @param queueId a queue UUID
     * @param ownerId the queue's owner
     */
    @Property(tries = 20)
    @Label("Preservation: Global admin domain returns all queues")
    void globalAdminDomainReturnsAllQueues(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId) {

        // Arrange
        QueueService queueService = mock(QueueService.class);
        QueueCleanupService cleanupService = mock(QueueCleanupService.class);
        QueueController controller = new QueueController(queueService, cleanupService, "test-internal-token");

        String domainId = "GLOBAL_DOMAIN";
        String solutionId = "GLOBAL_SOLUTION";

        List<QueueDTO> allQueues = List.of(
                new QueueDTO(queueId, "queue-1", ownerId, "IMMEDIATE",
                        "uri1", 1, Instant.now(), null, null, 3, 5000L, 0L));

        when(queueService.getAllQueues(domainId, solutionId, null)).thenReturn(allQueues);

        // Act: global admin domain → all queues returned
        ResponseEntity<?> response = controller.getQueues(
                domainId, solutionId,
                null,   // no X-Environment-Id
                null,   // no fingerprint
                null,   // no solution UUID
                null);  // no service token

        // Assert: 200 OK with all queues
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(SifCollection.class);

        verify(queueService).getAllQueues(domainId, solutionId, null);
        verify(queueService, never()).getQueuesByOwner(any(), any(), any());
    }

    /**
     * Property 2.4: Preservation - Authenticated env with matching owner gets own queues
     *
     * <p><strong>Validates: Requirement 3.4</strong>
     *
     * <p>When a non-admin, non-broker-services environment calls GET /queues,
     * the controller scopes to queues owned by that environment. This confirms
     * that owner-scoped queries continue to work correctly.
     *
     * @param ownerId the environment's ID (also the owner)
     * @param queueId the queue ID owned by this environment
     */
    @Property(tries = 20)
    @Label("Preservation: Authenticated env GET queues with matching owner returns 200 with queues")
    void authenticatedEnvGetQueuesWithMatchingOwnerReturns200(
            @ForAll("uuids") UUID ownerId,
            @ForAll("uuids") UUID queueId) {

        // Arrange
        QueueService queueService = mock(QueueService.class);
        QueueCleanupService cleanupService = mock(QueueCleanupService.class);
        QueueController controller = new QueueController(queueService, cleanupService, "test-internal-token");

        String domainId = "test-domain";
        String solutionId = "test-solution";

        List<QueueDTO> ownedQueues = List.of(
                new QueueDTO(queueId, "my-queue", ownerId, "IMMEDIATE",
                        "uri", 1, Instant.now(), null, null, 3, 5000L, 0L));

        when(queueService.getQueuesByOwner(ownerId, domainId, solutionId)).thenReturn(ownedQueues);

        // Act: non-admin with environmentId set → gets only owned queues
        ResponseEntity<?> response = controller.getQueues(
                domainId, solutionId,
                ownerId.toString(),  // X-Environment-Id = own ID
                null,                // no broker-services fingerprint
                null,                // no solution UUID
                null);               // no service token

        // Assert: 200 OK with owned queues
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(SifCollection.class);

        verify(queueService).getQueuesByOwner(ownerId, domainId, solutionId);
        verify(queueService, never()).getAllQueues(any(), any(), any());
    }

    // ========== Test Data Generators ==========

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
