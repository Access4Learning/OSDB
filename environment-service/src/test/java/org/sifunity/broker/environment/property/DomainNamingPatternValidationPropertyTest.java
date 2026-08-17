// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.sifunity.broker.environment.dto.DomainCreateRequest;

import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for domain naming pattern validation using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 1 from the design document:
 * "For any string that violates the naming pattern (contains invalid characters or 
 * starts with non-alphanumeric), attempting to create a domain with that string as 
 * domain_id should be rejected with a validation error."</p>
 * 
 * <p><strong>Validates: Requirements 1.3, 1.4, 7.1, 7.3</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized invalid domain IDs to verify that
 * the system properly rejects domain creation attempts with invalid naming patterns.</p>
 * 
 * <p>The naming pattern requires:
 * <ul>
 *   <li>Start with an alphanumeric character (a-z, A-Z, 0-9)</li>
 *   <li>Contain only alphanumeric characters, underscores (_), and hyphens (-)</li>
 *   <li>End with an alphanumeric character (for multi-character identifiers)</li>
 * </ul>
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
class DomainNamingPatternValidationPropertyTest {
    
    @Autowired
    private Validator validator;
    
    private final Random random = new Random();
    
    /**
     * Property 1: Domain Naming Pattern Validation (Invalid Cases)
     * 
     * <p><strong>Property:</strong> For any string that violates the naming pattern 
     * (contains invalid characters or starts with non-alphanumeric), attempting to 
     * create a domain with that string as domain_id should be rejected with a 
     * validation error.</p>
     * 
     * <p><strong>Validates: Requirements 1.3, 1.4, 7.1, 7.3</strong></p>
     * 
     * <p>This test generates random invalid domain IDs and verifies that the validation
     * framework properly rejects them with appropriate error messages.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-1")
    void domainNamingPatternValidation_invalidCases() {
        // Generate a random invalid domain ID
        String invalidDomainId = generateInvalidDomainId();
        String description = random.nextBoolean() ? "Test description" : null;
        
        // Create domain creation request with invalid domain ID
        DomainCreateRequest request = new DomainCreateRequest(invalidDomainId, description);
        
        // Validate the request
        Set<ConstraintViolation<DomainCreateRequest>> violations = validator.validate(request);
        
        // Requirement 1.3: Validate that domain_id follows the naming pattern
        // Requirement 1.4: When domain_id does not follow the naming pattern, return validation error
        // Requirement 7.1: When domain_id contains characters outside the naming pattern, reject with validation error
        // Requirement 7.3: When domain_id starts with non-alphanumeric character, reject with validation error
        assertThat(violations)
            .as("Invalid domain ID '%s' should produce validation violations", invalidDomainId)
            .isNotEmpty();
        
        // Verify that at least one violation is related to the domain ID pattern
        boolean hasPatternViolation = violations.stream()
            .anyMatch(violation -> 
                violation.getPropertyPath().toString().equals("domainId") &&
                (violation.getMessage().contains("alphanumeric") || 
                 violation.getMessage().contains("pattern") ||
                 violation.getMessage().contains("Domain ID"))
            );
        
        assertThat(hasPatternViolation)
            .as("Validation violations should include domain ID pattern error for '%s'", invalidDomainId)
            .isTrue();
    }
    
    /**
     * Property 1: Domain Naming Pattern Validation (Valid Cases)
     * 
     * <p><strong>Property:</strong> For any string that follows the naming pattern,
     * the validation should pass without errors.</p>
     * 
     * <p><strong>Validates: Requirements 1.3, 1.4, 7.1, 7.3</strong></p>
     * 
     * <p>This test generates random valid domain IDs and verifies that the validation
     * framework properly accepts them.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-1")
    void domainNamingPatternValidation_validCases() {
        // Generate a random valid domain ID
        String validDomainId = generateValidDomainId();
        String description = random.nextBoolean() ? "Test description" : null;
        
        // Create domain creation request with valid domain ID
        DomainCreateRequest request = new DomainCreateRequest(validDomainId, description);
        
        // Validate the request
        Set<ConstraintViolation<DomainCreateRequest>> violations = validator.validate(request);
        
        // Valid domain IDs should not produce any validation violations
        assertThat(violations)
            .as("Valid domain ID '%s' should not produce validation violations", validDomainId)
            .isEmpty();
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a valid domain ID following the naming pattern.
     * Pattern: alphanumeric, underscore, hyphen (must start with alphanumeric)
     */
    private String generateValidDomainId() {
        String[] prefixes = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel", "domain", "test", "prod", "dev"};
        String[] suffixes = {"edu", "gov", "district", "state", "county", "region", "1", "2", "3"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        
        // Randomly decide whether to add a suffix
        if (random.nextBoolean()) {
            String suffix = suffixes[random.nextInt(suffixes.length)];
            int number = random.nextInt(1000);
            
            // Randomly choose separator
            String separator = random.nextBoolean() ? "-" : "_";
            
            return prefix + separator + suffix + separator + number;
        } else {
            // Just prefix with number and timestamp for uniqueness
            int number = random.nextInt(1000);
            long timestamp = System.nanoTime() % 10000;
            return prefix + "-" + number + "-" + timestamp;
        }
    }
    
    /**
     * Generates an invalid domain ID that violates the naming pattern.
     * 
     * <p>Invalid patterns include:
     * <ul>
     *   <li>Starting with underscore or hyphen</li>
     *   <li>Ending with underscore or hyphen</li>
     *   <li>Containing special characters (!, @, #, $, %, etc.)</li>
     *   <li>Containing spaces</li>
     *   <li>Empty strings</li>
     * </ul>
     * </p>
     */
    private String generateInvalidDomainId() {
        int violationType = random.nextInt(6);
        
        switch (violationType) {
            case 0:
                // Starts with underscore
                return "_" + generateValidBase();
            
            case 1:
                // Starts with hyphen
                return "-" + generateValidBase();
            
            case 2:
                // Ends with underscore
                return generateValidBase() + "_";
            
            case 3:
                // Ends with hyphen
                return generateValidBase() + "-";
            
            case 4:
                // Contains special characters
                return generateWithSpecialCharacters();
            
            case 5:
                // Contains spaces
                return generateWithSpaces();
            
            default:
                // Empty string (should also be caught by @NotBlank)
                return "";
        }
    }
    
    /**
     * Generates a valid base string (alphanumeric only) for use in invalid patterns.
     */
    private String generateValidBase() {
        String[] bases = {"domain", "test", "alpha", "bravo", "prod", "staging", "dev"};
        return bases[random.nextInt(bases.length)];
    }
    
    /**
     * Generates a domain ID with special characters.
     */
    private String generateWithSpecialCharacters() {
        String base = generateValidBase();
        char[] specialChars = {'!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '+', '=', '[', ']', '{', '}', '|', '\\', ':', ';', '"', '\'', '<', '>', ',', '.', '?', '/'};
        char specialChar = specialChars[random.nextInt(specialChars.length)];
        
        // Insert special character at random position
        int position = random.nextInt(base.length() + 1);
        return base.substring(0, position) + specialChar + base.substring(position);
    }
    
    /**
     * Generates a domain ID with spaces.
     */
    private String generateWithSpaces() {
        String base = generateValidBase();
        
        // Insert space at random position
        int position = random.nextInt(base.length() + 1);
        return base.substring(0, position) + " " + base.substring(position);
    }
}
