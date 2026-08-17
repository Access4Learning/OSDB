// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.sifunity.broker.environment.domain.Zone;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ZoneRepository.
 * 
 * <p>Tests the custom query methods defined in the repository interface.</p>
 * 
 * <p>Validates:</p>
 * <ul>
 *   <li>Repository can be injected and basic methods work</li>
 *   <li>findBySolutionUuidAndZoneId returns empty for non-existent zones</li>
 *   <li>existsBySolutionUuidAndZoneId returns false for non-existent zones</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ZoneRepositoryTest {
    
    @Autowired
    private ZoneRepository zoneRepository;
    
    @Test
    void repositoryCanBeInjected() {
        // Verify the repository can be injected and is not null
        assertThat(zoneRepository).isNotNull();
    }
    
    @Test
    void findBySolutionUuidAndZoneId_WhenZoneDoesNotExist_ReturnsEmpty() {
        // Given
        UUID solutionUuid = UUID.randomUUID();
        String nonExistentZoneId = "non-existent-zone";
        
        // When
        Optional<Zone> result = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, nonExistentZoneId);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void existsBySolutionUuidAndZoneId_WhenZoneDoesNotExist_ReturnsFalse() {
        // Given
        UUID solutionUuid = UUID.randomUUID();
        String nonExistentZoneId = "non-existent-zone";
        
        // When
        boolean exists = zoneRepository.existsBySolutionUuidAndZoneId(solutionUuid, nonExistentZoneId);
        
        // Then
        assertThat(exists).isFalse();
    }
}
