// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.dto.EnvironmentSummaryDTO;
import org.sifunity.broker.environment.dto.UserLoginRequest;
import org.sifunity.broker.environment.dto.UserLoginResponse;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.UserAuthenticationService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for authentication returning the correct environment list.
 *
 * <p><strong>Feature: admin-user-management, Property 7: Authentication returns
 * correct environment list</strong></p>
 *
 * <p>For any admin user with a set of granted environment associations E,
 * successful authentication should return a list of environments that is exactly
 * the set E (same IDs, no extras, no missing).</p>
 *
 * <p><strong>Validates: Requirements 3.3</strong></p>
 */
class AuthenticationEnvironmentListPropertyTest {

    static PostgreSQLContainer<?> postgres;
    @SuppressWarnings("resource")
    static GenericContainer<?> redis;
    static ConfigurableApplicationContext context;

    private UserAuthenticationService authService;
    private AdminUserRepository adminUserRepository;
    private UserEnvironmentAccessRepository accessRepository;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;
    private EnvironmentRepository environmentRepository;

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
        // Do NOT use "test" profile to avoid TestRedisConfiguration mock overriding real Redis
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
            "--spring.data.redis.host=" + redis.getHost(),
            "--spring.data.redis.port=" + redis.getMappedPort(6379),
            "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
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
            "--observability.enabled=false",
            "--observability.metrics.enabled=false",
            "--observability.tracing.enabled=false"
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
        authService = context.getBean(UserAuthenticationService.class);
        adminUserRepository = context.getBean(AdminUserRepository.class);
        accessRepository = context.getBean(UserEnvironmentAccessRepository.class);
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);
        environmentRepository = context.getBean(EnvironmentRepository.class);

        // Clean up between tries
        accessRepository.deleteAll();
        adminUserRepository.deleteAll();
        environmentRepository.deleteAll();
    }

    /**
     * Successful authentication returns exactly the set of granted environment
     * associations — same IDs, no extras, no missing.
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: admin-user-management, Property 7: Authentication returns correct environment list")
    void authenticationReturnsExactlyGrantedEnvironments(
            @ForAll("environmentCount") int numEnvironments) {

        String password = "TestPassword123!";
        String username = "authuser-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Create supporting hierarchy: domain + solution
        Domain domain = new Domain();
        domain.setDomainId("auth-test-" + UUID.randomUUID().toString().substring(0, 8));
        domain.setDescription("Test domain for auth env list");
        domain = domainRepository.save(domain);

        Solution solution = new Solution();
        solution.setDomainUuid(domain.getId());
        solution.setSolutionId("sol-" + UUID.randomUUID().toString().substring(0, 8));
        solution.setDescription("Test solution");
        solution = solutionRepository.save(solution);

        // 2. Create admin user with bcrypt-hashed password
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(username);
        adminUser.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt(10)));
        adminUser.setDomain(domain);
        adminUser.setSolution(solution);
        adminUser = adminUserRepository.save(adminUser);

        // 3. Create N environments and associate them with the user
        Set<UUID> expectedEnvironmentIds = new HashSet<>();
        for (int i = 0; i < numEnvironments; i++) {
            Environment env = new Environment();
            env.setApplicationName("app-" + UUID.randomUUID().toString().substring(0, 8));
            env.setSessionToken(UUID.randomUUID().toString());
            env.setSharedSecret("PLACEHOLDER");
            env.setType(EnvironmentType.DIRECT);
            env.setSolutionUuid(solution.getId());
            env.setCreatedAt(Instant.now());
            env = environmentRepository.save(env);

            expectedEnvironmentIds.add(env.getId());

            UserEnvironmentAccess access = new UserEnvironmentAccess();
            access.setUser(adminUser);
            access.setEnvironment(env);
            accessRepository.save(access);
        }

        // 4. Login with correct credentials
        UserLoginRequest loginRequest = new UserLoginRequest(
                username, password, domain.getDomainId(), solution.getSolutionId());
        UserLoginResponse response = authService.login(loginRequest);

        // 5. Verify the returned environment list matches exactly the granted set
        assertThat(response).isNotNull();
        assertThat(response.sessionToken()).isNotNull().isNotBlank();

        List<EnvironmentSummaryDTO> returnedEnvironments = response.environments();
        Set<UUID> returnedIds = returnedEnvironments.stream()
                .map(EnvironmentSummaryDTO::id)
                .collect(Collectors.toSet());

        assertThat(returnedIds)
            .as("Returned environment IDs must exactly match the granted set (no extras, no missing)")
            .isEqualTo(expectedEnvironmentIds);

        assertThat(returnedEnvironments)
            .as("Returned environment count must equal granted count")
            .hasSize(numEnvironments);
    }

    // ========== Arbitraries ==========

    /**
     * Generates environment count between 1 and 5 (inclusive) per task spec.
     */
    @Provide
    Arbitrary<Integer> environmentCount() {
        return Arbitraries.integers().between(1, 5);
    }
}
