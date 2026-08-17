// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateBody;
import org.sifunity.broker.environment.dto.ZoneCreateRequestWrapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Integration test verifying that the environment-service controllers accept
 * requests with different domain/solution path variables.
 *
 * <p>Actual membership validation (403 rejection) is a gateway-level concern
 * tested in gateway property tests (Tasks 4.7/4.8). This test verifies the
 * controller-level behavior: that the controllers accept the prefixed paths
 * and that an environment created in domain A can be listed when querying
 * domain A's path, but not when querying domain B's path (because the
 * environment belongs to solution A, not solution B).</p>
 *
 * <p><strong>Validates: Requirements 11.4, 11.5</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class UrlPrefixMembershipPathAcceptanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String domainA;
    private String solutionA;
    private String zoneA;
    private String domainB;
    private String solutionB;

    @BeforeEach
    void setUp() throws Exception {
        long ts = System.currentTimeMillis();
        domainA = "domA" + ts;
        solutionA = "solA" + ts;
        zoneA = "zoneA" + ts;
        domainB = "domB" + ts;
        solutionB = "solB" + ts;

        // Create domain A with solution and zone
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DomainCreateRequest(domainA, "Domain A"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/" + domainA + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SolutionCreateRequest(domainA, solutionA, "Sol A"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/" + domainA + "/" + solutionA + "/zones/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneA, "Zone A")))))
            .andExpect(status().isCreated());

        // Create domain B with solution
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DomainCreateRequest(domainB, "Domain B"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/" + domainB + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SolutionCreateRequest(domainB, solutionB, "Sol B"))))
            .andExpect(status().isCreated());
    }

    @AfterEach
    void tearDown() throws Exception {
        mockMvc.perform(delete("/domains/{domainId}", domainA)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/domains/{domainId}", domainB)).andExpect(status().isNoContent());
    }

    /**
     * Register an environment in domain A / solution A, then verify:
     * - Listing via domain A / solution A path returns the environment
     * - Listing via domain B / solution B path does NOT return the environment
     *   (scoped listing, not 403 — that's the gateway's job)
     */
    @Test
    void testEnvironmentScopedToDomainSolution() throws Exception {
        String appName = "memberapp" + System.currentTimeMillis();

        // Register environment in domain A / solution A
        EnvironmentRegistrationRequest req = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneA), null, null));

        mockMvc.perform(post("/" + domainA + "/" + solutionA + "/environments/environment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());

        // List via domain A / solution A — should find the environment
        mockMvc.perform(get("/" + domainA + "/" + solutionA + "/environments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$..environment[?(@.consumerName == '" + appName + "')]").exists());

        // List via domain B / solution B — should NOT find the environment
        // (the controller scopes listing by solutionId from the path)
        mockMvc.perform(get("/" + domainB + "/" + solutionB + "/environments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$..environment[?(@.consumerName == '" + appName + "')]").doesNotExist());
    }
}
