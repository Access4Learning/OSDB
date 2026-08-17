// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.dto.CreateJobRequest;
import org.sifunity.broker.jobservice.dto.CreatePhaseRequest;
import org.sifunity.broker.jobservice.dto.UpdatePhaseStateRequest;
import org.sifunity.broker.jobservice.repository.JobPhaseRepository;
import org.sifunity.broker.jobservice.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for JobPhaseController.
 * Tests complete request flows with real database using Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class JobPhaseControllerIntegrationTest {

    private static final String TEST_DOMAIN_ID = "d47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String TEST_SOLUTION_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final String JOBS_BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID + "/jobs";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobPhaseRepository jobPhaseRepository;

    @BeforeEach
    void setUp() {
        jobPhaseRepository.deleteAll();
        jobRepository.deleteAll();
    }

    private String createTestJob() throws Exception {
        CreateJobRequest createRequest = new CreateJobRequest(
                UUID.randomUUID(),
                "TestService",
                "zone1",
                "context1",
                null
        );

        String createResponse = mockMvc.perform(post(JOBS_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(createResponse).get("id").asText();
    }

    @Test
    void testCreatePhase_WithValidRequest_Returns201() throws Exception {
        // Given
        String jobId = createTestJob();
        
        CreatePhaseRequest request = new CreatePhaseRequest(
                "Phase1",
                JobState.NOTSTARTED,
                "{\"key\":\"value\"}"
        );

        // When & Then
        mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.phaseName").value("Phase1"))
                .andExpect(jsonPath("$.state").value("NOTSTARTED"))
                .andExpect(jsonPath("$.metadata").value("{\"key\":\"value\"}"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void testCreatePhase_WithInvalidRequest_Returns400() throws Exception {
        // Given
        String jobId = createTestJob();
        
        // Missing required field (phaseName)
        String invalidRequest = "{\"state\":\"NOTSTARTED\"}";

        // When & Then
        mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePhase_WithNonExistentJob_Returns404() throws Exception {
        // Given
        UUID nonExistentJobId = UUID.randomUUID();
        
        CreatePhaseRequest request = new CreatePhaseRequest(
                "Phase1",
                JobState.NOTSTARTED,
                null
        );

        // When & Then
        mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", nonExistentJobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreatePhase_WithDuplicateName_Returns409() throws Exception {
        // Given
        String jobId = createTestJob();
        
        CreatePhaseRequest request = new CreatePhaseRequest(
                "Phase1",
                JobState.NOTSTARTED,
                null
        );

        // Create first phase
        mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // When & Then - try to create duplicate
        mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void testGetPhases_ReturnsAllPhasesOrderedByCreation() throws Exception {
        // Given
        String jobId = createTestJob();
        
        CreatePhaseRequest request1 = new CreatePhaseRequest("Phase1", JobState.NOTSTARTED, null);
        CreatePhaseRequest request2 = new CreatePhaseRequest("Phase2", JobState.NOTSTARTED, null);
        CreatePhaseRequest request3 = new CreatePhaseRequest("Phase3", JobState.NOTSTARTED, null);

        mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request3)))
                .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(get(JOBS_BASE_PATH + "/{jobId}/phases", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].phaseName").value("Phase1"))
                .andExpect(jsonPath("$[1].phaseName").value("Phase2"))
                .andExpect(jsonPath("$[2].phaseName").value("Phase3"));
    }

    @Test
    void testGetPhases_WithNonExistentJob_Returns404() throws Exception {
        // Given
        UUID nonExistentJobId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(get(JOBS_BASE_PATH + "/{jobId}/phases", nonExistentJobId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdatePhaseState_WithValidTransition_Returns200() throws Exception {
        // Given
        String jobId = createTestJob();
        
        CreatePhaseRequest createRequest = new CreatePhaseRequest(
                "Phase1",
                JobState.NOTSTARTED,
                null
        );

        String createResponse = mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String phaseId = objectMapper.readTree(createResponse).get("id").asText();

        UpdatePhaseStateRequest updateRequest = new UpdatePhaseStateRequest(
                JobState.INPROGRESS,
                "{\"updated\":\"true\"}"
        );

        // When & Then
        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/phases/{phaseId}/state", jobId, phaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("INPROGRESS"))
                .andExpect(jsonPath("$.metadata").value("{\"updated\":\"true\"}"));
    }

    @Test
    void testUpdatePhaseState_WithInvalidTransition_Returns400() throws Exception {
        // Given
        String jobId = createTestJob();
        
        CreatePhaseRequest createRequest = new CreatePhaseRequest(
                "Phase1",
                JobState.NOTSTARTED,
                null
        );

        String createResponse = mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String phaseId = objectMapper.readTree(createResponse).get("id").asText();

        // Try invalid transition: NOTSTARTED -> COMPLETED
        UpdatePhaseStateRequest updateRequest = new UpdatePhaseStateRequest(JobState.COMPLETED, null);

        // When & Then
        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/phases/{phaseId}/state", jobId, phaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdatePhaseState_FailedPhaseTransitionsJobToFailed() throws Exception {
        // Given
        String jobId = createTestJob();
        
        // Transition job to INPROGRESS first
        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/state", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new org.sifunity.broker.jobservice.dto.UpdateJobStateRequest(JobState.INPROGRESS, null))))
                .andExpect(status().isOk());

        // Create phase
        CreatePhaseRequest createRequest = new CreatePhaseRequest(
                "Phase1",
                JobState.NOTSTARTED,
                null
        );

        String createResponse = mockMvc.perform(post(JOBS_BASE_PATH + "/{jobId}/phases", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String phaseId = objectMapper.readTree(createResponse).get("id").asText();

        // Transition phase to INPROGRESS
        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/phases/{phaseId}/state", jobId, phaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdatePhaseStateRequest(JobState.INPROGRESS, null))))
                .andExpect(status().isOk());

        // When - transition phase to FAILED
        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/phases/{phaseId}/state", jobId, phaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdatePhaseStateRequest(JobState.FAILED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));

        // Then - verify job is also FAILED
        mockMvc.perform(get(JOBS_BASE_PATH + "/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
    }
}
