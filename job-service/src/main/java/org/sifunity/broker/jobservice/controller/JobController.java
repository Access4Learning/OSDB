// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.common.dto.SifCollection;
import org.sifunity.broker.jobservice.domain.Job;
import org.sifunity.broker.jobservice.dto.CreateJobRequest;
import org.sifunity.broker.jobservice.dto.JobDTO;
import org.sifunity.broker.jobservice.dto.UpdateJobStateRequest;
import org.sifunity.broker.jobservice.mapper.JobMapper;
import org.sifunity.broker.jobservice.service.JobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for job management operations.
 */
@Tag(name = "SIF API")
@RestController
@RequestMapping("/{domainId}/{solutionId}/jobs")
@Validated
public class JobController {
    
    private final JobService jobService;
    private final JobMapper jobMapper;
    
    public JobController(JobService jobService, JobMapper jobMapper) {
        this.jobService = jobService;
        this.jobMapper = jobMapper;
    }
    
    /**
     * Creates a new job.
     *
     * @param request the job creation request
     * @return the created job
     */
    @PostMapping
    public ResponseEntity<JobDTO> createJob(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody CreateJobRequest request) {
        Job job = jobService.createJob(
            request.getEnvironmentId(),
            request.getServiceName(),
            request.getZoneId(),
            request.getContextId(),
            request.getMetadata()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(jobMapper.toDto(job));
    }
    
    /**
     * Retrieves a job by ID.
     *
     * @param jobId the job ID
     * @return the job
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobDTO> getJob(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID jobId) {
        Job job = jobService.getJob(jobId);
        return ResponseEntity.ok(jobMapper.toDto(job));
    }
    
    /**
     * Lists jobs with optional filters.
     *
     * @param environmentId the environment ID (required)
     * @param state the job state filter (optional)
     * @param serviceName the service name filter (optional)
     * @return list of jobs
     */
    @GetMapping
    public ResponseEntity<List<JobDTO>> listJobs(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam UUID environmentId,
            @RequestParam(required = false) JobState state,
            @RequestParam(required = false) String serviceName) {
        List<Job> jobs = jobService.listJobs(environmentId, state, serviceName);
        List<JobDTO> jobDTOs = jobs.stream()
            .map(jobMapper::toDto)
            .collect(Collectors.toList());
        return ResponseEntity.ok(jobDTOs);
    }
    
    /**
     * Updates a job's state.
     *
     * @param jobId the job ID
     * @param request the state update request
     * @return the updated job
     */
    @PutMapping("/{jobId}/state")
    public ResponseEntity<JobDTO> updateJobState(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID jobId,
            @Valid @RequestBody UpdateJobStateRequest request) {
        Job job = jobService.updateJobState(jobId, request.getState(), request.getMetadata());
        return ResponseEntity.ok(jobMapper.toDto(job));
    }
    
    /**
     * Deletes a job.
     *
     * @param jobId the job ID
     * @return no content
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID jobId) {
        jobService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }
}
