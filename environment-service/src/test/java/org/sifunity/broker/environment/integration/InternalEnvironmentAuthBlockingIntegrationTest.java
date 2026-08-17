// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.dto.TokenValidationResponse;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.AuthenticationService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Integration test for AuthenticationService handling of internal vs. external environments.
 *
 * <p>Validates that the environment-service's AuthenticationService:</p>
 * <ul>
 *   <li>Rejects environments whose sharedSecret is the placeholder "not-used"
 *       (never provisioned for external auth)</li>
 *   <li>Authenticates normal environments with a valid HMAC</li>
 *   <li>Authenticates broker-services (solution-admin) environments with a valid HMAC —
 *       broker-services is the per-solution solution-admin credential and DOES authenticate
 *       externally (it is bootstrapped with a real shared secret, not "not-used")</li>
 * </ul>
 *
 * <p>Uses Testcontainers with PostgreSQL for a production-like test environment.</p>
 */
@SpringBootTest
@Testcontainers
@DisplayName("Internal Environment Auth Blocking Integration Test")
class InternalEnvironmentAuthBlockingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private TestEntityHierarchy hierarchy;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("secrets.backend.type", () -> "plaintext");
    }

    @BeforeAll
    static void beforeAll() {
        assertThat(postgres.isRunning()).isTrue();
    }

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.create(domainService, solutionService);
    }

    // ========== Test: Broker-services environment authenticates (solution admin) ==========

    @Test
    @DisplayName("Authenticate as broker-services environment with valid HMAC → succeeds (solution admin)")
    void authenticateAsBrokerServicesEnvironment_succeeds() {
        // The SolutionService auto-creates a BROKER_SERVICES environment with fingerprint
        // "broker-services" and a REAL shared secret for each solution. It is the per-solution
        // solution-admin credential and MUST be able to authenticate externally.
        Environment brokerServicesEnv = environmentRepository
                .findByFingerprintAndSolutionUuid("broker-services", hierarchy.getSolutionUuid())
                .orElseThrow(() -> new AssertionError("broker-services environment should exist"));

        String sessionToken = brokerServicesEnv.getSessionToken();
        String sharedSecret = environmentService.getSharedSecret(brokerServicesEnv.getId());
        String timestamp = Instant.now().toString();
        String validHash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        TokenValidationRequest request = new TokenValidationRequest(sessionToken, timestamp, validHash);

        // Act: broker-services authenticates like any other environment
        TokenValidationResponse response = authenticationService.validateToken(request);

        // Assert: authentication succeeds and the fingerprint is surfaced for solution-admin detection
        assertThat(response).isNotNull();
        assertThat(response.environmentId()).isEqualTo(brokerServicesEnv.getId());
        assertThat(response.fingerprint()).isEqualTo("broker-services");
    }

    // ========== Test: Environment with sharedSecret "not-used" blocked ==========

    @Test
    @DisplayName("Authenticate as environment with sharedSecret 'not-used' → 401 rejected")
    void authenticateAsEnvironmentWithNotUsedSecret_rejected() {
        // Create a custom environment with sharedSecret "not-used" but different fingerprint
        Environment customEnv = Environment.builder()
                .applicationName("custom-internal-" + System.nanoTime())
                .sessionToken("custom-internal-token-" + UUID.randomUUID())
                .sharedSecret("not-used")
                .fingerprint("custom-internal-fp")
                .solutionUuid(hierarchy.getSolutionUuid())
                .type(EnvironmentType.BROKERED)
                .createdAt(Instant.now())
                .build();
        customEnv = environmentRepository.save(customEnv);

        String sessionToken = customEnv.getSessionToken();
        String timestamp = Instant.now().toString();
        String fakeHash = "any-hash-value-doesnt-matter";

        TokenValidationRequest request = new TokenValidationRequest(sessionToken, timestamp, fakeHash);

        // Act & Assert: should throw InvalidTokenException because sharedSecret is "not-used"
        assertThatThrownBy(() -> authenticationService.validateToken(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Internal environment cannot authenticate externally");
    }

    // ========== Test: Normal environment authentication succeeds ==========

    @Test
    @DisplayName("Authenticate as normal environment → succeeds")
    void authenticateAsNormalEnvironment_succeeds() {
        // Register a normal environment (gets a real sharedSecret)
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("NormalApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));

        SifEnvironmentRegistrationResponse registrationResponse =
                environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);

        String sessionToken = registrationResponse.environment().sessionToken();
        String sharedSecret = registrationResponse.sharedSecret();

        // Compute valid HMAC
        String timestamp = Instant.now().toString();
        String validHash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        TokenValidationRequest request = new TokenValidationRequest(sessionToken, timestamp, validHash);

        // Act
        TokenValidationResponse response = authenticationService.validateToken(request);

        // Assert: authentication should succeed
        assertThat(response).isNotNull();
        assertThat(response.environmentId()).isEqualTo(registrationResponse.environment().id());
        assertThat(response.applicationName()).isEqualTo(registrationRequest.environment().consumerName());
    }
}
