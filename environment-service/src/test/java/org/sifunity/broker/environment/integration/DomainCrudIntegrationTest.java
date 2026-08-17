// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.sifunity.broker.environment.dto.DomainUpdateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for domain CRUD operations.
 * 
 * <p>This test validates the complete CRUD lifecycle of domains through the REST API:
 * <ol>
 *   <li>Create domain (POST /domains)</li>
 *   <li>Retrieve domain by ID (GET /domains/{domainId})</li>
 *   <li>List all domains (GET /domains)</li>
 *   <li>Update domain (PUT /domains/{domainId})</li>
 *   <li>Delete domain (DELETE /domains/{domainId})</li>
 * </ol>
 * 
 * <p><strong>Validates: Requirements 8.1</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class DomainCrudIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Test the complete domain CRUD lifecycle.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>POST /domains returns 201 Created with domain data</li>
     *   <li>GET /domains/{domainId} returns 200 OK with domain data</li>
     *   <li>GET /domains returns 200 OK with list of domains</li>
     *   <li>PUT /domains/{domainId} returns 200 OK with updated domain data</li>
     *   <li>DELETE /domains/{domainId} returns 204 No Content</li>
     *   <li>GET /domains/{domainId} after deletion returns 404 Not Found</li>
     * </ul>
     */
    @Test
    void testDomainCrudLifecycle() throws Exception {
        String domainId = "test-domain-" + System.currentTimeMillis();
        String description = "Test domain for integration testing";
        
        // ========== Step 1: Create Domain (POST /domains) ==========
        
        DomainCreateRequest createRequest = new DomainCreateRequest(domainId, description);
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        MvcResult createResult = mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.domainId").value(domainId))
            .andExpect(jsonPath("$.description").value(description))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn();
        
        String createResponseJson = createResult.getResponse().getContentAsString();
        DomainDTO createdDomain = objectMapper.readValue(createResponseJson, DomainDTO.class);
        
        assertThat(createdDomain).isNotNull();
        assertThat(createdDomain.id()).isNotNull();
        assertThat(createdDomain.domainId()).isEqualTo(domainId);
        assertThat(createdDomain.description()).isEqualTo(description);
        assertThat(createdDomain.createdAt()).isNotNull();
        
        // ========== Step 2: Retrieve Domain (GET /domains/{domainId}) ==========
        
        MvcResult getResult = mockMvc.perform(get("/domains/{domainId}", domainId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(createdDomain.id().toString()))
            .andExpect(jsonPath("$.domainId").value(domainId))
            .andExpect(jsonPath("$.description").value(description))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn();
        
        String getResponseJson = getResult.getResponse().getContentAsString();
        DomainDTO retrievedDomain = objectMapper.readValue(getResponseJson, DomainDTO.class);
        
        assertThat(retrievedDomain).isNotNull();
        assertThat(retrievedDomain.id()).isEqualTo(createdDomain.id());
        assertThat(retrievedDomain.domainId()).isEqualTo(domainId);
        assertThat(retrievedDomain.description()).isEqualTo(description);
        assertThat(retrievedDomain.createdAt()).isEqualTo(createdDomain.createdAt());
        
        // ========== Step 3: List All Domains (GET /domains) ==========
        
        mockMvc.perform(get("/domains"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.domains.domain").isArray())
            .andExpect(jsonPath("$.domains.domain[?(@.domainId == '" + domainId + "')]").exists())
            .andExpect(jsonPath("$.domains.domain[?(@.domainId == '" + domainId + "')].id").value(createdDomain.id().toString()))
            .andExpect(jsonPath("$.domains.domain[?(@.domainId == '" + domainId + "')].description").value(description));
        
        // ========== Step 4: Update Domain (PUT /domains/{domainId}) ==========
        
        String updatedDescription = "Updated description for test domain";
        DomainUpdateRequest updateRequest = new DomainUpdateRequest(updatedDescription);
        String updateRequestJson = objectMapper.writeValueAsString(updateRequest);
        
        MvcResult updateResult = mockMvc.perform(put("/domains/{domainId}", domainId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestJson))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(createdDomain.id().toString()))
            .andExpect(jsonPath("$.domainId").value(domainId))
            .andExpect(jsonPath("$.description").value(updatedDescription))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn();
        
        String updateResponseJson = updateResult.getResponse().getContentAsString();
        DomainDTO updatedDomain = objectMapper.readValue(updateResponseJson, DomainDTO.class);
        
        assertThat(updatedDomain).isNotNull();
        assertThat(updatedDomain.id()).isEqualTo(createdDomain.id());
        assertThat(updatedDomain.domainId()).isEqualTo(domainId);
        assertThat(updatedDomain.description()).isEqualTo(updatedDescription);
        assertThat(updatedDomain.createdAt()).isEqualTo(createdDomain.createdAt());
        
        // ========== Step 5: Delete Domain (DELETE /domains/{domainId}) ==========
        
        mockMvc.perform(delete("/domains/{domainId}", domainId))
            .andExpect(status().isNoContent());
        
        // ========== Step 6: Verify Domain is Deleted (GET /domains/{domainId}) ==========
        
        mockMvc.perform(get("/domains/{domainId}", domainId))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test creating a domain with minimal data (no description).
     */
    @Test
    void testCreateDomainWithoutDescription() throws Exception {
        String domainId = "minimal-domain-" + System.currentTimeMillis();
        
        DomainCreateRequest createRequest = new DomainCreateRequest(domainId, null);
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        MvcResult createResult = mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.domainId").value(domainId))
            .andExpect(jsonPath("$.description").isEmpty())
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn();
        
        String createResponseJson = createResult.getResponse().getContentAsString();
        DomainDTO createdDomain = objectMapper.readValue(createResponseJson, DomainDTO.class);
        
        assertThat(createdDomain.description()).isNull();
        
        // Clean up
        mockMvc.perform(delete("/domains/{domainId}", domainId))
            .andExpect(status().isNoContent());
    }
    
    /**
     * Test creating a domain with duplicate domain ID returns 409 Conflict.
     */
    @Test
    void testCreateDuplicateDomainReturnsConflict() throws Exception {
        String domainId = "duplicate-domain-" + System.currentTimeMillis();
        
        DomainCreateRequest createRequest = new DomainCreateRequest(domainId, "First domain");
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        // Create first domain
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isCreated());
        
        // Attempt to create duplicate domain
        DomainCreateRequest duplicateRequest = new DomainCreateRequest(domainId, "Duplicate domain");
        String duplicateRequestJson = objectMapper.writeValueAsString(duplicateRequest);
        
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateRequestJson))
            .andExpect(status().isConflict());
        
        // Clean up
        mockMvc.perform(delete("/domains/{domainId}", domainId))
            .andExpect(status().isNoContent());
    }
    
    /**
     * Test retrieving a non-existent domain returns 404 Not Found.
     */
    @Test
    void testGetNonExistentDomainReturnsNotFound() throws Exception {
        String nonExistentDomainId = "non-existent-domain-" + System.currentTimeMillis();
        
        mockMvc.perform(get("/domains/{domainId}", nonExistentDomainId))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test updating a non-existent domain returns 404 Not Found.
     */
    @Test
    void testUpdateNonExistentDomainReturnsNotFound() throws Exception {
        String nonExistentDomainId = "non-existent-domain-" + System.currentTimeMillis();
        
        DomainUpdateRequest updateRequest = new DomainUpdateRequest("Updated description");
        String updateRequestJson = objectMapper.writeValueAsString(updateRequest);
        
        mockMvc.perform(put("/domains/{domainId}", nonExistentDomainId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestJson))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test deleting a non-existent domain returns 404 Not Found.
     */
    @Test
    void testDeleteNonExistentDomainReturnsNotFound() throws Exception {
        String nonExistentDomainId = "non-existent-domain-" + System.currentTimeMillis();
        
        mockMvc.perform(delete("/domains/{domainId}", nonExistentDomainId))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test creating a domain with invalid domain ID returns 400 Bad Request.
     */
    @Test
    void testCreateDomainWithInvalidIdReturnsBadRequest() throws Exception {
        // Domain ID starting with non-alphanumeric character
        String invalidDomainId = "-invalid-domain";
        
        DomainCreateRequest createRequest = new DomainCreateRequest(invalidDomainId, "Invalid domain");
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isBadRequest());
    }
    
    /**
     * Test creating multiple domains and listing them.
     */
    @Test
    void testCreateMultipleDomainsAndList() throws Exception {
        String domainId1 = "multi-domain-1-" + System.currentTimeMillis();
        String domainId2 = "multi-domain-2-" + System.currentTimeMillis();
        String domainId3 = "multi-domain-3-" + System.currentTimeMillis();
        
        // Create three domains
        DomainCreateRequest request1 = new DomainCreateRequest(domainId1, "First domain");
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());
        
        DomainCreateRequest request2 = new DomainCreateRequest(domainId2, "Second domain");
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated());
        
        DomainCreateRequest request3 = new DomainCreateRequest(domainId3, "Third domain");
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3)))
            .andExpect(status().isCreated());
        
        // List all domains and verify all three exist
        mockMvc.perform(get("/domains"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domains.domain[?(@.domainId == '" + domainId1 + "')]").exists())
            .andExpect(jsonPath("$.domains.domain[?(@.domainId == '" + domainId2 + "')]").exists())
            .andExpect(jsonPath("$.domains.domain[?(@.domainId == '" + domainId3 + "')]").exists());
        
        // Clean up
        mockMvc.perform(delete("/domains/{domainId}", domainId1)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/domains/{domainId}", domainId2)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/domains/{domainId}", domainId3)).andExpect(status().isNoContent());
    }
}
