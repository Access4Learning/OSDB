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
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.XQueryTemplateRepository;
import org.sifunity.broker.environment.service.*;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;

/**
 * Integration test verifying the decoupling between Named Query Template CRUD
 * and provider registration after the XQueryTemplateService fix.
 *
 * <p>Confirms that:
 * <ul>
 *   <li>Template creation does NOT auto-create provider registrations</li>
 *   <li>Template deletion does NOT auto-remove provider registrations</li>
 *   <li>Separately registered providers survive template deletion (decoupling)</li>
 *   <li>The existing {@code POST /providers/provider} flow with
 *       {@code serviceType=XQUERYTEMPLATE} continues to work</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 2.1, 2.4, 3.5, 3.6</strong></p>
 */
@SpringBootTest
@Testcontainers
class XQueryProviderRegistrationFixIntegrationTest {

    private static final List<SupportedRequestType> IMMEDIATE_QUERY = List.of(
            new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private XQueryTemplateService xQueryTemplateService;

    @Autowired
    private XQueryTemplateRepository xQueryTemplateRepository;

    @Autowired
    private ProviderRegistrationRepository providerRegistrationRepository;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private ContextServiceService contextServiceService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private EnvironmentService environmentService;

    private TestEntityHierarchy hierarchy;
    private String solutionId;
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
        // Create Domain → Solution → Zone → Context hierarchy
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        solutionId = hierarchy.getSolutionId();

        // Register an environment with defaultZoneId set
        String appName = "XQueryFixTestApp-" + System.nanoTime();
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), solutionId, null));
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(hierarchy.getDomainId(), envRequest);
        environmentId = envResponse.environment().id();
    }

    /**
     * Creating a template should only add a row to xquery_templates;
     * the provider_registry table must remain unchanged.
     *
     * <p><strong>Validates: Requirement 2.1</strong></p>
     */
    @Test
    void createTemplate_doesNotCreateProviderRegistration() {
        long providerCountBefore = providerRegistrationRepository.count();
        long templateCountBefore = xQueryTemplateRepository.count();

        String queryToken = "CreateNoProvider-" + System.nanoTime();
        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, "SINGULAR", "APPROVED", null,
                "Integration test template", "SELECT 1",
                new XQueryParametersDTO(List.of(new XQueryParameterDTO("studentId", "xs:string", null, "Student ID", true))),
                "http://sif.org/StudentPersonal"));

        XQueryTemplateDTO created = xQueryTemplateService.create(hierarchy.getDomainId(), solutionId, environmentId, request);

        // Template was persisted
        assertThat(created.id()).isEqualTo(queryToken);
        assertThat(xQueryTemplateRepository.count()).isEqualTo(templateCountBefore + 1);

        // Provider registry unchanged
        long providerCountAfter = providerRegistrationRepository.count();
        assertThat(providerCountAfter)
                .as("provider_registry row count must not change after template creation")
                .isEqualTo(providerCountBefore);
    }

    /**
     * Deleting a template should only remove the xquery_templates row;
     * the provider_registry table must remain unchanged.
     *
     * <p><strong>Validates: Requirement 2.4</strong></p>
     */
    @Test
    void deleteTemplate_doesNotRemoveProviderRegistration() {
        // Create a template first
        String queryToken = "DeleteNoProvider-" + System.nanoTime();
        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, "FORMULA", "PENDING", null,
                "Template for delete test", null, new XQueryParametersDTO(List.of()), null));
        xQueryTemplateService.create(hierarchy.getDomainId(), solutionId, environmentId, request);

        long providerCountBefore = providerRegistrationRepository.count();
        long templateCountBefore = xQueryTemplateRepository.count();

        // Delete the template
        xQueryTemplateService.delete(hierarchy.getDomainId(), solutionId, queryToken);

        // Template removed
        assertThat(xQueryTemplateRepository.count()).isEqualTo(templateCountBefore - 1);

        // Provider registry unchanged
        long providerCountAfter = providerRegistrationRepository.count();
        assertThat(providerCountAfter)
                .as("provider_registry row count must not change after template deletion")
                .isEqualTo(providerCountBefore);
    }

    /**
     * Provider registered via the separate POST /providers/provider path
     * must survive template deletion (decoupling confirmed).
     *
     * <p>Flow: create template → separately register provider → delete template
     * → verify provider registration still exists.</p>
     *
     * <p><strong>Validates: Requirements 2.4, 3.5, 3.6</strong></p>
     */
    @Test
    void separateProviderRegistration_survivesTemplateDeletion() {
        String queryToken = "DecouplingTest-" + System.nanoTime();

        // Step 1: Create template
        XQueryTemplateCreateRequest templateRequest = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                queryToken, "SINGULAR", "APPROVED", null,
                "Template for decoupling test", "SELECT * FROM t",
                new XQueryParametersDTO(List.of()), null));
        xQueryTemplateService.create(hierarchy.getDomainId(), solutionId, environmentId, templateRequest);

        // Step 2: Separately register a provider via the providers path
        // Create a context_service for this queryToken in the DEFAULT context
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                hierarchy.getContextUuid(), queryToken, "XQUERYTEMPLATE");

        ProviderRegistrationRequest providerRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.XQUERYTEMPLATE,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://provider.example.com/xquery/" + queryToken),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY)));

        ProviderRegistrationDTO registeredProvider = providerService.registerProvider(
                hierarchy.getDomainId(), solutionId, providerRequest);
        UUID providerRegistrationId = registeredProvider.id();

        // Verify provider registration exists
        assertThat(providerRegistrationRepository.findById(providerRegistrationId)).isPresent();

        // Step 3: Delete the template
        xQueryTemplateService.delete(hierarchy.getDomainId(), solutionId, queryToken);

        // Step 4: Verify provider registration still exists (decoupling confirmed)
        assertThat(providerRegistrationRepository.findById(providerRegistrationId))
                .as("Provider registration must survive template deletion")
                .isPresent();

        // Verify the provider's endpoint is intact
        assertThat(providerRegistrationRepository.findById(providerRegistrationId).get().getEndpoint())
                .isEqualTo("http://provider.example.com/xquery/" + queryToken);
    }

    /**
     * The existing POST /providers/provider flow with serviceType=XQUERYTEMPLATE
     * continues to work correctly (preservation of separate registration path).
     *
     * <p><strong>Validates: Requirements 3.5, 3.6</strong></p>
     */
    @Test
    void providerRegistrationEndpoint_withXQueryTemplateServiceType_stillWorks() {
        String queryToken = "ProvRegPreservation-" + System.nanoTime();

        // Create a context_service for this queryToken
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                hierarchy.getContextUuid(), queryToken, "XQUERYTEMPLATE");

        // Register provider via the standard providers endpoint
        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.XQUERYTEMPLATE,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://provider.example.com/xquery/" + queryToken),
                SupportedRequestsDTO.of(IMMEDIATE_QUERY)));

        ProviderRegistrationDTO created = providerService.registerProvider(
                hierarchy.getDomainId(), solutionId, request);

        // Verify registration was successful
        assertThat(created.id()).isNotNull();
        assertThat(created.environmentId()).isEqualTo(environmentId);
        assertThat(created.serviceName()).isEqualTo(queryToken);
        assertThat(ProtocolDTO.locationOf(created.endPoint())).isEqualTo("http://provider.example.com/xquery/" + queryToken);
        assertThat(created.serviceType()).isEqualTo(ServiceType.XQUERYTEMPLATE);
        assertThat(created.supportedRequestTypes().supportedRequestType()).isEqualTo(IMMEDIATE_QUERY);

        // Verify it can be listed
        List<ProviderRegistrationDTO> registrations = providerService.listProviderRegistrations(environmentId);
        assertThat(registrations)
                .as("Provider registration list should contain the XQUERYTEMPLATE registration")
                .anyMatch(r -> r.serviceName().equals(queryToken)
                        && r.serviceType() == ServiceType.XQUERYTEMPLATE
                        && ProtocolDTO.locationOf(r.endPoint()).equals("http://provider.example.com/xquery/" + queryToken));
    }
}
