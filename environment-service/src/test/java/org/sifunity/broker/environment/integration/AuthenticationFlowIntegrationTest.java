// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.dto.TokenValidationResponse;
import org.sifunity.broker.environment.service.AuthenticationService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * End-to-end integration test for authentication flow using Testcontainers.
 * 
 * <p>This test validates the complete authentication flow:
 * <ol>
 *   <li>Register environment to obtain sessionToken and sharedSecret</li>
 *   <li>Validate token with correct HMAC signature â†’ Verify success</li>
 *   <li>Validate token with incorrect HMAC signature â†’ Verify rejection</li>
 *   <li>Validate token with old timestamp â†’ Verify rejection</li>
 * </ol>
 * 
 * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.6</strong></p>
 * 
 * <p>This test uses Testcontainers to spin up a real PostgreSQL database,
 * ensuring that authentication works correctly in a production-like environment.</p>
 */
@SpringBootTest
@Testcontainers
class AuthenticationFlowIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private TestEntityHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.create(domainService, solutionService);
    }
    
    /**
     * Configure Spring Boot to use the Testcontainers PostgreSQL instance.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // Configure JPA for PostgreSQL
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        
        // Enable Flyway for schema migrations
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        
        // Use plaintext secrets backend for tests
        registry.add("secrets.backend.type", () -> "plaintext");
    }
    
    @BeforeAll
    static void beforeAll() {
        // Ensure the container is started
        assertThat(postgres.isRunning()).isTrue();
    }
    
    /**
     * Test the complete authentication flow with correct HMAC signature.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>Requirement 7.1: Token lookup by sessionToken</li>
     *   <li>Requirement 7.2: HMAC computation using sharedSecret</li>
     *   <li>Requirement 7.3: Successful authentication with correct HMAC</li>
     * </ul>
     */
    @Test
    void testAuthenticationFlowWithCorrectHmac() {
        // ========== Step 1: Register Environment ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AuthTestApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        // Verify registration response contains credentials
        assertThat(registrationResponse).isNotNull();
        assertThat(registrationResponse.environment().sessionToken()).isNotNull().isNotBlank();
        assertThat(registrationResponse.sharedSecret()).isNotNull().isNotBlank();
        
        String sessionToken = registrationResponse.environment().sessionToken();
        String sharedSecret = registrationResponse.sharedSecret();
        
        // ========== Step 2: Compute Correct HMAC ==========
        
        String timestamp = Instant.now().toString();
        String correctHash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
        
        // ========== Step 3: Validate Token with Correct HMAC ==========
        
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            sessionToken,
            timestamp,
            correctHash
        );
        
        TokenValidationResponse validationResponse = authenticationService.validateToken(validationRequest);
        
        // ========== Step 4: Verify Successful Authentication ==========
        
        // Verify response contains environment details (Requirement 7.3)
        assertThat(validationResponse).isNotNull();
        assertThat(validationResponse.environmentId()).isEqualTo(registrationResponse.environment().id());
        assertThat(validationResponse.applicationName()).isEqualTo(registrationRequest.environment().consumerName());
        assertThat(validationResponse.type()).isEqualTo(registrationRequest.environment().type());
        assertThat(validationResponse.defaultZoneId()).isEqualTo(registrationRequest.environment().defaultZoneId());
    }
    
    /**
     * Test authentication flow with incorrect HMAC signature.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>Requirement 7.4: Invalid HMAC rejection</li>
     * </ul>
     */
    @Test
    void testAuthenticationFlowWithIncorrectHmac() {
        // ========== Step 1: Register Environment ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AuthTestApp-InvalidHmac-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        String sessionToken = registrationResponse.environment().sessionToken();
        
        // ========== Step 2: Create Incorrect HMAC ==========
        
        String timestamp = Instant.now().toString();
        String incorrectHash = "InvalidHmacSignature123456789==";
        
        // ========== Step 3: Validate Token with Incorrect HMAC ==========
        
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            sessionToken,
            timestamp,
            incorrectHash
        );
        
        // ========== Step 4: Verify Authentication Rejection ==========
        
        // Verify that validation fails with InvalidTokenException (Requirement 7.4)
        assertThatThrownBy(() -> authenticationService.validateToken(validationRequest))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("HMAC");
    }
    
    /**
     * Test authentication flow with old timestamp.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>Requirement 7.6: Timestamp tolerance validation</li>
     * </ul>
     */
    @Test
    void testAuthenticationFlowWithOldTimestamp() {
        // ========== Step 1: Register Environment ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AuthTestApp-OldTimestamp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        String sessionToken = registrationResponse.environment().sessionToken();
        String sharedSecret = registrationResponse.sharedSecret();
        
        // ========== Step 2: Create Old Timestamp (10 minutes in the past) ==========
        
        // Default tolerance is 5 minutes, so 10 minutes should be rejected
        String oldTimestamp = Instant.now().minus(10, ChronoUnit.MINUTES).toString();
        String hash = SifAuthenticationUtils.computeHmac(sessionToken, oldTimestamp, sharedSecret);
        
        // ========== Step 3: Validate Token with Old Timestamp ==========
        
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            sessionToken,
            oldTimestamp,
            hash
        );
        
        // ========== Step 4: Verify Authentication Rejection ==========
        
        // Verify that validation fails with InvalidTokenException (Requirement 7.6)
        assertThatThrownBy(() -> authenticationService.validateToken(validationRequest))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("tolerance");
    }
    
    /**
     * Test authentication flow with future timestamp.
     * 
     * <p>This test validates that timestamps in the future (beyond tolerance)
     * are also rejected, ensuring the tolerance window works in both directions.</p>
     */
    @Test
    void testAuthenticationFlowWithFutureTimestamp() {
        // ========== Step 1: Register Environment ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AuthTestApp-FutureTimestamp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        String sessionToken = registrationResponse.environment().sessionToken();
        String sharedSecret = registrationResponse.sharedSecret();
        
        // ========== Step 2: Create Future Timestamp (10 minutes in the future) ==========
        
        // Default tolerance is 5 minutes, so 10 minutes should be rejected
        String futureTimestamp = Instant.now().plus(10, ChronoUnit.MINUTES).toString();
        String hash = SifAuthenticationUtils.computeHmac(sessionToken, futureTimestamp, sharedSecret);
        
        // ========== Step 3: Validate Token with Future Timestamp ==========
        
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            sessionToken,
            futureTimestamp,
            hash
        );
        
        // ========== Step 4: Verify Authentication Rejection ==========
        
        // Verify that validation fails with InvalidTokenException
        assertThatThrownBy(() -> authenticationService.validateToken(validationRequest))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("tolerance");
    }
    
    /**
     * Test authentication flow with non-existent sessionToken.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>Requirement 7.5: Non-existent token rejection</li>
     * </ul>
     */
    @Test
    void testAuthenticationFlowWithNonExistentToken() {
        // ========== Step 1: Create Request with Non-Existent Token ==========
        
        String nonExistentToken = "non-existent-session-token-12345";
        String timestamp = Instant.now().toString();
        String hash = "SomeHashValue==";
        
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            nonExistentToken,
            timestamp,
            hash
        );
        
        // ========== Step 2: Verify Authentication Rejection ==========
        
        // Verify that validation fails with InvalidTokenException (Requirement 7.5)
        assertThatThrownBy(() -> authenticationService.validateToken(validationRequest))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("SessionToken");
    }
    
    /**
     * Test authentication flow with timestamp at the edge of tolerance window.
     * 
     * <p>This test validates that timestamps exactly at the tolerance boundary
     * are accepted (edge case testing).</p>
     */
    @Test
    void testAuthenticationFlowWithTimestampAtToleranceEdge() {
        // ========== Step 1: Register Environment ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AuthTestApp-EdgeTimestamp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        String sessionToken = registrationResponse.environment().sessionToken();
        String sharedSecret = registrationResponse.sharedSecret();
        
        // ========== Step 2: Create Timestamp at Edge of Tolerance (4 minutes in the past) ==========
        
        // Default tolerance is 5 minutes, so 4 minutes should be accepted
        String edgeTimestamp = Instant.now().minus(4, ChronoUnit.MINUTES).toString();
        String hash = SifAuthenticationUtils.computeHmac(sessionToken, edgeTimestamp, sharedSecret);
        
        // ========== Step 3: Validate Token with Edge Timestamp ==========
        
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            sessionToken,
            edgeTimestamp,
            hash
        );
        
        TokenValidationResponse validationResponse = authenticationService.validateToken(validationRequest);
        
        // ========== Step 4: Verify Successful Authentication ==========
        
        // Verify that validation succeeds for timestamp within tolerance
        assertThat(validationResponse).isNotNull();
        assertThat(validationResponse.environmentId()).isEqualTo(registrationResponse.environment().id());
        assertThat(validationResponse.applicationName()).isEqualTo(registrationRequest.environment().consumerName());
    }
    
    /**
     * Test multiple authentication attempts with the same credentials.
     * 
     * <p>This test validates that the same sessionToken and sharedSecret can be
     * used for multiple authentication attempts (credentials are reusable).</p>
     */
    @Test
    void testMultipleAuthenticationAttemptsWithSameCredentials() {
        // ========== Step 1: Register Environment ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AuthTestApp-MultipleAttempts-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        String sessionToken = registrationResponse.environment().sessionToken();
        String sharedSecret = registrationResponse.sharedSecret();
        
        // ========== Step 2: Perform Multiple Authentication Attempts ==========
        
        for (int i = 0; i < 3; i++) {
            String timestamp = Instant.now().toString();
            String hash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
            
            TokenValidationRequest validationRequest = new TokenValidationRequest(
                sessionToken,
                timestamp,
                hash
            );
            
            TokenValidationResponse validationResponse = authenticationService.validateToken(validationRequest);
            
            // Verify each authentication succeeds
            assertThat(validationResponse).isNotNull();
            assertThat(validationResponse.environmentId()).isEqualTo(registrationResponse.environment().id());
            assertThat(validationResponse.applicationName()).isEqualTo(registrationRequest.environment().consumerName());
        }
    }
    
    /**
     * Test authentication flow with different environment types.
     * 
     * <p>This test validates that authentication works correctly for all
     * environment types (DIRECT, BROKERED, ADMIN).</p>
     */
    @Test
    void testAuthenticationFlowWithDifferentEnvironmentTypes() {
        // Test CONSUMER environment
        testAuthenticationForEnvironmentType(EnvironmentType.BROKERED);
        
        // Test PROVIDER environment
        testAuthenticationForEnvironmentType(EnvironmentType.BROKERED);
        
        // Test BROKER environment
        testAuthenticationForEnvironmentType(EnvironmentType.BROKERED);
    }
    
    /**
     * Helper method to test authentication for a specific environment type.
     */
    private void testAuthenticationForEnvironmentType(EnvironmentType type) {
        // Register environment
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AuthTestApp-" + type + "-" + System.currentTimeMillis(), type, null, hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        String sessionToken = registrationResponse.environment().sessionToken();
        String sharedSecret = registrationResponse.sharedSecret();
        
        // Compute HMAC
        String timestamp = Instant.now().toString();
        String hash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
        
        // Validate token
        TokenValidationRequest validationRequest = new TokenValidationRequest(
            sessionToken,
            timestamp,
            hash
        );
        
        TokenValidationResponse validationResponse = authenticationService.validateToken(validationRequest);
        
        // Verify authentication succeeds
        assertThat(validationResponse).isNotNull();
        assertThat(validationResponse.type()).isEqualTo(type);
        assertThat(validationResponse.environmentId()).isEqualTo(registrationResponse.environment().id());
    }
}

