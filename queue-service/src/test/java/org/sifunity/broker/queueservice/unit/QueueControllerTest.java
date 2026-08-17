// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.queueservice.controller.QueueController;
import org.sifunity.broker.queueservice.dto.CreateQueueRequest;
import org.sifunity.broker.queueservice.dto.QueueCreateRequest;
import org.sifunity.broker.queueservice.dto.QueueResponse;
import org.sifunity.broker.queueservice.service.QueueCleanupService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueueController — covers ownership checks, fingerprint bypass,
 * scoped queue listing, and delete/purge authorization.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueController Unit Tests")
class QueueControllerTest {

    private static final String VALID_TOKEN = "test-internal-token";

    @Mock private QueueService queueService;
    @Mock private QueueCleanupService queueCleanupService;

    private QueueController controller;

    private UUID queueId;
    private UUID ownerId;
    private String domainId;
    private String solutionId;

    @BeforeEach
    void setUp() {
        controller = new QueueController(queueService, queueCleanupService, VALID_TOKEN);
        queueId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        domainId = "test-domain";
        solutionId = "test-solution";
    }

    private QueueDTO queueOwnedBy(UUID owner) {
        return new QueueDTO(queueId, "test-queue", owner, "IMMEDIATE", null, 1,
                Instant.now(), null, null, 3, 5000L, 0L);
    }

    // ── createQueue() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("createQueue() Tests")
    class CreateQueueTests {

        @Test
        @DisplayName("Should return 403 when environmentId != request.ownerId")
        void ownershipViolation_returns403() {
            UUID otherEnv = UUID.randomUUID();
            QueueCreateRequest request = new QueueCreateRequest(
                    new CreateQueueRequest("my-queue", ownerId, 3, 5000L, 1));

            ResponseEntity<?> response = controller.createQueue(
                    domainId, solutionId, request, otherEnv.toString(), null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
            verifyNoInteractions(queueService);
        }

        @Test
        @DisplayName("Should create queue when environmentId matches request.ownerId")
        void validOwnership_createsQueue() {
            QueueCreateRequest request = new QueueCreateRequest(
                    new CreateQueueRequest("my-queue", ownerId, 3, 5000L, 1));
            QueueDTO created = queueOwnedBy(ownerId);
            when(queueService.createQueue("my-queue", ownerId, 3, 5000L, 1, domainId, solutionId, null))
                    .thenReturn(created);

            ResponseEntity<?> response = controller.createQueue(
                    domainId, solutionId, request, ownerId.toString(), null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isEqualTo(new QueueResponse(created));
        }

        @Test
        @DisplayName("Should return 403 when environmentId header is null (fail-closed)")
        void nullEnvironmentId_returns403() {
            QueueCreateRequest request = new QueueCreateRequest(
                    new CreateQueueRequest("my-queue", ownerId, 3, 5000L, 1));

            ResponseEntity<?> response = controller.createQueue(
                    domainId, solutionId, request, null, null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
            verifyNoInteractions(queueService);
        }

        @Test
        @DisplayName("Should allow broker-services with valid token to create queue for any owner")
        void brokerServices_withToken_bypassesOwnershipCheck() {
            UUID otherEnv = UUID.randomUUID();
            QueueCreateRequest request = new QueueCreateRequest(
                    new CreateQueueRequest("my-queue", ownerId, 3, 5000L, 1));
            QueueDTO created = queueOwnedBy(ownerId);
            when(queueService.createQueue("my-queue", ownerId, 3, 5000L, 1, domainId, solutionId, null))
                    .thenReturn(created);

            ResponseEntity<?> response = controller.createQueue(
                    domainId, solutionId, request, otherEnv.toString(), "broker-services", null, VALID_TOKEN);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isEqualTo(new QueueResponse(created));
        }

        @Test
        @DisplayName("Should return 403 when broker-services fingerprint without valid token")
        void brokerServices_withoutToken_returns403() {
            UUID otherEnv = UUID.randomUUID();
            QueueCreateRequest request = new QueueCreateRequest(
                    new CreateQueueRequest("my-queue", ownerId, 3, 5000L, 1));

            ResponseEntity<?> response = controller.createQueue(
                    domainId, solutionId, request, otherEnv.toString(), "broker-services", null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── getQueue() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getQueue() Tests")
    class GetQueueTests {

        @Test
        @DisplayName("Should bypass ownership check when fingerprint is 'broker-services' with valid token")
        void brokerServicesFingerprint_withToken_bypassesOwnership() {
            UUID otherEnv = UUID.randomUUID();
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId, domainId, solutionId)).thenReturn(queue);

            ResponseEntity<?> response = controller.getQueue(
                    domainId, solutionId, queueId, otherEnv.toString(), "broker-services", VALID_TOKEN);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(new QueueResponse(queue));
        }

        @Test
        @DisplayName("Should return 403 on ownership violation without broker-services fingerprint")
        void ownershipViolation_returns403() {
            UUID otherEnv = UUID.randomUUID();
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId, domainId, solutionId)).thenReturn(queue);

            ResponseEntity<?> response = controller.getQueue(
                    domainId, solutionId, queueId, otherEnv.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
        }

        @Test
        @DisplayName("Should return queue when environmentId matches ownerId")
        void validOwnership_returnsQueue() {
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId, domainId, solutionId)).thenReturn(queue);

            ResponseEntity<?> response = controller.getQueue(
                    domainId, solutionId, queueId, ownerId.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(new QueueResponse(queue));
        }

        @Test
        @DisplayName("Should return 403 when broker-services fingerprint without valid token")
        void brokerServices_withoutToken_returns403() {
            UUID otherEnv = UUID.randomUUID();
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId, domainId, solutionId)).thenReturn(queue);

            ResponseEntity<?> response = controller.getQueue(
                    domainId, solutionId, queueId, otherEnv.toString(), "broker-services", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── getQueues() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getQueues() Tests")
    class GetQueuesTests {

        @Test
        @DisplayName("Should return 403 when environmentId is null (fail-closed)")
        void nullEnvironmentId_returns403() {
            ResponseEntity<?> response = controller.getQueues(domainId, solutionId, null, null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
            verifyNoInteractions(queueService);
        }

        @Test
        @DisplayName("Should return all queues when fingerprint is 'broker-services' with valid token")
        void brokerServicesFingerprint_withToken_returnsAll() {
            UUID someEnv = UUID.randomUUID();
            List<QueueDTO> allQueues = List.of(queueOwnedBy(ownerId));
            when(queueService.getAllQueues(eq(domainId), eq(solutionId), isNull())).thenReturn(allQueues);

            ResponseEntity<?> response = controller.getQueues(
                    domainId, solutionId, someEnv.toString(), "broker-services", null, VALID_TOKEN);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(queueService).getAllQueues(eq(domainId), eq(solutionId), isNull());
        }

        @Test
        @DisplayName("Should scope to owner when environmentId is present and no broker-services fingerprint")
        void scopedToOwner() {
            List<QueueDTO> ownerQueues = List.of(queueOwnedBy(ownerId));
            when(queueService.getQueuesByOwner(ownerId, domainId, solutionId)).thenReturn(ownerQueues);

            ResponseEntity<?> response = controller.getQueues(
                    domainId, solutionId, ownerId.toString(), null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(queueService).getQueuesByOwner(ownerId, domainId, solutionId);
            verify(queueService, never()).getAllQueues(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should scope to owner when broker-services fingerprint without valid token")
        void brokerServices_withoutToken_scopesToOwner() {
            UUID someEnv = UUID.randomUUID();
            List<QueueDTO> ownerQueues = List.of(queueOwnedBy(ownerId));
            when(queueService.getQueuesByOwner(eq(someEnv), eq(domainId), eq(solutionId))).thenReturn(ownerQueues);

            ResponseEntity<?> response = controller.getQueues(
                    domainId, solutionId, someEnv.toString(), "broker-services", null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(queueService).getQueuesByOwner(eq(someEnv), eq(domainId), eq(solutionId));
        }
    }

    // ── deleteQueue() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteQueue() Tests")
    class DeleteQueueTests {

        @Test
        @DisplayName("Should bypass ownership check when fingerprint is 'broker-services' with valid token")
        void brokerServicesFingerprint_withToken_bypassesOwnership() {
            UUID otherEnv = UUID.randomUUID();
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId)).thenReturn(queue);

            ResponseEntity<?> response = controller.deleteQueue(
                    domainId, solutionId, queueId, otherEnv.toString(), "broker-services", VALID_TOKEN);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            verify(queueCleanupService).submitDelete(queueId, domainId, solutionId, ownerId);
        }

        @Test
        @DisplayName("Should return 403 on ownership violation")
        void ownershipViolation_returns403() {
            UUID otherEnv = UUID.randomUUID();
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId)).thenReturn(queue);

            ResponseEntity<?> response = controller.deleteQueue(
                    domainId, solutionId, queueId, otherEnv.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verifyNoInteractions(queueCleanupService);
        }

        @Test
        @DisplayName("Should return 403 when broker-services fingerprint without valid token")
        void brokerServices_withoutToken_returns403() {
            UUID otherEnv = UUID.randomUUID();
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId)).thenReturn(queue);

            ResponseEntity<?> response = controller.deleteQueue(
                    domainId, solutionId, queueId, otherEnv.toString(), "broker-services", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verifyNoInteractions(queueCleanupService);
        }
    }

    // ── purgeQueue() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("purgeQueue() Tests")
    class PurgeQueueTests {

        @Test
        @DisplayName("Should return 403 on ownership violation")
        void ownershipViolation_returns403() {
            UUID otherEnv = UUID.randomUUID();
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId)).thenReturn(queue);

            ResponseEntity<?> response = controller.purgeQueue(
                    domainId, solutionId, queueId, otherEnv.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verifyNoInteractions(queueCleanupService);
        }

        @Test
        @DisplayName("Should accept purge when ownership matches")
        void validOwnership_acceptsPurge() {
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId)).thenReturn(queue);

            ResponseEntity<?> response = controller.purgeQueue(
                    domainId, solutionId, queueId, ownerId.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            verify(queueCleanupService).submitPurge(queueId, domainId, solutionId, ownerId);
        }
    }

    // ── getQueueStatistics() ────────────────────────────────────────────

    @Nested
    @DisplayName("getQueueStatistics() Tests")
    class GetQueueStatisticsTests {

        @Test
        @DisplayName("Should bypass ownership check when fingerprint is 'broker-services' with valid token")
        void brokerServicesFingerprint_withToken_bypassesOwnership() {
            UUID otherEnv = UUID.randomUUID();
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId)).thenReturn(queue);

            QueueService.QueueStatistics stats = new QueueService.QueueStatistics(
                    queueId, 10, 2, 1, 13);
            when(queueService.getQueueStatistics(eq(queueId), eq(domainId), eq(solutionId), eq(otherEnv)))
                    .thenReturn(stats);

            ResponseEntity<?> response = controller.getQueueStatistics(
                    domainId, solutionId, queueId, otherEnv.toString(), "broker-services", VALID_TOKEN);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should return 403 on ownership violation without broker-services fingerprint")
        void ownershipViolation_returns403() {
            UUID otherEnv = UUID.randomUUID();
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId)).thenReturn(queue);

            ResponseEntity<?> response = controller.getQueueStatistics(
                    domainId, solutionId, queueId, otherEnv.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should return 403 when broker-services fingerprint without valid token")
        void brokerServices_withoutToken_returns403() {
            UUID otherEnv = UUID.randomUUID();
            QueueDTO queue = queueOwnedBy(ownerId);
            when(queueService.getQueue(queueId)).thenReturn(queue);

            ResponseEntity<?> response = controller.getQueueStatistics(
                    domainId, solutionId, queueId, otherEnv.toString(), "broker-services", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
