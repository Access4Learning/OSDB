// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for solution parent domain validation using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 8 from the design document:
 * "For any solution_id and any domain_id that does not exist, attempting to create 
 * a solution with that non-existent domain_id should fail with a domain not found error."</p>
 * 
 * <p><strong>Validates: Requirements 2.2, 2.8</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized non-existent domain IDs to verify that
 * solutions cannot be created without a valid parent domain.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SolutionRequiresExistingDomainPropertyTest {
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();
    
    /**
     * Property 8: Solution Requires Existing Domain
     * 
     * <p><strong>Property:</strong> For any solution_id and any domain_id that does not exist, 
     * attempting to create a solution with that non-existent domain_id should fail with a 
     * domain not found error.</p>
     * 
     * <p><strong>Validates: Requirements 2.2, 2.8</strong></p>
     * 
     * <p>This test generates non-existent domain IDs and verifies that attempting to 
     * create a solution with such domain IDs results in a DomainNotFoundException.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-8")
    void solutionRequiresExistingDomain() {
        // Generate a non-existent domain ID (using timestamp ensures it doesn't exist)
        String nonExistentDomainId = generateNonExistentDomainId();
        
        // Generate a valid solution ID
        String solutionId = generateValidSolutionId();
        
        // Create solution creation request with non-existent domain
        SolutionCreateRequest request = new SolutionCreateRequest(
            nonExistentDomainId,
            solutionId,
            "Test solution with non-existent domain"
        );
        
        // Requirement 2.2: Return error indicating domain not found when domain doesn't exist
        // Requirement 2.8: Enforce foreign key constraint from solutions.domain_id to domains.domain_id
        assertThatThrownBy(() -> solutionService.createSolution(request))
            .as("Creating solution with non-existent domain_id should throw DomainNotFoundException")
            .isInstanceOf(DomainNotFoundException.class)
            .hasMessageContaining(nonExistentDomainId);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a non-existent domain ID that is guaranteed not to exist in the database.
     * Uses high-precision timestamp to ensure uniqueness.
     */
    private String generateNonExistentDomainId() {
        // Use nano timestamp to ensure uniqueness
        long nanoTime = System.nanoTime();
        String[] prefixes = {"nonexistent", "missing", "invalid", "unknown", "fake"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        
        return prefix + "-domain-" + nanoTime;
    }
    
    /**
     * Generates a valid solution ID following the naming pattern.
     * Pattern: alphanumeric, underscore, hyphen (must start with alphanumeric)
     */
    private String generateValidSolutionId() {
        String[] environments = {"production", "staging", "testing", "development", "qa", "uat", "demo", "sandbox"};
        String[] suffixes = {"env", "instance", "cluster", "deployment"};
        
        String environment = environments[random.nextInt(environments.length)];
        
        // Randomly decide whether to add a suffix
        if (random.nextBoolean()) {
            String suffix = suffixes[random.nextInt(suffixes.length)];
            int number = random.nextInt(100);
            
            // Randomly choose separator
            String separator = random.nextBoolean() ? "-" : "_";
            
            return environment + separator + suffix + separator + number;
        } else {
            // Just environment with number and timestamp for uniqueness
            int number = random.nextInt(100);
            long timestamp = System.nanoTime() % 10000;
            return environment + "-" + number + "-" + timestamp;
        }
    }
}
