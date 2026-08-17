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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Integration test for environment registration with solution_id.
 * 
 * <p>This test validates that environments can be registered with a solution_id
 * reference, and that the solution_id is properly stored and returned in the
 * registration response.</p>
 * 
 * <p><strong>Validates: Requirements 8.10</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class EnvironmentRegistrationWithSolutionIdIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String testDomainId;
    private String testSolutionId;
    private String testZoneId;
    
    /**
     * Set up test data by creating a parent domain, solution, and zone before each test.
     */
    @BeforeEach
    void setUp() throws Exception {
        testDomainId = "test-domain-" + System.currentTimeMillis();
        testSolutionId = "test-solution-" + System.currentTimeMillis();
        testZoneId = "test-zone-" + System.currentTimeMillis();
        
        // Create domain
        DomainCreateRequest domainRequest = new DomainCreateRequest(testDomainId, "Test domain for environment registration test");
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(domainRequest)))
            .andExpect(status().isCreated());
        
        // Create solution
        SolutionCreateRequest solutionRequest = new SolutionCreateRequest(testDomainId, testSolutionId, "Test solution for environment registration test");
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solutionRequest)))
            .andExpect(status().isCreated());
        
        // Create zone
        ZoneCreateRequestWrapper zoneRequest = new ZoneCreateRequestWrapper(
            new ZoneCreateBody(testZoneId, "Test zone for environment registration test"));
        mockMvc.perform(post("/" + testDomainId + "/" + testSolutionId + "/zones/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(zoneRequest)))
            .andExpect(status().isCreated());
    }
    
    /**
     * Clean up test data by deleting the parent domain after each test.
     */
    @AfterEach
    void tearDown() throws Exception {
        mockMvc.perform(delete("/domains/{domainId}", testDomainId))
            .andExpect(status().isNoContent());
    }
    
    /**
     * Test that registering an environment with solution_id succeeds and returns the solution_id.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>Environment registration with solution_id succeeds (201 Created)</li>
     *   <li>Registration response includes the solution_id</li>
     *   <li>Environment is properly associated with the solution</li>
     *   <li>Environment can be retrieved with solution_id</li>
     * </ul>
     */
    @Test
    void testEnvironmentRegistrationWithSolutionId() throws Exception {
        String applicationName = "test-app-" + System.currentTimeMillis();
        
        // ========== Step 1: Register Environment with solution_id ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, EnvironmentType.BROKERED, DefaultZoneDTO.of(testZoneId), testSolutionId, null));
        String registrationRequestJson = objectMapper.writeValueAsString(registrationRequest);
        
        MvcResult registrationResult = mockMvc.perform(post("/" + testDomainId + "/" + testSolutionId + "/environments/environment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationRequestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.environment.id").exists())
            .andExpect(jsonPath("$.environment.consumerName").value(applicationName))
            .andExpect(jsonPath("$.environment.sessionToken").exists())
            .andExpect(jsonPath("$.environment.sharedSecret").exists())
            .andExpect(jsonPath("$.environment.defaultZone.id").value(testZoneId))
            .andExpect(jsonPath("$.environment.type").value("BROKERED"))
            .andExpect(jsonPath("$.environment.solutionId").value(testSolutionId))
            .andReturn();
        
        String registrationResponseJson = registrationResult.getResponse().getContentAsString();
        JsonNode regNode = objectMapper.readTree(registrationResponseJson);
        
        assertThat(regNode).isNotNull();
        JsonNode envNode = regNode.get("environment");
        assertThat(envNode.get("id").asText()).isNotEmpty();
        assertThat(envNode.get("consumerName").asText()).isEqualTo(applicationName);
        assertThat(envNode.get("solutionId").asText()).isEqualTo(testSolutionId);
        assertThat(envNode.get("sessionToken").asText()).isNotBlank();
        assertThat(envNode.get("sharedSecret").asText()).isNotBlank();
        
        String environmentId = envNode.get("id").asText();
        
        // ========== Step 2: Retrieve Environment and Verify solution_id ==========
        
        mockMvc.perform(get("/" + testDomainId + "/" + testSolutionId + "/environments/{id}", environmentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.environment.id").value(environmentId))
            .andExpect(jsonPath("$.environment.consumerName").value(applicationName))
            .andExpect(jsonPath("$.environment.solutionId").value(testSolutionId));
        
        // ========== Step 3: Filter Environments by solution_id ==========
        
        mockMvc.perform(get("/" + testDomainId + "/" + testSolutionId + "/environments")
                .param("solutionId", testSolutionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.environments.environment[?(@.consumerName == '" + applicationName + "')]").exists())
            .andExpect(jsonPath("$.environments.environment[?(@.consumerName == '" + applicationName + "')].solutionId").value(testSolutionId));
        
        // Clean up
        mockMvc.perform(delete("/" + testDomainId + "/" + testSolutionId + "/environments/{id}", environmentId))
            .andExpect(status().isNoContent());
    }
}



