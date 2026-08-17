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
import org.sifunity.broker.environment.dto.ZoneCreateBody;
import org.sifunity.broker.environment.dto.ZoneCreateRequestWrapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for zone_id uniqueness constraint within a solution.
 * 
 * <p>This test validates that the system enforces uniqueness of zone_id
 * within a single solution. Attempting to create a second zone with the same
 * zone_id in the same solution should result in a 409 Conflict error.</p>
 * 
 * <p><strong>Validates: Requirements 8.8</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class ZoneIdUniquenessWithinSolutionIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String testDomainId;
    private String testSolutionId;
    
    /**
     * Set up test data by creating a parent domain and solution before each test.
     */
    @BeforeEach
    void setUp() throws Exception {
        testDomainId = "test-domain-" + System.currentTimeMillis();
        testSolutionId = "test-solution-" + System.currentTimeMillis();
        
        // Create domain
        DomainCreateRequest domainRequest = new DomainCreateRequest(testDomainId, "Test domain for zone uniqueness test");
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(domainRequest)))
            .andExpect(status().isCreated());
        
        // Create solution
        SolutionCreateRequest solutionRequest = new SolutionCreateRequest(testDomainId, testSolutionId, "Test solution for zone uniqueness test");
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solutionRequest)))
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
     * Test that creating a zone with a duplicate zone_id in the same solution returns 409 Conflict.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>First zone creation succeeds (201 Created)</li>
     *   <li>Second zone creation with same zone_id in same solution fails (409 Conflict)</li>
     *   <li>Uniqueness constraint is enforced within the solution scope</li>
     * </ul>
     */
    @Test
    void testZoneIdUniquenessWithinSolutionEnforcement() throws Exception {
        String zoneId = "unique-zone-" + System.currentTimeMillis();
        
        // ========== Step 1: Create First Zone in Solution ==========
        
        ZoneCreateRequestWrapper firstRequest = new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, "First zone"));
        String firstRequestJson = objectMapper.writeValueAsString(firstRequest);
        
        mockMvc.perform(post("/" + testDomainId + "/" + testSolutionId + "/zones/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstRequestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.zone.id").value(zoneId))
            .andExpect(jsonPath("$.zone.description").value("First zone"));
        
        // ========== Step 2: Attempt to Create Second Zone with Same zone_id in Same Solution ==========
        
        ZoneCreateRequestWrapper duplicateRequest = new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, "Second zone with duplicate ID"));
        String duplicateRequestJson = objectMapper.writeValueAsString(duplicateRequest);
        
        mockMvc.perform(post("/" + testDomainId + "/" + testSolutionId + "/zones/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateRequestJson))
            .andExpect(status().isConflict());
        
        // ========== Step 3: Verify Only First Zone Exists ==========
        
        mockMvc.perform(get("/" + testDomainId + "/" + testSolutionId + "/zones")
                .param("solutionId", testSolutionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones.zone[?(@.id == '" + zoneId + "')]").exists())
            .andExpect(jsonPath("$.zones.zone[?(@.id == '" + zoneId + "')].description").value("First zone"));
    }
}

