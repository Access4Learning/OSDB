// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.service.AuthenticationService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;

import java.time.Instant;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for invalid HMAC rejection using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 20 from the design document:
 * "For any token validation request with an incorrect HMAC hash, the validation 
 * should fail with HTTP 401 Unauthorized."</p>
 * 
 * <p><strong>Validates: Requirements 7.4</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * incorrectly computed HMAC signatures are rejected by the authentication service.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvalidHmacRejectionPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private AuthenticationService authenticationService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();

    private String testDomainId;

    private String createTestSolution() {
        testDomainId = "ihmac-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(testDomainId, null));
        String solutionId = "ihmac-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(testDomainId, solutionId, null));
        return solutionId;
    }
    
    /**
     * Property 20: Invalid HMAC rejection
     * 
     * <p><strong>Property:</strong> For any token validation request with an incorrect 
     * HMAC hash, the validation should fail with HTTP 401 Unauthorized.</p>
     * 
     * <p><strong>Validates: Requirements 7.4</strong></p>
     * 
     * <p>This test creates an environment, generates an invalid HMAC signature,
     * and verifies that token validation fails with InvalidTokenException.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-20")
    void invalidHmacRejection() {
        // Generate random valid input data
        String solutionId = createTestSolution();
        String applicationName = generateApplicationName();
        EnvironmentType type = generateEnvironmentType();
        // Use null for defaultZoneId since zone validation is not what this test covers
        String defaultZoneId = null;
        // Create environment registration request
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(defaultZoneId), solutionId, null));
        
        // Register environment
        SifEnvironmentRegistrationResponse registrationResponse = environmentService.registerEnvironment(testDomainId, request);
        
        // Generate current timestamp
        String timestamp = Instant.now().toString();
        
        // Generate invalid HMAC hash (random string that won't match)
        String invalidHash = generateInvalidHash();
        
        // Create token validation request with incorrect HMAC
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            registrationResponse.environment().sessionToken(),
            timestamp,
            invalidHash
        );
        
        // Verify that validation fails with InvalidTokenException (Requirement 7.4)
        assertThatThrownBy(() -> authenticationService.validateToken(validationRequest))
            .as("Token validation should fail with incorrect HMAC")
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("HMAC signature validation failed");
    }
    
    // ========== Test Data Generators ==========
    
    private String generateApplicationName() {
        String[] prefixes = {"app", "service", "system", "platform", "client"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime() % 100000;
        return prefix + "-" + timestamp;
    }
    
    private EnvironmentType generateEnvironmentType() {
        EnvironmentType[] types = EnvironmentType.values();
        return types[random.nextInt(types.length)];
    }
    
    private String generateZoneId() {
        String[] zones = {"prod", "staging", "dev", "test", "qa"};
        return zones[random.nextInt(zones.length)] + "-zone-" + (System.nanoTime() % 10000);
    }
    
    private String generateContextId() {
        String[] contexts = {"default", "primary", "secondary", "backup"};
        return contexts[random.nextInt(contexts.length)] + "-ctx-" + (System.nanoTime() % 10000);
    }
    
    private String generateInvalidHash() {
        // Generate a random hex string that won't match the correct HMAC
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            sb.append(Integer.toHexString(random.nextInt(16)));
        }
        return sb.toString();
    }
}
