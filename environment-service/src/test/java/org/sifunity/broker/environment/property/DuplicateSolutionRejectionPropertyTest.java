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
import org.sifunity.broker.environment.exception.DuplicateSolutionException;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for duplicate solution rejection within a domain using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 9 from the design document:
 * "For any domain and solution_id, after successfully creating a solution with that 
 * solution_id in that domain, attempting to create another solution with the same 
 * solution_id in the same domain should fail with a conflict error."</p>
 * 
 * <p><strong>Validates: Requirements 2.3</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * the system properly rejects duplicate solution creation attempts within the same
 * domain and maintains solution_id uniqueness within each domain.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DuplicateSolutionRejectionPropertyTest {
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 9: Duplicate Solution Rejection Within Domain
     * 
     * <p><strong>Property:</strong> For any domain and solution_id, after successfully 
     * creating a solution with that solution_id in that domain, attempting to create 
     * another solution with the same solution_id in the same domain should fail with 
     * a conflict error.</p>
     * 
     * <p><strong>Validates: Requirements 2.3</strong></p>
     * 
     * <p>This test creates a domain, then creates a solution within that domain with a 
     * random valid solution ID, then attempts to create another solution with the same 
     * solution ID in the same domain and verifies that a DuplicateSolutionException is thrown.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-9")
    void duplicateSolutionRejectionWithinDomain() {
        // First, create a parent domain
        String domainId = generateValidDomainId();
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, "Test domain for duplicate solution test");
        DomainDTO createdDomain = domainService.createDomain(domainRequest);
        
        // Generate random valid input data for solution
        String solutionId = generateValidSolutionId();
        String firstDescription = random.nextBoolean() ? generateValidDescription() : null;
        String secondDescription = random.nextBoolean() ? generateValidDescription() : null;
        
        // Create first solution creation request
        SolutionCreateRequest firstRequest = new SolutionCreateRequest(domainId, solutionId, firstDescription);
        
        // Create the first solution (should succeed)
        SolutionDTO createdSolution = solutionService.createSolution(firstRequest);
        
        // Verify the first solution was created successfully
        assertThat(createdSolution)
            .as("First solution should be created successfully")
            .isNotNull();
        
        assertThat(createdSolution.domainId())
            .as("Created solution should have the requested domain ID")
            .isEqualTo(domainId);
        
        assertThat(createdSolution.solutionId())
            .as("Created solution should have the requested solution ID")
            .isEqualTo(solutionId);
        
        // Create second solution creation request with the SAME solution_id in the SAME domain
        SolutionCreateRequest secondRequest = new SolutionCreateRequest(domainId, solutionId, secondDescription);
        
        // Attempt to create a second solution with the same solution_id in the same domain (should fail)
        // Requirement 2.3: When a solution creation request is received with a solution_id 
        // that already exists within the specified domain, THE Environment_Service SHALL 
        // return an error indicating duplicate solution
        assertThatThrownBy(() -> solutionService.createSolution(secondRequest))
            .as("Creating a solution with duplicate solution_id in the same domain should throw DuplicateSolutionException")
            .isInstanceOf(DuplicateSolutionException.class)
            .hasMessageContaining(solutionId)
            .hasMessageContaining(domainId)
            .hasMessageContaining("already exists");
        
        // Additional property verification: The original solution should still exist and be unchanged
        SolutionDTO retrievedSolution = solutionService.getSolution(domainId, solutionId);
        
        assertThat(retrievedSolution.id())
            .as("Original solution UUID should remain unchanged after duplicate attempt")
            .isEqualTo(createdSolution.id());
        
        assertThat(retrievedSolution.domainId())
            .as("Original solution domainId should remain unchanged after duplicate attempt")
            .isEqualTo(createdSolution.domainId());
        
        assertThat(retrievedSolution.solutionId())
            .as("Original solution solutionId should remain unchanged after duplicate attempt")
            .isEqualTo(createdSolution.solutionId());
        
        assertThat(retrievedSolution.description())
            .as("Original solution description should remain unchanged after duplicate attempt")
            .isEqualTo(createdSolution.description());
        
        assertThat(retrievedSolution.createdAt())
            .as("Original solution createdAt should remain unchanged after duplicate attempt")
            .isEqualTo(createdSolution.createdAt());
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
            "%s environment for %s",
            "%s deployment instance",
            "%s solution for testing",
            "%s operational environment",
            "%s SIF environment",
            "%s integration environment"
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
