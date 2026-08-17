// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.util.MessageBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for the requestId fix in {@code buildDelayedRequestEnvelope}.
 *
 * <p>Validates that the builder always produces an envelope with a consistent,
 * non-null requestId — either the consumer-supplied token or the generated
 * messageId — present in both the envelope field and the headers map.</p>
 */
class DelayedRequestIdPropertyTest {

    private final MessageBuilder messageBuilder = new MessageBuilder();

    // ── Property 1: Generated fallback — requestId == messageId.toString() ───

    @Property(tries = 20)
    @Label("When no consumer requestId is supplied, envelope requestId equals messageId")
    void generatedFallback_requestIdEqualsMessageId(
            @ForAll("serviceNames") String serviceName) {

        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");

        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                UUID.randomUUID(), "test-env",
                UUID.randomUUID(), request,
                new HashMap<>(), "zone-1", "DEFAULT",
                null);  // no consumer-supplied requestId

        assertThat(envelope.getRequestId())
                .as("requestId field must be non-null")
                .isNotNull()
                .isNotBlank();

        assertThat(envelope.getRequestId())
                .as("requestId must equal messageId when no consumer token supplied")
                .isEqualTo(envelope.getMessageId().toString());

        assertThat(envelope.getHeaders().get("requestId"))
                .as("requestId must also be in headers map for provider-facing path")
                .isEqualTo(envelope.getRequestId());
    }

    // ── Property 2: Consumer-supplied token is preserved verbatim ─────────────

    @Property(tries = 20)
    @Label("Consumer-supplied requestId is preserved verbatim in field and headers")
    void consumerSupplied_preservedVerbatim(
            @ForAll("nonBlankTokens") String consumerToken,
            @ForAll("serviceNames") String serviceName) {

        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");

        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                UUID.randomUUID(), "test-env",
                UUID.randomUUID(), request,
                new HashMap<>(), "zone-1", "DEFAULT",
                consumerToken);

        assertThat(envelope.getRequestId())
                .as("requestId field must be the consumer-supplied token")
                .isEqualTo(consumerToken);

        assertThat(envelope.getHeaders().get("requestId"))
                .as("requestId header must match the consumer-supplied token")
                .isEqualTo(consumerToken);

        // messageId should be different from consumer token (it's a fresh UUID)
        assertThat(envelope.getMessageId().toString())
                .as("messageId should be independent of consumer-supplied requestId")
                .isNotEqualTo(consumerToken);
    }

    // ── Property 3: Blank consumer token falls back to generated ──────────────

    @Property(tries = 20)
    @Label("Blank consumer requestId falls back to generated messageId")
    void blankConsumerToken_fallsBackToGenerated(
            @ForAll("blankStrings") String blankToken,
            @ForAll("serviceNames") String serviceName) {

        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName(serviceName);
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");

        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                UUID.randomUUID(), "test-env",
                UUID.randomUUID(), request,
                new HashMap<>(), "zone-1", "DEFAULT",
                blankToken);

        assertThat(envelope.getRequestId())
                .as("requestId must fall back to messageId for blank tokens")
                .isEqualTo(envelope.getMessageId().toString());
    }

    // ── Property 4: Integer consumer token (SIF-compliant) ───────────────────

    @Property(tries = 20)
    @Label("Integer consumer requestId (per SIF spec) is preserved as-is")
    void integerToken_preserved(
            @ForAll("positiveIntegers") int token) {

        String consumerToken = String.valueOf(token);

        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");

        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                UUID.randomUUID(), "test-env",
                UUID.randomUUID(), request,
                new HashMap<>(), "zone-1", "DEFAULT",
                consumerToken);

        assertThat(envelope.getRequestId())
                .as("Integer requestId per SIF spec must be preserved")
                .isEqualTo(consumerToken);

        assertThat(envelope.getHeaders().get("requestId"))
                .isEqualTo(consumerToken);
    }

    // ── Arbitraries ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> nonBlankTokens() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(64)
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
                "StudentPersonal", "SchoolInfo", "StudentSchoolEnrollment",
                "personnel__persons", "chronicle__course-entries", "course__courses");
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", null);
    }

    @Provide
    Arbitrary<Integer> positiveIntegers() {
        return Arbitraries.integers().between(1, 999999);
    }
}
