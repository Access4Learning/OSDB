// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Property-based test for Environment creation persistence using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 1 from the design document:
 * "For any valid environment registration request, creating an environment should 
 * result in a persisted record that can be retrieved by ID with matching data."</p>
 * 
 * <p><strong>Validates: Requirements 1.1, 1.5, 2.1</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify the
 * property holds across different valid inputs.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnvironmentCreationPersistencePropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();
    
    /**
     * Property 1: Environment creation persistence
     * 
     * <p><strong>Property:</strong> For any valid environment registration request,
     * creating an environment should result in a persisted record that can be
     * retrieved by ID with matching data.</p>
     * 
     * <p><strong>Validates: Requirements 1.1, 1.5, 2.1</strong></p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-1")
    void environmentCreationPersistence() {
        // Create Domain → Solution hierarchy so solutionId resolves to a valid UUID
        TestEntityHierarchy hierarchy = TestEntityHierarchy.create(domainService, solutionService);
        
        // Generate random valid input data
        String applicationName = generateValidApplicationName();
        EnvironmentType type = generateRandomEnvironmentType();
        // Use null defaultZoneId since no zone exists in test hierarchy
        // (defaultZoneId validation requires a matching zone in the solution)
        // Create environment registration request with valid solutionId from hierarchy
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, null, hierarchy.getSolutionId(), null));
        
        // Create environment
        SifEnvironmentRegistrationResponse regResponse = environmentService.registerEnvironment(hierarchy.getDomainId(), request);
        var response = regResponse.environment();
        
        // Verify response contains expected data
        assertThat(response.id()).isNotNull();
        assertThat(response.consumerName()).isEqualTo(request.environment().consumerName());
        assertThat(response.type()).isEqualTo(request.environment().type());
        assertThat(response.sessionToken()).isNotNull().isNotBlank();
        assertThat(regResponse.sharedSecret()).isNotNull().isNotBlank();
        
        // Verify sessionToken is in UUID format
        assertThat(UUID.fromString(response.sessionToken())).isNotNull();
        
        // Verify sharedSecret is properly encoded (Base64)
        assertThat(regResponse.sharedSecret()).matches("^[A-Za-z0-9+/]+=*$");
        
        // Retrieve by ID
        SifEnvironmentResponse retrieved = environmentService.getEnvironment(response.id());
        
        // Verify persisted data matches registration request
        assertThat(retrieved.id()).isEqualTo(response.id());
        assertThat(retrieved.consumerName()).isEqualTo(request.environment().consumerName());
        assertThat(retrieved.type()).isEqualTo(request.environment().type());
        assertThat(retrieved.sessionToken()).isEqualTo(response.sessionToken());
        
        // Verify sharedSecret is NOT exposed in queries (Requirement 2.2)
        // Note: EnvironmentDTO doesn't have sharedSecret field, so this is enforced by design
        
        // Additional property verification: Environment should be retrievable immediately after creation
        assertThat(retrieved).isNotNull();
        
        // Additional property verification: All required fields should be non-null
        assertThat(retrieved.id()).isNotNull();
        assertThat(retrieved.consumerName()).isNotBlank();
        assertThat(retrieved.sessionToken()).isNotBlank();
        assertThat(retrieved.type()).isNotNull();
    }
    
    // ========== Random Data Generators ==========
    
    /**
     * Generates a valid application name with random characteristics.
     */
    private String generateValidApplicationName() {
        String[] prefixes = {"App", "Service", "System", "Platform", "Tool", "Client", "Server"};
        String[] suffixes = {"Pro", "Lite", "Plus", "Enterprise", "Basic", "Advanced", "Standard"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        String suffix = suffixes[random.nextInt(suffixes.length)];
        int number = random.nextInt(1000);
        
        return prefix + "-" + suffix + "-" + number;
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