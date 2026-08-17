// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.domain.Job;
import org.sifunity.broker.jobservice.domain.JobPhase;
import org.sifunity.broker.jobservice.exception.InvalidStateTransitionException;
import org.sifunity.broker.jobservice.exception.PhaseNotFoundException;
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
 * Property-based tests for job phase state updates.
 * 
 * **Validates: Requirements 5.1-5.6, 12.4**
 * 
 * Tests the following properties:
 * - Property 2: Valid State Transitions Are Accepted (for phases)
 * - Property 15: Failed Phase Transitions Job To Failed
 */
class PhaseStateUpdatePropertiesTest {
    
    /**
     * Property 2: Valid State Transitions Are Accepted (for phases)
     * 
     * **Validates: Requirements 5.1, 5.2, 5.3, 5.4**
     * 
     * For any phase in a given state, transitioning to a valid next state 
     * (NOTSTARTED→INPROGRESS, INPROGRESS→COMPLETED, INPROGRESS→FAILED) 
     * should succeed and update the updatedAt timestamp.
     */
    @Property(tries = 100)
    @Label("Feature: job-service, Property 2: Valid State Transitions Are Accepted")
    void validPhaseStateTransitionsAreAccepted(
            @ForAll("validPhaseTransitions") StateTransition transition) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = new StateTransitionValidator();
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A phase in the initial state
        UUID phaseId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPhase phase = createPhase(phaseId, jobId, transition.fromState());
        Instant originalUpdatedAt = phase.getUpdatedAt();
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Mock job service for FAILED state transitions
        if (transition.toState() == JobState.FAILED) {
            Job job = createTestJob(jobId, JobState.INPROGRESS);
            when(jobService.getJob(jobId)).thenReturn(job);
            when(jobService.updateJobState(eq(jobId), eq(JobState.FAILED), eq(null))).thenReturn(job);
        }
        
        // When: Transitioning to valid next state
        JobPhase updated = phaseService.updatePhaseState(phaseId, transition.toState(), null);
        
        // Then: Transition succeeds
        assertThat(updated.getState()).isEqualTo(transition.toState());
        
        // And: Timestamp is updated
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
    }
    
    /**
     * Property 3: Invalid State Transitions Are Rejected (for phases)
     * 
     * **Validates: Requirements 5.5**
     * 
     * For any phase, attempting an invalid state transition should fail with 
     * InvalidStateTransitionException.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 3: Invalid State Transitions Are Rejected")
    void invalidPhaseStateTransitionsAreRejected(
            @ForAll("invalidPhaseTransitions") StateTransition transition) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = new StateTransitionValidator();
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A phase in the initial state
        UUID phaseId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPhase phase = createPhase(phaseId, jobId, transition.fromState());
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        
        // When/Then: Attempting invalid transition throws exception
        assertThatThrownBy(() -> 
            phaseService.updatePhaseState(phaseId, transition.toState(), null))
            .isInstanceOf(InvalidStateTransitionException.class)
            .hasMessageContaining("Invalid phase transition")
            .hasMessageContaining(transition.fromState().toString())
            .hasMessageContaining(transition.toState().toString());
        
        // And: Phase is not saved
        verify(phaseRepository, never()).save(any());
    }
    
    /**
     * Property 4: Terminal States Cannot Transition (for phases)
     * 
     * **Validates: Requirements 5.6**
     * 
     * For any phase in COMPLETED or FAILED state, attempting any state 
     * transition should be rejected.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 4: Terminal States Cannot Transition")
    void terminalPhaseStatesCannotTransition(
            @ForAll("terminalStates") JobState terminalState,
            @ForAll JobState targetState) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = new StateTransitionValidator();
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A phase in a terminal state
        UUID phaseId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPhase phase = createPhase(phaseId, jobId, terminalState);
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        
        // Skip if trying to transition to the same state (idempotent)
        if (terminalState == targetState) {
            return;
        }
        
        // When/Then: Attempting any transition from terminal state throws exception
        assertThatThrownBy(() -> 
            phaseService.updatePhaseState(phaseId, targetState, null))
            .isInstanceOf(InvalidStateTransitionException.class);
        
        // And: Phase is not saved
        verify(phaseRepository, never()).save(any());
    }
    
    /**
     * Property 15: Failed Phase Transitions Job To Failed
     * 
     * **Validates: Requirements 12.4**
     * 
     * For any job in INPROGRESS state with a phase, updating that phase to 
     * FAILED state should automatically transition the job to FAILED state.
     */
    @Property(tries = 100)
    @Label("Feature: job-service, Property 15: Failed Phase Transitions Job To Failed")
    void failedPhaseTransitionsJobToFailed(
            @ForAll("inProgressPhaseStates") JobState initialPhaseState) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = new StateTransitionValidator();
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job in INPROGRESS state with a phase
        UUID phaseId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPhase phase = createPhase(phaseId, jobId, initialPhaseState);
        Job job = createTestJob(jobId, JobState.INPROGRESS);
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobService.getJob(jobId)).thenReturn(job);
        when(jobService.updateJobState(eq(jobId), eq(JobState.FAILED), eq(null))).thenReturn(job);
        
        // When: Updating the phase to FAILED state
        phaseService.updatePhaseState(phaseId, JobState.FAILED, null);
        
        // Then: The job is automatically transitioned to FAILED
        verify(jobService).getJob(jobId);
        verify(jobService).updateJobState(jobId, JobState.FAILED, null);
    }
    
    /**
     * Property: Failed Phase Does Not Affect Non-InProgress Job
     * 
     * **Validates: Requirements 12.4**
     * 
     * For any job NOT in INPROGRESS state, updating a phase to FAILED state 
     * should not affect the job state.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 15: Failed Phase Transitions Job To Failed")
    void failedPhaseDoesNotAffectNonInProgressJob(
            @ForAll("nonInProgressJobStates") JobState jobState,
            @ForAll("inProgressPhaseStates") JobState initialPhaseState) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = new StateTransitionValidator();
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job NOT in INPROGRESS state with a phase
        UUID phaseId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPhase phase = createPhase(phaseId, jobId, initialPhaseState);
        Job job = createTestJob(jobId, jobState);
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobService.getJob(jobId)).thenReturn(job);
        
        // When: Updating the phase to FAILED state
        phaseService.updatePhaseState(phaseId, JobState.FAILED, null);
        
        // Then: The job state is NOT updated
        verify(jobService).getJob(jobId);
        verify(jobService, never()).updateJobState(any(), any(), any());
    }
    
    /**
     * Property: Non-Failed Phase State Updates Do Not Affect Job
     * 
     * **Validates: Requirements 12.4**
     * 
     * For any phase state update that is NOT to FAILED state, the job state 
     * should not be affected.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 15: Failed Phase Transitions Job To Failed")
    void nonFailedPhaseStateUpdatesDoNotAffectJob(
            @ForAll("validPhaseTransitions") StateTransition transition) {
        
        // Skip FAILED transitions
        if (transition.toState() == JobState.FAILED) {
            return;
        }
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = new StateTransitionValidator();
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A phase in the initial state
        UUID phaseId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPhase phase = createPhase(phaseId, jobId, transition.fromState());
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Updating the phase to a non-FAILED state
        phaseService.updatePhaseState(phaseId, transition.toState(), null);
        
        // Then: The job service is never called
        verify(jobService, never()).getJob(any());
        verify(jobService, never()).updateJobState(any(), any(), any());
    }
    
    /**
     * Property: Phase State Update Preserves Metadata When Null
     * 
     * **Validates: Requirements 5.4, 15.3**
     * 
     * For any phase state update with null metadata, the existing metadata 
     * should be preserved.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 2: Valid State Transitions Are Accepted")
    void phaseStateUpdatePreservesMetadataWhenNull(
            @ForAll("validPhaseTransitions") StateTransition transition,
            @ForAll("optionalMetadata") String originalMetadata) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = new StateTransitionValidator();
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A phase with metadata
        UUID phaseId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobPhase phase = createPhase(phaseId, jobId, transition.fromState());
        phase.setMetadata(originalMetadata);
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Mock job service for FAILED state transitions
        if (transition.toState() == JobState.FAILED) {
            Job job = createTestJob(jobId, JobState.INPROGRESS);
            when(jobService.getJob(jobId)).thenReturn(job);
            when(jobService.updateJobState(eq(jobId), eq(JobState.FAILED), eq(null))).thenReturn(job);
        }
        
        // When: Updating state with null metadata
        JobPhase updated = phaseService.updatePhaseState(phaseId, transition.toState(), null);
        
        // Then: Original metadata is preserved
        assertThat(updated.getMetadata()).isEqualTo(originalMetadata);
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<StateTransition> validPhaseTransitions() {
        return Arbitraries.of(
            new StateTransition(JobState.NOTSTARTED, JobState.INPROGRESS),
            new StateTransition(JobState.INPROGRESS, JobState.COMPLETED),
            new StateTransition(JobState.INPROGRESS, JobState.FAILED)
        );
    }
    
    @Provide
    Arbitrary<StateTransition> invalidPhaseTransitions() {
        return Arbitraries.of(
            new StateTransition(JobState.NOTSTARTED, JobState.COMPLETED),
            new StateTransition(JobState.NOTSTARTED, JobState.FAILED),
            new StateTransition(JobState.INPROGRESS, JobState.NOTSTARTED),
            new StateTransition(JobState.COMPLETED, JobState.NOTSTARTED),
            new StateTransition(JobState.COMPLETED, JobState.INPROGRESS),
            new StateTransition(JobState.FAILED, JobState.NOTSTARTED),
            new StateTransition(JobState.FAILED, JobState.INPROGRESS)
        );
    }
    
    @Provide
    Arbitrary<JobState> terminalStates() {
        return Arbitraries.of(JobState.COMPLETED, JobState.FAILED);
    }
    
    @Provide
    Arbitrary<JobState> inProgressPhaseStates() {
        // Only INPROGRESS can transition to FAILED
        return Arbitraries.just(JobState.INPROGRESS);
    }
    
    @Provide
    Arbitrary<JobState> nonInProgressJobStates() {
        return Arbitraries.of(JobState.NOTSTARTED, JobState.COMPLETED, JobState.FAILED);
    }
    
    @Provide
    Arbitrary<String> optionalMetadata() {
        return Arbitraries.of(
            null,
            "{}",
            "{\"key\":\"value\"}",
            "{\"progress\":50}",
            "{\"status\":\"pending\",\"retries\":0}"
        );
    }
    
    // ========== Helper Classes ==========
    
    static class StateTransition {
        private final JobState fromState;
        private final JobState toState;
        
        StateTransition(JobState fromState, JobState toState) {
            this.fromState = fromState;
            this.toState = toState;
        }
        
        JobState fromState() {
            return fromState;
        }
        
        JobState toState() {
            return toState;
        }
    }
    
    // ========== Helper Methods ==========
    
    private JobPhase createPhase(UUID phaseId, UUID jobId, JobState state) {
        JobPhase phase = new JobPhase();
        phase.setId(phaseId);
        phase.setJobId(jobId);
        phase.setPhaseName("TestPhase");
        phase.setState(state);
        phase.setCreatedAt(Instant.now().minusSeconds(60));
        phase.setUpdatedAt(Instant.now().minusSeconds(60));
        return phase;
    }
    
    private Job createTestJob(UUID jobId, JobState state) {
        Job job = new Job();
        job.setId(jobId);
        job.setEnvironmentId(UUID.randomUUID());
        job.setServiceName("TestService");
        job.setZoneId("zone1");
        job.setState(state);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        return job;
    }
}
