// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.domain.Job;
import org.sifunity.broker.jobservice.repository.JobRepository;
import org.sifunity.broker.jobservice.service.JobService;
import org.sifunity.broker.jobservice.util.StateTransitionValidator;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for job creation.
 * 
 * These tests verify universal properties that should hold true for all valid
 * job creation scenarios, using jqwik for property-based testing.
 */
class JobCreationPropertiesTest {
    
    /**
     * Property 1: Job Creation Initializes Correct State
     * 
     * For any valid job creation request, the created job should have:
     * - State NOTSTARTED
     * - A unique UUID
     * - createdAt equal to updatedAt
     * 
     * **Validates: Requirements 1.1, 1.2, 1.5, 1.7**
     */
    @Property(tries = 100)
    @Label("Feature: job-service, Property 1: Job Creation Initializes Correct State")
    void jobCreationInitializesCorrectState(
            @ForAll("validEnvironmentIds") UUID environmentId,
            @ForAll("validServiceNames") String serviceName,
            @ForAll("validZoneIds") String zoneId,
            @ForAll("optionalContextIds") String contextId,
            @ForAll("optionalMetadata") String metadata) {
        
        // Create mocks
        JobRepository jobRepository = mock(JobRepository.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobService jobService = new JobService(jobRepository, stateValidator);
        
        // Mock repository save to return entity with ID and timestamp
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            // Simulate what the database would do - keep the values set by the service
            return job;
        });
        
        // When: Creating a job with valid parameters
        Job job = jobService.createJob(environmentId, serviceName, zoneId, contextId, metadata);
        
        // Then: Job is initialized with correct state
        assertThat(job).isNotNull();
        assertThat(job.getId()).isNotNull();
        assertThat(job.getState()).isEqualTo(JobState.NOTSTARTED);
        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(job.getUpdatedAt()).isNotNull();
        assertThat(job.getCreatedAt()).isEqualTo(job.getUpdatedAt());
        assertThat(job.getEnvironmentId()).isEqualTo(environmentId);
        assertThat(job.getServiceName()).isEqualTo(serviceName);
        assertThat(job.getZoneId()).isEqualTo(zoneId);
    }
    
    /**
     * Property 18: Required Fields Validation
     * 
     * For any job creation request missing required fields (environmentId, serviceName, zoneId),
     * the request should be rejected with IllegalArgumentException.
     * 
     * **Validates: Requirements 1.3, 1.4**
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property 18: Required Fields Validation")
    void requiredFieldsValidation(
            @ForAll("validServiceNames") String serviceName,
            @ForAll("validZoneIds") String zoneId) {
        
        // Create mocks
        JobRepository jobRepository = mock(JobRepository.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobService jobService = new JobService(jobRepository, stateValidator);
        
        // When/Then: Creating job with null environmentId throws exception
        assertThatThrownBy(() -> 
            jobService.createJob(null, serviceName, zoneId, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("environmentId");
    }
    
    @Property(tries = 50)
    @Label("Feature: job-service, Property 18: Required Fields Validation - Service Name")
    void requiredFieldsValidation_ServiceName(
            @ForAll("validEnvironmentIds") UUID environmentId,
            @ForAll("validZoneIds") String zoneId) {
        
        // Create mocks
        JobRepository jobRepository = mock(JobRepository.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobService jobService = new JobService(jobRepository, stateValidator);
        
        // When/Then: Creating job with null serviceName throws exception
        assertThatThrownBy(() -> 
            jobService.createJob(environmentId, null, zoneId, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("serviceName");
        
        // When/Then: Creating job with blank serviceName throws exception
        assertThatThrownBy(() -> 
            jobService.createJob(environmentId, "  ", zoneId, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("serviceName");
    }
    
    @Property(tries = 50)
    @Label("Feature: job-service, Property 18: Required Fields Validation - Zone ID")
    void requiredFieldsValidation_ZoneId(
            @ForAll("validEnvironmentIds") UUID environmentId,
            @ForAll("validServiceNames") String serviceName) {
        
        // Create mocks
        JobRepository jobRepository = mock(JobRepository.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobService jobService = new JobService(jobRepository, stateValidator);
        
        // When/Then: Creating job with null zoneId throws exception
        assertThatThrownBy(() -> 
            jobService.createJob(environmentId, serviceName, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zoneId");
        
        // When/Then: Creating job with blank zoneId throws exception
        assertThatThrownBy(() -> 
            jobService.createJob(environmentId, serviceName, "  ", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zoneId");
    }
    
    /**
     * Property: Job IDs are unique
     * 
     * For any two jobs created, they should have different UUIDs.
     */
    @Property(tries = 50)
    @Label("Feature: job-service, Property: Job IDs are unique")
    void jobIdsAreUnique(
            @ForAll("validEnvironmentIds") UUID environmentId,
            @ForAll("validServiceNames") String serviceName,
            @ForAll("validZoneIds") String zoneId) {
        
        // Create mocks
        JobRepository jobRepository = mock(JobRepository.class);
        StateTransitionValidator stateValidator = mock(StateTransitionValidator.class);
        JobService jobService = new JobService(jobRepository, stateValidator);
        
        // Mock repository save to return entity with ID and timestamp
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            return job;
        });
        
        // When: Creating two jobs with the same parameters
        Job job1 = jobService.createJob(environmentId, serviceName, zoneId, null, null);
        Job job2 = jobService.createJob(environmentId, serviceName, zoneId, null, null);
        
        // Then: Jobs have different IDs
        assertThat(job1.getId()).isNotEqualTo(job2.getId());
    }
    
    // ========== Arbitraries (Data Generators) ==========
    
    @Provide
    Arbitrary<UUID> validEnvironmentIds() {
        return Arbitraries.create(UUID::randomUUID);
    }
    
    @Provide
    Arbitrary<String> validServiceNames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(50)
            .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1) + "Service");
    }
    
    @Provide
    Arbitrary<String> validZoneIds() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(20)
            .map(s -> "zone-" + s);
    }
    
    @Provide
    Arbitrary<String> optionalContextIds() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(20)
            .map(s -> "context-" + s)
            .injectNull(0.3); // 30% chance of null
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
}
