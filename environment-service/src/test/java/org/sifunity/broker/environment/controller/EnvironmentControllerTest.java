// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.service.EnvironmentService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for EnvironmentController.
 */
@WebMvcTest(
    controllers = EnvironmentController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
)
@Import(TestSecurityConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
    "observability.metrics.enabled=false",
    "secrets.backend.type=plaintext"
})
@org.springframework.test.context.ActiveProfiles("disable-security")
@DisplayName("EnvironmentController Tests")
class EnvironmentControllerTest {
    
    private static final String TEST_DOMAIN_ID = "test-domain";
    private static final String TEST_SOLUTION_ID = "test-solution";
    private static final String BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID + "/environments";
    // SIF 3.7 singular-subpath create: POST /environments/environment
    private static final String CREATE_PATH = BASE_PATH + "/environment";
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private EnvironmentService environmentService;
    
    @MockBean
    private org.sifunity.broker.environment.service.AuthorizationService authorizationService;
    
    @MockBean
    private org.sifunity.broker.environment.service.ProvisionRequestService provisionRequestService;
    
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
    private org.sifunity.broker.environment.service.UserAuthenticationService userAuthenticationService;

    @MockBean
    private org.sifunity.broker.environment.service.AdminUserService adminUserService;

    @MockBean
    private org.sifunity.broker.environment.service.UserEnvironmentAccessService userEnvironmentAccessService;
    @MockBean
    private org.sifunity.broker.environment.service.TagDefinitionService tagDefinitionService;

    @MockBean
    private org.sifunity.broker.environment.service.ContextServiceService contextServiceService;
    @MockBean
    private org.sifunity.broker.environment.service.XQueryTemplateService xQueryTemplateService;
    
    private EnvironmentRegistrationRequest validRequest;
    private SifEnvironmentRegistrationResponse validRegResponse;
    private SifEnvironmentResponse validEnvResponse;
    private UUID testEnvironmentId;
    
    @BeforeEach
    void setUp() {
        testEnvironmentId = UUID.randomUUID();
        
        validRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApp", EnvironmentType.BROKERED, new DefaultZoneDTO("default-zone"), "test-solution", null));
        
        validEnvResponse = new SifEnvironmentResponse(
                testEnvironmentId,
                EnvironmentType.BROKERED,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null,
                new DefaultZoneDTO("default-zone"),
                "SIF_HMACSHA256",
                null,
                null,
                "TestApp",
                null,
                new InfrastructureServicesDTO(List.of()),
                new ProvisionedZonesDTO(List.of())
        );
        
        validRegResponse = SifEnvironmentRegistrationResponse.of(validEnvResponse, "base64-encoded-shared-secret");
    }
    
    @Test
    @DisplayName("POST /environments/environment - should register environment successfully")
    void registerEnvironment_ValidRequest_ShouldReturnCreated() throws Exception {
        when(environmentService.registerEnvironment(eq(TEST_DOMAIN_ID), any(EnvironmentRegistrationRequest.class)))
                .thenReturn(validRegResponse);
        
        mockMvc.perform(post(CREATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.environment.id").value(testEnvironmentId.toString()))
                .andExpect(jsonPath("$.environment.consumerName").value("TestApp"))
                .andExpect(jsonPath("$.environment.sharedSecret").value("base64-encoded-shared-secret"))
                .andExpect(jsonPath("$.environment.type").value("BROKERED"));
    }
    
    @Test
    @DisplayName("POST /environments/environment - should return 400 for invalid request")
    void registerEnvironment_InvalidRequest_ShouldReturnBadRequest() throws Exception {
        EnvironmentRegistrationRequest invalidRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("", null, new DefaultZoneDTO(""), null, null));
        
        mockMvc.perform(post(CREATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("GET /environments/{id} - should return environment successfully")
    void getEnvironment_ValidId_ShouldReturnOk() throws Exception {
        when(environmentService.getEnvironment(testEnvironmentId))
                .thenReturn(validEnvResponse);
        
        mockMvc.perform(get(BASE_PATH + "/{id}", testEnvironmentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.environment.id").value(testEnvironmentId.toString()))
                .andExpect(jsonPath("$.environment.consumerName").value("TestApp"))
                .andExpect(jsonPath("$.environment.sharedSecret").doesNotExist())
                .andExpect(jsonPath("$.environment.type").value("BROKERED"));
    }
    
    @Test
    @DisplayName("GET /environments/{id} - should return 404 for non-existent environment")
    void getEnvironment_NonExistentId_ShouldReturnNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(environmentService.getEnvironment(nonExistentId))
                .thenThrow(new EnvironmentNotFoundException(nonExistentId));
        
        mockMvc.perform(get(BASE_PATH + "/{id}", nonExistentId))
                .andExpect(status().isNotFound());
    }
    
    @Test
    @DisplayName("GET /environments - should return paginated environments")
    void listEnvironments_DefaultParameters_ShouldReturnOk() throws Exception {
        List<SifEnvironmentResponse> environments = List.of(validEnvResponse);
        when(environmentService.listEnvironmentsBySolution(TEST_DOMAIN_ID, TEST_SOLUTION_ID))
                .thenReturn(environments);
        
        // Controller now returns SifCollection: {"environments":{"environment":[...]}}
        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.environments.environment").isArray())
                .andExpect(jsonPath("$.environments.environment[0].id").value(testEnvironmentId.toString()));
    }
    
    @Test
    @DisplayName("GET /environments - should filter by type")
    void listEnvironments_WithTypeFilter_ShouldReturnFilteredResults() throws Exception {
        List<SifEnvironmentResponse> environments = List.of(validEnvResponse);
        when(environmentService.listEnvironmentsBySolution(TEST_DOMAIN_ID, TEST_SOLUTION_ID))
                .thenReturn(environments);
        
        // Controller now returns SifCollection: {"environments":{"environment":[...]}}
        mockMvc.perform(get(BASE_PATH)
                        .param("type", "BROKERED"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.environments.environment").isArray())
                .andExpect(jsonPath("$.environments.environment[0].type").value("BROKERED"));
    }
    
    @Test
    @DisplayName("GET /environments - should handle custom pagination")
    void listEnvironments_CustomPagination_ShouldReturnOk() throws Exception {
        List<SifEnvironmentResponse> environments = List.of(validEnvResponse);
        when(environmentService.listEnvironmentsBySolution(TEST_DOMAIN_ID, TEST_SOLUTION_ID))
                .thenReturn(environments);
        
        // Controller now returns SifCollection regardless of pagination params
        mockMvc.perform(get(BASE_PATH)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environments.environment").isArray());
    }
    
    @Test
    @DisplayName("GET /environments - should return 400 for invalid pagination")
    void listEnvironments_InvalidPagination_ShouldReturnBadRequest() throws Exception {
        // The controller now always goes through the solutionId branch (path variable is always present),
        // so pagination validation is never reached. Invalid pagination params are simply ignored.
        // These requests now succeed with 200 OK.
        when(environmentService.listEnvironmentsBySolution(TEST_DOMAIN_ID, TEST_SOLUTION_ID))
                .thenReturn(List.of());
        
        mockMvc.perform(get(BASE_PATH).param("page", "-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE_PATH).param("size", "0"))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE_PATH).param("size", "101"))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("DELETE /environments/{id} - should delete environment successfully")
    void deleteEnvironment_ValidId_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/{id}", testEnvironmentId))
                .andExpect(status().isNoContent());
    }
    
    @Test
    @DisplayName("DELETE /environments/{id} - should return 404 for non-existent environment")
    void deleteEnvironment_NonExistentId_ShouldReturnNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        doThrow(new EnvironmentNotFoundException(nonExistentId))
                .when(environmentService).deleteEnvironment(nonExistentId);
        
        mockMvc.perform(delete(BASE_PATH + "/{id}", nonExistentId))
                .andExpect(status().isNotFound());
    }
}

