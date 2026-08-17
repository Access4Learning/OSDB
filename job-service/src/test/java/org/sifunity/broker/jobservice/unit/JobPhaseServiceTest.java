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
import org.sifunity.broker.jobservice.domain.JobPhase;
import org.sifunity.broker.jobservice.exception.DuplicatePhaseException;
import org.sifunity.broker.jobservice.exception.InvalidStateTransitionException;
import org.sifunity.broker.jobservice.exception.JobNotFoundException;
import org.sifunity.broker.jobservice.exception.PhaseNotFoundException;
import org.sifunity.broker.jobservice.repository.JobPhaseRepository;
import org.sifunity.broker.jobservice.service.JobPhaseService;
import org.sifunity.broker.jobservice.service.JobService;
import org.sifunity.broker.jobservice.util.StateTransitionValidator;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JobPhaseService.
 * 
 * Tests cover:
 * - Phase creation with validation
 * - Duplicate phase name detection
 * - Phase listing ordered by creation time
 * - Phase state transitions
 * - Automatic job failure when phase fails
 * - Error handling for non-existent jobs and phases
 */
@ExtendWith(MockitoExtension.class)
class JobPhaseServiceTest {
    
    @Mock
    private JobPhaseRepository phaseRepository;
    
    @Mock
    private JobService jobService;
    
    @Mock
    private StateTransitionValidator stateValidator;
    
    @InjectMocks
    private JobPhaseService phaseService;
    
    private UUID jobId;
    private UUID phaseId;
    private Job testJob;
    
    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        phaseId = UUID.randomUUID();
        
        testJob = new Job();
        testJob.setId(jobId);
        testJob.setEnvironmentId(UUID.randomUUID());
        testJob.setServiceName("TestService");
        testJob.setZoneId("zone1");
        testJob.setState(JobState.INPROGRESS);
        testJob.setCreatedAt(Instant.now());
        testJob.setUpdatedAt(Instant.now());
    }
    
    // ========== Phase Creation Tests ==========
    
    @Test
    void testCreatePhase_ValidParameters_CreatesPhase() {
        // Given
        String phaseName = "InitializationPhase";
        JobState state = JobState.NOTSTARTED;
        String metadata = "{\"key\":\"value\"}";
        
        when(jobService.getJob(jobId)).thenReturn(testJob);
        when(phaseRepository.existsByJobIdAndPhaseName(jobId, phaseName)).thenReturn(false);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        JobPhase result = phaseService.createPhase(jobId, phaseName, state, metadata);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getJobId()).isEqualTo(jobId);
        assertThat(result.getPhaseName()).isEqualTo(phaseName);
        assertThat(result.getState()).isEqualTo(state);
        assertThat(result.getMetadata()).isEqualTo(metadata);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getCreatedAt()).isEqualTo(result.getUpdatedAt());
        
        verify(jobService).getJob(jobId);
        verify(phaseRepository).existsByJobIdAndPhaseName(jobId, phaseName);
        verify(phaseRepository).save(any(JobPhase.class));
        verify(jobService).updateCurrentPhase(jobId, phaseName);
    }
    
    @Test
    void testCreatePhase_NonExistentJob_ThrowsJobNotFoundException() {
        // Given
        String phaseName = "TestPhase";
        when(jobService.getJob(jobId)).thenThrow(new JobNotFoundException("Job not found: " + jobId));
        
        // When/Then
        assertThatThrownBy(() -> phaseService.createPhase(jobId, phaseName, JobState.NOTSTARTED, null))
            .isInstanceOf(JobNotFoundException.class)
            .hasMessageContaining("Job not found");
        
        verify(jobService).getJob(jobId);
        verify(phaseRepository, never()).save(any());
    }
    
    @Test
    void testCreatePhase_DuplicatePhaseName_ThrowsDuplicatePhaseException() {
        // Given
        String phaseName = "DuplicatePhase";
        when(jobService.getJob(jobId)).thenReturn(testJob);
        when(phaseRepository.existsByJobIdAndPhaseName(jobId, phaseName)).thenReturn(true);
        
        // When/Then
        assertThatThrownBy(() -> phaseService.createPhase(jobId, phaseName, JobState.NOTSTARTED, null))
            .isInstanceOf(DuplicatePhaseException.class)
            .hasMessageContaining("Phase already exists")
            .hasMessageContaining(phaseName);
        
        verify(phaseRepository, never()).save(any());
    }
    
    @Test
    void testCreatePhase_NullPhaseName_ThrowsIllegalArgumentException() {
        // Given
        when(jobService.getJob(jobId)).thenReturn(testJob);
        
        // When/Then
        assertThatThrownBy(() -> phaseService.createPhase(jobId, null, JobState.NOTSTARTED, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("phaseName cannot be null or blank");
    }
    
    @Test
    void testCreatePhase_BlankPhaseName_ThrowsIllegalArgumentException() {
        // Given
        when(jobService.getJob(jobId)).thenReturn(testJob);
        
        // When/Then
        assertThatThrownBy(() -> phaseService.createPhase(jobId, "   ", JobState.NOTSTARTED, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("phaseName cannot be null or blank");
    }
    
    @Test
    void testCreatePhase_PhaseNameTooLong_ThrowsIllegalArgumentException() {
        // Given
        String longPhaseName = "a".repeat(101);
        when(jobService.getJob(jobId)).thenReturn(testJob);
        
        // When/Then
        assertThatThrownBy(() -> phaseService.createPhase(jobId, longPhaseName, JobState.NOTSTARTED, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("phaseName cannot exceed 100 characters");
    }
    
    @Test
    void testCreatePhase_NullState_ThrowsIllegalArgumentException() {
        // Given
        when(jobService.getJob(jobId)).thenReturn(testJob);
        
        // When/Then
        assertThatThrownBy(() -> phaseService.createPhase(jobId, "TestPhase", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("state cannot be null");
    }
    
    @Test
    void testCreatePhase_UpdatesJobCurrentPhase() {
        // Given
        String phaseName = "CurrentPhase";
        when(jobService.getJob(jobId)).thenReturn(testJob);
        when(phaseRepository.existsByJobIdAndPhaseName(jobId, phaseName)).thenReturn(false);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        phaseService.createPhase(jobId, phaseName, JobState.NOTSTARTED, null);
        
        // Then
        verify(jobService).updateCurrentPhase(jobId, phaseName);
    }
    
    // ========== Phase Listing Tests ==========
    
    @Test
    void testGetPhasesByJobId_ReturnsAllPhasesOrderedByCreatedAt() {
        // Given
        Instant now = Instant.now();
        
        JobPhase phase1 = createPhase(UUID.randomUUID(), "Phase1", JobState.COMPLETED, now.minusSeconds(300));
        JobPhase phase2 = createPhase(UUID.randomUUID(), "Phase2", JobState.INPROGRESS, now.minusSeconds(200));
        JobPhase phase3 = createPhase(UUID.randomUUID(), "Phase3", JobState.NOTSTARTED, now.minusSeconds(100));
        
        when(jobService.getJob(jobId)).thenReturn(testJob);
        when(phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId))
            .thenReturn(Arrays.asList(phase1, phase2, phase3));
        
        // When
        List<JobPhase> result = phaseService.getPhasesByJobId(jobId);
        
        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getPhaseName()).isEqualTo("Phase1");
        assertThat(result.get(1).getPhaseName()).isEqualTo("Phase2");
        assertThat(result.get(2).getPhaseName()).isEqualTo("Phase3");
        
        // Verify ordering by creation time (oldest first)
        assertThat(result.get(0).getCreatedAt()).isBefore(result.get(1).getCreatedAt());
        assertThat(result.get(1).getCreatedAt()).isBefore(result.get(2).getCreatedAt());
        
        verify(jobService).getJob(jobId);
        verify(phaseRepository).findByJobIdOrderByCreatedAtAsc(jobId);
    }
    
    @Test
    void testGetPhasesByJobId_NoPhases_ReturnsEmptyList() {
        // Given
        when(jobService.getJob(jobId)).thenReturn(testJob);
        when(phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId)).thenReturn(List.of());
        
        // When
        List<JobPhase> result = phaseService.getPhasesByJobId(jobId);
        
        // Then
        assertThat(result).isEmpty();
        verify(jobService).getJob(jobId);
    }
    
    @Test
    void testGetPhasesByJobId_NonExistentJob_ThrowsJobNotFoundException() {
        // Given
        when(jobService.getJob(jobId)).thenThrow(new JobNotFoundException("Job not found: " + jobId));
        
        // When/Then
        assertThatThrownBy(() -> phaseService.getPhasesByJobId(jobId))
            .isInstanceOf(JobNotFoundException.class)
            .hasMessageContaining("Job not found");
        
        verify(phaseRepository, never()).findByJobIdOrderByCreatedAtAsc(any());
    }
    
    // ========== Phase State Update Tests ==========
    
    @Test
    void testUpdatePhaseState_ValidTransition_UpdatesState() {
        // Given
        JobPhase phase = createPhase(phaseId, "TestPhase", JobState.NOTSTARTED, Instant.now());
        Instant originalUpdatedAt = phase.getUpdatedAt();
        JobState newState = JobState.INPROGRESS;
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(stateValidator.isValidTransition(JobState.NOTSTARTED, newState)).thenReturn(true);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        JobPhase result = phaseService.updatePhaseState(phaseId, newState, null);
        
        // Then
        assertThat(result.getState()).isEqualTo(newState);
        assertThat(result.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        
        verify(phaseRepository).findById(phaseId);
        verify(stateValidator).isValidTransition(JobState.NOTSTARTED, newState);
        verify(phaseRepository).save(any(JobPhase.class));
    }
    
    @Test
    void testUpdatePhaseState_InvalidTransition_ThrowsInvalidStateTransitionException() {
        // Given
        JobPhase phase = createPhase(phaseId, "TestPhase", JobState.COMPLETED, Instant.now());
        JobState newState = JobState.INPROGRESS;
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(stateValidator.isValidTransition(JobState.COMPLETED, newState)).thenReturn(false);
        
        // When/Then
        assertThatThrownBy(() -> phaseService.updatePhaseState(phaseId, newState, null))
            .isInstanceOf(InvalidStateTransitionException.class)
            .hasMessageContaining("Invalid phase transition")
            .hasMessageContaining("COMPLETED")
            .hasMessageContaining("INPROGRESS");
        
        verify(phaseRepository, never()).save(any());
    }
    
    @Test
    void testUpdatePhaseState_NonExistentPhase_ThrowsPhaseNotFoundException() {
        // Given
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> phaseService.updatePhaseState(phaseId, JobState.INPROGRESS, null))
            .isInstanceOf(PhaseNotFoundException.class)
            .hasMessageContaining("Phase not found")
            .hasMessageContaining(phaseId.toString());
        
        verify(stateValidator, never()).isValidTransition(any(), any());
        verify(phaseRepository, never()).save(any());
    }
    
    @Test
    void testUpdatePhaseState_WithMetadata_UpdatesMetadata() {
        // Given
        JobPhase phase = createPhase(phaseId, "TestPhase", JobState.NOTSTARTED, Instant.now());
        String newMetadata = "{\"progress\":\"50%\"}";
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(stateValidator.isValidTransition(JobState.NOTSTARTED, JobState.INPROGRESS)).thenReturn(true);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        JobPhase result = phaseService.updatePhaseState(phaseId, JobState.INPROGRESS, newMetadata);
        
        // Then
        assertThat(result.getMetadata()).isEqualTo(newMetadata);
    }
    
    @Test
    void testUpdatePhaseState_NullMetadata_PreservesExistingMetadata() {
        // Given
        String originalMetadata = "{\"original\":\"data\"}";
        JobPhase phase = createPhase(phaseId, "TestPhase", JobState.NOTSTARTED, Instant.now());
        phase.setMetadata(originalMetadata);
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(stateValidator.isValidTransition(JobState.NOTSTARTED, JobState.INPROGRESS)).thenReturn(true);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        JobPhase result = phaseService.updatePhaseState(phaseId, JobState.INPROGRESS, null);
        
        // Then
        assertThat(result.getMetadata()).isEqualTo(originalMetadata);
    }
    
    // ========== Phase Failure Cascades to Job Tests ==========
    
    @Test
    void testUpdatePhaseState_ToFailed_FailsJobIfInProgress() {
        // Given
        JobPhase phase = createPhase(phaseId, "TestPhase", JobState.INPROGRESS, Instant.now());
        phase.setJobId(jobId);
        
        testJob.setState(JobState.INPROGRESS);
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(stateValidator.isValidTransition(JobState.INPROGRESS, JobState.FAILED)).thenReturn(true);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobService.getJob(jobId)).thenReturn(testJob);
        when(jobService.updateJobState(eq(jobId), eq(JobState.FAILED), eq(null))).thenReturn(testJob);
        
        // When
        phaseService.updatePhaseState(phaseId, JobState.FAILED, null);
        
        // Then
        verify(jobService).getJob(jobId);
        verify(jobService).updateJobState(jobId, JobState.FAILED, null);
    }
    
    @Test
    void testUpdatePhaseState_ToFailed_DoesNotFailJobIfNotInProgress() {
        // Given
        JobPhase phase = createPhase(phaseId, "TestPhase", JobState.INPROGRESS, Instant.now());
        phase.setJobId(jobId);
        
        testJob.setState(JobState.COMPLETED);
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(stateValidator.isValidTransition(JobState.INPROGRESS, JobState.FAILED)).thenReturn(true);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobService.getJob(jobId)).thenReturn(testJob);
        
        // When
        phaseService.updatePhaseState(phaseId, JobState.FAILED, null);
        
        // Then
        verify(jobService).getJob(jobId);
        verify(jobService, never()).updateJobState(any(), any(), any());
    }
    
    @Test
    void testUpdatePhaseState_ToCompleted_DoesNotAffectJob() {
        // Given
        JobPhase phase = createPhase(phaseId, "TestPhase", JobState.INPROGRESS, Instant.now());
        phase.setJobId(jobId);
        
        when(phaseRepository.findById(phaseId)).thenReturn(Optional.of(phase));
        when(stateValidator.isValidTransition(JobState.INPROGRESS, JobState.COMPLETED)).thenReturn(true);
        when(phaseRepository.save(any(JobPhase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        phaseService.updatePhaseState(phaseId, JobState.COMPLETED, null);
        
        // Then
        verify(jobService, never()).getJob(any());
        verify(jobService, never()).updateJobState(any(), any(), any());
    }
    
    // ========== Helper Methods ==========
    
    private JobPhase createPhase(UUID id, String name, JobState state, Instant createdAt) {
        JobPhase phase = new JobPhase();
        phase.setId(id);
        phase.setJobId(jobId);
        phase.setPhaseName(name);
        phase.setState(state);
        phase.setCreatedAt(createdAt);
        phase.setUpdatedAt(createdAt);
        return phase;
    }
}
