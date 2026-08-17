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
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.DomainCreateRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for domain_id uniqueness constraint.
 * 
 * <p>This test validates that the system enforces uniqueness of domain_id
 * across all domains. Attempting to create a second domain with the same
 * domain_id should result in a 409 Conflict error.</p>
 * 
 * <p><strong>Validates: Requirements 8.5</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class DomainIdUniquenessIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Test that creating a domain with a duplicate domain_id returns 409 Conflict.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>First domain creation succeeds (201 Created)</li>
     *   <li>Second domain creation with same domain_id fails (409 Conflict)</li>
     *   <li>Uniqueness constraint is enforced at the API level</li>
     * </ul>
     */
    @Test
    void testDomainIdUniquenessEnforcement() throws Exception {
        String domainId = "unique-domain-" + System.currentTimeMillis();
        
        // ========== Step 1: Create First Domain ==========
        
        DomainCreateRequest firstRequest = new DomainCreateRequest(domainId, "First domain");
        String firstRequestJson = objectMapper.writeValueAsString(firstRequest);
        
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstRequestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.domainId").value(domainId))
            .andExpect(jsonPath("$.description").value("First domain"));
        
        // ========== Step 2: Attempt to Create Second Domain with Same domain_id ==========
        
        DomainCreateRequest duplicateRequest = new DomainCreateRequest(domainId, "Second domain with duplicate ID");
        String duplicateRequestJson = objectMapper.writeValueAsString(duplicateRequest);
        
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateRequestJson))
            .andExpect(status().isConflict());
        
        // ========== Step 3: Verify Only First Domain Exists ==========
        
        mockMvc.perform(get("/domains/{domainId}", domainId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.domainId").value(domainId))
            .andExpect(jsonPath("$.description").value("First domain"));
        
        // Clean up
        mockMvc.perform(delete("/domains/{domainId}", domainId))
            .andExpect(status().isNoContent());
    }
}
