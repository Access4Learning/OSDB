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
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.AvailableProviderOptionDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.ContextServiceService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Integration test for the available-options endpoint logic.
 *
 * <p>Verifies that the set difference computation (PROVIDE ACLs minus existing
 * registrations) correctly returns only unregistered context_services.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.3</strong></p>
 */
@SpringBootTest
@Testcontainers
class AvailableProviderOptionsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private ProviderService providerService;

    @Autowired
    private AclService aclService;

    @Autowired
    private ContextServiceService contextServiceService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    private TestEntityHierarchy hierarchy;
    private UUID environmentId;

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
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AvailableOptionsApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null)));
        environmentId = envResponse.environment().id();
    }

    /**
     * Environment with PROVIDE/APPROVED ACLs and NO existing registrations
     * returns all ACL context services as available options.
     * Requirements: 4.1, 4.2
     */
    @Test
    void availableOptions_withProvideAclsAndNoRegistrations_returnsAllAclContextServices() {
        // Create PROVIDE/APPROVED ACLs for two services
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "StudentService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "TeacherService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));

        // Get available options
        List<AvailableProviderOptionDTO> options = providerService.getAvailableOptions(environmentId);

        // Should return both since no registrations exist
        assertThat(options).hasSize(2);
        assertThat(options).extracting(AvailableProviderOptionDTO::serviceName)
                .containsExactlyInAnyOrder("StudentService", "TeacherService");
        assertThat(options).extracting(AvailableProviderOptionDTO::zoneId)
                .containsOnly(hierarchy.getZoneId());
        assertThat(options).extracting(AvailableProviderOptionDTO::contextId)
                .containsOnly(hierarchy.getContextId());
        // Verify each option has a non-null contextServiceUuid
        assertThat(options).extracting(AvailableProviderOptionDTO::contextServiceUuid)
                .doesNotContainNull();
        // Verify serviceType is populated
        assertThat(options).extracting(AvailableProviderOptionDTO::serviceType)
                .doesNotContainNull();
    }

    /**
     * Environment with PROVIDE/APPROVED ACLs and SOME existing registrations
     * returns only the unregistered context_services (set difference correctness).
     * Requirements: 4.1, 4.2
     */
    @Test
    void availableOptions_withSomeRegistrations_returnsOnlyUnregistered() {
        // Create PROVIDE/APPROVED ACLs for three services
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "StudentService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "TeacherService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "GradeService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));

        // Register a provider for StudentService (resolves contextServiceUuid via ACL)
        UUID studentCsUuid = providerService.getAvailableOptions(environmentId).stream()
                .filter(o -> "StudentService".equals(o.serviceName()))
                .findFirst()
                .orElseThrow()
                .contextServiceUuid();

        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),
                new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                studentCsUuid,
                ProtocolDTO.of("http://localhost:9000/students"),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))))))));

        // Get available options — StudentService should be excluded
        List<AvailableProviderOptionDTO> options = providerService.getAvailableOptions(environmentId);

        assertThat(options).hasSize(2);
        assertThat(options).extracting(AvailableProviderOptionDTO::serviceName)
                .containsExactlyInAnyOrder("TeacherService", "GradeService");
        // StudentService should NOT appear
        assertThat(options).extracting(AvailableProviderOptionDTO::serviceName)
                .doesNotContain("StudentService");
    }

    /**
     * Environment with NO PROVIDE ACLs returns empty list.
     * Requirements: 4.3
     */
    @Test
    void availableOptions_withNoProvideAcls_returnsEmptyList() {
        // No ACLs created for the environment
        List<AvailableProviderOptionDTO> options = providerService.getAvailableOptions(environmentId);

        assertThat(options).isEmpty();
    }

    /**
     * Environment where all ACL entries already have registrations returns empty list.
     * Requirements: 4.1, 4.3
     */
    @Test
    void availableOptions_allAclsRegistered_returnsEmptyList() {
        // Create PROVIDE/APPROVED ACL for one service
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "OnlyService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));

        // Get the contextServiceUuid and register it
        UUID csUuid = providerService.getAvailableOptions(environmentId).stream()
                .findFirst()
                .orElseThrow()
                .contextServiceUuid();

        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),
                new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                csUuid,
                ProtocolDTO.of("http://localhost:9001/only"),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))))))));

        // All ACLs now have registrations
        List<AvailableProviderOptionDTO> options = providerService.getAvailableOptions(environmentId);

        assertThat(options).isEmpty();
    }

    /**
     * QUERY ACLs (non-PROVIDE) should NOT appear in available options.
     * Requirements: 4.1
     */
    @Test
    void availableOptions_withOnlyQueryAcls_returnsEmptyList() {
        // Create QUERY/APPROVED ACLs (not PROVIDE)
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "QueryOnlyService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.QUERY, AccessLevel.APPROVED));

        List<AvailableProviderOptionDTO> options = providerService.getAvailableOptions(environmentId);

        assertThat(options).isEmpty();
    }

    /**
     * PROVIDE ACLs with REJECTED level should NOT appear in available options.
     * Requirements: 4.1
     */
    @Test
    void availableOptions_withProvideRejectedAcls_returnsEmptyList() {
        // Create PROVIDE/REJECTED ACL (not APPROVED)
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "RejectedService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.REJECTED));

        List<AvailableProviderOptionDTO> options = providerService.getAvailableOptions(environmentId);

        assertThat(options).isEmpty();
    }
}
