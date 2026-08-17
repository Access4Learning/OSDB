// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.sifunity.broker.environment.domain.Context;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ContextRepository.
 * 
 * <p>Tests the custom query methods defined in the repository interface.</p>
 * 
 * <p>Validates:</p>
 * <ul>
 *   <li>Repository can be injected and basic methods work</li>
 *   <li>findByZoneUuid method exists and returns empty list for non-existent zone UUIDs</li>
 *   <li>findByZoneUuidAndContextId method exists and returns empty for non-existent contexts</li>
 *   <li>existsByZoneUuidAndContextId method exists and returns false for non-existent contexts</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ContextRepositoryTest {
    
    @Autowired
    private ContextRepository contextRepository;
    
    @Test
    void repositoryCanBeInjected() {
        // Verify the repository can be injected and is not null
        assertThat(contextRepository).isNotNull();
    }
    
    @Test
    void findByZoneUuid_WhenZoneUuidDoesNotExist_ReturnsEmptyList() {
        // Given
        UUID nonExistentZoneUuid = UUID.randomUUID();
        
        // When
        List<Context> result = contextRepository.findByZoneUuid(nonExistentZoneUuid);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void findByZoneUuidAndContextId_WhenContextDoesNotExist_ReturnsEmpty() {
        // Given
        UUID nonExistentZoneUuid = UUID.randomUUID();
        String nonExistentContextId = "non-existent-context";
        
        // When
        Optional<Context> result = contextRepository.findByZoneUuidAndContextId(nonExistentZoneUuid, nonExistentContextId);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void existsByZoneUuidAndContextId_WhenContextDoesNotExist_ReturnsFalse() {
        // Given
        UUID nonExistentZoneUuid = UUID.randomUUID();
        String nonExistentContextId = "non-existent-context";
        
        // When
        boolean exists = contextRepository.existsByZoneUuidAndContextId(nonExistentZoneUuid, nonExistentContextId);
        
        // Then
        assertThat(exists).isFalse();
    }
}