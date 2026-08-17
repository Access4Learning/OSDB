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
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.exception.DuplicateProviderException;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.service.ContextServiceService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Zone;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * End-to-end integration test for provider registry using Testcontainers.
 */
@SpringBootTest
@Testcontainers
class ProviderRegistryIntegrationTest {
    
    private static final List<SupportedRequestType> IMMEDIATE_AND_DELAYED = List.of(
        new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY, Operation.UPDATE))),
        new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE)))
    );
    
    private static final List<SupportedRequestType> IMMEDIATE_ONLY = List.of(
        new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY)))
    );
    
    private static final List<SupportedRequestType> DELAYED_ONLY = List.of(
        new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE)))
    );
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private ProviderService providerService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private ContextServiceService contextServiceService;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ContextRepository contextRepository;

    private TestEntityHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
    }
    
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
    
    @Test
    void testProviderRegistrationAndLookup() {
        EnvironmentRegistrationRequest environmentRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("ProviderApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse environmentResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), environmentRequest);
        UUID environmentId = environmentResponse.environment().id();
        
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(
            hierarchy.getContextUuid(), "StudentService", "OBJECT");
        
        ProviderRegistrationRequest providerRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("http://localhost:8080/api/students"),
                SupportedRequestsDTO.of(IMMEDIATE_AND_DELAYED)));
        
        ProviderRegistrationDTO registeredProvider = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),providerRequest);
        
        assertThat(registeredProvider).isNotNull();
        assertThat(registeredProvider.id()).isNotNull();
        assertThat(registeredProvider.environmentId()).isEqualTo(environmentId);
        assertThat(registeredProvider.serviceName()).isEqualTo("StudentService");
        assertThat(registeredProvider.serviceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(registeredProvider.supportedRequestTypes().supportedRequestType()).isEqualTo(IMMEDIATE_AND_DELAYED);
        assertThat(registeredProvider.registeredAt()).isNotNull();
        
        Optional<ProviderRegistrationDTO> lookedUpProvider = providerService.lookupProvider(
            hierarchy.getSolutionUuid(), "StudentService", hierarchy.getZoneId(), hierarchy.getContextId()
        );
        
        assertThat(lookedUpProvider).as("Provider should be found").isPresent();
        
        ProviderRegistrationDTO foundProvider = lookedUpProvider.get();
        assertThat(foundProvider.id()).isEqualTo(registeredProvider.id());
        assertThat(foundProvider.serviceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(foundProvider.supportedRequestTypes().supportedRequestType()).isEqualTo(IMMEDIATE_AND_DELAYED);
    }
    
    @Test
    void testLookupNonExistentProvider() {
        Optional<ProviderRegistrationDTO> lookedUpProvider = providerService.lookupProvider(
            hierarchy.getSolutionUuid(), "NonExistentService", hierarchy.getZoneId(), hierarchy.getContextId()
        );
        assertThat(lookedUpProvider).as("Lookup should return empty for non-existent provider").isEmpty();
    }
    
    @Test
    void testDuplicateProviderRegistration() {
        // Register provider in first environment
        EnvironmentRegistrationRequest environmentRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("DuplicateProviderApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse environmentResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), environmentRequest);
        UUID environmentId = environmentResponse.environment().id();
        
        UUID teacherServiceUuid = contextServiceService.resolveOrCreate(
            hierarchy.getContextUuid(), "TeacherService", "OBJECT");
        
        ProviderRegistrationRequest firstProviderRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                teacherServiceUuid,
                ProtocolDTO.of("http://localhost:8081/api/teachers"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
        
        ProviderRegistrationDTO firstProvider = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),firstProviderRequest);
        assertThat(firstProvider).isNotNull();
        
        // Cross-environment registration with same contextServiceUuid should FAIL
        // Per SIF spec: only one provider per zone/context/service
        EnvironmentRegistrationRequest secondEnvironmentRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("SecondProviderApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse secondEnvironmentResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), secondEnvironmentRequest);
        UUID secondEnvironmentId = secondEnvironmentResponse.environment().id();
        
        ProviderRegistrationRequest crossEnvProviderRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                secondEnvironmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                teacherServiceUuid,
                ProtocolDTO.of("http://localhost:8082/api/teachers-duplicate"),
                SupportedRequestsDTO.of(DELAYED_ONLY)));
        
        assertThatThrownBy(() -> providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),crossEnvProviderRequest))
            .isInstanceOf(DuplicateProviderException.class);
        
        // Same-environment duplicate should also throw DuplicateProviderException
        ProviderRegistrationRequest sameEnvDuplicateRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                teacherServiceUuid,
                ProtocolDTO.of("http://localhost:8083/api/teachers-dup2"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
        
        assertThatThrownBy(() -> providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),sameEnvDuplicateRequest))
            .isInstanceOf(DuplicateProviderException.class)
            .hasMessageContaining("TeacherService")
            .hasMessageContaining(hierarchy.getZoneId())
            .hasMessageContaining(hierarchy.getContextId());
    }
    
    @Test
    void testProviderRegistrationWithNullContext() {
        // Since context_services requires a non-null context_uuid, null contextId is no longer
        // supported for provider registration. Use the DEFAULT context from the hierarchy instead.
        EnvironmentRegistrationRequest environmentRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("NullContextProviderApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse environmentResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), environmentRequest);
        UUID environmentId = environmentResponse.environment().id();
        
        UUID gradeServiceUuid = contextServiceService.resolveOrCreate(
            hierarchy.getContextUuid(), "GradeService", "OBJECT");
        
        ProviderRegistrationRequest providerRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                gradeServiceUuid,
                ProtocolDTO.of("http://localhost:8083/api/grades"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
        
        ProviderRegistrationDTO registeredProvider = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),providerRequest);
        assertThat(registeredProvider).isNotNull();
        assertThat(registeredProvider.contextId()).isEqualTo(hierarchy.getContextId());
        
        Optional<ProviderRegistrationDTO> lookedUpProvider = providerService.lookupProvider(
            hierarchy.getSolutionUuid(), "GradeService", hierarchy.getZoneId(), hierarchy.getContextId()
        );
        
        assertThat(lookedUpProvider).isPresent();
        assertThat(lookedUpProvider.get().id()).isEqualTo(registeredProvider.id());
    }
    
    @Test
    void testProviderRegistrationWithDifferentContexts() {
        // Create two separate zones with their own contexts
        String zoneId1 = "ctx-zone1-" + System.nanoTime();
        zoneService.createZone(hierarchy.getDomainId(), new org.sifunity.broker.environment.dto.ZoneCreateRequest(hierarchy.getSolutionId(), zoneId1, "Test zone 1"));
        String contextId1 = "DEFAULT";
        
        String zoneId2 = "ctx-zone2-" + System.nanoTime();
        zoneService.createZone(hierarchy.getDomainId(), new org.sifunity.broker.environment.dto.ZoneCreateRequest(hierarchy.getSolutionId(), zoneId2, "Test zone 2"));
        String contextId2 = "DEFAULT";
        
        EnvironmentRegistrationRequest environment1Request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("Provider1-" + System.currentTimeMillis(), EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId1), hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse environment1Response = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), environment1Request);
        UUID environment1Id = environment1Response.environment().id();
        
        EnvironmentRegistrationRequest environment2Request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("Provider2-" + System.currentTimeMillis(), EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId2), hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse environment2Response = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), environment2Request);
        UUID environment2Id = environment2Response.environment().id();
        
        // Resolve contextServiceUuids for each zone's DEFAULT context
        Zone zone1 = zoneRepository.findBySolutionUuidAndZoneId(hierarchy.getSolutionUuid(), zoneId1).orElseThrow();
        Context context1 = contextRepository.findByZoneUuidAndContextId(zone1.getId(), contextId1).orElseThrow();
        UUID attendanceServiceUuid1 = contextServiceService.resolveOrCreate(
            context1.getId(), "AttendanceService", "OBJECT");
        
        Zone zone2 = zoneRepository.findBySolutionUuidAndZoneId(hierarchy.getSolutionUuid(), zoneId2).orElseThrow();
        Context context2 = contextRepository.findByZoneUuidAndContextId(zone2.getId(), contextId2).orElseThrow();
        UUID attendanceServiceUuid2 = contextServiceService.resolveOrCreate(
            context2.getId(), "AttendanceService", "OBJECT");
        
        ProviderRegistrationRequest provider1Request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environment1Id,
                null,
                ServiceType.OBJECT,
                null,
                null,
                attendanceServiceUuid1,
                ProtocolDTO.of("http://localhost:8084/api/attendance-context1"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
        ProviderRegistrationDTO provider1 = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),provider1Request);
        assertThat(provider1).isNotNull();
        
        ProviderRegistrationRequest provider2Request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environment2Id,
                null,
                ServiceType.OBJECT,
                null,
                null,
                attendanceServiceUuid2,
                ProtocolDTO.of("http://localhost:8085/api/attendance-context2"),
                SupportedRequestsDTO.of(DELAYED_ONLY)));
        ProviderRegistrationDTO provider2 = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),provider2Request);
        assertThat(provider2).isNotNull();
        
        Optional<ProviderRegistrationDTO> lookedUpProvider1 = providerService.lookupProvider(
            hierarchy.getSolutionUuid(), "AttendanceService", zoneId1, contextId1
        );
        assertThat(lookedUpProvider1).isPresent();
        assertThat(lookedUpProvider1.get().id()).isEqualTo(provider1.id());
        
        Optional<ProviderRegistrationDTO> lookedUpProvider2 = providerService.lookupProvider(
            hierarchy.getSolutionUuid(), "AttendanceService", zoneId2, contextId2
        );
        assertThat(lookedUpProvider2).isPresent();
        assertThat(lookedUpProvider2.get().id()).isEqualTo(provider2.id());
    }
    
    @Test
    void testProviderRegistrationWithAllRequestTypes() {
        EnvironmentRegistrationRequest environmentRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AllPatternsProvider-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse environmentResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), environmentRequest);
        UUID environmentId = environmentResponse.environment().id();
        
        List<SupportedRequestType> allRequestTypes = List.of(
            new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))),
            new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))
        );
        
        UUID comprehensiveServiceUuid = contextServiceService.resolveOrCreate(
            hierarchy.getContextUuid(), "ComprehensiveService", "OBJECT");
        
        ProviderRegistrationRequest providerRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                comprehensiveServiceUuid,
                ProtocolDTO.of("http://localhost:8086/api/comprehensive"),
                SupportedRequestsDTO.of(allRequestTypes)));
        
        ProviderRegistrationDTO registeredProvider = providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),providerRequest);
        assertThat(registeredProvider).isNotNull();
        assertThat(registeredProvider.supportedRequestTypes().supportedRequestType()).isEqualTo(allRequestTypes);
        
        Optional<ProviderRegistrationDTO> lookedUpProvider = providerService.lookupProvider(
            hierarchy.getSolutionUuid(), "ComprehensiveService", hierarchy.getZoneId(), hierarchy.getContextId()
        );
        assertThat(lookedUpProvider).isPresent();
        assertThat(lookedUpProvider.get().supportedRequestTypes().supportedRequestType()).isEqualTo(allRequestTypes);
    }
    
    @Test
    void testLookupWithWrongZoneOrContext() {
        EnvironmentRegistrationRequest environmentRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("ExactMatchProvider-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse environmentResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), environmentRequest);
        UUID environmentId = environmentResponse.environment().id();
        
        UUID exactMatchServiceUuid = contextServiceService.resolveOrCreate(
            hierarchy.getContextUuid(), "ExactMatchService", "OBJECT");
        
        ProviderRegistrationRequest providerRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                exactMatchServiceUuid,
                ProtocolDTO.of("http://localhost:8087/api/exactmatch"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
        providerService.registerProvider(hierarchy.getDomainId(), hierarchy.getSolutionId(),providerRequest);
        
        assertThat(providerService.lookupProvider(hierarchy.getSolutionUuid(), "ExactMatchService", "staging-nonexistent", hierarchy.getContextId()))
            .as("Lookup with wrong zone should return empty").isEmpty();
        
        assertThat(providerService.lookupProvider(hierarchy.getSolutionUuid(), "ExactMatchService", hierarchy.getZoneId(), "other-nonexistent"))
            .as("Lookup with wrong context should return empty").isEmpty();
        
        assertThat(providerService.lookupProvider(
            hierarchy.getSolutionUuid(), "ExactMatchService", hierarchy.getZoneId(), hierarchy.getContextId()
        ))
            .as("Lookup with correct values should find provider").isPresent();
    }
}
