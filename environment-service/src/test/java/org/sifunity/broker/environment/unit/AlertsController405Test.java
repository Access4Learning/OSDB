// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.sifunity.broker.environment.controller.AlertsController;
import org.sifunity.broker.environment.service.AlertsService;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.MessageContextCacheService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.ProvisionRequestService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.EntityTagService;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for AlertsController 405 Method Not Allowed responses.
 *
 * <p>Verifies that PUT and DELETE operations on alerts endpoints are rejected
 * with HTTP 405, since alerts are immutable per SIF 3.7 specification.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.3, 4.4</strong></p>
 */
@WebMvcTest(
    controllers = AlertsController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
)
@Import(TestSecurityConfiguration.class)
@TestPropertySource(properties = {
    "observability.metrics.enabled=false",
    "secrets.backend.type=plaintext"
})
@ActiveProfiles("disable-security")
@DisplayName("AlertsController 405 Responses")
class AlertsController405Test {

    private static final String DOMAIN_ID = "test-domain";
    private static final String SOLUTION_ID = "test-solution";
    private static final String BASE_PATH = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/alerts";

    @Autowired
    private MockMvc mockMvc;

    // --- Controller direct dependencies ---
    @MockBean
    private AlertsService alertsService;

    @MockBean
    private AuthorizationService authorizationService;

    // --- Dependencies required by other controllers loaded in context ---
    @MockBean
    private ProviderService providerService;

    @MockBean
    private ProvisionRequestService provisionRequestService;

    @MockBean
    private DomainService domainService;

    @MockBean
    private SolutionService solutionService;

    @MockBean
    private EntityTagService entityTagService;

    @MockBean
    private MessageContextCacheService messageContextCacheService;

    // --- Repositories needed by services ---
    @MockBean
    private ProviderRegistrationRepository providerRegistrationRepository;

    @MockBean
    private AclEntryRepository aclEntryRepository;

    @MockBean
    private EnvironmentRepository environmentRepository;

    @MockBean
    private ZoneRepository zoneRepository;

    @MockBean
    private ContextRepository contextRepository;

    @MockBean
    private org.sifunity.broker.environment.repository.ContextServiceRepository contextServiceRepository;

    @MockBean
    private DomainRepository domainRepository;

    @MockBean
    private SolutionRepository solutionRepository;

    @MockBean
    private org.sifunity.broker.environment.repository.SolutionServiceRepository solutionServiceRepository;

    @MockBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

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

    /**
     * Requirement 4.1: PUT on /alerts/{id} returns 405.
     */
    @Test
    @DisplayName("PUT /alerts/{id} returns 405 Method Not Allowed")
    void putOnSingleAlert_returns405() throws Exception {
        UUID alertId = UUID.randomUUID();

        mockMvc.perform(put(BASE_PATH + "/{id}", alertId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.subCode").value("ALERT_006"));
    }

    /**
     * Requirement 4.2: DELETE on /alerts/{id} returns 405.
     */
    @Test
    @DisplayName("DELETE /alerts/{id} returns 405 Method Not Allowed")
    void deleteOnSingleAlert_returns405() throws Exception {
        UUID alertId = UUID.randomUUID();

        mockMvc.perform(delete(BASE_PATH + "/{id}", alertId))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.subCode").value("ALERT_006"));
    }

    /**
     * Requirement 4.3: PUT on /alerts returns 405.
     */
    @Test
    @DisplayName("PUT /alerts returns 405 Method Not Allowed")
    void putOnAlertsCollection_returns405() throws Exception {
        mockMvc.perform(put(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.subCode").value("ALERT_006"));
    }

    /**
     * Requirement 4.4: DELETE on /alerts returns 405.
     */
    @Test
    @DisplayName("DELETE /alerts returns 405 Method Not Allowed")
    void deleteOnAlertsCollection_returns405() throws Exception {
        mockMvc.perform(delete(BASE_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.subCode").value("ALERT_006"));
    }
}
