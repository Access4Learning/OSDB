// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.service.AuthenticationService;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for non-existent token rejection using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 21 from the design document:
 * "For any token validation request with a sessionToken that does not exist, 
 * the validation should fail with HTTP 401 Unauthorized."</p>
 * 
 * <p><strong>Validates: Requirements 7.5</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * non-existent sessionTokens are rejected by the authentication service.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NonExistentTokenRejectionPropertyTest {
    
    @Autowired
    private AuthenticationService authenticationService;
    
    private final Random random = new Random();
    
    /**
     * Property 21: Non-existent token rejection
     * 
     * <p><strong>Property:</strong> For any token validation request with a sessionToken 
     * that does not exist, the validation should fail with HTTP 401 Unauthorized.</p>
     * 
     * <p><strong>Validates: Requirements 7.5</strong></p>
     * 
     * <p>This test generates a random non-existent sessionToken and verifies that 
     * token validation fails with InvalidTokenException.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-21")
    void nonExistentTokenRejection() {
        // Generate a random non-existent sessionToken (UUID that doesn't exist in database)
        String nonExistentSessionToken = UUID.randomUUID().toString();
        
        // Generate current timestamp
        String timestamp = Instant.now().toString();
        
        // Generate a random HMAC hash (doesn't matter since token doesn't exist)
        String hash = SifAuthenticationUtils.computeHmac(
            nonExistentSessionToken,
            timestamp,
            "random-secret-" + random.nextInt(10000)
        );
        
        // Create token validation request with non-existent sessionToken
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            nonExistentSessionToken,
            timestamp,
            hash
        );
        
        // Verify that validation fails with InvalidTokenException (Requirement 7.5)
        assertThatThrownBy(() -> authenticationService.validateToken(validationRequest))
            .as("Token validation should fail with non-existent sessionToken")
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("SessionToken does not exist");
    }
}
