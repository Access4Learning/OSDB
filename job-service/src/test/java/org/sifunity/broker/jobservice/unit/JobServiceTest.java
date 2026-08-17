// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.domain.Job;
import org.sifunity.broker.jobservice.exception.InvalidStateTransitionException;
import org.sifunity.broker.jobservice.exception.JobInProgressException;
import org.sifunity.broker.jobservice.exception.JobNotFoundException;
import org.sifunity.broker.jobservice.repository.JobRepository;
import org.sifunity.broker.jobservice.service.JobService;
import org.sifunity.broker.jobservice.util.StateTransitionValidator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JobService.
 * 
 * Tests the business logic for job management operations using mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {
    
    @Mock
    private JobRepository jobRepository;
    
    @Mock
    private StateTransitionValidator stateValidator;
    
    @InjectMocks
    private JobService jobService;
    
    private UUID environmentId;
    private UUID jobId;
    
    @BeforeEach
    void setUp() {
        environmentId = UUID.randomUUID();
        jobId = UUID.randomUUID();
    }
    
    // ========== createJob Tests ==========
    
    @Test
    void createJob_ValidParameters_CreatesJobWithCorrectState() {
        // Given: Valid job parameters
        String serviceName = "TestService";
        String zoneId = "zone1";
        String contextId = "context1";
        String metadata = "{\"key\":\"value\"}";
        
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Creating a job
        Job job = jobService.createJob(environmentId, serviceName, zoneId, contextId, metadata);
        
        // Then: Job is created with correct properties
        assertThat(job).isNotNull();
        assertThat(job.getId()).isNotNull();
        assertThat(job.getEnvironmentId()).isEqualTo(environmentId);
        assertThat(job.getServiceName()).isEqualTo(serviceName);
        assertThat(job.getZoneId()).isEqualTo(zoneId);
        assertThat(job.getContextId()).isEqualTo(contextId);
        assertThat(job.getState()).isEqualTo(JobState.NOTSTARTED);
        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(job.getUpdatedAt()).isNotNull();
        assertThat(job.getCreatedAt()).isEqualTo(job.getUpdatedAt());
        assertThat(job.getMetadata()).isEqualTo(metadata);
        
        verify(jobRepository).save(any(Job.class));
    }
    
    @Test
    void createJob_NullEnvironmentId_ThrowsException() {
        // When/Then: Creating job with null environmentId throws exception
        assertThatThrownBy(() -> 
            jobService.createJob(null, "service", "zone", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("environmentId cannot be null");
    }
    
    @Test
    void createJob_NullServiceName_ThrowsException() {
        // When/Then: Creating job with null serviceName throws exception
        assertThatThrownBy(() -> 
            jobService.createJob(environmentId, null, "zone", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("serviceName cannot be null or blank");
    }
    
    @Test
    void createJob_BlankServiceName_ThrowsException() {
        // When/Then: Creating job with blank serviceName throws exception
        assertThatThrownBy(() -> 
            jobService.createJob(environmentId, "  ", "zone", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("serviceName cannot be null or blank");
    }
    
    @Test
    void createJob_NullZoneId_ThrowsException() {
        // When/Then: Creating job with null zoneId throws exception
        assertThatThrownBy(() -> 
            jobService.createJob(environmentId, "service", null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zoneId cannot be null or blank");
    }
    
    @Test
    void createJob_BlankZoneId_ThrowsException() {
        // When/Then: Creating job with blank zoneId throws exception
        assertThatThrownBy(() -> 
            jobService.createJob(environmentId, "service", "  ", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zoneId cannot be null or blank");
    }
    
    @Test
    void createJob_OptionalParametersNull_CreatesJob() {
        // Given: Optional parameters are null
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Creating a job without optional parameters
        Job job = jobService.createJob(environmentId, "service", "zone", null, null);
        
        // Then: Job is created successfully
        assertThat(job).isNotNull();
        assertThat(job.getContextId()).isNull();
        assertThat(job.getMetadata()).isNull();
    }
    
    // ========== getJob Tests ==========
    
    @Test
    void getJob_ExistingJob_ReturnsJob() {
        // Given: A job exists
        Job existingJob = createTestJob(jobId, environmentId, JobState.NOTSTARTED);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(existingJob));
        
        // When: Getting the job
        Job job = jobService.getJob(jobId);
        
        // Then: Job is returned
        assertThat(job).isNotNull();
        assertThat(job.getId()).isEqualTo(jobId);
        verify(jobRepository).findById(jobId);
    }
    
    @Test
    void getJob_NonExistentJob_ThrowsException() {
        // Given: Job does not exist
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());
        
        // When/Then: Getting non-existent job throws exception
        assertThatThrownBy(() -> jobService.getJob(jobId))
            .isInstanceOf(JobNotFoundException.class)
            .hasMessageContaining("Job not found: " + jobId);
    }
    
    // ========== listJobs Tests ==========
    
    @Test
    void listJobs_NoFilters_ReturnsAllJobsForEnvironment() {
        // Given: Jobs exist for the environment
        List<Job> expectedJobs = List.of(
            createTestJob(UUID.randomUUID(), environmentId, JobState.NOTSTARTED),
            createTestJob(UUID.randomUUID(), environmentId, JobState.INPROGRESS)
        );
        when(jobRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId))
            .thenReturn(expectedJobs);
        
        // When: Listing jobs without filters
        List<Job> jobs = jobService.listJobs(environmentId, null, null);
        
        // Then: All jobs are returned
        assertThat(jobs).hasSize(2);
        verify(jobRepository).findByEnvironmentIdOrderByCreatedAtDesc(environmentId);
    }
    
    @Test
    void listJobs_StateFilter_ReturnsFilteredJobs() {
        // Given: Jobs with specific state exist
        List<Job> expectedJobs = List.of(
            createTestJob(UUID.randomUUID(), environmentId, JobState.INPROGRESS)
        );
        when(jobRepository.findByEnvironmentIdAndState(environmentId, JobState.INPROGRESS))
            .thenReturn(expectedJobs);
        
        // When: Listing jobs with state filter
        List<Job> jobs = jobService.listJobs(environmentId, JobState.INPROGRESS, null);
        
        // Then: Only jobs with matching state are returned
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getState()).isEqualTo(JobState.INPROGRESS);
        verify(jobRepository).findByEnvironmentIdAndState(environmentId, JobState.INPROGRESS);
    }
    
    @Test
    void listJobs_ServiceNameFilter_ReturnsFilteredJobs() {
        // Given: Jobs with specific service name exist
        String serviceName = "TestService";
        List<Job> expectedJobs = List.of(
            createTestJob(UUID.randomUUID(), environmentId, JobState.NOTSTARTED)
        );
        when(jobRepository.findByEnvironmentIdAndServiceName(environmentId, serviceName))
            .thenReturn(expectedJobs);
        
        // When: Listing jobs with service name filter
        List<Job> jobs = jobService.listJobs(environmentId, null, serviceName);
        
        // Then: Only jobs with matching service name are returned
        assertThat(jobs).hasSize(1);
        verify(jobRepository).findByEnvironmentIdAndServiceName(environmentId, serviceName);
    }
    
    @Test
    void listJobs_BothFilters_ReturnsFilteredJobs() {
        // Given: Jobs matching both filters exist
        String serviceName = "TestService";
        List<Job> expectedJobs = List.of(
            createTestJob(UUID.randomUUID(), environmentId, JobState.INPROGRESS)
        );
        when(jobRepository.findByEnvironmentIdAndStateAndServiceName(
            environmentId, JobState.INPROGRESS, serviceName))
            .thenReturn(expectedJobs);
        
        // When: Listing jobs with both filters
        List<Job> jobs = jobService.listJobs(environmentId, JobState.INPROGRESS, serviceName);
        
        // Then: Only jobs matching both criteria are returned
        assertThat(jobs).hasSize(1);
        verify(jobRepository).findByEnvironmentIdAndStateAndServiceName(
            environmentId, JobState.INPROGRESS, serviceName);
    }
    
    // ========== updateJobState Tests ==========
    
    @Test
    void updateJobState_ValidTransition_UpdatesState() {
        // Given: A job in NOTSTARTED state
        Job job = createTestJob(jobId, environmentId, JobState.NOTSTARTED);
        Instant originalUpdatedAt = job.getUpdatedAt();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(stateValidator.isValidTransition(JobState.NOTSTARTED, JobState.INPROGRESS))
            .thenReturn(true);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Updating to INPROGRESS
        Job updatedJob = jobService.updateJobState(jobId, JobState.INPROGRESS, null);
        
        // Then: State is updated
        assertThat(updatedJob.getState()).isEqualTo(JobState.INPROGRESS);
        assertThat(updatedJob.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        verify(stateValidator).isValidTransition(JobState.NOTSTARTED, JobState.INPROGRESS);
        verify(jobRepository).save(any(Job.class));
    }
    
    @Test
    void updateJobState_InvalidTransition_ThrowsException() {
        // Given: A job in NOTSTARTED state
        Job job = createTestJob(jobId, environmentId, JobState.NOTSTARTED);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(stateValidator.isValidTransition(JobState.NOTSTARTED, JobState.COMPLETED))
            .thenReturn(false);
        
        // When/Then: Attempting invalid transition throws exception
        assertThatThrownBy(() -> 
            jobService.updateJobState(jobId, JobState.COMPLETED, null))
            .isInstanceOf(InvalidStateTransitionException.class)
            .hasMessageContaining("Invalid transition from NOTSTARTED to COMPLETED");
    }
    
    @Test
    void updateJobState_WithMetadata_UpdatesMetadata() {
        // Given: A job in INPROGRESS state
        Job job = createTestJob(jobId, environmentId, JobState.INPROGRESS);
        String newMetadata = "{\"progress\":50}";
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(stateValidator.isValidTransition(JobState.INPROGRESS, JobState.COMPLETED))
            .thenReturn(true);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Updating state with metadata
        Job updatedJob = jobService.updateJobState(jobId, JobState.COMPLETED, newMetadata);
        
        // Then: Metadata is updated
        assertThat(updatedJob.getMetadata()).isEqualTo(newMetadata);
    }
    
    // ========== deleteJob Tests ==========
    
    @Test
    void deleteJob_CompletedJob_DeletesSuccessfully() {
        // Given: A job in COMPLETED state
        Job job = createTestJob(jobId, environmentId, JobState.COMPLETED);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        
        // When: Deleting the job
        jobService.deleteJob(jobId);
        
        // Then: Job is deleted
        verify(jobRepository).delete(job);
    }
    
    @Test
    void deleteJob_InProgressJob_ThrowsException() {
        // Given: A job in INPROGRESS state
        Job job = createTestJob(jobId, environmentId, JobState.INPROGRESS);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        
        // When/Then: Attempting to delete throws exception
        assertThatThrownBy(() -> jobService.deleteJob(jobId))
            .isInstanceOf(JobInProgressException.class)
            .hasMessageContaining("Cannot delete job in INPROGRESS state");
    }
    
    @Test
    void deleteJob_NonExistentJob_ThrowsException() {
        // Given: Job does not exist
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());
        
        // When/Then: Attempting to delete throws exception
        assertThatThrownBy(() -> jobService.deleteJob(jobId))
            .isInstanceOf(JobNotFoundException.class);
    }
    
    // ========== updateCurrentPhase Tests ==========
    
    @Test
    void updateCurrentPhase_ExistingJob_UpdatesPhase() {
        // Given: A job exists
        Job job = createTestJob(jobId, environmentId, JobState.INPROGRESS);
        String phaseName = "Phase1";
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Updating current phase
        jobService.updateCurrentPhase(jobId, phaseName);
        
        // Then: Current phase is updated
        assertThat(job.getCurrentPhase()).isEqualTo(phaseName);
        verify(jobRepository).save(job);
    }
    
    // ========== Helper Methods ==========
    
    private Job createTestJob(UUID id, UUID envId, JobState state) {
        Job job = new Job();
        job.setId(id);
        job.setEnvironmentId(envId);
        job.setServiceName("TestService");
        job.setZoneId("zone1");
        job.setState(state);
        Instant now = Instant.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return job;
    }
}
