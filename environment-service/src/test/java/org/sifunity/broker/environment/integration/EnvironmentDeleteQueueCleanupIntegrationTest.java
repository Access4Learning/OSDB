// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.QueueServiceClient;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Integration tests for the full deleteEnvironment flow with queue cleanup.
 *
 * <p>Uses {@code @MockBean} for {@link QueueServiceClient} to verify queue cleanup
 * interactions during environment deletion without requiring WireMock or an actual
 * queue-service instance.</p>
 *
 * <p>Tests validate:
 * <ul>
 *   <li>Queue cleanup is called with correct parameters during environment deletion</li>
 *   <li>Environment deletion succeeds even when queue cleanup fails</li>
 *   <li>Domain/solution deletion paths do NOT trigger queue cleanup</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 1.4, 2.2, 2.3, 2.4, 3.1, 5.2, 6.1, 6.2, 6.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnvironmentDeleteQueueCleanupIntegrationTest {

    @MockBean
    private QueueServiceClient queueServiceClient;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private TestEntityHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.create(domainService, solutionService);
    }

    /**
     * Registers a test environment in the hierarchy.
     */
    private UUID registerEnvironment(String appName) {
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName + "-" + System.nanoTime(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse response = environmentService.registerEnvironment(hierarchy.getDomainId(), request);
        return response.environment().id();
    }

    // ========== Test: Environment with owned queues ==========

    /**
     * Test that deleteEnvironment calls QueueServiceClient with correct parameters
     * and successfully deletes the environment.
     *
     * <p>Simulates an environment with 3 owned queues by verifying the client is
     * called with the correct environmentId, domainId, and solutionId.</p>
     */
    @Test
    void deleteEnvironment_callsQueueCleanupWithCorrectParameters_thenDeletesEnvironment() {
        // Given
        UUID environmentId = registerEnvironment("QueueCleanupApp");
        doNothing().when(queueServiceClient).deleteQueuesForEnvironment(any(), any(), any());

        // When
        environmentService.deleteEnvironment(environmentId);

        // Then - queue cleanup called with correct parameters
        verify(queueServiceClient).deleteQueuesForEnvironment(
                eq(environmentId),
                eq(hierarchy.getDomainId()),
                eq(hierarchy.getSolutionId()));

        // And - environment is deleted
        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId))
                .isInstanceOf(EnvironmentNotFoundException.class);
    }

    // ========== Test: Environment with 0 queues (no-op cleanup) ==========

    /**
     * Test that deleteEnvironment succeeds when QueueServiceClient reports no queues.
     * The client is still called (it handles the empty case internally).
     */
    @Test
    void deleteEnvironment_noQueues_clientCalledAndEnvironmentDeleted() {
        // Given - client does nothing (simulates 0 owned queues)
        UUID environmentId = registerEnvironment("NoQueuesApp");
        doNothing().when(queueServiceClient).deleteQueuesForEnvironment(any(), any(), any());

        // When
        environmentService.deleteEnvironment(environmentId);

        // Then - client was called
        verify(queueServiceClient).deleteQueuesForEnvironment(
                eq(environmentId),
                eq(hierarchy.getDomainId()),
                eq(hierarchy.getSolutionId()));

        // And - environment is deleted
        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId))
                .isInstanceOf(EnvironmentNotFoundException.class);
    }

    // ========== Test: Queue-service down (RuntimeException) ==========

    /**
     * Test that deleteEnvironment succeeds even when QueueServiceClient throws
     * an exception (simulating queue-service being down or returning errors).
     *
     * <p>The environment must still be deleted with a WARN log.</p>
     */
    @Test
    void deleteEnvironment_queueServiceDown_environmentStillDeleted() {
        // Given - client throws (simulates connection refused / service unavailable)
        UUID environmentId = registerEnvironment("ServiceDownApp");
        doThrow(new RuntimeException("Connection refused: queue-service:8085"))
                .when(queueServiceClient).deleteQueuesForEnvironment(any(), any(), any());

        // When
        environmentService.deleteEnvironment(environmentId);

        // Then - client was called (attempted)
        verify(queueServiceClient).deleteQueuesForEnvironment(
                eq(environmentId),
                eq(hierarchy.getDomainId()),
                eq(hierarchy.getSolutionId()));

        // And - environment is still deleted despite queue cleanup failure
        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId))
                .isInstanceOf(EnvironmentNotFoundException.class);
    }

    // ========== Test: Mixed responses (client handles internally) ==========

    /**
     * Test that deleteEnvironment succeeds when QueueServiceClient encounters
     * mixed responses (202, 404, 500) internally. Since the client handles errors
     * internally and never throws, this verifies the environment is deleted normally.
     *
     * <p>The QueueServiceClient's contract is best-effort: it logs and continues.
     * This test verifies the integration point works correctly.</p>
     */
    @Test
    void deleteEnvironment_mixedQueueResponses_environmentDeleted() {
        // Given - client completes without throwing (it handles mixed responses internally)
        UUID environmentId = registerEnvironment("MixedResponseApp");
        doNothing().when(queueServiceClient).deleteQueuesForEnvironment(any(), any(), any());

        // When
        environmentService.deleteEnvironment(environmentId);

        // Then - client was called
        verify(queueServiceClient).deleteQueuesForEnvironment(
                eq(environmentId),
                eq(hierarchy.getDomainId()),
                eq(hierarchy.getSolutionId()));

        // And - environment is deleted
        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId))
                .isInstanceOf(EnvironmentNotFoundException.class);
    }

    // ========== Test: Domain deletion does NOT trigger queue cleanup ==========

    /**
     * Test that deleting a domain does NOT call QueueServiceClient.
     * Queue cleanup is only triggered by direct environment deletion, not cascade.
     *
     * <p><strong>Validates: Requirements 6.1, 6.2</strong></p>
     */
    @Test
    void deleteDomain_doesNotTriggerQueueCleanup() {
        // Given - create a separate hierarchy for this test (will be deleted)
        TestEntityHierarchy separateHierarchy = TestEntityHierarchy.create(domainService, solutionService);

        // Register an environment in the separate hierarchy
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("DomainDeleteApp-" + System.nanoTime(), EnvironmentType.BROKERED, null, separateHierarchy.getSolutionId(), null));
        environmentService.registerEnvironment(separateHierarchy.getDomainId(), request);

        // Reset mock to clear any interactions from registration
        reset(queueServiceClient);

        // When - delete the domain (cascades to solution and environment)
        domainService.deleteDomain(separateHierarchy.getDomainId());

        // Then - QueueServiceClient was NOT called
        verifyNoInteractions(queueServiceClient);
    }

    // ========== Test: Solution deletion does NOT trigger queue cleanup ==========

    /**
     * Test that deleting a solution does NOT call QueueServiceClient.
     * Queue cleanup is only triggered by direct environment deletion, not cascade.
     *
     * <p><strong>Validates: Requirements 6.2, 6.3</strong></p>
     */
    @Test
    void deleteSolution_doesNotTriggerQueueCleanup() {
        // Given - create a separate hierarchy for this test (will be deleted)
        TestEntityHierarchy separateHierarchy = TestEntityHierarchy.create(domainService, solutionService);

        // Register an environment in the separate hierarchy
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("SolutionDeleteApp-" + System.nanoTime(), EnvironmentType.BROKERED, null, separateHierarchy.getSolutionId(), null));
        environmentService.registerEnvironment(separateHierarchy.getDomainId(), request);

        // Reset mock to clear any interactions from registration
        reset(queueServiceClient);

        // When - delete the solution (cascades to environment via DB FK)
        solutionService.deleteSolution(separateHierarchy.getDomainId(), separateHierarchy.getSolutionId());

        // Then - QueueServiceClient was NOT called
        verifyNoInteractions(queueServiceClient);
    }

    // ========== Test: Multiple environments deleted independently ==========

    /**
     * Test that deleting multiple environments each triggers its own queue cleanup call.
     */
    @Test
    void deleteMultipleEnvironments_eachTriggersQueueCleanup() {
        // Given
        UUID envId1 = registerEnvironment("MultiEnv1");
        UUID envId2 = registerEnvironment("MultiEnv2");
        UUID envId3 = registerEnvironment("MultiEnv3");
        doNothing().when(queueServiceClient).deleteQueuesForEnvironment(any(), any(), any());

        // When
        environmentService.deleteEnvironment(envId1);
        environmentService.deleteEnvironment(envId2);
        environmentService.deleteEnvironment(envId3);

        // Then - each environment triggered its own cleanup call
        verify(queueServiceClient).deleteQueuesForEnvironment(
                eq(envId1), eq(hierarchy.getDomainId()), eq(hierarchy.getSolutionId()));
        verify(queueServiceClient).deleteQueuesForEnvironment(
                eq(envId2), eq(hierarchy.getDomainId()), eq(hierarchy.getSolutionId()));
        verify(queueServiceClient).deleteQueuesForEnvironment(
                eq(envId3), eq(hierarchy.getDomainId()), eq(hierarchy.getSolutionId()));

        // And - all environments are deleted
        assertThatThrownBy(() -> environmentService.getEnvironment(envId1))
                .isInstanceOf(EnvironmentNotFoundException.class);
        assertThatThrownBy(() -> environmentService.getEnvironment(envId2))
                .isInstanceOf(EnvironmentNotFoundException.class);
        assertThatThrownBy(() -> environmentService.getEnvironment(envId3))
                .isInstanceOf(EnvironmentNotFoundException.class);
    }
}
