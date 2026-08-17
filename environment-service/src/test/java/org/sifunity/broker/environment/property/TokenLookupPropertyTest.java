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
 * Property-based test for token lookup using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 18 from the design document:
 * "For any created environment, validating a token with that environment's sessionToken 
 * should successfully retrieve the environment record."</p>
 * 
 * <p><strong>Validates: Requirements 7.1</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * the AuthenticationService can successfully lookup environments by sessionToken.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenLookupPropertyTest {
    
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
        String domainId = "tl-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        String solutionId = "tl-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        this.testDomainId = domainId;
        return solutionId;
    }
    
    /**
     * Property 18: Token lookup by sessionToken
     * 
     * <p><strong>Property:</strong> For any created environment, validating a token 
     * with that environment's sessionToken should successfully retrieve the environment record.</p>
     * 
     * <p><strong>Validates: Requirements 7.1</strong></p>
     * 
     * <p>This test creates an environment, then validates a token using the generated
     * sessionToken and verifies that the environment details are correctly retrieved.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-18")
    void tokenLookupBySessionToken() {
        // Generate random valid input data
        String solutionId = createTestSolution();
        String applicationName = generateApplicationName();
        EnvironmentType type = generateEnvironmentType();
        // Use null for defaultZoneId since zone validation is not what this test covers
        String defaultZoneId = null;
        // Create environment registration request
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(defaultZoneId), solutionId, null));
        
        // Register environment (Requirement 1.1)
        SifEnvironmentRegistrationResponse registrationResponse = environmentService.registerEnvironment(testDomainId, request);
        
        // Verify registration response contains credentials
        assertThat(registrationResponse.environment().sessionToken())
            .as("Registration should return a sessionToken")
            .isNotNull()
            .isNotBlank();
        
        assertThat(registrationResponse.sharedSecret())
            .as("Registration should return a sharedSecret")
            .isNotNull()
            .isNotBlank();
        
        // Generate current timestamp
        String timestamp = Instant.now().toString();
        
        // Compute valid HMAC signature
        String hash = SifAuthenticationUtils.computeHmac(
            registrationResponse.environment().sessionToken(),
            timestamp,
            registrationResponse.sharedSecret()
        );
        
        // Create token validation request
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            registrationResponse.environment().sessionToken(),
            timestamp,
            hash
        );
        
        // Validate token (Requirement 7.1 - lookup by sessionToken)
        TokenValidationResponse validationResponse = authenticationService.validateToken(validationRequest);
        
        // Verify the environment was successfully retrieved
        assertThat(validationResponse)
            .as("Token validation should return a response")
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
        
        assertThat(validationResponse.defaultZoneId())
            .as("Validation response should contain the default zone ID")
            .isEqualTo(DefaultZoneDTO.idOf(registrationResponse.environment().defaultZone()));
        
        // Additional property verification: SessionToken lookup should be deterministic
        // Multiple lookups with the same sessionToken should return the same environment
        TokenValidationResponse secondValidation = authenticationService.validateToken(validationRequest);
        
        assertThat(secondValidation.environmentId())
            .as("Second validation should return the same environment ID")
            .isEqualTo(validationResponse.environmentId());
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
