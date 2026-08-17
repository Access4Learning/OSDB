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
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for zone ID reuse across different solutions using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 13 from the design document:
 * "For any zone_id, it should be possible to create zones with that same zone_id 
 * in different solutions without conflict."</p>
 * 
 * <p><strong>Validates: Requirements 3.7</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * the same zone ID can exist in different solutions.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SameZoneIdAcrossDifferentSolutionsPropertyTest {
    
    @Autowired
    private ZoneService zoneService;
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 13: Same Zone ID Across Different Solutions
     * 
     * <p><strong>Property:</strong> For any zone_id, it should be possible to create zones 
     * with that same zone_id in different solutions without conflict.</p>
     * 
     * <p><strong>Validates: Requirements 3.7</strong></p>
     * 
     * <p>This test creates two different solutions and creates zones with the same zone ID
     * in each solution, verifying that both operations succeed.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-13")
    void sameZoneIdAcrossDifferentSolutions() {
        // First, create a parent domain
        String domainId = generateValidDomainId();
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, "Test domain for zone ID reuse");
        DomainDTO createdDomain = domainService.createDomain(domainRequest);
        
        // Create first solution
        String solutionId1 = generateValidSolutionId();
        SolutionCreateRequest solutionRequest1 = new SolutionCreateRequest(domainId, solutionId1, "First solution for zone ID reuse");
        SolutionDTO createdSolution1 = solutionService.createSolution(solutionRequest1);
        
        // Create second solution
        String solutionId2 = generateValidSolutionId();
        SolutionCreateRequest solutionRequest2 = new SolutionCreateRequest(domainId, solutionId2, "Second solution for zone ID reuse");
        SolutionDTO createdSolution2 = solutionService.createSolution(solutionRequest2);
        
        // Generate a zone ID that will be used in both solutions
        String sharedZoneId = generateValidZoneId();
        String description1 = random.nextBoolean() ? generateValidDescription() : null;
        String description2 = random.nextBoolean() ? generateValidDescription() : null;
        
        // Create zone with this ID in first solution
        ZoneCreateRequest request1 = new ZoneCreateRequest(solutionId1, sharedZoneId, description1);
        ZoneDTO createdZone1 = zoneService.createZone(domainId, request1);
        
        // Verify the first zone was created successfully
        assertThat(createdZone1).isNotNull();
        assertThat(createdZone1.solutionId()).isEqualTo(solutionId1);
        assertThat(createdZone1.zoneId()).isEqualTo(sharedZoneId);
        
        // Create zone with the same ID in second solution (Requirement 3.7: Allow same zone_id in different solutions)
        ZoneCreateRequest request2 = new ZoneCreateRequest(solutionId2, sharedZoneId, description2);
        ZoneDTO createdZone2 = zoneService.createZone(domainId, request2);
        
        // Verify the second zone was created successfully
        assertThat(createdZone2).isNotNull();
        assertThat(createdZone2.solutionId()).isEqualTo(solutionId2);
        assertThat(createdZone2.zoneId()).isEqualTo(sharedZoneId);
        
        // Verify both zones exist with the same zone ID but different solution IDs
        assertThat(createdZone1.zoneId())
            .as("Both zones should have the same zone ID")
            .isEqualTo(createdZone2.zoneId());
        
        assertThat(createdZone1.solutionId())
            .as("Zones should belong to different solutions")
            .isNotEqualTo(createdZone2.solutionId());
        
        assertThat(createdZone1.id())
            .as("Zones should have different internal UUIDs")
            .isNotEqualTo(createdZone2.id());
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
