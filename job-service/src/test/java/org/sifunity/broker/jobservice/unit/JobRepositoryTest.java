// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.domain.Job;
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
 * Unit tests for JobRepository.
 * 
 * Tests the custom query methods to ensure they correctly filter and order jobs.
 * Uses @SpringBootTest with Testcontainers PostgreSQL for real database testing.
 */
@SpringBootTest
@Testcontainers
@Transactional
class JobRepositoryTest {
    
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
    private JobRepository jobRepository;
    
    private UUID environmentId1;
    private UUID environmentId2;
    private Instant now;
    
    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        
        environmentId1 = UUID.randomUUID();
        environmentId2 = UUID.randomUUID();
        now = Instant.now();
    }
    
    @Test
    void findByEnvironmentIdOrderByCreatedAtDesc_ReturnsJobsInReverseChronologicalOrder() {
        // Given: Three jobs for the same environment created at different times
        Job job1 = createJob(environmentId1, "service1", JobState.NOTSTARTED, now.minusSeconds(300));
        Job job2 = createJob(environmentId1, "service2", JobState.INPROGRESS, now.minusSeconds(200));
        Job job3 = createJob(environmentId1, "service3", JobState.COMPLETED, now.minusSeconds(100));
        
        jobRepository.saveAll(List.of(job1, job2, job3));
        
        // When: Querying by environment ID
        List<Job> jobs = jobRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId1);
        
        // Then: Jobs are returned newest first
        assertThat(jobs).hasSize(3);
        assertThat(jobs.get(0).getId()).isEqualTo(job3.getId());
        assertThat(jobs.get(1).getId()).isEqualTo(job2.getId());
        assertThat(jobs.get(2).getId()).isEqualTo(job1.getId());
    }
    
    @Test
    void findByEnvironmentIdOrderByCreatedAtDesc_FiltersCorrectEnvironment() {
        // Given: Jobs for two different environments
        Job job1 = createJob(environmentId1, "service1", JobState.NOTSTARTED, now);
        Job job2 = createJob(environmentId2, "service2", JobState.INPROGRESS, now);
        
        jobRepository.saveAll(List.of(job1, job2));
        
        // When: Querying by environment ID 1
        List<Job> jobs = jobRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId1);
        
        // Then: Only jobs for environment 1 are returned
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getId()).isEqualTo(job1.getId());
    }
    
    @Test
    void findByEnvironmentIdAndState_FiltersCorrectly() {
        // Given: Jobs with different states
        Job job1 = createJob(environmentId1, "service1", JobState.NOTSTARTED, now.minusSeconds(300));
        Job job2 = createJob(environmentId1, "service2", JobState.INPROGRESS, now.minusSeconds(200));
        Job job3 = createJob(environmentId1, "service3", JobState.INPROGRESS, now.minusSeconds(100));
        Job job4 = createJob(environmentId1, "service4", JobState.COMPLETED, now);
        
        jobRepository.saveAll(List.of(job1, job2, job3, job4));
        
        // When: Querying for INPROGRESS jobs
        List<Job> jobs = jobRepository.findByEnvironmentIdAndState(environmentId1, JobState.INPROGRESS);
        
        // Then: Only INPROGRESS jobs are returned
        assertThat(jobs).hasSize(2);
        assertThat(jobs).extracting(Job::getId).containsExactlyInAnyOrder(job2.getId(), job3.getId());
        assertThat(jobs).allMatch(job -> job.getState() == JobState.INPROGRESS);
    }
    
    @Test
    void findByEnvironmentIdAndServiceName_FiltersCorrectly() {
        // Given: Jobs with different service names
        Job job1 = createJob(environmentId1, "serviceA", JobState.NOTSTARTED, now.minusSeconds(300));
        Job job2 = createJob(environmentId1, "serviceB", JobState.INPROGRESS, now.minusSeconds(200));
        Job job3 = createJob(environmentId1, "serviceA", JobState.INPROGRESS, now.minusSeconds(100));
        Job job4 = createJob(environmentId1, "serviceC", JobState.COMPLETED, now);
        
        jobRepository.saveAll(List.of(job1, job2, job3, job4));
        
        // When: Querying for serviceA jobs
        List<Job> jobs = jobRepository.findByEnvironmentIdAndServiceName(environmentId1, "serviceA");
        
        // Then: Only serviceA jobs are returned
        assertThat(jobs).hasSize(2);
        assertThat(jobs).extracting(Job::getId).containsExactlyInAnyOrder(job1.getId(), job3.getId());
        assertThat(jobs).allMatch(job -> job.getServiceName().equals("serviceA"));
    }
    
    @Test
    void findByEnvironmentIdAndStateAndServiceName_FiltersCorrectly() {
        // Given: Jobs with various states and service names
        Job job1 = createJob(environmentId1, "serviceA", JobState.NOTSTARTED, now.minusSeconds(400));
        Job job2 = createJob(environmentId1, "serviceA", JobState.INPROGRESS, now.minusSeconds(300));
        Job job3 = createJob(environmentId1, "serviceB", JobState.INPROGRESS, now.minusSeconds(200));
        Job job4 = createJob(environmentId1, "serviceA", JobState.INPROGRESS, now.minusSeconds(100));
        Job job5 = createJob(environmentId1, "serviceA", JobState.COMPLETED, now);
        
        jobRepository.saveAll(List.of(job1, job2, job3, job4, job5));
        
        // When: Querying for INPROGRESS serviceA jobs
        List<Job> jobs = jobRepository.findByEnvironmentIdAndStateAndServiceName(
                environmentId1, JobState.INPROGRESS, "serviceA");
        
        // Then: Only INPROGRESS serviceA jobs are returned
        assertThat(jobs).hasSize(2);
        assertThat(jobs).extracting(Job::getId).containsExactlyInAnyOrder(job2.getId(), job4.getId());
        assertThat(jobs).allMatch(job -> 
                job.getState() == JobState.INPROGRESS && 
                job.getServiceName().equals("serviceA"));
    }
    
    @Test
    void findByEnvironmentIdOrderByCreatedAtDesc_ReturnsEmptyListWhenNoJobs() {
        // Given: No jobs for the environment
        
        // When: Querying by environment ID
        List<Job> jobs = jobRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId1);
        
        // Then: Empty list is returned
        assertThat(jobs).isEmpty();
    }
    
    @Test
    void findByEnvironmentIdAndState_ReturnsEmptyListWhenNoMatchingJobs() {
        // Given: Jobs with different states
        Job job1 = createJob(environmentId1, "service1", JobState.NOTSTARTED, now);
        jobRepository.save(job1);
        
        // When: Querying for COMPLETED jobs
        List<Job> jobs = jobRepository.findByEnvironmentIdAndState(environmentId1, JobState.COMPLETED);
        
        // Then: Empty list is returned
        assertThat(jobs).isEmpty();
    }
    
    // Helper method to create a job
    private Job createJob(UUID environmentId, String serviceName, JobState state, Instant createdAt) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEnvironmentId(environmentId);
        job.setServiceName(serviceName);
        job.setZoneId("default");
        job.setState(state);
        job.setCreatedAt(createdAt);
        job.setUpdatedAt(createdAt);
        return job;
    }
}
