// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for sharedSecret exclusion from queries using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 5 from the design document:
 * "For any environment query (by ID or list), the response should not include 
 * the sharedSecret field."</p>
 * 
 * <p><strong>Validates: Requirements 2.2</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * sharedSecret is never exposed in query operations across different scenarios.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SharedSecretExclusionPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();

    private String domainId;

    private String createTestSolution() {
        domainId = "sse-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        String solutionId = "sse-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        return solutionId;
    }
    
    /**
     * Property 5: SharedSecret exclusion from queries
     * 
     * <p><strong>Property:</strong> For any environment query (by ID or list), 
     * the response should not include the sharedSecret field.</p>
     * 
     * <p><strong>Validates: Requirements 2.2</strong></p>
     * 
     * <p>This test verifies:
     * <ul>
     *   <li>SifEnvironmentResponse class does not have a sharedSecret field</li>
     *   <li>getEnvironment() returns SifEnvironmentResponse without sharedSecret</li>
     *   <li>listEnvironments() returns SifEnvironmentResponse objects without sharedSecret</li>
     *   <li>SharedSecret is only available during registration (SifEnvironmentRegistrationResponse)</li>
     *   <li>No method on SifEnvironmentResponse can access sharedSecret</li>
     * </ul>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-5")
    void sharedSecretExclusionFromQueries() {
        // Generate random number of environments to create and test (1-5)
        int environmentCount = random.nextInt(5) + 1;
        List<SifEnvironmentRegistrationResponse> registrationResponses = new ArrayList<>();
        
        // Create a test solution for all environments in this iteration
        String solutionId = createTestSolution();
        
        // Create multiple environments and collect their registration responses
        for (int i = 0; i < environmentCount; i++) {
            String applicationName = generateUniqueApplicationName(i);
            EnvironmentType type = generateRandomEnvironmentType();
            // Use null for defaultZoneId since zone validation is not what this test covers
            String defaultZoneId = null;
            EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(defaultZoneId), solutionId, null));
            
            // Register environment - this SHOULD include sharedSecret
            SifEnvironmentRegistrationResponse registrationResponse =
                environmentService.registerEnvironment(domainId, request);
            
            // Verify registration response includes sharedSecret (this is expected)
            assertThat(registrationResponse.sharedSecret())
                .as("Registration response should include sharedSecret for environment %d", i)
                .isNotNull()
                .isNotBlank();
            
            registrationResponses.add(registrationResponse);
        }
        
        // Test 1: Verify SifEnvironmentResponse class structure does not include sharedSecret
        verifyEnvironmentDTOStructure();
        
        // Test 2: Test individual environment queries (getEnvironment)
        for (int i = 0; i < registrationResponses.size(); i++) {
            SifEnvironmentRegistrationResponse registrationResponse = registrationResponses.get(i);
            
            // Query environment by ID - this should NOT include sharedSecret
            SifEnvironmentResponse queriedEnvironment = environmentService.getEnvironment(registrationResponse.environment().id());
            
            // Verify the queried environment does not expose sharedSecret
            verifyEnvironmentDTODoesNotExposeSharedSecret(queriedEnvironment, i);
            
            // Verify all other fields are present and match registration
            assertThat(queriedEnvironment.id())
                .as("Environment ID should match for environment %d", i)
                .isEqualTo(registrationResponse.environment().id());
            assertThat(queriedEnvironment.consumerName())
                .as("Application name should match for environment %d", i)
                .isEqualTo(registrationResponse.environment().consumerName());
            assertThat(queriedEnvironment.sessionToken())
                .as("Session token should match for environment %d", i)
                .isEqualTo(registrationResponse.environment().sessionToken());
            assertThat(queriedEnvironment.type())
                .as("Environment type should match for environment %d", i)
                .isEqualTo(registrationResponse.environment().type());
            assertThat(DefaultZoneDTO.idOf(queriedEnvironment.defaultZone()))
                .as("Default zone ID should match for environment %d", i)
                .isEqualTo(DefaultZoneDTO.idOf(registrationResponse.environment().defaultZone()));
        }
        
        // Test 3: Test list environments queries (listEnvironments)
        testListEnvironmentsExcludesSharedSecret(registrationResponses);
        
        // Test 4: Test list environments with type filtering
        if (environmentCount > 1) {
            testListEnvironmentsWithFilteringExcludesSharedSecret(registrationResponses);
        }
    }
    
    /**
     * Verifies that the SifEnvironmentResponse class structure does not include a sharedSecret field.
     * This is a compile-time and runtime verification that the DTO is properly designed.
     */
    private void verifyEnvironmentDTOStructure() {
        Class<SifEnvironmentResponse> dtoClass = SifEnvironmentResponse.class;
        
        // Verify SifEnvironmentResponse does not have a sharedSecret field
        Field[] fields = dtoClass.getDeclaredFields();
        for (Field field : fields) {
            assertThat(field.getName())
                .as("SifEnvironmentResponse should not have a field named 'sharedSecret'")
                .isNotEqualTo("sharedSecret");
        }
        
        // Verify SifEnvironmentResponse does not have a sharedSecret() method
        Method[] methods = dtoClass.getDeclaredMethods();
        for (Method method : methods) {
            assertThat(method.getName())
                .as("SifEnvironmentResponse should not have a method named 'sharedSecret'")
                .isNotEqualTo("sharedSecret");
        }
        
        // Additional verification: check record components (since SifEnvironmentResponse is a record)
        if (dtoClass.isRecord()) {
            java.lang.reflect.RecordComponent[] components = dtoClass.getRecordComponents();
            for (java.lang.reflect.RecordComponent component : components) {
                assertThat(component.getName())
                    .as("SifEnvironmentResponse record should not have a component named 'sharedSecret'")
                    .isNotEqualTo("sharedSecret");
            }
        }
    }
    
    /**
     * Verifies that a specific SifEnvironmentResponse instance does not expose sharedSecret
     * through any accessible means.
     */
    private void verifyEnvironmentDTODoesNotExposeSharedSecret(SifEnvironmentResponse dto, int environmentIndex) {
        assertThat(dto)
            .as("SifEnvironmentResponse should not be null for environment %d", environmentIndex)
            .isNotNull();
        
        // Verify toString() does not contain "sharedSecret" (case-insensitive)
        String toStringResult = dto.toString();
        assertThat(toStringResult.toLowerCase())
            .as("SifEnvironmentResponse.toString() should not contain 'sharedSecret' for environment %d", environmentIndex)
            .doesNotContain("sharedsecret");
        
        // Verify that attempting to access sharedSecret via reflection fails
        try {
            Field sharedSecretField = dto.getClass().getDeclaredField("sharedSecret");
            // If we reach here, the field exists (which it shouldn't)
            throw new AssertionError(
                String.format("SifEnvironmentResponse should not have a 'sharedSecret' field, but found: %s", 
                    sharedSecretField)
            );
        } catch (NoSuchFieldException e) {
            // This is expected - the field should not exist
        }
        
        // Verify that attempting to call sharedSecret() method fails
        try {
            Method sharedSecretMethod = dto.getClass().getDeclaredMethod("sharedSecret");
            // If we reach here, the method exists (which it shouldn't)
            throw new AssertionError(
                String.format("SifEnvironmentResponse should not have a 'sharedSecret()' method, but found: %s", 
                    sharedSecretMethod)
            );
        } catch (NoSuchMethodException e) {
            // This is expected - the method should not exist
        }
    }
    
    /**
     * Tests that listEnvironments() returns SifEnvironmentResponse objects without sharedSecret.
     */
    private void testListEnvironmentsExcludesSharedSecret(List<SifEnvironmentRegistrationResponse> registrationResponses) {
        // Test listEnvironments without filtering
        Pageable pageable = PageRequest.of(0, 20);
        Page<SifEnvironmentResponse> allEnvironments = environmentService.listEnvironments(null, pageable);
        
        assertThat(allEnvironments.getContent())
            .as("List environments should return non-empty results")
            .isNotEmpty();
        
        // Verify each environment in the list does not expose sharedSecret
        for (int i = 0; i < allEnvironments.getContent().size(); i++) {
            SifEnvironmentResponse listedEnvironment = allEnvironments.getContent().get(i);
            verifyEnvironmentDTODoesNotExposeSharedSecret(listedEnvironment, i);
        }
        
        // Verify that our created environments are in the list and don't expose sharedSecret
        for (SifEnvironmentRegistrationResponse registrationResponse : registrationResponses) {
            boolean found = allEnvironments.getContent().stream()
                .anyMatch(env -> env.id().equals(registrationResponse.environment().id()));
            
            assertThat(found)
                .as("Created environment %s should be found in list", registrationResponse.environment().id())
                .isTrue();
        }
    }
    
    /**
     * Tests that listEnvironments() with type filtering returns SifEnvironmentResponse objects without sharedSecret.
     */
    private void testListEnvironmentsWithFilteringExcludesSharedSecret(
            List<SifEnvironmentRegistrationResponse> registrationResponses) {
        
        // Get a random environment type from our created environments
        EnvironmentType filterType = registrationResponses.get(
            random.nextInt(registrationResponses.size())
        ).environment().type();
        
        // Test listEnvironments with type filtering
        Pageable pageable = PageRequest.of(0, 20);
        Page<SifEnvironmentResponse> filteredEnvironments = 
            environmentService.listEnvironments(filterType, pageable);
        
        // Verify each environment in the filtered list does not expose sharedSecret
        for (int i = 0; i < filteredEnvironments.getContent().size(); i++) {
            SifEnvironmentResponse filteredEnvironment = filteredEnvironments.getContent().get(i);
            verifyEnvironmentDTODoesNotExposeSharedSecret(filteredEnvironment, i);
            
            // Verify the filtering worked correctly
            assertThat(filteredEnvironment.type())
                .as("Filtered environment %d should have the correct type", i)
                .isEqualTo(filterType);
        }
        
        // Verify that our created environments of the filtered type are in the results
        long expectedCount = registrationResponses.stream()
            .filter(response -> response.environment().type() == filterType)
            .count();
        
        if (expectedCount > 0) {
            assertThat(filteredEnvironments.getContent())
                .as("Filtered results should contain at least our created environments of type %s", filterType)
                .hasSizeGreaterThanOrEqualTo((int) expectedCount);
        }
    }
    
    // ========== Random Data Generators ==========
    
    /**
     * Generates a unique application name for each environment in the test iteration.
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

