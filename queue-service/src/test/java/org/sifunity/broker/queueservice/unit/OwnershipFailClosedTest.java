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
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.queueservice.controller.MessageController;
import org.sifunity.broker.queueservice.dto.EnqueueMessageRequest;
import org.sifunity.broker.queueservice.service.DeadLetterService;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests that verify the fail-closed ownership enforcement and token-gated
 * broker-services bypass in MessageController.
 *
 * <p>Validates Requirements 3.1, 3.3, 3.4, and 5.3.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Ownership Fail-Closed Tests (Req 3.1, 3.3, 3.4)")
class OwnershipFailClosedTest {

    private static final String VALID_TOKEN = "test-internal-token-secret";
    private static final String WRONG_TOKEN = "wrong-token-value";

    @Mock private MessageService messageService;
    @Mock private DeliveryService deliveryService;
    @Mock private DeadLetterService deadLetterService;
    @Mock private QueueService queueService;

    private MessageController controller;

    private UUID queueId;
    private UUID ownerId;
    private String domainId;
    private String solutionId;

    @BeforeEach
    void setUp() {
        controller = new MessageController(messageService, deliveryService, deadLetterService, queueService, VALID_TOKEN);
        queueId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        domainId = "test-domain";
        solutionId = "test-solution";
    }

    private QueueDTO queueOwnedBy(UUID owner) {
        return new QueueDTO(queueId, "test-queue", owner, "IMMEDIATE", null, 1,
                Instant.now(), null, null, 3, 5000L, 0L);
    }

    // ── Requirement 3.1: Null X-Environment-Id → 403 ──────────────────────

    @Nested
    @DisplayName("Req 3.1: Null X-Environment-Id → 403")
    class NullEnvironmentIdTests {

        @Test
        @DisplayName("enqueueMessage with null environmentId returns 403 with SIF error")
        void enqueue_nullEnvironmentId_returns403() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            EnqueueMessageRequest request = new EnqueueMessageRequest(
                    UUID.randomUUID(), null, "zone", "ctx", "svc",
                    "event", null, null, null, null, "{\"data\":1}");

            ResponseEntity<?> response = controller.enqueueMessage(
                    domainId, solutionId, queueId, request, null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
            SifErrorResponse error = ((SifErrorResponseEnvelope) response.getBody()).error();
            assertThat(error.description()).contains("Missing X-Environment-Id");
        }

        @Test
        @DisplayName("getNextMessage with null environmentId returns 403 with SIF error")
        void getNext_nullEnvironmentId_returns403() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            ResponseEntity<?> response = controller.getNextMessage(
                    domainId, solutionId, queueId, null, "X_DEFAULT_Q_CONSUMER", null,
                    null, null, new MockHttpServletRequest(), null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
            SifErrorResponse error = ((SifErrorResponseEnvelope) response.getBody()).error();
            assertThat(error.description()).contains("Missing X-Environment-Id");
        }

        @Test
        @DisplayName("deleteMessage with null environmentId (no bypass) returns 403")
        void delete_nullEnvironmentId_noBrokerBypass_returns403() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            ResponseEntity<?> response = controller.deleteMessage(
                    domainId, solutionId, queueId, UUID.randomUUID().toString(),
                    null, null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
            SifErrorResponse error = ((SifErrorResponseEnvelope) response.getBody()).error();
            assertThat(error.description()).contains("Missing X-Environment-Id");
        }
    }

    // ── Requirement 3.3: Forged broker-services fingerprint ────────────────

    @Nested
    @DisplayName("Req 3.3: Forged broker-services fingerprint")
    class ForgedFingerprintTests {

        @Test
        @DisplayName("broker-services fingerprint WITHOUT token → 403 (bypass not honored)")
        void fingerprint_withoutToken_returns403() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            ResponseEntity<?> response = controller.deleteMessage(
                    domainId, solutionId, queueId, UUID.randomUUID().toString(),
                    null, "broker-services", null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
        }

        @Test
        @DisplayName("broker-services fingerprint WITH wrong token → 403 (bypass not honored)")
        void fingerprint_withWrongToken_returns403() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            ResponseEntity<?> response = controller.deleteMessage(
                    domainId, solutionId, queueId, UUID.randomUUID().toString(),
                    null, "broker-services", WRONG_TOKEN, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
        }

        @Test
        @DisplayName("broker-services fingerprint WITH valid token → allowed (not 403)")
        void fingerprint_withValidToken_allowed() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            String messageId = UUID.randomUUID().toString();
            when(deliveryService.deleteMessage(eq(queueId), eq(messageId), eq(domainId), eq(solutionId), eq(ownerId)))
                    .thenReturn(true);

            ResponseEntity<?> response = controller.deleteMessage(
                    domainId, solutionId, queueId, messageId,
                    null, "broker-services", VALID_TOKEN, null);

            // Should NOT be 403 — bypass is honored with valid token
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("broker-services fingerprint WITH valid token but non-existent message → 410 (not 403)")
        void fingerprint_withValidToken_messageNotFound_returns410() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            String messageId = UUID.randomUUID().toString();
            when(deliveryService.deleteMessage(eq(queueId), eq(messageId), eq(domainId), eq(solutionId), eq(ownerId)))
                    .thenReturn(false);

            ResponseEntity<?> response = controller.deleteMessage(
                    domainId, solutionId, queueId, messageId,
                    null, "broker-services", VALID_TOKEN, null);

            // The load-bearing assertion is that the bypass was HONORED — not 403. The specific
            // not-found status is incidental to this test, and D7 moved it from the undeclared 404
            // to the declared 410 for an absent message in an existing queue.
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        }

        @Test
        @DisplayName("broker-services fingerprint WITH valid token on enqueueMessage → allowed (not 403)")
        void enqueue_fingerprint_withValidToken_allowed() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            var metadata = org.sifunity.broker.queueservice.domain.MessageMetadata.builder()
                    .messageId(UUID.randomUUID()).queueId(queueId)
                    .storageKey("key")
                    .status(org.sifunity.broker.queueservice.domain.MessageStatus.PENDING)
                    .enqueuedAt(Instant.now()).deliveryAttempts(0).build();

            when(messageService.enqueueMessage(eq(queueId), any(), eq(domainId), eq(solutionId),
                    eq(ownerId), eq(ownerId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(metadata);

            EnqueueMessageRequest request = new EnqueueMessageRequest(
                    UUID.randomUUID(), null, "zone", "ctx", "svc",
                    "event", null, null, null, null, "{\"data\":1}");

            ResponseEntity<?> response = controller.enqueueMessage(
                    domainId, solutionId, queueId, request,
                    null, "broker-services", VALID_TOKEN);

            // Should NOT be 403 — bypass is honored with valid token
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    // ── Requirement 3.4: Gateway-mediated request (valid owner) → unchanged ─

    @Nested
    @DisplayName("Req 3.4: Gateway-mediated request → unchanged behavior")
    class GatewayMediatedTests {

        @Test
        @DisplayName("getNextMessage with valid owner environmentId → normal empty-queue response (204)")
        void getNext_validOwner_returns204WhenEmpty() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));
            when(deliveryService.deliverNextMessage(eq(queueId), eq("X_DEFAULT_Q_CONSUMER"),
                    eq(""), eq(domainId), eq(solutionId), eq(ownerId)))
                    .thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.getNextMessage(
                    domainId, solutionId, queueId, null, "X_DEFAULT_Q_CONSUMER",
                    ownerId.toString(), null, null, new MockHttpServletRequest(), null);

            // Gateway injects valid X-Environment-Id matching queue owner → normal behavior
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("enqueueMessage with valid owner environmentId → normal 201 response")
        void enqueue_validOwner_returns201() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            var metadata = org.sifunity.broker.queueservice.domain.MessageMetadata.builder()
                    .messageId(UUID.randomUUID()).queueId(queueId)
                    .storageKey("key")
                    .status(org.sifunity.broker.queueservice.domain.MessageStatus.PENDING)
                    .enqueuedAt(Instant.now()).deliveryAttempts(0).build();

            when(messageService.enqueueMessage(eq(queueId), any(), eq(domainId), eq(solutionId),
                    eq(ownerId), eq(ownerId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(metadata);

            EnqueueMessageRequest request = new EnqueueMessageRequest(
                    UUID.randomUUID(), null, "zone", "ctx", "svc",
                    "event", null, null, null, null, "{\"data\":1}");

            ResponseEntity<?> response = controller.enqueueMessage(
                    domainId, solutionId, queueId, request, ownerId.toString(), null, null);

            // Gateway-mediated request with valid owner → 201 Created
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("deleteMessage with valid owner environmentId → normal 204 response")
        void delete_validOwner_returns204() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            String messageId = UUID.randomUUID().toString();
            when(deliveryService.deleteMessage(eq(queueId), eq(messageId), eq(domainId), eq(solutionId), eq(ownerId)))
                    .thenReturn(true);

            ResponseEntity<?> response = controller.deleteMessage(
                    domainId, solutionId, queueId, messageId,
                    ownerId.toString(), null, null, null);

            // Gateway-mediated request with valid owner → normal deletion
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }
}
