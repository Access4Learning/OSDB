// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for unauthorized environment selection rejection.
 *
 * <p><strong>Feature: admin-user-management, Property 11: Unauthorized environment
 * selection rejected</strong></p>
 *
 * <p>For any authenticated admin user and any environment ID that is NOT in their
 * granted access list, attempting to select that environment should be rejected
 * with HTTP 403.</p>
 *
 * <p><strong>Validates: Requirements 4.4</strong></p>
 */
class UnauthorizedEnvironmentSelectionPropertyTest {

    static PostgreSQLContainer<?> postgres;
    @SuppressWarnings("resource")
    static GenericContainer<?> redis;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private EnvironmentService environmentService;
    private AdminUserService adminUserService;
    private UserEnvironmentAccessService accessService;
    private UserAuthenticationService authService;

    @BeforeContainer
    static void startContainers() {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
        postgres.start();

        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
        redis.start();

        SpringApplication app = new SpringApplication(EnvironmentServiceApplication.class);
        // Use "redis-integration" profile to avoid loading TestRedisConfiguration
        // (which is @Profile({"test", "disable-security"}) and provides a mock StringRedisTemplate)
        app.setAdditionalProfiles("redis-integration");
        context = app.run(
            "--server.port=0",
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword(),
            "--spring.datasource.driver-class-name=org.postgresql.Driver",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
            "--spring.flyway.enabled=true",
            "--spring.flyway.locations=classpath:db/migration",
            "--spring.flyway.baseline-on-migrate=true",
            "--spring.main.allow-bean-definition-overriding=true",
            "--spring.data.redis.host=" + redis.getHost(),
            "--spring.data.redis.port=" + redis.getMappedPort(6379),
            // Disable observability and management to avoid conflicts
            "--observability.enabled=false",
            "--management.endpoints.enabled-by-default=false",
            // Secrets backend for tests
            "--secrets.backend.type=plaintext",
            // Bootstrap config
            "--sif.broker.bootstrap.enabled=true",
            "--sif.broker.bootstrap.admin.sessionToken=test-admin-token",
            "--sif.broker.bootstrap.admin.sharedSecret=dGVzdC1hZG1pbi1zZWNyZXQ=",
            "--sif.broker.infrastructure.gateway-base-url=http://localhost:8080",
            "--sif.broker.infrastructure.environment=http://localhost:8081",
            "--sif.broker.infrastructure.provision-requests=http://localhost:8081",
            "--sif.broker.infrastructure.requests-connector=http://localhost:8083",
            "--sif.broker.infrastructure.events-connector=http://localhost:8084",
            "--sif.broker.infrastructure.queues=http://localhost:8085",
            "--sif.broker.infrastructure.subscriptions=http://localhost:8086",
            "--sif.broker.infrastructure.services-connector=http://localhost:8087",
            "--sif.broker.infrastructure.responses-connector=http://localhost:8088",
            "--authentication.timestamp-tolerance-minutes=5",
            "--spring.security.user.name=test",
            "--spring.security.user.password=test",
            "--spring.jackson.default-property-inclusion=always"
        );
    }

    @AfterContainer
    static void stopContainers() {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @BeforeTry
    void setUp() {
        domainService = context.getBean(DomainService.class);
        solutionService = context.getBean(SolutionService.class);
        zoneService = context.getBean(ZoneService.class);
        environmentService = context.getBean(EnvironmentService.class);
        adminUserService = context.getBean(AdminUserService.class);
        accessService = context.getBean(UserEnvironmentAccessService.class);
        authService = context.getBean(UserAuthenticationService.class);
    }

    /**
     * Property 11: Unauthorized environment selection rejected.
     *
     * <p><strong>Validates: Requirements 4.4</strong></p>
     *
     * <p>For each try:</p>
     * <ol>
     *   <li>Create a user with access to environment A</li>
     *   <li>Create environment B (not in user's access list)</li>
     *   <li>Login successfully</li>
     *   <li>Attempt to select environment B → verify SifBrokerException with 403 status</li>
     * </ol>
     */
    @Property(tries = 10)
    @Tag("Feature: admin-user-management, Property 11: Unauthorized environment selection rejected")
    void selectingUnauthorizedEnvironmentReturns403(
            @ForAll("validUsername") String username,
            @ForAll("validPassword") String password) {

        // 1. Create hierarchy: domain → solution
        String domainId = "unauth-env-domain-" + System.nanoTime();
        DomainDTO domain = domainService.createDomain(
            new DomainCreateRequest(domainId, "Test domain for unauthorized env selection"));

        String solutionId = "unauth-env-solution-" + System.nanoTime();
        SolutionDTO solution = solutionService.createSolution(
            new SolutionCreateRequest(domainId, solutionId, "Test solution"));

        // 2. Create environment A (user will have access to this)
        String zoneIdA = "zone-a-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneIdA, "Zone A for auth test"));
        String appNameA = "authorized-app-" + System.nanoTime();
        SifEnvironmentRegistrationResponse envResponseA = environmentService.registerEnvironment(
            domainId,
            new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appNameA, EnvironmentType.DIRECT, DefaultZoneDTO.of(zoneIdA), solutionId, null)));
        UUID environmentIdA = envResponseA.environment().id();

        // 3. Create environment B (user will NOT have access to this)
        String zoneIdB = "zone-b-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneIdB, "Zone B for auth test"));
        String appNameB = "unauthorized-app-" + System.nanoTime();
        SifEnvironmentRegistrationResponse envResponseB = environmentService.registerEnvironment(
            domainId,
            new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appNameB, EnvironmentType.DIRECT, DefaultZoneDTO.of(zoneIdB), solutionId, null)));
        UUID environmentIdB = envResponseB.environment().id();

        // 4. Create admin user
        AdminUserDTO adminUser = adminUserService.createUser(
            new AdminUserCreateRequest(username, password, domain.domainId(), solution.solutionId()));

        // 5. Grant access ONLY to environment A
        accessService.grantAccess(adminUser.id(), new UserEnvironmentAccessRequest(environmentIdA));

        // 6. Login successfully
        UserLoginResponse loginResponse = authService.login(
            new UserLoginRequest(username, password, domain.domainId(), solution.solutionId()));
        String sessionToken = loginResponse.sessionToken();

        assertThat(sessionToken).isNotNull().isNotBlank();

        // 7. Attempt to select environment B (unauthorized) → expect 403
        assertThatThrownBy(() -> authService.selectEnvironment(sessionToken, environmentIdB))
            .as("Selecting an environment not in user's access list should throw SifBrokerException with 403")
            .isInstanceOf(SifBrokerException.class)
            .satisfies(thrown -> {
                SifBrokerException ex = (SifBrokerException) thrown;
                assertThat(ex.getHttpStatus().value())
                    .as("HTTP status should be 403 Forbidden")
                    .isEqualTo(403);
                assertThat(ex.getMessage())
                    .as("Error message should indicate unauthorized access")
                    .isEqualTo("Not authorized to access this environment");
            });

        // 8. Verify selecting environment A (authorized) succeeds
        authService.selectEnvironment(sessionToken, environmentIdA);
        // No exception means success
    }

    // ========== Arbitraries ==========

    /**
     * Generates valid usernames matching ^[a-zA-Z0-9_-]{3,128}$.
     * Uses unique suffix to avoid collisions across tries.
     */
    @Provide
    Arbitrary<String> validUsername() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(3)
            .ofMaxLength(12)
            .map(base -> base + "-" + System.nanoTime());
    }

    /**
     * Generates valid passwords (8-72 chars).
     */
    @Provide
    Arbitrary<String> validPassword() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .ofMinLength(8)
            .ofMaxLength(20);
    }
}
