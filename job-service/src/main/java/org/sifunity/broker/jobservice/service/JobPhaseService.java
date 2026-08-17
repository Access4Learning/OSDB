// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.service;

import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.domain.JobPhase;
import org.sifunity.broker.jobservice.domain.Job;
import org.sifunity.broker.jobservice.exception.DuplicatePhaseException;
import org.sifunity.broker.jobservice.exception.InvalidStateTransitionException;
import org.sifunity.broker.jobservice.exception.PhaseNotFoundException;
import org.sifunity.broker.jobservice.repository.JobPhaseRepository;
import org.sifunity.broker.jobservice.util.StateTransitionValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for JobPhase management operations.
 * 
 * <p>This service implements the business logic for job phase lifecycle management including:
 * <ul>
 *   <li>Phase creation with validation and duplicate checking</li>
 *   <li>Phase retrieval and listing ordered by creation time</li>
 *   <li>Phase state transitions with validation</li>
 *   <li>Automatic job state updates when phases fail</li>
 * </ul>
 * 
 * <p>All state transitions are validated using {@link StateTransitionValidator}
 * to ensure phases follow the defined lifecycle rules.</p>
 * 
 * <p>The service uses Spring's {@code @Transactional} annotation to ensure
 * database operations are atomic and consistent.</p>
 */
@Service
@Transactional
public class JobPhaseService {
    
    private final JobPhaseRepository phaseRepository;
    private final JobService jobService;
    private final StateTransitionValidator stateValidator;
    
    /**
     * Constructs a new JobPhaseService with required dependencies.
     * 
     * @param phaseRepository repository for phase data access
     * @param jobService service for job operations
     * @param stateValidator validator for state transitions
     */
    public JobPhaseService(JobPhaseRepository phaseRepository, 
                          JobService jobService,
                          StateTransitionValidator stateValidator) {
        this.phaseRepository = phaseRepository;
        this.jobService = jobService;
        this.stateValidator = stateValidator;
    }
    
    /**
     * Creates a new phase for a job with the specified parameters.
     * 
     * <p>The phase is initialized with:
     * <ul>
     *   <li>A unique UUID identifier</li>
     *   <li>createdAt and updatedAt set to current time</li>
     *   <li>Association with the parent job</li>
     * </ul>
     * 
     * <p>Validation performed:
     * <ul>
     *   <li>Job must exist (throws JobNotFoundException if not)</li>
     *   <li>Phase name must be unique within the job (throws DuplicatePhaseException if duplicate)</li>
     *   <li>Phase name must not be blank and max 100 characters</li>
     *   <li>State must be a valid JobState value</li>
     * </ul>
     * 
     * <p>Side effects:
     * <ul>
     *   <li>Updates the job's currentPhase field to the new phase name</li>
     *   <li>Updates the job's updatedAt timestamp</li>
     * </ul>
     * 
     * @param jobId the ID of the job this phase belongs to (required)
     * @param phaseName the name of the phase (required, max 100 chars, unique within job)
     * @param state the initial state of the phase (required)
     * @param metadata optional custom metadata as JSON string (max 10KB)
     * @return the created phase entity
     * @throws org.sifunity.broker.jobservice.exception.JobNotFoundException if the job does not exist
     * @throws DuplicatePhaseException if a phase with this name already exists for the job
     * @throws IllegalArgumentException if required parameters are null or invalid
     */
    public JobPhase createPhase(UUID jobId, String phaseName, 
                               JobState state, String metadata) {
        // Verify job exists (throws JobNotFoundException if not)
        jobService.getJob(jobId);
        
        // Validate input parameters
        if (phaseName == null || phaseName.isBlank()) {
            throw new IllegalArgumentException("phaseName cannot be null or blank");
        }
        if (phaseName.length() > 100) {
            throw new IllegalArgumentException("phaseName cannot exceed 100 characters");
        }
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        
        // Check for duplicate phase name
        if (phaseRepository.existsByJobIdAndPhaseName(jobId, phaseName)) {
            throw new DuplicatePhaseException(
                "Phase already exists with name '" + phaseName + "' for job: " + jobId);
        }
        
        // Create phase entity
        JobPhase phase = new JobPhase();
        phase.setId(UUID.randomUUID());
        phase.setJobId(jobId);
        phase.setPhaseName(phaseName);
        phase.setState(state);
        
        Instant now = Instant.now();
        phase.setCreatedAt(now);
        phase.setUpdatedAt(now);
        phase.setMetadata(metadata);
        
        JobPhase savedPhase = phaseRepository.save(phase);
        
        // Update job's current phase
        jobService.updateCurrentPhase(jobId, phaseName);
        
        return savedPhase;
    }
    
    /**
     * Retrieves all phases for a given job, ordered by creation time ascending.
     * 
     * <p>This method returns phases in chronological order (oldest first) to show
     * the progression through the job's lifecycle.</p>
     * 
     * <p>If the job has no phases, an empty list is returned.</p>
     * 
     * @param jobId the unique identifier of the job
     * @return list of phases ordered by createdAt ascending (oldest first)
     * @throws org.sifunity.broker.jobservice.exception.JobNotFoundException if the job does not exist
     */
    public List<JobPhase> getPhasesByJobId(UUID jobId) {
        // Verify job exists (throws JobNotFoundException if not)
        jobService.getJob(jobId);
        
        return phaseRepository.findByJobIdOrderByCreatedAtAsc(jobId);
    }
    
    /**
     * Updates the state of a phase.
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
     * <p>Side effects:
     * <ul>
     *   <li>If the new state is FAILED and the job is in INPROGRESS state,
     *       the job is automatically transitioned to FAILED state</li>
     * </ul>
     * 
     * @param phaseId the unique identifier of the phase
     * @param newState the desired new state
     * @param metadata optional metadata to update (if null, existing metadata is preserved)
     * @return the updated phase entity
     * @throws PhaseNotFoundException if no phase exists with the given ID
     * @throws InvalidStateTransitionException if the state transition is invalid
     */
    public JobPhase updatePhaseState(UUID phaseId, JobState newState, String metadata) {
        JobPhase phase = phaseRepository.findById(phaseId)
            .orElseThrow(() -> new PhaseNotFoundException(
                "Phase not found: " + phaseId));
        
        // Validate state transition
        if (!stateValidator.isValidTransition(phase.getState(), newState)) {
            throw new InvalidStateTransitionException(
                "Invalid phase transition from " + phase.getState() + 
                " to " + newState);
        }
        
        // Update phase
        phase.setState(newState);
        phase.setUpdatedAt(Instant.now());
        if (metadata != null) {
            phase.setMetadata(metadata);
        }
        
        JobPhase savedPhase = phaseRepository.save(phase);
        
        // If phase failed, fail the job if it's in progress
        if (newState == JobState.FAILED) {
            Job job = jobService.getJob(phase.getJobId());
            if (job.getState() == JobState.INPROGRESS) {
                jobService.updateJobState(job.getId(), JobState.FAILED, null);
            }
        }
        
        return savedPhase;
    }
}
