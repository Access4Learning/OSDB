// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;

/**
 * Integration test for backward compatibility after V41 migration drops provider_name column.
 *
 * <p>Verifies that:</p>
 * <ul>
 *   <li>V41 migration applied successfully</li>
 *   <li>provider_registry table no longer has provider_name column but retains all other columns</li>
 *   <li>Existing registrations remain accessible via list endpoint with consumerName</li>
 *   <li>The DTO contract returns consumerName instead of providerName</li>
 * </ul>
 *
 * <p>Uses Testcontainers with real PostgreSQL.</p>
 *
 * <p><strong>Validates: Requirements 11.1, 11.2, 11.3</strong></p>
 */
@SpringBootTest
@Testcontainers
class ProviderRegistryBackwardCompatibilityIntegrationTest {

    private static final List<SupportedRequestType> IMMEDIATE_QUERY = List.of(
            new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        applicationName = "BackwardCompatApp-" + System.currentTimeMillis();
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(
                hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null)));
        environmentId = envResponse.environment().id();

        ContextServiceDTO contextService = contextServiceService.createContextService(hierarchy.getContextUuid(), "SchoolInfo", "OBJECT");
        contextServiceUuid = contextService.id();
    }

    /**
     * Verify V41 migration applied successfully — Flyway history contains V41.
     * Requirements: 11.1
     */
    @Test
    void migrationV41AppliedSuccessfully() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '41' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify provider_registry table no longer has provider_name column
     * but retains all other required columns after V41 migration.
     * Requirements: 11.1
     */
    @Test
    void providerRegistryTable_noProviderNameColumn_retainsAllOtherColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'provider_registry' ORDER BY ordinal_position",
                String.class);

        // provider_name must NOT be present
        assertThat(columns).doesNotContain("provider_name");

        // All other essential columns must still be present
        assertThat(columns).contains("id");
        assertThat(columns).contains("environment_id");
        assertThat(columns).contains("context_service_uuid");
        assertThat(columns).contains("endpoint");
        assertThat(columns).contains("service_type");
        assertThat(columns).contains("supported_request_types");
        assertThat(columns).contains("registered_at");
    }

    /**
     * Verify that registrations created after V41 migration are accessible via
     * the list endpoint and return consumerName (not providerName).
     * Requirements: 11.2, 11.3
     */
    @Test
    void existingRegistrations_accessibleViaListEndpoint_withConsumerName() {
        // Create a registration (simulates an existing registration post-migration)
        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://localhost:8080/schools"),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY)));

        ProviderRegistrationDTO created = providerService.registerProvider(
                hierarchy.getDomainId(), hierarchy.getSolutionId(), request);
        assertThat(created.id()).isNotNull();

        // List registrations — verifies list endpoint works correctly after column drop
        List<ProviderRegistrationDTO> registrations = providerService.listProviderRegistrations(environmentId);

        assertThat(registrations).hasSize(1);
        ProviderRegistrationDTO listed = registrations.get(0);

        // Verify all data is intact
        assertThat(listed.id()).isEqualTo(created.id());
        assertThat(listed.environmentId()).isEqualTo(environmentId);
        assertThat(listed.serviceName()).isEqualTo("SchoolInfo");
        assertThat(listed.zoneId()).isEqualTo(hierarchy.getZoneId());
        assertThat(listed.contextId()).isEqualTo(hierarchy.getContextId());
        assertThat(ProtocolDTO.locationOf(listed.endPoint())).isEqualTo("http://localhost:8080/schools");
        assertThat(listed.serviceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(listed.supportedRequestTypes().supportedRequestType()).isEqualTo(IMMEDIATE_QUERY);
        assertThat(listed.registeredAt()).isNotNull();

        // Verify consumerName is present (replaces providerName)
        assertThat(listed.providerName()).isEqualTo(applicationName);
    }

    /**
     * Verify that multiple registrations for an environment are all returned
     * correctly by the list endpoint after V41 migration.
     * Requirements: 11.2
     */
    @Test
    void multipleRegistrations_allAccessible_afterMigration() {
        // Create a second context_service
        ContextServiceDTO secondContextService = contextServiceService.createContextService(hierarchy.getContextUuid(), "StaffPersonal", "OBJECT");

        // Register two providers
        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),
                new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://localhost:8080/schools"),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY))));

        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),
                new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                secondContextService.id(),
                ProtocolDTO.of("http://localhost:8080/staff"),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY))));

        // List all registrations
        List<ProviderRegistrationDTO> registrations = providerService.listProviderRegistrations(environmentId);

        assertThat(registrations).hasSize(2);

        // Both should have consumerName and all data intact
        for (ProviderRegistrationDTO reg : registrations) {
            assertThat(reg.providerName()).isEqualTo(applicationName);
            assertThat(reg.environmentId()).isEqualTo(environmentId);
            assertThat(ProtocolDTO.locationOf(reg.endPoint())).isNotBlank();
            assertThat(reg.serviceType()).isEqualTo(ServiceType.OBJECT);
            assertThat(reg.registeredAt()).isNotNull();
        }

        // Verify distinct services
        List<String> serviceNames = registrations.stream()
                .map(ProviderRegistrationDTO::serviceName)
                .toList();
        assertThat(serviceNames).containsExactlyInAnyOrder("SchoolInfo", "StaffPersonal");
    }

    /**
     * Verify that registrations inserted directly via SQL (simulating pre-migration data)
     * are accessible through the service layer after V41.
     * Requirements: 11.1, 11.2
     *
     * <p>This method's stored JSON was updated for D1. It previously inserted the pre-D1 flat
     * {@code "operations":["QUERY"]}, which no longer binds to {@link SupportedRequestType}. That is
     * <b>deliberate</b>: {@code V48__normalise_supported_request_types_operations.sql} rewrites stored
     * rows once, so the running system has no tolerance for the old form and nothing in the read path
     * to remove later. A row arriving in the legacy shape <em>after</em> migration would mean something
     * is still writing it, which should fail loudly rather than be quietly accepted.</p>
     *
     * <p>The assertion this method actually exists for — a row written by raw SQL, with no
     * {@code provider_name} column, being readable through the service layer after V41 — is unchanged.
     * {@code migrationNormalisesLegacyOperationsArray} below covers the legacy shape itself.</p>
     */
    @Test
    void directlyInsertedRegistration_accessibleViaServiceLayer() {
        // Insert a registration directly via SQL to simulate a pre-existing row
        UUID registrationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO provider_registry (id, environment_id, context_service_uuid, endpoint, service_type, supported_request_types, registered_at) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb, NOW())",
                registrationId,
                environmentId,
                contextServiceUuid,
                "http://legacy-system:9090/data",
                "OBJECT",
                "[{\"requestType\":\"IMMEDIATE\",\"operations\":{\"operation\":[\"QUERY\"]}}]");

        // Verify the row is accessible via service layer
        List<ProviderRegistrationDTO> registrations = providerService.listProviderRegistrations(environmentId);

        assertThat(registrations).hasSize(1);
        ProviderRegistrationDTO dto = registrations.get(0);
        assertThat(dto.id()).isEqualTo(registrationId);
        assertThat(ProtocolDTO.locationOf(dto.endPoint())).isEqualTo("http://legacy-system:9090/data");
        assertThat(dto.providerName()).isEqualTo(applicationName);
        assertThat(dto.serviceName()).isEqualTo("SchoolInfo");
    }

    /**
     * Verify that V48 rewrites a pre-D1 row into the nested {@code operations} form, and that the
     * rewritten row is then readable through the service layer.
     *
     * <p>V48 runs at startup, before any test body, so a row inserted here is necessarily <em>after</em>
     * it. The migration's own SQL is therefore re-executed against this row — read from the classpath
     * rather than copied, so the statement under test is the one that ships. Running it twice also
     * pins the idempotency the migration claims.</p>
     *
     * <p>Without this, deleting V48 would leave every test green: nothing else writes the legacy shape
     * any more, so nothing else would notice.</p>
     *
     * Requirements: 2.13, plus the 11.1/11.2 backward-compatibility contract
     */
    @Test
    void migrationNormalisesLegacyOperationsArray() throws Exception {
        UUID registrationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO provider_registry (id, environment_id, context_service_uuid, endpoint, service_type, supported_request_types, registered_at) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb, NOW())",
                registrationId,
                environmentId,
                contextServiceUuid,
                "http://legacy-system:9090/data",
                "OBJECT",
                // Two entries, so entry-order preservation is actually exercised.
                "[{\"requestType\":\"IMMEDIATE\",\"operations\":[\"QUERY\",\"CREATE\"]},"
                + "{\"requestType\":\"DELAYED\",\"operations\":[\"UPDATE\"]}]");

        String migrationSql = new String(new ClassPathResource(
                "db/migration/V48__normalise_supported_request_types_operations.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        jdbcTemplate.execute(migrationSql);

        String stored = jdbcTemplate.queryForObject(
                "SELECT supported_request_types::text FROM provider_registry WHERE id = ?",
                String.class, registrationId);
        assertThat(stored)
                .as("operations must become an object carrying a singular operation array")
                .contains("\"operation\":")
                .doesNotContain("\"operations\": [")
                .doesNotContain("\"operations\":[");

        List<ProviderRegistrationDTO> registrations = providerService.listProviderRegistrations(environmentId);
        assertThat(registrations).hasSize(1);
        SupportedRequestsDTO types = registrations.get(0).supportedRequestTypes();

        assertThat(types.supportedRequestType())
                .as("entry order must survive the rewrite")
                .extracting(SupportedRequestType::requestType)
                .containsExactly(RequestType.IMMEDIATE, RequestType.DELAYED);
        assertThat(types.supportedRequestType().get(0).operations().operation())
                .containsExactlyInAnyOrder(Operation.QUERY, Operation.CREATE);
        assertThat(types.supportedRequestType().get(1).operations().operation())
                .containsExactly(Operation.UPDATE);

        // Idempotent: a second run must change nothing.
        jdbcTemplate.execute(migrationSql);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT supported_request_types::text FROM provider_registry WHERE id = ?",
                String.class, registrationId))
                .as("re-running the migration must be a no-op")
                .isEqualTo(stored);
    }
}

