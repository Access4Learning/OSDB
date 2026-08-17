// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.dto.TokenValidationResponse;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.AuthenticationService;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Token Validation Resolution.
 *
 * <p><strong>Feature: global-admin-bootstrap, Property 4: Token Validation Resolution</strong></p>
 *
 * <p>For any environment with a valid solutionId, the
 * {@code AuthenticationService.validateToken()} method SHALL resolve and return
 * both {@code domainId} and {@code solutionId} in the {@code TokenValidationResponse}.
 * There is no special-case skip based on environment type.</p>
 *
 * <p><strong>Validates: Requirements 3.4</strong></p>
 */
class TokenValidationResolutionPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;
    private AuthenticationService authenticationService;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;
    private EnvironmentRepository environmentRepository;

    @BeforeContainer
    static void startContainer() {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
        postgres.start();

        SpringApplication app = new SpringApplication(EnvironmentServiceApplication.class);
        app.setAdditionalProfiles("test");
        context = app.run(
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword(),
            "--spring.datasource.driver-class-name=org.postgresql.Driver",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
            "--spring.flyway.enabled=true",
            "--spring.flyway.locations=classpath:db/migration",
            "--spring.flyway.baseline-on-migrate=true",
            "--sif.broker.bootstrap.enabled=true",
            "--server.port=0"
        );
    }

    @AfterContainer
    static void stopContainer() {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeTry
    void setUp() {
        authenticationService = context.getBean(AuthenticationService.class);
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);
        environmentRepository = context.getBean(EnvironmentRepository.class);

        // Clean up test environments (but not the bootstrap admin)
        environmentRepository.findAll().stream()
            .filter(e -> e.getApplicationName() != null && e.getApplicationName().startsWith("test-app-"))
            .forEach(e -> environmentRepository.delete(e));
        // Clean up test solutions and domains
        solutionRepository.findAll().stream()
            .filter(s -> s.getSolutionId() != null && s.getSolutionId().startsWith("test-sol-"))
            .forEach(s -> solutionRepository.delete(s));
        domainRepository.findAll().stream()
            .filter(d -> d.getDomainId() != null && d.getDomainId().startsWith("test-dom-"))
            .forEach(d -> domainRepository.delete(d));
    }

    /**
     * Property 4: Token Validation Resolution
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     *
     * <p>For any environment with a valid solutionId (backed by a real solution
     * in the database), validateToken resolves both domainId and solutionId
     * in the response. This holds for all environment types (DIRECT, BROKERED).</p>
     */
    @Property(tries = 10)
    @Tag("global-admin-bootstrap-property-4")
    void validateTokenResolvesDomainAndSolutionForAllEnvironments(
            @ForAll("environmentTypes") EnvironmentType envType,
            @ForAll("uniqueSuffixes") String suffix) {

        String domainId = "test-dom-" + suffix;
        String solutionId = "test-sol-" + suffix;
        String appName = "test-app-" + suffix;
        String sessionToken = UUID.randomUUID().toString();
        String sharedSecret = "dGVzdC1zZWNyZXQtMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkw";

        // Create domain
        Domain domain = new Domain();
        domain.setDomainId(domainId);
        domain.setDescription("Test domain " + suffix);
        domainRepository.save(domain);

        // Create solution under that domain
        Solution solution = new Solution();
        solution.setDomainUuid(domain.getId());
        solution.setSolutionId(solutionId);
        solution.setDescription("Test solution " + suffix);
        solutionRepository.save(solution);

        // Create environment with the given type and solutionUuid
        Environment env = Environment.builder()
            .applicationName(appName)
            .type(envType)
            .solutionUuid(solution.getId())
            .sessionToken(sessionToken)
            .sharedSecret(sharedSecret)
            .defaultZoneId("default")
            .build();
        environmentRepository.save(env);

        // Build a valid HMAC request
        String timestamp = Instant.now().toString();
        String hmac = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
        TokenValidationRequest request = new TokenValidationRequest(sessionToken, timestamp, hmac);

        // Validate token
        TokenValidationResponse response = authenticationService.validateToken(request);

        // Assert both domainId and solutionId are resolved
        assertThat(response.solutionId())
            .as("solutionId must be resolved for environment type %s", envType)
            .isEqualTo(solutionId);
        assertThat(response.domainId())
            .as("domainId must be resolved for environment type %s", envType)
            .isEqualTo(domainId);
        assertThat(response.environmentId()).isNotNull();
        assertThat(response.type()).isEqualTo(envType);

        // Cleanup this iteration's data
        environmentRepository.delete(env);
        solutionRepository.delete(solution);
        domainRepository.delete(domain);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(EnvironmentType.DIRECT, EnvironmentType.BROKERED);
    }

    @Provide
    Arbitrary<String> uniqueSuffixes() {
        return Arbitraries.integers().between(1, 100_000)
            .map(i -> String.valueOf(i) + "-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
