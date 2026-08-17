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
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Property-based test for environment filtering by solution using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 18 from the design document:
 * "For any solution containing environments, listing environments filtered by that 
 * solution_id should return only environments belonging to that solution."</p>
 * 
 * <p><strong>Validates: Requirements 4.8</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * environment filtering by solution ID works correctly.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnvironmentFilteringBySolutionPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();
    
    /**
     * Property 18: Environment Filtering By Solution
     * 
     * <p><strong>Property:</strong> For any solution containing environments, listing 
     * environments filtered by that solution_id should return only environments belonging 
     * to that solution.</p>
     * 
     * <p><strong>Validates: Requirements 4.8</strong></p>
     * 
     * <p>This test:
     * <ul>
     *   <li>Creates a domain and two solutions</li>
     *   <li>Registers multiple environments in each solution</li>
     *   <li>Filters environments by solution_id</li>
     *   <li>Verifies only environments from that solution are returned</li>
     * </ul>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-18")
    void environmentFilteringBySolution() {
        // Create a domain
        String domainId = generateValidDomainId();
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, null);
        domainService.createDomain(domainRequest);
        
        // Create two solutions within the domain
        String solutionId1 = generateValidSolutionId();
        String solutionId2 = generateValidSolutionId();
        
        SolutionCreateRequest solutionRequest1 = new SolutionCreateRequest(domainId, solutionId1, null);
        SolutionCreateRequest solutionRequest2 = new SolutionCreateRequest(domainId, solutionId2, null);
        
        solutionService.createSolution(solutionRequest1);
        solutionService.createSolution(solutionRequest2);
        
        // Create random number of environments in solution 1 (1-5)
        int environmentsInSolution1 = 1 + random.nextInt(5);
        for (int i = 0; i < environmentsInSolution1; i++) {
            String applicationName = generateUniqueApplicationName();
            EnvironmentType type = generateRandomEnvironmentType();
            
            EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, null, solutionId1, null));

            environmentService.registerEnvironment(domainId, request);
        }
        
        // Create random number of environments in solution 2 (1-5)
        int environmentsInSolution2 = 1 + random.nextInt(5);
        for (int i = 0; i < environmentsInSolution2; i++) {
            String applicationName = generateUniqueApplicationName();
            EnvironmentType type = generateRandomEnvironmentType();
            
            EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, null, solutionId2, null));

            environmentService.registerEnvironment(domainId, request);
        }
        
        // Requirement 4.8: List environments filtered by solution_id
        List<SifEnvironmentResponse> environmentsFromSolution1 =
            environmentService.listEnvironmentsBySolution(domainId, solutionId1);

        List<SifEnvironmentResponse> environmentsFromSolution2 =
            environmentService.listEnvironmentsBySolution(domainId, solutionId2);
        
        // Verify correct number of environments returned for solution 1
        // +1 for the auto-created BROKER_SERVICES environment
        assertThat(environmentsFromSolution1)
            .as("Should return exactly the environments created in solution 1")
            .hasSize(environmentsInSolution1 + 1);
        
        // Verify all returned environments belong to solution 1
        assertThat(environmentsFromSolution1)
            .as("All returned environments should have solution_id matching solution 1")
            .allMatch(env -> solutionId1.equals(env.solutionId()));
        
        // Verify correct number of environments returned for solution 2
        // +1 for the auto-created BROKER_SERVICES environment
        assertThat(environmentsFromSolution2)
            .as("Should return exactly the environments created in solution 2")
            .hasSize(environmentsInSolution2 + 1);
        
        // Verify all returned environments belong to solution 2
        assertThat(environmentsFromSolution2)
            .as("All returned environments should have solution_id matching solution 2")
            .allMatch(env -> solutionId2.equals(env.solutionId()));
        
        // Verify no overlap between the two lists
        assertThat(environmentsFromSolution1)
            .as("Environments from solution 1 should not appear in solution 2 list")
            .doesNotContainAnyElementsOf(environmentsFromSolution2);
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
}
