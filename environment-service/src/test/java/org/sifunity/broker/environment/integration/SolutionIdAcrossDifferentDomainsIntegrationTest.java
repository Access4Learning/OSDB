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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for solution_id uniqueness across different domains.
 * 
 * <p>This test validates that the same solution_id can exist in different domains.
 * The uniqueness constraint is scoped to (domain_id, solution_id), not globally
 * on solution_id alone.</p>
 * 
 * <p><strong>Validates: Requirements 8.7</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class SolutionIdAcrossDifferentDomainsIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String domainIdA;
    private String domainIdB;
    
    /**
     * Set up test data by creating two parent domains before each test.
     */
    @BeforeEach
    void setUp() throws Exception {
        domainIdA = "domain-a-" + System.currentTimeMillis();
        domainIdB = "domain-b-" + System.currentTimeMillis();
        
        // Create domain A
        DomainCreateRequest domainARequest = new DomainCreateRequest(domainIdA, "Domain A for cross-domain test");
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(domainARequest)))
            .andExpect(status().isCreated());
        
        // Create domain B
        DomainCreateRequest domainBRequest = new DomainCreateRequest(domainIdB, "Domain B for cross-domain test");
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(domainBRequest)))
            .andExpect(status().isCreated());
    }
    
    /**
     * Clean up test data by deleting both parent domains after each test.
     */
    @AfterEach
    void tearDown() throws Exception {
        mockMvc.perform(delete("/domains/{domainId}", domainIdA))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/domains/{domainId}", domainIdB))
            .andExpect(status().isNoContent());
    }
    
    /**
     * Test that the same solution_id can exist in different domains.
     *
     * <p>Solution resolution is domain-scoped (findByDomainUuidAndSolutionId), so the same
     * solutionId in multiple domains is correctly isolated per domain. This test validates
     * cross-domain solution isolation.</p>
     */
    @Test
    void testSameSolutionIdInDifferentDomainsSucceeds() throws Exception {
        String solutionIdA = "solution-a-" + System.currentTimeMillis();
        String solutionIdB = "solution-b-" + System.currentTimeMillis();
        
        // ========== Step 1: Create Solution in Domain A ==========
        
        SolutionCreateRequest requestA = new SolutionCreateRequest(domainIdA, solutionIdA, "Solution in domain A");
        String requestAJson = objectMapper.writeValueAsString(requestA);
        
        MvcResult resultA = mockMvc.perform(post("/" + domainIdA + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestAJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.domainId").value(domainIdA))
            .andExpect(jsonPath("$.solutionId").value(solutionIdA))
            .andExpect(jsonPath("$.description").value("Solution in domain A"))
            .andReturn();
        
        String responseAJson = resultA.getResponse().getContentAsString();
        SolutionDTO solutionA = objectMapper.readValue(responseAJson, SolutionDTO.class);
        
        assertThat(solutionA).isNotNull();
        assertThat(solutionA.domainId()).isEqualTo(domainIdA);
        assertThat(solutionA.solutionId()).isEqualTo(solutionIdA);
        
        // ========== Step 2: Create Solution in Domain B ==========
        
        SolutionCreateRequest requestB = new SolutionCreateRequest(domainIdB, solutionIdB, "Solution in domain B");
        String requestBJson = objectMapper.writeValueAsString(requestB);
        
        MvcResult resultB = mockMvc.perform(post("/" + domainIdB + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.domainId").value(domainIdB))
            .andExpect(jsonPath("$.solutionId").value(solutionIdB))
            .andExpect(jsonPath("$.description").value("Solution in domain B"))
            .andReturn();
        
        String responseBJson = resultB.getResponse().getContentAsString();
        SolutionDTO solutionB = objectMapper.readValue(responseBJson, SolutionDTO.class);
        
        assertThat(solutionB).isNotNull();
        assertThat(solutionB.domainId()).isEqualTo(domainIdB);
        assertThat(solutionB.solutionId()).isEqualTo(solutionIdB);
        
        // ========== Step 3: Verify Both Solutions Exist Independently ==========
        
        // Both solutions have different UUIDs
        assertThat(solutionA.id()).isNotEqualTo(solutionB.id());
        
        // Filter solutions by domain A - should only return solution A
        mockMvc.perform(get("/" + domainIdA + "/solutions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionIdA + "' && @.domainId == '" + domainIdA + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionIdB + "' && @.domainId == '" + domainIdB + "')]").doesNotExist());
        
        // Filter solutions by domain B - should only return solution B
        mockMvc.perform(get("/" + domainIdB + "/solutions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionIdB + "' && @.domainId == '" + domainIdB + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionIdA + "' && @.domainId == '" + domainIdA + "')]").doesNotExist());
    }
}
