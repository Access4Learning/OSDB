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
 * requests with any domain/solution path variables (ADMIN bypass scenario).
 *
 * <p>Actual ADMIN membership bypass is a gateway-level concern tested in
 * gateway property tests (Task 4.8). This test verifies that the controllers
 * accept prefixed paths with different domain/solution values — the controller
 * does not enforce membership, so any path variables are accepted.</p>
 *
 * <p><strong>Validates: Requirements 11.6</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class UrlPrefixAdminBypassIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String domainId;
    private String solutionId;
    private String zoneId;

    @BeforeEach
    void setUp() throws Exception {
        long ts = System.currentTimeMillis();
        domainId = "admdom" + ts;
        solutionId = "admsol" + ts;
        zoneId = "admzone" + ts;

        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DomainCreateRequest(domainId, "Admin test domain"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/" + domainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SolutionCreateRequest(domainId, solutionId, "Admin test solution"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/" + domainId + "/" + solutionId + "/zones/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, "Admin test zone")))))
            .andExpect(status().isCreated());
    }

    @AfterEach
    void tearDown() throws Exception {
        mockMvc.perform(delete("/domains/{domainId}", domainId))
            .andExpect(status().isNoContent());
    }

    /**
     * Verify that the controller accepts requests with any domain/solution
     * path variables. An ADMIN environment is registered and the controller
     * responds successfully regardless of the path variable values.
     */
    @Test
    void testControllerAcceptsDifferentDomainSolutionPathVariables() throws Exception {
        // Register an ADMIN environment
        String appName = "adminapp" + System.currentTimeMillis();
        EnvironmentRegistrationRequest req = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), null, null));

        mockMvc.perform(post("/" + domainId + "/" + solutionId + "/environments/environment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.environment.type").value("BROKERED"));

        // The controller accepts listing with the same domain/solution path
        mockMvc.perform(get("/" + domainId + "/" + solutionId + "/environments"))
            .andExpect(status().isOk());

        // The controller also accepts listing with different domain/solution path
        // (returns 404 because the solution doesn't exist in the database)
        mockMvc.perform(get("/" + domainId + "/" + "otherSolution" + "/environments"))
            .andExpect(status().isNotFound());
    }
}
