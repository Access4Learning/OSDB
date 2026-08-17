// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
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
 * Integration test for environment CRUD operations with the URL prefix.
 *
 * <p>Tests create, retrieve, list, and delete via
 * {@code /{domainId}/{solutionId}/environments}.</p>
 *
 * <p><strong>Validates: Requirements 11.1</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class UrlPrefixEnvironmentCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String domainId;
    private String solutionId;
    private String zoneId;

    @BeforeEach
    void setUp() throws Exception {
        domainId = "intdom" + System.currentTimeMillis();
        solutionId = "intsol" + System.currentTimeMillis();
        zoneId = "intzone" + System.currentTimeMillis();

        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DomainCreateRequest(domainId, "test"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/" + domainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SolutionCreateRequest(domainId, solutionId, "test"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/" + domainId + "/" + solutionId + "/zones/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, "test")))))
            .andExpect(status().isCreated());
    }

    @AfterEach
    void tearDown() throws Exception {
        mockMvc.perform(delete("/domains/{domainId}", domainId))
            .andExpect(status().isNoContent());
    }

    /**
     * Full CRUD lifecycle: create → retrieve → list → delete → verify gone.
     */
    @Test
    void testEnvironmentCrudWithPrefixedPath() throws Exception {
        String appName = "crudapp" + System.currentTimeMillis();
        String basePath = "/" + domainId + "/" + solutionId + "/environments";

        // CREATE
        EnvironmentRegistrationRequest req = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), null, null));

        MvcResult createResult = mockMvc.perform(post(basePath + "/environment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.environment.id").exists())
            .andExpect(jsonPath("$.environment.consumerName").value(appName))
            .andExpect(jsonPath("$.environment.solutionId").value(solutionId))
            .andReturn();

        String envId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("environment").get("id").asText();

        // RETRIEVE
        mockMvc.perform(get(basePath + "/{id}", envId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.environment.id").value(envId))
            .andExpect(jsonPath("$.environment.consumerName").value(appName));

        // LIST
        mockMvc.perform(get(basePath))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$..environment[?(@.id == '" + envId + "')]").exists());

        // DELETE
        mockMvc.perform(delete(basePath + "/{id}", envId))
            .andExpect(status().isNoContent());

        // VERIFY GONE
        mockMvc.perform(get(basePath + "/{id}", envId))
            .andExpect(status().isNotFound());
    }
}
