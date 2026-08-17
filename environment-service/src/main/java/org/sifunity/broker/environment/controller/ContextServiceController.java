// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.ContextServiceCreateRequest;
import org.sifunity.broker.environment.dto.ContextServiceDTO;
import org.sifunity.broker.environment.exception.ContextNotFoundException;
import org.sifunity.broker.environment.exception.ZoneNotFoundException;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.service.ContextServiceService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for context service management operations.
 * 
 * <p>This controller handles HTTP requests for context service lifecycle operations:
 * <ul>
 *   <li>Context service creation (POST /context-services)</li>
 *   <li>Context service listing by context (GET /context-services?contextUuid=...)</li>
 *   <li>Context service listing by zone (GET /context-services?zoneId=...)</li>
 *   <li>Context service deletion (DELETE /context-services/{id})</li>
 * </ul>
 * 
 * <p>All endpoints require authentication through the API Gateway.
 * Context services define which named services are available within a given context,
 * serving as the normalized representation of "service X exists in context Y".</p>
 * 
 * <p>Request validation is performed using Bean Validation annotations on DTOs.
 * Business logic validation and error handling is delegated to the service layer.</p>
 * 
 * @see ContextServiceService
 * @see ContextServiceCreateRequest
 * @see ContextServiceDTO
 */
@Tag(name = "SIF API", description = "SIF 3.7 specification endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/context-services")
public class ContextServiceController {

    private static final Logger log = LoggerFactory.getLogger(ContextServiceController.class);

    private final ContextServiceService contextServiceService;
    private final ZoneRepository zoneRepository;
    private final SolutionRepository solutionRepository;
    private final DomainRepository domainRepository;
    private final ContextRepository contextRepository;

    public ContextServiceController(ContextServiceService contextServiceService,
                                    ZoneRepository zoneRepository,
                                    SolutionRepository solutionRepository,
                                    DomainRepository domainRepository,
                                    ContextRepository contextRepository) {
        this.contextServiceService = contextServiceService;
        this.zoneRepository = zoneRepository;
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
        this.contextRepository = contextRepository;
    }

    /**
     * Creates a new context service.
     * 
     * <p>Creates a context service record that defines a named service within a specific
     * context. The caller provides zoneId + contextId strings; the service resolves the
     * internal context UUID from the domain/solution path parameters.</p>
     * 
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param request the context service creation request
     * @return ResponseEntity containing the created context service details
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>201 Created - Context service successfully created</li>
     *   <li>400 Bad Request - Invalid request data (validation errors or invalid service type)</li>
     *   <li>404 Not Found - Zone or context does not exist</li>
     *   <li>409 Conflict - Context service with same (context, serviceName) already exists</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 5.1, 5.2, 5.7</p>
     */
    @PostMapping
    public ResponseEntity<ContextServiceDTO> createContextService(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody ContextServiceCreateRequest request) {

        log.info("Creating context service: zone='{}', context='{}', serviceName='{}', serviceType='{}'",
                request.zoneId(), request.contextId(), request.serviceName(), request.serviceType());

        // Resolve context UUID from string identifiers
        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(d -> d.getId())
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainId));

        UUID solutionUuid = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .map(s -> s.getId())
                .orElseThrow(() -> new IllegalArgumentException("Solution not found: " + solutionId));

        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, request.zoneId())
                .orElseThrow(() -> new ZoneNotFoundException(request.zoneId()));

        Context context = contextRepository.findByZoneUuidAndContextId(zone.getId(), request.contextId())
                .orElseThrow(() -> new ContextNotFoundException(request.contextId()));

        ContextServiceDTO created = contextServiceService.createContextService(
                context.getId(), request.serviceName(), request.serviceType());

        log.info("Context service created successfully: id={}, serviceName='{}'",
                created.id(), created.serviceName());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists context services by zone and context identifiers.
     * 
     * <p>Resolves the zoneId + contextId strings to a context UUID internally,
     * then returns all context service records for that context.</p>
     * 
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param zoneId the zone business identifier
     * @param contextId the context business identifier (within the zone)
     * @return ResponseEntity containing a list of context service DTOs
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - Context services returned (may be empty list)</li>
     *   <li>404 Not Found - Zone or context does not exist</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 5.3</p>
     */
    @GetMapping(params = {"zoneId", "contextId"})
    public ResponseEntity<List<ContextServiceDTO>> listByContext(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam String zoneId,
            @RequestParam String contextId) {

        log.debug("Listing context services for zone: {}, context: {}", zoneId, contextId);

        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(d -> d.getId())
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainId));

        UUID solutionUuid = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .map(s -> s.getId())
                .orElseThrow(() -> new IllegalArgumentException("Solution not found: " + solutionId));

        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId)
                .orElseThrow(() -> new ZoneNotFoundException(zoneId));

        Context context = contextRepository.findByZoneUuidAndContextId(zone.getId(), contextId)
                .orElseThrow(() -> new ContextNotFoundException(contextId));

        List<ContextServiceDTO> results = contextServiceService.listByContext(context.getId());

        log.debug("Returning {} context services for zone: {}, context: {}", results.size(), zoneId, contextId);

        return ResponseEntity.ok(results);
    }

    /**
     * Lists context services by zone ID.
     * 
     * <p>Resolves the zoneId string to a zone UUID, then returns all context service
     * records for all contexts belonging to that zone.</p>
     * 
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param zoneId the zone ID string to resolve and filter by
     * @return ResponseEntity containing a list of context service DTOs
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - Context services returned (may be empty list)</li>
     *   <li>404 Not Found - Zone with given zoneId does not exist</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 5.4</p>
     */
    @GetMapping(params = "zoneId")
    public ResponseEntity<List<ContextServiceDTO>> listByZone(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam String zoneId) {

        log.debug("Listing context services for zone: {}, solution: {}", zoneId, solutionId);

        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(d -> d.getId())
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainId));

        UUID solutionUuid = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .map(s -> s.getId())
                .orElseThrow(() -> new IllegalArgumentException("Solution not found: " + solutionId));

        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId)
                .orElseThrow(() -> new ZoneNotFoundException(zoneId));

        List<ContextServiceDTO> results = contextServiceService.listByZone(zone.getId());

        log.debug("Returning {} context services for zone: {}", results.size(), zoneId);

        return ResponseEntity.ok(results);
    }

    /**
     * Deletes a context service by ID.
     * 
     * <p>Removes the specified context service record. Dependent acl_entries and
     * provider_registry rows are cascade-deleted via database FK ON DELETE CASCADE.</p>
     * 
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param id the UUID of the context service to delete
     * @return ResponseEntity with no content
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>204 No Content - Context service successfully deleted</li>
     *   <li>404 Not Found - Context service does not exist</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 5.5, 5.6</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContextService(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id) {

        log.info("Deleting context service: id={}", id);

        contextServiceService.deleteContextService(id);

        log.info("Context service deleted successfully: id={}", id);

        return ResponseEntity.noContent().build();
    }
}
