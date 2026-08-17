// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sifunity.broker.common.dto.SifCollection;
import org.sifunity.broker.jobservice.domain.JobPhase;
import org.sifunity.broker.jobservice.dto.CreatePhaseRequest;
import org.sifunity.broker.jobservice.dto.JobPhaseDTO;
import org.sifunity.broker.jobservice.dto.UpdatePhaseStateRequest;
import org.sifunity.broker.jobservice.mapper.JobPhaseMapper;
import org.sifunity.broker.jobservice.service.JobPhaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for job phase management operations.
 */
@Tag(name = "SIF API")
@RestController
@RequestMapping("/{domainId}/{solutionId}/jobs/{jobId}/phases")
@Validated
public class JobPhaseController {
    
    private final JobPhaseService phaseService;
    private final JobPhaseMapper phaseMapper;
    
    public JobPhaseController(JobPhaseService phaseService, JobPhaseMapper phaseMapper) {
        this.phaseService = phaseService;
        this.phaseMapper = phaseMapper;
    }
    
    /**
     * Creates a new phase for a job.
     *
     * @param jobId the job ID
     * @param request the phase creation request
     * @return the created phase
     */
    @PostMapping
    public ResponseEntity<JobPhaseDTO> createPhase(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID jobId,
            @Valid @RequestBody CreatePhaseRequest request) {
        JobPhase phase = phaseService.createPhase(
            jobId,
            request.getPhaseName(),
            request.getState(),
            request.getMetadata()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(phaseMapper.toDto(phase));
    }
    
    /**
     * Retrieves all phases for a job.
     *
     * @param jobId the job ID
     * @return list of phases
     */
    @GetMapping
    public ResponseEntity<List<JobPhaseDTO>> getPhases(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID jobId) {
        List<JobPhase> phases = phaseService.getPhasesByJobId(jobId);
        List<JobPhaseDTO> phaseDTOs = phases.stream()
            .map(phaseMapper::toDto)
            .collect(Collectors.toList());
        return ResponseEntity.ok(phaseDTOs);
    }
    
    /**
     * Updates a phase's state.
     *
     * @param jobId the job ID
     * @param phaseId the phase ID
     * @param request the state update request
     * @return the updated phase
     */
    @PutMapping("/{phaseId}/state")
    public ResponseEntity<JobPhaseDTO> updatePhaseState(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID jobId,
            @PathVariable UUID phaseId,
            @Valid @RequestBody UpdatePhaseStateRequest request) {
        JobPhase phase = phaseService.updatePhaseState(
            phaseId,
            request.getState(),
            request.getMetadata()
        );
        return ResponseEntity.ok(phaseMapper.toDto(phase));
    }
}
