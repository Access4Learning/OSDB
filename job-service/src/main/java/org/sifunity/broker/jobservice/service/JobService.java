// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.service;

import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.domain.Job;
import org.sifunity.broker.jobservice.exception.InvalidStateTransitionException;
import org.sifunity.broker.jobservice.exception.JobInProgressException;
import org.sifunity.broker.jobservice.exception.JobNotFoundException;
import org.sifunity.broker.jobservice.repository.JobRepository;
import org.sifunity.broker.jobservice.util.StateTransitionValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for Job management operations.
 * 
 * <p>This service implements the business logic for job lifecycle management including:
 * <ul>
 *   <li>Job creation with validation</li>
 *   <li>Job retrieval and listing with filtering</li>
 *   <li>Job state transitions with validation</li>
 *   <li>Job deletion with constraints</li>
 *   <li>Current phase tracking</li>
 * </ul>
 * 
 * <p>All state transitions are validated using {@link StateTransitionValidator}
 * to ensure jobs follow the defined lifecycle rules.</p>
 * 
 * <p>The service uses Spring's {@code @Transactional} annotation to ensure
 * database operations are atomic and consistent.</p>
 */
@Service
@Transactional
public class JobService {
    
    private final JobRepository jobRepository;
    private final StateTransitionValidator stateValidator;
    
    /**
     * Constructs a new JobService with required dependencies.
     * 
     * @param jobRepository repository for job data access
     * @param stateValidator validator for state transitions
     */
    public JobService(JobRepository jobRepository, StateTransitionValidator stateValidator) {
        this.jobRepository = jobRepository;
        this.stateValidator = stateValidator;
    }
    
    /**
     * Creates a new job with the specified parameters.
     * 
     * <p>The job is initialized with:
     * <ul>
     *   <li>A unique UUID identifier</li>
     *   <li>State set to NOTSTARTED</li>
     *   <li>createdAt and updatedAt set to current time</li>
     *   <li>Version set to 0 for optimistic locking</li>
     * </ul>
     * 
     * <p>Input validation is performed on:
     * <ul>
     *   <li>environmentId: must be a valid UUID (not null)</li>
     *   <li>serviceName: must not be blank</li>
     *   <li>zoneId: must not be blank</li>
     * </ul>
     * 
     * @param environmentId the environment that owns this job (required)
     * @param serviceName the name of the functional service (required)
     * @param zoneId the zone ID for routing (required)
     * @param contextId optional context ID for additional routing information
     * @param metadata optional custom metadata as JSON string (max 10KB)
     * @return the created job entity
     * @throws IllegalArgumentException if required parameters are null or blank
     */
    public Job createJob(UUID environmentId, String serviceName, 
                        String zoneId, String contextId, String metadata) {
        // Validate required inputs
        if (environmentId == null) {
            throw new IllegalArgumentException("environmentId cannot be null");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName cannot be null or blank");
        }
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("zoneId cannot be null or blank");
        }
        
        // Create job entity
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEnvironmentId(environmentId);
        job.setServiceName(serviceName);
        job.setZoneId(zoneId);
        job.setContextId(contextId);
        job.setState(JobState.NOTSTARTED);
        
        Instant now = Instant.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setMetadata(metadata);
        
        return jobRepository.save(job);
    }
    
    /**
     * Retrieves a job by its ID.
     * 
     * @param jobId the unique identifier of the job
     * @return the job entity
     * @throws JobNotFoundException if no job exists with the given ID
     */
    public Job getJob(UUID jobId) {
        return jobRepository.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException(
                "Job not found: " + jobId));
    }
    
    /**
     * Lists jobs for a given environment with optional filtering.
     * 
     * <p>Results are always ordered by creation date descending (newest first).</p>
     * 
     * <p>Filtering options:
     * <ul>
     *   <li>No filters: returns all jobs for the environment</li>
     *   <li>state only: returns jobs in the specified state</li>
     *   <li>serviceName only: returns jobs for the specified service</li>
     *   <li>Both filters: returns jobs matching both criteria</li>
     * </ul>
     * 
     * @param environmentId the environment ID to filter by (required)
     * @param state optional state filter (NOTSTARTED, INPROGRESS, COMPLETED, FAILED)
     * @param serviceName optional service name filter
     * @return list of jobs matching the criteria, ordered by createdAt descending
     */
    public List<Job> listJobs(UUID environmentId, JobState state, String serviceName) {
        if (state != null && serviceName != null) {
            return jobRepository.findByEnvironmentIdAndStateAndServiceName(
                environmentId, state, serviceName);
        } else if (state != null) {
            return jobRepository.findByEnvironmentIdAndState(
                environmentId, state);
        } else if (serviceName != null) {
            return jobRepository.findByEnvironmentIdAndServiceName(
                environmentId, serviceName);
        } else {
            return jobRepository.findByEnvironmentIdOrderByCreatedAtDesc(
                environmentId);
        }
    }
    
    /**
     * Updates the state of a job.
     * 
     * <p>State transitions are validated using {@link StateTransitionValidator}.
     * Valid transitions:
     * <ul>
     *   <li>NOTSTARTED → INPROGRESS</li>
     *   <li>INPROGRESS → COMPLETED</li>
     *   <li>INPROGRESS → FAILED</li>
     * </ul>
     * 
     * <p>Terminal states (COMPLETED and FAILED) cannot transition to any other state.</p>
     * 
     * <p>The updatedAt timestamp is automatically updated when the state changes.</p>
     * 
     * @param jobId the unique identifier of the job
     * @param newState the desired new state
     * @param metadata optional metadata to update (if null, existing metadata is preserved)
     * @return the updated job entity
     * @throws JobNotFoundException if no job exists with the given ID
     * @throws InvalidStateTransitionException if the state transition is invalid
     */
    public Job updateJobState(UUID jobId, JobState newState, String metadata) {
        Job job = getJob(jobId);
        
        // Validate state transition
        if (!stateValidator.isValidTransition(job.getState(), newState)) {
            throw new InvalidStateTransitionException(
                "Invalid transition from " + job.getState() + 
                " to " + newState);
        }
        
        // Update job
        job.setState(newState);
        job.setUpdatedAt(Instant.now());
        if (metadata != null) {
            job.setMetadata(metadata);
        }
        
        return jobRepository.save(job);
    }
    
    /**
     * Deletes a job and all its associated phases.
     * 
     * <p>Deletion constraints:
     * <ul>
     *   <li>Jobs in INPROGRESS state cannot be deleted (throws JobInProgressException)</li>
     *   <li>Jobs in NOTSTARTED, COMPLETED, or FAILED state can be deleted</li>
     *   <li>All associated phases are automatically deleted via cascade delete</li>
     * </ul>
     * 
     * @param jobId the unique identifier of the job to delete
     * @throws JobNotFoundException if no job exists with the given ID
     * @throws JobInProgressException if the job is in INPROGRESS state
     */
    public void deleteJob(UUID jobId) {
        Job job = getJob(jobId);
        
        // Prevent deletion of in-progress jobs
        if (job.getState() == JobState.INPROGRESS) {
            throw new JobInProgressException(
                "Cannot delete job in INPROGRESS state: " + jobId);
        }
        
        jobRepository.delete(job);
    }
    
    /**
     * Updates the current phase field of a job.
     * 
     * <p>This method is called by {@link JobPhaseService} when a new phase
     * is created or when a phase becomes active. It tracks which phase
     * is currently being executed.</p>
     * 
     * <p>The updatedAt timestamp is automatically updated.</p>
     * 
     * @param jobId the unique identifier of the job
     * @param phaseName the name of the current phase
     * @throws JobNotFoundException if no job exists with the given ID
     */
    public void updateCurrentPhase(UUID jobId, String phaseName) {
        Job job = getJob(jobId);
        job.setCurrentPhase(phaseName);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
    }
}
