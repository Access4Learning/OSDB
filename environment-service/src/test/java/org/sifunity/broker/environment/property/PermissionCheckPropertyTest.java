// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.service.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for permission check correctness.
 *
 * <p><strong>Feature: context-services-refactoring, Property 9: Permission check
 * correctness</strong></p>
 *
 * <p>For any (environmentId, serviceName, contextUuid, accessType) query, the
 * AclService SHALL return true if and only if an ACL entry exists with that
 * environment, a context_service matching (contextUuid, serviceName), that accessType,
 * and accessLevel = APPROVED.</p>
 *
 * <p><strong>Validates: Requirements 7.6</strong></p>
 */
class PermissionCheckPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;

    private DomainService domainService;
    private SolutionService solutionService;
    private ZoneService zoneService;
    private EnvironmentService environmentService;
    private AclService aclService;
    private DomainRepository domainRepository;

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
            "--spring.flyway.baseline-on-migrate=true"
        );
    }

    @AfterContainer
    static void stopContainer() {
        if (context != null) context.close();
        if (postgres != null) postgres.stop();
    }

    @BeforeTry
    void setUp() {
        domainService = context.getBean(DomainService.class);
        solutionService = context.getBean(SolutionService.class);
        zoneService = context.getBean(ZoneService.class);
        environmentService = context.getBean(EnvironmentService.class);
        aclService = context.getBean(AclService.class);
        domainRepository = context.getBean(DomainRepository.class);
        domainRepository.deleteAll();
    }

    /**
     * Property 9: Permission check correctness â€” APPROVED grants permission.
     *
     * <p><strong>Validates: Requirements 7.6</strong></p>
     */
    @Property(tries = 100)
    @Tag("Feature: context-services-refactoring, Property 9: Permission check correctness")
    void permissionCheckReturnsCorrectBoolean(
            @ForAll("serviceNames") String serviceName,
            @ForAll("nonProvideAccessTypes") AccessType accessType,
            @ForAll("accessLevels") AccessLevel accessLevel) {

        // Create hierarchy: domain â†’ solution â†’ zone â†’ context â†’ environment
        String domainId = "d-" + System.nanoTime();
        String solutionId = "s-" + System.nanoTime();
        String zoneId = "z-" + System.nanoTime();

        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain"));
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));

        // Get the auto-created DEFAULT context UUID
        List<ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);
        ContextDTO defaultContext = contexts.stream()
            .filter(c -> "DEFAULT".equals(c.contextId()))
            .findFirst()
            .orElseThrow();
        UUID contextUuid = defaultContext.id();

        // Create environment
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
            domainId, new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("PermApp" + System.nanoTime(), EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null)));
        UUID environmentId = envResponse.environment().id();

        // Before creating ACL, permission should be false
        boolean permBefore = aclService.hasPermission(environmentId, serviceName, contextUuid, accessType);
        assertThat(permBefore)
            .as("Permission must be false when no ACL entry exists")
            .isFalse();

        // Create ACL entry with the given accessLevel
        aclService.createAclEntry(new AclEntryCreateRequest(
            environmentId, serviceName, zoneId, defaultContext.contextId(), accessType, accessLevel));

        // Check permission after creating ACL
        boolean permAfter = aclService.hasPermission(environmentId, serviceName, contextUuid, accessType);

        if (accessLevel == AccessLevel.APPROVED) {
            assertThat(permAfter)
                .as("Permission must be true when ACL entry exists with APPROVED level")
                .isTrue();
        } else {
            assertThat(permAfter)
                .as("Permission must be false when ACL entry exists with %s level", accessLevel)
                .isFalse();
        }

        // Verify permission for a different service name is still false
        String otherServiceName = serviceName + "Other";
        boolean permOther = aclService.hasPermission(environmentId, otherServiceName, contextUuid, accessType);
        assertThat(permOther)
            .as("Permission must be false for a different service name")
            .isFalse();

        // Verify permission for a different environment is still false
        UUID otherEnvId = UUID.randomUUID();
        boolean permOtherEnv = aclService.hasPermission(otherEnvId, serviceName, contextUuid, accessType);
        assertThat(permOtherEnv)
            .as("Permission must be false for a different environment")
            .isFalse();
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> serviceNames() {
        Arbitrary<Character> firstChar = Arbitraries.chars().range('a', 'z').range('A', 'Z');
        Arbitrary<String> rest = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .ofMinLength(1)
            .ofMaxLength(30);
        return Combinators.combine(firstChar, rest).as((f, r) -> f + r);
    }

    @Provide
    Arbitrary<AccessType> nonProvideAccessTypes() {
        // Exclude PROVIDE to avoid single-provider constraint conflicts
        return Arbitraries.of(AccessType.QUERY, AccessType.SUBSCRIBE, AccessType.CREATE, AccessType.DELETE, AccessType.UPDATE);
    }

    @Provide
    Arbitrary<AccessLevel> accessLevels() {
        return Arbitraries.of(AccessLevel.values());
    }
}