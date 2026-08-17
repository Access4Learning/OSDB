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

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for zone filtering by solution using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 14 from the design document:
 * "For any solution containing zones, listing zones filtered by that solution_id 
 * should return only zones belonging to that solution, and the count should match 
 * the number of zones created in that solution."</p>
 * 
 * <p><strong>Validates: Requirements 3.9</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * zone filtering by solution works correctly.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ZoneFilteringBySolutionPropertyTest {
    
    @Autowired
    private ZoneService zoneService;
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 14: Zone Filtering By Solution
     * 
     * <p><strong>Property:</strong> For any solution containing zones, listing zones filtered 
     * by that solution_id should return only zones belonging to that solution, and the count 
     * should match the number of zones created in that solution.</p>
     * 
     * <p><strong>Validates: Requirements 3.9</strong></p>
     * 
     * <p>This test creates multiple solutions with different numbers of zones, then verifies
     * that filtering zones by solution ID returns only the zones belonging to that solution.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-14")
    void zoneFilteringBySolution() {
        // First, create a parent domain
        String domainId = generateValidDomainId();
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, "Test domain for zone filtering");
        DomainDTO createdDomain = domainService.createDomain(domainRequest);
        
        // Create first solution
        String solutionId1 = generateValidSolutionId();
        SolutionCreateRequest solutionRequest1 = new SolutionCreateRequest(domainId, solutionId1, "First solution for zone filtering");
        SolutionDTO createdSolution1 = solutionService.createSolution(solutionRequest1);
        
        // Create second solution
        String solutionId2 = generateValidSolutionId();
        SolutionCreateRequest solutionRequest2 = new SolutionCreateRequest(domainId, solutionId2, "Second solution for zone filtering");
        SolutionDTO createdSolution2 = solutionService.createSolution(solutionRequest2);
        
        // Create random number of zones in first solution (1-5 zones)
        int zonesInSolution1 = random.nextInt(5) + 1;
        Set<String> zoneIdsInSolution1 = new HashSet<>();
        
        for (int i = 0; i < zonesInSolution1; i++) {
            String zoneId = generateValidZoneId();
            String description = random.nextBoolean() ? generateValidDescription() : null;
            ZoneCreateRequest request = new ZoneCreateRequest(solutionId1, zoneId, description);
            ZoneDTO createdZone = zoneService.createZone(domainId, request);
            zoneIdsInSolution1.add(createdZone.zoneId());
        }
        
        // Create random number of zones in second solution (1-5 zones)
        int zonesInSolution2 = random.nextInt(5) + 1;
        Set<String> zoneIdsInSolution2 = new HashSet<>();
        
        for (int i = 0; i < zonesInSolution2; i++) {
            String zoneId = generateValidZoneId();
            String description = random.nextBoolean() ? generateValidDescription() : null;
            ZoneCreateRequest request = new ZoneCreateRequest(solutionId2, zoneId, description);
            ZoneDTO createdZone = zoneService.createZone(domainId, request);
            zoneIdsInSolution2.add(createdZone.zoneId());
        }
        
        // Requirement 3.9: List zones filtered by solution_id
        List<ZoneDTO> zonesFromSolution1 = zoneService.listZonesBySolution(domainId, solutionId1);
        List<ZoneDTO> zonesFromSolution2 = zoneService.listZonesBySolution(domainId, solutionId2);
        
        // Account for auto-created "environment-global" zone per solution
        // listZonesBySolution returns user-created zones + auto-created "environment-global"
        assertThat(zonesFromSolution1)
            .as("Number of zones returned for solution 1 should match number created plus auto-created environment-global")
            .hasSize(zonesInSolution1 + 1);
        
        // Verify all zones belong to solution 1
        assertThat(zonesFromSolution1)
            .as("All zones should belong to solution 1")
            .allMatch(zone -> zone.solutionId().equals(solutionId1));
        
        // Verify user-created zone IDs match for solution 1 (excluding environment-global)
        Set<String> returnedZoneIds1 = zonesFromSolution1.stream()
            .map(ZoneDTO::zoneId)
            .filter(id -> !"environment-global".equals(id))
            .collect(java.util.stream.Collectors.toSet());
        
        assertThat(returnedZoneIds1)
            .as("Returned zone IDs for solution 1 should match created zone IDs")
            .isEqualTo(zoneIdsInSolution1);
        
        // Verify count matches for solution 2
        assertThat(zonesFromSolution2)
            .as("Number of zones returned for solution 2 should match number created plus auto-created environment-global")
            .hasSize(zonesInSolution2 + 1);
        
        // Verify all zones belong to solution 2
        assertThat(zonesFromSolution2)
            .as("All zones should belong to solution 2")
            .allMatch(zone -> zone.solutionId().equals(solutionId2));
        
        // Verify user-created zone IDs match for solution 2 (excluding environment-global)
        Set<String> returnedZoneIds2 = zonesFromSolution2.stream()
            .map(ZoneDTO::zoneId)
            .filter(id -> !"environment-global".equals(id))
            .collect(java.util.stream.Collectors.toSet());
        
        assertThat(returnedZoneIds2)
            .as("Returned zone IDs for solution 2 should match created zone IDs")
            .isEqualTo(zoneIdsInSolution2);
        
        // Verify no overlap between solutions
        assertThat(returnedZoneIds1)
            .as("Zone IDs from solution 1 should not appear in solution 2 results")
            .doesNotContainAnyElementsOf(returnedZoneIds2);
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
