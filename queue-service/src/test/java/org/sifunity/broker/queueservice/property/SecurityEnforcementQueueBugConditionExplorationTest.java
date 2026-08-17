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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug Condition Exploration Test for Security Enforcement Gap 5.
 *
 * <p><strong>CRITICAL: This test MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Gap 5: Non-admin environment GETs /{domainId}/{solutionId}/queues — should return only
 * queues owned by the authenticated environment. On unfixed code, the ownership scoping may
 * be bypassed when environmentId is present but the filter logic has a defect, or when
 * a client-supplied ownerId query parameter overrides the authenticated ID.</p>
 *
 * <p>The current QueueController.getQueues() checks:
 * {@code if (environmentId == null || "broker-services".equals(fingerprint))} to decide
 * between all-queues vs. owner-scoped. The bug manifests because non-admin environments
 * may see unscoped results (empty or all queues).</p>
 *
 * <p><strong>Validates: Requirements 1.5</strong></p>
 */
class SecurityEnforcementQueueBugConditionExplorationTest {

    /**
     * Property 1: Bug Condition — Non-admin queue listing must return only owned queues.
     *
     * <p>For any non-admin, non-broker-services environment that GETs /queues, the response
     * SHALL contain ONLY queues where queue.ownerId == authenticated environment's ID.</p>
     *
     * <p>The bug manifests because the controller branches on
     * {@code environmentId == null || "broker-services".equals(fingerprint)}. When the
     * gateway fails to inject X-Environment-Id (due to Gap 3 on certain paths), the
     * queue controller treats the request as solution-wide, returning all queues or empty.
     * Even when environmentId IS present, the controller should enforce that the ownerId
     * query parameter cannot be used by non-admin callers to see other environments' queues.</p>
     *
     * <p>This test simulates the scenario where X-Environment-Id is null for a non-admin
     * caller (the gateway didn't inject it properly). The controller should still reject
     * or scope properly rather than returning all queues.</p>
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — returns all queues when environmentId is null.</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns 403 or only owned queues.</p>
     *
     * <p><strong>Validates: Requirements 1.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Gap 5: Non-admin GET /queues with null environmentId should not return all queues")
    void nonAdminQueueListingWithNullEnvIdShouldNotReturnAllQueues(
            @ForAll("environmentTypes") String environmentType,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) {

        // Arrange: Create scenario with multiple queue owners
        UUID env1Id = UUID.randomUUID();
        UUID env2Id = UUID.randomUUID();
        // Non-admin fingerprint (not "broker-services")
        String fingerprint = "app-" + environmentType.toLowerCase();

        QueueDTO queue1 = new QueueDTO(
                UUID.randomUUID(), "queue-1", env1Id, "IMMEDIATE",
                "uri1", 1, Instant.now(), null, null, 3, 5000L, 0L);
        QueueDTO queue2 = new QueueDTO(
                UUID.randomUUID(), "queue-2", env2Id, "IMMEDIATE",
                "uri2", 1, Instant.now(), null, null, 3, 5000L, 0L);
        QueueDTO queue3 = new QueueDTO(
                UUID.randomUUID(), "queue-3", env1Id, "IMMEDIATE",
                "uri3", 1, Instant.now(), null, null, 3, 5000L, 0L);

        QueueService queueService = mock(QueueService.class);
        QueueCleanupService cleanupService = mock(QueueCleanupService.class);

        // Mock getAllQueues to return ALL queues for the solution
        when(queueService.getAllQueues(eq(domainId), eq(solutionId), any()))
                .thenReturn(List.of(queue1, queue2, queue3));

        QueueController controller = new QueueController(queueService, cleanupService, "test-internal-token");

        // Act: Call getQueues with null environmentId but NON-broker-services fingerprint
        // This simulates the bug where gateway didn't inject X-Environment-Id
        // but the caller is clearly not an admin or broker-services
        ResponseEntity<?> response = controller.getQueues(
                domainId, solutionId,
                null,          // X-Environment-Id is null (gap from gateway)
                fingerprint,   // Non-broker-services fingerprint proves this is NOT an admin
                null,          // No solution UUID
                null);         // No service token

        // Assert: A non-admin caller with a non-broker-services fingerprint
        // should NOT get all queues just because environmentId is null.
        // On FIXED code: the controller returns 403 for non-admin with no environmentId.
        // The key invariant: a non-admin caller must NEVER see all queues.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> environmentTypes() {
        return Arbitraries.of("DIRECT", "BROKERED");
    }

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of("test-domain", "school-district-1", "state-edu");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of("prod-solution", "test-solution", "staging");
    }
}
