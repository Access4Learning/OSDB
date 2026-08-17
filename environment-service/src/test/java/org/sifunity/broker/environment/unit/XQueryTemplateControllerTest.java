// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.unit;

import org.sifunity.broker.environment.dto.XQueryTemplateUpdateBody;
import org.sifunity.broker.environment.dto.XQueryTemplateUpdateRequest;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.sifunity.broker.environment.controller.XQueryTemplateController;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.PermissionCheckResponse;
import org.sifunity.broker.environment.dto.XQueryParameterDTO;
import org.sifunity.broker.environment.dto.XQueryParametersDTO;
import org.sifunity.broker.environment.dto.XQueryTemplateBody;
import org.sifunity.broker.environment.dto.XQueryTemplateCreateRequest;
import org.sifunity.broker.environment.dto.XQueryTemplateDTO;
import org.sifunity.broker.environment.exception.DuplicateQueryTokenException;
import org.sifunity.broker.environment.exception.XQueryTemplateNotFoundException;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.XQueryTemplateService;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link XQueryTemplateController}.
 *
 * <p>Uses {@code @WebMvcTest} controller slice to test HTTP request handling,
 * status codes, and SIF envelope response structure without loading the full
 * application context.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6</strong></p>
 */
@WebMvcTest(
    controllers = XQueryTemplateController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
)
@Import(TestSecurityConfiguration.class)
@TestPropertySource(properties = {
    "observability.metrics.enabled=false",
    "secrets.backend.type=plaintext"
})
@ActiveProfiles("disable-security")
@DisplayName("XQueryTemplateController")
class XQueryTemplateControllerTest {

    private static final String DOMAIN_ID = "test-domain";
    private static final String SOLUTION_ID = "test-solution";
    private static final String BASE_PATH = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/xquerys";
    // SIF 3.7 singular-subpath create: POST /xquerys/xquery
    private static final String CREATE_PATH = BASE_PATH + "/xquery";
    private static final String QUERY_TOKEN = "StudentSnapshot";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private XQueryTemplateService xQueryTemplateService;

    @MockBean
    private AuthorizationService authorizationService;

    // Additional beans required by Spring context
    @MockBean
    private org.sifunity.broker.environment.service.ProviderService providerService;
    @MockBean
    private org.sifunity.broker.environment.service.ProvisionRequestService provisionRequestService;
    @MockBean
    private org.sifunity.broker.environment.service.DomainService domainService;
    @MockBean
    private org.sifunity.broker.environment.service.SolutionService solutionService;
    @MockBean
    private org.sifunity.broker.environment.service.EntityTagService entityTagService;
    @MockBean
    private org.sifunity.broker.environment.service.MessageContextCacheService messageContextCacheService;
    @MockBean
    private org.sifunity.broker.environment.service.AlertsService alertsService;
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
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private Environment requester;

    @BeforeEach
    void setUp() {
        requester = new Environment();
        requester.setId(UUID.randomUUID());
        requester.setDefaultZoneId("test-zone");
    }

    private XQueryTemplateDTO sampleDTO() {
        return new XQueryTemplateDTO(
                QUERY_TOKEN,
                "SINGULAR",
                "APPROVED",
                null,
                "Retrieve student snapshot",
                "SELECT * FROM students",
                new XQueryParametersDTO(List.of(new XQueryParameterDTO("studentId", "string", null, "Student ID", true))),
                "urn:sif:student"
        );
    }

    private String createRequestJson() throws Exception {
        var request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                QUERY_TOKEN,
                "SINGULAR",
                "APPROVED",
                null,
                "Retrieve student snapshot",
                "SELECT * FROM students",
                new XQueryParametersDTO(List.of(new XQueryParameterDTO("studentId", "string", null, "Student ID", true))),
                "urn:sif:student"
        ));
        return objectMapper.writeValueAsString(request);
    }

    // ========== POST - Create Template ==========

    @Nested
    @DisplayName("POST /{domainId}/{solutionId}/xquerys/xquery")
    class CreateTemplate {

        /**
         * Requirement 1.1: Create Named Query Template returns 201 Created.
         */
        @Test
        @DisplayName("returns 201 Created on success")
        void createTemplate_success_returns201() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);
            when(xQueryTemplateService.create(eq(DOMAIN_ID), eq(SOLUTION_ID), any(UUID.class), any(XQueryTemplateCreateRequest.class)))
                    .thenReturn(sampleDTO());

            mockMvc.perform(post(CREATE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequestJson())
                            .requestAttr("requester", requester))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.xquery.id").value(QUERY_TOKEN))
                    .andExpect(jsonPath("$.xquery.type").value("SINGULAR"))
                    .andExpect(jsonPath("$.xquery.status").value("APPROVED"))
                    .andExpect(jsonPath("$.xquery.description").value("Retrieve student snapshot"));
        }

        /**
         * Requirement 1.5: Duplicate queryToken in same solution returns 409 Conflict.
         */
        @Test
        @DisplayName("returns 409 Conflict for duplicate queryToken")
        void createTemplate_duplicate_returns409() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);
            when(xQueryTemplateService.create(eq(DOMAIN_ID), eq(SOLUTION_ID), any(UUID.class), any(XQueryTemplateCreateRequest.class)))
                    .thenThrow(new DuplicateQueryTokenException(QUERY_TOKEN, SOLUTION_ID));

            mockMvc.perform(post(CREATE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequestJson())
                            .requestAttr("requester", requester))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.subCode").value("XQUERY_001"));
        }

        /**
         * Requirement 1.6: Non-provider without PROVIDE ACL gets 403 Forbidden.
         */
        @Test
        @DisplayName("returns 403 Forbidden when no PROVIDE access")
        void createTemplate_noProvideAccess_returns403() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(false);
            when(authorizationService.checkPermission(any()))
                    .thenReturn(new PermissionCheckResponse(false));

            mockMvc.perform(post(CREATE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequestJson())
                            .requestAttr("requester", requester))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.subCode").value("XQUERY_003"));
        }

        /**
         * Requirement 2.5: Empty queryToken returns 400 Bad Request.
         */
        @Test
        @DisplayName("returns 400 Bad Request for empty queryToken")
        void createTemplate_emptyToken_returns400() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);
            when(xQueryTemplateService.create(eq(DOMAIN_ID), eq(SOLUTION_ID), any(UUID.class), any(XQueryTemplateCreateRequest.class)))
                    .thenThrow(new IllegalArgumentException("Query token must not be empty"));

            var request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                    "", "SINGULAR", "APPROVED", null, "desc", null, new XQueryParametersDTO(List.of()), null));

            mockMvc.perform(post(CREATE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .requestAttr("requester", requester))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Requirement 2.3: Invalid type returns 400 Bad Request.
         */
        @Test
        @DisplayName("returns 400 Bad Request for invalid type")
        void createTemplate_invalidType_returns400() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);
            when(xQueryTemplateService.create(eq(DOMAIN_ID), eq(SOLUTION_ID), any(UUID.class), any(XQueryTemplateCreateRequest.class)))
                    .thenThrow(new IllegalArgumentException("Invalid type: 'INVALID'. Valid values: SINGULAR, FORMULA, EXTENDED"));

            var request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                    "TestQuery", "INVALID", "APPROVED", null, "desc", null, new XQueryParametersDTO(List.of()), null));

            mockMvc.perform(post(CREATE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .requestAttr("requester", requester))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Requirement 2.4: Invalid status returns 400 Bad Request.
         */
        @Test
        @DisplayName("returns 400 Bad Request for invalid status")
        void createTemplate_invalidStatus_returns400() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);
            when(xQueryTemplateService.create(eq(DOMAIN_ID), eq(SOLUTION_ID), any(UUID.class), any(XQueryTemplateCreateRequest.class)))
                    .thenThrow(new IllegalArgumentException("Invalid status: 'INVALID'. Valid values: PENDING, APPROVED, DISALLOWED"));

            var request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                    "TestQuery", "SINGULAR", "INVALID", null, "desc", null, new XQueryParametersDTO(List.of()), null));

            mockMvc.perform(post(CREATE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .requestAttr("requester", requester))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========== GET (collection) - List Templates ==========

    @Nested
    @DisplayName("GET /{domainId}/{solutionId}/xquerys")
    class ListTemplates {

        /**
         * Requirement 1.2: List templates returns 200 OK with collection.
         */
        @Test
        @DisplayName("returns 200 OK with template collection")
        void listTemplates_withResults_returns200() throws Exception {
            when(xQueryTemplateService.list(DOMAIN_ID, SOLUTION_ID)).thenReturn(List.of(sampleDTO()));

            mockMvc.perform(get(BASE_PATH)
                            .requestAttr("requester", requester))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.xquerys.xquery").isArray())
                    .andExpect(jsonPath("$.xquerys.xquery[0].id").value(QUERY_TOKEN));
        }

        /**
         * Requirement 1.2: Empty list returns 204 No Content.
         */
        @Test
        @DisplayName("returns 204 No Content when no templates exist")
        void listTemplates_empty_returns204() throws Exception {
            when(xQueryTemplateService.list(DOMAIN_ID, SOLUTION_ID)).thenReturn(List.of());

            mockMvc.perform(get(BASE_PATH)
                            .requestAttr("requester", requester))
                    .andExpect(status().isNoContent());
        }
    }

    // ========== GET (single) - Get Template By Token ==========

    @Nested
    @DisplayName("GET /{domainId}/{solutionId}/xquerys/{queryToken}")
    class GetTemplate {

        /**
         * Requirement 1.3: Get template by token returns 200 OK.
         */
        @Test
        @DisplayName("returns 200 OK with template data")
        void getTemplate_found_returns200() throws Exception {
            when(xQueryTemplateService.getByToken(DOMAIN_ID, SOLUTION_ID, QUERY_TOKEN)).thenReturn(sampleDTO());

            mockMvc.perform(get(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .requestAttr("requester", requester))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.xquery.id").value(QUERY_TOKEN))
                    .andExpect(jsonPath("$.xquery.type").value("SINGULAR"))
                    // D6 (2.13): the schema nests parameters as an object carrying a "parameter"
                    // array, so the path gains a level: parameters.parameter[0], not parameters[0].
                    .andExpect(jsonPath("$.xquery.parameters.parameter[0].name").value("studentId"));
        }

        /**
         * Requirement 1.3: Non-existent token returns 404 Not Found.
         */
        @Test
        @DisplayName("returns 404 Not Found for non-existent token")
        void getTemplate_notFound_returns404() throws Exception {
            when(xQueryTemplateService.getByToken(DOMAIN_ID, SOLUTION_ID, "NonExistent"))
                    .thenThrow(new XQueryTemplateNotFoundException("NonExistent", SOLUTION_ID));

            mockMvc.perform(get(BASE_PATH + "/{queryToken}", "NonExistent")
                            .requestAttr("requester", requester))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.subCode").value("XQUERY_002"));
        }
    }

    // ========== DELETE - Delete Template ==========

    @Nested
    @DisplayName("DELETE /{domainId}/{solutionId}/xquerys/{queryToken}")
    class DeleteTemplate {

        /**
         * Requirement 1.4: Delete template returns 204 No Content.
         */
        @Test
        @DisplayName("returns 204 No Content on successful delete")
        void deleteTemplate_success_returns204() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);
            doNothing().when(xQueryTemplateService).delete(DOMAIN_ID, SOLUTION_ID, QUERY_TOKEN);

            mockMvc.perform(delete(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .requestAttr("requester", requester))
                    .andExpect(status().isNoContent());
        }

        /**
         * Requirement 1.4: Delete non-existent token returns 404 Not Found.
         */
        @Test
        @DisplayName("returns 404 Not Found for non-existent token")
        void deleteTemplate_notFound_returns404() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);
            doThrow(new XQueryTemplateNotFoundException("NonExistent", SOLUTION_ID))
                    .when(xQueryTemplateService).delete(DOMAIN_ID, SOLUTION_ID, "NonExistent");

            mockMvc.perform(delete(BASE_PATH + "/{queryToken}", "NonExistent")
                            .requestAttr("requester", requester))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.subCode").value("XQUERY_002"));
        }

        /**
         * Requirement 1.6: Delete without PROVIDE access returns 403 Forbidden.
         */
        @Test
        @DisplayName("returns 403 Forbidden when no PROVIDE access")
        void deleteTemplate_noProvideAccess_returns403() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(false);
            when(authorizationService.checkPermission(any()))
                    .thenReturn(new PermissionCheckResponse(false));

            mockMvc.perform(delete(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .requestAttr("requester", requester))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.subCode").value("XQUERY_003"));
        }
    }

    @Nested
    @DisplayName("PUT /xquerys/{queryToken}")
    class UpdateTemplateTests {

        /**
         * The wrapped, partial form. Only the supplied keys reach the service -- asserted with a
         * captor, because "the endpoint returned 200" would pass even if the body were dropped.
         */
        @Test
        @DisplayName("accepts a wrapped partial body and passes it through")
        void acceptsWrappedPartialBody() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);
            when(xQueryTemplateService.update(any(), any(), any(), any())).thenReturn(sampleDto());

            mockMvc.perform(put(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"xquery\":{\"status\":\"APPROVED\"}}")
                            .requestAttr("requester", requester))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.xquery.id").value(QUERY_TOKEN));

            ArgumentCaptor<XQueryTemplateUpdateRequest> captor =
                    ArgumentCaptor.forClass(XQueryTemplateUpdateRequest.class);
            verify(xQueryTemplateService).update(eq(DOMAIN_ID), eq(SOLUTION_ID), eq(QUERY_TOKEN),
                    captor.capture());

            XQueryTemplateUpdateBody body = captor.getValue().xquery();
            assertThat(body.status()).isEqualTo("APPROVED");
            assertThat(body.script())
                    .as("an omitted field must arrive as null so the service leaves it alone, "
                        + "rather than being defaulted to something that overwrites the stored value")
                    .isNull();
            assertThat(body.type()).isNull();
            assertThat(body.parameters()).isNull();
        }

        /**
         * This is the assertion that pins the envelope. A test sending only the wrapped form would
         * keep passing if the wrapper were made optional again.
         */
        @Test
        @DisplayName("rejects an unwrapped flat body")
        void rejectsUnwrappedBody() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);

            mockMvc.perform(put(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"APPROVED\"}")
                            .requestAttr("requester", requester))
                    .andExpect(status().isBadRequest());

            verify(xQueryTemplateService, never()).update(any(), any(), any(), any());
        }

        /**
         * The query token is not settable through the body -- it identifies the template, and zone
         * bindings reference it by name. FAIL_ON_UNKNOWN_PROPERTIES makes an attempt a 400.
         */
        /**
         * Pins the @NotNull on the wrapper specifically.
         *
         * <p>`rejectsUnwrappedBody` does not: FAIL_ON_UNKNOWN_PROPERTIES rejects a flat body at the
         * Jackson layer, so it passes with or without the constraint -- verified by removing the
         * annotation and watching it still pass. An empty object parses cleanly and leaves
         * `xquery` null, so only validation can catch it; without @NotNull this reaches the service
         * and NPEs into a 500.</p>
         */
        @Test
        @DisplayName("rejects a body with no xquery wrapper at all")
        void rejectsMissingWrapper() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);

            mockMvc.perform(put(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .requestAttr("requester", requester))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(put(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"xquery\":null}")
                            .requestAttr("requester", requester))
                    .andExpect(status().isBadRequest());

            verify(xQueryTemplateService, never()).update(any(), any(), any(), any());
        }

        @Test
        @DisplayName("rejects an attempt to change the query token")
        void rejectsTokenRename() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);

            mockMvc.perform(put(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"xquery\":{\"id\":\"SomethingElse\"}}")
                            .requestAttr("requester", requester))
                    .andExpect(status().isBadRequest());

            verify(xQueryTemplateService, never()).update(any(), any(), any(), any());
        }

        @Test
        @DisplayName("returns 404 when no template has that token")
        void notFound() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);
            when(xQueryTemplateService.update(any(), any(), any(), any()))
                    .thenThrow(new XQueryTemplateNotFoundException(QUERY_TOKEN, SOLUTION_ID));

            mockMvc.perform(put(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"xquery\":{\"status\":\"APPROVED\"}}")
                            .requestAttr("requester", requester))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 400 for an invalid status value")
        void invalidStatus() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(true);
            when(xQueryTemplateService.update(any(), any(), any(), any()))
                    .thenThrow(new IllegalArgumentException("Invalid status: NOPE"));

            mockMvc.perform(put(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"xquery\":{\"status\":\"NOPE\"}}")
                            .requestAttr("requester", requester))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 403 without PROVIDE access")
        void noProvideAccess() throws Exception {
            when(authorizationService.isAdmin(any(Environment.class))).thenReturn(false);
            when(authorizationService.checkPermission(any()))
                    .thenReturn(new PermissionCheckResponse(false));

            mockMvc.perform(put(BASE_PATH + "/{queryToken}", QUERY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"xquery\":{\"status\":\"APPROVED\"}}")
                            .requestAttr("requester", requester))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.subCode").value("XQUERY_003"));

            verify(xQueryTemplateService, never()).update(any(), any(), any(), any());
        }

        private XQueryTemplateDTO sampleDto() {
            return new XQueryTemplateDTO(QUERY_TOKEN, "SINGULAR", "APPROVED", null,
                    "desc", "SELECT 1", new XQueryParametersDTO(List.of()), "urn:x");
        }
    }
}
