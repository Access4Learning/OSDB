// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EventPublicationRequest DTO.
 * Tests validation annotations and default value handling.
 */
class EventPublicationRequestTest {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldCreateRequestWithAllFields() {
        // Given
        String serviceName = "StudentPersonal";
        String zoneId = "zone-1";
        String contextId = "DEFAULT";
        String messageId = "msg-123";
        Instant timestamp = Instant.now();
        JsonNode payload = objectMapper.createObjectNode().put("test", "data");

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            serviceName, zoneId, contextId, messageId, timestamp, payload, null, null
        );

        // Then
        assertThat(request.serviceName()).isEqualTo(serviceName);
        assertThat(request.zoneId()).isEqualTo(zoneId);
        assertThat(request.contextId()).isEqualTo(contextId);
        assertThat(request.messageId()).isEqualTo(messageId);
        assertThat(request.timestamp()).isEqualTo(timestamp);
        assertThat(request.payload()).isEqualTo(payload);
    }

    @Test
    void shouldDefaultContextIdToDefaultWhenNull() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", null, "msg-123", Instant.now(), payload, null, null
        );

        // Then
        assertThat(request.contextId()).isEqualTo("DEFAULT");
    }

    @Test
    void shouldDefaultContextIdToDefaultWhenBlank() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "  ", "msg-123", Instant.now(), payload, null, null
        );

        // Then
        assertThat(request.contextId()).isEqualTo("DEFAULT");
    }

    @Test
    void shouldGenerateMessageIdWhenNull() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "DEFAULT", null, Instant.now(), payload, null, null
        );

        // Then
        assertThat(request.messageId()).isNotNull();
        assertThat(request.messageId()).isNotBlank();
    }

    @Test
    void shouldGenerateMessageIdWhenBlank() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "DEFAULT", "  ", Instant.now(), payload, null, null
        );

        // Then
        assertThat(request.messageId()).isNotNull();
        assertThat(request.messageId()).isNotBlank();
        assertThat(request.messageId()).isNotEqualTo("  ");
    }

    @Test
    void shouldGenerateTimestampWhenNull() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();
        Instant before = Instant.now();

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "DEFAULT", "msg-123", null, payload, null, null
        );

        // Then
        Instant after = Instant.now();
        assertThat(request.timestamp()).isNotNull();
        assertThat(request.timestamp()).isBetween(before, after);
    }

    @Test
    void shouldValidateSuccessfullyWithValidData() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "DEFAULT", "msg-123", Instant.now(), payload, null, null
        );

        // When
        Set<ConstraintViolation<EventPublicationRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailValidationWhenServiceNameIsNull() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();
        EventPublicationRequest request = new EventPublicationRequest(
            null, "zone-1", "DEFAULT", "msg-123", Instant.now(), payload, null, null
        );

        // When
        Set<ConstraintViolation<EventPublicationRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("serviceName is required");
    }

    @Test
    void shouldFailValidationWhenServiceNameIsBlank() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();
        EventPublicationRequest request = new EventPublicationRequest(
            "  ", "zone-1", "DEFAULT", "msg-123", Instant.now(), payload, null, null
        );

        // When
        Set<ConstraintViolation<EventPublicationRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("serviceName is required");
    }

    @Test
    void shouldFailValidationWhenZoneIdIsNull() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", null, "DEFAULT", "msg-123", Instant.now(), payload, null, null
        );

        // When
        Set<ConstraintViolation<EventPublicationRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("zoneId is required");
    }

    @Test
    void shouldFailValidationWhenZoneIdIsBlank() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "  ", "DEFAULT", "msg-123", Instant.now(), payload, null, null
        );

        // When
        Set<ConstraintViolation<EventPublicationRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("zoneId is required");
    }

    @Test
    void shouldFailValidationWhenPayloadIsNull() {
        // Given
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "DEFAULT", "msg-123", Instant.now(), null, null, null
        );

        // When
        Set<ConstraintViolation<EventPublicationRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("payload is required");
    }

    @Test
    void shouldHandleAllDefaultsSimultaneously() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();
        Instant before = Instant.now();

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", null, null, null, payload, null, null
        );

        // Then
        Instant after = Instant.now();
        assertThat(request.contextId()).isEqualTo("DEFAULT");
        assertThat(request.messageId()).isNotNull().isNotBlank();
        assertThat(request.timestamp()).isNotNull().isBetween(before, after);
    }

    @Test
    void shouldDefaultHeadersToEmptyMapWhenNull() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "DEFAULT", "msg-123", Instant.now(), payload, null, null
        );

        // Then
        assertThat(request.headers()).isNotNull();
        assertThat(request.headers()).isEmpty();
    }

    @Test
    void shouldKeepHeadersWhenProvided() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();
        java.util.Map<String, String> headers = java.util.Map.of("X-Custom", "value");

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "DEFAULT", "msg-123", Instant.now(), payload, headers, null
        );

        // Then
        assertThat(request.headers()).containsEntry("X-Custom", "value");
    }

    @Test
    void shouldKeepContextIdWhenProvided() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "my-context", "msg-123", Instant.now(), payload, null, null
        );

        // Then
        assertThat(request.contextId()).isEqualTo("my-context");
    }

    @Test
    void shouldKeepMessageIdWhenProvided() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "DEFAULT", "my-msg-id", Instant.now(), payload, null, null
        );

        // Then
        assertThat(request.messageId()).isEqualTo("my-msg-id");
    }

    @Test
    void shouldDefaultContextIdWhenEmptyString() {
        // Given
        JsonNode payload = objectMapper.createObjectNode();

        // When
        EventPublicationRequest request = new EventPublicationRequest(
            "StudentPersonal", "zone-1", "", "msg-123", Instant.now(), payload, null, null
        );

        // Then
        assertThat(request.contextId()).isEqualTo("DEFAULT");
    }
}