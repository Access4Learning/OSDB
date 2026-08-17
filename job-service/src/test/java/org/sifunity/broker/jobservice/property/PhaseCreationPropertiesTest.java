// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.domain.Job;
import org.sifunity.broker.jobservice.domain.JobPhase;
import org.sifunity.broker.jobservice.exception.DuplicatePhaseException;
import org.sifunity.broker.jobservice.exception.JobNotFoundException;
import org.sifunity.broker.jobservice.repository.JobPhaseRepository;
import org.sifunity.broker.jobservice.service.JobPhaseService;
import org.sifunity.broker.jobservice.service.JobService;
import org.sifunity.broker.jobservice.util.StateTransitionValidator;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for job phase creation.
 * 
 * **Validates: Requirements 4.1, 4.6, 11.1-11.5**
 * 
 * Tests the following properties:
 * - Property 5: Phase Creation Updates Job Current Phase
 * - Property 13: Phase Parameter Validation Rejects Invalid Inputs
 * - Property 14: Phase Names Must Be Unique Within Job
 */
class PhaseCreationPropertiesTest {
    
    /**
     * Property 5: Phase Creation Updates Job Current Phase
     * 
     * **Validates: Requirements 4.1, 4.6**
     * 
     * For any job and valid phase creation request, creating the phase should 
     * update the job's currentPhase field to the new phase name.
     */
    @Property(tries = 100)
    @Label("Feature: job-service, Property 5: Phase Creation Updates Job Current Phase")
    void phaseCreationUpdatesJobCurrentPhase(
            @ForAll("validPhaseNames") String phaseName,
            @ForAll JobState state) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job exists
        UUID jobId = UUID.randomUUID();
        Job job = createTestJob(jobId);
        
        when(jobService.getJob(jobId)).thenReturn(job);
        when(phaseRepository.existsByJobIdAndPhaseName(jobId, phaseName)).thenReturn(false);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Creating a phase for the job
        JobPhase phase = phaseService.createPhase(jobId, phaseName, state, null);
        
        // Then: The phase is created successfully
        assertThat(phase).isNotNull();
        assertThat(phase.getId()).isNotNull();
        assertThat(phase.getJobId()).isEqualTo(jobId);
        assertThat(phase.getPhaseName()).isEqualTo(phaseName);
        assertThat(phase.getState()).isEqualTo(state);
        assertThat(phase.getCreatedAt()).isNotNull();
        assertThat(phase.getUpdatedAt()).isNotNull();
        assertThat(phase.getCreatedAt()).isEqualTo(phase.getUpdatedAt());
        
        // And: The job's currentPhase is updated to the new phase name
        verify(jobService).updateCurrentPhase(jobId, phaseName);
    }
    
    /**
     * Property 13: Phase Parameter Validation Rejects Invalid Inputs
     * 
     * **Validates: Requirements 11.1, 11.2, 11.3, 11.4**
     * 
     * For any phase creation request with invalid parameters (blank phase name, 
     * name exceeding 100 characters), the request should be rejected with 
     * IllegalArgumentException.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 13: Phase Parameter Validation Rejects Invalid Inputs")
    void phaseParameterValidationRejectsInvalidInputs(
            @ForAll("invalidPhaseNames") String invalidPhaseName) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job exists
        UUID jobId = UUID.randomUUID();
        Job job = createTestJob(jobId);
        when(jobService.getJob(jobId)).thenReturn(job);
        
        // When/Then: Creating a phase with invalid parameters throws exception
        assertThatThrownBy(() -> 
            phaseService.createPhase(jobId, invalidPhaseName, JobState.NOTSTARTED, null))
            .isInstanceOf(IllegalArgumentException.class)
            .satisfies(ex -> {
                String message = ex.getMessage();
                assertThat(message).containsAnyOf("phaseName cannot be null or blank", 
                                                  "phaseName cannot exceed 100 characters");
            });
    }
    
    /**
     * Property 13b: Null State Validation
     * 
     * **Validates: Requirements 11.3**
     * 
     * For any phase creation request with null state, the request should be 
     * rejected with IllegalArgumentException.
     */
    @Property(tries = 10)
    @Label("Feature: job-service, Property 13: Phase Parameter Validation Rejects Invalid Inputs")
    void nullStateValidationRejectsNullState(
            @ForAll("validPhaseNames") String phaseName) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job exists
        UUID jobId = UUID.randomUUID();
        Job job = createTestJob(jobId);
        when(jobService.getJob(jobId)).thenReturn(job);
        
        // When/Then: Creating a phase with null state throws exception
        assertThatThrownBy(() -> 
            phaseService.createPhase(jobId, phaseName, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("state cannot be null");
    }
    
    /**
     * Property 14: Phase Names Must Be Unique Within Job
     * 
     * **Validates: Requirements 11.5**
     * 
     * For any job, attempting to create two phases with the same phaseName 
     * should result in the second creation failing with DuplicatePhaseException.
     */
    @Property(tries = 100)
    @Label("Feature: job-service, Property 14: Phase Names Must Be Unique Within Job")
    void phaseNamesMustBeUniqueWithinJob(
            @ForAll("validPhaseNames") String phaseName,
            @ForAll JobState state1,
            @ForAll JobState state2) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job exists with a phase
        UUID jobId = UUID.randomUUID();
        Job job = createTestJob(jobId);
        
        when(jobService.getJob(jobId)).thenReturn(job);
        when(phaseRepository.existsByJobIdAndPhaseName(jobId, phaseName))
            .thenReturn(false)  // First call returns false (phase doesn't exist)
            .thenReturn(true);  // Second call returns true (phase now exists)
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Creating first phase succeeds
        JobPhase firstPhase = phaseService.createPhase(jobId, phaseName, state1, null);
        assertThat(firstPhase).isNotNull();
        
        // Then: Attempting to create another phase with the same name fails
        assertThatThrownBy(() -> 
            phaseService.createPhase(jobId, phaseName, state2, null))
            .isInstanceOf(DuplicatePhaseException.class)
            .hasMessageContaining("Phase already exists")
            .hasMessageContaining(phaseName);
    }
    
    /**
     * Property: Phase Names Can Be Reused Across Different Jobs
     * 
     * **Validates: Requirements 11.5**
     * 
     * The same phase name can be used in different jobs without conflict.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 14: Phase Names Must Be Unique Within Job")
    void phaseNamesCanBeReusedAcrossDifferentJobs(
            @ForAll("validPhaseNames") String phaseName,
            @ForAll JobState state) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: Two different jobs exist
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        Job job1 = createTestJob(jobId1);
        Job job2 = createTestJob(jobId2);
        
        when(jobService.getJob(jobId1)).thenReturn(job1);
        when(jobService.getJob(jobId2)).thenReturn(job2);
        when(phaseRepository.existsByJobIdAndPhaseName(any(), eq(phaseName))).thenReturn(false);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Creating phases with the same name in different jobs
        JobPhase phase1 = phaseService.createPhase(jobId1, phaseName, state, null);
        JobPhase phase2 = phaseService.createPhase(jobId2, phaseName, state, null);
        
        // Then: Both phases are created successfully
        assertThat(phase1).isNotNull();
        assertThat(phase2).isNotNull();
        assertThat(phase1.getId()).isNotEqualTo(phase2.getId());
        assertThat(phase1.getJobId()).isNotEqualTo(phase2.getJobId());
        assertThat(phase1.getPhaseName()).isEqualTo(phase2.getPhaseName());
    }
    
    /**
     * Property: Phase Creation Initializes Timestamps Correctly
     * 
     * **Validates: Requirements 4.4**
     * 
     * For any phase creation, createdAt and updatedAt should be set to the 
     * same value at creation time.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 5: Phase Creation Updates Job Current Phase")
    void phaseCreationInitializesTimestampsCorrectly(
            @ForAll("validPhaseNames") String phaseName,
            @ForAll JobState state) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job exists
        UUID jobId = UUID.randomUUID();
        Job job = createTestJob(jobId);
        
        when(jobService.getJob(jobId)).thenReturn(job);
        when(phaseRepository.existsByJobIdAndPhaseName(jobId, phaseName)).thenReturn(false);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        Instant beforeCreation = Instant.now();
        
        // When: Creating a phase
        JobPhase phase = phaseService.createPhase(jobId, phaseName, state, null);
        Instant afterCreation = Instant.now();
        
        // Then: Timestamps are initialized correctly
        assertThat(phase.getCreatedAt()).isNotNull();
        assertThat(phase.getUpdatedAt()).isNotNull();
        assertThat(phase.getCreatedAt()).isEqualTo(phase.getUpdatedAt());
        assertThat(phase.getCreatedAt()).isBetween(beforeCreation, afterCreation);
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<String> validPhaseNames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withChars('_', '-')
            .ofMinLength(1)
            .ofMaxLength(100);
    }
    
    @Provide
    Arbitrary<String> invalidPhaseNames() {
        return Arbitraries.oneOf(
            // Blank strings
            Arbitraries.just(""),
            Arbitraries.just("   "),
            Arbitraries.just("\t"),
            Arbitraries.just("\n"),
            // Too long strings (> 100 characters)
            Arbitraries.strings().alpha().ofMinLength(101).ofMaxLength(200)
        );
    }
    
    // ========== Helper Methods ==========
    
    private Job createTestJob(UUID jobId) {
        Job job = new Job();
        job.setId(jobId);
        job.setEnvironmentId(UUID.randomUUID());
        job.setServiceName("TestService");
        job.setZoneId("zone1");
        job.setState(JobState.INPROGRESS);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        return job;
    }
}
