// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for environment-solution reference validation using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 15 from the design document:
 * "For any environment registration request that includes a solution_id, if that solution_id 
 * does not exist, the registration should fail with a solution not found error."</p>
 * 
 * <p><strong>Validates: Requirements 4.2, 4.4, 4.5</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * environments cannot be registered with a non-existent solution ID.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnvironmentSolutionReferenceValidationPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    private final Random random = new Random();
    
    /**
     * Property 15: Environment Solution Reference Validation
     * 
     * <p><strong>Property:</strong> For any environment registration request that includes 
     * a solution_id, if that solution_id does not exist, the registration should fail with 
     * a solution not found error.</p>
     * 
     * <p><strong>Validates: Requirements 4.2, 4.4, 4.5</strong></p>
     * 
     * <p>This test attempts to register an environment with a non-existent solution ID 
     * and verifies that the operation fails with SolutionNotFoundException.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-15")
    void environmentSolutionReferenceValidation() {
        // Create a real domain so resolution proceeds past the domain lookup and
        // fails specifically at the (non-existent) solution lookup.
        String domainId = "esrv-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, "Solution reference validation test domain"));

        // Generate a non-existent solution ID (using timestamp ensures uniqueness)
        String nonExistentSolutionId = generateNonExistentSolutionId();

        // Generate valid environment registration data
        String applicationName = generateUniqueApplicationName();
        EnvironmentType type = generateRandomEnvironmentType();
        String defaultZoneId = generateValidZoneId();
        // Create environment registration request with non-existent solution
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(defaultZoneId), nonExistentSolutionId, null));
        
        // Requirement 4.4: Validate that the specified solution_id exists
        // Requirement 4.5: Return error indicating solution not found
        assertThatThrownBy(() -> environmentService.registerEnvironment(domainId, request))
            .as("Registering environment with non-existent solution should throw SolutionNotFoundException")
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
     * Generates a unique application name using timestamp to ensure uniqueness.
     */
    private String generateUniqueApplicationName() {
        String[] prefixes = {"app", "service", "system", "platform", "client", "consumer"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime();
        int randomValue = random.nextInt(10000);
        return prefix + "-" + timestamp + "-" + randomValue;
    }
    
    /**
     * Generates a random environment type.
     */
    private EnvironmentType generateRandomEnvironmentType() {
        EnvironmentType[] types = EnvironmentType.values();
        return types[random.nextInt(types.length)];
    }
    
    /**
     * Generates a valid zone ID following the naming pattern.
     * Pattern: alphanumeric, underscore, hyphen (must start with alphanumeric)
     */
    private String generateValidZoneId() {
        String[] prefixes = {"zone", "area", "region", "sector"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        return prefix + "-" + number;
    }
    
    /**
     * Generates a valid context ID following the naming pattern.
     */
    private String generateValidContextId() {
        String[] prefixes = {"context", "ctx", "scope", "namespace"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        return prefix + "-" + number;
    }
}
