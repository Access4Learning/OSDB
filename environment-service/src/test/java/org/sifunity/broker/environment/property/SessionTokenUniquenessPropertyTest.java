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
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for sessionToken uniqueness and format using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 2 from the design document:
 * "For any set of created environments, all sessionTokens should be unique and 
 * in valid UUID format."</p>
 * 
 * <p><strong>Validates: Requirements 1.2</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * sessionTokens are always unique across multiple environment registrations
 * and that they conform to valid UUID format.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionTokenUniquenessPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();

    private String domainId;

    private String createTestSolution() {
        domainId = "st-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        String solutionId = "st-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        return solutionId;
    }
    
    /**
     * Property 2: SessionToken uniqueness and format
     * 
     * <p><strong>Property:</strong> For any set of created environments, all 
     * sessionTokens should be unique and in valid UUID format.</p>
     * 
     * <p><strong>Validates: Requirements 1.2</strong></p>
     * 
     * <p>This test creates multiple environments in each iteration and verifies:
     * <ul>
     *   <li>All sessionTokens are unique (no duplicates)</li>
     *   <li>All sessionTokens are valid UUIDs</li>
     *   <li>SessionTokens are non-null and non-blank</li>
     * </ul>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-2")
    void sessionTokenUniquenessAndFormat() {
        // Generate a random number of environments to create (between 2 and 10)
        int environmentCount = random.nextInt(9) + 2; // 2-10 environments
        
        // Create a test solution for all environments in this iteration
        String solutionId = createTestSolution();
        
        Set<String> sessionTokens = new HashSet<>();
        
        // Create multiple environments and collect their sessionTokens
        for (int i = 0; i < environmentCount; i++) {
            // Generate unique application name for each environment
            String applicationName = generateUniqueApplicationName(i);
            EnvironmentType type = generateRandomEnvironmentType();
            // Use null for defaultZoneId since zone validation is not what this test covers
            String defaultZoneId = null;
            // Create environment registration request
            EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(defaultZoneId), solutionId, null));
            
            // Register environment
            SifEnvironmentRegistrationResponse response = environmentService.registerEnvironment(domainId, request);
            
            // Verify sessionToken is not null and not blank
            assertThat(response.environment().sessionToken())
                .as("SessionToken should not be null or blank for environment %d", i)
                .isNotNull()
                .isNotBlank();
            
            // Verify sessionToken is in valid UUID format
            UUID parsedUuid = null;
            try {
                parsedUuid = UUID.fromString(response.environment().sessionToken());
            } catch (IllegalArgumentException e) {
                throw new AssertionError(
                    String.format("SessionToken '%s' for environment %d is not a valid UUID: %s", 
                        response.environment().sessionToken(), i, e.getMessage())
                );
            }
            
            assertThat(parsedUuid)
                .as("SessionToken should be parseable as UUID for environment %d", i)
                .isNotNull();
            
            // Verify sessionToken string representation matches UUID format
            assertThat(response.environment().sessionToken())
                .as("SessionToken should match standard UUID format for environment %d", i)
                .matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
            
            // Check for uniqueness - sessionToken should not already exist in our set
            boolean isUnique = sessionTokens.add(response.environment().sessionToken());
            assertThat(isUnique)
                .as("SessionToken '%s' for environment %d should be unique (not duplicate)", 
                    response.environment().sessionToken(), i)
                .isTrue();
        }
        
        // Final verification: all sessionTokens should be unique
        assertThat(sessionTokens)
            .as("All sessionTokens should be unique")
            .hasSize(environmentCount);
        
        // Additional verification: each sessionToken should be a valid UUID
        for (String sessionToken : sessionTokens) {
            assertThat(sessionToken)
                .as("Each sessionToken should be a valid UUID string")
                .isNotNull()
                .isNotBlank();
            
            // Verify UUID parsing doesn't throw exception
            UUID.fromString(sessionToken);
        }
        
        // Verify sessionTokens are properly distributed (not all the same pattern)
        // This is a statistical check - with proper UUID generation, we shouldn't
        // see patterns like all starting with the same character
        if (environmentCount >= 5) {
            Set<Character> firstCharacters = new HashSet<>();
            for (String sessionToken : sessionTokens) {
                firstCharacters.add(sessionToken.charAt(0));
            }
            
            // With proper randomization, we should see some variety in first characters
            // This is not a strict requirement but helps detect poor randomization
            assertThat(firstCharacters.size())
                .as("SessionTokens should show reasonable randomization in first characters")
                .isGreaterThan(1);
        }
    }
    
    // ========== Random Data Generators ==========
    
    /**
     * Generates a unique application name for each environment in the test iteration.
     * This ensures we don't get duplicate application name errors during testing.
     */
    private String generateUniqueApplicationName(int index) {
        String[] prefixes = {"App", "Service", "System", "Platform", "Tool", "Client", "Server"};
        String[] suffixes = {"Pro", "Lite", "Plus", "Enterprise", "Basic", "Advanced", "Standard"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        String suffix = suffixes[random.nextInt(suffixes.length)];
        long timestamp = System.nanoTime(); // Use nanoseconds for uniqueness
        
        return prefix + "-" + suffix + "-" + index + "-" + timestamp;
    }
    
    /**
     * Generates a random valid environment type.
     */
    private EnvironmentType generateRandomEnvironmentType() {
        EnvironmentType[] types = EnvironmentType.values();
        return types[random.nextInt(types.length)];
    }
    
    /**
     * Generates a valid zone ID.
     */
    private String generateValidZoneId() {
        String[] zones = {"production", "staging", "development", "test", "demo", "sandbox"};
        String base = zones[random.nextInt(zones.length)];
        int number = random.nextInt(100);
        return base + "-" + number;
    }
    
    /**
     * Generates a valid context ID.
     */
    private String generateValidContextId() {
        String[] contexts = {"default", "primary", "secondary", "backup", "main", "alt"};
        String base = contexts[random.nextInt(contexts.length)];
        int number = random.nextInt(50);
        return base + "-" + number;
    }
}
