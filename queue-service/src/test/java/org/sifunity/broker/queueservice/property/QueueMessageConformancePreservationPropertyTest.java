// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.Instant;
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
 * Preservation properties for the queue-message operations.
 *
 * <p><strong>These MUST pass both before and after wave 2's tasks 7 and 8a.</strong> Written
 * observation-first against unfixed code, they pin the behaviour those fixes must not disturb.</p>
 *
 * <h2>Why each one is here</h2>
 *
 * <p>Task 7 substitutes 404 → 410 on two branches and task 8a introduces a <em>shared</em> header
 * builder used by every response branch. Both are sweeping edits to a controller whose other statuses
 * are already conformant, so the realistic failure mode is collateral:</p>
 *
 * <ul>
 *   <li><b>A blanket 404 → 410 replacement.</b> The spec is explicit that
 *       {@code QueueNotFoundException} → 404 is declared and must be left untouched. Only a
 *       <em>message</em> missing from an existing queue becomes 410. A search-and-replace across the
 *       controller would take the queue-level 404 with it.</li>
 *   <li><b>Authorization outcomes.</b> The ownership 403 sits in the same methods being edited and
 *       must survive unchanged.</li>
 *   <li><b>Existing headers.</b> A shared header builder that <em>replaces</em> rather than
 *       <em>extends</em> the current 200-path headers would drop {@code messageid} — which consumers
 *       feed back as {@code deleteMessageId} on the next poll, so losing it breaks the ACK-and-get-next
 *       cycle — and would drop the forwarded SIF headers.</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 3.7, 3.8, 3.12, 3.13 (preservation)</strong></p>
 */
@DisplayName("SIF wire-format conformance — queue-message preservation")
class QueueMessageConformancePreservationPropertyTest {

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

        when(queueService.getQueueForRouting(any(UUID.class)))
                .thenReturn(new QueueDTO(queueId, "cert-queue", ownerId, null, null, null,
                        Instant.now(), null, null, null, null, null));

        mockMvc = MockMvcBuilders.standaloneSetup(new MessageController(
                messageService, deliveryService, deadLetterService, queueService, TOKEN)).build();
    }

    /**
     * The ownership 403 lives in the same methods task 7 edits and must survive. Note the caller here
     * is a non-owner with no broker-services fingerprint.
     */
    @Test
    @DisplayName("An ownership violation still returns 403")
    void ownershipViolationStillReturns403() throws Exception {
        MvcResult result = mockMvc.perform(
                delete("/{d}/{s}/queues/{q}/messages/{m}", DOMAIN, SOLUTION, queueId, UUID.randomUUID())
                        .header("X-Environment-Id", UUID.randomUUID().toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("authorization outcomes are out of scope for the status substitution and must not move")
                .isEqualTo(403);
    }

    /**
     * Fail-closed on an absent environment id must also survive — it is the same branch as above but
     * reached by a different route, and it is the one a refactor is most likely to loosen.
     */
    @Test
    @DisplayName("A missing environment id still fails closed with 403")
    void missingEnvironmentIdStillFailsClosed() throws Exception {
        MvcResult result = mockMvc.perform(
                delete("/{d}/{s}/queues/{q}/messages/{m}", DOMAIN, SOLUTION, queueId, UUID.randomUUID()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    /**
     * A successful DELETE is 204 today and stays 204. Task 7 changes only the not-found branch.
     */
    @Test
    @DisplayName("A successful DELETE still returns 204")
    void successfulDeleteStillReturns204() throws Exception {
        when(deliveryService.deleteMessage(any(UUID.class), anyString(), anyString(), anyString(), any(UUID.class)))
                .thenReturn(true);

        MvcResult result = mockMvc.perform(
                delete("/{d}/{s}/queues/{q}/messages/{m}", DOMAIN, SOLUTION, queueId, UUID.randomUUID())
                        .header("X-Environment-Id", ownerId.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
    }

    /**
     * <strong>The most load-bearing property here.</strong> {@code messageid} is what the consumer
     * sends back as {@code deleteMessageId} on its next poll, so a header builder that replaces rather
     * than extends the 200-path headers would silently break the ACK-and-get-next cycle while every
     * conformance assertion still passed.
     */
    @Test
    @DisplayName("A delivered message still carries messageid, and still forwards its SIF headers")
    void deliveredMessageStillCarriesMessageIdAndForwardedHeaders() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageMetadata msg = new MessageMetadata();
        msg.setMessageId(messageId);
        msg.setQueueId(queueId);
        msg.setPayload(Map.of("refId", "abc123"));
        msg.setSifHeaders(Map.of(
                "messageType", "RESPONSE",
                "serviceType", "OBJECT",
                "relativeServicePath", "StudentPersonals"));

        when(deliveryService.deliverNextMessage(
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), any(UUID.class)))
                .thenReturn(Optional.of(msg));

        MvcResult result = mockMvc.perform(
                get("/{d}/{s}/queues/{q}/messages", DOMAIN, SOLUTION, queueId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("messageid"))
                .as("consumers feed messageid back as deleteMessageId; losing it breaks "
                        + "ACK-and-get-next without violating any schema")
                .isEqualTo(messageId.toString());
        assertThat(result.getResponse().getHeader("serviceType"))
                .as("stored SIF headers must continue to be forwarded")
                .isEqualTo("OBJECT");
        assertThat(result.getResponse().getHeader("relativeServicePath"))
                .isEqualTo("StudentPersonals");
    }

    /**
     * The blacklist exists for a reason and must keep working: request-only and transport headers must
     * not leak into the response even once a header builder is introduced.
     */
    @Test
    @DisplayName("Blacklisted request headers are still not echoed into the response")
    void blacklistedHeadersAreStillSuppressed() throws Exception {
        MessageMetadata msg = new MessageMetadata();
        msg.setMessageId(UUID.randomUUID());
        msg.setQueueId(queueId);
        msg.setPayload(Map.of("refId", "abc123"));
        msg.setSifHeaders(Map.of(
                "authorization", "Basic c2hvdWxkLW5vdC1sZWFr",
                "x-environment-id", ownerId.toString(),
                "serviceType", "OBJECT"));

        when(deliveryService.deliverNextMessage(
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), any(UUID.class)))
                .thenReturn(Optional.of(msg));

        MvcResult result = mockMvc.perform(
                get("/{d}/{s}/queues/{q}/messages", DOMAIN, SOLUTION, queueId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andReturn();

        assertThat(result.getResponse().getHeader("authorization"))
                .as("a stored authorization header must never be echoed back")
                .isNull();
        assertThat(result.getResponse().getHeader("x-environment-id")).isNull();
        assertThat(result.getResponse().getHeader("serviceType"))
                .as("suppression must stay selective — non-blacklisted headers still pass")
                .isEqualTo("OBJECT");
    }
}
