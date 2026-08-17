// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProvisionRequestRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the ProvisionRequest HTTP round-trip.
 *
 * <p>Uses auto-approve=false to test the full manual resolution flow:
 * POST (create, pending) â†’ GET (202) â†’ PUT (admin resolve) â†’ GET (200) â†’ DELETE (204) â†’ GET (404).
 * Also tests foreign key cascade on environment deletion.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 2.1, 2.2, 3.1, 4.1, 5.1, 8.2, 9.1, 9.2, 9.3, 9.4</strong></p>
 */
@SpringBootTest(properties = "sif.broker.provision-requests.auto-approve=false")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
@DisplayName("Provision Request Integration Tests")
class ProvisionRequestIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DomainService domainService;
    @Autowired private SolutionService solutionService;
    @Autowired private ZoneService zoneService;
    @Autowired private EnvironmentService environmentService;
    @Autowired private EnvironmentRepository environmentRepository;
    @Autowired private ProvisionRequestRepository provisionRequestRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private TestEntityHierarchy hierarchy;
    private String domainId;
    private String solutionId;
    private String zoneId;
    private String contextId;
    private UUID environmentId;
    private Environment environment;

    // Admin
    private UUID adminEnvironmentId;
    private Environment adminEnvironment;

    @BeforeEach
    void setUp() {
        // Consumer hierarchy: domain + solution + zone + DEFAULT context
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        domainId = hierarchy.getDomainId();
        solutionId = hierarchy.getSolutionId();
        zoneId = hierarchy.getZoneId();
        contextId = hierarchy.getContextId();

        // Register a BROKERED environment in consumer hierarchy
        SifEnvironmentRegistrationResponse regResponse = environmentService.registerEnvironment(domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("ConsumerApp-" + System.nanoTime(), EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null)));
        environmentId = regResponse.environment().id();
        environment = environmentRepository.findById(environmentId).orElseThrow();

        // Admin environment: register in the bootstrapped GLOBAL_DOMAIN/GLOBAL_SOLUTION
        // (already created by BootstrapComponent at startup) so isAdmin() returns true
        SifEnvironmentRegistrationResponse adminRegResponse = environmentService.registerEnvironment(GlobalAdminConstants.GLOBAL_DOMAIN,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AdminApp-" + System.nanoTime(), EnvironmentType.BROKERED, null, GlobalAdminConstants.GLOBAL_SOLUTION, null)));
        adminEnvironmentId = adminRegResponse.environment().id();
        adminEnvironment = environmentRepository.findById(adminEnvironmentId).orElseThrow();
    }

    // ---- Helper: build provision request URL ----

    private String provisionRequestUrl() {
        return "/" + domainId + "/" + solutionId + "/environments/" + environmentId + "/provisionRequests";
    }

    private String provisionRequestUrl(UUID prId) {
        return provisionRequestUrl() + "/" + prId;
    }

    // ---- Helper: build a valid create payload JSON ----

    private String createPayloadJson() throws Exception {
        var payload = new ProvisionRequestCreateDTO(
                new ProvisionedZonesDTO(List.of(
                        new ProvisionedZoneDTO(zoneId,
                                new ProvisionedZoneServicesDTO(List.of(
                                        new ProvisionedServiceDTO("OBJECT", "students", contextId,
                                                new ProvisionedServiceRightsDTO(List.of(
                                                        new RightDTO("QUERY", "REQUESTED"),
                                                        new RightDTO("CREATE", "REQUESTED")
                                                )))))))));
        return objectMapper.writeValueAsString(payload);
    }

    // ---- Helper: build admin resolve payload JSON ----

    private String resolvePayloadJson(String queryValue, String createValue) throws Exception {
        var payload = new ProvisionRequestResolveDTO(
                new ProvisionedZonesDTO(List.of(
                        new ProvisionedZoneDTO(zoneId,
                                new ProvisionedZoneServicesDTO(List.of(
                                        new ProvisionedServiceDTO("OBJECT", "students", contextId,
                                                new ProvisionedServiceRightsDTO(List.of(
                                                        new RightDTO("QUERY", queryValue),
                                                        new RightDTO("CREATE", createValue)
                                                )))))))));
        return objectMapper.writeValueAsString(payload);
    }

    // ========== Test: Full manual resolution flow ==========

    @Test
    @DisplayName("Full manual flow: POST(201) â†’ GET(202) â†’ PUT(200, ACCEPTED) â†’ GET(200) â†’ DELETE(204) â†’ GET(404)")
    void fullManualResolutionFlow() throws Exception {
        // 1. POST create â†’ 201, completionStatus absent
        MvcResult createResult = mockMvc.perform(post(provisionRequestUrl() + "/provisionRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayloadJson())
                        .requestAttr("requester", environment))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        assertThat(createNode.has("id")).isTrue();
        UUID prId = UUID.fromString(createNode.get("id").asText());
        // completionStatus should be absent (null â†’ omitted by @JsonInclude)
        assertThat(createNode.has("completionStatus")).isFalse();
        assertThat(createNode.has("provisionedZones")).isTrue();

        // 2. GET â†’ 202 (pending)
        mockMvc.perform(get(provisionRequestUrl(prId))
                        .requestAttr("requester", environment))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(prId.toString()))
                .andExpect(jsonPath("$.completionStatus").doesNotExist());

        // 3. PUT admin resolve (all APPROVED) â†’ 200, completionStatus=ACCEPTED
        MvcResult resolveResult = mockMvc.perform(put(provisionRequestUrl(prId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolvePayloadJson("APPROVED", "APPROVED"))
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode resolveNode = objectMapper.readTree(resolveResult.getResponse().getContentAsString());
        assertThat(resolveNode.get("completionStatus").asText()).isEqualTo("ACCEPTED");

        // 4. GET â†’ 200, completionStatus=ACCEPTED
        mockMvc.perform(get(provisionRequestUrl(prId))
                        .requestAttr("requester", environment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionStatus").value("ACCEPTED"));

        // 5. DELETE â†’ 204
        mockMvc.perform(delete(provisionRequestUrl(prId))
                        .requestAttr("requester", environment))
                .andExpect(status().isNoContent());

        // 6. GET â†’ 404
        mockMvc.perform(get(provisionRequestUrl(prId))
                        .requestAttr("requester", environment))
                .andExpect(status().isNotFound());
    }

    // ========== Test: Foreign key cascade ==========

    @Test
    @DisplayName("Deleting environment cascades to provision_requests table")
    void foreignKeyCascadeOnEnvironmentDelete() throws Exception {
        // Create a provision request
        MvcResult createResult = mockMvc.perform(post(provisionRequestUrl() + "/provisionRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayloadJson())
                        .requestAttr("requester", environment))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID prId = UUID.fromString(createNode.get("id").asText());

        // Verify provision request exists
        assertThat(provisionRequestRepository.findById(prId)).isPresent();

        // Flush to ensure all writes are committed
        entityManager.flush();
        entityManager.clear();

        // Delete the environment directly via repository
        environmentRepository.deleteById(environmentId);
        entityManager.flush();
        entityManager.clear();

        // Verify provision request is gone (cascade delete)
        assertThat(provisionRequestRepository.findByEnvironmentId(environmentId)).isEmpty();
    }
}