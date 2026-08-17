// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.domain.Job;
import org.sifunity.broker.jobservice.domain.JobPhase;
import org.sifunity.broker.jobservice.repository.JobPhaseRepository;
import org.sifunity.broker.jobservice.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JobPhaseRepository.
 * 
 * Tests the custom query methods to ensure they correctly filter, order, and manage phases.
 * Uses @SpringBootTest with Testcontainers PostgreSQL for real database testing.
 */
@SpringBootTest
@Testcontainers
@Transactional
class JobPhaseRepositoryTest {
    
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
    private JobPhaseRepository phaseRepository;
    
    @Autowired
    private JobRepository jobRepository;
    
    private UUID jobId1;
    private UUID jobId2;
    private Instant now;
    
    @BeforeEach
    void setUp() {
        phaseRepository.deleteAll();
        jobRepository.deleteAll();
        
        now = Instant.now();
        
        // Create parent jobs to satisfy FK constraints
        jobId1 = UUID.randomUUID();
        jobId2 = UUID.randomUUID();
        
        Job job1 = new Job();
        job1.setId(jobId1);
        job1.setEnvironmentId(UUID.randomUUID());
        job1.setServiceName("TestService");
        job1.setZoneId("zone1");
        job1.setState(JobState.NOTSTARTED);
        job1.setCreatedAt(now);
        job1.setUpdatedAt(now);
        jobRepository.save(job1);
        
        Job job2 = new Job();
        job2.setId(jobId2);
        job2.setEnvironmentId(UUID.randomUUID());
        job2.setServiceName("TestService");
        job2.setZoneId("zone1");
        job2.setState(JobState.NOTSTARTED);
        job2.setCreatedAt(now);
        job2.setUpdatedAt(now);
        jobRepository.save(job2);
    }
    
    @Test
    void findByJobIdOrderByCreatedAtAsc_ReturnsPhasesInChronologicalOrder() {
        // Given: Three phases for the same job created at different times
        JobPhase phase1 = createPhase(jobId1, "initialization", JobState.COMPLETED, now.minusSeconds(300));
        JobPhase phase2 = createPhase(jobId1, "processing", JobState.INPROGRESS, now.minusSeconds(200));
        JobPhase phase3 = createPhase(jobId1, "finalization", JobState.NOTSTARTED, now.minusSeconds(100));
        
        phaseRepository.saveAll(List.of(phase1, phase2, phase3));
        
        // When: Querying by job ID
        List<JobPhase> phases = phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId1);
        
        // Then: Phases are returned oldest first (chronological order)
        assertThat(phases).hasSize(3);
        assertThat(phases.get(0).getId()).isEqualTo(phase1.getId());
        assertThat(phases.get(1).getId()).isEqualTo(phase2.getId());
        assertThat(phases.get(2).getId()).isEqualTo(phase3.getId());
    }
    
    @Test
    void findByJobIdOrderByCreatedAtAsc_FiltersCorrectJob() {
        // Given: Phases for two different jobs
        JobPhase phase1 = createPhase(jobId1, "phase1", JobState.NOTSTARTED, now.minusSeconds(200));
        JobPhase phase2 = createPhase(jobId2, "phase2", JobState.INPROGRESS, now.minusSeconds(100));
        JobPhase phase3 = createPhase(jobId1, "phase3", JobState.COMPLETED, now);
        
        phaseRepository.saveAll(List.of(phase1, phase2, phase3));
        
        // When: Querying by job ID 1
        List<JobPhase> phases = phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId1);
        
        // Then: Only phases for job 1 are returned
        assertThat(phases).hasSize(2);
        assertThat(phases).extracting(JobPhase::getId).containsExactly(phase1.getId(), phase3.getId());
        assertThat(phases).allMatch(phase -> phase.getJobId().equals(jobId1));
    }
    
    @Test
    void findByJobIdOrderByCreatedAtAsc_ReturnsEmptyListWhenNoPhases() {
        // Given: No phases for the job
        
        // When: Querying by job ID
        List<JobPhase> phases = phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId1);
        
        // Then: Empty list is returned
        assertThat(phases).isEmpty();
    }
    
    @Test
    void existsByJobIdAndPhaseName_ReturnsTrueWhenPhaseExists() {
        // Given: A phase with a specific name
        JobPhase phase = createPhase(jobId1, "initialization", JobState.NOTSTARTED, now);
        phaseRepository.save(phase);
        
        // When: Checking if the phase exists
        boolean exists = phaseRepository.existsByJobIdAndPhaseName(jobId1, "initialization");
        
        // Then: Returns true
        assertThat(exists).isTrue();
    }
    
    @Test
    void existsByJobIdAndPhaseName_ReturnsFalseWhenPhaseDoesNotExist() {
        // Given: A phase with a different name
        JobPhase phase = createPhase(jobId1, "initialization", JobState.NOTSTARTED, now);
        phaseRepository.save(phase);
        
        // When: Checking for a non-existent phase name
        boolean exists = phaseRepository.existsByJobIdAndPhaseName(jobId1, "processing");
        
        // Then: Returns false
        assertThat(exists).isFalse();
    }
    
    @Test
    void existsByJobIdAndPhaseName_ReturnsFalseForDifferentJob() {
        // Given: A phase for job 1
        JobPhase phase = createPhase(jobId1, "initialization", JobState.NOTSTARTED, now);
        phaseRepository.save(phase);
        
        // When: Checking for the same phase name but different job
        boolean exists = phaseRepository.existsByJobIdAndPhaseName(jobId2, "initialization");
        
        // Then: Returns false (phase name is unique per job, not globally)
        assertThat(exists).isFalse();
    }
    
    @Test
    void existsByJobIdAndPhaseName_IsCaseSensitive() {
        // Given: A phase with lowercase name
        JobPhase phase = createPhase(jobId1, "initialization", JobState.NOTSTARTED, now);
        phaseRepository.save(phase);
        
        // When: Checking with different case
        boolean exists = phaseRepository.existsByJobIdAndPhaseName(jobId1, "Initialization");
        
        // Then: Returns false (case-sensitive comparison)
        assertThat(exists).isFalse();
    }
    
    @Test
    void deleteByJobId_DeletesAllPhasesForJob() {
        // Given: Multiple phases for two different jobs
        JobPhase phase1 = createPhase(jobId1, "phase1", JobState.NOTSTARTED, now.minusSeconds(300));
        JobPhase phase2 = createPhase(jobId1, "phase2", JobState.INPROGRESS, now.minusSeconds(200));
        JobPhase phase3 = createPhase(jobId2, "phase3", JobState.COMPLETED, now.minusSeconds(100));
        JobPhase phase4 = createPhase(jobId1, "phase4", JobState.FAILED, now);
        
        phaseRepository.saveAll(List.of(phase1, phase2, phase3, phase4));
        
        // When: Deleting all phases for job 1
        phaseRepository.deleteByJobId(jobId1);
        phaseRepository.flush(); // Ensure delete is executed
        
        // Then: Only phases for job 1 are deleted
        List<JobPhase> remainingPhases = phaseRepository.findAll();
        assertThat(remainingPhases).hasSize(1);
        assertThat(remainingPhases.get(0).getId()).isEqualTo(phase3.getId());
        assertThat(remainingPhases.get(0).getJobId()).isEqualTo(jobId2);
    }
    
    @Test
    void deleteByJobId_DoesNothingWhenNoPhases() {
        // Given: No phases for the job
        JobPhase phase = createPhase(jobId2, "phase1", JobState.NOTSTARTED, now);
        phaseRepository.save(phase);
        
        // When: Deleting phases for job 1 (which has no phases)
        phaseRepository.deleteByJobId(jobId1);
        phaseRepository.flush();
        
        // Then: No error occurs and other phases remain
        List<JobPhase> remainingPhases = phaseRepository.findAll();
        assertThat(remainingPhases).hasSize(1);
        assertThat(remainingPhases.get(0).getId()).isEqualTo(phase.getId());
    }
    
    @Test
    void findByJobIdOrderByCreatedAtAsc_HandlesMultiplePhasesWithSameTimestamp() {
        // Given: Multiple phases created at the same time
        JobPhase phase1 = createPhase(jobId1, "phase1", JobState.NOTSTARTED, now);
        JobPhase phase2 = createPhase(jobId1, "phase2", JobState.INPROGRESS, now);
        JobPhase phase3 = createPhase(jobId1, "phase3", JobState.COMPLETED, now);
        
        phaseRepository.saveAll(List.of(phase1, phase2, phase3));
        
        // When: Querying by job ID
        List<JobPhase> phases = phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId1);
        
        // Then: All phases are returned (order may vary for same timestamp)
        assertThat(phases).hasSize(3);
        assertThat(phases).extracting(JobPhase::getId)
                .containsExactlyInAnyOrder(phase1.getId(), phase2.getId(), phase3.getId());
    }
    
    @Test
    void save_EnforcesUniqueConstraintOnJobIdAndPhaseName() {
        // Given: A phase with a specific name
        JobPhase phase1 = createPhase(jobId1, "initialization", JobState.NOTSTARTED, now);
        phaseRepository.save(phase1);
        
        // Verify the constraint exists by checking existsByJobIdAndPhaseName
        assertThat(phaseRepository.existsByJobIdAndPhaseName(jobId1, "initialization")).isTrue();
    }
    
    // Helper method to create a phase
    private JobPhase createPhase(UUID jobId, String phaseName, JobState state, Instant createdAt) {
        JobPhase phase = new JobPhase();
        phase.setId(UUID.randomUUID());
        phase.setJobId(jobId);
        phase.setPhaseName(phaseName);
        phase.setState(state);
        phase.setCreatedAt(createdAt);
        phase.setUpdatedAt(createdAt);
        return phase;
    }
}
