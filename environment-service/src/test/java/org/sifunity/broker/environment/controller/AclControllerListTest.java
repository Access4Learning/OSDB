// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for the modified {@link AclController#listAclEntries} endpoint
 * covering session-scoped ACL listing scenarios.
 *
 * <p>Tests admin vs non-admin paths, environment isolation, and error responses.</p>
 */
@WebMvcTest(
    controllers = AclController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@Import(TestSecurityConfiguration.class)
@TestPropertySource(properties = {"observability.metrics.enabled=false", "secrets.backend.type=plaintext"})
@ActiveProfiles("disable-security")
class AclControllerListTest {

    private static final String BASE_PATH = "/test-domain/test-solution/acl-entries";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AclService aclService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private org.sifunity.broker.environment.service.ProvisionRequestService provisionRequestService;

    // Mock beans required by the application context
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

    private Environment createEnvironment(UUID id) {
        Environment env = new Environment();
        env.setId(id);
        env.setSolutionUuid(UUID.randomUUID());
        return env;
    }

    @Test
    void testNonAdminWithoutEnvironmentId_Returns200WithOwnEntries() throws Exception {
        UUID envId = UUID.randomUUID();
        Environment requester = createEnvironment(envId);

        List<AclEntryDTO> entries = Arrays.asList(
            new AclEntryDTO(UUID.randomUUID(), envId, "StudentService", "DEFAULT", "DEFAULT", AccessType.QUERY, AccessLevel.APPROVED),
            new AclEntryDTO(UUID.randomUUID(), envId, "TeacherService", null, null, AccessType.PROVIDE, AccessLevel.REQUESTED)
        );

        when(authorizationService.isAdmin(requester)).thenReturn(false);
        when(aclService.listAclEntries(envId)).thenReturn(entries);

        mockMvc.perform(get(BASE_PATH).requestAttr("requester", requester))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].environmentId").value(envId.toString()))
                .andExpect(jsonPath("$[1].environmentId").value(envId.toString()));
    }

    @Test
    void testNonAdminWithOwnEnvironmentId_Returns200WithOwnEntries() throws Exception {
        UUID envId = UUID.randomUUID();
        Environment requester = createEnvironment(envId);

        List<AclEntryDTO> entries = List.of(
            new AclEntryDTO(UUID.randomUUID(), envId, "StudentService", null, null, AccessType.QUERY, AccessLevel.APPROVED)
        );

        when(authorizationService.isAdmin(requester)).thenReturn(false);
        when(aclService.listAclEntries(envId)).thenReturn(entries);

        mockMvc.perform(get(BASE_PATH)
                .param("environmentId", envId.toString())
                .requestAttr("requester", requester))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].environmentId").value(envId.toString()));
    }

    @Test
    void testNonAdminWithDifferentEnvironmentId_Returns403() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Environment requester = createEnvironment(requesterId);

        when(authorizationService.isAdmin(requester)).thenReturn(false);

        mockMvc.perform(get(BASE_PATH)
                .param("environmentId", targetId.toString())
                .requestAttr("requester", requester))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error.subCode").value("ACL_003"));
    }

    @Test
    void testAdminWithEnvironmentId_Returns200WithTargetEntries() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Environment admin = createEnvironment(adminId);

        List<AclEntryDTO> entries = List.of(
            new AclEntryDTO(UUID.randomUUID(), targetId, "StudentService", null, null, AccessType.QUERY, AccessLevel.APPROVED)
        );

        when(authorizationService.isAdmin(admin)).thenReturn(true);
        when(aclService.listAclEntries(targetId)).thenReturn(entries);

        mockMvc.perform(get(BASE_PATH)
                .param("environmentId", targetId.toString())
                .requestAttr("requester", admin))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].environmentId").value(targetId.toString()));
    }

    @Test
    void testAdminWithoutEnvironmentId_Returns400() throws Exception {
        UUID adminId = UUID.randomUUID();
        Environment admin = createEnvironment(adminId);

        when(authorizationService.isAdmin(admin)).thenReturn(true);

        mockMvc.perform(get(BASE_PATH).requestAttr("requester", admin))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error.subCode").value("ACL_004"));
    }

    @Test
    void testNonAdminWithNoAclEntries_Returns200WithEmptyList() throws Exception {
        UUID envId = UUID.randomUUID();
        Environment requester = createEnvironment(envId);

        when(authorizationService.isAdmin(requester)).thenReturn(false);
        when(aclService.listAclEntries(envId)).thenReturn(List.of());

        mockMvc.perform(get(BASE_PATH).requestAttr("requester", requester))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testErrorResponseContainsRequiredFields() throws Exception {
        // Test 403 error response fields
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Environment requester = createEnvironment(requesterId);

        when(authorizationService.isAdmin(requester)).thenReturn(false);

        mockMvc.perform(get(BASE_PATH)
                .param("environmentId", targetId.toString())
                .requestAttr("requester", requester))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.subCode").value("ACL_003"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.error.scope").value("BROKER"))
                .andExpect(jsonPath("$.error.timestamp").doesNotExist());

        // Test 400 error response fields
        UUID adminId = UUID.randomUUID();
        Environment admin = createEnvironment(adminId);

        when(authorizationService.isAdmin(admin)).thenReturn(true);

        mockMvc.perform(get(BASE_PATH).requestAttr("requester", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.subCode").value("ACL_004"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.error.scope").value("BROKER"))
                .andExpect(jsonPath("$.error.timestamp").doesNotExist());
    }
}

