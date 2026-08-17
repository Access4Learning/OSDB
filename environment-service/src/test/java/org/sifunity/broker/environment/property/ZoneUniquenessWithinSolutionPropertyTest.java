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
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.exception.DuplicateZoneException;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for zone uniqueness within solution using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 12 from the design document:
 * "For any solution and zone_id, after successfully creating a zone with that zone_id 
 * in that solution, attempting to create another zone with the same zone_id in the same 
 * solution should fail with a conflict error."</p>
 * 
 * <p><strong>Validates: Requirements 3.5, 3.6</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * zone IDs are unique within a solution.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ZoneUniquenessWithinSolutionPropertyTest {
    
    @Autowired
    private ZoneService zoneService;
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 12: Zone Uniqueness Within Solution
     * 
     * <p><strong>Property:</strong> For any solution and zone_id, after successfully creating 
     * a zone with that zone_id in that solution, attempting to create another zone with the 
     * same zone_id in the same solution should fail with a conflict error.</p>
     * 
     * <p><strong>Validates: Requirements 3.5, 3.6</strong></p>
     * 
     * <p>This test creates a zone in a solution, then attempts to create another zone
     * with the same zone ID in the same solution and verifies that the operation fails
     * with DuplicateZoneException.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-12")
    void zoneUniquenessWithinSolution() {
        // First, create a parent domain
        String domainId = generateValidDomainId();
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, "Test domain for zone uniqueness");
        DomainDTO createdDomain = domainService.createDomain(domainRequest);
        
        // Create a parent solution
        String solutionId = generateValidSolutionId();
        SolutionCreateRequest solutionRequest = new SolutionCreateRequest(domainId, solutionId, "Test solution for zone uniqueness");
        SolutionDTO createdSolution = solutionService.createSolution(solutionRequest);
        
        // Generate a zone ID
        String zoneId = generateValidZoneId();
        String description1 = random.nextBoolean() ? generateValidDescription() : null;
        
        // Create the first zone
        ZoneCreateRequest request1 = new ZoneCreateRequest(solutionId, zoneId, description1);
        ZoneDTO createdZone = zoneService.createZone(domainId, request1);
        
        // Verify the first zone was created successfully
        assertThat(createdZone).isNotNull();
        assertThat(createdZone.solutionId()).isEqualTo(solutionId);
        assertThat(createdZone.zoneId()).isEqualTo(zoneId);
        
        // Attempt to create a second zone with the same zone ID in the same solution
        String description2 = random.nextBoolean() ? generateValidDescription() : null;
        ZoneCreateRequest request2 = new ZoneCreateRequest(solutionId, zoneId, description2);
        
        // Requirement 3.5: Enforce uniqueness of zone_id within a solution
        // Requirement 3.6: Return error indicating duplicate zone
        assertThatThrownBy(() -> zoneService.createZone(domainId, request2))
            .as("Creating duplicate zone in same solution should throw DuplicateZoneException")
            .isInstanceOf(DuplicateZoneException.class)
            .hasMessageContaining(zoneId)
            .hasMessageContaining(solutionId);
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
        
        String environment = environments[random.nextInt(environments.length)];
        int number = random.nextInt(100);
        long timestamp = System.nanoTime() % 10000;
        
        return environment + "-" + number + "-" + timestamp;
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
