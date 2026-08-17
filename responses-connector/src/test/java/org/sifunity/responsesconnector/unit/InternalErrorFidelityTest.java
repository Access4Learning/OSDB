// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.responsesconnector.controller.InternalResponseController;
import org.sifunity.responsesconnector.dto.InternalErrorDetail;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.sifunity.responsesconnector.util.MessageBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Regression tests for error-report fidelity on {@code POST /internal/responses/error}.
 *
 * <p><b>The bug these pin down.</b> The handler used to bind {@code @RequestBody String}, while
 * {@code requests-connector} sends an {@code application/json} object. Spring's
 * {@code StringHttpMessageConverter} accepted that happily, so the entire JSON document arrived as
 * the message <em>text</em>. {@code buildInternalErrorEnvelope} then wrapped that text in a fresh
 * {@code SifErrorResponse} hardcoded to 500 / {@code INTERNAL_ERROR} / {@code BROKER}. A consumer
 * therefore saw a broker-internal 500 carrying double-encoded JSON, and the provider-scoped cause
 * the sender had carefully built was recoverable only by re-parsing the message string.
 *
 * <p>The existing integration coverage missed it by posting {@code text/plain} with a bare string —
 * a shape production never sends.
 *
 * <p>Standalone MockMvc is used deliberately: it exercises the real Spring message converters and
 * argument binding (which is where the defect lived) without needing an application context.
 */
@DisplayName("Internal error report fidelity")
class InternalErrorFidelityTest {

    private MockMvc mockMvc;
    private ResponseRoutingService routingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        routingService = mock(ResponseRoutingService.class);
        HeaderSanitizer headerSanitizer = mock(HeaderSanitizer.class);
        when(headerSanitizer.sanitize(any(jakarta.servlet.http.HttpServletRequest.class)))
            .thenReturn(Map.of());

        mockMvc = MockMvcBuilders
            .standaloneSetup(new InternalResponseController(routingService, headerSanitizer))
            .build();
    }

    @Test
    @DisplayName("A production-shaped JSON body binds to fields, not to one flattened string")
    void bindsStructuredFieldsRatherThanFlatteningTheBody() throws Exception {
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();

        when(routingService.routeInternalErrorResponse(anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(ResponseResultDTO.builder()
                .messageId(UUID.randomUUID())
                .status("ERROR_DELIVERED")
                .timestamp(Instant.now())
                .build());

        // Exactly the shape ResponsesConnectorClient.routeErrorResponse sends
        String body = objectMapper.writeValueAsString(Map.of("error", Map.of(
            "code", 502,
            "subCode", "PROVIDER_ERROR",
            "message", "Paging failed on page 3 of 7: provider returned status 503",
            "scope", "PROVIDER")));

        mockMvc.perform(post("/internal/responses/error")
                .header("queueId", queueId.toString())
                .header("requestId", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        ArgumentCaptor<InternalErrorDetail> captor = ArgumentCaptor.forClass(InternalErrorDetail.class);
        verify(routingService).routeInternalErrorResponse(
            anyString(), anyString(), any(), any(), captor.capture(), any());

        InternalErrorDetail received = captor.getValue();
        assertThat(received.codeOrDefault()).isEqualTo(502);
        assertThat(received.subCodeOrDefault()).isEqualTo("PROVIDER_ERROR");
        assertThat(received.scopeOrDefault()).isEqualTo("PROVIDER");
        assertThat(received.message())
            .isEqualTo("Paging failed on page 3 of 7: provider returned status 503");

        // The specific regression: the message must be the message, not the whole document
        assertThat(received.message()).doesNotContain("subCode", "{", "}");
    }

    @Test
    @DisplayName("The envelope preserves the sender's classification instead of hardcoding it")
    void envelopePreservesSenderClassification() {
        MessageBuilder builder = new MessageBuilder();
        String requestId = UUID.randomUUID().toString();

        InternalErrorDetail detail = new InternalErrorDetail(
            504, "PROVIDER_TIMEOUT", "Provider did not respond within 30s", "after 3 retries", "PROVIDER");

        SifMessageEnvelope<SifErrorResponse> envelope =
            builder.buildInternalErrorEnvelope(requestId, detail);

        assertThat(envelope.getRequestId()).isEqualTo(requestId);
        assertThat(envelope.getMessageType()).isEqualTo(MessageType.RESPONSE);

        SifErrorResponse error = envelope.getPayload();
        assertThat(error.code()).isEqualTo(504);
        assertThat(error.subCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(error.scope()).isEqualTo("PROVIDER");
        assertThat(error.description()).isEqualTo("after 3 retries");
        assertThat(error.message()).isEqualTo("Provider did not respond within 30s");
    }

    @Test
    @DisplayName("A message-only report still yields the previous safe defaults")
    void messageOnlyReportFallsBackToInternalErrorDefaults() {
        MessageBuilder builder = new MessageBuilder();

        SifErrorResponse error = builder
            .buildInternalErrorEnvelope(UUID.randomUUID().toString(),
                InternalErrorDetail.ofMessage("something broke"))
            .getPayload();

        assertThat(error.code()).isEqualTo(500);
        assertThat(error.subCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.scope()).isEqualTo("BROKER");
        assertThat(error.message()).isEqualTo("something broke");
    }

    @Test
    @DisplayName("A report from a pre-fix requests-connector is still accepted (rolling deploy)")
    void preFixSenderShapeIsStillAccepted() throws Exception {
        when(routingService.routeInternalErrorResponse(anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(ResponseResultDTO.builder().messageId(UUID.randomUUID())
                .status("ERROR_DELIVERED").timestamp(Instant.now()).build());

        // The shape the PREVIOUS ResponsesConnectorClient sent: the identifier in "code" as a
        // String, no "subCode", plus a "timestamp" the new DTO does not declare.
        String oldSenderBody = objectMapper.writeValueAsString(Map.of("error", Map.of(
            "code", "PROVIDER_ERROR",
            "message", "Provider call failed after 3 retries",
            "scope", "PROVIDER",
            "timestamp", "2026-01-01T00:00:00Z")));

        var result = mockMvc.perform(post("/internal/responses/error")
                .header("queueId", UUID.randomUUID().toString())
                .header("requestId", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(oldSenderBody))
            .andReturn();

        assertThat(result.getResponse().getStatus())
            .as("Binding 'code' strictly as Integer returns 400 here, and the sender only logs the "
                + "failure — so the consumer would never learn its provider failed. Accepting an "
                + "awkward report beats losing it.")
            .isEqualTo(200);

        ArgumentCaptor<InternalErrorDetail> captor = ArgumentCaptor.forClass(InternalErrorDetail.class);
        verify(routingService).routeInternalErrorResponse(
            anyString(), anyString(), any(), any(), captor.capture(), any());

        InternalErrorDetail received = captor.getValue();
        // The legacy identifier is recovered into subCode rather than discarded...
        assertThat(received.subCodeOrDefault()).isEqualTo("PROVIDER_ERROR");
        assertThat(received.scopeOrDefault()).isEqualTo("PROVIDER");
        assertThat(received.message()).isEqualTo("Provider call failed after 3 retries");
        // ...and with no numeric status supplied, the safe default stands in.
        assertThat(received.codeOrDefault()).isEqualTo(500);
    }

    @Test
    @DisplayName("A plain-text body is rejected rather than silently flattened")
    void plainTextBodyIsRejected() throws Exception {
        mockMvc.perform(post("/internal/responses/error")
                .header("queueId", UUID.randomUUID().toString())
                .header("requestId", UUID.randomUUID().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .content("Provider timeout: no response received within 30 seconds"))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
    }
}
