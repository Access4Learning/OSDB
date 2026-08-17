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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for zone_id uniqueness across different solutions.
 * 
 * <p>This test validates that the same zone_id can exist in different solutions.
 * The uniqueness constraint is scoped to (solution_id, zone_id), not globally
 * on zone_id alone.</p>
 * 
 * <p><strong>Validates: Requirements 8.9</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class ZoneIdAcrossDifferentSolutionsIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;

    private String testDomainId;
    private String solutionIdA;
    private String solutionIdB;
    
    /**
     * Set up test data by creating a parent domain and two solutions before each test.
     */
    @BeforeEach
    void setUp() throws Exception {
        testDomainId = "test-domain-" + System.currentTimeMillis();
        solutionIdA = "solution-a-" + System.currentTimeMillis();
        solutionIdB = "solution-b-" + System.currentTimeMillis();
        
        // Create domain
        DomainCreateRequest domainRequest = new DomainCreateRequest(testDomainId, "Test domain for cross-solution zone test");
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(domainRequest)))
            .andExpect(status().isCreated());
        
        // Create solution A
        SolutionCreateRequest solutionARequest = new SolutionCreateRequest(testDomainId, solutionIdA, "Solution A for cross-solution test");
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solutionARequest)))
            .andExpect(status().isCreated());
        
        // Create solution B
        SolutionCreateRequest solutionBRequest = new SolutionCreateRequest(testDomainId, solutionIdB, "Solution B for cross-solution test");
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solutionBRequest)))
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
     * Test that the same zone_id can exist in different solutions.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>Zone creation in solution A succeeds (201 Created)</li>
     *   <li>Zone creation with same zone_id in solution B succeeds (201 Created)</li>
     *   <li>Both zones exist independently with correct solution associations</li>
     *   <li>Uniqueness constraint is scoped to solution, not global</li>
     * </ul>
     */
    @Test
    void testSameZoneIdInDifferentSolutionsSucceeds() throws Exception {
        String zoneId = "shared-zone-" + System.currentTimeMillis();
        
        // ========== Step 1: Create Zone in Solution A ==========
        
        ZoneCreateRequestWrapper requestA = new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, "Zone in solution A"));
        mockMvc.perform(post("/" + testDomainId + "/" + solutionIdA + "/zones/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestA)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.zone.id").value(zoneId))
            .andExpect(jsonPath("$.zone.description").value("Zone in solution A"));

        // ========== Step 2: Create Zone with Same zone_id in Solution B ==========

        ZoneCreateRequestWrapper requestB = new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, "Zone in solution B"));
        mockMvc.perform(post("/" + testDomainId + "/" + solutionIdB + "/zones/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestB)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.zone.id").value(zoneId))
            .andExpect(jsonPath("$.zone.description").value("Zone in solution B"));

        // ========== Step 3: Verify Uniqueness Is Scoped to Solution, Not Global ==========
        // Both creates above returning 201 is itself the proof: a globally-unique zone_id
        // constraint would have rejected the second create (same zoneId, different solution).
        // The internal UUID and solutionId are no longer exposed in the API response
        // (SIF spec compliance), so independent existence is confirmed via each solution's
        // scoped zone listing.
        mockMvc.perform(get("/" + testDomainId + "/" + solutionIdA + "/zones")
                .param("solutionId", solutionIdA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones.zone[?(@.id == '" + zoneId + "')]").exists());

        mockMvc.perform(get("/" + testDomainId + "/" + solutionIdB + "/zones")
                .param("solutionId", solutionIdB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones.zone[?(@.id == '" + zoneId + "')]").exists());
    }
}

