// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.dto.SolutionUpdateRequest;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for solution update identity preservation using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 23 from the design document:
 * "For any solution and any new description, after updating the solution's description, 
 * retrieving the solution should return the updated description while preserving the 
 * domain_id, solution_id, and created_at timestamp."</p>
 * 
 * <p><strong>Validates: Requirements 2.13</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized data to verify that
 * solution updates preserve identity fields while updating the description.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SolutionUpdatePreservesIdentityPropertyTest {
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 23: Solution Update Preserves Identity
     * 
     * <p><strong>Property:</strong> For any solution and any new description, after updating 
     * the solution's description, retrieving the solution should return the updated description 
     * while preserving the domain_id, solution_id, and created_at timestamp.</p>
     * 
     * <p><strong>Validates: Requirements 2.13</strong></p>
     * 
     * <p>This test creates a solution, updates its description, and verifies that the 
     * identity fields remain unchanged while the description is updated.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-23")
    void solutionUpdatePreservesIdentity() {
        // First, create a parent domain
        String domainId = generateValidDomainId();
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, "Test domain for solution");
        DomainDTO createdDomain = domainService.createDomain(domainRequest);
        
        // Create a solution with initial description
        String solutionId = generateValidSolutionId();
        String initialDescription = "Initial description for solution";
        SolutionCreateRequest createRequest = new SolutionCreateRequest(
            domainId,
            solutionId,
            initialDescription
        );
        SolutionDTO createdSolution = solutionService.createSolution(createRequest);
        
        // Store original identity fields
        var originalId = createdSolution.id();
        var originalDomainId = createdSolution.domainId();
        var originalSolutionId = createdSolution.solutionId();
        var originalCreatedAt = createdSolution.createdAt();
        
        // Generate a new description
        String newDescription = random.nextBoolean() ? generateValidDescription() : null;
        
        // Update the solution's description (Requirement 2.13: Update solution's description)
        SolutionUpdateRequest updateRequest = new SolutionUpdateRequest(newDescription);
        SolutionDTO updatedSolution = solutionService.updateSolution(domainId, solutionId, updateRequest);
        
        // Verify the description was updated
        assertThat(updatedSolution.description())
            .as("Updated solution should have the new description")
            .isEqualTo(newDescription);
        
        // Verify identity fields are preserved
        assertThat(updatedSolution.id())
            .as("Solution UUID should remain unchanged after update")
            .isEqualTo(originalId);
        
        assertThat(updatedSolution.domainId())
            .as("Domain ID should remain unchanged after update")
            .isEqualTo(originalDomainId);
        
        assertThat(updatedSolution.solutionId())
            .as("Solution ID should remain unchanged after update")
            .isEqualTo(originalSolutionId);
        
        assertThat(updatedSolution.createdAt())
            .as("Creation timestamp should remain unchanged after update")
            .isEqualTo(originalCreatedAt);
        
        // Retrieve the solution again to verify persistence
        SolutionDTO retrievedSolution = solutionService.getSolution(domainId, solutionId);
        
        // Verify retrieved solution has updated description
        assertThat(retrievedSolution.description())
            .as("Retrieved solution should have the updated description")
            .isEqualTo(newDescription);
        
        // Verify retrieved solution preserves identity fields
        assertThat(retrievedSolution.id())
            .as("Retrieved solution UUID should match original")
            .isEqualTo(originalId);
        
        assertThat(retrievedSolution.domainId())
            .as("Retrieved solution domainId should match original")
            .isEqualTo(originalDomainId);
        
        assertThat(retrievedSolution.solutionId())
            .as("Retrieved solution solutionId should match original")
            .isEqualTo(originalSolutionId);
        
        assertThat(retrievedSolution.createdAt())
            .as("Retrieved solution createdAt should match original")
            .isEqualTo(originalCreatedAt);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a valid domain ID following the naming pattern.
     * Pattern: alphanumeric, underscore, hyphen (must start with alphanumeric)
     */
    private String generateValidDomainId() {
        String[] prefixes = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        
        return prefix + "-" + number + "-" + timestamp;
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
    
    /**
     * Generates a valid description for a solution.
     */
    private String generateValidDescription() {
        String[] templates = {
            "Updated %s environment for %s",
            "Modified %s deployment instance",
            "Revised %s solution for testing",
            "Changed %s operational environment",
            "Updated %s SIF environment",
            "Modified %s integration environment"
        };
        
        String[] environments = {
            "Production",
            "Staging",
            "Testing",
            "Development",
            "QA",
            "UAT",
            "Demo",
            "Sandbox"
        };
        
        String[] purposes = {
            "student data",
            "assessment data",
            "attendance tracking",
            "grade reporting",
            "enrollment management",
            "staff directory"
        };
        
        String template = templates[random.nextInt(templates.length)];
        String environment = environments[random.nextInt(environments.length)];
        
        // Some templates need two parameters
        if (template.contains("%s") && template.indexOf("%s") != template.lastIndexOf("%s")) {
            String purpose = purposes[random.nextInt(purposes.length)];
            return String.format(template, environment, purpose);
        } else {
            return String.format(template, environment);
        }
    }
}
