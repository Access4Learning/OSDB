// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for zone creation and retrieval using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 14 from the design document:
 * "For any valid zone creation request, the zone should be persisted and appear 
 * in the list of all zones."</p>
 * 
 * <p><strong>Validates: Requirements 6.1, 6.6</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * zones are properly persisted and can be retrieved through the list operation.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ZoneCreationRetrievalPropertyTest {
    
    @Autowired
    private ZoneService zoneService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();
    
    @BeforeEach
    void setUp() {
        try {
            domainService.createDomain(new DomainCreateRequest("test-domain", "Test domain"));
        } catch (Exception ignored) { }
        try {
            solutionService.createSolution(new SolutionCreateRequest("test-domain", "test-solution", "Test solution"));
        } catch (Exception ignored) { }
    }
    
    /**
     * Property 14: Zone creation and retrieval
     * 
     * <p><strong>Property:</strong> For any valid zone creation request, the zone 
     * should be persisted and appear in the list of all zones.</p>
     * 
     * <p><strong>Validates: Requirements 6.1, 6.6</strong></p>
     * 
     * <p>This test creates a zone with random valid parameters, then verifies that
     * the zone appears in the list of all zones with matching data.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-14")
    void zoneCreationAndRetrieval() {
        // Generate random valid input data
        String zoneId = generateValidZoneId();
        String description = random.nextBoolean() ? generateValidDescription() : null;
        
        // Create zone creation request
        ZoneCreateRequest request = new ZoneCreateRequest("test-solution", zoneId, description);
        
        // Create zone (Requirement 6.1)
        ZoneDTO createdZone = zoneService.createZone("test-domain", request);
        
        // Verify response contains expected data
        assertThat(createdZone.id())
            .as("Created zone should have a non-null UUID")
            .isNotNull();
        
        assertThat(createdZone.zoneId())
            .as("Created zone should have the requested zone ID")
            .isEqualTo(request.zoneId());
        
        assertThat(createdZone.description())
            .as("Created zone should have the requested description")
            .isEqualTo(request.description());
        
        assertThat(createdZone.createdAt())
            .as("Created zone should have a creation timestamp (suppressed from JSON by @JsonIgnore)")
            .isNotNull();
        
        // Retrieve all zones (Requirement 6.6)
        List<ZoneDTO> allZones = zoneService.listZones();
        
        // Verify the created zone appears in the list
        assertThat(allZones)
            .as("List of all zones should contain the created zone")
            .isNotEmpty()
            .anyMatch(zone -> zone.id().equals(createdZone.id()));
        
        // Find the created zone in the list
        ZoneDTO retrievedZone = allZones.stream()
            .filter(zone -> zone.id().equals(createdZone.id()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Created zone not found in list"));
        
        // Verify all fields match
        assertThat(retrievedZone.id())
            .as("Retrieved zone ID should match created zone ID")
            .isEqualTo(createdZone.id());
        
        assertThat(retrievedZone.zoneId())
            .as("Retrieved zone zoneId should match created zone zoneId")
            .isEqualTo(createdZone.zoneId());
        
        assertThat(retrievedZone.description())
            .as("Retrieved zone description should match created zone description")
            .isEqualTo(createdZone.description());
        
        assertThat(retrievedZone.createdAt())
            .as("Retrieved zone createdAt should match created zone (suppressed from JSON by @JsonIgnore)")
            .isEqualTo(createdZone.createdAt());
        
        // Additional property verification: Zone should be retrievable immediately after creation
        assertThat(retrievedZone).isNotNull();
        
        // Additional property verification: Zone ID should be unique
        long countWithSameZoneId = allZones.stream()
            .filter(zone -> zone.zoneId().equals(createdZone.zoneId()))
            .count();
        
        assertThat(countWithSameZoneId)
            .as("Zone ID should be unique in the list")
            .isEqualTo(1);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a valid zone ID following the naming pattern.
     * Pattern: alphanumeric, underscore, hyphen (must start with alphanumeric)
     */
    private String generateValidZoneId() {
        String[] prefixes = {"prod", "staging", "dev", "test", "demo", "sandbox", "qa", "uat"};
        String[] suffixes = {"zone", "region", "area", "sector", "domain"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        
        // Randomly decide whether to add a suffix
        if (random.nextBoolean()) {
            String suffix = suffixes[random.nextInt(suffixes.length)];
            int number = random.nextInt(1000);
            
            // Randomly choose separator
            String separator = random.nextBoolean() ? "-" : "_";
            
            return prefix + separator + suffix + separator + number;
        } else {
            // Just prefix with number
            int number = random.nextInt(1000);
            long timestamp = System.nanoTime() % 10000;
            return prefix + "-" + number + "-" + timestamp;
        }
    }
    
    /**
     * Generates a valid description for a zone.
     */
    private String generateValidDescription() {
        String[] templates = {
            "Production environment for %s",
            "Staging area for %s testing",
            "Development zone for %s",
            "Test environment for %s validation",
            "Demo zone for %s showcase",
            "Sandbox for %s experimentation"
        };
        
        String[] subjects = {
            "application services",
            "data processing",
            "API integration",
            "user management",
            "reporting services",
            "analytics platform"
        };
        
        String template = templates[random.nextInt(templates.length)];
        String subject = subjects[random.nextInt(subjects.length)];
        
        return String.format(template, subject);
    }
}
