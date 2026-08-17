// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.service.DomainService;

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for non-existent domain retrieval using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 4 from the design document:
 * "For any domain_id that does not exist in the system, attempting to retrieve 
 * that domain should return a not found error."</p>
 * 
 * <p><strong>Validates: Requirements 1.8</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized non-existent domain IDs to 
 * verify that the system properly returns not found errors for domains that 
 * don't exist.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NonExistentDomainRetrievalPropertyTest {
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 4: Non-Existent Domain Retrieval
     * 
     * <p><strong>Property:</strong> For any domain_id that does not exist in the system, 
     * attempting to retrieve that domain should return a not found error.</p>
     * 
     * <p><strong>Validates: Requirements 1.8</strong></p>
     * 
     * <p>This test generates random domain IDs that are guaranteed not to exist in the
     * system, then attempts to retrieve them and verifies that a DomainNotFoundException
     * is thrown.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-4")
    void nonExistentDomainRetrieval() {
        // Generate a domain ID that is guaranteed not to exist
        String nonExistentDomainId = generateNonExistentDomainId();
        
        // Attempt to retrieve the non-existent domain
        // Requirement 1.8: When a domain retrieval request specifies a domain_id that 
        // does not exist, THE Environment_Service SHALL return a not found error
        assertThatThrownBy(() -> domainService.getDomain(nonExistentDomainId))
            .as("Retrieving a non-existent domain should throw DomainNotFoundException")
            .isInstanceOf(DomainNotFoundException.class)
            .hasMessageContaining(nonExistentDomainId)
            .hasMessageContaining("not found");
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a domain ID that is guaranteed not to exist in the system.
     * Uses UUID-based generation to ensure uniqueness.
     */
    private String generateNonExistentDomainId() {
        // Use UUID to generate a unique identifier that won't exist
        // Prefix with "nonexistent-" to make it clear this is a test ID
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return "nonexistent-domain-" + uniqueId;
    }
}
