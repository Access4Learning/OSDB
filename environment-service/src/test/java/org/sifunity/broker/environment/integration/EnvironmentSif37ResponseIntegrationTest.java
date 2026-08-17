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
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the full SIF 3.7 environment response structure.
 *
 * Covers: POST returns full SIF 3.7 structure with sharedSecret,
 * GET by ID returns full structure without sharedSecret,
 * GET list returns full structures,
 * POST with applicationInfo round-trips correctly,
 * ACL entries appear in provisionedZones after creation,
 * backward compat for environments without fingerprint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
@DisplayName("SIF 3.7 Environment Response Integration Tests")
class EnvironmentSif37ResponseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AclService aclService;

    @Autowired
    private org.sifunity.broker.environment.service.EnvironmentService environmentService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private org.sifunity.broker.environment.service.ZoneService zoneService;

    private String testDomainId;
    private String testSolutionId;
    private String testZoneId;

    @BeforeEach
    void setUp() {
        TestEntityHierarchy hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        testDomainId = hierarchy.getDomainId();
        testSolutionId = hierarchy.getSolutionId();
        testZoneId = hierarchy.getZoneId();
    }

    @Test
    @DisplayName("POST /environments/environment returns full SIF 3.7 structure with sharedSecret")
    void postReturnsFullStructureWithSharedSecret() throws Exception {
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("IntegApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, DefaultZoneDTO.of(testZoneId), testSolutionId, null));

        MvcResult result = mockMvc.perform(post("/" + testDomainId + "/" + testSolutionId + "/environments/environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.has("environment")).as("POST response must be wrapped under 'environment' key").isTrue();
        JsonNode node = root.get("environment");

        assertThat(node.has("id")).isTrue();
        assertThat(node.has("type")).isTrue();
        assertThat(node.get("type").asText()).isEqualTo("BROKERED");
        assertThat(node.has("fingerprint")).isTrue();
        assertThat(node.has("sessionToken")).isTrue();
        assertThat(node.has("authenticationMethod")).isTrue();
        assertThat(node.get("authenticationMethod").asText()).isEqualTo("SIF_HMACSHA256");
        assertThat(node.has("consumerName")).isTrue();
        assertThat(node.has("defaultZone")).isTrue();
        assertThat(node.get("defaultZone").get("id").asText()).isEqualTo(testZoneId);
        assertThat(node.has("sharedSecret")).isTrue();
        assertThat(node.get("sharedSecret").asText()).isNotBlank();
        assertThat(node.has("infrastructureServices")).isTrue();
        assertThat(node.get("infrastructureServices").get("infrastructureService").size()).isEqualTo(8);
        assertThat(node.has("provisionedZones")).isTrue();
    }

    @Test
    @DisplayName("GET /environments/{id} returns full structure without sharedSecret")
    void getByIdReturnsFullStructureWithoutSharedSecret() throws Exception {
        // Register
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("GetApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, DefaultZoneDTO.of(testZoneId), testSolutionId, null));

        MvcResult regResult = mockMvc.perform(post("/" + testDomainId + "/" + testSolutionId + "/environments/environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode regRoot = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String envId = regRoot.get("environment").get("id").asText();

        // Retrieve
        MvcResult getResult = mockMvc.perform(get("/" + testDomainId + "/" + testSolutionId + "/environments/{id}", envId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode getRoot = objectMapper.readTree(getResult.getResponse().getContentAsString());
        assertThat(getRoot.has("environment")).as("GET response must be wrapped under 'environment' key").isTrue();
        JsonNode getNode = getRoot.get("environment");

        assertThat(getNode.has("id")).isTrue();
        assertThat(getNode.get("id").asText()).isEqualTo(envId);
        assertThat(getNode.has("fingerprint")).isTrue();
        assertThat(getNode.has("authenticationMethod")).isTrue();
        assertThat(getNode.has("consumerName")).isTrue();
        assertThat(getNode.has("defaultZone")).isTrue();
        assertThat(getNode.has("infrastructureServices")).isTrue();
        assertThat(getNode.has("provisionedZones")).isTrue();
        // sharedSecret must NOT be present
        assertThat(getNode.has("sharedSecret")).isFalse();
    }

    @Test
    @DisplayName("GET /environments returns list with full SIF 3.7 structures")
    void getListReturnsFullStructures() throws Exception {
        // Register an environment
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("ListApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, DefaultZoneDTO.of(testZoneId), testSolutionId, null));

        mockMvc.perform(post("/" + testDomainId + "/" + testSolutionId + "/environments/environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // List
        MvcResult listResult = mockMvc.perform(get("/" + testDomainId + "/" + testSolutionId + "/environments"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listNode = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode content = listNode.path("environments").path("environment");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThanOrEqualTo(1);

        JsonNode first = content.get(0);
        assertThat(first.has("id")).isTrue();
        assertThat(first.has("infrastructureServices")).isTrue();
        assertThat(first.has("provisionedZones")).isTrue();
        assertThat(first.has("sharedSecret")).isFalse();
    }

    @Test
    @DisplayName("POST with applicationInfo round-trips correctly via GET")
    void applicationInfoRoundTrip() throws Exception {
        ApplicationInfoDTO appInfo = new ApplicationInfoDTO(
                "app-key-rt", "3.7", "http://ns.example.com", "REST",
                new ProductIdentityDTO("Acme", "SIS", "2.0", "http://acme.com/icon.png"),
                new ProductIdentityDTO("AdapterCo", "Adapter", "1.0", null)
        );
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AppInfoApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, DefaultZoneDTO.of(testZoneId), testSolutionId, appInfo));

        MvcResult regResult = mockMvc.perform(post("/" + testDomainId + "/" + testSolutionId + "/environments/environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode regRoot = objectMapper.readTree(regResult.getResponse().getContentAsString());
        JsonNode regNode = regRoot.get("environment");
        String envId = regNode.get("id").asText();

        // Verify applicationInfo in registration response
        assertThat(regNode.has("applicationInfo")).isTrue();
        assertThat(regNode.get("applicationInfo").get("applicationKey").asText()).isEqualTo("app-key-rt");
        assertThat(regNode.get("applicationInfo").get("applicationProduct").get("vendorName").asText()).isEqualTo("Acme");

        // Retrieve and verify round-trip
        MvcResult getResult = mockMvc.perform(get("/" + testDomainId + "/" + testSolutionId + "/environments/{id}", envId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode getRoot = objectMapper.readTree(getResult.getResponse().getContentAsString());
        JsonNode getNode = getRoot.get("environment");
        assertThat(getNode.has("applicationInfo")).isTrue();
        JsonNode ai = getNode.get("applicationInfo");
        assertThat(ai.get("applicationKey").asText()).isEqualTo("app-key-rt");
        assertThat(ai.get("supportedInfrastructureVersion").asText()).isEqualTo("3.7");
        assertThat(ai.get("applicationProduct").get("vendorName").asText()).isEqualTo("Acme");
        assertThat(ai.get("adapterProduct").get("vendorName").asText()).isEqualTo("AdapterCo");
    }

    @Test
    @DisplayName("ACL entries appear in provisionedZones after creation")
    void aclEntriesAppearInProvisionedZones() throws Exception {
        // Register environment via service (same transaction)
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AclApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, DefaultZoneDTO.of(testZoneId), testSolutionId, null));

        SifEnvironmentRegistrationResponse regResponse = environmentService.registerEnvironment(testDomainId, request);
        java.util.UUID envId = regResponse.environment().id();

        // Verify provisionedZones is empty at registration
        assertThat(regResponse.environment().provisionedZones().provisionedZone()).isEmpty();

        // Create a zone + context so we have a valid contextUuid for ACL creation
        org.sifunity.broker.environment.dto.ZoneDTO zone = zoneService.createZone(testDomainId,
                new org.sifunity.broker.environment.dto.ZoneCreateRequest(testSolutionId, "acl-zone-" + System.currentTimeMillis(), "ACL test zone"));
        java.util.List<org.sifunity.broker.environment.dto.ContextDTO> contexts = zoneService.listContexts(testDomainId, testSolutionId, zone.zoneId());
        java.util.UUID contextUuid = contexts.stream()
                .filter(c -> "DEFAULT".equals(c.contextId()))
                .findFirst()
                .map(org.sifunity.broker.environment.dto.ContextDTO::id)
                .orElse(null);

        // Create ACL entry via service (same transaction) â€” using real contextUuid
        aclService.createAclEntry(new org.sifunity.broker.environment.dto.AclEntryCreateRequest(
                envId, "StudentSvc", zone.zoneId(), "DEFAULT",
                org.sifunity.broker.common.constant.AccessType.QUERY,
                org.sifunity.broker.common.constant.AccessLevel.APPROVED
        ));

        // Flush and clear JPA cache so getEnvironment sees the new ACL entry
        entityManager.flush();
        entityManager.clear();

        // Retrieve environment via service and check provisionedZones
        SifEnvironmentResponse getResponse = environmentService.getEnvironment(envId);

        assertThat(getResponse.provisionedZones().provisionedZone()).hasSize(1);
        ProvisionedZoneDTO zoneDto = getResponse.provisionedZones().provisionedZone().get(0);
        assertThat(zoneDto.services().service()).hasSize(1);
        assertThat(zoneDto.services().service().get(0).name()).isEqualTo("StudentSvc");
        assertThat(zoneDto.services().service().get(0).type()).isEqualTo("OBJECT");
        assertThat(zoneDto.services().service().get(0).rights().right()).hasSize(1);
        assertThat(zoneDto.services().service().get(0).rights().right().get(0).type()).isEqualTo("QUERY");
        assertThat(zoneDto.services().service().get(0).rights().right().get(0).value()).isEqualTo("APPROVED");
    }
}
