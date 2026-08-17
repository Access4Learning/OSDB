// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.domain.Job;
import org.sifunity.broker.jobservice.domain.JobPhase;
import org.sifunity.broker.jobservice.repository.JobPhaseRepository;
import org.sifunity.broker.jobservice.service.JobPhaseService;
import org.sifunity.broker.jobservice.service.JobService;
import org.sifunity.broker.jobservice.util.StateTransitionValidator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for job phase listing.
 * 
 * **Validates: Requirements 7.1, 7.3, 7.4**
 * 
 * Tests the following property:
 * - Property 7: Phase Listing Returns All Phases Ordered By Creation
 */
class PhaseListingPropertiesTest {
    
    /**
     * Property 7: Phase Listing Returns All Phases Ordered By Creation
     * 
     * **Validates: Requirements 7.1, 7.3, 7.4**
     * 
     * For any job with multiple phases, listing phases should return all phases 
     * ordered by createdAt ascending (oldest first).
     */
    @Property(tries = 100)
    @Label("Feature: job-service, Property 7: Phase Listing Returns All Phases Ordered By Creation")
    void phaseListingReturnsAllPhasesOrderedByCreation(
            @ForAll("phaseCount") int phaseCount) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job exists with multiple phases
        UUID jobId = UUID.randomUUID();
        Job job = createTestJob(jobId);
        
        List<JobPhase> phases = createPhasesWithDifferentTimestamps(jobId, phaseCount);
        
        when(jobService.getJob(jobId)).thenReturn(job);
        when(phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId)).thenReturn(phases);
        
        // When: Listing phases for the job
        List<JobPhase> result = phaseService.getPhasesByJobId(jobId);
        
        // Then: All phases are returned
        assertThat(result).hasSize(phaseCount);
        
        // And: Phases are ordered by createdAt ascending (oldest first)
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getCreatedAt())
                .isBeforeOrEqualTo(result.get(i + 1).getCreatedAt());
        }
        
        // And: All phases belong to the correct job
        for (JobPhase phase : result) {
            assertThat(phase.getJobId()).isEqualTo(jobId);
        }
    }
    
    /**
     * Property: Empty Phase List for Job with No Phases
     * 
     * **Validates: Requirements 7.2**
     * 
     * For any job with no phases, listing phases should return an empty list.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 7: Phase Listing Returns All Phases Ordered By Creation")
    void emptyPhaseListForJobWithNoPhases() {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job exists with no phases
        UUID jobId = UUID.randomUUID();
        Job job = createTestJob(jobId);
        
        when(jobService.getJob(jobId)).thenReturn(job);
        when(phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId)).thenReturn(List.of());
        
        // When: Listing phases for the job
        List<JobPhase> result = phaseService.getPhasesByJobId(jobId);
        
        // Then: An empty list is returned
        assertThat(result).isEmpty();
    }
    
    /**
     * Property: Phase Listing Includes All Phase Metadata
     * 
     * **Validates: Requirements 7.4**
     * 
     * For any job with phases, listing phases should include phase name, state, 
     * and timestamps for each phase.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 7: Phase Listing Returns All Phases Ordered By Creation")
    void phaseListingIncludesAllPhaseMetadata(
            @ForAll("phaseCount") int phaseCount) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job exists with multiple phases
        UUID jobId = UUID.randomUUID();
        Job job = createTestJob(jobId);
        
        List<JobPhase> phases = createPhasesWithDifferentTimestamps(jobId, phaseCount);
        
        when(jobService.getJob(jobId)).thenReturn(job);
        when(phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId)).thenReturn(phases);
        
        // When: Listing phases for the job
        List<JobPhase> result = phaseService.getPhasesByJobId(jobId);
        
        // Then: Each phase includes all required metadata
        for (JobPhase phase : result) {
            assertThat(phase.getId()).isNotNull();
            assertThat(phase.getJobId()).isNotNull();
            assertThat(phase.getPhaseName()).isNotBlank();
            assertThat(phase.getState()).isNotNull();
            assertThat(phase.getCreatedAt()).isNotNull();
            assertThat(phase.getUpdatedAt()).isNotNull();
        }
    }
    
    /**
     * Property: Phase Listing Preserves Chronological Order
     * 
     * **Validates: Requirements 7.3**
     * 
     * For any sequence of phase creations, the listing should preserve the 
     * chronological order of creation.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 7: Phase Listing Returns All Phases Ordered By Creation")
    void phaseListingPreservesChronologicalOrder(
            @ForAll("phaseNames") List<String> phaseNames) {
        
        // Create mocks
        JobPhaseRepository phaseRepository = mock(JobPhaseRepository.class);
        JobService jobService = mock(JobService.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobPhaseService phaseService = new JobPhaseService(phaseRepository, jobService, stateValidator);
        
        // Given: A job exists
        UUID jobId = UUID.randomUUID();
        Job job = createTestJob(jobId);
        
        // Create phases with incrementing timestamps
        List<JobPhase> phases = new ArrayList<>();
        Instant baseTime = Instant.now().minusSeconds(phaseNames.size() * 10);
        
        for (int i = 0; i < phaseNames.size(); i++) {
            JobPhase phase = new JobPhase();
            phase.setId(UUID.randomUUID());
            phase.setJobId(jobId);
            phase.setPhaseName(phaseNames.get(i));
            phase.setState(JobState.NOTSTARTED);
            phase.setCreatedAt(baseTime.plusSeconds(i * 10));
            phase.setUpdatedAt(baseTime.plusSeconds(i * 10));
            phases.add(phase);
        }
        
        when(jobService.getJob(jobId)).thenReturn(job);
        when(phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId)).thenReturn(phases);
        
        // When: Listing phases for the job
        List<JobPhase> result = phaseService.getPhasesByJobId(jobId);
        
        // Then: Phases are in chronological order
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).getPhaseName()).isEqualTo(phaseNames.get(i));
        }
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<Integer> phaseCount() {
        return Arbitraries.integers().between(1, 10);
    }
    
    @Provide
    Arbitrary<List<String>> phaseNames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .ofMinLength(1)
            .ofMaxLength(20)
            .list()
            .ofMinSize(1)
            .ofMaxSize(10)
            .uniqueElements();
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
    
    private List<JobPhase> createPhasesWithDifferentTimestamps(UUID jobId, int count) {
        List<JobPhase> phases = new ArrayList<>();
        Instant baseTime = Instant.now().minusSeconds(count * 10);
        
        for (int i = 0; i < count; i++) {
            JobPhase phase = new JobPhase();
            phase.setId(UUID.randomUUID());
            phase.setJobId(jobId);
            phase.setPhaseName("Phase" + i);
            phase.setState(JobState.values()[i % JobState.values().length]);
            phase.setCreatedAt(baseTime.plusSeconds(i * 10));
            phase.setUpdatedAt(baseTime.plusSeconds(i * 10));
            phases.add(phase);
        }
        
        return phases;
    }
}
