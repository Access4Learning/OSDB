// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.dto.CreateJobRequest;
import org.sifunity.broker.jobservice.dto.UpdateJobStateRequest;
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
 * Integration tests for JobController.
 * Tests complete request flows with real database using Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class JobControllerIntegrationTest {

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

    @Test
    void testCreateJob_WithValidRequest_Returns201() throws Exception {
        // Given
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(),
                "TestService",
                "zone1",
                "context1",
                "{\"key\":\"value\"}"
        );

        // When & Then
        mockMvc.perform(post(JOBS_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.environmentId").value(request.getEnvironmentId().toString()))
                .andExpect(jsonPath("$.serviceName").value("TestService"))
                .andExpect(jsonPath("$.zoneId").value("zone1"))
                .andExpect(jsonPath("$.contextId").value("context1"))
                .andExpect(jsonPath("$.state").value("NOTSTARTED"))
                .andExpect(jsonPath("$.metadata").value("{\"key\":\"value\"}"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void testCreateJob_WithInvalidRequest_Returns400() throws Exception {
        // Given - missing required field
        String invalidRequest = "{\"serviceName\":\"TestService\"}";

        // When & Then
        mockMvc.perform(post(JOBS_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetJob_WithExistingId_Returns200() throws Exception {
        // Given
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

        String jobId = objectMapper.readTree(createResponse).get("id").asText();

        // When & Then
        mockMvc.perform(get(JOBS_BASE_PATH + "/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId))
                .andExpect(jsonPath("$.serviceName").value("TestService"));
    }

    @Test
    void testGetJob_WithNonExistentId_Returns404() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(get(JOBS_BASE_PATH + "/{jobId}", nonExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testListJobs_WithEnvironmentId_ReturnsAllJobs() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        
        CreateJobRequest request1 = new CreateJobRequest(environmentId, "Service1", "zone1", "context1", null);
        CreateJobRequest request2 = new CreateJobRequest(environmentId, "Service2", "zone1", "context1", null);

        mockMvc.perform(post(JOBS_BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(JOBS_BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(get(JOBS_BASE_PATH)
                        .param("environmentId", environmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].serviceName", containsInAnyOrder("Service1", "Service2")));
    }

    @Test
    void testListJobs_WithStateFilter_ReturnsFilteredJobs() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        
        CreateJobRequest request = new CreateJobRequest(environmentId, "Service1", "zone1", "context1", null);

        String createResponse = mockMvc.perform(post(JOBS_BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jobId = objectMapper.readTree(createResponse).get("id").asText();

        // Update job state to INPROGRESS
        UpdateJobStateRequest updateRequest = new UpdateJobStateRequest(JobState.INPROGRESS, null);
        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/state", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // When & Then - filter by INPROGRESS
        mockMvc.perform(get(JOBS_BASE_PATH)
                        .param("environmentId", environmentId.toString())
                        .param("state", "INPROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].state").value("INPROGRESS"));

        // Filter by NOTSTARTED should return empty
        mockMvc.perform(get(JOBS_BASE_PATH)
                        .param("environmentId", environmentId.toString())
                        .param("state", "NOTSTARTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testListJobs_WithServiceNameFilter_ReturnsFilteredJobs() throws Exception {
        // Given
        UUID environmentId = UUID.randomUUID();
        
        CreateJobRequest request1 = new CreateJobRequest(environmentId, "Service1", "zone1", "context1", null);
        CreateJobRequest request2 = new CreateJobRequest(environmentId, "Service2", "zone1", "context1", null);

        mockMvc.perform(post(JOBS_BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(JOBS_BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(get(JOBS_BASE_PATH)
                        .param("environmentId", environmentId.toString())
                        .param("serviceName", "Service1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].serviceName").value("Service1"));
    }

    @Test
    void testUpdateJobState_WithValidTransition_Returns200() throws Exception {
        // Given
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

        String jobId = objectMapper.readTree(createResponse).get("id").asText();

        UpdateJobStateRequest updateRequest = new UpdateJobStateRequest(
                JobState.INPROGRESS,
                "{\"updated\":\"true\"}"
        );

        // When & Then
        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/state", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("INPROGRESS"))
                .andExpect(jsonPath("$.metadata").value("{\"updated\":\"true\"}"));
    }

    @Test
    void testUpdateJobState_WithInvalidTransition_Returns400() throws Exception {
        // Given
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

        String jobId = objectMapper.readTree(createResponse).get("id").asText();

        // Try invalid transition: NOTSTARTED -> COMPLETED (must go through INPROGRESS)
        UpdateJobStateRequest updateRequest = new UpdateJobStateRequest(JobState.COMPLETED, null);

        // When & Then
        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/state", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDeleteJob_WithCompletedJob_Returns204() throws Exception {
        // Given
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

        String jobId = objectMapper.readTree(createResponse).get("id").asText();

        // Transition to COMPLETED
        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/state", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateJobStateRequest(JobState.INPROGRESS, null))))
                .andExpect(status().isOk());

        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/state", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateJobStateRequest(JobState.COMPLETED, null))))
                .andExpect(status().isOk());

        // When & Then
        mockMvc.perform(delete(JOBS_BASE_PATH + "/{jobId}", jobId))
                .andExpect(status().isNoContent());

        // Verify job is deleted
        mockMvc.perform(get(JOBS_BASE_PATH + "/{jobId}", jobId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteJob_WithInProgressJob_Returns400() throws Exception {
        // Given
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

        String jobId = objectMapper.readTree(createResponse).get("id").asText();

        // Transition to INPROGRESS
        mockMvc.perform(put(JOBS_BASE_PATH + "/{jobId}/state", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateJobStateRequest(JobState.INPROGRESS, null))))
                .andExpect(status().isOk());

        // When & Then - should fail to delete in-progress job
        mockMvc.perform(delete(JOBS_BASE_PATH + "/{jobId}", jobId))
                .andExpect(status().isConflict());
    }
}
