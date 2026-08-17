// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.ProviderRegistration;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.exception.DuplicateProviderException;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.exception.InvalidProviderEnvironmentException;
import org.sifunity.broker.environment.exception.ProviderRegistrationNotFoundException;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Unit tests for ProviderService.
 * 
 * <p>Tests all business logic for provider registration management including
 * validation, duplicate checking, and CRUD operations.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderService")
class ProviderServiceTest {
    
    private static final List<SupportedRequestType> DEFAULT_SUPPORTED_REQUEST_TYPES = List.of(
        new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY, Operation.UPDATE))),
        new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE)))
    );
    
    private static final List<SupportedRequestType> IMMEDIATE_ONLY = List.of(
        new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY)))
    );
    
    @Mock
    private ProviderRegistrationRepository providerRegistrationRepository;
    
    @Mock
    private EnvironmentRepository environmentRepository;
    
    @Mock
    private ContextServiceService contextServiceService;
    
    @Mock
    private ContextServiceRepository contextServiceRepository;
    
    @Mock
    private ContextRepository contextRepository;
    
    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private DomainRepository domainRepository;

    @InjectMocks
    private ProviderService providerService;

    private static final String DOMAIN_ID = "test-domain";
    private static final String SOLUTION_ID = "test-solution";

    private UUID environmentId;
    private UUID providerRegistrationId;
    private UUID contextServiceUuid;
    private UUID contextUuid;
    private UUID zoneUuid;
    private UUID solutionUuid;
    private UUID domainUuid;
    private Environment providerEnvironment;
    private Environment consumerEnvironment;
    private ProviderRegistrationRequest validRequest;
    private ProviderRegistration savedProviderRegistration;
    private Zone zone;
    private Context context;
    private ContextService contextService;
    private Solution solution;
    private Domain domain;
    
    @BeforeEach
    void setUp() {
        environmentId = UUID.randomUUID();
        providerRegistrationId = UUID.randomUUID();
        contextServiceUuid = UUID.randomUUID();
        contextUuid = UUID.randomUUID();
        zoneUuid = UUID.randomUUID();
        solutionUuid = UUID.randomUUID();
        domainUuid = UUID.randomUUID();
        
        // Create provider environment
        providerEnvironment = new Environment();
        providerEnvironment.setId(environmentId);
        providerEnvironment.setApplicationName("TestProvider");
        providerEnvironment.setType(EnvironmentType.BROKERED);
        providerEnvironment.setDefaultZoneId("default");
        
        // Create consumer environment (for negative tests - uses DIRECT type to trigger type check)
        consumerEnvironment = new Environment();
        consumerEnvironment.setId(UUID.randomUUID());
        consumerEnvironment.setApplicationName("TestConsumer");
        consumerEnvironment.setType(EnvironmentType.DIRECT);
        consumerEnvironment.setDefaultZoneId("default");
        
        // Create zone and context for resolution
        zone = new Zone();
        zone.setId(zoneUuid);
        zone.setZoneId("default");
        zone.setSolutionUuid(solutionUuid);

        context = new Context();
        context.setId(contextUuid);
        context.setContextId("context1");
        context.setZoneUuid(zoneUuid);

        // Create context service
        contextService = new ContextService();
        contextService.setId(contextServiceUuid);
        contextService.setContextUuid(contextUuid);
        contextService.setServiceName("StudentPersonal");
        contextService.setServiceType("OBJECT");

        // Create solution and domain for tenant-ownership resolution
        // (provider → contextService → context → zone → solution → domain)
        solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId(SOLUTION_ID);
        solution.setDomainUuid(domainUuid);

        domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(DOMAIN_ID);
        
        // Create valid request
        validRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("https://api.example.com/students"),
                SupportedRequestsDTO.of(DEFAULT_SUPPORTED_REQUEST_TYPES)));
        
        // Create saved provider registration (uses contextServiceUuid instead of serviceName/zoneId/contextId)
        savedProviderRegistration = new ProviderRegistration();
        savedProviderRegistration.setId(providerRegistrationId);
        savedProviderRegistration.setEnvironment(providerEnvironment);
        savedProviderRegistration.setContextServiceUuid(contextServiceUuid);
        savedProviderRegistration.setEndpoint("https://api.example.com/students");
        savedProviderRegistration.setServiceType(ServiceType.OBJECT);
        savedProviderRegistration.setSupportedRequestTypes(DEFAULT_SUPPORTED_REQUEST_TYPES);
        savedProviderRegistration.setRegisteredAt(Instant.now());
    }
    
    @Nested
    @DisplayName("registerProvider")
    class RegisterProviderTests {
        
        @Test
        @DisplayName("should successfully register provider with valid request")
        void shouldRegisterProviderSuccessfully() {
            // Given - registerProvider now looks up contextService directly by UUID
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(providerEnvironment));
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(contextService));
            when(providerRegistrationRepository.findByContextServiceUuid(contextServiceUuid))
                    .thenReturn(Optional.empty());
            when(providerRegistrationRepository.save(any(ProviderRegistration.class)))
                    .thenReturn(savedProviderRegistration);
            // Tenant-ownership walk + mapToProviderRegistrationDTO resolve context/zone/solution/domain
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
            when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
            when(domainRepository.findById(domainUuid)).thenReturn(Optional.of(domain));

            // When
            ProviderRegistrationDTO result = providerService.registerProvider(DOMAIN_ID, SOLUTION_ID, validRequest);
            
            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(providerRegistrationId);
            assertThat(result.environmentId()).isEqualTo(environmentId);
            assertThat(result.serviceName()).isEqualTo("StudentPersonal");
            assertThat(result.zoneId()).isEqualTo("default");
            assertThat(result.contextId()).isEqualTo("context1");
            assertThat(ProtocolDTO.locationOf(result.endPoint())).isEqualTo("https://api.example.com/students");
            assertThat(result.serviceType()).isEqualTo(ServiceType.OBJECT);
            assertThat(result.supportedRequestTypes().supportedRequestType()).isEqualTo(DEFAULT_SUPPORTED_REQUEST_TYPES);
            assertThat(result.registeredAt()).isNotNull();
            
            verify(providerRegistrationRepository).save(any(ProviderRegistration.class));
        }
        
        @Test
        @DisplayName("should throw EnvironmentNotFoundException when environment does not exist")
        void shouldThrowEnvironmentNotFoundExceptionWhenEnvironmentDoesNotExist() {
            // Given
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> providerService.registerProvider(DOMAIN_ID, SOLUTION_ID, validRequest))
                    .isInstanceOf(EnvironmentNotFoundException.class)
                    .hasMessageContaining(environmentId.toString());
            
            verify(environmentRepository).findById(environmentId);
            verifyNoInteractions(providerRegistrationRepository);
        }
        
        @Test
        @DisplayName("should throw InvalidProviderEnvironmentException when environment is not BROKERED type")
        void shouldThrowInvalidProviderEnvironmentExceptionWhenEnvironmentIsNotProvider() {
            // Given
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(consumerEnvironment));

            // When & Then
            assertThatThrownBy(() -> providerService.registerProvider(DOMAIN_ID, SOLUTION_ID, validRequest))
                    .isInstanceOf(InvalidProviderEnvironmentException.class)
                    .hasMessageContaining("DIRECT")
                    .hasMessageContaining("BROKERED");
            
            verify(environmentRepository).findById(environmentId);
            verifyNoInteractions(providerRegistrationRepository);
        }
        
        @Test
        @DisplayName("should throw DuplicateProviderException when provider already exists")
        void shouldThrowDuplicateProviderExceptionWhenProviderAlreadyExists() {
            // Given - registerProvider now checks by contextServiceUuid alone (one provider per service)
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(providerEnvironment));
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(contextService));
            when(providerRegistrationRepository.findByContextServiceUuid(contextServiceUuid))
                    .thenReturn(Optional.of(new ProviderRegistration()));
            // Tenant-ownership walk (runs before the duplicate check) + DuplicateProviderException
            // message both need context/zone/solution/domain resolution
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
            when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
            when(domainRepository.findById(domainUuid)).thenReturn(Optional.of(domain));

            // When & Then
            assertThatThrownBy(() -> providerService.registerProvider(DOMAIN_ID, SOLUTION_ID, validRequest))
                    .isInstanceOf(DuplicateProviderException.class)
                    .hasMessageContaining("StudentPersonal")
                    .hasMessageContaining("default")
                    .hasMessageContaining("context1");
            
            verify(providerRegistrationRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should throw IllegalArgumentException for invalid endpoint URL")
        void shouldThrowIllegalArgumentExceptionForInvalidEndpointUrl() {
            // Given
            ProviderRegistrationRequest invalidRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("invalid-url"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
            
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(providerEnvironment));

            // When & Then
            assertThatThrownBy(() -> providerService.registerProvider(DOMAIN_ID, SOLUTION_ID, invalidRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid endpoint URL format");
            
            verify(environmentRepository).findById(environmentId);
            verifyNoInteractions(providerRegistrationRepository);
        }
        
        @Test
        @DisplayName("should throw IllegalArgumentException for non-HTTP/HTTPS endpoint")
        void shouldThrowIllegalArgumentExceptionForNonHttpEndpoint() {
            // Given
            ProviderRegistrationRequest invalidRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("ftp://api.example.com/students"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
            
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(providerEnvironment));

            // When & Then
            assertThatThrownBy(() -> providerService.registerProvider(DOMAIN_ID, SOLUTION_ID, invalidRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTP or HTTPS protocol");
            
            verify(environmentRepository).findById(environmentId);
            verifyNoInteractions(providerRegistrationRepository);
        }
        
        // Note: serviceName and zoneId validation tests removed — those fields
        // no longer exist in ProviderRegistrationRequest (replaced by contextServiceUuid).
        // Validation of service names and zone IDs now happens at the context_service level.
    }
    
    @Nested
    @DisplayName("lookupProvider")
    class LookupProviderTests {
        
        @Test
        @DisplayName("should return provider when found")
        void shouldReturnProviderWhenFound() {
            // Given
            when(zoneRepository.findBySolutionUuidAndZoneId(zone.getSolutionUuid(), "default")).thenReturn(Optional.of(zone));
            when(contextRepository.findByZoneUuidAndContextId(zoneUuid, "context1")).thenReturn(Optional.of(context));
            when(contextServiceRepository.findByContextUuidAndServiceName(contextUuid, "StudentPersonal"))
                    .thenReturn(Optional.of(contextService));
            when(providerRegistrationRepository.findByContextServiceUuid(contextServiceUuid))
                    .thenReturn(Optional.of(savedProviderRegistration));
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(contextService));
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
            
            // When
            Optional<ProviderRegistrationDTO> result = providerService.lookupProvider(
                    zone.getSolutionUuid(), "StudentPersonal", "default", "context1");
            
            // Then
            assertThat(result).isPresent();
            ProviderRegistrationDTO dto = result.get();
            assertThat(dto.id()).isEqualTo(providerRegistrationId);
            assertThat(dto.serviceName()).isEqualTo("StudentPersonal");
            assertThat(dto.zoneId()).isEqualTo("default");
            assertThat(dto.contextId()).isEqualTo("context1");
        }
        
        @Test
        @DisplayName("should return empty when provider not found")
        void shouldReturnEmptyWhenProviderNotFound() {
            // Given
            when(zoneRepository.findBySolutionUuidAndZoneId(zone.getSolutionUuid(), "default")).thenReturn(Optional.of(zone));
            when(contextRepository.findByZoneUuidAndContextId(zoneUuid, "context1")).thenReturn(Optional.of(context));
            when(contextServiceRepository.findByContextUuidAndServiceName(contextUuid, "NonExistentService"))
                    .thenReturn(Optional.empty());
            
            // When
            Optional<ProviderRegistrationDTO> result = providerService.lookupProvider(
                    zone.getSolutionUuid(), "NonExistentService", "default", "context1");
            
            // Then
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("should throw exception for invalid service name")
        void shouldThrowExceptionForInvalidServiceName() {
            // When & Then
            assertThatThrownBy(() -> providerService.lookupProvider(zone.getSolutionUuid(), "123Invalid", "default", "context1"))
                    .hasMessageContaining("serviceName");
            
            verifyNoInteractions(providerRegistrationRepository);
        }
        
        @Test
        @DisplayName("should throw exception for invalid zone ID")
        void shouldThrowExceptionForInvalidZoneId() {
            // When & Then
            assertThatThrownBy(() -> providerService.lookupProvider(zone.getSolutionUuid(), "StudentPersonal", "invalid zone!", "context1"))
                    .hasMessageContaining("zoneId");
            
            verifyNoInteractions(providerRegistrationRepository);
        }
    }
    
    @Nested
    @DisplayName("listProviderRegistrations")
    class ListProviderRegistrationsTests {
        
        @Test
        @DisplayName("should return list of provider registrations for environment")
        void shouldReturnListOfProviderRegistrationsForEnvironment() {
            // Given
            List<ProviderRegistration> providerRegistrations = List.of(savedProviderRegistration);
            when(providerRegistrationRepository.findByEnvironmentId(environmentId))
                    .thenReturn(providerRegistrations);
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(contextService));
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
            
            // When
            List<ProviderRegistrationDTO> result = providerService.listProviderRegistrations(environmentId);
            
            // Then
            assertThat(result).hasSize(1);
            ProviderRegistrationDTO dto = result.get(0);
            assertThat(dto.id()).isEqualTo(providerRegistrationId);
            assertThat(dto.environmentId()).isEqualTo(environmentId);
            assertThat(dto.serviceName()).isEqualTo("StudentPersonal");
            
            verify(providerRegistrationRepository).findByEnvironmentId(environmentId);
        }
        
        @Test
        @DisplayName("should return empty list when no registrations found")
        void shouldReturnEmptyListWhenNoRegistrationsFound() {
            // Given
            when(providerRegistrationRepository.findByEnvironmentId(environmentId))
                    .thenReturn(List.of());
            
            // When
            List<ProviderRegistrationDTO> result = providerService.listProviderRegistrations(environmentId);
            
            // Then
            assertThat(result).isEmpty();
            
            verify(providerRegistrationRepository).findByEnvironmentId(environmentId);
        }
    }
    
    @Nested
    @DisplayName("deleteProviderRegistration")
    class DeleteProviderRegistrationTests {
        
        @Test
        @DisplayName("should successfully delete provider registration")
        void shouldSuccessfullyDeleteProviderRegistration() {
            // Given
            when(providerRegistrationRepository.findById(providerRegistrationId))
                    .thenReturn(Optional.of(savedProviderRegistration));
            // Tenant-ownership walk: provider → contextService → context → zone → solution → domain
            when(contextServiceRepository.findById(contextServiceUuid)).thenReturn(Optional.of(contextService));
            when(contextRepository.findById(contextUuid)).thenReturn(Optional.of(context));
            when(zoneRepository.findById(zoneUuid)).thenReturn(Optional.of(zone));
            when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
            when(domainRepository.findById(domainUuid)).thenReturn(Optional.of(domain));

            // When
            providerService.deleteProviderRegistration(DOMAIN_ID, SOLUTION_ID, providerRegistrationId);

            // Then
            verify(providerRegistrationRepository).findById(providerRegistrationId);
            verify(providerRegistrationRepository).delete(savedProviderRegistration);
        }
        
        @Test
        @DisplayName("should throw ProviderRegistrationNotFoundException when registration does not exist")
        void shouldThrowProviderRegistrationNotFoundExceptionWhenRegistrationDoesNotExist() {
            // Given
            when(providerRegistrationRepository.findById(providerRegistrationId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> providerService.deleteProviderRegistration(DOMAIN_ID, SOLUTION_ID, providerRegistrationId))
                    .isInstanceOf(ProviderRegistrationNotFoundException.class)
                    .hasMessageContaining(providerRegistrationId.toString());
            
            verify(providerRegistrationRepository).findById(providerRegistrationId);
            verify(providerRegistrationRepository, never()).delete(any());
        }
    }
}
