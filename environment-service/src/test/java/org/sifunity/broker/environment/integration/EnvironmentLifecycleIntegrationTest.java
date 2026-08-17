// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.test.TestEntityHierarchy;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Integration test for environment lifecycle via MockMvc.
 *
 * <p>Validates register → get → list → delete → verify-gone flow
 * using the prefixed URL pattern {@code /{domainId}/{solutionId}/environments}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class EnvironmentLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TestEntityHierarchy hierarchy;

    @BeforeEach
    void setUp() throws Exception {
        hierarchy = TestEntityHierarchy.createViaMockMvc(mockMvc, objectMapper);
    }

    @Test
    void testEnvironmentLifecycleViaMockMvc() throws Exception {
        String basePath = hierarchy.resourceBasePath() + "/environments";
        String appName = "lifecycle-app-" + System.currentTimeMillis();

        // Register
        EnvironmentRegistrationRequest req = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));

        MvcResult regResult = mockMvc.perform(post(basePath + "/environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.environment.consumerName").value(appName))
                .andExpect(jsonPath("$.environment.type").value("BROKERED"))
                .andExpect(jsonPath("$.environment.sessionToken").exists())
                .andExpect(jsonPath("$.environment.sharedSecret").exists())
                .andReturn();

        String envId = objectMapper.readTree(
                regResult.getResponse().getContentAsString()).get("environment").get("id").asText();

        // Get by ID
        mockMvc.perform(get(basePath + "/{id}", envId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment.id").value(envId))
                .andExpect(jsonPath("$.environment.consumerName").value(appName));

        // List (SIF collection wrapper)
        mockMvc.perform(get(basePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environments.environment").isArray())
                .andExpect(jsonPath("$.environments.environment[?(@.consumerName == '" + appName + "')]").exists());

        // Delete
        mockMvc.perform(delete(basePath + "/{id}", envId))
                .andExpect(status().isNoContent());

        // Verify gone
        mockMvc.perform(get(basePath + "/{id}", envId))
                .andExpect(status().isNotFound());
    }
}
