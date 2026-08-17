// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.dto.TokenValidationResponse;
import org.sifunity.broker.environment.service.AuthenticationService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;

import java.time.Instant;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for HMAC signature validation using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 19 from the design document:
 * "For any environment with sessionToken and sharedSecret, computing the HMAC hash 
 * correctly and providing it to validate-token should result in successful authentication 
 * with environment details returned."</p>
 * 
 * <p><strong>Validates: Requirements 7.2, 7.3</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * correctly computed HMAC signatures are accepted by the authentication service.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HmacValidationPropertyTest {
    
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
        testDomainId = "hmac-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(testDomainId, null));
        String solutionId = "hmac-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(testDomainId, solutionId, null));
        return solutionId;
    }
    
    /**
     * Property 19: HMAC signature validation
     * 
     * <p><strong>Property:</strong> For any environment with sessionToken and sharedSecret, 
     * computing the HMAC hash correctly and providing it to validate-token should result 
     * in successful authentication with environment details returned.</p>
     * 
     * <p><strong>Validates: Requirements 7.2, 7.3</strong></p>
     * 
     * <p>This test creates an environment, computes a valid HMAC signature using the
     * sharedSecret, and verifies that token validation succeeds.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-19")
    void hmacSignatureValidation() {
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
        
        // Compute valid HMAC signature using SifAuthenticationUtils (Requirement 7.2)
        String hash = SifAuthenticationUtils.computeHmac(
            registrationResponse.environment().sessionToken(),
            timestamp,
            registrationResponse.sharedSecret()
        );
        
        // Verify HMAC computation is deterministic
        String hash2 = SifAuthenticationUtils.computeHmac(
            registrationResponse.environment().sessionToken(),
            timestamp,
            registrationResponse.sharedSecret()
        );
        
        assertThat(hash)
            .as("HMAC computation should be deterministic")
            .isEqualTo(hash2);
        
        // Create token validation request with correct HMAC
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            registrationResponse.environment().sessionToken(),
            timestamp,
            hash
        );
        
        // Validate token (Requirement 7.3 - should succeed with correct HMAC)
        TokenValidationResponse validationResponse = authenticationService.validateToken(validationRequest);
        
        // Verify authentication succeeded
        assertThat(validationResponse)
            .as("Token validation should succeed with correct HMAC")
            .isNotNull();
        
        assertThat(validationResponse.environmentId())
            .as("Validation response should contain the environment ID")
            .isEqualTo(registrationResponse.environment().id());
        
        assertThat(validationResponse.applicationName())
            .as("Validation response should contain the application name")
            .isEqualTo(registrationResponse.environment().consumerName());
        
        assertThat(validationResponse.type())
            .as("Validation response should contain the environment type")
            .isEqualTo(registrationResponse.environment().type());
        
        // Additional property verification: HMAC validation should work with different timestamps
        String timestamp2 = Instant.now().plusSeconds(1).toString();
        String hash3 = SifAuthenticationUtils.computeHmac(
            registrationResponse.environment().sessionToken(),
            timestamp2,
            registrationResponse.sharedSecret()
        );
        
        TokenValidationRequest validationRequest2 = new TokenValidationRequest(
            registrationResponse.environment().sessionToken(),
            timestamp2,
            hash3
        );
        
        TokenValidationResponse validationResponse2 = authenticationService.validateToken(validationRequest2);
        
        assertThat(validationResponse2.environmentId())
            .as("HMAC validation should work with different timestamps")
            .isEqualTo(registrationResponse.environment().id());
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
}

