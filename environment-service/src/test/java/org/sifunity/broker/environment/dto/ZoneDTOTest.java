// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ZoneDTO.
 * 
 * Tests the ZoneDTO record structure and behavior.
 */
@DisplayName("ZoneDTO Tests")
class ZoneDTOTest {

    @Test
    @DisplayName("Should create ZoneDTO with all fields")
    void shouldCreateZoneDTOWithAllFields() {
        // Given
        UUID id = UUID.randomUUID();
        String solutionId = "test-solution";
        String zoneId = "test-zone";
        String description = "Test zone description";
        Instant createdAt = Instant.now();
        
        // When
        ZoneDTO zoneDTO = new ZoneDTO(id, solutionId, zoneId, description, createdAt);
        
        // Then
        assertThat(zoneDTO.id()).isEqualTo(id);
        assertThat(zoneDTO.solutionId()).isEqualTo(solutionId);
        assertThat(zoneDTO.zoneId()).isEqualTo(zoneId);
        assertThat(zoneDTO.description()).isEqualTo(description);
        assertThat(zoneDTO.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("Should create ZoneDTO with null description")
    void shouldCreateZoneDTOWithNullDescription() {
        // Given
        UUID id = UUID.randomUUID();
        String solutionId = "test-solution";
        String zoneId = "test-zone";
        String description = null;
        Instant createdAt = Instant.now();
        
        // When
        ZoneDTO zoneDTO = new ZoneDTO(id, solutionId, zoneId, description, createdAt);
        
        // Then
        assertThat(zoneDTO.id()).isEqualTo(id);
        assertThat(zoneDTO.solutionId()).isEqualTo(solutionId);
        assertThat(zoneDTO.zoneId()).isEqualTo(zoneId);
        assertThat(zoneDTO.description()).isNull();
        assertThat(zoneDTO.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("Should support equality comparison")
    void shouldSupportEqualityComparison() {
        // Given
        UUID id = UUID.randomUUID();
        String solutionId = "test-solution";
        String zoneId = "test-zone";
        String description = "Test zone description";
        Instant createdAt = Instant.now();
        
        ZoneDTO zoneDTO1 = new ZoneDTO(id, solutionId, zoneId, description, createdAt);
        ZoneDTO zoneDTO2 = new ZoneDTO(id, solutionId, zoneId, description, createdAt);
        ZoneDTO zoneDTO3 = new ZoneDTO(UUID.randomUUID(), solutionId, zoneId, description, createdAt);
        
        // Then
        assertThat(zoneDTO1).isEqualTo(zoneDTO2);
        assertThat(zoneDTO1).isNotEqualTo(zoneDTO3);
        assertThat(zoneDTO1.hashCode()).isEqualTo(zoneDTO2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToStringRepresentation() {
        // Given
        UUID id = UUID.randomUUID();
        String solutionId = "test-solution";
        String zoneId = "test-zone";
        String description = "Test zone description";
        Instant createdAt = Instant.now();
        
        ZoneDTO zoneDTO = new ZoneDTO(id, solutionId, zoneId, description, createdAt);
        
        // When
        String toString = zoneDTO.toString();
        
        // Then
        assertThat(toString).contains("ZoneDTO");
        assertThat(toString).contains(id.toString());
        assertThat(toString).contains(solutionId);
        assertThat(toString).contains(zoneId);
        assertThat(toString).contains(description);
        assertThat(toString).contains(createdAt.toString());
    }
}
