// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.SolutionService;
import org.sifunity.broker.environment.dto.SolutionServiceCreateRequest;
import org.sifunity.broker.environment.dto.SolutionServiceDTO;
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.SolutionServiceRepository;
import org.sifunity.broker.environment.service.XQueryTemplateService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for solution service catalog management.
 * 
 * <p>Manages the service catalog entries within a solution. Each entry defines
 * a service name + type combination that is available within the solution.
 * Administrators use this catalog when provisioning zones, creating ACL entries,
 * or registering providers.</p>
 * 
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /{domainId}/{solutionId}/solution-services - List all services for a solution</li>
 *   <li>POST /{domainId}/{solutionId}/solution-services - Create a new service entry</li>
 *   <li>DELETE /{domainId}/{solutionId}/solution-services/{id} - Delete a service entry</li>
 * </ul>
 */
@Tag(name = "SIF API", description = "SIF 3.7 specification endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/solution-services")
public class SolutionServiceController {

    private static final Logger log = LoggerFactory.getLogger(SolutionServiceController.class);

    private static final Set<String> VALID_SERVICE_TYPES = Set.of(
            "UTILITY", "OBJECT", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE"
    );

    /** Service type whose rows are paired with a Named Query template of the same name. */
    private static final String XQUERYTEMPLATE_SERVICE_TYPE = "XQUERYTEMPLATE";

    private final SolutionServiceRepository solutionServiceRepository;
    private final SolutionRepository solutionRepository;
    private final DomainRepository domainRepository;
    private final XQueryTemplateService xQueryTemplateService;

    public SolutionServiceController(SolutionServiceRepository solutionServiceRepository,
                                     SolutionRepository solutionRepository,
                                     DomainRepository domainRepository,
                                     XQueryTemplateService xQueryTemplateService) {
        this.solutionServiceRepository = solutionServiceRepository;
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
        this.xQueryTemplateService = xQueryTemplateService;
    }

    /**
     * Resolves a solution scoped to its owning domain. {@code solutionId} is unique only
     * within a domain, so the lookup is scoped by {@code domainId}.
     */
    private Solution resolveSolution(String domainId, String solutionId) {
        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(org.sifunity.broker.environment.domain.Domain::getId)
                .orElseThrow(() -> new DomainNotFoundException(domainId));
        return solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .orElseThrow(() -> new org.sifunity.broker.environment.exception.SolutionNotFoundException(solutionId));
    }

    /**
     * Lists all solution service catalog entries for a solution.
     * 
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID string from the URL path
     * @return list of solution service DTOs
     */
    @GetMapping
    public ResponseEntity<List<SolutionServiceDTO>> listSolutionServices(
            @PathVariable String domainId,
            @PathVariable String solutionId) {

        log.debug("Listing solution services for solution: {}", solutionId);

        Solution solution = resolveSolution(domainId, solutionId);

        List<SolutionServiceDTO> results = solutionServiceRepository.findBySolutionId(solution.getId())
                .stream()
                .map(this::toDTO)
                .toList();

        log.debug("Returning {} solution services for solution: {}", results.size(), solutionId);

        return ResponseEntity.ok(results);
    }

    /**
     * Creates a new solution service catalog entry.
     * 
     * <p>Validates that the serviceType is one of the allowed values and that
     * the (solutionId, serviceName, serviceType) combination does not already exist.</p>
     * 
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID string from the URL path
     * @param request the creation request containing serviceName and serviceType
     * @return the created solution service DTO
     */
    @PostMapping
    public ResponseEntity<SolutionServiceDTO> createSolutionService(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody SolutionServiceCreateRequest request) {

        log.info("Creating solution service: solution={}, serviceName='{}', serviceType='{}'",
                solutionId, request.serviceName(), request.serviceType());

        // Validate serviceType
        if (!VALID_SERVICE_TYPES.contains(request.serviceType())) {
            return ResponseEntity.badRequest().build();
        }

        // Resolve solutionId string → Solution entity (scoped to domain)
        Solution solution = resolveSolution(domainId, solutionId);

        // Check for duplicate
        if (solutionServiceRepository.existsBySolutionIdAndServiceNameAndServiceType(
                solution.getId(), request.serviceName(), request.serviceType())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        // Create entity
        SolutionService entity = new SolutionService();
        entity.setSolutionId(solution.getId());
        entity.setServiceName(request.serviceName());
        entity.setServiceType(request.serviceType());

        SolutionService saved = solutionServiceRepository.save(entity);

        log.info("Solution service created: id={}, solution={}, serviceName='{}'",
                saved.getId(), solutionId, saved.getServiceName());

        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(saved));
    }

    /**
     * Deletes a solution service catalog entry by UUID.
     * 
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID string from the URL path
     * @param id the UUID of the solution service to delete
     * @return 204 No Content on success, 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSolutionService(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id) {

        log.info("Deleting solution service: id={}", id);

        SolutionService existing = solutionServiceRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        solutionServiceRepository.deleteById(id);

        // Mirror of XQueryTemplateService.delete: an XQUERYTEMPLATE service row and its Named Query
        // template are two halves of one thing, coupled by name with no foreign key, so removing
        // either removes both. Tolerant of a missing template — service rows predating the coupling
        // have none, and deleting such an orphan must still succeed.
        if (XQUERYTEMPLATE_SERVICE_TYPE.equals(existing.getServiceType())) {
            try {
                boolean removed = xQueryTemplateService.deleteTemplateIfPresent(
                        domainId, solutionId, existing.getServiceName());
                log.info("Companion Named Query template for '{}': {}",
                        existing.getServiceName(), removed ? "deleted" : "none found");
            } catch (RuntimeException e) {
                log.warn("Deleted the solution service but could not delete its companion Named Query "
                         + "template name='{}' in solution='{}'. Cause: {}",
                        existing.getServiceName(), solutionId, e.toString());
            }
        }

        log.info("Solution service deleted: id={}", id);

        return ResponseEntity.noContent().build();
    }

    private SolutionServiceDTO toDTO(SolutionService entity) {
        return new SolutionServiceDTO(
                entity.getId(),
                entity.getServiceName(),
                entity.getServiceType(),
                entity.getCreatedAt()
        );
    }
}
