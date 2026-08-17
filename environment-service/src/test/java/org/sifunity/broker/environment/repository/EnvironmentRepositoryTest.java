// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Environment;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EnvironmentRepository.
 * 
 * <p>Tests the custom query methods defined in the repository interface.</p>
 * 
 * <p>Validates:</p>
 * <ul>
 *   <li>Repository can be injected and basic methods work</li>
 *   <li>findBySessionToken method exists and returns empty for non-existent tokens</li>
 *   <li>existsByApplicationName method exists and returns false for non-existent names</li>
 *   <li>findByType method exists and can be called</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EnvironmentRepositoryTest {
    
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Test
    void repositoryCanBeInjected() {
        // Verify the repository can be injected and is not null
        assertThat(environmentRepository).isNotNull();
    }
    
    @Test
    void findBySessionToken_WhenTokenDoesNotExist_ReturnsEmpty() {
        // Given
        String nonExistentToken = UUID.randomUUID().toString();
        
        // When
        Optional<Environment> result = environmentRepository.findBySessionToken(nonExistentToken);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void existsByApplicationName_WhenNameDoesNotExist_ReturnsFalse() {
        // Given
        String nonExistentName = "NonExistentApp";
        
        // When
        boolean exists = environmentRepository.existsByApplicationName(nonExistentName);
        
        // Then
        assertThat(exists).isFalse();
    }
    
    @Test
    void findByType_CanBeCalled() {
        // Given
        EnvironmentType type = EnvironmentType.DIRECT;
        
        // When - Just verify the method can be called without errors
        var result = environmentRepository.findByType(type, org.springframework.data.domain.PageRequest.of(0, 10));
        
        // Then - No DIRECT environments exist (bootstrap creates BROKERED admin only)
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }
}