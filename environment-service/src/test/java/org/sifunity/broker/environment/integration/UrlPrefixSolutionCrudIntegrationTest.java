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
import org.sifunity.broker.environment.dto.SolutionUpdateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for solution CRUD operations with the domain-nested path.
 *
 * <p>Tests create, retrieve, list, update, delete via
 * {@code /{domainId}/solutions}.</p>
 *
 * <p><strong>Validates: Requirements 11.3</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class UrlPrefixSolutionCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String domainId;

    @BeforeEach
    void setUp() throws Exception {
        domainId = "soldom" + System.currentTimeMillis();

        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DomainCreateRequest(domainId, "test"))))
            .andExpect(status().isCreated());
    }

    @AfterEach
    void tearDown() throws Exception {
        mockMvc.perform(delete("/domains/{domainId}", domainId))
            .andExpect(status().isNoContent());
    }

    /**
     * Full CRUD lifecycle: create → retrieve → list → update → delete → verify gone.
     */
    @Test
    void testSolutionCrudWithDomainNestedPath() throws Exception {
        String solId = "testsol" + System.currentTimeMillis();
        String basePath = "/" + domainId + "/solutions";

        // CREATE
        SolutionCreateRequest createReq = new SolutionCreateRequest(domainId, solId, "Initial description");

        MvcResult createResult = mockMvc.perform(post(basePath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.domainId").value(domainId))
            .andExpect(jsonPath("$.solutionId").value(solId))
            .andExpect(jsonPath("$.description").value("Initial description"))
            .andReturn();

        SolutionDTO created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), SolutionDTO.class);
        assertThat(created.id()).isNotNull();

        // RETRIEVE
        mockMvc.perform(get(basePath + "/{solutionId}", solId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solutionId").value(solId))
            .andExpect(jsonPath("$.domainId").value(domainId));

        // LIST
        mockMvc.perform(get(basePath))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solutions.solution[?(@.solutionId == '" + solId + "')]").exists());

        // UPDATE
        mockMvc.perform(put(basePath + "/{solutionId}", solId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SolutionUpdateRequest("Updated description"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Updated description"));

        // DELETE
        mockMvc.perform(delete(basePath + "/{solutionId}", solId))
            .andExpect(status().isNoContent());

        // VERIFY GONE
        mockMvc.perform(get(basePath + "/{solutionId}", solId))
            .andExpect(status().isNotFound());
    }
}
