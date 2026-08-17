// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.controller.MessageController;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.service.DeadLetterService;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Bug-condition tests for D7, D8 and D9 — undeclared statuses and missing required response headers
 * on the queue-message operations.
 *
 * <p><strong>THESE ARE EXPECTED TO FAIL until waves 2 fixes land</strong> (tasks 7 and 8a). Failure is
 * the point: each is the executable statement of a defect, and each turns green as its fix lands. Do
 * not "fix" them by weakening an assertion.</p>
 *
 * <h2>Why the queue-message operations are the entire header surface</h2>
 *
 * <p>Header conformance applies only to operations that <em>declare</em> response headers. In
 * `OpenAPI.json` the infrastructure object operations (`/environments/environment`,
 * `/subscriptions/subscription`, `/providers/provider`, `/alerts/alert`, `/codeSets`, `/xquerys/xquery`,
 * `/zones`, …) declare none — their responses list status codes only — so the absence of SIF message
 * headers on those 250+ exchanges is explicitly <b>not</b> a defect. Only `queueMessageGet` and
 * `queueMessageDelete` (via `infraPaths.yaml`) plus the unexercised job-phase paths declare headers.
 * That leaves the 33 queue-message exchanges as the whole surface.</p>
 *
 * <h2>The mechanism behind D8</h2>
 *
 * <p>{@code timestamp} is not merely forgotten — {@code MessageController}'s
 * {@code RESPONSE_HEADER_BLACKLIST} lists it explicitly, so even a message carrying one has it
 * stripped on the way out. The fix therefore cannot be "forward more of the stored headers"; the
 * response headers have to be constructed.</p>
 *
 * <p>Standalone MockMvc with mocked services on purpose: this exercises real Spring status and header
 * handling without Redis or a socket, so it runs in a plain dev environment as well as CI.</p>
 *
 * <p><strong>Validates: bug condition 1.14-1.19; Requirements 2.14, 2.15, 2.17, 2.18, 2.19</strong></p>
 */
@DisplayName("SIF wire-format conformance — queue-message statuses and headers (bug conditions)")
class QueueMessageStatusBugConditionExplorationTest {

    private static final String DOMAIN = "test-domain";
    private static final String SOLUTION = "test-solution";
    private static final String TOKEN = "test-internal-token";

    private MockMvc mockMvc;
    private DeliveryService deliveryService;

    private UUID queueId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        MessageService messageService = mock(MessageService.class);
        deliveryService = mock(DeliveryService.class);
        DeadLetterService deadLetterService = mock(DeadLetterService.class);
        QueueService queueService = mock(QueueService.class);

        queueId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        // Ownership passes, so every assertion below is about the status/headers rather than authz.
        when(queueService.getQueueForRouting(any(UUID.class)))
                .thenReturn(new QueueDTO(queueId, "cert-queue", ownerId, null, null, null,
                        java.time.Instant.now(), null, null, null, null, null));

        mockMvc = MockMvcBuilders.standaloneSetup(new MessageController(
                messageService, deliveryService, deadLetterService, queueService, TOKEN)).build();
    }

    // ==================== D7 — undeclared status codes ====================

    @Nested
    @DisplayName("D7 — undeclared status codes")
    class UndeclaredStatuses {

        /**
         * Bug condition 1.14 → Requirement 2.14. A {@code deleteMessageId} that is no longer at the
         * head of the pending-entries list means the message is <em>gone</em>, not that the resource
         * was never there. {@code queueMessageGet} does not declare 404.
         */
        @Test
        @DisplayName("GET with a stale deleteMessageId returns 410 GONE, not the undeclared 404")
        void staleDeleteMessageIdYields410() throws Exception {
            when(deliveryService.resolveStreamIdFromMessageId(
                    anyString(), anyString(), any(UUID.class), any(UUID.class), anyString(), anyString()))
                    .thenReturn(null);

            MvcResult result = mockMvc.perform(
                    get("/{d}/{s}/queues/{q}/messages", DOMAIN, SOLUTION, queueId)
                            .header("X-Environment-Id", ownerId.toString())
                            .header("deleteMessageId", UUID.randomUUID().toString()))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("queueMessageGet declares no 404; a message absent from the PEL is GONE")
                    .isEqualTo(410);
            assertThat(result.getResponse().getContentAsString())
                    .as("the subCode must move with the status")
                    .contains("MSG_410");
        }

        /**
         * Bug condition 1.15 → Requirement 2.15. Same reasoning on the DELETE path.
         *
         * <p>Note {@code QueueNotFoundException} → 404 is declared and conformant, and must be left
         * alone; this is only about a missing <em>message</em> in an existing queue.</p>
         */
        @Test
        @DisplayName("DELETE of a message not in the queue returns 410 GONE, not the undeclared 404")
        void deletingAbsentMessageYields410() throws Exception {
            when(deliveryService.deleteMessage(any(UUID.class), anyString(), anyString(), anyString(), any(UUID.class)))
                    .thenReturn(false);

            MvcResult result = mockMvc.perform(
                    delete("/{d}/{s}/queues/{q}/messages/{m}", DOMAIN, SOLUTION, queueId, UUID.randomUUID())
                            .header("X-Environment-Id", ownerId.toString()))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("queueMessageDelete declares no 404 for an absent message")
                    .isEqualTo(410);
            assertThat(result.getResponse().getContentAsString()).contains("MSG_410");
        }

        /**
         * Bug condition 1.16 → Requirement 2.15. Recorded as an <em>observation</em>: the audit found
         * a 202 on this path, but the current implementation already returns 204. If this passes on
         * unfixed code, 1.16 has been fixed since the audit and task 7 should say so rather than
         * changing anything.
         */
        @Test
        @DisplayName("OBSERVATION — a successful DELETE returns the declared 204, not 202")
        void successfulDeleteYields204() throws Exception {
            when(deliveryService.deleteMessage(any(UUID.class), anyString(), anyString(), anyString(), any(UUID.class)))
                    .thenReturn(true);

            MvcResult result = mockMvc.perform(
                    delete("/{d}/{s}/queues/{q}/messages/{m}", DOMAIN, SOLUTION, queueId, UUID.randomUUID())
                            .header("X-Environment-Id", ownerId.toString()))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("202 is not declared for queueMessageDelete; 204 is")
                    .isEqualTo(204);
        }
    }

    // ==================== D8 / D9 — required response headers ====================

    @Nested
    @DisplayName("D8/D9 — required response headers")
    class RequiredHeaders {

        /**
         * Bug condition 1.18 → Requirement 2.17. {@code commonDefs.yaml} marks {@code timestamp}
         * required on every SIF response. It is currently blacklisted out of the 200 path entirely.
         */
        @Test
        @DisplayName("A delivered message carries the required timestamp header")
        void deliveredMessageCarriesTimestamp() throws Exception {
            stubDeliveredMessage();

            MvcResult result = performGet();

            assertThat(result.getResponse().getHeader("timestamp"))
                    .as("timestamp is required on every SIF response, and is currently in "
                            + "RESPONSE_HEADER_BLACKLIST so it is actively stripped")
                    .isNotNull();
        }

        /**
         * Bug condition 1.19 → Requirement 2.18. {@code responseAction} must echo the request's
         * {@code requestAction}.
         */
        @Test
        @DisplayName("A delivered message echoes requestAction as responseAction")
        void deliveredMessageEchoesResponseAction() throws Exception {
            stubDeliveredMessage();

            MvcResult result = mockMvc.perform(
                    get("/{d}/{s}/queues/{q}/messages", DOMAIN, SOLUTION, queueId)
                            .header("X-Environment-Id", ownerId.toString())
                            .header("requestAction", "QUERY"))
                    .andReturn();

            assertThat(result.getResponse().getHeader("responseAction"))
                    .as("responseAction is required and must echo the request's requestAction")
                    .isEqualTo("QUERY");
        }

        /**
         * Requirement 2.19. The required headers belong on <em>every</em> declared branch, not just
         * the 200. An empty queue is the branch most easily missed because it has no body to carry
         * correlating information.
         */
        @Test
        @DisplayName("An empty-queue 204 still carries the required headers")
        void emptyQueueResponseCarriesRequiredHeaders() throws Exception {
            when(deliveryService.deliverNextMessage(
                    any(UUID.class), anyString(), anyString(), anyString(), anyString(), any(UUID.class)))
                    .thenReturn(Optional.empty());

            MvcResult result = performGet();

            assertThat(result.getResponse().getStatus()).isEqualTo(204);
            assertThat(result.getResponse().getHeader("timestamp"))
                    .as("a 204 is still a SIF response and still declares required headers")
                    .isNotNull();
            assertThat(result.getResponse().getHeader("messageType"))
                    .as("messageType must be RESPONSE for a non-error branch")
                    .isEqualTo("RESPONSE");
        }

        /**
         * Requirement 2.19. On an error branch {@code messageType} must be {@code ERROR}, which is the
         * part a shared header builder is most likely to get uniformly wrong.
         */
        @Test
        @DisplayName("An error branch carries messageType ERROR and the required headers")
        void errorBranchCarriesErrorMessageType() throws Exception {
            when(deliveryService.deleteMessage(any(UUID.class), anyString(), anyString(), anyString(), any(UUID.class)))
                    .thenReturn(false);

            MvcResult result = mockMvc.perform(
                    delete("/{d}/{s}/queues/{q}/messages/{m}", DOMAIN, SOLUTION, queueId, UUID.randomUUID())
                            .header("X-Environment-Id", ownerId.toString()))
                    .andReturn();

            assertThat(result.getResponse().getHeader("timestamp")).isNotNull();
            assertThat(result.getResponse().getHeader("messageType"))
                    .as("status >= 300 must report messageType ERROR, not RESPONSE")
                    .isEqualTo("ERROR");
        }
    }

    // ==================== helpers ====================

    private void stubDeliveredMessage() {
        MessageMetadata msg = new MessageMetadata();
        msg.setMessageId(UUID.randomUUID());
        msg.setQueueId(queueId);
        msg.setPayload(Map.of("refId", "abc123"));
        msg.setSifHeaders(Map.of());

        when(deliveryService.deliverNextMessage(
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), any(UUID.class)))
                .thenReturn(Optional.of(msg));
    }

    private MvcResult performGet() throws Exception {
        return mockMvc.perform(
                get("/{d}/{s}/queues/{q}/messages", DOMAIN, SOLUTION, queueId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andReturn();
    }
}
