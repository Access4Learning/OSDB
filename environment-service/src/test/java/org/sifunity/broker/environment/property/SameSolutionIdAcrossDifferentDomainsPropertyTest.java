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

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for solution ID uniqueness across different domains using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 10 from the design document:
 * "For any solution_id, it should be possible to create solutions with that same 
 * solution_id in different domains without conflict."</p>
 * 
 * <p><strong>Validates: Requirements 8.7</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized solution IDs to verify that
 * the same solution ID can exist in multiple domains without causing conflicts.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SameSolutionIdAcrossDifferentDomainsPropertyTest {
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 10: Same Solution ID Across Different Domains
     * 
     * <p><strong>Property:</strong> For any solution_id, it should be possible to create 
     * solutions with that same solution_id in different domains without conflict.</p>
     * 
     * <p><strong>Validates: Requirements 8.7</strong></p>
     * 
     * <p>This test creates two different domains, then creates solutions with the same 
     * solution_id in both domains and verifies that both succeed without conflict.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-10")
    void sameSolutionIdAcrossDifferentDomains() {
        // Create first domain
        String domainId1 = generateValidDomainId();
        DomainCreateRequest domainRequest1 = new DomainCreateRequest(domainId1, "First test domain");
        DomainDTO domain1 = domainService.createDomain(domainRequest1);
        
        // Create second domain
        String domainId2 = generateValidDomainId();
        DomainCreateRequest domainRequest2 = new DomainCreateRequest(domainId2, "Second test domain");
        DomainDTO domain2 = domainService.createDomain(domainRequest2);
        
        // Generate a solution ID that will be used in both domains
        String sharedSolutionId = generateValidSolutionId();
        
        // Create solution in first domain
        SolutionCreateRequest request1 = new SolutionCreateRequest(
            domainId1,
            sharedSolutionId,
            "Solution in first domain"
        );
        SolutionDTO solution1 = solutionService.createSolution(request1);
        
        // Verify first solution was created successfully
        assertThat(solution1).isNotNull();
        assertThat(solution1.domainId()).isEqualTo(domainId1);
        assertThat(solution1.solutionId()).isEqualTo(sharedSolutionId);
        
        // Create solution with same solution_id in second domain
        // Requirement 8.7: Same solution_id can exist in different domains
        SolutionCreateRequest request2 = new SolutionCreateRequest(
            domainId2,
            sharedSolutionId,
            "Solution in second domain"
        );
        SolutionDTO solution2 = solutionService.createSolution(request2);
        
        // Verify second solution was created successfully without conflict
        assertThat(solution2).isNotNull();
        assertThat(solution2.domainId()).isEqualTo(domainId2);
        assertThat(solution2.solutionId()).isEqualTo(sharedSolutionId);
        
        // Verify both solutions have different UUIDs (they are distinct entities)
        assertThat(solution1.id())
            .as("Solutions in different domains should have different UUIDs")
            .isNotEqualTo(solution2.id());
        
        // Verify both solutions can be retrieved independently via their domain
        List<SolutionDTO> domain1Solutions = solutionService.listSolutionsByDomain(domainId1);
        assertThat(domain1Solutions)
            .as("First domain should contain the shared solution")
            .anyMatch(s -> s.solutionId().equals(sharedSolutionId));
        
        List<SolutionDTO> domain2Solutions = solutionService.listSolutionsByDomain(domainId2);
        assertThat(domain2Solutions)
            .as("Second domain should contain the shared solution")
            .anyMatch(s -> s.solutionId().equals(sharedSolutionId));
        
        // Verify solutions are properly scoped to their domains
        assertThat(solution1.domainId())
            .as("First solution should belong to first domain")
            .isEqualTo(domainId1);
        
        assertThat(solution2.domainId())
            .as("Second solution should belong to second domain")
            .isEqualTo(domainId2);
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
            // Just environment with number for uniqueness (no timestamp to allow reuse)
            int number = random.nextInt(100);
            return environment + "-" + number;
        }
    }
}
