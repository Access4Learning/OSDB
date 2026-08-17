// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ResponseResultDTO.
 * Tests field handling and builder pattern.
 */
class ResponseResultDTOTest {

    @Test
    void shouldCreateResultWithAllFields() {
        // Given
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String status = "DELIVERED";
        Instant timestamp = Instant.now();

        // When
        ResponseResultDTO result = ResponseResultDTO.builder()
            .messageId(messageId)
            .queueId(queueId)
            .status(status)
            .timestamp(timestamp)
            .build();

        // Then
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.getQueueId()).isEqualTo(queueId);
        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(result.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void shouldSupportDeliveredStatus() {
        // When
        ResponseResultDTO result = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(UUID.randomUUID())
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();

        // Then
        assertThat(result.getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void shouldSupportErrorDeliveredStatus() {
        // When
        ResponseResultDTO result = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(UUID.randomUUID())
            .status("ERROR_DELIVERED")
            .timestamp(Instant.now())
            .build();

        // Then
        assertThat(result.getStatus()).isEqualTo("ERROR_DELIVERED");
    }

    @Test
    void shouldSupportBuilderPattern() {
        // When
        ResponseResultDTO result = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(UUID.randomUUID())
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isNotNull();
        assertThat(result.getQueueId()).isNotNull();
        assertThat(result.getStatus()).isNotNull();
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    void shouldSupportNoArgsConstructor() {
        // When
        ResponseResultDTO result = new ResponseResultDTO();
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String status = "DELIVERED";
        Instant timestamp = Instant.now();
        
        result.setMessageId(messageId);
        result.setQueueId(queueId);
        result.setStatus(status);
        result.setTimestamp(timestamp);

        // Then
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.getQueueId()).isEqualTo(queueId);
        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(result.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void shouldSupportAllArgsConstructor() {
        // Given
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String status = "DELIVERED";
        Instant timestamp = Instant.now();

        // When
        ResponseResultDTO result = new ResponseResultDTO(messageId, queueId, status, timestamp);

        // Then
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.getQueueId()).isEqualTo(queueId);
        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(result.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void shouldAllowNullFields() {
        // When
        ResponseResultDTO result = ResponseResultDTO.builder().build();

        // Then
        assertThat(result.getMessageId()).isNull();
        assertThat(result.getQueueId()).isNull();
        assertThat(result.getStatus()).isNull();
        assertThat(result.getTimestamp()).isNull();
    }

    @Test
    void shouldHandlePartialData() {
        // Given
        UUID messageId = UUID.randomUUID();
        String status = "DELIVERED";

        // When
        ResponseResultDTO result = ResponseResultDTO.builder()
            .messageId(messageId)
            .status(status)
            .build();

        // Then
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(result.getQueueId()).isNull();
        assertThat(result.getTimestamp()).isNull();
    }

    @Test
    void shouldSupportEqualsAndHashCode() {
        // Given
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String status = "DELIVERED";
        Instant timestamp = Instant.now();

        ResponseResultDTO result1 = ResponseResultDTO.builder()
            .messageId(messageId)
            .queueId(queueId)
            .status(status)
            .timestamp(timestamp)
            .build();

        ResponseResultDTO result2 = ResponseResultDTO.builder()
            .messageId(messageId)
            .queueId(queueId)
            .status(status)
            .timestamp(timestamp)
            .build();

        // Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    void shouldSupportToString() {
        // Given
        ResponseResultDTO result = ResponseResultDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(UUID.randomUUID())
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();

        // When
        String toString = result.toString();

        // Then
        assertThat(toString).contains("messageId");
        assertThat(toString).contains("queueId");
        assertThat(toString).contains("status");
        assertThat(toString).contains("timestamp");
        assertThat(toString).contains("DELIVERED");
    }
}
