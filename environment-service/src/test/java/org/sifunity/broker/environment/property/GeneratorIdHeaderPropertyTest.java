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
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for generatorId header set to username.
 *
 * <p><strong>Feature: admin-user-management, Property 12: generatorId header set to
 * username</strong></p>
 *
 * <p>For any admin user with username U and an active session with a selected environment,
 * the session context should contain the correct username that would be used as the
 * generatorId header value on all forwarded requests.</p>
 *
 * <p>Since the backend HMAC proxy is not yet implemented, this test verifies at the
 * service/session level that the AdminSessionContext.username() matches the authenticated
 * admin user's username after login and environment selection.</p>
 *
 * <p><strong>Validates: Requirements 5.2, 5.3</strong></p>
 */
class GeneratorIdHeaderPropertyTest {

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
            "--observability.enabled=false",
            "--management.endpoints.enabled-by-default=false",
            "--secrets.backend.type=plaintext",
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
     * Property 12: generatorId header set to username.
     *
     * <p><strong>Validates: Requirements 5.2, 5.3</strong></p>
     *
     * <p>For each try:</p>
     * <ol>
     *   <li>Create a user with a known username</li>
     *   <li>Login and select an environment</li>
     *   <li>Validate the session context contains the correct username</li>
     *   <li>Verify the username matches what would be set as the generatorId header</li>
     * </ol>
     */
    @Property(tries = 10)
    @Tag("Feature: admin-user-management, Property 12: generatorId header set to username")
    void sessionContextUsernameMatchesGeneratorIdHeader(
            @ForAll("validUsername") String username,
            @ForAll("validPassword") String password) {

        // 1. Create hierarchy: domain → solution → environment
        String domainId = "genid-domain-" + System.nanoTime();
        DomainDTO domain = domainService.createDomain(
            new DomainCreateRequest(domainId, "Test domain for generatorId property"));

        String solutionId = "genid-solution-" + System.nanoTime();
        SolutionDTO solution = solutionService.createSolution(
            new SolutionCreateRequest(domainId, solutionId, "Test solution"));

        String appName = "genid-app-" + System.nanoTime();
        String zoneId = "genid-zone-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Zone for generatorId test"));
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
            domainId,
            new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.DIRECT, DefaultZoneDTO.of(zoneId), solutionId, null)));
        UUID environmentId = envResponse.environment().id();

        // 2. Create admin user with the known username
        AdminUserDTO adminUser = adminUserService.createUser(
            new AdminUserCreateRequest(username, password, domain.domainId(), solution.solutionId()));

        // 3. Grant access to the environment
        accessService.grantAccess(adminUser.id(), new UserEnvironmentAccessRequest(environmentId));

        // 4. Login
        UserLoginResponse loginResponse = authService.login(
            new UserLoginRequest(username, password, domain.domainId(), solution.solutionId()));
        String sessionToken = loginResponse.sessionToken();
        assertThat(sessionToken).isNotNull().isNotBlank();

        // 5. Select environment
        authService.selectEnvironment(sessionToken, environmentId);

        // 6. Validate session context after environment selection
        AdminSessionContext sessionContext = authService.validateSession(sessionToken);

        // 7. Verify the session context username matches the original username
        //    This is the value that would be set as the generatorId header
        assertThat(sessionContext.username())
            .as("AdminSessionContext.username() should equal the authenticated user's username, "
                + "which is the value used as the generatorId header on forwarded requests")
            .isEqualTo(username);

        // 8. Verify the selected environment is stored in the session
        assertThat(sessionContext.selectedEnvironmentId())
            .as("Session should have the selected environment stored")
            .isEqualTo(environmentId);

        // 9. Verify the username is non-null and within the generatorId max length (256 chars per spec)
        assertThat(sessionContext.username())
            .as("generatorId value (username) must be non-null and within 256 chars")
            .isNotNull()
            .hasSizeLessThanOrEqualTo(256);

        // 10. Verify the username is the SIF 3.7 generatorId (not consumerName or sourceName)
        //     The generatorId identifies the human operator per SIF 3.7 spec.
        //     We verify the session context provides exactly the username for this purpose.
        assertThat(sessionContext.username())
            .as("The generatorId header value must be the admin user's username, not any other identifier")
            .isEqualTo(adminUser.username());
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
