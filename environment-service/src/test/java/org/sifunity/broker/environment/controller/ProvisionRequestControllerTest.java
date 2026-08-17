// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.AdminRequiredException;
import org.sifunity.broker.environment.exception.ProvisionRequestNotFoundException;
import org.sifunity.broker.environment.exception.ProvisionRequestValidationException;
import org.sifunity.broker.environment.service.ProvisionRequestService;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ProvisionRequestController.
 *
 * <p>Tests REST API endpoints for provision request lifecycle:
 * create, query, delete, and admin resolution.</p>
 */
@WebMvcTest(
    controllers = ProvisionRequestController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
)
@Import(TestSecurityConfiguration.class)
@TestPropertySource(properties = {"observability.metrics.enabled=false", "secrets.backend.type=plaintext"})
@ActiveProfiles("disable-security")
class ProvisionRequestControllerTest {

    private static final String TEST_DOMAIN_ID = "test-domain";
    private static final String TEST_SOLUTION_ID = "test-solution";
    private static final UUID TEST_ENV_ID = UUID.randomUUID();
    private static final String BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID
            + "/environments/" + TEST_ENV_ID + "/provisionRequests";
    // SIF 3.7 singular-subpath create: POST .../provisionRequests/provisionRequest
    private static final String CREATE_PATH = BASE_PATH + "/provisionRequest";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Main service under test
    @MockBean
    private ProvisionRequestService provisionRequestService;

    // Beans required by other controllers loaded in context
    @MockBean
    private org.sifunity.broker.environment.service.AclService aclService;

    @MockBean
    private org.sifunity.broker.environment.service.AuthorizationService authorizationService;

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

    // ---- Helpers ----

    private Environment mockRequester() {
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        env.setSolutionUuid(UUID.randomUUID());
        return env;
    }

    private ProvisionRequestCreateDTO sampleCreatePayload() {
        RightDTO right = new RightDTO("QUERY", "REQUESTED");
        ProvisionedServiceDTO svc = new ProvisionedServiceDTO(
                "OBJECT", "students", "DEFAULT",
                new ProvisionedServiceRightsDTO(List.of(right)));
        ProvisionedZoneDTO zone = new ProvisionedZoneDTO(
                "SuffolkMiddleSchool",
                new ProvisionedZoneServicesDTO(List.of(svc)));
        return new ProvisionRequestCreateDTO(new ProvisionedZonesDTO(List.of(zone)));
    }

    private ProvisionRequestResponseDTO sampleResponse(UUID id, String completionStatus) {
        RightDTO right = new RightDTO("QUERY", completionStatus != null ? "APPROVED" : "REQUESTED");
        ProvisionedServiceDTO svc = new ProvisionedServiceDTO(
                "OBJECT", "students", "DEFAULT",
                new ProvisionedServiceRightsDTO(List.of(right)));
        ProvisionedZoneDTO zone = new ProvisionedZoneDTO(
                "SuffolkMiddleSchool",
                new ProvisionedZoneServicesDTO(List.of(svc)));
        return new ProvisionRequestResponseDTO(id, completionStatus, new ProvisionedZonesDTO(List.of(zone)));
    }

    // ---- Tests ----

    @Test
    void createProvisionRequest_validPayload_returns201() throws Exception {
        UUID prId = UUID.randomUUID();
        ProvisionRequestResponseDTO response = sampleResponse(prId, "ACCEPTED");

        when(provisionRequestService.createProvisionRequest(
                eq(TEST_ENV_ID), any(ProvisionRequestCreateDTO.class), any(Environment.class)))
                .thenReturn(response);

        mockMvc.perform(post(CREATE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleCreatePayload()))
                .requestAttr("requester", mockRequester()))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(prId.toString()))
                .andExpect(jsonPath("$.completionStatus").value("ACCEPTED"));
    }

    @Test
    void getProvisionRequest_resolved_returns200() throws Exception {
        UUID prId = UUID.randomUUID();
        ProvisionRequestResponseDTO response = sampleResponse(prId, "ACCEPTED");

        when(provisionRequestService.getProvisionRequest(eq(prId), any(Environment.class)))
                .thenReturn(response);

        mockMvc.perform(get(BASE_PATH + "/{id}", prId)
                .requestAttr("requester", mockRequester()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(prId.toString()))
                .andExpect(jsonPath("$.completionStatus").value("ACCEPTED"));
    }

    @Test
    void getProvisionRequest_unresolved_returns202() throws Exception {
        UUID prId = UUID.randomUUID();
        ProvisionRequestResponseDTO response = sampleResponse(prId, null);

        when(provisionRequestService.getProvisionRequest(eq(prId), any(Environment.class)))
                .thenReturn(response);

        mockMvc.perform(get(BASE_PATH + "/{id}", prId)
                .requestAttr("requester", mockRequester()))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(prId.toString()))
                .andExpect(jsonPath("$.completionStatus").doesNotExist());
    }

    @Test
    void getProvisionRequest_notFound_returns404() throws Exception {
        UUID prId = UUID.randomUUID();

        when(provisionRequestService.getProvisionRequest(eq(prId), any(Environment.class)))
                .thenThrow(new ProvisionRequestNotFoundException(prId));

        mockMvc.perform(get(BASE_PATH + "/{id}", prId)
                .requestAttr("requester", mockRequester()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("PROV_002"));
    }

    @Test
    void deleteProvisionRequest_returns204() throws Exception {
        UUID prId = UUID.randomUUID();

        doNothing().when(provisionRequestService)
                .deleteProvisionRequest(eq(prId), any(Environment.class));

        mockMvc.perform(delete(BASE_PATH + "/{id}", prId)
                .requestAttr("requester", mockRequester()))
                .andExpect(status().isNoContent());
    }

    @Test
    void resolveProvisionRequest_nonAdmin_returns403() throws Exception {
        UUID prId = UUID.randomUUID();
        ProvisionRequestResolveDTO resolvePayload = new ProvisionRequestResolveDTO(
                sampleCreatePayload().provisionedZones());

        when(provisionRequestService.resolveProvisionRequest(
                eq(prId), any(ProvisionRequestResolveDTO.class), any(Environment.class)))
                .thenThrow(new AdminRequiredException(TEST_ENV_ID));

        mockMvc.perform(put(BASE_PATH + "/{id}", prId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resolvePayload))
                .requestAttr("requester", mockRequester()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("PROV_004"));
    }

    @Test
    void createProvisionRequest_invalidPayload_returns400() throws Exception {
        when(provisionRequestService.createProvisionRequest(
                eq(TEST_ENV_ID), any(ProvisionRequestCreateDTO.class), any(Environment.class)))
                .thenThrow(new ProvisionRequestValidationException("PROV_001",
                        "provisionedZones must contain at least one provisionedZone"));

        mockMvc.perform(post(CREATE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleCreatePayload()))
                .requestAttr("requester", mockRequester()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.subCode").value("PROV_001"));
    }
}

