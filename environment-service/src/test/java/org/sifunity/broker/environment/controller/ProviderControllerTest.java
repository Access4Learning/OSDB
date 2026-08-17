// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.exception.DuplicateProviderException;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.exception.InvalidProviderEnvironmentException;
import org.sifunity.broker.environment.exception.ProviderRegistrationNotFoundException;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.common.constant.EnvironmentType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for ProviderController.
 * 
 * <p>Tests all REST endpoints for provider registration management including
 * request validation, response formatting, and error handling.</p>
 */
@WebMvcTest(
    controllers = ProviderController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
)
@Import(TestSecurityConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
    "observability.metrics.enabled=false",
    "secrets.backend.type=plaintext"
})
@org.springframework.test.context.ActiveProfiles("disable-security")
@DisplayName("ProviderController")
class ProviderControllerTest {
    
    private static final String TEST_DOMAIN_ID = "test-domain";
    private static final String TEST_SOLUTION_ID = "test-solution";
    private static final String BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID + "/providers";
    // SIF 3.7 singular-subpath create: POST /providers/provider
    private static final String CREATE_PATH = BASE_PATH + "/provider";
    
    private static final List<SupportedRequestType> DEFAULT_SUPPORTED_REQUEST_TYPES = List.of(
        new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY, Operation.UPDATE))),
        new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE)))
    );
    
    private static final List<SupportedRequestType> IMMEDIATE_ONLY = List.of(
        new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY)))
    );
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private ProviderService providerService;
    
    @MockBean
    private org.sifunity.broker.environment.service.AuthorizationService authorizationService;
    
    @MockBean
    private org.sifunity.broker.environment.service.ProvisionRequestService provisionRequestService;
    
    // Mock repositories needed by other controllers that get loaded
    @MockBean
    private org.sifunity.broker.environment.repository.ProviderRegistrationRepository providerRegistrationRepository;
    
    @MockBean
    private org.sifunity.broker.environment.repository.AclEntryRepository aclEntryRepository;
    
    @MockBean
    private org.sifunity.broker.environment.repository.EnvironmentRepository environmentRepository;
    
    @MockBean
    private org.sifunity.broker.environment.repository.ZoneRepository zoneRepository;
    
    @MockBean
    private org.sifunity.broker.environment.repository.ContextRepository contextRepository;
    
    @MockBean
    private org.sifunity.broker.environment.repository.ContextServiceRepository contextServiceRepository;
    
    @MockBean
    private org.sifunity.broker.environment.repository.DomainRepository domainRepository;
    
    @MockBean
    private org.sifunity.broker.environment.repository.SolutionRepository solutionRepository;

    @MockBean
    private org.sifunity.broker.environment.repository.SolutionServiceRepository solutionServiceRepository;
    
    // Mock services for new Domain and Solution controllers
    @MockBean
    private org.sifunity.broker.environment.service.DomainService domainService;
    
    @MockBean
    private org.sifunity.broker.environment.service.SolutionService solutionService;
    
    @MockBean
    private org.sifunity.broker.environment.service.EntityTagService entityTagService;
    
    @MockBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    @MockBean
    private org.sifunity.broker.environment.service.AlertsService alertsService;
    @MockBean
    private org.sifunity.broker.environment.service.MessageContextCacheService messageContextCacheService;
    @MockBean
    private org.sifunity.broker.environment.service.CodeSetsService codeSetsService;
    @MockBean
    private org.sifunity.broker.environment.service.NamespacesService namespacesService;
    @MockBean
    private org.sifunity.broker.environment.service.NamespaceEventPublisher namespaceEventPublisher;
    @MockBean
    private org.sifunity.broker.environment.service.ContextServiceService contextServiceService;
    @MockBean
    private org.sifunity.broker.environment.service.UserAuthenticationService userAuthenticationService;
    @MockBean
    private org.sifunity.broker.environment.service.AdminUserService adminUserService;
    @MockBean
    private org.sifunity.broker.environment.service.UserEnvironmentAccessService userEnvironmentAccessService;
    @MockBean
    private org.sifunity.broker.environment.service.TagDefinitionService tagDefinitionService;
    @MockBean
    private org.sifunity.broker.environment.service.XQueryTemplateService xQueryTemplateService;
    
    private UUID environmentId;
    private UUID providerRegistrationId;
    private ProviderRegistrationRequest validRequest;
    private ProviderRegistrationDTO providerRegistrationDTO;
    
    @BeforeEach
    void setUp() {
        environmentId = UUID.randomUUID();
        providerRegistrationId = UUID.randomUUID();
        
        // Stub domain + solution resolution so lookupProvider resolves (domainId, solutionId) → solutionUuid.
        // Resolution is now domain-scoped: findByDomainId → findByDomainUuidAndSolutionId.
        UUID mockDomainUuid = UUID.randomUUID();
        org.sifunity.broker.environment.domain.Domain mockDomain = new org.sifunity.broker.environment.domain.Domain();
        mockDomain.setId(mockDomainUuid);
        mockDomain.setDomainId(TEST_DOMAIN_ID);
        org.mockito.Mockito.lenient().when(domainRepository.findByDomainId(TEST_DOMAIN_ID))
                .thenReturn(java.util.Optional.of(mockDomain));

        org.sifunity.broker.environment.domain.Solution mockSolution = new org.sifunity.broker.environment.domain.Solution();
        mockSolution.setId(UUID.randomUUID());
        mockSolution.setSolutionId(TEST_SOLUTION_ID);
        org.mockito.Mockito.lenient().when(solutionRepository.findByDomainUuidAndSolutionId(mockDomainUuid, TEST_SOLUTION_ID))
                .thenReturn(java.util.Optional.of(mockSolution));
        
        validRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                UUID.randomUUID(),
                ProtocolDTO.of("https://api.example.com/students"),
                SupportedRequestsDTO.of(DEFAULT_SUPPORTED_REQUEST_TYPES)));
        
        providerRegistrationDTO = new ProviderRegistrationDTO(
                providerRegistrationId,
                environmentId,
                "StudentPersonal",
                "default",
                "context1",
                // D1: providerName replaces the old consumerName and is required by the schema, so a
                // null here would make the fixture itself non-conformant.
                "TestProvider",
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("https://api.example.com/students"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(DEFAULT_SUPPORTED_REQUEST_TYPES),
                Instant.now()
        );
    }
    
    @Nested
    @DisplayName("POST /providers/provider")
    class RegisterProviderTests {

        /**
         * Canary for D1's request envelope. Every other test in this class builds the request through
         * the record, so they would all keep passing if the wrapper were removed — they prove the
         * wrapped form is <em>accepted</em>, not that the flat form is <em>rejected</em>. This sends the
         * pre-D1 flat JSON by hand, which is the only way to detect a regression that re-admits it.
         */
        @Test
        @DisplayName("should reject the pre-D1 flat body that is not wrapped under 'provider'")
        void shouldRejectUnwrappedFlatBody() throws Exception {
            String flatBody = """
                {
                  "environmentId": "%s",
                  "contextServiceUuid": "%s",
                  "endpoint": "https://api.example.com/students",
                  "serviceType": "OBJECT",
                  "supportedRequestTypes": [
                    { "requestType": "IMMEDIATE", "operations": ["QUERY"] }
                  ]
                }
                """.formatted(environmentId, UUID.randomUUID());

            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(flatBody))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(providerService);
        }

        /**
         * The conforming identification form: a client sends the (serviceName, zoneId, contextId)
         * triple, since contextServiceUuid is an OSDB-internal key it cannot know.
         */
        @Test
        @DisplayName("should accept the schema triple instead of contextServiceUuid")
        void shouldAcceptServiceNameZoneContextTriple() throws Exception {
            when(providerService.registerProvider(any(), any(), any(ProviderRegistrationRequest.class)))
                    .thenReturn(providerRegistrationDTO);

            String tripleBody = """
                {
                  "provider": {
                    "environmentId": "%s",
                    "serviceName": "StudentPersonal",
                    "serviceType": "OBJECT",
                    "zoneId": "district-zone",
                    "contextId": "DEFAULT",
                    "endPoint": { "location": "https://api.example.com/students" },
                    "supportedRequestTypes": {
                      "supportedRequestType": [
                        { "requestType": "IMMEDIATE", "operations": { "operation": ["QUERY"] } }
                      ]
                    }
                  }
                }
                """.formatted(environmentId);

            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(tripleBody))
                    .andExpect(status().isCreated());

            verify(providerService).registerProvider(any(), any(), any(ProviderRegistrationRequest.class));
        }

        /**
         * Neither identification form supplied — rejected by
         * {@code ProviderRegistrationBody.isServiceIdentifiable()} before the service is reached.
         */
        @Test
        @DisplayName("should reject a body with neither contextServiceUuid nor the triple")
        void shouldRejectUnidentifiableService() throws Exception {
            String noIdentity = """
                {
                  "provider": {
                    "environmentId": "%s",
                    "serviceType": "OBJECT",
                    "endPoint": { "location": "https://api.example.com/students" },
                    "supportedRequestTypes": {
                      "supportedRequestType": [
                        { "requestType": "IMMEDIATE", "operations": { "operation": ["QUERY"] } }
                      ]
                    }
                  }
                }
                """.formatted(environmentId);

            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(noIdentity))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(providerService);
        }


        @Test
        @DisplayName("should register provider successfully with valid request")
        void shouldRegisterProviderSuccessfully() throws Exception {
            // Given
            when(providerService.registerProvider(any(), any(), any(ProviderRegistrationRequest.class)))
                    .thenReturn(providerRegistrationDTO);
            
            // When & Then
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.provider.id").value(providerRegistrationId.toString()))
                    .andExpect(jsonPath("$.provider.environmentId").value(environmentId.toString()))
                    .andExpect(jsonPath("$.provider.serviceName").value("StudentPersonal"))
                    .andExpect(jsonPath("$.provider.zoneId").value("default"))
                    .andExpect(jsonPath("$.provider.contextId").value("context1"))
                    // D1: endPoint is an object with a required location — note the capital P.
                    .andExpect(jsonPath("$.provider.endPoint.location").value("https://api.example.com/students"))
                    .andExpect(jsonPath("$.provider.serviceType").value("OBJECT"))
                    // D1: providerName is required; consumerName is not part of the provider schema.
                    .andExpect(jsonPath("$.provider.providerName").exists())
                    .andExpect(jsonPath("$.provider.consumerName").doesNotExist())
                    // D1: querySupport's key is required even when its contents are all optional.
                    .andExpect(jsonPath("$.provider.querySupport").exists())
                    // D1: supportedRequestTypes is an object wrapping a singular supportedRequestType
                    // array — the bare array it used to be is the defect.
                    .andExpect(jsonPath("$.provider.supportedRequestTypes").isMap())
                    .andExpect(jsonPath("$.provider.supportedRequestTypes.supportedRequestType").isArray())
                    .andExpect(jsonPath("$.provider.registeredAt").exists());
            
            verify(providerService).registerProvider(any(), any(), any(ProviderRegistrationRequest.class));
        }
        
        @Test
        @DisplayName("should register provider with null context")
        void shouldRegisterProviderWithNullContext() throws Exception {
            // Given
            ProviderRegistrationRequest requestWithNullContext = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                UUID.randomUUID(),
                ProtocolDTO.of("https://api.example.com/students"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
            
            ProviderRegistrationDTO responseWithNullContext = new ProviderRegistrationDTO(
                providerRegistrationId,
                environmentId,
                "StudentPersonal",
                "default",
                null,               // contextId — the point of this case
                "TestProvider",     // providerName (D1: required)
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("https://api.example.com/students"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(IMMEDIATE_ONLY),
                Instant.now()
        );
            
            when(providerService.registerProvider(any(), any(), any(ProviderRegistrationRequest.class)))
                    .thenReturn(responseWithNullContext);
            
            // When & Then
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestWithNullContext)))
                    .andExpect(status().isCreated())
                    // D5: a null contextId is now OMITTED rather than emitted as null.
                    .andExpect(jsonPath("$.provider.contextId").doesNotExist())
                    // Sibling control: without this, "contextId is absent" would also be satisfied by
                    // an empty or missing provider object, which is not what this test is about.
                    .andExpect(jsonPath("$.provider.serviceName").value("StudentPersonal"));

            verify(providerService).registerProvider(any(), any(), any(ProviderRegistrationRequest.class));
        }
        
        @Test
        @DisplayName("should return 400 for invalid request data")
        void shouldReturn400ForInvalidRequestData() throws Exception {
            // Given - request with missing required fields
            String invalidJson = """
                {
                    "environmentId": null,
                    "contextServiceUuid": null,
                    "endpoint": "invalid-url",
                    "serviceType": null,
                    "supportedRequestTypes": []
                }
                """;
            
            // When & Then
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson))
                    .andExpect(status().isBadRequest());
            
            verifyNoInteractions(providerService);
        }
        
        @Test
        @DisplayName("should return 404 when environment not found")
        void shouldReturn404WhenEnvironmentNotFound() throws Exception {
            // Given
            when(providerService.registerProvider(any(), any(), any(ProviderRegistrationRequest.class)))
                    .thenThrow(new EnvironmentNotFoundException(environmentId));
            
            // When & Then
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
            
            verify(providerService).registerProvider(any(), any(), any(ProviderRegistrationRequest.class));
        }
        
        @Test
        @DisplayName("should return 400 when environment is not BROKERED type")
        void shouldReturn400WhenEnvironmentIsNotProviderType() throws Exception {
            // Given
            when(providerService.registerProvider(any(), any(), any(ProviderRegistrationRequest.class)))
                    .thenThrow(new InvalidProviderEnvironmentException(environmentId, EnvironmentType.DIRECT));
            
            // When & Then
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
            
            verify(providerService).registerProvider(any(), any(), any(ProviderRegistrationRequest.class));
        }
        
        @Test
        @DisplayName("should return 409 when provider already exists")
        void shouldReturn409WhenProviderAlreadyExists() throws Exception {
            // Given
            when(providerService.registerProvider(any(), any(), any(ProviderRegistrationRequest.class)))
                    .thenThrow(new DuplicateProviderException("StudentPersonal", "default", "context1"));
            
            // When & Then
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isConflict());
            
            verify(providerService).registerProvider(any(), any(), any(ProviderRegistrationRequest.class));
        }
        
        @Test
        @DisplayName("should return 400 for invalid URL format")
        void shouldReturn400ForInvalidUrlFormat() throws Exception {
            // Given
            when(providerService.registerProvider(any(), any(), any(ProviderRegistrationRequest.class)))
                    .thenThrow(new IllegalArgumentException("Invalid endpoint URL format"));
            
            // When & Then
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
            
            verify(providerService).registerProvider(any(), any(), any(ProviderRegistrationRequest.class));
        }
    }
    
    @Nested
    @DisplayName("GET /providers/lookup")
    class LookupProviderTests {
        
        @Test
        @DisplayName("should return provider when found")
        void shouldReturnProviderWhenFound() throws Exception {
            // Given
            when(providerService.lookupProvider(any(), eq("StudentPersonal"), eq("default"), eq("context1")))
                    .thenReturn(Optional.of(providerRegistrationDTO));
            
            // When & Then
            mockMvc.perform(get(BASE_PATH + "/lookup")
                    .param("serviceName", "StudentPersonal")
                    .param("zoneId", "default")
                    .param("contextId", "context1"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.provider.id").value(providerRegistrationId.toString()))
                    .andExpect(jsonPath("$.provider.serviceName").value("StudentPersonal"))
                    .andExpect(jsonPath("$.provider.zoneId").value("default"))
                    .andExpect(jsonPath("$.provider.contextId").value("context1"));
            
            verify(providerService).lookupProvider(any(), eq("StudentPersonal"), eq("default"), eq("context1"));
        }
        
        @Test
        @DisplayName("should return 404 when provider not found")
        void shouldReturn404WhenProviderNotFound() throws Exception {
            // Given
            when(providerService.lookupProvider(any(), eq("NonExistentService"), eq("default"), eq("context1")))
                    .thenReturn(Optional.empty());
            
            // When & Then
            mockMvc.perform(get(BASE_PATH + "/lookup")
                    .param("serviceName", "NonExistentService")
                    .param("zoneId", "default")
                    .param("contextId", "context1"))
                    .andExpect(status().isNotFound());
            
            verify(providerService).lookupProvider(any(), eq("NonExistentService"), eq("default"), eq("context1"));
        }
        
        @Test
        @DisplayName("should handle null context correctly")
        void shouldHandleNullContextCorrectly() throws Exception {
            // Given
            when(providerService.lookupProvider(any(), eq("StudentPersonal"), eq("default"), isNull()))
                    .thenReturn(Optional.of(providerRegistrationDTO));
            
            // When & Then
            mockMvc.perform(get(BASE_PATH + "/lookup")
                    .param("serviceName", "StudentPersonal")
                    .param("zoneId", "default"))
                    // contextId parameter omitted
                    .andExpect(status().isOk());
            
            verify(providerService).lookupProvider(any(), eq("StudentPersonal"), eq("default"), isNull());
        }
        
        @Test
        @DisplayName("should return 400 for missing required parameters")
        void shouldReturn400ForMissingRequiredParameters() throws Exception {
            // When & Then - missing serviceName
            mockMvc.perform(get(BASE_PATH + "/lookup")
                    .param("zoneId", "default"))
                    .andExpect(status().isBadRequest());
            
            // When & Then - missing zoneId
            mockMvc.perform(get(BASE_PATH + "/lookup")
                    .param("serviceName", "StudentPersonal"))
                    .andExpect(status().isBadRequest());
            
            verifyNoInteractions(providerService);
        }
        
        @Test
        @DisplayName("should return 400 for invalid service name format")
        void shouldReturn400ForInvalidServiceNameFormat() throws Exception {
            // Given
            when(providerService.lookupProvider(any(), eq("123Invalid"), eq("default"), eq("context1")))
                    .thenThrow(new IllegalArgumentException("Invalid service name format"));
            
            // When & Then
            mockMvc.perform(get(BASE_PATH + "/lookup")
                    .param("serviceName", "123Invalid")
                    .param("zoneId", "default")
                    .param("contextId", "context1"))
                    .andExpect(status().isBadRequest());
            
            verify(providerService).lookupProvider(any(), eq("123Invalid"), eq("default"), eq("context1"));
        }
    }
    
    @Nested
    @DisplayName("GET /providers")
    class ListProviderRegistrationsTests {
        
        @Test
        @DisplayName("should return list of provider registrations")
        void shouldReturnListOfProviderRegistrations() throws Exception {
            // Given
            List<ProviderRegistrationDTO> providerRegistrations = List.of(providerRegistrationDTO);
            when(providerService.listProviderRegistrations(environmentId))
                    .thenReturn(providerRegistrations);
            
            // When & Then
            mockMvc.perform(get(BASE_PATH)
                    .param("environmentId", environmentId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    // D1: the collection is wrapped as {"providers":{"provider":[...]}} rather
                    // than returned as a bare array.
                    .andExpect(jsonPath("$.providers.provider").isArray())
                    .andExpect(jsonPath("$.providers.provider[0].id").value(providerRegistrationId.toString()))
                    .andExpect(jsonPath("$.providers.provider[0].environmentId").value(environmentId.toString()));
            
            verify(providerService).listProviderRegistrations(environmentId);
        }
        
        @Test
        @DisplayName("should return empty list when no registrations found")
        void shouldReturnEmptyListWhenNoRegistrationsFound() throws Exception {
            // Given
            when(providerService.listProviderRegistrations(environmentId))
                    .thenReturn(List.of());
            
            // When & Then
            mockMvc.perform(get(BASE_PATH)
                    .param("environmentId", environmentId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    // D1: an empty collection still carries the envelope, so the wrapper is
                    // present and only the inner array is empty.
                    .andExpect(jsonPath("$.providers.provider").isArray())
                    .andExpect(jsonPath("$.providers.provider").isEmpty());
            
            verify(providerService).listProviderRegistrations(environmentId);
        }
        
        @Test
        @DisplayName("should return 400 for missing environmentId parameter")
        void shouldReturn400ForMissingEnvironmentIdParameter() throws Exception {
            // When & Then
            mockMvc.perform(get(BASE_PATH))
                    .andExpect(status().isBadRequest());
            
            verifyNoInteractions(providerService);
        }
        
        @Test
        @DisplayName("should return 400 for invalid environmentId format")
        void shouldReturn400ForInvalidEnvironmentIdFormat() throws Exception {
            // When & Then
            mockMvc.perform(get(BASE_PATH)
                    .param("environmentId", "invalid-uuid"))
                    .andExpect(status().isBadRequest());
            
            verifyNoInteractions(providerService);
        }
    }
    
    @Nested
    @DisplayName("PUT /providers/{id}")
    class UpdateProviderRegistrationTests {

        private static final String UPDATE_PATH = "/test-domain/test-solution/providers/";

        /**
         * The wrapped, nested form -- identical in shape to POST.
         *
         * <p>Update previously took a flat {@code endpoint} string and a bare
         * {@code List<SupportedRequestType>} while registration took the wrapped form, so a client
         * needed two mental models for one resource. The controller unwraps to the service layer's
         * flat signature, which is asserted here so the unwrapping cannot silently drop a field.</p>
         */
        @Test
        @DisplayName("should accept the wrapped body and pass the unwrapped values to the service")
        void shouldAcceptWrappedBody() throws Exception {
            UUID id = UUID.randomUUID();
            when(providerService.updateProvider(any(), any(), any(UUID.class), any(), any()))
                    .thenReturn(providerRegistrationDTO);

            String body = """
                {
                  "provider": {
                    "endPoint": { "location": "https://api.example.com/updated" },
                    "supportedRequestTypes": {
                      "supportedRequestType": [
                        { "requestType": "IMMEDIATE", "operations": { "operation": ["QUERY", "CREATE"] } }
                      ]
                    }
                  }
                }
                """;

            mockMvc.perform(put(UPDATE_PATH + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provider.id").exists());

            ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<SupportedRequestType>> typesCaptor =
                    ArgumentCaptor.forClass((Class<List<SupportedRequestType>>) (Class<?>) List.class);
            verify(providerService).updateProvider(any(), any(), eq(id),
                    endpointCaptor.capture(), typesCaptor.capture());

            assertThat(endpointCaptor.getValue())
                    .as("the nested endPoint.location must reach the service, not the wrapper")
                    .isEqualTo("https://api.example.com/updated");
            assertThat(typesCaptor.getValue()).hasSize(1);
            assertThat(typesCaptor.getValue().get(0).operations().operation())
                    .as("operations must survive both levels of unwrapping")
                    .containsExactlyInAnyOrder(Operation.QUERY, Operation.CREATE);
        }

        /**
         * The pre-D1 flat body must be rejected outright.
         *
         * <p>This is the assertion that actually pins the change. A test that only sends the wrapped
         * form would keep passing if the wrapper were made optional again -- it proves the new shape is
         * accepted, not that the old one is refused.</p>
         */
        @Test
        @DisplayName("should reject the pre-D1 flat body that is not wrapped under 'provider'")
        void shouldRejectUnwrappedFlatUpdateBody() throws Exception {
            String flatBody = """
                {
                  "endpoint": "https://api.example.com/updated",
                  "supportedRequestTypes": [
                    { "requestType": "IMMEDIATE", "operations": ["QUERY"] }
                  ]
                }
                """;

            mockMvc.perform(put(UPDATE_PATH + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(flatBody))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(providerService);
        }

        /**
         * A wrapper whose supportedRequestType array is empty is rejected by
         * {@code ProviderUpdateBody.isAnyRequestTypeSupported()}, matching registration.
         */
        @Test
        @DisplayName("should reject an empty supportedRequestType array")
        void shouldRejectEmptySupportedRequestTypes() throws Exception {
            String body = """
                {
                  "provider": {
                    "endPoint": { "location": "https://api.example.com/updated" },
                    "supportedRequestTypes": { "supportedRequestType": [] }
                  }
                }
                """;

            mockMvc.perform(put(UPDATE_PATH + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(providerService);
        }

        /**
         * A non-http(s) endpoint is rejected by the body's @AssertTrue, matching registration.
         */
        @Test
        @DisplayName("should reject a non-http endpoint location")
        void shouldRejectNonHttpEndpoint() throws Exception {
            String body = """
                {
                  "provider": {
                    "endPoint": { "location": "ftp://api.example.com/updated" },
                    "supportedRequestTypes": {
                      "supportedRequestType": [
                        { "requestType": "IMMEDIATE", "operations": { "operation": ["QUERY"] } }
                      ]
                    }
                  }
                }
                """;

            mockMvc.perform(put(UPDATE_PATH + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(providerService);
        }

        /**
         * A DELAYED-only provider legitimately has no endpoint, so an absent endPoint is accepted.
         * Control for the two rejection tests above: without it they would also pass against an
         * implementation that rejected every update body.
         */
        @Test
        @DisplayName("should accept an absent endPoint for a DELAYED-only provider")
        void shouldAcceptAbsentEndPointForDelayedOnly() throws Exception {
            when(providerService.updateProvider(any(), any(), any(UUID.class), any(), any()))
                    .thenReturn(providerRegistrationDTO);

            String body = """
                {
                  "provider": {
                    "supportedRequestTypes": {
                      "supportedRequestType": [
                        { "requestType": "DELAYED", "operations": { "operation": ["QUERY"] } }
                      ]
                    }
                  }
                }
                """;

            mockMvc.perform(put(UPDATE_PATH + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /providers/{id}")
    class DeleteProviderRegistrationTests {
        
        @Test
        @DisplayName("should delete provider registration successfully")
        void shouldDeleteProviderRegistrationSuccessfully() throws Exception {
            // Given
            doNothing().when(providerService).deleteProviderRegistration(any(), any(), eq(providerRegistrationId));
            
            // When & Then
            mockMvc.perform(delete(BASE_PATH + "/{id}", providerRegistrationId))
                    .andExpect(status().isNoContent());
            
            verify(providerService).deleteProviderRegistration(any(), any(), eq(providerRegistrationId));
        }
        
        @Test
        @DisplayName("should return 404 when provider registration not found")
        void shouldReturn404WhenProviderRegistrationNotFound() throws Exception {
            // Given
            doThrow(new ProviderRegistrationNotFoundException(providerRegistrationId))
                    .when(providerService).deleteProviderRegistration(any(), any(), eq(providerRegistrationId));
            
            // When & Then
            mockMvc.perform(delete(BASE_PATH + "/{id}", providerRegistrationId))
                    .andExpect(status().isNotFound());
            
            verify(providerService).deleteProviderRegistration(any(), any(), eq(providerRegistrationId));
        }
        
        @Test
        @DisplayName("should return 400 for invalid ID format")
        void shouldReturn400ForInvalidIdFormat() throws Exception {
            // When & Then
            mockMvc.perform(delete(BASE_PATH + "/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());
            
            verifyNoInteractions(providerService);
        }
    }
    
    @Nested
    @DisplayName("Request Validation")
    class RequestValidationTests {
        
        @Test
        @DisplayName("should validate required fields in registration request")
        void shouldValidateRequiredFieldsInRegistrationRequest() throws Exception {
            // Given - request with all null/empty required fields
            String invalidJson = """
                {
                    "environmentId": null,
                    "contextServiceUuid": null,
                    "endpoint": "",
                    "serviceType": null,
                    "supportedRequestTypes": []
                }
                """;
            
            // When & Then
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson))
                    .andExpect(status().isBadRequest());
            
            verifyNoInteractions(providerService);
        }
        
        @Test
        @DisplayName("should validate URL format in registration request")
        void shouldValidateUrlFormatInRegistrationRequest() throws Exception {
            // Given - request with invalid URL
            ProviderRegistrationRequest invalidUrlRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                UUID.randomUUID(),
                ProtocolDTO.of("not-a-url"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
            
            // When & Then
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidUrlRequest)))
                    .andExpect(status().isBadRequest());
            
            verifyNoInteractions(providerService);
        }
        
        @Test
        @DisplayName("should accept valid HTTP and HTTPS URLs")
        void shouldAcceptValidHttpAndHttpsUrls() throws Exception {
            // Given
            when(providerService.registerProvider(any(), any(), any(ProviderRegistrationRequest.class)))
                    .thenReturn(providerRegistrationDTO);
            
            // Test HTTPS URL
            ProviderRegistrationRequest httpsRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                UUID.randomUUID(),
                ProtocolDTO.of("https://api.example.com/students"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
            
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(httpsRequest)))
                    .andExpect(status().isCreated());
            
            // Test HTTP URL
            ProviderRegistrationRequest httpRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                UUID.randomUUID(),
                ProtocolDTO.of("http://api.example.com/students"),
                SupportedRequestsDTO.of(IMMEDIATE_ONLY)));
            
            mockMvc.perform(post(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(httpRequest)))
                    .andExpect(status().isCreated());
            
            verify(providerService, times(2)).registerProvider(any(), any(), any(ProviderRegistrationRequest.class));
        }
    }
}

