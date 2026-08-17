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
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.ContextServiceCreateRequest;
import org.sifunity.broker.environment.dto.ContextServiceDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
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
 * Integration test for the full provider registration flow without providerName.
 *
 * <p>Verifies that POST registration with contextServiceUuid creates a provider,
 * and GET returns a response with consumerName and no providerName field.</p>
 *
 * <p>Uses Testcontainers with real PostgreSQL for full-fidelity testing.</p>
 *
 * <p><strong>Validates: Requirements 2.3, 2.4, 2.5, 11.2</strong></p>
 */
@SpringBootTest
@Testcontainers
class ProviderRegistrationFlowIntegrationTest {

    private static final List<SupportedRequestType> IMMEDIATE_QUERY = List.of(
            new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private ProviderService providerService;

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
    private String applicationName;
    private UUID contextServiceUuid;

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
        // Create Domain → Solution → Zone → Context hierarchy
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        // Register an environment (consumerName = applicationName)
        applicationName = "FlowTestApp-" + System.currentTimeMillis();
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null)));
        environmentId = envResponse.environment().id();

        // Create a context_service for the test
        ContextServiceDTO contextService = contextServiceService.createContextService(hierarchy.getContextUuid(), "StudentPersonal", "OBJECT");
        contextServiceUuid = contextService.id();
    }

    /**
     * Full registration flow: POST with contextServiceUuid, then GET and verify
     * response has consumerName and no providerName.
     * Requirements: 2.3, 2.4, 2.5, 11.2
     */
    @Test
    void registerProvider_withContextServiceUuid_responseHasConsumerNameAndNoProviderName() {
        // POST: register provider with contextServiceUuid (no providerName in request)
        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://localhost:9090/students"),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY)));

        ProviderRegistrationDTO created = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(), request);

        // Verify POST response
        assertThat(created.id()).isNotNull();
        assertThat(created.environmentId()).isEqualTo(environmentId);
        assertThat(created.providerName()).isEqualTo(applicationName);
        assertThat(created.serviceName()).isEqualTo("StudentPersonal");
        assertThat(created.zoneId()).isEqualTo(hierarchy.getZoneId());
        assertThat(created.contextId()).isEqualTo(hierarchy.getContextId());
        assertThat(ProtocolDTO.locationOf(created.endPoint())).isEqualTo("http://localhost:9090/students");
        assertThat(created.serviceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(created.supportedRequestTypes().supportedRequestType()).isEqualTo(IMMEDIATE_QUERY);
        assertThat(created.registeredAt()).isNotNull();

        // GET: list provider registrations and verify same contract
        List<ProviderRegistrationDTO> registrations = providerService.listProviderRegistrations(environmentId);

        assertThat(registrations).hasSize(1);
        ProviderRegistrationDTO listed = registrations.get(0);
        assertThat(listed.id()).isEqualTo(created.id());
        assertThat(listed.providerName()).isEqualTo(applicationName);
        assertThat(listed.serviceName()).isEqualTo("StudentPersonal");
        assertThat(listed.zoneId()).isEqualTo(hierarchy.getZoneId());
        assertThat(listed.contextId()).isEqualTo(hierarchy.getContextId());
        assertThat(ProtocolDTO.locationOf(listed.endPoint())).isEqualTo("http://localhost:9090/students");
        assertThat(listed.serviceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(listed.supportedRequestTypes().supportedRequestType()).isEqualTo(IMMEDIATE_QUERY);
        assertThat(listed.registeredAt()).isNotNull();
    }

    /**
     * Verify that ProviderRegistrationDTO carries {@code providerName} and no longer carries
     * {@code consumerName}.
     *
     * <p>This assertion is inverted from its original form. It previously pinned
     * {@code contains("consumerName").doesNotContain("providerName")} — i.e. it asserted the exact
     * defect D1 fixes. {@code consumerName} is not part of the SIF 3.7 {@code provider} schema at all,
     * so emitting it was a violation regardless of its value; {@code providerName} is required. The
     * value is unchanged (it still derives from the environment's {@code consumerName} — see the
     * spec's DECIDED note); only the field name is corrected.</p>
     *
     * Requirements: 2.1, 2.5
     */
    @Test
    void providerRegistrationDTO_hasProviderName_notConsumerName() {
        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://localhost:9091/verify"),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY)));

        ProviderRegistrationDTO dto = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(), request);

        // providerName is present and carries the environment's consumer name
        assertThat(dto.providerName()).isNotNull();
        assertThat(dto.providerName()).isEqualTo(applicationName);

        // The schema field is providerName; consumerName must not appear on the provider at all
        assertThat(dto.getClass().getRecordComponents())
                .extracting("name")
                .contains("providerName")
                .doesNotContain("consumerName");
    }

    /**
     * Verify that existing registrations remain accessible after schema changes
     * (backward compatibility for routing and lookup).
     * Requirements: 11.2
     */
    @Test
    void existingRegistration_remainsAccessible_forLookup() {
        // Register provider
        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://localhost:9092/lookup-test"),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY)));

        ProviderRegistrationDTO created = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(), request);

        // Lookup by serviceName + zoneId + contextId should still work
        var found = providerService.lookupProvider(
            hierarchy.getSolutionUuid(), "StudentPersonal", hierarchy.getZoneId(), hierarchy.getContextId()
        );

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(created.id());
        assertThat(found.get().providerName()).isEqualTo(applicationName);
        assertThat(ProtocolDTO.locationOf(found.get().endPoint())).isEqualTo("http://localhost:9092/lookup-test");
    }
}

