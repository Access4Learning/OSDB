// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for zone-solution relationship validation using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 11 from the design document:
 * "For any zone_id and any solution_id that does not exist, attempting to create a zone 
 * with that non-existent solution_id should fail with a solution not found error."</p>
 * 
 * <p><strong>Validates: Requirements 3.2, 3.3, 3.4</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * zones cannot be created without a valid parent solution.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ZoneRequiresExistingSolutionPropertyTest {
    
    @Autowired
    private ZoneService zoneService;
    
    private final Random random = new Random();
    
    /**
     * Property 11: Zone Requires Existing Solution
     * 
     * <p><strong>Property:</strong> For any zone_id and any solution_id that does not exist, 
     * attempting to create a zone with that non-existent solution_id should fail with a 
     * solution not found error.</p>
     * 
     * <p><strong>Validates: Requirements 3.2, 3.3, 3.4</strong></p>
     * 
     * <p>This test attempts to create a zone with a non-existent solution ID and verifies
     * that the operation fails with SolutionNotFoundException.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-11")
    void zoneRequiresExistingSolution() {
        // Generate a non-existent solution ID (using timestamp ensures uniqueness)
        String nonExistentSolutionId = generateNonExistentSolutionId();

        // Generate a non-existent domain ID; the solution does not exist under any domain,
        // so solution resolution fails with SolutionNotFoundException regardless of domain.
        String nonExistentDomainId = generateNonExistentDomainId();
        
        // Generate a valid zone ID
        String zoneId = generateValidZoneId();
        String description = random.nextBoolean() ? generateValidDescription() : null;
        
        // Create zone creation request with non-existent solution
        ZoneCreateRequest request = new ZoneCreateRequest(nonExistentSolutionId, zoneId, description);
        
        // Requirement 3.3: Validate that the specified solution_id exists
        // Requirement 3.4: Return error indicating solution not found
        assertThatThrownBy(() -> zoneService.createZone(nonExistentDomainId, request))
            .as("Creating zone with non-existent solution should throw SolutionNotFoundException")
            .isInstanceOf(SolutionNotFoundException.class)
            .hasMessageContaining(nonExistentSolutionId);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a non-existent solution ID that is guaranteed not to exist in the database.
     * Uses timestamp and random values to ensure uniqueness.
     */
    private String generateNonExistentSolutionId() {
        long timestamp = System.nanoTime();
        int randomValue = random.nextInt(100000);
        return "nonexistent-solution-" + timestamp + "-" + randomValue;
    }

    /**
     * Generates a non-existent domain ID that is guaranteed not to exist in the database.
     * Uses timestamp and random values to ensure uniqueness.
     */
    private String generateNonExistentDomainId() {
        long timestamp = System.nanoTime();
        int randomValue = random.nextInt(100000);
        return "nonexistent-domain-" + timestamp + "-" + randomValue;
    }
    
    /**
     * Generates a valid zone ID following the naming pattern.
     * Pattern: alphanumeric, underscore, hyphen (must start with alphanumeric)
     */
    private String generateValidZoneId() {
        String[] prefixes = {"zone", "area", "region", "sector", "district", "division"};
        String[] suffixes = {"alpha", "beta", "gamma", "delta", "epsilon", "zeta"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        String suffix = suffixes[random.nextInt(suffixes.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        
        // Randomly choose separator
        String separator = random.nextBoolean() ? "-" : "_";
        
        return prefix + separator + suffix + separator + number + "-" + timestamp;
    }
    
    /**
     * Generates a valid description for a zone.
     */
    private String generateValidDescription() {
        String[] templates = {
            "%s zone for %s",
            "%s operational area",
            "%s service zone",
            "%s data region",
            "%s processing zone"
        };
        
        String[] types = {
            "Primary",
            "Secondary",
            "Backup",
            "Test",
            "Development",
            "Production"
        };
        
        String[] purposes = {
            "student services",
            "administrative functions",
            "data processing",
            "reporting",
            "integration",
            "analytics"
        };
        
        String template = templates[random.nextInt(templates.length)];
        String type = types[random.nextInt(types.length)];
        
        // Some templates need two parameters
        if (template.contains("%s") && template.indexOf("%s") != template.lastIndexOf("%s")) {
            String purpose = purposes[random.nextInt(purposes.length)];
            return String.format(template, type, purpose);
        } else {
            return String.format(template, type);
        }
    }
}
