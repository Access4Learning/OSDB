// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for QueueDTO.
 */
class QueueDTOTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private QueueDTO createDTO(UUID id, String name, UUID ownerId, Instant created,
                               Integer maxRetries, Long retryDelayMs, Long messageCount) {
        return new QueueDTO(id, name, ownerId, "IMMEDIATE", null, 1,
                           created, null, null, maxRetries, retryDelayMs, messageCount);
    }

    @Test
    void testEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant created = Instant.parse("2024-01-01T00:00:00Z");

        QueueDTO dto1 = createDTO(id, "test-queue", ownerId, created, 3, 5000L, 0L);
        QueueDTO dto2 = createDTO(id, "test-queue", ownerId, created, 3, 5000L, 0L);

        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    void testToString() {
        UUID id = UUID.randomUUID();
        QueueDTO dto = createDTO(id, "test-queue", UUID.randomUUID(), Instant.now(), 3, 5000L, 10L);
        String toString = dto.toString();

        assertThat(toString).contains("QueueDTO");
        assertThat(toString).contains(id.toString());
        assertThat(toString).contains("test-queue");
    }

    @Test
    void testValidation_valid() {
        QueueDTO dto = createDTO(UUID.randomUUID(), "test-queue", UUID.randomUUID(),
                                 Instant.now(), 3, 5000L, 0L);

        Set<ConstraintViolation<QueueDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void testValidation_nullId() {
        QueueDTO dto = createDTO(null, "test-queue", UUID.randomUUID(), Instant.now(), 3, 5000L, 0L);

        Set<ConstraintViolation<QueueDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("id");
    }

    @Test
    void testValidation_blankName() {
        QueueDTO dto = createDTO(UUID.randomUUID(), "", UUID.randomUUID(), Instant.now(), 3, 5000L, 0L);

        Set<ConstraintViolation<QueueDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("name");
    }

    @Test
    void testValidation_nullOwnerId() {
        QueueDTO dto = createDTO(UUID.randomUUID(), "test-queue", null, Instant.now(), 3, 5000L, 0L);

        Set<ConstraintViolation<QueueDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("ownerId");
    }

    @Test
    void testValidation_nullCreated() {
        QueueDTO dto = createDTO(UUID.randomUUID(), "test-queue", UUID.randomUUID(), null, 3, 5000L, 0L);

        Set<ConstraintViolation<QueueDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("created");
    }

    @Test
    void testValidation_maxRetriesCanBeNull() {
        // maxRetries is an internal-only field (no longer @NotNull); null is valid and is
        // excluded from API JSON per SIF 3.7.
        QueueDTO dto = createDTO(UUID.randomUUID(), "test-queue", UUID.randomUUID(), Instant.now(), null, 5000L, 0L);

        Set<ConstraintViolation<QueueDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void testValidation_retryDelayMsCanBeNull() {
        // retryDelayMs is an internal-only field (no longer @NotNull); null is valid and is
        // excluded from API JSON per SIF 3.7.
        QueueDTO dto = createDTO(UUID.randomUUID(), "test-queue", UUID.randomUUID(), Instant.now(), 3, null, 0L);

        Set<ConstraintViolation<QueueDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void testValidation_messageCountCanBeNull() {
        QueueDTO dto = createDTO(UUID.randomUUID(), "test-queue", UUID.randomUUID(), Instant.now(), 3, 5000L, null);

        Set<ConstraintViolation<QueueDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }
}
