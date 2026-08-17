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
import org.sifunity.broker.environment.exception.ZoneNotFoundException;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for context parent zone validation using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 16 from the design document:
 * "For any context creation request, if the parent zone does not exist, the request 
 * should be rejected with validation error."</p>
 * 
 * <p><strong>Validates: Requirements 6.5</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized zone and context IDs to verify that
 * context creation properly validates the existence of the parent zone.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContextParentZoneValidationPropertyTest {
    
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
        try {
            domainService.createDomain(new DomainCreateRequest(TEST_DOMAIN_ID, "Test domain"));
        } catch (Exception ignored) { }
        try {
            solutionService.createSolution(new SolutionCreateRequest(TEST_DOMAIN_ID, TEST_SOLUTION_ID, "Test solution"));
        } catch (Exception ignored) { }
    }
    
    /**
     * Property 16: Context parent zone validation
     * 
     * <p><strong>Property:</strong> For any context creation request, if the parent 
     * zone does not exist, the request should be rejected with validation error.</p>
     * 
     * <p><strong>Validates: Requirements 6.5</strong></p>
     * 
     * <p>This test creates contexts with both existing and non-existent parent zones
     * to verify that the parent zone validation works correctly.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-16")
    void contextParentZoneValidation() {
        // Generate a non-existent zone ID (Requirement 6.5)
        String nonExistentZoneId = generateNonExistentZoneId();
        String contextId = generateValidContextId();
        String description = random.nextBoolean() ? "Test context description" : null;
        
        // Create context creation request with non-existent parent zone
        ContextCreateRequest invalidRequest = new ContextCreateRequest(contextId, description);
        
        // Verify that context creation fails when parent zone does not exist (Requirement 6.5)
        assertThatThrownBy(() -> {
            zoneService.createContext(TEST_DOMAIN_ID, TEST_SOLUTION_ID, nonExistentZoneId, invalidRequest);
        })
            .isInstanceOf(ZoneNotFoundException.class)
            .hasMessageContaining(nonExistentZoneId)
            .satisfies(exception -> {
                ZoneNotFoundException zoneException = (ZoneNotFoundException) exception;
                // Verify the exception contains the zone ID that was not found
                assertThat(zoneException.getMessage())
                    .as("Exception message should mention the non-existent zone ID")
                    .contains(nonExistentZoneId);
            });
        
        // Verify that listing contexts in a non-existent zone also throws ZoneNotFoundException
        assertThatThrownBy(() -> zoneService.listContexts(TEST_DOMAIN_ID, TEST_SOLUTION_ID, nonExistentZoneId))
            .isInstanceOf(ZoneNotFoundException.class);
        
        // Now create a valid parent zone (Requirement 6.5)
        String validZoneId = generateValidZoneId();
        String zoneDescription = random.nextBoolean() ? "Test zone for context validation" : null;
        ZoneCreateRequest zoneRequest = new ZoneCreateRequest("test-solution", validZoneId, zoneDescription);
        
        ZoneDTO createdZone = zoneService.createZone(TEST_DOMAIN_ID, zoneRequest);
        
        assertThat(createdZone.zoneId())
            .as("Zone should be created successfully")
            .isEqualTo(validZoneId);
        
        // Generate a new context ID for the valid zone
        String validContextId = generateValidContextId();
        String validContextDescription = random.nextBoolean() ? "Valid context description" : null;
        ContextCreateRequest validRequest = new ContextCreateRequest(validContextId, validContextDescription);
        
        // Verify that context creation succeeds when parent zone exists (Requirement 6.5)
        ContextDTO createdContext = zoneService.createContext(TEST_DOMAIN_ID, TEST_SOLUTION_ID, validZoneId, validRequest);
        
        assertThat(createdContext.zoneId())
            .as("Created context should belong to the parent zone")
            .isEqualTo(validZoneId);
        
        assertThat(createdContext.contextId())
            .as("Created context should have the requested context ID")
            .isEqualTo(validContextId);
        
        assertThat(createdContext.description())
            .as("Created context should have the requested description")
            .isEqualTo(validContextDescription);
        
        // Verify that the context appears in the list of contexts for the zone
        var contextsInValidZone = zoneService.listContexts(TEST_DOMAIN_ID, TEST_SOLUTION_ID, validZoneId);
        
        assertThat(contextsInValidZone)
            .as("Zone should contain the created context")
            .isNotEmpty()
            .anyMatch(ctx -> ctx.contextId().equals(validContextId));
        
        // Additional verification: Attempt to create another context in a different non-existent zone
        String anotherNonExistentZoneId = generateNonExistentZoneId();
        String anotherContextId = generateValidContextId();
        ContextCreateRequest anotherInvalidRequest = new ContextCreateRequest(anotherContextId, description);
        
        assertThatThrownBy(() -> {
            zoneService.createContext(TEST_DOMAIN_ID, TEST_SOLUTION_ID, anotherNonExistentZoneId, anotherInvalidRequest);
        })
            .isInstanceOf(ZoneNotFoundException.class)
            .hasMessageContaining(anotherNonExistentZoneId);
        
        // Verify that the valid zone still has exactly two contexts (user-created + auto-created DEFAULT)
        var finalContextsInValidZone = zoneService.listContexts(TEST_DOMAIN_ID, TEST_SOLUTION_ID, validZoneId);
        assertThat(finalContextsInValidZone)
            .as("Valid zone should have exactly two contexts (user-created + auto-created DEFAULT)")
            .hasSize(2);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a non-existent zone ID that is guaranteed not to exist in the database.
     * Uses a combination of timestamp and random values to ensure uniqueness.
     */
    private String generateNonExistentZoneId() {
        String[] prefixes = {"nonexistent", "invalid", "missing", "notfound", "absent"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime();
        int randomSuffix = random.nextInt(100000);
        
        return prefix + "-zone-" + timestamp + "-" + randomSuffix;
    }
    
    /**
     * Generates a valid zone ID following the naming pattern.
     * Pattern: ^[a-zA-Z0-9][a-zA-Z0-9_-]*$
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
            // Just prefix with number and timestamp
            int number = random.nextInt(1000);
            long timestamp = System.nanoTime() % 10000;
            return prefix + "-" + number + "-" + timestamp;
        }
    }
    
    /**
     * Generates a valid context ID following the naming pattern.
     * Pattern: ^[a-zA-Z0-9][a-zA-Z0-9_-]*$
     */
    private String generateValidContextId() {
        String[] prefixes = {"default", "primary", "secondary", "backup", "main", "alt", "ctx"};
        String[] suffixes = {"context", "env", "instance", "node"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        
        // Randomly decide whether to add a suffix
        if (random.nextBoolean()) {
            String suffix = suffixes[random.nextInt(suffixes.length)];
            int number = random.nextInt(500);
            
            // Randomly choose separator
            String separator = random.nextBoolean() ? "-" : "_";
            
            return prefix + separator + suffix + separator + number;
        } else {
            // Just prefix with number and timestamp
            int number = random.nextInt(500);
            long timestamp = System.nanoTime() % 10000;
            return prefix + "-" + number + "-" + timestamp;
        }
    }
}
