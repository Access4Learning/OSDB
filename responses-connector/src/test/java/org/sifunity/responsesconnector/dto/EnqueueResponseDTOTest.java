// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EnqueueResponseDTO.
 * Tests field handling and builder pattern.
 */
class EnqueueResponseDTOTest {

    @Test
    void shouldCreateResponseWithAllFields() {
        // Given
        UUID messageId = UUID.randomUUID();
        String status = "ENQUEUED";

        // When
        EnqueueResponseDTO response = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status(status)
            .build();

        // Then
        assertThat(response.getMessageId()).isEqualTo(messageId);
        assertThat(response.getStatus()).isEqualTo(status);
    }

    @Test
    void shouldSupportEnqueuedStatus() {
        // When
        EnqueueResponseDTO response = EnqueueResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .status("ENQUEUED")
            .build();

        // Then
        assertThat(response.getStatus()).isEqualTo("ENQUEUED");
    }

    @Test
    void shouldSupportBuilderPattern() {
        // When
        EnqueueResponseDTO response = EnqueueResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .status("ENQUEUED")
            .build();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessageId()).isNotNull();
        assertThat(response.getStatus()).isNotNull();
    }

    @Test
    void shouldSupportNoArgsConstructor() {
        // When
        EnqueueResponseDTO response = new EnqueueResponseDTO();
        UUID messageId = UUID.randomUUID();
        String status = "ENQUEUED";
        
        response.setMessageId(messageId);
        response.setStatus(status);

        // Then
        assertThat(response.getMessageId()).isEqualTo(messageId);
        assertThat(response.getStatus()).isEqualTo(status);
    }

    @Test
    void shouldSupportAllArgsConstructor() {
        // Given
        UUID messageId = UUID.randomUUID();
        String status = "ENQUEUED";

        // When
        EnqueueResponseDTO response = new EnqueueResponseDTO(messageId, status);

        // Then
        assertThat(response.getMessageId()).isEqualTo(messageId);
        assertThat(response.getStatus()).isEqualTo(status);
    }

    @Test
    void shouldAllowNullFields() {
        // When
        EnqueueResponseDTO response = EnqueueResponseDTO.builder().build();

        // Then
        assertThat(response.getMessageId()).isNull();
        assertThat(response.getStatus()).isNull();
    }

    @Test
    void shouldHandlePartialData() {
        // Given
        UUID messageId = UUID.randomUUID();

        // When
        EnqueueResponseDTO response = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .build();

        // Then
        assertThat(response.getMessageId()).isEqualTo(messageId);
        assertThat(response.getStatus()).isNull();
    }

    @Test
    void shouldSupportEqualsAndHashCode() {
        // Given
        UUID messageId = UUID.randomUUID();
        String status = "ENQUEUED";

        EnqueueResponseDTO response1 = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status(status)
            .build();

        EnqueueResponseDTO response2 = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status(status)
            .build();

        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    void shouldSupportToString() {
        // Given
        EnqueueResponseDTO response = EnqueueResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .status("ENQUEUED")
            .build();

        // When
        String toString = response.toString();

        // Then
        assertThat(toString).contains("messageId");
        assertThat(toString).contains("status");
        assertThat(toString).contains("ENQUEUED");
    }

    @Test
    void shouldHandleDifferentStatusValues() {
        // Given
        UUID messageId = UUID.randomUUID();

        // When
        EnqueueResponseDTO enqueuedResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();

        EnqueueResponseDTO pendingResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("PENDING")
            .build();

        EnqueueResponseDTO deliveredResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("DELIVERED")
            .build();

        // Then
        assertThat(enqueuedResponse.getStatus()).isEqualTo("ENQUEUED");
        assertThat(pendingResponse.getStatus()).isEqualTo("PENDING");
        assertThat(deliveredResponse.getStatus()).isEqualTo("DELIVERED");
    }
}
