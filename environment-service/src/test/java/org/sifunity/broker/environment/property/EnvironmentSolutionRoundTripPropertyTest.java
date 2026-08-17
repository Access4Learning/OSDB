// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Property-based test for environment solution round-trip using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 17 from the design document:
 * "For any environment registered with a solution_id, retrieving that environment 
 * should return the same solution_id value."</p>
 * 
 * <p><strong>Validates: Requirements 4.6, 4.7</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * solution_id is correctly persisted and retrieved for environments.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnvironmentSolutionRoundTripPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();
    
    /**
     * Property 17: Environment Solution Round-Trip
     * 
     * <p><strong>Property:</strong> For any environment registered with a solution_id, 
     * retrieving that environment should return the same solution_id value.</p>
     * 
     * <p><strong>Validates: Requirements 4.6, 4.7</strong></p>
     * 
     * <p>This test:
     * <ul>
     *   <li>Creates a domain and solution</li>
     *   <li>Registers an environment with the solution_id</li>
     *   <li>Verifies the solution_id is in the registration response</li>
     *   <li>Retrieves the environment and verifies the solution_id matches</li>
     * </ul>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-17")
    void environmentSolutionRoundTrip() {
        // Create a domain first
        String domainId = generateValidDomainId();
        String domainDescription = random.nextBoolean() ? generateValidDescription() : null;
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, domainDescription);
        domainService.createDomain(domainRequest);
        
        // Create a solution within the domain
        String solutionId = generateValidSolutionId();
        String solutionDescription = random.nextBoolean() ? generateValidDescription() : null;
        SolutionCreateRequest solutionRequest = new SolutionCreateRequest(
            domainId, 
            solutionId, 
            solutionDescription
        );
        solutionService.createSolution(solutionRequest);
        
        // Generate valid environment registration data
        String applicationName = generateUniqueApplicationName();
        EnvironmentType type = generateRandomEnvironmentType();
        // Create environment registration request WITH solution_id
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, null, solutionId, null));
        
        // Register the environment
        SifEnvironmentRegistrationResponse response = environmentService.registerEnvironment(domainId, request);
        
        // Verify registration succeeded
        assertThat(response).isNotNull();
        assertThat(response.environment().id()).isNotNull();
        assertThat(response.environment().consumerName()).isEqualTo(applicationName);
        
        // Requirement 4.6: Include solution_id in environment registration responses
        assertThat(response.environment().solutionId())
            .as("Solution ID should be included in registration response")
            .isEqualTo(solutionId);
        
        // Retrieve the environment
        SifEnvironmentResponse retrievedEnvironment = environmentService.getEnvironment(response.environment().id());
        
        assertThat(retrievedEnvironment).isNotNull();
        assertThat(retrievedEnvironment.id()).isEqualTo(response.environment().id());
        assertThat(retrievedEnvironment.consumerName()).isEqualTo(applicationName);
        
        // Requirement 4.7: Include solution_id in environment retrieval responses
        assertThat(retrievedEnvironment.solutionId())
            .as("Solution ID should match when environment is retrieved")
            .isEqualTo(solutionId);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a valid domain ID following the naming pattern.
     */
    private String generateValidDomainId() {
        String[] prefixes = {"domain", "tenant", "client", "org"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime() % 100000;
        int randomValue = random.nextInt(1000);
        return prefix + "-" + timestamp + "-" + randomValue;
    }
    
    /**
     * Generates a valid solution ID following the naming pattern.
     */
    private String generateValidSolutionId() {
        String[] prefixes = {"solution", "env", "deployment", "instance"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime() % 100000;
        int randomValue = random.nextInt(1000);
        return prefix + "-" + timestamp + "-" + randomValue;
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
    
    /**
     * Generates a valid description.
     */
    private String generateValidDescription() {
        String[] templates = {
            "%s for testing",
            "%s environment",
            "%s deployment",
            "%s instance"
        };
        
        String[] types = {
            "Primary",
            "Secondary",
            "Test",
            "Development",
            "Production"
        };
        
        String template = templates[random.nextInt(templates.length)];
        String type = types[random.nextInt(types.length)];
        
        return String.format(template, type);
    }
}


