// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EnqueueResponse DTO.
 * Validates construction, validation annotations, and factory methods.
 */
@DisplayName("EnqueueResponse Tests")
class EnqueueResponseTest {
    
    private Validator validator;
    
    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    @Test
    @DisplayName("Should create response with all valid fields")
    void shouldCreateResponseWithAllValidFields() {
        // Given
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String storageKey = "storage/messages/12345";
        Instant enqueuedAt = Instant.now();
        String status = "ENQUEUED";
        
        // When
        EnqueueResponse response = new EnqueueResponse(
            messageId,
            queueId,
            storageKey,
            enqueuedAt,
            status
        );
        
        // Then
        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.queueId()).isEqualTo(queueId);
        assertThat(response.storageKey()).isEqualTo(storageKey);
        assertThat(response.enqueuedAt()).isEqualTo(enqueuedAt);
        assertThat(response.status()).isEqualTo(status);
    }
    
    @Test
    @DisplayName("Should create response using factory method")
    void shouldCreateResponseUsingFactoryMethod() {
        // Given
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String storageKey = "storage/messages/67890";
        Instant enqueuedAt = Instant.now();
        String status = "PENDING";
        
        // When
        EnqueueResponse response = EnqueueResponse.of(
            messageId,
            queueId,
            storageKey,
            enqueuedAt,
            status
        );
        
        // Then
        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.queueId()).isEqualTo(queueId);
        assertThat(response.storageKey()).isEqualTo(storageKey);
        assertThat(response.enqueuedAt()).isEqualTo(enqueuedAt);
        assertThat(response.status()).isEqualTo(status);
    }
    
    @Test
    @DisplayName("Should pass validation with all valid fields")
    void shouldPassValidationWithAllValidFields() {
        // Given
        EnqueueResponse response = new EnqueueResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "storage/messages/12345",
            Instant.now(),
            "ENQUEUED"
        );
        
        // When
        Set<ConstraintViolation<EnqueueResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).isEmpty();
    }
    
    @Test
    @DisplayName("Should fail validation when messageId is null")
    void shouldFailValidationWhenMessageIdIsNull() {
        // Given
        EnqueueResponse response = new EnqueueResponse(
            null, // Invalid
            UUID.randomUUID(),
            "storage/messages/12345",
            Instant.now(),
            "ENQUEUED"
        );
        
        // When
        Set<ConstraintViolation<EnqueueResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("messageId cannot be null");
    }
    
    @Test
    @DisplayName("Should fail validation when queueId is null")
    void shouldFailValidationWhenQueueIdIsNull() {
        // Given
        EnqueueResponse response = new EnqueueResponse(
            UUID.randomUUID(),
            null, // Invalid
            "storage/messages/12345",
            Instant.now(),
            "ENQUEUED"
        );
        
        // When
        Set<ConstraintViolation<EnqueueResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("queueId cannot be null");
    }
    
    @Test
    @DisplayName("Should fail validation when storageKey is null")
    void shouldFailValidationWhenStorageKeyIsNull() {
        // Given
        EnqueueResponse response = new EnqueueResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null, // Invalid
            Instant.now(),
            "ENQUEUED"
        );
        
        // When
        Set<ConstraintViolation<EnqueueResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("storageKey cannot be blank");
    }
    
    @Test
    @DisplayName("Should fail validation when storageKey is blank")
    void shouldFailValidationWhenStorageKeyIsBlank() {
        // Given
        EnqueueResponse response = new EnqueueResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "   ", // Invalid - blank
            Instant.now(),
            "ENQUEUED"
        );
        
        // When
        Set<ConstraintViolation<EnqueueResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("storageKey cannot be blank");
    }
    
    @Test
    @DisplayName("Should fail validation when enqueuedAt is null")
    void shouldFailValidationWhenEnqueuedAtIsNull() {
        // Given
        EnqueueResponse response = new EnqueueResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "storage/messages/12345",
            null, // Invalid
            "ENQUEUED"
        );
        
        // When
        Set<ConstraintViolation<EnqueueResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("enqueuedAt cannot be null");
    }
    
    @Test
    @DisplayName("Should fail validation when status is null")
    void shouldFailValidationWhenStatusIsNull() {
        // Given
        EnqueueResponse response = new EnqueueResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "storage/messages/12345",
            Instant.now(),
            null // Invalid
        );
        
        // When
        Set<ConstraintViolation<EnqueueResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("status cannot be blank");
    }
    
    @Test
    @DisplayName("Should fail validation when status is blank")
    void shouldFailValidationWhenStatusIsBlank() {
        // Given
        EnqueueResponse response = new EnqueueResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "storage/messages/12345",
            Instant.now(),
            "   " // Invalid - blank
        );
        
        // When
        Set<ConstraintViolation<EnqueueResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("status cannot be blank");
    }
    
    @Test
    @DisplayName("Should handle different status values")
    void shouldHandleDifferentStatusValues() {
        // Test various status values
        String[] statuses = {"ENQUEUED", "PENDING", "DELIVERED", "FAILED"};
        
        for (String status : statuses) {
            EnqueueResponse response = EnqueueResponse.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "storage/messages/test",
                Instant.now(),
                status
            );
            
            assertThat(response.status()).isEqualTo(status);
            
            Set<ConstraintViolation<EnqueueResponse>> violations = validator.validate(response);
            assertThat(violations).isEmpty();
        }
    }
    
    @Test
    @DisplayName("Should handle different storage key formats")
    void shouldHandleDifferentStorageKeyFormats() {
        // Test various storage key formats
        String[] storageKeys = {
            "storage/messages/12345",
            "s3://bucket/messages/67890",
            "/local/storage/message.json",
            "messages/queue-1/msg-abc123"
        };
        
        for (String storageKey : storageKeys) {
            EnqueueResponse response = EnqueueResponse.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                storageKey,
                Instant.now(),
                "ENQUEUED"
            );
            
            assertThat(response.storageKey()).isEqualTo(storageKey);
            
            Set<ConstraintViolation<EnqueueResponse>> violations = validator.validate(response);
            assertThat(violations).isEmpty();
        }
    }
    
    @Test
    @DisplayName("Should preserve timestamp precision")
    void shouldPreserveTimestampPrecision() {
        // Given
        Instant now = Instant.now();
        
        // When
        EnqueueResponse response = EnqueueResponse.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "storage/messages/12345",
            now,
            "ENQUEUED"
        );
        
        // Then
        assertThat(response.enqueuedAt()).isEqualTo(now);
        assertThat(response.enqueuedAt().getNano()).isEqualTo(now.getNano());
    }
    
    @Test
    @DisplayName("Should be immutable")
    void shouldBeImmutable() {
        // Given
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String storageKey = "storage/messages/12345";
        Instant enqueuedAt = Instant.now();
        String status = "ENQUEUED";
        
        // When
        EnqueueResponse response = new EnqueueResponse(
            messageId,
            queueId,
            storageKey,
            enqueuedAt,
            status
        );
        
        // Then - all fields should be accessible and unchanged
        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.queueId()).isEqualTo(queueId);
        assertThat(response.storageKey()).isEqualTo(storageKey);
        assertThat(response.enqueuedAt()).isEqualTo(enqueuedAt);
        assertThat(response.status()).isEqualTo(status);
    }
}
