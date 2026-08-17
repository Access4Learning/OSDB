// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;
import org.sifunity.broker.environment.dto.PermissionCheckRequest;
import org.sifunity.broker.environment.dto.PermissionCheckResponse;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.dto.TokenValidationResponse;
import org.sifunity.broker.environment.service.AuthenticationService;
import org.sifunity.broker.environment.service.AuthorizationService;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for InternalAuthController.
 * 
 * <p>Tests the REST endpoints for internal authentication and authorization APIs
 * used by the API Gateway. Focuses on request/response mapping, validation,
 * and error handling.</p>
 */
@WebMvcTest(
    controllers = InternalAuthController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
)
@Import(TestSecurityConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
    "observability.metrics.enabled=false",
    "secrets.backend.type=plaintext",
    "broker.internal-service-token=test-internal-token"
})
class InternalAuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private AuthenticationService authenticationService;
    
    @MockBean
    private AuthorizationService authorizationService;
    
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
    @MockBean
    private org.sifunity.broker.environment.repository.XQueryTemplateRepository xQueryTemplateRepository;
    
    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Test
    void validateToken_WithValidRequest_ReturnsEnvironmentDetails() throws Exception {
        // Given
        TokenValidationRequest request = new TokenValidationRequest(
                "test-session-token",
                "2024-01-01T12:00:00Z",
                "test-hash"
        );
        
        TokenValidationResponse expectedResponse = new TokenValidationResponse(
                UUID.randomUUID(),
                "TestApp",
                EnvironmentType.BROKERED,
                "default-zone",
                null,
                null,
                null,
                null
        );
        
        when(authenticationService.validateToken(any(TokenValidationRequest.class)))
                .thenReturn(expectedResponse);
        
        // When & Then
        mockMvc.perform(post("/internal/validate-token")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.environmentId").value(expectedResponse.environmentId().toString()))
                .andExpect(jsonPath("$.applicationName").value("TestApp"))
                .andExpect(jsonPath("$.type").value("BROKERED"))
                .andExpect(jsonPath("$.defaultZoneId").value("default-zone"));
    }
    
    @Test
    void validateToken_WithInvalidToken_ReturnsUnauthorized() throws Exception {
        // Given
        TokenValidationRequest request = new TokenValidationRequest(
                "invalid-token",
                "2024-01-01T12:00:00Z",
                "invalid-hash"
        );
        
        when(authenticationService.validateToken(any(TokenValidationRequest.class)))
                .thenThrow(new InvalidTokenException("SessionToken does not exist"));
        
        // When & Then
        mockMvc.perform(post("/internal/validate-token")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void validateToken_WithMissingSessionToken_ReturnsBadRequest() throws Exception {
        // Given
        TokenValidationRequest request = new TokenValidationRequest(
                "", // Empty session token
                "2024-01-01T12:00:00Z",
                "test-hash"
        );
        
        // When & Then
        mockMvc.perform(post("/internal/validate-token")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void validateToken_WithMissingTimestamp_ReturnsBadRequest() throws Exception {
        // Given
        TokenValidationRequest request = new TokenValidationRequest(
                "test-session-token",
                "", // Empty timestamp
                "test-hash"
        );
        
        // When & Then
        mockMvc.perform(post("/internal/validate-token")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void validateToken_WithMissingHash_ReturnsBadRequest() throws Exception {
        // Given
        TokenValidationRequest request = new TokenValidationRequest(
                "test-session-token",
                "2024-01-01T12:00:00Z",
                "" // Empty hash
        );
        
        // When & Then
        mockMvc.perform(post("/internal/validate-token")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void checkPermission_WithValidRequest_ReturnsPermissionGranted() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId,
                "TestService",
                "test-zone",
                "test-context",
                AccessType.QUERY
        );
        
        PermissionCheckResponse expectedResponse = new PermissionCheckResponse(true);
        
        when(authorizationService.checkPermission(any(PermissionCheckRequest.class)))
                .thenReturn(expectedResponse);
        
        // When & Then
        mockMvc.perform(post("/internal/check-permission")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.granted").value(true));
    }
    
    @Test
    void checkPermission_WithValidRequest_ReturnsPermissionDenied() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId,
                "TestService",
                "test-zone",
                "test-context",
                AccessType.QUERY
        );
        
        PermissionCheckResponse expectedResponse = new PermissionCheckResponse(false);
        
        when(authorizationService.checkPermission(any(PermissionCheckRequest.class)))
                .thenReturn(expectedResponse);
        
        // When & Then
        mockMvc.perform(post("/internal/check-permission")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.granted").value(false));
    }
    
    @Test
    void checkPermission_WithMissingEnvironmentId_ReturnsBadRequest() throws Exception {
        // Given
        PermissionCheckRequest request = new PermissionCheckRequest(
                null, // Missing environment ID
                "TestService",
                "test-zone",
                "test-context",
                AccessType.QUERY
        );
        
        // When & Then
        mockMvc.perform(post("/internal/check-permission")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void checkPermission_WithMissingServiceName_ReturnsBadRequest() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId,
                "", // Empty service name
                "test-zone",
                "test-context",
                AccessType.QUERY
        );
        
        // When & Then
        mockMvc.perform(post("/internal/check-permission")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void checkPermission_WithMissingZoneId_ReturnsBadRequest() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId,
                "TestService",
                "", // Empty zone ID
                "test-context",
                AccessType.QUERY
        );
        
        // When & Then
        mockMvc.perform(post("/internal/check-permission")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void checkPermission_WithMissingAccessType_ReturnsBadRequest() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId,
                "TestService",
                "test-zone",
                "test-context",
                null // Missing access type
        );
        
        // When & Then
        mockMvc.perform(post("/internal/check-permission")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void checkPermission_WithNullContextId_IsValid() throws Exception {
        // Given - contextId is optional and can be null
        UUID environmentId = UUID.randomUUID();
        PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId,
                "TestService",
                "test-zone",
                null, // Null context ID is valid
                AccessType.QUERY
        );
        
        PermissionCheckResponse expectedResponse = new PermissionCheckResponse(true);
        
        when(authorizationService.checkPermission(any(PermissionCheckRequest.class)))
                .thenReturn(expectedResponse);
        
        // When & Then
        mockMvc.perform(post("/internal/check-permission")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.granted").value(true));
    }

    // ========== resolveContext Tests ==========

    @Test
    void resolveContext_WithValidHierarchy_ReturnsContextUuid() throws Exception {
        // Given — full domain → solution → zone → context hierarchy exists
        UUID domainUuid = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        UUID zoneUuid = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();

        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId("testDomain");

        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setDomainUuid(domainUuid);
        solution.setSolutionId("testSolution");

        Zone zone = new Zone();
        zone.setId(zoneUuid);
        zone.setZoneId("testZone");

        Context context = new Context();
        context.setId(contextUuid);
        context.setContextId("DEFAULT");

        when(domainRepository.findByDomainId("testDomain")).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, "testSolution")).thenReturn(Optional.of(solution));
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, "testZone")).thenReturn(Optional.of(zone));
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, "DEFAULT")).thenReturn(Optional.of(context));

        // When & Then
        mockMvc.perform(get("/internal/contexts/resolve")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .param("domainId", "testDomain")
                        .param("solutionId", "testSolution")
                        .param("zoneId", "testZone")
                        .param("contextId", "DEFAULT"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.contextUuid").value(contextUuid.toString()));
    }

    @Test
    void resolveContext_WithInvalidDomain_Returns404() throws Exception {
        // Given — domain does not exist
        when(domainRepository.findByDomainId("nonExistent")).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/internal/contexts/resolve")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .param("domainId", "nonExistent")
                        .param("solutionId", "testSolution")
                        .param("zoneId", "testZone")
                        .param("contextId", "DEFAULT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveContext_WithInvalidZone_Returns404() throws Exception {
        // Given — domain and solution exist, but zone does not
        UUID domainUuid = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();

        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId("testDomain");

        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setDomainUuid(domainUuid);
        solution.setSolutionId("testSolution");

        when(domainRepository.findByDomainId("testDomain")).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, "testSolution")).thenReturn(Optional.of(solution));
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, "badZone")).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/internal/contexts/resolve")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .param("domainId", "testDomain")
                        .param("solutionId", "testSolution")
                        .param("zoneId", "badZone")
                        .param("contextId", "DEFAULT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveContext_WithInvalidContext_Returns404() throws Exception {
        // Given — domain, solution, zone exist but context does not
        UUID domainUuid = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        UUID zoneUuid = UUID.randomUUID();

        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId("testDomain");

        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setDomainUuid(domainUuid);
        solution.setSolutionId("testSolution");

        Zone zone = new Zone();
        zone.setId(zoneUuid);
        zone.setZoneId("testZone");

        when(domainRepository.findByDomainId("testDomain")).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, "testSolution")).thenReturn(Optional.of(solution));
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, "testZone")).thenReturn(Optional.of(zone));
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, "NONEXISTENT")).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/internal/contexts/resolve")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .param("domainId", "testDomain")
                        .param("solutionId", "testSolution")
                        .param("zoneId", "testZone")
                        .param("contextId", "NONEXISTENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveContext_WithDefaultContextIdParam_DefaultsToDefault() throws Exception {
        // Given — contextId param omitted, should default to "DEFAULT"
        UUID domainUuid = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        UUID zoneUuid = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();

        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId("testDomain");

        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setDomainUuid(domainUuid);
        solution.setSolutionId("testSolution");

        Zone zone = new Zone();
        zone.setId(zoneUuid);
        zone.setZoneId("testZone");

        Context context = new Context();
        context.setId(contextUuid);
        context.setContextId("DEFAULT");

        when(domainRepository.findByDomainId("testDomain")).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, "testSolution")).thenReturn(Optional.of(solution));
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, "testZone")).thenReturn(Optional.of(zone));
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, "DEFAULT")).thenReturn(Optional.of(context));

        // When & Then — omit contextId param, endpoint defaults to "DEFAULT"
        mockMvc.perform(get("/internal/contexts/resolve")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .param("domainId", "testDomain")
                        .param("solutionId", "testSolution")
                        .param("zoneId", "testZone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextUuid").value(contextUuid.toString()));
    }
}
