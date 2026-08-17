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
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.MessageStatus;
import org.sifunity.broker.queueservice.dto.EnqueueMessageRequest;
import org.sifunity.broker.queueservice.service.DeadLetterService;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageController — covers ownership checks, environment resolution,
 * messageType defaults, PEL validation, empty queue, and SIF header forwarding.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageController Unit Tests")
class MessageControllerTest {

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
        controller = new MessageController(messageService, deliveryService, deadLetterService, queueService, "test-internal-token");
        queueId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        domainId = "test-domain";
        solutionId = "test-solution";
    }

    private QueueDTO queueOwnedBy(UUID owner) {
        return new QueueDTO(queueId, "test-queue", owner, "IMMEDIATE", null, 1,
                Instant.now(), null, null, 3, 5000L, 0L);
    }

    // ── enqueueMessage() ────────────────────────────────────────────────

    @Nested
    @DisplayName("enqueueMessage() Tests")
    class EnqueueMessageTests {

        @Test
        @DisplayName("Should return 403 when environmentId header != queue.ownerId")
        void ownershipViolation_returns403() {
            UUID otherEnv = UUID.randomUUID();
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            EnqueueMessageRequest request = new EnqueueMessageRequest(
                    UUID.randomUUID(), null, "zone", "ctx", "svc",
                    "event", null, null, null, null, "{\"data\":1}");

            ResponseEntity<?> response = controller.enqueueMessage(
                    domainId, solutionId, queueId, request, otherEnv.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
        }

        @Test
        @DisplayName("Should fallback to queue.ownerId when request.environment() is null")
        void nullEnvironment_fallsBackToOwnerId() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            MessageMetadata metadata = MessageMetadata.builder()
                    .messageId(UUID.randomUUID()).queueId(queueId)
                    .storageKey("key").status(MessageStatus.PENDING)
                    .enqueuedAt(Instant.now()).deliveryAttempts(0).build();

            when(messageService.enqueueMessage(eq(queueId), any(), eq(domainId), eq(solutionId),
                    eq(ownerId), eq(ownerId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(metadata);

            EnqueueMessageRequest request = new EnqueueMessageRequest(
                    UUID.randomUUID(), null, "zone", "ctx", "svc",
                    "event", null, null, null, null, "{\"data\":1}");

            ResponseEntity<?> response = controller.enqueueMessage(
                    domainId, solutionId, queueId, request, ownerId.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            // sourceEnvUuid should be ownerId (fallback)
            verify(messageService).enqueueMessage(eq(queueId), any(), eq(domainId), eq(solutionId),
                    eq(ownerId), eq(ownerId), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should fallback to queue.ownerId when request.environment() is blank")
        void blankEnvironment_fallsBackToOwnerId() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            MessageMetadata metadata = MessageMetadata.builder()
                    .messageId(UUID.randomUUID()).queueId(queueId)
                    .storageKey("key").status(MessageStatus.PENDING)
                    .enqueuedAt(Instant.now()).deliveryAttempts(0).build();

            when(messageService.enqueueMessage(eq(queueId), any(), eq(domainId), eq(solutionId),
                    eq(ownerId), eq(ownerId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(metadata);

            EnqueueMessageRequest request = new EnqueueMessageRequest(
                    UUID.randomUUID(), "   ", "zone", "ctx", "svc",
                    "event", null, null, null, null, "{\"data\":1}");

            ResponseEntity<?> response = controller.enqueueMessage(
                    domainId, solutionId, queueId, request, ownerId.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(messageService).enqueueMessage(eq(queueId), any(), eq(domainId), eq(solutionId),
                    eq(ownerId), eq(ownerId), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should fallback to queue.ownerId when request.environment() has invalid UUID")
        void invalidUuidEnvironment_fallsBackToOwnerId() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            MessageMetadata metadata = MessageMetadata.builder()
                    .messageId(UUID.randomUUID()).queueId(queueId)
                    .storageKey("key").status(MessageStatus.PENDING)
                    .enqueuedAt(Instant.now()).deliveryAttempts(0).build();

            when(messageService.enqueueMessage(eq(queueId), any(), eq(domainId), eq(solutionId),
                    eq(ownerId), eq(ownerId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(metadata);

            EnqueueMessageRequest request = new EnqueueMessageRequest(
                    UUID.randomUUID(), "not-a-uuid", "zone", "ctx", "svc",
                    "event", null, null, null, null, "{\"data\":1}");

            ResponseEntity<?> response = controller.enqueueMessage(
                    domainId, solutionId, queueId, request, ownerId.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(messageService).enqueueMessage(eq(queueId), any(), eq(domainId), eq(solutionId),
                    eq(ownerId), eq(ownerId), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should default messageType to 'event' when request.messageType() is null")
        void nullMessageType_defaultsToEvent() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            MessageMetadata metadata = MessageMetadata.builder()
                    .messageId(UUID.randomUUID()).queueId(queueId)
                    .storageKey("key").status(MessageStatus.PENDING)
                    .enqueuedAt(Instant.now()).deliveryAttempts(0).build();

            when(messageService.enqueueMessage(eq(queueId), any(), eq(domainId), eq(solutionId),
                    eq(ownerId), eq(ownerId), any(), any(), eq("event"), any(), any(), any()))
                    .thenReturn(metadata);

            EnqueueMessageRequest request = new EnqueueMessageRequest(
                    UUID.randomUUID(), ownerId.toString(), "zone", "ctx", "svc",
                    null, null, null, null, null, "{\"data\":1}");

            ResponseEntity<?> response = controller.enqueueMessage(
                    domainId, solutionId, queueId, request, ownerId.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(messageService).enqueueMessage(eq(queueId), any(), eq(domainId), eq(solutionId),
                    eq(ownerId), eq(ownerId), any(), any(), eq("event"), any(), any(), any());
        }
    }

    // ── getNextMessage() ────────────────────────────────────────────────

    @Nested
    @DisplayName("getNextMessage() Tests")
    class GetNextMessageTests {

        private MockHttpServletRequest mockRequest() {
            return new MockHttpServletRequest();
        }

        private MockHttpServletRequest mockRequestWithMatrixParam(String deleteMessageId) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            String uri = "/test-domain/test-solution/queues/" + queueId + "/messages;deleteMessageId=" + deleteMessageId;
            req.setAttribute("org.sifunity.broker.ORIGINAL_URI", uri);
            return req;
        }

        @Test
        @DisplayName("Should return 403 on ownership violation")
        void ownershipViolation_returns403() {
            UUID otherEnv = UUID.randomUUID();
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            ResponseEntity<?> response = controller.getNextMessage(
                    domainId, solutionId, queueId, null, "X_DEFAULT_Q_CONSUMER", otherEnv.toString(), null, null, mockRequest(), null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should return 410 Gone when resolvedStreamId is null (PEL validation failure)")
        void pelValidationFailure_returns410() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));
            when(deliveryService.resolveStreamIdFromMessageId(
                    eq(domainId), eq(solutionId), eq(ownerId), eq(queueId),
                    eq("X_DEFAULT_Q_CONSUMER"), eq("some-message-id")))
                    .thenReturn(null);

            ResponseEntity<?> response = controller.getNextMessage(
                    domainId, solutionId, queueId, "some-message-id", "X_DEFAULT_Q_CONSUMER", ownerId.toString(), null, null, mockRequest(), null);

            // D7: queueMessageGet declares no 404. A message absent from the pending-entries list is
            // GONE, not never-existed. QueueNotFoundException -> 404 remains declared and unchanged.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
        }

        @Test
        @DisplayName("Should return 204 No Content when queue is empty")
        void emptyQueue_returns204() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));
            when(deliveryService.deliverNextMessage(eq(queueId), eq("X_DEFAULT_Q_CONSUMER"),
                    eq(""), eq(domainId), eq(solutionId), eq(ownerId)))
                    .thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.getNextMessage(
                    domainId, solutionId, queueId, null, "X_DEFAULT_Q_CONSUMER", ownerId.toString(), null, null, mockRequest(), null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("Should return 200 with SIF headers and blacklist filtering on successful delivery")
        void successfulDelivery_returnsSifHeaders() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            Map<String, String> sifHeaders = new LinkedHashMap<>();
            sifHeaders.put("messageType", "event");
            sifHeaders.put("zoneId", "zone1");
            sifHeaders.put("content-type", "should-be-blacklisted");
            sifHeaders.put("authorization", "should-be-blacklisted");

            UUID messageId = UUID.randomUUID();
            MessageMetadata msg = MessageMetadata.builder()
                    .messageId(messageId).queueId(queueId)
                    .storageKey("key").status(MessageStatus.DELIVERED)
                    .enqueuedAt(Instant.now()).deliveredAt(Instant.now())
                    .deliveryAttempts(1).payload("{\"student\":\"data\"}")
                    .streamId("1234-0").sifHeaders(sifHeaders).build();

            when(deliveryService.deliverNextMessage(eq(queueId), eq("X_DEFAULT_Q_CONSUMER"),
                    eq(""), eq(domainId), eq(solutionId), eq(ownerId)))
                    .thenReturn(Optional.of(msg));

            ResponseEntity<?> response = controller.getNextMessage(
                    domainId, solutionId, queueId, null, "X_DEFAULT_Q_CONSUMER", ownerId.toString(), null, null, mockRequest(), null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("{\"student\":\"data\"}");
            // messageid header always present
            assertThat(response.getHeaders().getFirst("messageid")).isEqualTo(messageId.toString());
            // Non-blacklisted headers forwarded
            assertThat(response.getHeaders().getFirst("messageType")).isEqualTo("event");
            assertThat(response.getHeaders().getFirst("zoneId")).isEqualTo("zone1");
            // Blacklisted headers NOT forwarded
            assertThat(response.getHeaders().containsKey("content-type")).isFalse();
            assertThat(response.getHeaders().containsKey("authorization")).isFalse();
        }

        @Test
        @DisplayName("Should handle null sifHeaders on delivered message gracefully")
        void nullSifHeaders_noExtraHeaders() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));

            UUID messageId = UUID.randomUUID();
            MessageMetadata msg = MessageMetadata.builder()
                    .messageId(messageId).queueId(queueId)
                    .storageKey("key").status(MessageStatus.DELIVERED)
                    .enqueuedAt(Instant.now()).deliveredAt(Instant.now())
                    .deliveryAttempts(1).payload("{\"data\":1}")
                    .streamId("1234-0").sifHeaders(null).build();

            when(deliveryService.deliverNextMessage(eq(queueId), eq("X_DEFAULT_Q_CONSUMER"),
                    eq(""), eq(domainId), eq(solutionId), eq(ownerId)))
                    .thenReturn(Optional.of(msg));

            ResponseEntity<?> response = controller.getNextMessage(
                    domainId, solutionId, queueId, null, "X_DEFAULT_Q_CONSUMER", ownerId.toString(), null, null, mockRequest(), null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getFirst("messageid")).isEqualTo(messageId.toString());

            // D8/D9: the SIF-required response headers are now always constructed, so the set is
            // messageid + timestamp + messageType rather than messageid alone. Asserted as an exact
            // set rather than a count, so this keeps its original intent — "nothing beyond what is
            // required leaks out when the message carries no stored headers" — while surviving a
            // future required-header addition more informatively than a bare number would.
            assertThat(response.getHeaders().keySet())
                    .containsExactlyInAnyOrder("messageid", "timestamp", "messageType");
            // With no stored messageType, the status-derived fallback applies.
            assertThat(response.getHeaders().getFirst("messageType")).isEqualTo("RESPONSE");
            // No requestAction was sent, so responseAction must be absent rather than invented.
            assertThat(response.getHeaders().getFirst("responseAction")).isNull();
        }

        @Test
        @DisplayName("Should return 409 when both header and matrix param provide deleteMessageId")
        void conflictWhenBothHeaderAndMatrixParam_returns409() {
            ResponseEntity<?> response = controller.getNextMessage(
                    domainId, solutionId, queueId, "header-id", "X_DEFAULT_Q_CONSUMER", ownerId.toString(),
                    null, null, mockRequestWithMatrixParam("matrix-id"), null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
        }

        @Test
        @DisplayName("Should resolve deleteMessageId from matrix parameter when header is absent")
        void matrixParam_fallback() {
            when(queueService.getQueueForRouting(queueId)).thenReturn(queueOwnedBy(ownerId));
            when(deliveryService.resolveStreamIdFromMessageId(
                    eq(domainId), eq(solutionId), eq(ownerId), eq(queueId),
                    eq("X_DEFAULT_Q_CONSUMER"), eq("matrix-msg-id")))
                    .thenReturn(null);

            ResponseEntity<?> response = controller.getNextMessage(
                    domainId, solutionId, queueId, null, "X_DEFAULT_Q_CONSUMER", ownerId.toString(),
                    null, null, mockRequestWithMatrixParam("matrix-msg-id"), null);

            // D7: same GONE semantics as the header path — this test is about the matrix-param
            // fallback resolving at all, not about which not-found status is returned.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        }
    }

    // ── getDeadLetterMessages() ─────────────────────────────────────────

    @Nested
    @DisplayName("getDeadLetterMessages() Tests")
    class GetDeadLetterMessagesTests {

        @Test
        @DisplayName("Should return 403 on ownership violation")
        void ownershipViolation_returns403() {
            UUID otherEnv = UUID.randomUUID();
            when(queueService.getQueueDetails(queueId)).thenReturn(queueOwnedBy(ownerId));

            ResponseEntity<?> response = controller.getDeadLetterMessages(
                    domainId, solutionId, queueId, otherEnv.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── resolveEnvironmentId() ──────────────────────────────────────────

    @Nested
    @DisplayName("resolveEnvironmentId() Tests")
    class ResolveEnvironmentIdTests {

        @Test
        @DisplayName("Should return 403 when environmentId header is null (fail-closed)")
        void nullEnvironmentId_returns403() {
            // Fail-closed: null environmentId is denied with 403 per Requirement 3.1
            when(queueService.getQueueDetails(queueId)).thenReturn(queueOwnedBy(ownerId));

            ResponseEntity<?> response = controller.getDeadLetterMessages(
                    domainId, solutionId, queueId, null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(SifErrorResponseEnvelope.class);
            SifErrorResponse error = ((SifErrorResponseEnvelope) response.getBody()).error();
            assertThat(error.description()).contains("Missing X-Environment-Id header");
        }
    }
}
