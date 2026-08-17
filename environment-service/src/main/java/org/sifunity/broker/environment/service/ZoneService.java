// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.ContextCreateRequest;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.exception.DuplicateContextException;
import org.sifunity.broker.environment.exception.ContextNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateZoneException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.exception.ZoneNotFoundException;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.util.List;
import java.util.UUID;

/**
 * Service class for managing zones and contexts within the SIF Unity Broker.
 * 
 * <p>This service provides business logic for zone and context management operations,
 * including creation, retrieval, and validation. Zones provide logical grouping of
 * services and data, while contexts provide additional routing dimensions within zones.</p>
 * 
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Zone creation with uniqueness validation</li>
 *   <li>Zone listing and retrieval</li>
 *   <li>Context creation with parent zone validation</li>
 *   <li>Context listing within zones</li>
 *   <li>Zone and context existence validation</li>
 * </ul>
 * 
 * <p>All operations are transactional to ensure data consistency.</p>
 * 
 * @see Zone
 * @see Context
 * @see ZoneRepository
 * @see ContextRepository
 */
@Service
@Transactional(readOnly = true)
public class ZoneService {
    
    private static final Logger log = LoggerFactory.getLogger(ZoneService.class);
    
    private final ZoneRepository zoneRepository;
    private final ContextRepository contextRepository;
    private final SolutionRepository solutionRepository;
    private final DomainRepository domainRepository;

    public ZoneService(ZoneRepository zoneRepository, ContextRepository contextRepository,
                       SolutionRepository solutionRepository, DomainRepository domainRepository) {
        this.zoneRepository = zoneRepository;
        this.contextRepository = contextRepository;
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
    }

    /**
     * Resolves a solution scoped to its owning domain. {@code solutionId} is unique only
     * within a domain, so the lookup is scoped by {@code domainId} to avoid cross-tenant
     * resolution.
     *
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @return the resolved Solution
     * @throws SolutionNotFoundException if the domain or solution does not exist
     */
    private Solution resolveSolution(String domainId, String solutionId) {
        UUID domainUuid = domainRepository.findByDomainId(domainId)
            .map(Domain::getId)
            .orElseThrow(() -> new SolutionNotFoundException(solutionId));
        return solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
            .orElseThrow(() -> new SolutionNotFoundException(solutionId));
    }
    
    /**
     * Creates a new zone with the specified zone ID and description.
     * 
     * <p>This method validates that the zone ID is unique within the solution and follows the required
     * naming pattern (enforced by validation annotations). If the zone ID already
     * exists in the solution, a DuplicateZoneException is thrown.</p>
     * 
     * <p>The zone ID must start with an alphanumeric character and can contain
     * alphanumeric characters, underscores, and hyphens.</p>
     * 
     * @param request the zone creation request containing solution ID, zone ID and optional description
     * @return ZoneDTO containing the created zone information
     * @throws SolutionNotFoundException if the parent solution does not exist
     * @throws DuplicateZoneException if a zone with the same zone ID already exists in the solution
     * @see ZoneCreateRequest
     * @see ZoneDTO
     */
    @Transactional
    public ZoneDTO createZone(String domainId, ZoneCreateRequest request) {
        log.debug("Creating zone with ID: {} in solution: {}", request.zoneId(), request.solutionId());

        // Look up parent solution scoped to its domain, resolve to UUID
        Solution solution = resolveSolution(domainId, request.solutionId());
        
        // Validate zone uniqueness within solution using UUID
        if (zoneRepository.existsBySolutionUuidAndZoneId(solution.getId(), request.zoneId())) {
            log.warn("Attempted to create duplicate zone with ID: {} in solution: {}", 
                request.zoneId(), request.solutionId());
            throw new DuplicateZoneException(request.solutionId(), request.zoneId());
        }
        
        // Create and save zone with UUID FK
        Zone zone = new Zone();
        zone.setSolutionUuid(solution.getId());
        zone.setZoneId(request.zoneId());
        zone.setDescription(request.description());
        
        Zone savedZone = zoneRepository.save(zone);
        log.info("Successfully created zone with ID: {} in solution: {} (UUID: {})", 
            savedZone.getZoneId(), request.solutionId(), savedZone.getId());
        
        // Auto-create DEFAULT context per SIF spec using zone's UUID
        if (!contextRepository.existsByZoneUuidAndContextId(savedZone.getId(), "DEFAULT")) {
            Context defaultContext = new Context();
            defaultContext.setZoneUuid(savedZone.getId());
            defaultContext.setContextId("DEFAULT");
            defaultContext.setDescription("Default context (auto-created per SIF spec)");
            contextRepository.save(defaultContext);
            log.info("Created DEFAULT context in zone: {}", savedZone.getZoneId());
        }
        
        return mapToZoneDTO(savedZone, request.solutionId());
    }
    
    /**
     * Retrieves all zones in the system.
     * 
     * <p>This method returns a list of all zones currently registered in the system,
     * ordered by creation time. The list may be empty if no zones have been created.</p>
     * 
     * @return List of ZoneDTO objects representing all zones
     * @see ZoneDTO
     */
    public List<ZoneDTO> listZones() {
        log.debug("Retrieving all zones");
        
        List<Zone> zones = zoneRepository.findAll();
        log.debug("Found {} zones", zones.size());
        
        return zones.stream()
            .map(this::mapToZoneDTO)
            .toList();
    }
    
    /**
     * Retrieves all zones within a specific solution.
     * 
     * <p>This method returns a list of all zones that belong to the specified solution,
     * ordered by creation time. The list may be empty if no zones have been created
     * in the solution.</p>
     * 
     * @param solutionId the solution ID to filter by
     * @return List of ZoneDTO objects representing all zones in the solution
     * @throws SolutionNotFoundException if the solution does not exist
     * @see ZoneDTO
     */
    public List<ZoneDTO> listZonesBySolution(String domainId, String solutionId) {
        log.debug("Retrieving zones for solution: {}", solutionId);

        // Look up solution scoped to its domain, resolve to UUID
        Solution solution = resolveSolution(domainId, solutionId);
        
        List<Zone> zones = zoneRepository.findBySolutionUuid(solution.getId());
        log.debug("Found {} zones in solution: {}", zones.size(), solutionId);
        
        return zones.stream()
            .map(zone -> mapToZoneDTO(zone, solutionId))
            .toList();
    }
    
    /**
     * Creates a new context within the specified zone.
     * 
     * <p>This method validates that:</p>
     * <ul>
     *   <li>The parent zone exists</li>
     *   <li>The context ID is unique within the zone</li>
     *   <li>The context ID follows the required naming pattern</li>
     * </ul>
     * 
     * <p>The context ID must start with an alphanumeric character and can contain
     * alphanumeric characters, underscores, and hyphens.</p>
     * 
     * @param zoneId the zone ID where the context should be created
     * @param request the context creation request containing context ID and optional description
     * @return ContextDTO containing the created context information
     * @throws ZoneNotFoundException if the parent zone does not exist
     * @throws DuplicateContextException if a context with the same ID already exists in the zone
     * @see ContextCreateRequest
     * @see ContextDTO
     */
    @Transactional
    public ContextDTO createContext(String domainId, String solutionId, String zoneId, ContextCreateRequest request) {
        log.debug("Creating context with ID: {} in zone: {}", request.contextId(), zoneId);

        // Look up solution scoped to its domain, then zone scoped to that solution
        Solution solution = resolveSolution(domainId, solutionId);

        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), zoneId)
            .orElseThrow(() -> {
                log.warn("Attempted to create context in non-existent zone: {}", zoneId);
                return new ZoneNotFoundException(zoneId);
            });
        
        // Validate context uniqueness within zone using UUID
        if (contextRepository.existsByZoneUuidAndContextId(zone.getId(), request.contextId())) {
            log.warn("Attempted to create duplicate context with ID: {} in zone: {}", request.contextId(), zoneId);
            throw new DuplicateContextException(zoneId, request.contextId());
        }
        
        // Create and save context with UUID FK
        Context context = new Context();
        context.setZoneUuid(zone.getId());
        context.setContextId(request.contextId());
        context.setDescription(request.description());
        
        Context savedContext = contextRepository.save(context);
        log.info("Successfully created context with ID: {} in zone: {} (UUID: {})", 
            savedContext.getContextId(), zoneId, savedContext.getId());
        
        return mapToContextDTO(savedContext, zoneId);
    }

    /**
     * Retrieves all contexts within the specified zone.
     * 
     * <p>This method returns a list of all contexts within the specified zone,
     * ordered by creation time. The list may be empty if no contexts have been
     * created in the zone.</p>
     * 
     * @param zoneId the zone ID to retrieve contexts for
     * @return List of ContextDTO objects representing all contexts in the zone
     * @see ContextDTO
     */
    public List<ContextDTO> listContexts(String domainId, String solutionId, String zoneId) {
        log.debug("Retrieving contexts for zone: {}", zoneId);

        // Look up solution scoped to its domain, then zone scoped to that solution
        Solution solution = resolveSolution(domainId, solutionId);

        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), zoneId)
            .orElseThrow(() -> new ZoneNotFoundException(zoneId));
        
        List<Context> contexts = contextRepository.findByZoneUuid(zone.getId());
        log.debug("Found {} contexts in zone: {}", contexts.size(), zoneId);
        
        return contexts.stream()
            .map(context -> mapToContextDTO(context, zoneId))
            .toList();
    }
    
    /**
     * Deletes a zone and all its descendant data within a single transaction.
     * 
     * <p>This method performs the following operations in order:</p>
     * <ol>
     *   <li>Resolves the solution UUID from the solution ID string</li>
     *   <li>Finds the zone scoped to the solution</li>
     *   <li>Deletes all contexts within the zone (FK cascades handle context_services → ACL entries → provider registrations → subscriptions)</li>
     *   <li>Deletes the zone itself</li>
     * </ol>
     * 
     * @param solutionId the solution ID containing the zone
     * @param zoneId the zone ID to delete
     * @throws SolutionNotFoundException if the solution does not exist
     * @throws ZoneNotFoundException if the zone does not exist within the solution
     */
    @Transactional
    public void deleteZone(String domainId, String solutionId, String zoneId) {
        log.debug("Deleting zone with ID: {} in solution: {}", zoneId, solutionId);

        // Resolve solution scoped to its domain
        Solution solution = resolveSolution(domainId, solutionId);
        
        // Find zone scoped to solution
        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), zoneId)
            .orElseThrow(() -> {
                log.warn("Attempted to delete non-existent zone: {} in solution: {}", zoneId, solutionId);
                return new ZoneNotFoundException(zoneId);
            });
        
        // Delete all contexts in zone (FK cascades handle context_services → ACL entries → provider registrations → subscriptions)
        contextRepository.deleteByZoneUuid(zone.getId());
        log.debug("Deleted contexts for zone: {} in solution: {}", zoneId, solutionId);
        
        // Delete the zone
        zoneRepository.delete(zone);
        log.info("Successfully deleted zone with ID: {} in solution: {}", zoneId, solutionId);
    }

    /**
     * Deletes a specific context within a zone.
     *
     * @param solutionId the solution ID containing the zone
     * @param zoneId the zone ID containing the context
     * @param contextId the context ID to delete
     * @throws SolutionNotFoundException if the solution does not exist
     * @throws ZoneNotFoundException if the zone does not exist
     * @throws ContextNotFoundException if the context does not exist
     */
    @Transactional
    public void deleteContext(String domainId, String solutionId, String zoneId, String contextId) {
        log.debug("Deleting context: {} in zone: {} solution: {}", contextId, zoneId, solutionId);

        Solution solution = resolveSolution(domainId, solutionId);

        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), zoneId)
            .orElseThrow(() -> new ZoneNotFoundException(zoneId));

        Context context = contextRepository.findByZoneUuidAndContextId(zone.getId(), contextId)
            .orElseThrow(() -> new ContextNotFoundException(contextId));

        // FK cascades handle context_services → ACL entries → provider registrations → subscriptions
        contextRepository.delete(context);
        log.info("Successfully deleted context: {} in zone: {} solution: {}", contextId, zoneId, solutionId);
    }
    
    /**
     * Checks if a zone with the specified zone ID exists within a solution.
     * 
     * <p>This method is used for validation purposes by other services
     * that need to verify zone existence before performing operations.</p>
     * 
     * @param solutionId the solution ID to scope the check to
     * @param zoneId the zone ID to check
     * @return true if the zone exists within the solution, false otherwise
     */
    public boolean zoneExists(String domainId, String solutionId, String zoneId) {
        return domainRepository.findByDomainId(domainId)
            .flatMap(domain -> solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), solutionId))
            .map(solution -> zoneRepository.existsBySolutionUuidAndZoneId(solution.getId(), zoneId))
            .orElse(false);
    }
    
    /**
     * Checks if a context with the specified zone ID and context ID exists.
     * 
     * <p>This method is used for validation purposes by other services
     * that need to verify context existence before performing operations.</p>
     * 
     * @param zoneId the zone ID to check
     * @param contextId the context ID to check
     * @return true if the context exists, false otherwise
     */
    public boolean contextExists(String domainId, String solutionId, String zoneId, String contextId) {
        return domainRepository.findByDomainId(domainId)
            .flatMap(domain -> solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), solutionId))
            .flatMap(solution -> zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), zoneId))
            .map(zone -> contextRepository.existsByZoneUuidAndContextId(zone.getId(), contextId))
            .orElse(false);
    }
    
    /**
     * Maps a Zone entity to a ZoneDTO, resolving solutionUuid back to solutionId string.
     * Used when the solutionId is not already known (e.g., listZones).
     * 
     * @param zone the Zone entity to map
     * @return ZoneDTO containing the zone information
     */
    private ZoneDTO mapToZoneDTO(Zone zone) {
        String solutionId = solutionRepository.findById(zone.getSolutionUuid())
            .map(Solution::getSolutionId)
            .orElse(null);
        return mapToZoneDTO(zone, solutionId);
    }
    
    /**
     * Maps a Zone entity to a ZoneDTO using a pre-resolved solutionId string.
     * Used when the caller already has the solutionId (e.g., listZonesBySolution, createZone).
     * 
     * @param zone the Zone entity to map
     * @param solutionId the pre-resolved solution ID string
     * @return ZoneDTO containing the zone information
     */
    private ZoneDTO mapToZoneDTO(Zone zone, String solutionId) {
        return new ZoneDTO(
            zone.getId(),
            solutionId,
            zone.getZoneId(),
            zone.getDescription(),
            zone.getCreatedAt()  // populated internally; suppressed from JSON by @JsonIgnore on accessor
        );
    }
    
    /**
     * Maps a Context entity to a ContextDTO, resolving zoneUuid back to zoneId string.
     * Used when the zoneId is not already known.
     * 
     * @param context the Context entity to map
     * @return ContextDTO containing the context information
     */
    private ContextDTO mapToContextDTO(Context context) {
        String zoneId = zoneRepository.findById(context.getZoneUuid())
            .map(Zone::getZoneId)
            .orElse(null);
        return mapToContextDTO(context, zoneId);
    }
    
    /**
     * Maps a Context entity to a ContextDTO using a pre-resolved zoneId string.
     * Used when the caller already has the zoneId (e.g., listContexts, createContext).
     * 
     * @param context the Context entity to map
     * @param zoneId the pre-resolved zone ID string
     * @return ContextDTO containing the context information
     */
    private ContextDTO mapToContextDTO(Context context, String zoneId) {
        return new ContextDTO(
            context.getId(),
            zoneId,
            context.getContextId(),
            context.getDescription(),
            context.getCreatedAt()  // populated internally; suppressed from JSON by @JsonIgnore on accessor
        );
    }
}
