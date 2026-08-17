// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;
import org.sifunity.broker.environment.dto.ContextCreateBody;
import org.sifunity.broker.environment.dto.ContextCreateRequest;
import org.sifunity.broker.environment.dto.ContextCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.dto.ZoneCreateBody;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.exception.DuplicateContextException;
import org.sifunity.broker.environment.exception.DuplicateZoneException;
import org.sifunity.broker.environment.exception.ZoneNotFoundException;
import org.sifunity.broker.environment.service.ZoneService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ZoneController.
 * 
 * <p>Tests the REST API endpoints for zone and context management operations
 * including request validation, response mapping, and error handling.</p>
 */
@WebMvcTest(
    controllers = ZoneController.class,
    excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
)
@Import(TestSecurityConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
    "observability.metrics.enabled=false",
    "secrets.backend.type=plaintext"
})
@org.springframework.test.context.ActiveProfiles("disable-security")
@DisplayName("ZoneController")
class ZoneControllerTest {
    
    private static final String TEST_DOMAIN_ID = "test-domain";
    private static final String TEST_SOLUTION_ID = "test-solution";
    private static final String BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID + "/zones";
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private ZoneService zoneService;
    
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
    
    @Nested
    @DisplayName("POST /zones/zone")
    class CreateZoneTests {
        
        @Test
        @DisplayName("Should create zone successfully with valid request")
        void shouldCreateZoneSuccessfully() throws Exception {
            // Given
            ZoneCreateRequestWrapper request =
                new ZoneCreateRequestWrapper(new ZoneCreateBody("test-zone", "Test Zone Description"));
            ZoneDTO response = new ZoneDTO(
                UUID.randomUUID(),
                "test-solution",
                "test-zone",
                "Test Zone Description",
                Instant.now()
            );
            
            when(zoneService.createZone(eq(TEST_DOMAIN_ID), any(ZoneCreateRequest.class))).thenReturn(response);
            
            // When & Then (broker-services fingerprint bypasses admin guard)
            mockMvc.perform(post(BASE_PATH + "/zone")
                    .header("X-Environment-Fingerprint", "broker-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.zone.id").value("test-zone"))
                .andExpect(jsonPath("$.zone.description").value("Test Zone Description"))
                .andExpect(jsonPath("$.zone.createdAt").doesNotExist());
        }
        
        @Test
        @DisplayName("Should create zone with null description")
        void shouldCreateZoneWithNullDescription() throws Exception {
            // Given
            ZoneCreateRequestWrapper request =
                new ZoneCreateRequestWrapper(new ZoneCreateBody("test-zone", null));
            ZoneDTO response = new ZoneDTO(
                UUID.randomUUID(),
                "test-solution",
                "test-zone",
                null,
                Instant.now()
            );
            
            when(zoneService.createZone(eq(TEST_DOMAIN_ID), any(ZoneCreateRequest.class))).thenReturn(response);
            
            // When & Then (broker-services fingerprint bypasses admin guard)
            mockMvc.perform(post(BASE_PATH + "/zone")
                    .header("X-Environment-Fingerprint", "broker-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.zone.id").value("test-zone"))
                .andExpect(jsonPath("$.zone.description").isEmpty());
        }
        
        @Test
        @DisplayName("Should return 409 when zone ID already exists")
        void shouldReturn409WhenZoneIdExists() throws Exception {
            // Given
            ZoneCreateRequestWrapper request =
                new ZoneCreateRequestWrapper(new ZoneCreateBody("existing-zone", "Description"));
            
            when(zoneService.createZone(eq(TEST_DOMAIN_ID), any(ZoneCreateRequest.class)))
                .thenThrow(new DuplicateZoneException("existing-zone"));
            
            // When & Then (broker-services fingerprint bypasses admin guard)
            mockMvc.perform(post(BASE_PATH + "/zone")
                    .header("X-Environment-Fingerprint", "broker-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
        }
        
        @Test
        @DisplayName("Should return 400 for invalid zone ID format")
        void shouldReturn400ForInvalidZoneIdFormat() throws Exception {
            // Given — bad identifier supplied under the canonical zone.id
            ZoneCreateRequestWrapper request =
                new ZoneCreateRequestWrapper(new ZoneCreateBody("_invalid-zone", "Description"));
            
            // When & Then
            mockMvc.perform(post(BASE_PATH + "/zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
        
        @Test
        @DisplayName("Should return 400 for missing zone ID")
        void shouldReturn400ForMissingZoneId() throws Exception {
            // Given — body wrapped under "zone" with no id (only description)
            ZoneCreateRequestWrapper request =
                new ZoneCreateRequestWrapper(new ZoneCreateBody(null, "Description"));
            
            // When & Then
            mockMvc.perform(post(BASE_PATH + "/zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
        
        @Test
        @DisplayName("Should return 400 for blank zone ID")
        void shouldReturn400ForBlankZoneId() throws Exception {
            // Given
            ZoneCreateRequestWrapper request =
                new ZoneCreateRequestWrapper(new ZoneCreateBody("", "Description"));
            
            // When & Then
            mockMvc.perform(post(BASE_PATH + "/zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }
    
    @Nested
    @DisplayName("GET /zones")
    class ListZonesTests {
        
        @Test
        @DisplayName("Should return all zones")
        void shouldReturnAllZones() throws Exception {
            // Given
            List<ZoneDTO> zones = List.of(
                new ZoneDTO(UUID.randomUUID(), "test-solution", "zone1", "Zone 1", Instant.now()),
                new ZoneDTO(UUID.randomUUID(), "test-solution", "zone2", "Zone 2", Instant.now())
            );
            
            when(zoneService.listZonesBySolution(TEST_DOMAIN_ID, TEST_SOLUTION_ID)).thenReturn(zones);
            
            // When & Then — controller now returns SifCollection: {"zones":{"zone":[...]}}
            mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.zones.zone").isArray())
                .andExpect(jsonPath("$.zones.zone.length()").value(2))
                .andExpect(jsonPath("$.zones.zone[0].id").value("zone1"))
                .andExpect(jsonPath("$.zones.zone[1].id").value("zone2"));
        }
        
        @Test
        @DisplayName("Should return empty array when no zones exist")
        void shouldReturnEmptyArrayWhenNoZones() throws Exception {
            // Given
            when(zoneService.listZonesBySolution(TEST_DOMAIN_ID, TEST_SOLUTION_ID)).thenReturn(List.of());
            
            // When & Then — controller now returns SifCollection: {"zones":{"zone":[]}}
            mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.zones.zone").isArray())
                .andExpect(jsonPath("$.zones.zone.length()").value(0));
        }
    }
    
    @Nested
    @DisplayName("POST /zones/{zoneId}/contexts/context")
    class CreateContextTests {
        
        @Test
        @DisplayName("Should create context successfully with valid request")
        void shouldCreateContextSuccessfully() throws Exception {
            // Given
            String zoneId = "test-zone";
            ContextCreateRequestWrapper request =
                new ContextCreateRequestWrapper(new ContextCreateBody("test-context", "Test Context Description"));
            ContextDTO response = new ContextDTO(
                UUID.randomUUID(),
                zoneId,
                "test-context",
                "Test Context Description",
                Instant.now()
            );
            
            when(zoneService.createContext(eq(TEST_DOMAIN_ID), eq(TEST_SOLUTION_ID), eq(zoneId), any(ContextCreateRequest.class))).thenReturn(response);
            
            // When & Then (broker-services fingerprint bypasses admin guard)
            mockMvc.perform(post(BASE_PATH + "/{zoneId}/contexts/context", zoneId)
                    .header("X-Environment-Fingerprint", "broker-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.context.zoneId").value(zoneId))
                .andExpect(jsonPath("$.context.id").value("test-context"))
                .andExpect(jsonPath("$.context.description").value("Test Context Description"))
                .andExpect(jsonPath("$.context.createdAt").doesNotExist());
        }
        
        @Test
        @DisplayName("Should return 404 when parent zone does not exist")
        void shouldReturn404WhenParentZoneNotExists() throws Exception {
            // Given
            String zoneId = "non-existent-zone";
            ContextCreateRequestWrapper request =
                new ContextCreateRequestWrapper(new ContextCreateBody("test-context", "Description"));
            
            when(zoneService.createContext(eq(TEST_DOMAIN_ID), eq(TEST_SOLUTION_ID), eq(zoneId), any(ContextCreateRequest.class)))
                .thenThrow(new ZoneNotFoundException(zoneId));
            
            // When & Then (broker-services fingerprint bypasses admin guard)
            mockMvc.perform(post(BASE_PATH + "/{zoneId}/contexts/context", zoneId)
                    .header("X-Environment-Fingerprint", "broker-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        }
        
        @Test
        @DisplayName("Should return 409 when context already exists in zone")
        void shouldReturn409WhenContextExists() throws Exception {
            // Given
            String zoneId = "test-zone";
            ContextCreateRequestWrapper request =
                new ContextCreateRequestWrapper(new ContextCreateBody("existing-context", "Description"));
            
            when(zoneService.createContext(eq(TEST_DOMAIN_ID), eq(TEST_SOLUTION_ID), eq(zoneId), any(ContextCreateRequest.class)))
                .thenThrow(new DuplicateContextException(zoneId, "existing-context"));
            
            // When & Then (broker-services fingerprint bypasses admin guard)
            mockMvc.perform(post(BASE_PATH + "/{zoneId}/contexts/context", zoneId)
                    .header("X-Environment-Fingerprint", "broker-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
        }
        
        @Test
        @DisplayName("Should return 400 for invalid context ID format")
        void shouldReturn400ForInvalidContextIdFormat() throws Exception {
            // Given — bad identifier supplied under the canonical context.id
            String zoneId = "test-zone";
            ContextCreateRequestWrapper request =
                new ContextCreateRequestWrapper(new ContextCreateBody("_invalid-context", "Description"));
            
            // When & Then
            mockMvc.perform(post(BASE_PATH + "/{zoneId}/contexts/context", zoneId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }
    
    @Nested
    @DisplayName("GET /zones/{zoneId}/contexts")
    class ListContextsTests {
        
        @Test
        @DisplayName("Should return all contexts for zone")
        void shouldReturnAllContextsForZone() throws Exception {
            // Given
            String zoneId = "test-zone";
            List<ContextDTO> contexts = List.of(
                new ContextDTO(UUID.randomUUID(), zoneId, "context1", "Context 1", Instant.now()),
                new ContextDTO(UUID.randomUUID(), zoneId, "context2", "Context 2", Instant.now())
            );
            
            when(zoneService.listContexts(eq(TEST_DOMAIN_ID), eq(TEST_SOLUTION_ID), eq(zoneId))).thenReturn(contexts);
            
            // When & Then — controller now returns SifCollection: {"contexts":{"context":[...]}}
            mockMvc.perform(get(BASE_PATH + "/{zoneId}/contexts", zoneId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.contexts.context").isArray())
                .andExpect(jsonPath("$.contexts.context.length()").value(2))
                .andExpect(jsonPath("$.contexts.context[0].id").value("context1"))
                .andExpect(jsonPath("$.contexts.context[1].id").value("context2"))
                .andExpect(jsonPath("$.contexts.context[0].zoneId").value(zoneId))
                .andExpect(jsonPath("$.contexts.context[1].zoneId").value(zoneId));
        }
        
        @Test
        @DisplayName("Should return empty array when no contexts exist in zone")
        void shouldReturnEmptyArrayWhenNoContextsInZone() throws Exception {
            // Given
            String zoneId = "test-zone";
            when(zoneService.listContexts(eq(TEST_DOMAIN_ID), eq(TEST_SOLUTION_ID), eq(zoneId))).thenReturn(List.of());
            
            // When & Then — controller now returns SifCollection: {"contexts":{"context":[]}}
            mockMvc.perform(get(BASE_PATH + "/{zoneId}/contexts", zoneId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.contexts.context").isArray())
                .andExpect(jsonPath("$.contexts.context.length()").value(0));
        }
    }
}

