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
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for solution_id uniqueness constraint within a domain.
 * 
 * <p>This test validates that the system enforces uniqueness of solution_id
 * within a single domain. Attempting to create a second solution with the same
 * solution_id in the same domain should result in a 409 Conflict error.</p>
 * 
 * <p><strong>Validates: Requirements 8.6</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class SolutionIdUniquenessWithinDomainIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String testDomainId;
    
    /**
     * Set up test data by creating a parent domain before each test.
     */
    @BeforeEach
    void setUp() throws Exception {
        testDomainId = "test-domain-" + System.currentTimeMillis();
        
        DomainCreateRequest domainRequest = new DomainCreateRequest(testDomainId, "Test domain for solution uniqueness test");
        String domainRequestJson = objectMapper.writeValueAsString(domainRequest);
        
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(domainRequestJson))
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
     * Test that creating a solution with a duplicate solution_id in the same domain returns 409 Conflict.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>First solution creation succeeds (201 Created)</li>
     *   <li>Second solution creation with same solution_id in same domain fails (409 Conflict)</li>
     *   <li>Uniqueness constraint is enforced within the domain scope</li>
     * </ul>
     */
    @Test
    void testSolutionIdUniquenessWithinDomainEnforcement() throws Exception {
        String solutionId = "unique-solution-" + System.currentTimeMillis();
        
        // ========== Step 1: Create First Solution in Domain ==========
        
        SolutionCreateRequest firstRequest = new SolutionCreateRequest(testDomainId, solutionId, "First solution");
        String firstRequestJson = objectMapper.writeValueAsString(firstRequest);
        
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstRequestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.domainId").value(testDomainId))
            .andExpect(jsonPath("$.solutionId").value(solutionId))
            .andExpect(jsonPath("$.description").value("First solution"));
        
        // ========== Step 2: Attempt to Create Second Solution with Same solution_id in Same Domain ==========
        
        SolutionCreateRequest duplicateRequest = new SolutionCreateRequest(testDomainId, solutionId, "Second solution with duplicate ID");
        String duplicateRequestJson = objectMapper.writeValueAsString(duplicateRequest);
        
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateRequestJson))
            .andExpect(status().isConflict());
        
        // ========== Step 3: Verify Only First Solution Exists ==========
        
        mockMvc.perform(get("/" + testDomainId + "/solutions/{solutionId}", solutionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domainId").value(testDomainId))
            .andExpect(jsonPath("$.solutionId").value(solutionId))
            .andExpect(jsonPath("$.description").value("First solution"));
        
        // Clean up
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", solutionId))
            .andExpect(status().isNoContent());
    }
}
