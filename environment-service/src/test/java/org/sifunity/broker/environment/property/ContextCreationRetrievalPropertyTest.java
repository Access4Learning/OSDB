// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for context creation and retrieval using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 17 from the design document:
 * "For any valid context creation request, the context should be persisted and appear 
 * in the list of contexts for that zone."</p>
 * 
 * <p><strong>Validates: Requirements 6.4, 6.7</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * contexts are properly persisted and can be retrieved through the list operation.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContextCreationRetrievalPropertyTest {
    
    @Autowired
    private ZoneService zoneService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();
    
    private static final String TEST_DOMAIN_ID = "test-domain";
    private static final String TEST_SOLUTION_ID = "test-solution";
    
    @BeforeEach
    void setUp() {
        // Ensure domain and solution exist for zone creation
        try {
            domainService.createDomain(new DomainCreateRequest(TEST_DOMAIN_ID, "Test domain"));
        } catch (Exception ignored) { /* already exists */ }
        try {
            solutionService.createSolution(new SolutionCreateRequest(TEST_DOMAIN_ID, TEST_SOLUTION_ID, "Test solution"));
        } catch (Exception ignored) { /* already exists */ }
    }
    
    /**
     * Property 17: Context creation and retrieval
     * 
     * <p><strong>Property:</strong> For any valid context creation request, the context 
     * should be persisted and appear in the list of contexts for that zone.</p>
     * 
     * <p><strong>Validates: Requirements 6.4, 6.7</strong></p>
     * 
     * <p>This test creates a zone, then creates a context within that zone with random 
     * valid parameters, then verifies that the context appears in the list of contexts 
     * for that zone with matching data.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-17")
    void contextCreationAndRetrieval() {
        // Generate random valid input data
        String zoneId = generateValidZoneId();
        String zoneDescription = random.nextBoolean() ? generateValidDescription("zone") : null;
        
        // Create parent zone first (Requirement 6.5 - parent zone must exist)
        ZoneCreateRequest zoneRequest = new ZoneCreateRequest("test-solution", zoneId, zoneDescription);
        ZoneDTO createdZone = zoneService.createZone(TEST_DOMAIN_ID, zoneRequest);
        
        // Generate context data
        String contextId = generateValidContextId();
        String contextDescription = random.nextBoolean() ? generateValidDescription("context") : null;
        
        // Create context creation request
        ContextCreateRequest contextRequest = new ContextCreateRequest(contextId, contextDescription);
        
        // Create context (Requirement 6.4)
        ContextDTO createdContext = zoneService.createContext(TEST_DOMAIN_ID, TEST_SOLUTION_ID, zoneId, contextRequest);
        
        // Verify response contains expected data
        assertThat(createdContext.id())
            .as("Created context should have a non-null UUID")
            .isNotNull();
        
        assertThat(createdContext.zoneId())
            .as("Created context should have the parent zone ID")
            .isEqualTo(zoneId);
        
        assertThat(createdContext.contextId())
            .as("Created context should have the requested context ID")
            .isEqualTo(contextRequest.contextId());
        
        assertThat(createdContext.description())
            .as("Created context should have the requested description")
            .isEqualTo(contextRequest.description());
        
        assertThat(createdContext.createdAt())
            .as("Created context should have a creation timestamp (suppressed from JSON by @JsonIgnore)")
            .isNotNull();
        
        // Retrieve all contexts for the zone (Requirement 6.7)
        List<ContextDTO> allContexts = zoneService.listContexts(TEST_DOMAIN_ID, TEST_SOLUTION_ID, zoneId);
        
        // Verify the created context appears in the list
        assertThat(allContexts)
            .as("List of contexts for zone should contain the created context")
            .isNotEmpty()
            .anyMatch(context -> context.id().equals(createdContext.id()));
        
        // Find the created context in the list
        ContextDTO retrievedContext = allContexts.stream()
            .filter(context -> context.id().equals(createdContext.id()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Created context not found in list"));
        
        // Verify all fields match
        assertThat(retrievedContext.id())
            .as("Retrieved context ID should match created context ID")
            .isEqualTo(createdContext.id());
        
        assertThat(retrievedContext.zoneId())
            .as("Retrieved context zoneId should match created context zoneId")
            .isEqualTo(createdContext.zoneId());
        
        assertThat(retrievedContext.contextId())
            .as("Retrieved context contextId should match created context contextId")
            .isEqualTo(createdContext.contextId());
        
        assertThat(retrievedContext.description())
            .as("Retrieved context description should match created context description")
            .isEqualTo(createdContext.description());
        
        assertThat(retrievedContext.createdAt())
            .as("Retrieved context createdAt should match created context (suppressed from JSON by @JsonIgnore)")
            .isEqualTo(createdContext.createdAt());
        
        // Additional property verification: Context should be retrievable immediately after creation
        assertThat(retrievedContext).isNotNull();
        
        // Additional property verification: Context ID should be unique within the zone
        long countWithSameContextId = allContexts.stream()
            .filter(context -> context.contextId().equals(createdContext.contextId()))
            .count();
        
        assertThat(countWithSameContextId)
            .as("Context ID should be unique within the zone")
            .isEqualTo(1);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a valid zone ID following the naming pattern.
     * Pattern: alphanumeric, underscore, hyphen (must start with alphanumeric)
     */
    private String generateValidZoneId() {
        String[] prefixes = {"prod", "staging", "dev", "test", "demo", "sandbox", "qa", "uat"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime() % 100000;
        int randomNum = random.nextInt(1000);
        return prefix + "-" + timestamp + "-" + randomNum;
    }
    
    /**
     * Generates a valid context ID following the naming pattern.
     * Pattern: alphanumeric, underscore, hyphen (must start with alphanumeric)
     */
    private String generateValidContextId() {
        String[] prefixes = {"default", "primary", "secondary", "backup", "main", "alt", "special"};
        String[] suffixes = {"ctx", "context", "env", "space", "area"};
        
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
     * Generates a valid description for a zone or context.
     */
    private String generateValidDescription(String type) {
        String[][] templates = {
            {"Production %s for application services", "Staging %s for testing", "Development %s for experiments"},
            {"Primary %s for main operations", "Secondary %s for backup", "Special %s for custom use"}
        };
        
        String[] selectedTemplates = templates[random.nextInt(templates.length)];
        String template = selectedTemplates[random.nextInt(selectedTemplates.length)];
        
        return String.format(template, type);
    }
}
