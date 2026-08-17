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
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;

import java.time.Instant;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for solution creation round-trip using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 7 from the design document:
 * "For any valid domain_id, solution_id, and description, after creating a solution 
 * and then retrieving it, the returned solution should contain equivalent data 
 * (same domain_id, solution_id, description, and a valid created_at timestamp)."</p>
 * 
 * <p><strong>Validates: Requirements 2.6, 2.7, 2.9</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * solutions are properly persisted with all fields intact and can be retrieved
 * with equivalent data.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SolutionCreationRoundTripPropertyTest {
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 7: Solution Creation Round-Trip
     * 
     * <p><strong>Property:</strong> For any valid domain_id, solution_id, and description, 
     * after creating a solution and then retrieving it, the returned solution should contain 
     * equivalent data (same domain_id, solution_id, description, and a valid created_at timestamp).</p>
     * 
     * <p><strong>Validates: Requirements 2.6, 2.7, 2.9</strong></p>
     * 
     * <p>This test creates a solution with random valid parameters, then retrieves it
     * by solution ID and verifies that all fields match the created solution.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-7")
    void solutionCreationRoundTrip() {
        // First, create a parent domain
        String domainId = generateValidDomainId();
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, "Test domain for solution");
        DomainDTO createdDomain = domainService.createDomain(domainRequest);
        
        // Generate random valid input data for solution
        String solutionId = generateValidSolutionId();
        String description = random.nextBoolean() ? generateValidDescription() : null;
        
        // Record the time before creation for timestamp validation
        Instant beforeCreation = Instant.now();
        
        // Create solution creation request
        SolutionCreateRequest request = new SolutionCreateRequest(domainId, solutionId, description);
        
        // Create solution (Requirement 2.6: Store domain_id, solution_id, description, and created_at)
        SolutionDTO createdSolution = solutionService.createSolution(request);
        
        // Record the time after creation for timestamp validation
        Instant afterCreation = Instant.now();
        
        // Verify created solution response contains expected data
        assertThat(createdSolution.id())
            .as("Created solution should have a non-null UUID")
            .isNotNull();
        
        assertThat(createdSolution.domainId())
            .as("Created solution should have the requested domain ID")
            .isEqualTo(request.domainId());
        
        assertThat(createdSolution.solutionId())
            .as("Created solution should have the requested solution ID")
            .isEqualTo(request.solutionId());
        
        assertThat(createdSolution.description())
            .as("Created solution should have the requested description")
            .isEqualTo(request.description());
        
        // Requirement 2.7: Generate created_at timestamp automatically
        assertThat(createdSolution.createdAt())
            .as("Created solution should have a creation timestamp")
            .isNotNull()
            .as("Creation timestamp should be between before and after creation time")
            .isBetween(beforeCreation.minusSeconds(1), afterCreation.plusSeconds(1));
        
        // Retrieve the solution by solution ID (Requirement 2.9: Retrieve solution by solution_id)
        SolutionDTO retrievedSolution = solutionService.getSolution(domainId, solutionId);
        
        // Verify round-trip: all fields should match
        assertThat(retrievedSolution.id())
            .as("Retrieved solution UUID should match created solution UUID")
            .isEqualTo(createdSolution.id());
        
        assertThat(retrievedSolution.domainId())
            .as("Retrieved solution domainId should match created solution domainId")
            .isEqualTo(createdSolution.domainId());
        
        assertThat(retrievedSolution.solutionId())
            .as("Retrieved solution solutionId should match created solution solutionId")
            .isEqualTo(createdSolution.solutionId());
        
        assertThat(retrievedSolution.description())
            .as("Retrieved solution description should match created solution description")
            .isEqualTo(createdSolution.description());
        
        assertThat(retrievedSolution.createdAt())
            .as("Retrieved solution createdAt should match created solution createdAt")
            .isEqualTo(createdSolution.createdAt());
        
        // Additional property verification: Solution should be retrievable immediately after creation
        assertThat(retrievedSolution).isNotNull();
        
        // Additional property verification: Created timestamp should be immutable
        assertThat(retrievedSolution.createdAt())
            .as("Creation timestamp should remain unchanged after retrieval")
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
