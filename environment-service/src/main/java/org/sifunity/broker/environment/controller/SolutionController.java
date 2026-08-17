// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.common.dto.SifCollection;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.dto.SolutionUpdateRequest;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.List;

/**
 * REST controller for solution management operations.
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/{domainId}/solutions")
public class SolutionController {
    
    private static final Logger log = LoggerFactory.getLogger(SolutionController.class);
    
    private final SolutionService solutionService;
    
    public SolutionController(SolutionService solutionService) {
        this.solutionService = solutionService;
    }
    
    /**
     * Creates a new solution with the specified domain ID, solution ID, and optional description.
     * 
     * Returns HTTP 201 Created on successful solution creation.
     * Returns HTTP 404 Not Found if the parent domain does not exist.
     * Returns HTTP 409 Conflict if solution ID already exists in the domain.
     * Returns HTTP 400 Bad Request if validation fails.
     * 
     * @param request the solution creation request
     * @return ResponseEntity containing the created SolutionDTO
     */
    @PostMapping
    public ResponseEntity<SolutionDTO> createSolution(
            @PathVariable String domainId,
            @Valid @RequestBody SolutionCreateRequest request) {
        log.info("Received request to create solution with ID: {} in domain: {}", 
            request.solutionId(), domainId);
        
        // Use domainId from URL path, overriding any value in the request body
        SolutionCreateRequest effectiveRequest = new SolutionCreateRequest(domainId, request.solutionId(), request.description());
        SolutionDTO createdSolution = solutionService.createSolution(effectiveRequest);
        
        log.info("Successfully created solution with ID: {} in domain: {} (UUID: {})", 
            createdSolution.solutionId(), createdSolution.domainId(), createdSolution.id());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSolution);
    }
    
    /**
     * Retrieves a solution by its solution ID.
     * 
     * Returns HTTP 200 OK with the solution information.
     * Returns HTTP 404 Not Found if the solution does not exist.
     * 
     * @param solutionId the solution ID to retrieve
     * @return ResponseEntity containing the SolutionDTO
     */
    @GetMapping("/{solutionId}")
    public ResponseEntity<SolutionDTO> getSolution(
            @PathVariable String domainId,
            @PathVariable String solutionId) {
        log.debug("Received request to retrieve solution with ID: {}", solutionId);
        
        SolutionDTO solution = solutionService.getSolution(domainId, solutionId);
        
        return ResponseEntity.ok(solution);
    }
    
    /**
     * Retrieves all solutions in the system.
     * Optionally filters by domain ID if provided as a query parameter.
     * 
     * Returns HTTP 200 OK with the list of solutions.
     * 
     * @param domainId optional domain ID to filter by
     * @return ResponseEntity containing a list of SolutionDTO objects
     */
    @GetMapping
    public ResponseEntity<SifCollection> listSolutions(
        @PathVariable String domainId
    ) {
        log.debug("Received request to list solutions for domain: {}", domainId);
        
        List<SolutionDTO> solutions = solutionService.listSolutionsByDomain(domainId);
        
        log.debug("Returning {} solutions", solutions.size());
        
        return ResponseEntity.ok(SifCollection.of("solution", solutions));
    }
    
    /**
     * Updates a solution's description.
     * 
     * Returns HTTP 200 OK with the updated solution information.
     * Returns HTTP 404 Not Found if the solution does not exist.
     * 
     * @param solutionId the solution ID to update
     * @param request the update request containing new description
     * @return ResponseEntity containing the updated SolutionDTO
     */
    @PutMapping("/{solutionId}")
    public ResponseEntity<SolutionDTO> updateSolution(
        @PathVariable String domainId,
        @PathVariable String solutionId,
        @Valid @RequestBody SolutionUpdateRequest request
    ) {
        log.info("Received request to update solution with ID: {}", solutionId);
        
        SolutionDTO updatedSolution = solutionService.updateSolution(domainId, solutionId, request);
        
        log.info("Successfully updated solution with ID: {}", solutionId);
        
        return ResponseEntity.ok(updatedSolution);
    }
    
    /**
     * Deletes a solution by its solution ID.
     * Cascade deletes all zones, contexts, and environments within the solution.
     * 
     * Returns HTTP 204 No Content on successful deletion.
     * Returns HTTP 404 Not Found if the solution does not exist.
     * 
     * @param solutionId the solution ID to delete
     * @return ResponseEntity with no content
     */
    @DeleteMapping("/{solutionId}")
    public ResponseEntity<Void> deleteSolution(
            @PathVariable String domainId,
            @PathVariable String solutionId) {
        log.info("Received request to delete solution with ID: {}", solutionId);
        
        solutionService.deleteSolution(domainId, solutionId);
        
        log.info("Successfully deleted solution with ID: {}", solutionId);
        
        return ResponseEntity.noContent().build();
    }
}
