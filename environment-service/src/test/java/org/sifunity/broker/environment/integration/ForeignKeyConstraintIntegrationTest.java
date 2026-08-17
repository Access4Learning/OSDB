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
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateBody;
import org.sifunity.broker.environment.dto.ZoneCreateRequestWrapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Integration test for foreign key constraint enforcement.
 * 
 * <p>This test validates that the application properly enforces referential integrity
 * by rejecting operations that would violate foreign key constraints:
 * <ol>
 *   <li>Creating a solution with non-existent domain returns 404 Not Found</li>
 *   <li>Creating a zone with non-existent solution returns 404 Not Found</li>
 *   <li>Registering an environment with non-existent solution returns 404 Not Found</li>
 * </ol>
 * 
 * <p>These validations are performed at the application level (service layer) before
 * database operations, ensuring proper error responses to API clients.</p>
 * 
 * <p><strong>Validates: Requirements 8.4</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class ForeignKeyConstraintIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Test that attempting to create a solution with a non-existent domain returns 404 Not Found.
     * 
     * <p>This validates that the application enforces the foreign key constraint from
     * solutions.domain_id to domains.domain_id at the service layer, preventing orphaned
     * solutions from being created.</p>
     */
    @Test
    void testCreateSolutionWithNonExistentDomainReturnsNotFound() throws Exception {
        String nonExistentDomainId = "non-existent-domain-" + System.currentTimeMillis();
        String solutionId = "test-solution-" + System.currentTimeMillis();
        
        SolutionCreateRequest createRequest = new SolutionCreateRequest(
            nonExistentDomainId, 
            solutionId, 
            "Test solution with non-existent domain"
        );
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        mockMvc.perform(post("/" + nonExistentDomainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test that attempting to create a zone with a non-existent solution returns 404 Not Found.
     * 
     * <p>This validates that the application enforces the foreign key constraint from
     * zones.solution_id to solutions.solution_id at the service layer, preventing orphaned
     * zones from being created.</p>
     */
    @Test
    void testCreateZoneWithNonExistentSolutionReturnsNotFound() throws Exception {
        String nonExistentSolutionId = "non-existent-solution-" + System.currentTimeMillis();
        String zoneId = "test-zone-" + System.currentTimeMillis();
        
        ZoneCreateRequestWrapper createRequest = new ZoneCreateRequestWrapper(
            new ZoneCreateBody(zoneId, "Test zone with non-existent solution")
        );
        String createRequestJson = objectMapper.writeValueAsString(createRequest);
        
        mockMvc.perform(post("/test-domain/" + nonExistentSolutionId + "/zones/zone")
                .header("X-Environment-Fingerprint", "broker-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson))
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test that attempting to register an environment with a non-existent solution returns 404 Not Found.
     * 
     * <p>This validates that the application enforces the foreign key constraint from
     * environments.solution_id to solutions.solution_id at the service layer when a
     * solution_id is provided, preventing orphaned environments from being created.</p>
     * 
     * <p>Note: Registering an environment without a solution_id (null) is allowed for
     * SIF 3.3 compatibility and is tested separately.</p>
     */
    @Test
    void testRegisterEnvironmentWithNonExistentSolutionReturnsNotFound() throws Exception {
        String nonExistentSolutionId = "non-existent-solution-" + System.currentTimeMillis();
        String applicationName = "test-app-" + System.currentTimeMillis();
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(
            new EnvironmentRegistrationBody(
                applicationName,
                EnvironmentType.BROKERED,
                new DefaultZoneDTO("test-zone"),
                nonExistentSolutionId, // non-existent solution_id
                null));
        String registrationRequestJson = objectMapper.writeValueAsString(registrationRequest);
        
        mockMvc.perform(post("/test-domain/" + nonExistentSolutionId + "/environments/environment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationRequestJson))
            .andExpect(status().isNotFound());
    }
}
