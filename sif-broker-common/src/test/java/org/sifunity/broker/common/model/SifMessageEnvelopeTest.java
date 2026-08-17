// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SifMessageEnvelope.
 */
class SifMessageEnvelopeTest {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ========== Unit Tests for Envelope Creation ==========

    @Test
    void testEnvelopeCreation_withAllRequiredFields() {
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload("test payload")
            .build();

        assertThat(envelope).isNotNull();
        assertThat(envelope.getMessageType()).isEqualTo(MessageType.REQUEST);
        assertThat(envelope.getServiceName()).isEqualTo("StudentPersonal");
        assertThat(envelope.getServiceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(envelope.getPayload()).isEqualTo("test payload");
    }

    @Test
    void testEnvelopeCreation_withOptionalFields() {
        String requestId = UUID.randomUUID().toString();
        UUID queueId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .zoneId("zone1")
            .contextId("context1")
            .requestId(requestId)
            .queueId(queueId)
            .sourceId(sourceId)
            .payload("test payload")
            .build();

        assertThat(envelope.getZoneId()).isEqualTo("zone1");
        assertThat(envelope.getContextId()).isEqualTo("context1");
        assertThat(envelope.getRequestId()).isEqualTo(requestId);
        assertThat(envelope.getQueueId()).isEqualTo(queueId);
        assertThat(envelope.getSourceId()).isEqualTo(sourceId);
    }

    @Test
    void testEnvelopeCreation_withNullPayload() {
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.EVENT)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(null)
            .build();

        assertThat(envelope.getPayload()).isNull();
    }

    // ========== Unit Tests for Auto-Generation ==========

    @Test
    void testAutoGeneration_messageIdIsGenerated() {
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .build();

        assertThat(envelope.getMessageId()).isNotNull();
    }

    @Test
    void testAutoGeneration_timestampIsGenerated() {
        Instant before = Instant.now();
        
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .build();

        Instant after = Instant.now();

        assertThat(envelope.getTimestamp()).isNotNull();
        assertThat(envelope.getTimestamp()).isBetween(before, after);
    }

    @Test
    void testAutoGeneration_messageIdIsUnique() {
        SifMessageEnvelope<String> envelope1 = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .build();

        SifMessageEnvelope<String> envelope2 = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .build();

        assertThat(envelope1.getMessageId()).isNotEqualTo(envelope2.getMessageId());
    }

    @Test
    void testAutoGeneration_canOverrideMessageId() {
        UUID customMessageId = UUID.randomUUID();

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageId(customMessageId)
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .build();

        assertThat(envelope.getMessageId()).isEqualTo(customMessageId);
    }

    @Test
    void testAutoGeneration_canOverrideTimestamp() {
        Instant customTimestamp = Instant.parse("2024-01-01T00:00:00Z");

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .timestamp(customTimestamp)
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .build();

        assertThat(envelope.getTimestamp()).isEqualTo(customTimestamp);
    }

    // ========== Unit Tests for JSON Serialization/Deserialization ==========

    @Test
    void testJsonSerialization_withRequiredFields() throws Exception {
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
            .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload("test payload")
            .build();

        String json = objectMapper.writeValueAsString(envelope);

        assertThat(json).contains("\"messageId\":\"123e4567-e89b-12d3-a456-426614174000\"");
        assertThat(json).contains("\"timestamp\":\"2024-01-01T00:00:00Z\"");
        assertThat(json).contains("\"messageType\":\"REQUEST\"");
        assertThat(json).contains("\"serviceName\":\"StudentPersonal\"");
        assertThat(json).contains("\"serviceType\":\"OBJECT\"");
        assertThat(json).contains("\"payload\":\"test payload\"");
    }

    @Test
    void testJsonDeserialization_withRequiredFields() throws Exception {
        String json = """
            {
                "messageId": "123e4567-e89b-12d3-a456-426614174000",
                "timestamp": "2024-01-01T00:00:00Z",
                "messageType": "REQUEST",
                "serviceName": "StudentPersonal",
                "serviceType": "OBJECT",
                "payload": "test payload"
            }
            """;

        SifMessageEnvelope<?> envelope = objectMapper.readValue(json, SifMessageEnvelope.class);

        assertThat(envelope.getMessageId()).isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThat(envelope.getTimestamp()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(envelope.getMessageType()).isEqualTo(MessageType.REQUEST);
        assertThat(envelope.getServiceName()).isEqualTo("StudentPersonal");
        assertThat(envelope.getServiceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(envelope.getPayload()).isEqualTo("test payload");
    }

    @Test
    void testJsonSerialization_withOptionalFields() throws Exception {
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
            .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
            .messageType(MessageType.RESPONSE)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .zoneId("zone1")
            .contextId("context1")
            .requestId("223e4567-e89b-12d3-a456-426614174000")
            .queueId(UUID.fromString("323e4567-e89b-12d3-a456-426614174000"))
            .sourceId(UUID.fromString("423e4567-e89b-12d3-a456-426614174000"))
            .payload("test payload")
            .build();

        String json = objectMapper.writeValueAsString(envelope);

        assertThat(json).contains("\"zoneId\":\"zone1\"");
        assertThat(json).contains("\"contextId\":\"context1\"");
        assertThat(json).contains("\"requestId\":\"223e4567-e89b-12d3-a456-426614174000\"");
        assertThat(json).contains("\"queueId\":\"323e4567-e89b-12d3-a456-426614174000\"");
        assertThat(json).contains("\"sourceId\":\"423e4567-e89b-12d3-a456-426614174000\"");
    }

    @Test
    void testJsonDeserialization_withOptionalFields() throws Exception {
        String json = """
            {
                "messageId": "123e4567-e89b-12d3-a456-426614174000",
                "timestamp": "2024-01-01T00:00:00Z",
                "messageType": "RESPONSE",
                "serviceName": "StudentPersonal",
                "serviceType": "OBJECT",
                "zoneId": "zone1",
                "contextId": "context1",
                "requestId": "223e4567-e89b-12d3-a456-426614174000",
                "queueId": "323e4567-e89b-12d3-a456-426614174000",
                "sourceId": "423e4567-e89b-12d3-a456-426614174000",
                "payload": "test payload"
            }
            """;

        SifMessageEnvelope<?> envelope = objectMapper.readValue(json, SifMessageEnvelope.class);

        assertThat(envelope.getZoneId()).isEqualTo("zone1");
        assertThat(envelope.getContextId()).isEqualTo("context1");
        assertThat(envelope.getRequestId()).isEqualTo("223e4567-e89b-12d3-a456-426614174000");
        assertThat(envelope.getQueueId()).isEqualTo(UUID.fromString("323e4567-e89b-12d3-a456-426614174000"));
        assertThat(envelope.getSourceId()).isEqualTo(UUID.fromString("423e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void testJsonRoundTrip_preservesAllFields() throws Exception {
        SifMessageEnvelope<String> original = SifMessageEnvelope.<String>builder()
            .messageId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
            .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
            .messageType(MessageType.EVENT)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .zoneId("zone1")
            .contextId("context1")
            .payload("test payload")
            .build();

        String json = objectMapper.writeValueAsString(original);
        SifMessageEnvelope<?> deserialized = objectMapper.readValue(json, SifMessageEnvelope.class);

        assertThat(deserialized.getMessageId()).isEqualTo(original.getMessageId());
        assertThat(deserialized.getTimestamp()).isEqualTo(original.getTimestamp());
        assertThat(deserialized.getMessageType()).isEqualTo(original.getMessageType());
        assertThat(deserialized.getServiceName()).isEqualTo(original.getServiceName());
        assertThat(deserialized.getServiceType()).isEqualTo(original.getServiceType());
        assertThat(deserialized.getZoneId()).isEqualTo(original.getZoneId());
        assertThat(deserialized.getContextId()).isEqualTo(original.getContextId());
        assertThat(deserialized.getPayload()).isEqualTo(original.getPayload());
    }

    // ========== Validation Tests ==========

    @Test
    void testValidation_valid() {
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .build();

        Set<ConstraintViolation<SifMessageEnvelope<String>>> violations = validator.validate(envelope);
        assertThat(violations).isEmpty();
    }

    @Test
    void testValidation_nullMessageType() {
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(null)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .build();

        Set<ConstraintViolation<SifMessageEnvelope<String>>> violations = validator.validate(envelope);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("messageType");
    }

    @Test
    void testValidation_blankServiceName() {
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.REQUEST)
            .serviceName("")
            .serviceType(ServiceType.OBJECT)
            .build();

        Set<ConstraintViolation<SifMessageEnvelope<String>>> violations = validator.validate(envelope);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("serviceName");
    }

    @Test
    void testValidation_nullServiceType() {
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(null)
            .build();

        Set<ConstraintViolation<SifMessageEnvelope<String>>> violations = validator.validate(envelope);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("serviceType");
    }
}
