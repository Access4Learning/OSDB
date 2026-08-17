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
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.AclEntryUpdateRequest;
import org.sifunity.broker.environment.exception.AclEntryNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateAclEntryException;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.service.AclService;

import org.sifunity.broker.environment.domain.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AclController.
 * 
 * <p>Tests the REST API endpoints for ACL entry management including:
 * <ul>
 *   <li>Creating ACL entries with validation</li>
 *   <li>Listing ACL entries by environment</li>
 *   <li>Updating ACL entry access levels</li>
 *   <li>Deleting ACL entries</li>
 *   <li>Error handling and HTTP status codes</li>
 * </ul>
 * 
 * <p>Uses MockMvc for testing the web layer in isolation with mocked service layer.</p>
 */
@WebMvcTest(
    controllers = AclController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
)
@Import(TestSecurityConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
    "observability.metrics.enabled=false",
    "secrets.backend.type=plaintext"
})
@org.springframework.test.context.ActiveProfiles("disable-security")
class AclControllerTest {
    
    private static final String TEST_DOMAIN_ID = "test-domain";
    private static final String TEST_SOLUTION_ID = "test-solution";
    private static final String BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID + "/acl-entries";
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private AclService aclService;
    
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
    
    @Test
    void createAclEntry_Success() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        UUID aclEntryId = UUID.randomUUID();
        String serviceName = "StudentService";
        String zoneId = "DEFAULT";
        String contextId = "DEFAULT";
        AccessType accessType = AccessType.QUERY;
        AccessLevel accessLevel = AccessLevel.APPROVED;
        
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                environmentId, serviceName, zoneId, contextId, accessType, accessLevel);
        
        AclEntryDTO responseDto = new AclEntryDTO(
                aclEntryId, environmentId, serviceName, zoneId, contextId, accessType, accessLevel);
        
        when(aclService.createAclEntry(anyString(), anyString(), any(AclEntryCreateRequest.class), any(), anyBoolean()))
                .thenReturn(responseDto);
        
        // When & Then
        mockMvc.perform(post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(aclEntryId.toString()))
                .andExpect(jsonPath("$.environmentId").value(environmentId.toString()))
                .andExpect(jsonPath("$.serviceName").value(serviceName))
                .andExpect(jsonPath("$.zoneId").value(zoneId))
                .andExpect(jsonPath("$.contextId").value(contextId))
                .andExpect(jsonPath("$.accessType").value(accessType.toString()))
                .andExpect(jsonPath("$.accessLevel").value(accessLevel.toString()));
    }
    
    @Test
    void createAclEntry_ValidationError_MissingEnvironmentId() throws Exception {
        // Given - zoneId and contextId are @NotBlank so use valid values, but null environmentId
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                null, "StudentService", "DEFAULT", "DEFAULT", 
                AccessType.QUERY, AccessLevel.APPROVED);
        
        // When & Then
        mockMvc.perform(post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("VAL_001"))
                .andExpect(jsonPath("$.error.message").value("Validation failed"));
    }
    
    @Test
    void createAclEntry_ValidationError_BlankServiceName() throws Exception {
        // Given
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                UUID.randomUUID(), "", "DEFAULT", "DEFAULT", 
                AccessType.QUERY, AccessLevel.APPROVED);
        
        // When & Then
        mockMvc.perform(post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("VAL_001"))
                .andExpect(jsonPath("$.error.message").value("Validation failed"));
    }
    
    @Test
    void createAclEntry_ValidationError_MissingAccessType() throws Exception {
        // Given
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                UUID.randomUUID(), "StudentService", "DEFAULT", "DEFAULT", 
                null, AccessLevel.APPROVED);
        
        // When & Then
        mockMvc.perform(post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("VAL_001"))
                .andExpect(jsonPath("$.error.message").value("Validation failed"));
    }
    
    @Test
    void createAclEntry_EnvironmentNotFound() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                environmentId, "StudentService", "DEFAULT", "DEFAULT", 
                AccessType.QUERY, AccessLevel.APPROVED);
        
        when(aclService.createAclEntry(anyString(), anyString(), any(AclEntryCreateRequest.class), any(), anyBoolean()))
                .thenThrow(new EnvironmentNotFoundException(environmentId));
        
        // When & Then
        mockMvc.perform(post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("ENV_002"));
    }
    
    @Test
    void createAclEntry_DuplicateEntry() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        String serviceName = "StudentService";
        AccessType accessType = AccessType.QUERY;
        
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                environmentId, serviceName, "DEFAULT", "DEFAULT", accessType, AccessLevel.APPROVED);
        
        when(aclService.createAclEntry(anyString(), anyString(), any(AclEntryCreateRequest.class), any(), anyBoolean()))
                .thenThrow(new DuplicateAclEntryException(environmentId, serviceName, null, accessType));
        
        // When & Then
        mockMvc.perform(post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("ACL_002"));
    }
    
    @Test
    void listAclEntries_Success() throws Exception {
        // Given - non-admin requester with matching environmentId
        UUID environmentId = UUID.randomUUID();
        UUID aclEntry1Id = UUID.randomUUID();
        UUID aclEntry2Id = UUID.randomUUID();
        
        Environment requester = new Environment();
        requester.setId(environmentId);
        requester.setSolutionUuid(UUID.randomUUID());
        
        when(authorizationService.isAdmin(requester)).thenReturn(false);
        
        List<AclEntryDTO> aclEntries = Arrays.asList(
                new AclEntryDTO(aclEntry1Id, environmentId, "StudentService", "DEFAULT", "DEFAULT", 
                        AccessType.QUERY, AccessLevel.APPROVED),
                new AclEntryDTO(aclEntry2Id, environmentId, "TeacherService", null, null, 
                        AccessType.PROVIDE, AccessLevel.REQUESTED)
        );
        
        when(aclService.listAclEntries(environmentId)).thenReturn(aclEntries);
        
        // When & Then
        mockMvc.perform(get(BASE_PATH)
                .param("environmentId", environmentId.toString())
                .requestAttr("requester", requester))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(aclEntry1Id.toString()))
                .andExpect(jsonPath("$[0].serviceName").value("StudentService"))
                .andExpect(jsonPath("$[0].accessType").value("QUERY"))
                .andExpect(jsonPath("$[0].accessLevel").value("APPROVED"))
                .andExpect(jsonPath("$[1].id").value(aclEntry2Id.toString()))
                .andExpect(jsonPath("$[1].serviceName").value("TeacherService"))
                .andExpect(jsonPath("$[1].accessType").value("PROVIDE"))
                .andExpect(jsonPath("$[1].accessLevel").value("REQUESTED"))
                .andExpect(jsonPath("$[1].zoneId").isEmpty())
                .andExpect(jsonPath("$[1].contextId").isEmpty());
    }
    
    @Test
    void listAclEntries_EmptyList() throws Exception {
        // Given - non-admin requester with matching environmentId
        UUID environmentId = UUID.randomUUID();
        
        Environment requester = new Environment();
        requester.setId(environmentId);
        requester.setSolutionUuid(UUID.randomUUID());
        
        when(authorizationService.isAdmin(requester)).thenReturn(false);
        when(aclService.listAclEntries(environmentId)).thenReturn(List.of());
        
        // When & Then
        mockMvc.perform(get(BASE_PATH)
                .param("environmentId", environmentId.toString())
                .requestAttr("requester", requester))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));
    }
    
    @Test
    void listAclEntries_MissingEnvironmentIdParameter() throws Exception {
        // Given - admin caller without environmentId expects 400
        UUID adminId = UUID.randomUUID();
        
        Environment admin = new Environment();
        admin.setId(adminId);
        admin.setSolutionUuid(UUID.randomUUID());
        
        when(authorizationService.isAdmin(admin)).thenReturn(true);
        
        // When & Then
        mockMvc.perform(get(BASE_PATH)
                .requestAttr("requester", admin))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("ACL_004"));
    }
    
    @Test
    void listAclEntries_InvalidEnvironmentIdFormat() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_PATH)
                .param("environmentId", "invalid-uuid"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void updateAclEntry_Success() throws Exception {
        // Given
        UUID aclEntryId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        AccessLevel newAccessLevel = AccessLevel.REJECTED;
        
        AclEntryUpdateRequest request = new AclEntryUpdateRequest(newAccessLevel);
        
        AclEntryDTO updatedDto = new AclEntryDTO(
                aclEntryId, environmentId, "StudentService", "DEFAULT", "DEFAULT", 
                AccessType.QUERY, newAccessLevel);
        
        Environment adminEnv = new Environment();
        adminEnv.setId(UUID.randomUUID());
        when(authorizationService.isAdmin(adminEnv)).thenReturn(true);
        
        when(aclService.updateAclEntry(eq(aclEntryId), any(AclEntryUpdateRequest.class)))
                .thenReturn(updatedDto);
        
        // When & Then
        mockMvc.perform(put(BASE_PATH + "/{id}", aclEntryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("requester", adminEnv))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(aclEntryId.toString()))
                .andExpect(jsonPath("$.accessLevel").value(newAccessLevel.toString()));
    }
    
    @Test
    void updateAclEntry_ValidationError_MissingAccessLevel() throws Exception {
        // Given
        UUID aclEntryId = UUID.randomUUID();
        AclEntryUpdateRequest request = new AclEntryUpdateRequest(null);
        
        Environment adminEnv = new Environment();
        adminEnv.setId(UUID.randomUUID());
        when(authorizationService.isAdmin(adminEnv)).thenReturn(true);
        
        // When & Then
        mockMvc.perform(put(BASE_PATH + "/{id}", aclEntryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("requester", adminEnv))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("VAL_001"))
                .andExpect(jsonPath("$.error.message").value("Validation failed"));
    }
    
    @Test
    void updateAclEntry_NotFound() throws Exception {
        // Given
        UUID aclEntryId = UUID.randomUUID();
        AclEntryUpdateRequest request = new AclEntryUpdateRequest(AccessLevel.APPROVED);
        
        Environment adminEnv = new Environment();
        adminEnv.setId(UUID.randomUUID());
        when(authorizationService.isAdmin(adminEnv)).thenReturn(true);
        
        when(aclService.updateAclEntry(eq(aclEntryId), any(AclEntryUpdateRequest.class)))
                .thenThrow(new AclEntryNotFoundException(aclEntryId));
        
        // When & Then
        mockMvc.perform(put(BASE_PATH + "/{id}", aclEntryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("requester", adminEnv))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("ACL_001"));
    }
    
    @Test
    void updateAclEntry_InvalidIdFormat() throws Exception {
        // Given
        AclEntryUpdateRequest request = new AclEntryUpdateRequest(AccessLevel.APPROVED);
        
        Environment adminEnv = new Environment();
        adminEnv.setId(UUID.randomUUID());
        when(authorizationService.isAdmin(adminEnv)).thenReturn(true);
        
        // When & Then
        mockMvc.perform(put(BASE_PATH + "/{id}", "invalid-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("requester", adminEnv))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void deleteAclEntry_Success() throws Exception {
        // Given
        UUID aclEntryId = UUID.randomUUID();
        
        Environment adminEnv = new Environment();
        adminEnv.setId(UUID.randomUUID());
        when(authorizationService.isAdmin(adminEnv)).thenReturn(true);
        
        // When & Then
        mockMvc.perform(delete(BASE_PATH + "/{id}", aclEntryId)
                .requestAttr("requester", adminEnv))
                .andExpect(status().isNoContent());
    }
    
    @Test
    void deleteAclEntry_NotFound() throws Exception {
        // Given
        UUID aclEntryId = UUID.randomUUID();
        
        Environment adminEnv = new Environment();
        adminEnv.setId(UUID.randomUUID());
        when(authorizationService.isAdmin(adminEnv)).thenReturn(true);
        
        doThrow(new AclEntryNotFoundException(aclEntryId))
                .when(aclService).deleteAclEntry(aclEntryId);
        
        // When & Then
        mockMvc.perform(delete(BASE_PATH + "/{id}", aclEntryId)
                .requestAttr("requester", adminEnv))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("ACL_001"));
    }
    
    @Test
    void deleteAclEntry_InvalidIdFormat() throws Exception {
        // Given
        Environment adminEnv = new Environment();
        adminEnv.setId(UUID.randomUUID());
        when(authorizationService.isAdmin(adminEnv)).thenReturn(true);
        
        // When & Then
        mockMvc.perform(delete(BASE_PATH + "/{id}", "invalid-uuid")
                .requestAttr("requester", adminEnv))
                .andExpect(status().isBadRequest());
    }
}
