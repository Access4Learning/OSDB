// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.AclEntryUpdateRequest;
import org.sifunity.broker.environment.exception.AclEntryNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateAclEntryException;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.util.List;
import java.util.UUID;

/**
 * Service class for managing Access Control List (ACL) entries.
 * 
 * <p>This service handles the business logic for ACL management including:
 * <ul>
 *   <li>Creating ACL entries with validation and duplicate checking</li>
 *   <li>Retrieving ACL entries by environment</li>
 *   <li>Updating ACL entry access levels</li>
 *   <li>Deleting ACL entries</li>
 *   <li>Internal permission checking for authorization services</li>
 * </ul>
 * 
 * <p>Internally, ACL entries reference context_services via contextServiceUuid.
 * The service layer resolves (serviceName, contextUuid) → contextServiceUuid on create,
 * and maps contextServiceUuid → (serviceName, contextUuid) on read, preserving
 * the external API contract.</p>
 * 
 * @see AclEntry
 * @see AclEntryRepository
 * @see AclEntryCreateRequest
 * @see AclEntryDTO
 */
@Service
@Transactional
public class AclService {
    
    private static final Logger log = LoggerFactory.getLogger(AclService.class);
    
    private static final String DEFAULT_SERVICE_TYPE = "OBJECT";
    
    private final AclEntryRepository aclEntryRepository;
    private final EnvironmentRepository environmentRepository;
    private final ContextRepository contextRepository;
    private final ContextServiceService contextServiceService;
    private final ContextServiceRepository contextServiceRepository;
    private final SolutionRepository solutionRepository;
    private final ZoneRepository zoneRepository;
    private final DomainRepository domainRepository;

    public AclService(AclEntryRepository aclEntryRepository,
                      EnvironmentRepository environmentRepository,
                      ContextRepository contextRepository,
                      ContextServiceService contextServiceService,
                      ContextServiceRepository contextServiceRepository,
                      SolutionRepository solutionRepository,
                      ZoneRepository zoneRepository,
                      DomainRepository domainRepository) {
        this.aclEntryRepository = aclEntryRepository;
        this.environmentRepository = environmentRepository;
        this.contextRepository = contextRepository;
        this.contextServiceService = contextServiceService;
        this.contextServiceRepository = contextServiceRepository;
        this.solutionRepository = solutionRepository;
        this.zoneRepository = zoneRepository;
        this.domainRepository = domainRepository;
    }
    
    /**
     * Creates a new ACL entry with admin/self-provisioning enforcement.
     * 
     * <p>This method performs the complete ACL entry creation flow:
     * <ol>
     *   <li>Determines effective environmentId and accessLevel based on admin status</li>
     *   <li>Resolves zoneId + contextId to internal contextUuid via solution hierarchy</li>
     *   <li>Validates that the environment exists</li>
     *   <li>Resolves (serviceName, contextUuid) → contextServiceUuid via ContextServiceService</li>
     *   <li>Checks for duplicate ACL entry (environmentId, contextServiceUuid, accessType)</li>
     *   <li>Checks single-provider constraint for PROVIDE access type</li>
     *   <li>Creates and saves the AclEntry entity with contextServiceUuid</li>
     *   <li>Returns the created ACL entry as DTO with serviceName, zoneId, contextId</li>
     * </ol>
     * 
     * @param solutionId the solution ID from the URL path, used for zone/context resolution
     * @param request the ACL entry creation request containing all required parameters
     * @param requester the authenticated environment making the request
     * @param isAdmin whether the requester is an admin environment
     * @return AclEntryDTO representing the created ACL entry
     * @throws EnvironmentNotFoundException if the specified environment does not exist
     * @throws DuplicateAclEntryException if an ACL entry with the same parameters already exists
     * @throws SifBrokerException ACL_005 if solution not found
     * @throws SifBrokerException ACL_006 if zone not found in solution
     * @throws SifBrokerException ACL_007 if context not found in zone
     * 
     * <p><strong>Requirements:</strong> 1.4, 2.1, 2.5, 4.1, 4.2, 4.3</p>
     */
    @CacheEvict(value = {"acl-permissions", "acl-any-grant"}, allEntries = true)
    public AclEntryDTO createAclEntry(String domainId, String solutionId, AclEntryCreateRequest request,
                                       Environment requester, boolean isAdmin) {
        UUID effectiveEnvironmentId;
        AccessLevel effectiveAccessLevel;

        if (isAdmin) {
            effectiveEnvironmentId = request.environmentId();
            effectiveAccessLevel = request.accessLevel();
        } else {
            // Non-admin: force own environment, force PENDING
            effectiveEnvironmentId = requester.getId();
            effectiveAccessLevel = AccessLevel.PENDING;
        }

        log.debug("Creating ACL entry for environment: {}, service: {}, zoneId: {}, contextId: {}, access: {}, level: {} (admin: {})",
                effectiveEnvironmentId, request.serviceName(), request.zoneId(), request.contextId(),
                request.accessType(), effectiveAccessLevel, isAdmin);

        // Resolve zoneId + contextId to internal contextUuid via solution hierarchy
        UUID contextUuid = resolveContext(domainId, solutionId, request.zoneId(), request.contextId());
        
        // Validate environment exists
        Environment environment = environmentRepository.findById(effectiveEnvironmentId)
                .orElseThrow(() -> new EnvironmentNotFoundException(effectiveEnvironmentId));
        
        // Resolve (serviceName, contextUuid) → contextServiceUuid via ContextServiceService
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                contextUuid, request.serviceName(), DEFAULT_SERVICE_TYPE);
        
        // Check for duplicate ACL entry using (environmentId, contextServiceUuid, accessType)
        boolean exists = aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessType(
                effectiveEnvironmentId,
                contextServiceUuid,
                request.accessType()
        );
        
        if (exists) {
            log.warn("Attempted to create duplicate ACL entry for environment: {}, service: {}, zoneId: {}, contextId: {}, access: {}",
                    effectiveEnvironmentId, request.serviceName(), request.zoneId(), request.contextId(),
                    request.accessType());
            throw new DuplicateAclEntryException(
                    effectiveEnvironmentId,
                    request.serviceName(),
                    contextUuid,
                    request.accessType()
            );
        }

        // Check single-provider constraint: only one PROVIDE per context_service across all environments
        if (request.accessType() == AccessType.PROVIDE) {
            boolean providerExists = aclEntryRepository.existsByContextServiceUuidAndAccessType(
                    contextServiceUuid,
                    AccessType.PROVIDE
            );
            if (providerExists) {
                log.warn("Attempted to add second provider for service: {}, zoneId: {}, contextId: {}. Only one provider allowed per service/context.",
                        request.serviceName(), request.zoneId(), request.contextId());
                throw new SifBrokerException("ACL_003", HttpStatus.CONFLICT,
                        String.format("A provider already exists for service '%s' in this zone/context. " +
                                "Only one provider is allowed per service per zone/context.",
                                request.serviceName()));
            }
        }
        
        // Create and save AclEntry entity with contextServiceUuid
        AclEntry aclEntry = new AclEntry();
        aclEntry.setEnvironment(environment);
        aclEntry.setContextServiceUuid(contextServiceUuid);
        aclEntry.setAccessType(request.accessType());
        aclEntry.setAccessLevel(effectiveAccessLevel);
        
        AclEntry savedAclEntry = aclEntryRepository.save(aclEntry);
        
        log.info("Created ACL entry with ID: {} for environment: {} to access service: {} in zone: {}/context: {} with access: {} and level: {}",
                savedAclEntry.getId(), effectiveEnvironmentId, request.serviceName(),
                request.zoneId(), request.contextId(), request.accessType(), effectiveAccessLevel);
        
        // Load ContextService to map back to serviceName/contextUuid for DTO
        ContextService contextService = contextServiceRepository.findById(contextServiceUuid)
                .orElseThrow(() -> new IllegalStateException(
                        "ContextService not found for UUID: " + contextServiceUuid));
        
        return mapToAclEntryDTO(savedAclEntry, contextService);
    }
    
    /**
     * Backward-compatible overload for tests and internal callers that don't need
     * admin/self-provisioning logic. Delegates to the full 4-arg version with admin=true.
     * 
     * <p>This method resolves solutionId by looking up the environment's solution,
     * then delegates to the full createAclEntry with isAdmin=true.</p>
     * 
     * @param request the ACL entry creation request
     * @return AclEntryDTO representing the created ACL entry
     */
    @CacheEvict(value = {"acl-permissions", "acl-any-grant"}, allEntries = true)
    public AclEntryDTO createAclEntry(AclEntryCreateRequest request) {
        // Resolve environment to get solutionId
        Environment environment = environmentRepository.findById(request.environmentId())
                .orElseThrow(() -> new EnvironmentNotFoundException(request.environmentId()));
        
        // Get solution from environment's solutionUuid
        Solution solution = solutionRepository.findById(environment.getSolutionUuid())
                .orElseThrow(() -> new SifBrokerException("ACL_005", HttpStatus.NOT_FOUND,
                        "Solution not found for environment: " + request.environmentId()));

        // Resolve the owning domainId so the ACL resolution is domain-scoped
        String domainId = domainRepository.findById(solution.getDomainUuid())
                .map(Domain::getDomainId)
                .orElseThrow(() -> new SifBrokerException("ACL_005", HttpStatus.NOT_FOUND,
                        "Domain not found for environment: " + request.environmentId()));

        return createAclEntry(domainId, solution.getSolutionId(), request, environment, true);
    }

    /**
     * Lists all ACL entries for a specific environment.
     * 
     * <p>Returns all ACL entries associated with the specified environment,
     * resolving each contextServiceUuid back to (serviceName, contextUuid)
     * for the external API contract.</p>
     * 
     * @param environmentId the environment ID to retrieve ACL entries for
     * @return List of AclEntryDTO objects for the environment (empty list if none found)
     * 
     * <p><strong>Requirements:</strong> 6.2, 6.6</p>
     */
    @Transactional(readOnly = true)
    public List<AclEntryDTO> listAclEntries(UUID environmentId) {
        log.debug("Listing ACL entries for environment: {}", environmentId);
        
        List<AclEntry> aclEntries = aclEntryRepository.findByEnvironmentId(environmentId);
        
        log.debug("Found {} ACL entries for environment: {}", aclEntries.size(), environmentId);
        
        return aclEntries.stream()
                .map(aclEntry -> {
                    ContextService contextService = contextServiceRepository
                            .findById(aclEntry.getContextServiceUuid())
                            .orElse(null);
                    return mapToAclEntryDTO(aclEntry, contextService);
                })
                .toList();
    }
    
    /**
     * Updates an ACL entry's access level.
     * 
     * @param id the ID of the ACL entry to update
     * @param request the update request containing the new access level
     * @return AclEntryDTO representing the updated ACL entry
     * @throws AclEntryNotFoundException if the ACL entry does not exist
     * 
     * <p><strong>Requirements:</strong> 4.5</p>
     */
    @CacheEvict(value = {"acl-permissions", "acl-any-grant"}, allEntries = true)
    public AclEntryDTO updateAclEntry(UUID id, AclEntryUpdateRequest request) {
        log.debug("Updating ACL entry: {} to access level: {}", id, request.accessLevel());
        
        AclEntry aclEntry = aclEntryRepository.findById(id)
                .orElseThrow(() -> new AclEntryNotFoundException(id));
        
        aclEntry.setAccessLevel(request.accessLevel());
        AclEntry savedAclEntry = aclEntryRepository.save(aclEntry);
        
        log.info("Updated ACL entry: {} to access level: {}", id, request.accessLevel());
        
        ContextService contextService = contextServiceRepository
                .findById(savedAclEntry.getContextServiceUuid())
                .orElse(null);
        
        return mapToAclEntryDTO(savedAclEntry, contextService);
    }
    
    /**
     * Deletes an ACL entry.
     * 
     * @param id the ID of the ACL entry to delete
     * @throws AclEntryNotFoundException if the ACL entry does not exist
     * 
     * <p><strong>Requirements:</strong> 4.6</p>
     */
    @CacheEvict(value = {"acl-permissions", "acl-any-grant"}, allEntries = true)
    public void deleteAclEntry(UUID id) {
        log.debug("Deleting ACL entry: {}", id);
        
        AclEntry aclEntry = aclEntryRepository.findById(id)
                .orElseThrow(() -> new AclEntryNotFoundException(id));
        
        aclEntryRepository.delete(aclEntry);
        
        log.info("Deleted ACL entry: {}", id);
    }
    
    /**
     * Internal helper method to check if an environment has permission for a specific operation.
     * 
     * <p>Resolves (serviceName, contextUuid) → contextServiceUuid via ContextServiceRepository,
     * then checks if an APPROVED ACL entry exists. Returns false if the context_service
     * doesn't exist (no permission possible without a registered context_service).</p>
     * 
     * @param environmentId the environment ID to check
     * @param serviceName the service name to check access for
     * @param contextUuid the context UUID to check access in (may be null for wildcard)
     * @param accessType the type of access to check
     * @return true if permission is granted (APPROVED ACL entry exists), false otherwise
     * 
     * <p><strong>Requirements:</strong> 7.6</p>
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID environmentId, String serviceName, 
                                UUID contextUuid, AccessType accessType) {
        log.debug("Checking permission for environment: {}, service: {}, contextUuid: {}, access: {}",
                environmentId, serviceName, contextUuid, accessType);
        
        // Resolve (serviceName, contextUuid) → contextServiceUuid
        // If context_service doesn't exist, no permission is possible
        return contextServiceRepository.findByContextUuidAndServiceName(contextUuid, serviceName)
                .flatMap(contextService -> aclEntryRepository
                        .findByEnvironmentIdAndContextServiceUuidAndAccessType(
                                environmentId, contextService.getId(), accessType))
                .map(aclEntry -> aclEntry.getAccessLevel() == AccessLevel.APPROVED)
                .orElse(false);
    }
    
    /**
     * Resolves a context UUID from the SIF-standard solutionId → zoneId → contextId path.
     * 
     * <p>Resolution path: solutionId → Solution → Zone → Context → contextUuid</p>
     * 
     * @param solutionId the solution ID to resolve
     * @param zoneId the zone ID within the solution
     * @param contextId the context ID within the zone
     * @return the resolved context UUID
     * @throws SifBrokerException ACL_005 if solution not found
     * @throws SifBrokerException ACL_006 if zone not found in solution
     * @throws SifBrokerException ACL_007 if context not found in zone
     * 
     * <p><strong>Requirements:</strong> 2.1, 2.2, 2.3, 2.4</p>
     */
    private UUID resolveContext(String domainId, String solutionId, String zoneId, String contextId) {
        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(Domain::getId)
                .orElseThrow(() -> new SifBrokerException("ACL_005", HttpStatus.NOT_FOUND,
                        "Solution not found: " + solutionId));

        Solution solution = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .orElseThrow(() -> new SifBrokerException("ACL_005", HttpStatus.NOT_FOUND,
                        "Solution not found: " + solutionId));

        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), zoneId)
                .orElseThrow(() -> new SifBrokerException("ACL_006", HttpStatus.NOT_FOUND,
                        "Zone not found: " + zoneId + " in solution " + solutionId));

        Context context = contextRepository.findByZoneUuidAndContextId(zone.getId(), contextId)
                .orElseThrow(() -> new SifBrokerException("ACL_007", HttpStatus.NOT_FOUND,
                        "Context not found: " + contextId + " in zone " + zoneId));

        return context.getId();
    }

    /**
     * Maps an AclEntry entity to AclEntryDTO, resolving contextServiceUuid back to
     * serviceName, zoneId, and contextId for the SIF-compliant external API contract.
     * 
     * <p>When contextUuid is non-null, looks up Context → Zone to get contextId and zoneId strings.
     * When contextUuid is null (wildcard ACL), sets both zoneId and contextId to null.</p>
     * 
     * @param aclEntry the AclEntry entity to map
     * @param contextService the ContextService to extract serviceName and contextUuid from (may be null)
     * @return AclEntryDTO containing the entity data with resolved zoneId/contextId fields
     * 
     * <p><strong>Requirements:</strong> 3.4, 3.5</p>
     */
    private AclEntryDTO mapToAclEntryDTO(AclEntry aclEntry, ContextService contextService) {
        String serviceName = contextService != null ? contextService.getServiceName() : null;
        UUID contextUuid = contextService != null ? contextService.getContextUuid() : null;

        String zoneId = null;
        String contextId = null;

        if (contextUuid != null) {
            Context context = contextRepository.findById(contextUuid).orElse(null);
            if (context != null) {
                contextId = context.getContextId();
                Zone zone = zoneRepository.findById(context.getZoneUuid()).orElse(null);
                if (zone != null) {
                    zoneId = zone.getZoneId();
                }
            }
        }

        return new AclEntryDTO(
                aclEntry.getId(),
                aclEntry.getEnvironment().getId(),
                serviceName,
                zoneId,
                contextId,
                aclEntry.getAccessType(),
                aclEntry.getAccessLevel()
        );
    }
}
