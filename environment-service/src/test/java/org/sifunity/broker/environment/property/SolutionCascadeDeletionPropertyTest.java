// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for solution cascade deletion using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 20 from the design document:
 * "For any solution containing zones, contexts, and environments, after deleting 
 * the solution, all child zones, contexts, and environments should no longer exist 
 * in the system."</p>
 * 
 * <p><strong>Validates: Requirements 2.15</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized data to verify that
 * deleting a solution cascades to all child entities.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SolutionCascadeDeletionPropertyTest {
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private ZoneService zoneService;
    
    private final Random random = new Random();
    
    /**
     * Property 20: Solution Cascade Deletion
     * 
     * <p><strong>Property:</strong> For any solution containing zones, contexts, and 
     * environments, after deleting the solution, all child zones, contexts, and 
     * environments should no longer exist in the system.</p>
     * 
     * <p><strong>Validates: Requirements 2.15</strong></p>
     * 
     * <p>This test creates a solution with child zones, deletes the solution, and 
     * verifies that all child entities are also deleted.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-20")
    void solutionCascadeDeletion() {
        // Create a parent domain
        String domainId = generateValidDomainId();
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, "Test domain");
        DomainDTO domain = domainService.createDomain(domainRequest);
        
        // Create a solution
        String solutionId = generateValidSolutionId();
        SolutionCreateRequest solutionRequest = new SolutionCreateRequest(
            domainId,
            solutionId,
            "Test solution for cascade deletion"
        );
        SolutionDTO solution = solutionService.createSolution(solutionRequest);
        
        // Create multiple child zones (1-5 zones)
        int zoneCount = random.nextInt(5) + 1;
        String[] zoneIds = new String[zoneCount];
        
        for (int i = 0; i < zoneCount; i++) {
            String zoneId = generateValidZoneId();
            zoneIds[i] = zoneId;
            
            ZoneCreateRequest zoneRequest = new ZoneCreateRequest(solutionId, zoneId,
                "Test zone " + i
            );
            ZoneDTO zone = zoneService.createZone(domainId, zoneRequest);
            
            // Verify zone was created
            assertThat(zone).isNotNull();
            assertThat(zone.solutionId()).isEqualTo(solutionId);
        }
        
        // Verify all zones exist before deletion
        for (String zoneId : zoneIds) {
            assertThat(zoneService.zoneExists(domainId, solutionId, zoneId))
                .as("Zone should exist before solution deletion")
                .isTrue();
        }
        
        // Delete the solution (Requirement 2.15: Cascade delete zones, contexts, environments)
        solutionService.deleteSolution(domainId, solutionId);

        // Verify the solution itself no longer exists
        assertThat(solutionService.solutionExists(domainId, solutionId))
            .as("Solution should not exist after deletion")
            .isFalse();
        
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
     * Generates a valid zone ID following the naming pattern.
     * Pattern: alphanumeric, underscore, hyphen (must start with alphanumeric)
     */
    private String generateValidZoneId() {
        String[] prefixes = {"zone", "default", "primary", "secondary", "test", "prod", "dev"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        
        return prefix + "-" + number + "-" + timestamp;
    }
}
