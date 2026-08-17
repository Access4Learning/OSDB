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
import org.sifunity.broker.environment.service.EnvironmentService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for environment registration without solution using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates that environment registration without a solutionId is rejected
 * with an IllegalArgumentException, since solutionId is now required for all registrations.</p>
 * 
 * <p><strong>Validates: solutionId requirement enforcement</strong></p>
 * 
 * <p>This test runs 10 iterations with randomized input data to verify that
 * environments cannot be registered without a solution ID.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnvironmentRegistrationWithoutSolutionPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    private final Random random = new Random();
    
    /**
     * Property: Environment registration without solutionId is rejected.
     * 
     * <p><strong>Property:</strong> For any valid environment registration request without 
     * a solution_id, the registration should fail with IllegalArgumentException.</p>
     * 
     * <p>This test registers an environment without a solution ID and verifies that:
     * <ul>
     *   <li>The registration is rejected</li>
     *   <li>An IllegalArgumentException is thrown with appropriate message</li>
     * </ul>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-16")
    void environmentRegistrationWithoutSolutionIsRejected() {
        // Generate valid environment registration data
        String applicationName = generateUniqueApplicationName();
        EnvironmentType type = generateRandomEnvironmentType();
        String defaultZoneId = generateValidZoneId();
        // Any domainId; registration is rejected on the missing solutionId before the
        // domain is ever resolved, so no domain hierarchy is required for this test.
        String domainId = "reg-no-solution-domain-" + System.nanoTime();
        // Create environment registration request WITHOUT solution_id (null)
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(defaultZoneId), null, null));

        // Registration should be rejected because solutionId is now required
        assertThatThrownBy(() -> environmentService.registerEnvironment(domainId, request))
            .as("Registration without solutionId should be rejected")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("solutionId is required");
    }
    
    // ========== Test Data Generators ==========
    
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
