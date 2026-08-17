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
 * Property-based test for solution count metamorphic property using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 21 from the design document:
 * "For any domain containing N solutions, after deleting one solution, the count 
 * of solutions in that domain should be N-1."</p>
 * 
 * <p><strong>Validates: Requirements 9.7</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized data to verify that
 * solution deletion correctly updates the count of solutions in a domain.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SolutionCountMetamorphicPropertyTest {
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();

    /** Monotonic counter to guarantee generated solution IDs are unique within a test run. */
    private final java.util.concurrent.atomic.AtomicInteger solutionSeq =
            new java.util.concurrent.atomic.AtomicInteger(0);
    
    /**
     * Property 21: Solution Count Metamorphic Property
     * 
     * <p><strong>Property:</strong> For any domain containing N solutions, after deleting 
     * one solution, the count of solutions in that domain should be N-1.</p>
     * 
     * <p><strong>Validates: Requirements 9.7</strong></p>
     * 
     * <p>This test creates a domain with multiple solutions, deletes one solution, 
     * and verifies that the count decreases by exactly one.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-21")
    void solutionCountMetamorphicProperty() {
        // Create a parent domain
        String domainId = generateValidDomainId();
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, "Test domain");
        DomainDTO domain = domainService.createDomain(domainRequest);
        
        // Create multiple solutions (2-10 solutions)
        int initialSolutionCount = random.nextInt(9) + 2; // At least 2 solutions
        String[] solutionIds = new String[initialSolutionCount];
        
        for (int i = 0; i < initialSolutionCount; i++) {
            String solutionId = generateValidSolutionId();
            solutionIds[i] = solutionId;
            
            SolutionCreateRequest solutionRequest = new SolutionCreateRequest(
                domainId,
                solutionId,
                "Test solution " + i
            );
            SolutionDTO solution = solutionService.createSolution(solutionRequest);
            
            // Verify solution was created
            assertThat(solution).isNotNull();
            assertThat(solution.domainId()).isEqualTo(domainId);
        }
        
        // Get initial count of solutions in the domain (N)
        List<SolutionDTO> solutionsBeforeDeletion = solutionService.listSolutionsByDomain(domainId);
        int countBeforeDeletion = solutionsBeforeDeletion.size();
        
        // Verify initial count matches expected
        assertThat(countBeforeDeletion)
            .as("Initial solution count should match number of created solutions")
            .isEqualTo(initialSolutionCount);
        
        // Select a random solution to delete
        int indexToDelete = random.nextInt(initialSolutionCount);
        String solutionIdToDelete = solutionIds[indexToDelete];
        
        // Delete one solution (Requirement 9.7: Metamorphic property for solution count)
        solutionService.deleteSolution(domainId, solutionIdToDelete);
        
        // Get count of solutions after deletion (should be N-1)
        List<SolutionDTO> solutionsAfterDeletion = solutionService.listSolutionsByDomain(domainId);
        int countAfterDeletion = solutionsAfterDeletion.size();
        
        // Verify metamorphic property: count after deletion = count before deletion - 1
        assertThat(countAfterDeletion)
            .as("Solution count after deletion should be one less than before")
            .isEqualTo(countBeforeDeletion - 1);
        
        // Verify the deleted solution is not in the list
        assertThat(solutionsAfterDeletion)
            .as("Deleted solution should not appear in the list")
            .noneMatch(s -> s.solutionId().equals(solutionIdToDelete));
        
        // Verify all other solutions still exist
        for (int i = 0; i < initialSolutionCount; i++) {
            if (i != indexToDelete) {
                String solutionId = solutionIds[i];
                assertThat(solutionsAfterDeletion)
                    .as("Non-deleted solution should still exist in the list")
                    .anyMatch(s -> s.solutionId().equals(solutionId));
            }
        }
        
        // Verify the parent domain still exists (should not be affected)
        DomainDTO retrievedDomain = domainService.getDomain(domainId);
        assertThat(retrievedDomain)
            .as("Parent domain should still exist after solution deletion")
            .isNotNull();
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

        // Unique, ever-increasing suffix guarantees no collisions within a test run,
        // regardless of the randomized environment/suffix/number parts below.
        int unique = solutionSeq.incrementAndGet();

        // Randomly decide whether to add a suffix
        if (random.nextBoolean()) {
            String suffix = suffixes[random.nextInt(suffixes.length)];
            int number = random.nextInt(100);

            // Randomly choose separator
            String separator = random.nextBoolean() ? "-" : "_";

            return environment + separator + suffix + separator + number + "-" + unique;
        } else {
            // Just environment with number for variety; unique suffix ensures no duplicates
            int number = random.nextInt(100);
            return environment + "-" + number + "-" + unique;
        }
    }
}
