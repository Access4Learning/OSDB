// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MessageBuilder.
 * Covers null-handling branches in buildRequestEnvelope and buildDelayedRequestEnvelope.
 */
class MessageBuilderTest {

    private MessageBuilder messageBuilder;

    @BeforeEach
    void setUp() {
        messageBuilder = new MessageBuilder();
    }

    @Nested
    class BuildRequestEnvelopeTests {

        @Test
        void nullPassthroughHeaders_usesEmptyMap() {
            ImmediateRequestDTO request = new ImmediateRequestDTO();
            request.setServiceName("StudentPersonal");
            request.setServiceType("OBJECT");
            request.setMessageType("REQUEST");
            request.setPayload(Map.of("refId", "123"));

            SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                UUID.randomUUID(), "TestEnv", request, null);

            assertThat(envelope.getHeaders()).isNotNull().isEmpty();
            assertThat(envelope.getServiceName()).isEqualTo("StudentPersonal");
            assertThat(envelope.getMessageType()).isEqualTo(MessageType.REQUEST);
            assertThat(envelope.getServiceType()).isEqualTo(ServiceType.OBJECT);
            assertThat(envelope.getPayload()).isEqualTo(Map.of("refId", "123"));
        }

        @Test
        void withPassthroughHeaders_usesProvidedHeaders() {
            ImmediateRequestDTO request = new ImmediateRequestDTO();
            request.setServiceName("SchoolInfo");
            request.setServiceType("OBJECT");
            request.setMessageType("REQUEST");
            request.setPayload(Map.of("name", "Test School"));

            Map<String, String> headers = Map.of("zoneId", "zone1", "contextId", "ctx1");

            SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                UUID.randomUUID(), "TestEnv", request, headers);

            assertThat(envelope.getHeaders()).containsEntry("zoneId", "zone1");
            assertThat(envelope.getHeaders()).containsEntry("contextId", "ctx1");
        }
    }

    @Nested
    class BuildDelayedRequestEnvelopeTests {

        @Test
        void nullContextId_defaultsToDefault() {
            DelayedRequestDTO request = new DelayedRequestDTO();
            request.setServiceName("StudentPersonal");
            request.setServiceType("OBJECT");
            request.setMessageType("REQUEST");
            request.setPayload(Map.of("refId", "456"));

            SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                UUID.randomUUID(), "TestEnv", UUID.randomUUID(), request,
                Map.of("key", "value"), "zone1", null, null);

            assertThat(envelope.getContextId()).isEqualTo("DEFAULT");
        }

        @Test
        void nonNullContextId_usesProvidedValue() {
            DelayedRequestDTO request = new DelayedRequestDTO();
            request.setServiceName("StudentPersonal");
            request.setServiceType("OBJECT");
            request.setMessageType("REQUEST");
            request.setPayload(Map.of("refId", "789"));

            SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                UUID.randomUUID(), "TestEnv", UUID.randomUUID(), request,
                Map.of("key", "value"), "zone1", "CUSTOM_CONTEXT", null);

            assertThat(envelope.getContextId()).isEqualTo("CUSTOM_CONTEXT");
        }

        @Test
        void nullPassthroughHeaders_usesEmptyHashMap() {
            DelayedRequestDTO request = new DelayedRequestDTO();
            request.setServiceName("StudentPersonal");
            request.setServiceType("OBJECT");
            request.setMessageType("REQUEST");
            request.setPayload(Map.of("refId", "101"));

            SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                UUID.randomUUID(), "TestEnv", UUID.randomUUID(), request,
                null, "zone1", "DEFAULT", null);

            // Headers map is never empty — always contains injected requestId
            assertThat(envelope.getHeaders()).isNotNull().containsKey("requestId");
        }

        @Test
        void nullPayload_usesEmptyMap() {
            DelayedRequestDTO request = new DelayedRequestDTO();
            request.setServiceName("StudentPersonal");
            request.setServiceType("OBJECT");
            request.setMessageType("REQUEST");
            request.setPayload(null);

            SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                UUID.randomUUID(), "TestEnv", UUID.randomUUID(), request,
                Map.of("header", "val"), "zone1", "DEFAULT", null);

            assertThat(envelope.getPayload()).isNotNull();
            assertThat(envelope.getPayload()).isEqualTo(Collections.emptyMap());
        }

        @Test
        void allFieldsPopulated_correctEnvelope() {
            UUID envId = UUID.randomUUID();
            UUID queueId = UUID.randomUUID();

            DelayedRequestDTO request = new DelayedRequestDTO();
            request.setServiceName("SchoolInfo");
            request.setServiceType("OBJECT");
            request.setMessageType("REQUEST");
            request.setPayload(Map.of("schoolName", "Test"));

            Map<String, String> headers = Map.of("queryIntention", "ALL");

            SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                envId, "ProdEnv", queueId, request,
                headers, "production", "DEFAULT", "test-request-id");

            assertThat(envelope.getMessageId()).isNotNull();
            assertThat(envelope.getTimestamp()).isNotNull();
            assertThat(envelope.getMessageType()).isEqualTo(MessageType.REQUEST);
            assertThat(envelope.getServiceName()).isEqualTo("SchoolInfo");
            assertThat(envelope.getServiceType()).isEqualTo(ServiceType.OBJECT);
            assertThat(envelope.getSourceId()).isEqualTo(envId);
            assertThat(envelope.getQueueId()).isEqualTo(queueId);
            assertThat(envelope.getZoneId()).isEqualTo("production");
            assertThat(envelope.getContextId()).isEqualTo("DEFAULT");
            assertThat(envelope.getHeaders()).containsEntry("queryIntention", "ALL");
            assertThat(envelope.getPayload()).isEqualTo(Map.of("schoolName", "Test"));
        }
    }
}
