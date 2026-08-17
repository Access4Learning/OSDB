// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for the GET /internal/environments/{id}/session-token endpoint.
 *
 * Validates Requirements 1.1 and 1.2:
 * - Valid UUID returns session token as plain text (200)
 * - Non-existent UUID returns 404
 * - Environment with null/blank session token returns 404
 */
@WebMvcTest(
    controllers = InternalAuthController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
)
@Import(TestSecurityConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
    "observability.metrics.enabled=false",
    "secrets.backend.type=plaintext"
})
@org.springframework.test.context.ActiveProfiles("disable-security")
class InternalAuthControllerSessionTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.sifunity.broker.environment.service.AuthenticationService authenticationService;

    @MockBean
    private org.sifunity.broker.environment.service.AuthorizationService authorizationService;

    @MockBean
    private org.sifunity.broker.environment.service.ProviderService providerService;

    @MockBean
    private EnvironmentRepository environmentRepository;

    @MockBean
    private org.sifunity.broker.environment.repository.SolutionRepository solutionRepository;

    @MockBean
    private org.sifunity.broker.environment.service.EnvironmentService environmentService;

    @MockBean
    private org.sifunity.broker.environment.service.ProvisionRequestService provisionRequestService;

    @MockBean
    private org.sifunity.broker.environment.repository.ProviderRegistrationRepository providerRegistrationRepository;

    @MockBean
    private org.sifunity.broker.environment.repository.AclEntryRepository aclEntryRepository;

    @MockBean
    private org.sifunity.broker.environment.repository.ZoneRepository zoneRepository;

    @MockBean
    private org.sifunity.broker.environment.repository.ContextRepository contextRepository;

    @MockBean
    private org.sifunity.broker.environment.repository.ContextServiceRepository contextServiceRepository;

    @MockBean
    private org.sifunity.broker.environment.repository.DomainRepository domainRepository;

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

    @Test
    void getSessionToken_WithValidUuid_ReturnsSessionTokenAsPlainText() throws Exception {
        // Given
        UUID envId = UUID.randomUUID();
        String expectedToken = "test-session-token-abc123";

        Environment env = Environment.builder()
                .id(envId)
                .applicationName("TestApp")
                .sessionToken(expectedToken)
                .sharedSecret("secret")
                .type(EnvironmentType.BROKERED)
                .solutionUuid(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));

        // When & Then
        mockMvc.perform(get("/internal/environments/{id}/session-token", envId)
                        .header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedToken));
    }

    @Test
    void getSessionToken_WithNonExistentUuid_Returns404() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(environmentRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/internal/environments/{id}/session-token", nonExistentId)
                        .header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSessionToken_WithNullSessionToken_Returns404() throws Exception {
        // Given
        UUID envId = UUID.randomUUID();

        Environment env = Environment.builder()
                .id(envId)
                .applicationName("TestApp")
                .sessionToken(null)
                .sharedSecret("secret")
                .type(EnvironmentType.BROKERED)
                .solutionUuid(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));

        // When & Then
        mockMvc.perform(get("/internal/environments/{id}/session-token", envId)
                        .header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSessionToken_WithBlankSessionToken_Returns404() throws Exception {
        // Given
        UUID envId = UUID.randomUUID();

        Environment env = Environment.builder()
                .id(envId)
                .applicationName("TestApp")
                .sessionToken("   ")
                .sharedSecret("secret")
                .type(EnvironmentType.BROKERED)
                .solutionUuid(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));

        // When & Then
        mockMvc.perform(get("/internal/environments/{id}/session-token", envId)
                        .header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(status().isNotFound());
    }
}
