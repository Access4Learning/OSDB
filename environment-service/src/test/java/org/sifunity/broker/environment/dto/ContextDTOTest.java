// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ContextDTO.
 * 
 * Tests the ContextDTO record structure and behavior.
 */
@DisplayName("ContextDTO Tests")
class ContextDTOTest {

    @Test
    @DisplayName("Should create ContextDTO with all fields")
    void shouldCreateContextDTOWithAllFields() {
        // Given
        UUID id = UUID.randomUUID();
        String zoneId = "test-zone";
        String contextId = "test-context";
        String description = "Test context description";
        Instant createdAt = Instant.now();
        
        // When
        ContextDTO contextDTO = new ContextDTO(id, zoneId, contextId, description, createdAt);
        
        // Then
        assertThat(contextDTO.id()).isEqualTo(id);
        assertThat(contextDTO.zoneId()).isEqualTo(zoneId);
        assertThat(contextDTO.contextId()).isEqualTo(contextId);
        assertThat(contextDTO.description()).isEqualTo(description);
        assertThat(contextDTO.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("Should create ContextDTO with null description")
    void shouldCreateContextDTOWithNullDescription() {
        // Given
        UUID id = UUID.randomUUID();
        String zoneId = "test-zone";
        String contextId = "test-context";
        String description = null;
        Instant createdAt = Instant.now();
        
        // When
        ContextDTO contextDTO = new ContextDTO(id, zoneId, contextId, description, createdAt);
        
        // Then
        assertThat(contextDTO.id()).isEqualTo(id);
        assertThat(contextDTO.zoneId()).isEqualTo(zoneId);
        assertThat(contextDTO.contextId()).isEqualTo(contextId);
        assertThat(contextDTO.description()).isNull();
        assertThat(contextDTO.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("Should support equality comparison")
    void shouldSupportEqualityComparison() {
        // Given
        UUID id = UUID.randomUUID();
        String zoneId = "test-zone";
        String contextId = "test-context";
        String description = "Test context description";
        Instant createdAt = Instant.now();
        
        ContextDTO contextDTO1 = new ContextDTO(id, zoneId, contextId, description, createdAt);
        ContextDTO contextDTO2 = new ContextDTO(id, zoneId, contextId, description, createdAt);
        ContextDTO contextDTO3 = new ContextDTO(UUID.randomUUID(), zoneId, contextId, description, createdAt);
        
        // Then
        assertThat(contextDTO1).isEqualTo(contextDTO2);
        assertThat(contextDTO1).isNotEqualTo(contextDTO3);
        assertThat(contextDTO1.hashCode()).isEqualTo(contextDTO2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToStringRepresentation() {
        // Given
        UUID id = UUID.randomUUID();
        String zoneId = "test-zone";
        String contextId = "test-context";
        String description = "Test context description";
        Instant createdAt = Instant.now();
        
        ContextDTO contextDTO = new ContextDTO(id, zoneId, contextId, description, createdAt);
        
        // When
        String toString = contextDTO.toString();
        
        // Then
        assertThat(toString).contains("ContextDTO");
        assertThat(toString).contains(id.toString());
        assertThat(toString).contains(zoneId);
        assertThat(toString).contains(contextId);
        assertThat(toString).contains(description);
        assertThat(toString).contains(createdAt.toString());
    }
}
