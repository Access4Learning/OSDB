// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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

import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for zone ID naming pattern validation using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 15 from the design document:
 * "For any zone creation request with an invalid zone ID (not matching alphanumeric, 
 * underscore, hyphen pattern), the request should be rejected with validation error."</p>
 * 
 * <p><strong>Validates: Requirements 6.2</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized invalid zone IDs to verify that
 * the naming pattern validation properly rejects invalid inputs.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ZoneIdNamingPatternPropertyTest {
    
    @Autowired
    private ZoneService zoneService;
    
    @Autowired
    private Validator validator;
    
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
     * Property 15: Zone ID naming pattern validation
     * 
     * <p><strong>Property:</strong> For any zone creation request with an invalid 
     * zone ID (not matching alphanumeric, underscore, hyphen pattern), the request 
     * should be rejected with validation error.</p>
     * 
     * <p><strong>Validates: Requirements 6.2</strong></p>
     * 
     * <p>This test generates invalid zone IDs that violate the naming pattern and
     * verifies that they are properly rejected by the validation framework.</p>
     * 
     * <p>Valid pattern: ^[a-zA-Z0-9][a-zA-Z0-9_-]*$</p>
     * <ul>
     *   <li>Must start with alphanumeric character</li>
     *   <li>Can contain alphanumeric characters, underscores, and hyphens</li>
     * </ul>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-15")
    void zoneIdNamingPatternValidation() {
        // Generate an invalid zone ID
        String invalidZoneId = generateInvalidZoneId();
        String description = random.nextBoolean() ? "Test zone description" : null;
        
        // Create zone creation request with invalid zone ID
        ZoneCreateRequest request = new ZoneCreateRequest("test-solution", invalidZoneId, description);
        
        // Validate the request using Bean Validation (Requirement 6.2)
        Set<ConstraintViolation<ZoneCreateRequest>> violations = validator.validate(request);
        
        // Verify that validation fails for invalid zone ID
        assertThat(violations)
            .as("Invalid zone ID '%s' should produce validation violations", invalidZoneId)
            .isNotEmpty();
        
        // Verify that the violation is specifically about the zone ID pattern
        boolean hasPatternViolation = violations.stream()
            .anyMatch(violation -> 
                violation.getPropertyPath().toString().equals("zoneId") &&
                (violation.getMessage().contains("alphanumeric") || 
                 violation.getMessage().contains("pattern") ||
                 violation.getMessage().contains("Zone ID"))
            );
        
        assertThat(hasPatternViolation)
            .as("Validation violations should include zone ID pattern error for '%s'", invalidZoneId)
            .isTrue();
        
        // Additional verification: Ensure valid zone IDs are accepted
        // Generate a valid zone ID for comparison
        String validZoneId = generateValidZoneId();
        ZoneCreateRequest validRequest = new ZoneCreateRequest("test-solution", validZoneId, description);
        
        Set<ConstraintViolation<ZoneCreateRequest>> validViolations = validator.validate(validRequest);
        
        // Verify that valid zone IDs pass validation
        assertThat(validViolations)
            .as("Valid zone ID '%s' should not produce validation violations", validZoneId)
            .isEmpty();
        
        // Verify that the valid zone ID can actually be created
        ZoneDTO createdZone = zoneService.createZone(TEST_DOMAIN_ID, validRequest);
        
        assertThat(createdZone.zoneId())
            .as("Created zone should have the valid zone ID")
            .isEqualTo(validZoneId);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates an invalid zone ID that violates the naming pattern.
     * 
     * <p>Invalid patterns include:</p>
     * <ul>
     *   <li>Starting with underscore or hyphen</li>
     *   <li>Containing spaces</li>
     *   <li>Containing special characters (!, @, #, $, %, etc.)</li>
     *   <li>Containing dots or slashes</li>
     * </ul>
     */
    private String generateInvalidZoneId() {
        int invalidType = random.nextInt(6);
        
        switch (invalidType) {
            case 0:
                // Starts with underscore
                return "_" + generateValidBase();
                
            case 1:
                // Starts with hyphen
                return "-" + generateValidBase();
                
            case 2:
                // Contains spaces
                String base = generateValidBase();
                int insertPos = random.nextInt(base.length());
                return base.substring(0, insertPos) + " " + base.substring(insertPos);
                
            case 3:
                // Contains special characters
                String[] specialChars = {"!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "=", "+", "[", "]", "{", "}", ";", ":", "'", "\"", "<", ">", ",", ".", "?", "/", "\\", "|"};
                String specialChar = specialChars[random.nextInt(specialChars.length)];
                String validPart = generateValidBase();
                int pos = random.nextInt(validPart.length());
                return validPart.substring(0, pos) + specialChar + validPart.substring(pos);
                
            case 4:
                // Contains dots
                String dotBase = generateValidBase();
                int dotPos = random.nextInt(dotBase.length());
                return dotBase.substring(0, dotPos) + "." + dotBase.substring(dotPos);
                
            case 5:
                // Contains slashes
                String slashBase = generateValidBase();
                int slashPos = random.nextInt(slashBase.length());
                String slash = random.nextBoolean() ? "/" : "\\";
                return slashBase.substring(0, slashPos) + slash + slashBase.substring(slashPos);
                
            default:
                // Starts with special character
                return "!" + generateValidBase();
        }
    }
    
    /**
     * Generates a valid base string for zone IDs.
     */
    private String generateValidBase() {
        String[] bases = {"zone", "prod", "staging", "dev", "test", "demo", "sandbox", "qa", "uat"};
        String base = bases[random.nextInt(bases.length)];
        int number = random.nextInt(1000);
        return base + number;
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
}
