// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MessageDTO.
 */
class MessageDTOTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void testEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Instant enqueued = Instant.parse("2024-01-01T00:00:00Z");

        MessageDTO dto1 = new MessageDTO(id, queueId, "storage/key", enqueued, null, 0, MessageStatus.PENDING);
        MessageDTO dto2 = new MessageDTO(id, queueId, "storage/key", enqueued, null, 0, MessageStatus.PENDING);

        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    void testToString() {
        UUID id = UUID.randomUUID();
        MessageDTO dto = new MessageDTO(id, UUID.randomUUID(), "storage/key", Instant.now(), null, 0, MessageStatus.PENDING);
        String toString = dto.toString();

        assertThat(toString).contains("MessageDTO");
        assertThat(toString).contains(id.toString());
        assertThat(toString).contains("storage/key");
        assertThat(toString).contains("PENDING");
    }

    @Test
    void testValidation_valid() {
        MessageDTO dto = new MessageDTO(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "storage/key",
            Instant.now(),
            null,
            0,
            MessageStatus.PENDING
        );

        Set<ConstraintViolation<MessageDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void testValidation_nullId() {
        MessageDTO dto = new MessageDTO(null, UUID.randomUUID(), "storage/key", Instant.now(), null, 0, MessageStatus.PENDING);

        Set<ConstraintViolation<MessageDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("id");
    }

    @Test
    void testValidation_nullQueueId() {
        MessageDTO dto = new MessageDTO(UUID.randomUUID(), null, "storage/key", Instant.now(), null, 0, MessageStatus.PENDING);

        Set<ConstraintViolation<MessageDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("queueId");
    }

    @Test
    void testValidation_blankStorageKey() {
        MessageDTO dto = new MessageDTO(UUID.randomUUID(), UUID.randomUUID(), "", Instant.now(), null, 0, MessageStatus.PENDING);

        Set<ConstraintViolation<MessageDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("storageKey");
    }

    @Test
    void testValidation_nullEnqueuedAt() {
        MessageDTO dto = new MessageDTO(UUID.randomUUID(), UUID.randomUUID(), "storage/key", null, null, 0, MessageStatus.PENDING);

        Set<ConstraintViolation<MessageDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("enqueuedAt");
    }

    @Test
    void testValidation_nullStatus() {
        MessageDTO dto = new MessageDTO(UUID.randomUUID(), UUID.randomUUID(), "storage/key", Instant.now(), null, 0, null);

        Set<ConstraintViolation<MessageDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("status");
    }

    @Test
    void testValidation_optionalFieldsCanBeNull() {
        MessageDTO dto = new MessageDTO(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "storage/key",
            Instant.now(),
            null,  // deliveredAt is optional
            null,  // deliveryAttempts is optional
            MessageStatus.PENDING
        );

        Set<ConstraintViolation<MessageDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }
}
