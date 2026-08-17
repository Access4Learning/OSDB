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
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.dto.SolutionUpdateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for solution CRUD operations.
 * 
 * <p>This test validates the complete CRUD lifecycle of solutions through the REST API:
 * <ol>
 *   <li>Create solution (POST /solutions)</li>
 *   <li>Retrieve solution by ID (GET /solutions/{solutionId})</li>
 *   <li>List all solutions (GET /solutions)</li>
 *   <li>List solutions filtered by domain (GET /solutions?domainId={domainId})</li>
 *   <li>Update solution (PUT /solutions/{solutionId})</li>
 *   <li>Delete solution (DELETE /solutions/{solutionId})</li>
 * </ol>
 * 
 * <p><strong>Validates: Requirements 8.2</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class SolutionCrudIntegrationTest {
    
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
        
        DomainCreateRequest domainRequest = new DomainCreateRequest(testDomainId, "Test domain for solution tests");
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
        // Delete domain (cascade deletes all solutions)
        mockMvc.perform(delete("/domains/{domainId}", testDomainId))
            .andExpect(status().isNoContent());
    }
    
    /**
     * Test the complete solution CRUD lifecycle.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>POST /solutions returns 201 Created with solution data</li>
     *   <li>GET /solutions/{solutionId} returns 200 OK with solution data</li>
     *   <li>GET /solutions returns 200 OK with list of solutions</li>
     *   <li>GET /solutions?domainId={domainId} returns 200 OK with filtered list</li>
     *   <li>PUT /solutions/{solutionId} returns 200 OK with updated solution data</li>
     *   <li>DELETE /solutions/{solutionId} returns 204 No Content</li>
     *   <li>GET /solutions/{solutionId} after deletion returns 404 Not Found</li>
     * </ul>
     */
    @Test
    void testSolutionCrudLifecycle() throws Exception {
        String solutionId = "test-solution-" + System.currentTimeMillis();
        String description = "Test solution for integration testing";
        
        // ========== Step 1: Create Solution (POST /solutions) ==========
        
        SolutionCreateRequest createRequest = new SolutionCreateRequest(testDomainId, solutionId, description);
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        MvcResult createResult = mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.domainId").value(testDomainId))
            .andExpect(jsonPath("$.solutionId").value(solutionId))
            .andExpect(jsonPath("$.description").value(description))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn();
        
        String createResponseJson = createResult.getResponse().getContentAsString();
        SolutionDTO createdSolution = objectMapper.readValue(createResponseJson, SolutionDTO.class);
        
        assertThat(createdSolution).isNotNull();
        assertThat(createdSolution.id()).isNotNull();
        assertThat(createdSolution.domainId()).isEqualTo(testDomainId);
        assertThat(createdSolution.solutionId()).isEqualTo(solutionId);
        assertThat(createdSolution.description()).isEqualTo(description);
        assertThat(createdSolution.createdAt()).isNotNull();
        
        // ========== Step 2: Retrieve Solution (GET /solutions/{solutionId}) ==========
        
        MvcResult getResult = mockMvc.perform(get("/" + testDomainId + "/solutions/{solutionId}", solutionId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(createdSolution.id().toString()))
            .andExpect(jsonPath("$.domainId").value(testDomainId))
            .andExpect(jsonPath("$.solutionId").value(solutionId))
            .andExpect(jsonPath("$.description").value(description))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn();
        
        String getResponseJson = getResult.getResponse().getContentAsString();
        SolutionDTO retrievedSolution = objectMapper.readValue(getResponseJson, SolutionDTO.class);
        
        assertThat(retrievedSolution).isNotNull();
        assertThat(retrievedSolution.id()).isEqualTo(createdSolution.id());
        assertThat(retrievedSolution.domainId()).isEqualTo(testDomainId);
        assertThat(retrievedSolution.solutionId()).isEqualTo(solutionId);
        assertThat(retrievedSolution.description()).isEqualTo(description);
        assertThat(retrievedSolution.createdAt()).isEqualTo(createdSolution.createdAt());
        
        // ========== Step 3: List All Solutions (GET /solutions) ==========
        
        mockMvc.perform(get("/" + testDomainId + "/solutions"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.solutions.solution").isArray())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId + "')].id").value(createdSolution.id().toString()))
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId + "')].domainId").value(testDomainId))
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId + "')].description").value(description));
        
        // ========== Step 4: List Solutions Filtered by Domain (GET /{domainId}/solutions) ==========
        
        mockMvc.perform(get("/" + testDomainId + "/solutions"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.solutions.solution").isArray())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId + "')].domainId").value(testDomainId));
        
        // ========== Step 5: Update Solution (PUT /solutions/{solutionId}) ==========
        
        String updatedDescription = "Updated description for test solution";
        SolutionUpdateRequest updateRequest = new SolutionUpdateRequest(updatedDescription);
        String updateRequestJson = objectMapper.writeValueAsString(updateRequest);
        
        MvcResult updateResult = mockMvc.perform(put("/" + testDomainId + "/solutions/{solutionId}", solutionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestJson))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(createdSolution.id().toString()))
            .andExpect(jsonPath("$.domainId").value(testDomainId))
            .andExpect(jsonPath("$.solutionId").value(solutionId))
            .andExpect(jsonPath("$.description").value(updatedDescription))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn();
        
        String updateResponseJson = updateResult.getResponse().getContentAsString();
        SolutionDTO updatedSolution = objectMapper.readValue(updateResponseJson, SolutionDTO.class);
        
        assertThat(updatedSolution).isNotNull();
        assertThat(updatedSolution.id()).isEqualTo(createdSolution.id());
        assertThat(updatedSolution.domainId()).isEqualTo(testDomainId);
        assertThat(updatedSolution.solutionId()).isEqualTo(solutionId);
        assertThat(updatedSolution.description()).isEqualTo(updatedDescription);
        assertThat(updatedSolution.createdAt()).isEqualTo(createdSolution.createdAt());
        
        // ========== Step 6: Delete Solution (DELETE /solutions/{solutionId}) ==========
        
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", solutionId))
            .andExpect(status().isNoContent());
        
        // ========== Step 7: Verify Solution is Deleted (GET /solutions/{solutionId}) ==========
        
        mockMvc.perform(get("/" + testDomainId + "/solutions/{solutionId}", solutionId))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test creating a solution with minimal data (no description).
     */
    @Test
    void testCreateSolutionWithoutDescription() throws Exception {
        String solutionId = "minimal-solution-" + System.currentTimeMillis();
        
        SolutionCreateRequest createRequest = new SolutionCreateRequest(testDomainId, solutionId, null);
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        MvcResult createResult = mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.domainId").value(testDomainId))
            .andExpect(jsonPath("$.solutionId").value(solutionId))
            .andExpect(jsonPath("$.description").isEmpty())
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn();
        
        String createResponseJson = createResult.getResponse().getContentAsString();
        SolutionDTO createdSolution = objectMapper.readValue(createResponseJson, SolutionDTO.class);
        
        assertThat(createdSolution.description()).isNull();
        
        // Clean up
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", solutionId))
            .andExpect(status().isNoContent());
    }
    
    /**
     * Test creating a solution with non-existent domain returns 404 Not Found.
     */
    @Test
    void testCreateSolutionWithNonExistentDomainReturnsNotFound() throws Exception {
        String nonExistentDomainId = "non-existent-domain-" + System.currentTimeMillis();
        String solutionId = "test-solution-" + System.currentTimeMillis();
        
        SolutionCreateRequest createRequest = new SolutionCreateRequest(nonExistentDomainId, solutionId, "Test solution");
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        mockMvc.perform(post("/" + nonExistentDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test creating a solution with duplicate solution ID in same domain returns 409 Conflict.
     */
    @Test
    void testCreateDuplicateSolutionInSameDomainReturnsConflict() throws Exception {
        String solutionId = "duplicate-solution-" + System.currentTimeMillis();
        
        SolutionCreateRequest createRequest = new SolutionCreateRequest(testDomainId, solutionId, "First solution");
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        // Create first solution
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isCreated());
        
        // Attempt to create duplicate solution in same domain
        SolutionCreateRequest duplicateRequest = new SolutionCreateRequest(testDomainId, solutionId, "Duplicate solution");
        String duplicateRequestJson = objectMapper.writeValueAsString(duplicateRequest);
        
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateRequestJson))
            .andExpect(status().isConflict());
        
        // Clean up
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", solutionId))
            .andExpect(status().isNoContent());
    }
    
    /**
     * Test retrieving a non-existent solution returns 404 Not Found.
     */
    @Test
    void testGetNonExistentSolutionReturnsNotFound() throws Exception {
        String nonExistentSolutionId = "non-existent-solution-" + System.currentTimeMillis();
        
        mockMvc.perform(get("/" + testDomainId + "/solutions/{solutionId}", nonExistentSolutionId))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test updating a non-existent solution returns 404 Not Found.
     */
    @Test
    void testUpdateNonExistentSolutionReturnsNotFound() throws Exception {
        String nonExistentSolutionId = "non-existent-solution-" + System.currentTimeMillis();
        
        SolutionUpdateRequest updateRequest = new SolutionUpdateRequest("Updated description");
        String updateRequestJson = objectMapper.writeValueAsString(updateRequest);
        
        mockMvc.perform(put("/" + testDomainId + "/solutions/{solutionId}", nonExistentSolutionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestJson))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test deleting a non-existent solution returns 404 Not Found.
     */
    @Test
    void testDeleteNonExistentSolutionReturnsNotFound() throws Exception {
        String nonExistentSolutionId = "non-existent-solution-" + System.currentTimeMillis();
        
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", nonExistentSolutionId))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test creating a solution with invalid solution ID returns 400 Bad Request.
     */
    @Test
    void testCreateSolutionWithInvalidIdReturnsBadRequest() throws Exception {
        // Solution ID starting with non-alphanumeric character
        String invalidSolutionId = "-invalid-solution";
        
        SolutionCreateRequest createRequest = new SolutionCreateRequest(testDomainId, invalidSolutionId, "Invalid solution");
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isBadRequest());
    }
    
    /**
     * Test creating multiple solutions in same domain and listing them.
     */
    @Test
    void testCreateMultipleSolutionsInSameDomainAndList() throws Exception {
        String solutionId1 = "multi-solution-1-" + System.currentTimeMillis();
        String solutionId2 = "multi-solution-2-" + System.currentTimeMillis();
        String solutionId3 = "multi-solution-3-" + System.currentTimeMillis();
        
        // Create three solutions in the same domain
        SolutionCreateRequest request1 = new SolutionCreateRequest(testDomainId, solutionId1, "First solution");
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());
        
        SolutionCreateRequest request2 = new SolutionCreateRequest(testDomainId, solutionId2, "Second solution");
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated());
        
        SolutionCreateRequest request3 = new SolutionCreateRequest(testDomainId, solutionId3, "Third solution");
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3)))
            .andExpect(status().isCreated());
        
        // List all solutions and verify all three exist
        mockMvc.perform(get("/" + testDomainId + "/solutions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId1 + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId2 + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId3 + "')]").exists());
        
        // List solutions filtered by domain and verify all three exist
        mockMvc.perform(get("/" + testDomainId + "/solutions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId1 + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId2 + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionId3 + "')]").exists());
        
        // Clean up
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", solutionId1)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", solutionId2)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", solutionId3)).andExpect(status().isNoContent());
    }
    
    /**
     * Test filtering solutions by domain ID.
     */
    @Test
    void testFilterSolutionsByDomain() throws Exception {
        // Create a second domain
        String secondDomainId = "second-domain-" + System.currentTimeMillis();
        DomainCreateRequest secondDomainRequest = new DomainCreateRequest(secondDomainId, "Second test domain");
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondDomainRequest)))
            .andExpect(status().isCreated());
        
        // Create solutions in first domain
        String solution1InDomain1 = "solution1-domain1-" + System.currentTimeMillis();
        String solution2InDomain1 = "solution2-domain1-" + System.currentTimeMillis();
        
        SolutionCreateRequest request1 = new SolutionCreateRequest(testDomainId, solution1InDomain1, "Solution 1 in domain 1");
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());
        
        SolutionCreateRequest request2 = new SolutionCreateRequest(testDomainId, solution2InDomain1, "Solution 2 in domain 1");
        mockMvc.perform(post("/" + testDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated());
        
        // Create solution in second domain
        String solutionInDomain2 = "solution-domain2-" + System.currentTimeMillis();
        SolutionCreateRequest request3 = new SolutionCreateRequest(secondDomainId, solutionInDomain2, "Solution in domain 2");
        mockMvc.perform(post("/" + secondDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3)))
            .andExpect(status().isCreated());
        
        // Filter by first domain - should return only solutions from first domain
        MvcResult domain1Result = mockMvc.perform(get("/" + testDomainId + "/solutions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solution1InDomain1 + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solution2InDomain1 + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionInDomain2 + "')]").doesNotExist())
            .andReturn();
        
        // Filter by second domain - should return only solution from second domain
        MvcResult domain2Result = mockMvc.perform(get("/" + secondDomainId + "/solutions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solutionInDomain2 + "')]").exists())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solution1InDomain1 + "')]").doesNotExist())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solution2InDomain1 + "')]").doesNotExist())
            .andReturn();
        
        // Clean up
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", solution1InDomain1)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/" + testDomainId + "/solutions/{solutionId}", solution2InDomain1)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/" + secondDomainId + "/solutions/{solutionId}", solutionInDomain2)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/domains/{domainId}", secondDomainId)).andExpect(status().isNoContent());
    }
}
