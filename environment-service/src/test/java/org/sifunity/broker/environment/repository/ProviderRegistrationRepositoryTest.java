// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.sifunity.broker.environment.domain.ProviderRegistration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProviderRegistrationRepository.
 * 
 * <p>Tests the custom query methods defined in the repository interface.</p>
 * 
 * <p>Validates:</p>
 * <ul>
 *   <li>Repository can be injected and basic methods work</li>
 *   <li>findByContextServiceUuid method returns empty for non-existent registrations</li>
 *   <li>findByEnvironmentId method returns empty list for non-existent environment</li>
 *   <li>existsByEnvironmentIdAndContextServiceUuid method returns false for non-existent registrations</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProviderRegistrationRepositoryTest {
    
    @Autowired
    private ProviderRegistrationRepository providerRegistrationRepository;
    
    @Test
    void repositoryCanBeInjected() {
        // Verify the repository can be injected and is not null
        assertThat(providerRegistrationRepository).isNotNull();
    }
    
    @Test
    void findByContextServiceUuid_WhenRegistrationDoesNotExist_ReturnsEmpty() {
        // Given
        UUID nonExistentContextServiceUuid = UUID.randomUUID();
        
        // When
        Optional<ProviderRegistration> result = providerRegistrationRepository
            .findByContextServiceUuid(nonExistentContextServiceUuid);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void findByEnvironmentId_WhenEnvironmentDoesNotExist_ReturnsEmptyList() {
        // Given
        UUID nonExistentEnvironmentId = UUID.randomUUID();
        
        // When
        List<ProviderRegistration> result = providerRegistrationRepository
            .findByEnvironmentId(nonExistentEnvironmentId);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void existsByEnvironmentIdAndContextServiceUuid_WhenRegistrationDoesNotExist_ReturnsFalse() {
        // Given
        UUID nonExistentEnvironmentId = UUID.randomUUID();
        UUID nonExistentContextServiceUuid = UUID.randomUUID();
        
        // When
        boolean exists = providerRegistrationRepository
            .existsByEnvironmentIdAndContextServiceUuid(nonExistentEnvironmentId, nonExistentContextServiceUuid);
        
        // Then
        assertThat(exists).isFalse();
    }
}
