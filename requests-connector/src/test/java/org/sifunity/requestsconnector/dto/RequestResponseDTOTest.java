// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.dto;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RequestResponseDTO.
 * 
 * Tests the builder pattern and field assignments for both IMMEDIATE and DELAYED response scenarios.
 * 
 * Validates Requirements 1.3, 4.5, 5.3
 */
class RequestResponseDTOTest {
    
    @Test
    void testBuilderWithAllFields() {
        // Given
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String status = "ACCEPTED";
        Instant timestamp = Instant.now();
        Object payload = "test payload";
        
        // When
        RequestResponseDTO dto = RequestResponseDTO.builder()
            .messageId(messageId)
            .queueId(queueId)
            .status(status)
            .timestamp(timestamp)
            .payload(payload)
            .build();
        
        // Then
        assertThat(dto.getMessageId()).isEqualTo(messageId);
        assertThat(dto.getQueueId()).isEqualTo(queueId);
        assertThat(dto.getStatus()).isEqualTo(status);
        assertThat(dto.getTimestamp()).isEqualTo(timestamp);
        assertThat(dto.getPayload()).isEqualTo(payload);
    }
    
    @Test
    void testBuilderForImmediateResponse() {
        // Given - IMMEDIATE response has messageId, timestamp, and payload
        UUID messageId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        Object payload = "{\"studentId\": \"12345\"}";
        
        // When
        RequestResponseDTO dto = RequestResponseDTO.builder()
            .messageId(messageId)
            .timestamp(timestamp)
            .payload(payload)
            .build();
        
        // Then
        assertThat(dto.getMessageId()).isEqualTo(messageId);
        assertThat(dto.getTimestamp()).isEqualTo(timestamp);
        assertThat(dto.getPayload()).isEqualTo(payload);
        assertThat(dto.getQueueId()).isNull();
        assertThat(dto.getStatus()).isNull();
    }
    
    @Test
    void testBuilderForDelayedResponse() {
        // Given - DELAYED response has messageId, queueId, status, and timestamp
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String status = "ACCEPTED";
        Instant timestamp = Instant.now();
        
        // When
        RequestResponseDTO dto = RequestResponseDTO.builder()
            .messageId(messageId)
            .queueId(queueId)
            .status(status)
            .timestamp(timestamp)
            .build();
        
        // Then
        assertThat(dto.getMessageId()).isEqualTo(messageId);
        assertThat(dto.getQueueId()).isEqualTo(queueId);
        assertThat(dto.getStatus()).isEqualTo(status);
        assertThat(dto.getTimestamp()).isEqualTo(timestamp);
        assertThat(dto.getPayload()).isNull();
    }
    
    @Test
    void testBuilderWithMinimalFields() {
        // Given - Only messageId
        UUID messageId = UUID.randomUUID();
        
        // When
        RequestResponseDTO dto = RequestResponseDTO.builder()
            .messageId(messageId)
            .build();
        
        // Then
        assertThat(dto.getMessageId()).isEqualTo(messageId);
        assertThat(dto.getQueueId()).isNull();
        assertThat(dto.getStatus()).isNull();
        assertThat(dto.getTimestamp()).isNull();
        assertThat(dto.getPayload()).isNull();
    }
    
    @Test
    void testSettersAndGetters() {
        // Given
        RequestResponseDTO dto = new RequestResponseDTO();
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String status = "ACCEPTED";
        Instant timestamp = Instant.now();
        Object payload = "test";
        
        // When
        dto.setMessageId(messageId);
        dto.setQueueId(queueId);
        dto.setStatus(status);
        dto.setTimestamp(timestamp);
        dto.setPayload(payload);
        
        // Then
        assertThat(dto.getMessageId()).isEqualTo(messageId);
        assertThat(dto.getQueueId()).isEqualTo(queueId);
        assertThat(dto.getStatus()).isEqualTo(status);
        assertThat(dto.getTimestamp()).isEqualTo(timestamp);
        assertThat(dto.getPayload()).isEqualTo(payload);
    }
    
    @Test
    void testEqualsAndHashCode() {
        // Given
        UUID messageId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        
        RequestResponseDTO dto1 = RequestResponseDTO.builder()
            .messageId(messageId)
            .queueId(queueId)
            .status("ACCEPTED")
            .timestamp(timestamp)
            .build();
        
        RequestResponseDTO dto2 = RequestResponseDTO.builder()
            .messageId(messageId)
            .queueId(queueId)
            .status("ACCEPTED")
            .timestamp(timestamp)
            .build();
        
        // Then
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }
    
    @Test
    void testToString() {
        // Given
        UUID messageId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        
        RequestResponseDTO dto = RequestResponseDTO.builder()
            .messageId(messageId)
            .status("ACCEPTED")
            .timestamp(timestamp)
            .build();
        
        // When
        String toString = dto.toString();
        
        // Then
        assertThat(toString).contains("RequestResponseDTO");
        assertThat(toString).contains(messageId.toString());
        assertThat(toString).contains("ACCEPTED");
    }
}
