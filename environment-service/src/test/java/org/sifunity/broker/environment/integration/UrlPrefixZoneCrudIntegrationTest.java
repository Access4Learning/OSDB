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
 * Integration test for zone CRUD operations with the URL prefix.
 *
 * <p>Tests create and list via {@code /{domainId}/{solutionId}/zones}.</p>
 *
 * <p><strong>Validates: Requirements 11.2</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class UrlPrefixZoneCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String domainId;
    private String solutionId;

    @BeforeEach
    void setUp() throws Exception {
        domainId = "zdom" + System.currentTimeMillis();
        solutionId = "zsol" + System.currentTimeMillis();

        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DomainCreateRequest(domainId, "test"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/" + domainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SolutionCreateRequest(domainId, solutionId, "test"))))
            .andExpect(status().isCreated());
    }

    @AfterEach
    void tearDown() throws Exception {
        mockMvc.perform(delete("/domains/{domainId}", domainId))
            .andExpect(status().isNoContent());
    }

    /**
     * Create a zone and list zones via the prefixed path.
     */
    @Test
    void testZoneCrudWithPrefixedPath() throws Exception {
        String zoneId = "testzone" + System.currentTimeMillis();
        String basePath = "/" + domainId + "/" + solutionId + "/zones";

        // CREATE
        ZoneCreateRequestWrapper req = new ZoneCreateRequestWrapper(
            new ZoneCreateBody(zoneId, "Integration test zone"));

        mockMvc.perform(post(basePath + "/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.zone.id").value(zoneId));

        // LIST
        mockMvc.perform(get(basePath))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones.zone[?(@.id == '" + zoneId + "')]").exists());
    }

    /**
     * Create multiple zones and verify listing returns all of them.
     */
    @Test
    void testMultipleZonesListedViaPrefixedPath() throws Exception {
        String basePath = "/" + domainId + "/" + solutionId + "/zones";
        String zoneId1 = "zone1t" + System.currentTimeMillis();
        String zoneId2 = "zone2t" + System.currentTimeMillis();

        mockMvc.perform(post(basePath + "/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId1, "Zone 1")))))
            .andExpect(status().isCreated());

        mockMvc.perform(post(basePath + "/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId2, "Zone 2")))))
            .andExpect(status().isCreated());

        mockMvc.perform(get(basePath))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zones.zone[?(@.id == '" + zoneId1 + "')]").exists())
            .andExpect(jsonPath("$.zones.zone[?(@.id == '" + zoneId2 + "')]").exists());
    }
}

