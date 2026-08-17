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
import org.sifunity.broker.common.util.SifAuthenticationUtils;
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
 * Property-based test for timestamp tolerance validation using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 22 from the design document:
 * "For any token validation request with a timestamp more than 5 minutes in the 
 * past or future, the validation should fail with HTTP 401 Unauthorized."</p>
 * 
 * <p><strong>Validates: Requirements 7.6</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * timestamps outside the 5-minute tolerance window are rejected by the authentication service.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TimestampTolerancePropertyTest {
    
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
        String domainId = "ts-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        String solutionId = "ts-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        this.testDomainId = domainId;
        return solutionId;
    }
    
    /**
     * Property 22: Timestamp tolerance validation
     * 
     * <p><strong>Property:</strong> For any token validation request with a timestamp 
     * more than 5 minutes in the past or future, the validation should fail with 
     * HTTP 401 Unauthorized.</p>
     * 
     * <p><strong>Validates: Requirements 7.6</strong></p>
     * 
     * <p>This test creates an environment, generates a timestamp outside the 5-minute 
     * tolerance window, and verifies that token validation fails with InvalidTimestampException.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-22")
    void timestampToleranceValidation() {
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
        
        // Generate timestamp outside 5-minute tolerance window
        // Randomly choose past or future, and add 7-15 minutes offset
        // Using 7+ minutes to ensure we're always outside the 5-minute window
        // even accounting for test execution time and minute truncation
        boolean isPast = random.nextBoolean();
        int minutesOffset = 7 + random.nextInt(9); // 7-15 minutes
        
        Instant invalidTimestamp;
        if (isPast) {
            invalidTimestamp = Instant.now().minusSeconds(minutesOffset * 60L);
        } else {
            invalidTimestamp = Instant.now().plusSeconds(minutesOffset * 60L);
        }
        
        String timestamp = invalidTimestamp.toString();
        
        // Compute HMAC with the invalid timestamp
        String hash = SifAuthenticationUtils.computeHmac(
            registrationResponse.environment().sessionToken(),
            timestamp,
            registrationResponse.sharedSecret()
        );
        
        // Create token validation request with timestamp outside tolerance window
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            registrationResponse.environment().sessionToken(),
            timestamp,
            hash
        );
        
        // Verify that validation fails with InvalidTokenException (Requirement 7.6)
        assertThatThrownBy(() -> authenticationService.validateToken(validationRequest))
            .as("Token validation should fail with timestamp outside 5-minute tolerance window")
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Timestamp is outside tolerance window");
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
