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
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.dto.DomainUpdateRequest;
import org.sifunity.broker.environment.service.DomainService;

import java.util.List;

/**
 * REST controller for domain management operations.
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/domains")
public class DomainController {
    
    private static final Logger log = LoggerFactory.getLogger(DomainController.class);
    
    private final DomainService domainService;
    
    public DomainController(DomainService domainService) {
        this.domainService = domainService;
    }
    
    /**
     * Creates a new domain with the specified domain ID and optional description.
     * 
     * Returns HTTP 201 Created on successful domain creation.
     * Returns HTTP 409 Conflict if domain ID already exists.
     * Returns HTTP 400 Bad Request if validation fails.
     * 
     * @param request the domain creation request
     * @return ResponseEntity containing the created DomainDTO
     */
    @PostMapping
    public ResponseEntity<DomainDTO> createDomain(@Valid @RequestBody DomainCreateRequest request) {
        log.info("Received request to create domain with ID: {}", request.domainId());
        
        DomainDTO createdDomain = domainService.createDomain(request);
        
        log.info("Successfully created domain with ID: {} (UUID: {})", 
            createdDomain.domainId(), createdDomain.id());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(createdDomain);
    }
    
    /**
     * Retrieves a domain by its domain ID.
     * 
     * Returns HTTP 200 OK with the domain information.
     * Returns HTTP 404 Not Found if the domain does not exist.
     * 
     * @param domainId the domain ID to retrieve
     * @return ResponseEntity containing the DomainDTO
     */
    @GetMapping("/{domainId}")
    public ResponseEntity<DomainDTO> getDomain(@PathVariable String domainId) {
        log.debug("Received request to retrieve domain with ID: {}", domainId);
        
        DomainDTO domain = domainService.getDomain(domainId);
        
        return ResponseEntity.ok(domain);
    }
    
    /**
     * Retrieves all domains in the system.
     * 
     * Returns HTTP 200 OK with the list of domains.
     * 
     * @return ResponseEntity containing a list of DomainDTO objects
     */
    @GetMapping
    public ResponseEntity<SifCollection> listDomains() {
        log.debug("Received request to list all domains");
        
        List<DomainDTO> domains = domainService.listDomains();
        
        log.debug("Returning {} domains", domains.size());
        
        return ResponseEntity.ok(SifCollection.of("domain", domains));
    }
    
    /**
     * Updates a domain's description.
     * 
     * Returns HTTP 200 OK with the updated domain information.
     * Returns HTTP 404 Not Found if the domain does not exist.
     * 
     * @param domainId the domain ID to update
     * @param request the update request containing new description
     * @return ResponseEntity containing the updated DomainDTO
     */
    @PutMapping("/{domainId}")
    public ResponseEntity<DomainDTO> updateDomain(
        @PathVariable String domainId,
        @Valid @RequestBody DomainUpdateRequest request
    ) {
        log.info("Received request to update domain with ID: {}", domainId);
        
        DomainDTO updatedDomain = domainService.updateDomain(domainId, request);
        
        log.info("Successfully updated domain with ID: {}", domainId);
        
        return ResponseEntity.ok(updatedDomain);
    }
    
    /**
     * Deletes a domain by its domain ID.
     * Cascade deletes all solutions, zones, contexts, and environments within the domain.
     * 
     * Returns HTTP 204 No Content on successful deletion.
     * Returns HTTP 404 Not Found if the domain does not exist.
     * 
     * @param domainId the domain ID to delete
     * @return ResponseEntity with no content
     */
    @DeleteMapping("/{domainId}")
    public ResponseEntity<Void> deleteDomain(@PathVariable String domainId) {
        log.info("Received request to delete domain with ID: {}", domainId);
        
        domainService.deleteDomain(domainId);
        
        log.info("Successfully deleted domain with ID: {}", domainId);
        
        return ResponseEntity.noContent().build();
    }
}
