// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.dto.ContextServiceCreateRequest;
import org.sifunity.broker.environment.dto.ContextServiceDTO;
import org.sifunity.broker.environment.exception.ContextNotFoundException;
import org.sifunity.broker.environment.exception.ContextServiceNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateContextServiceException;
import org.sifunity.broker.environment.exception.InvalidServiceTypeException;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service class for managing context_services records.
 * 
 * <p>This service handles CRUD operations for context services and provides
 * the internal resolution logic used by AclService and ProviderService to
 * map (serviceName, contextUuid) pairs to context_service UUIDs.</p>
 * 
 * @see ContextService
 * @see ContextServiceRepository
 */
@Service
@Transactional
public class ContextServiceService {

    private static final Logger log = LoggerFactory.getLogger(ContextServiceService.class);

    private static final Set<String> ALLOWED_SERVICE_TYPES = Set.of(
            "UTILITY", "OBJECT", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE"
    );

    private final ContextServiceRepository contextServiceRepository;
    private final ContextRepository contextRepository;
    private final ZoneRepository zoneRepository;

    public ContextServiceService(ContextServiceRepository contextServiceRepository,
                                 ContextRepository contextRepository,
                                 ZoneRepository zoneRepository) {
        this.contextServiceRepository = contextServiceRepository;
        this.contextRepository = contextRepository;
        this.zoneRepository = zoneRepository;
    }

    /**
     * Creates a new context service record.
     * 
     * <p>Validates that the context exists, the service type is allowed,
     * and no duplicate (contextUuid, serviceName) pair exists.</p>
     * 
     * @param request the creation request
     * @return DTO of the created context service
     * @throws ContextNotFoundException if the context does not exist
     * @throws InvalidServiceTypeException if the service type is not in the allowed set
     * @throws DuplicateContextServiceException if the (contextUuid, serviceName) pair already exists
     * 
     * <p><strong>Requirements:</strong> 5.1, 5.2, 5.7</p>
     */
    /**
     * Creates a new context service given a resolved context UUID.
     *
     * @param contextUuid the resolved context UUID
     * @param serviceName the service name
     * @param serviceType the service type
     * @return the created context service DTO
     * 
     * <p><strong>Requirements:</strong> 5.1, 5.2, 5.7</p>
     */
    public ContextServiceDTO createContextService(UUID contextUuid, String serviceName, String serviceType) {
        log.debug("Creating context service: context={}, service='{}', type='{}'",
                contextUuid, serviceName, serviceType);

        // Validate context exists (Requirement 5.7)
        if (!contextRepository.existsById(contextUuid)) {
            throw new ContextNotFoundException(contextUuid);
        }

        // Validate service type (Requirement 1.3 - CHECK constraint values)
        if (!ALLOWED_SERVICE_TYPES.contains(serviceType)) {
            throw new InvalidServiceTypeException(serviceType);
        }

        // Check for duplicate (Requirement 5.2)
        if (contextServiceRepository.existsByContextUuidAndServiceName(
                contextUuid, serviceName)) {
            throw new DuplicateContextServiceException(contextUuid, serviceName);
        }

        ContextService entity = ContextService.builder()
                .contextUuid(contextUuid)
                .serviceName(serviceName)
                .serviceType(serviceType)
                .build();

        ContextService saved = contextServiceRepository.save(entity);
        log.info("Created context service: id={}, context={}, service='{}'",
                saved.getId(), saved.getContextUuid(), saved.getServiceName());

        return mapToDTO(saved);
    }

    /**
     * Lists all context services for a given context.
     * 
     * @param contextUuid the context UUID to filter by
     * @return list of context service DTOs
     * 
     * <p><strong>Requirements:</strong> 5.3</p>
     */
    @Transactional(readOnly = true)
    public List<ContextServiceDTO> listByContext(UUID contextUuid) {
        log.debug("Listing context services for context: {}", contextUuid);
        return contextServiceRepository.findByContextUuid(contextUuid).stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Lists all context services for all contexts within a given zone.
     * 
     * @param zoneUuid the zone UUID to filter by
     * @return list of context service DTOs across all contexts in the zone
     * 
     * <p><strong>Requirements:</strong> 5.4</p>
     */
    @Transactional(readOnly = true)
    public List<ContextServiceDTO> listByZone(UUID zoneUuid) {
        log.debug("Listing context services for zone: {}", zoneUuid);
        return contextServiceRepository.findByZoneUuid(zoneUuid).stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Deletes a context service by ID.
     * 
     * <p>Cascade deletion of dependent acl_entries and provider_registry rows
     * is handled by the database FK ON DELETE CASCADE constraints.</p>
     * 
     * @param id the context service UUID to delete
     * @throws ContextServiceNotFoundException if the context service does not exist
     * 
     * <p><strong>Requirements:</strong> 5.5, 5.6</p>
     */
    public void deleteContextService(UUID id) {
        log.debug("Deleting context service: {}", id);

        ContextService entity = contextServiceRepository.findById(id)
                .orElseThrow(() -> new ContextServiceNotFoundException(id));

        contextServiceRepository.delete(entity);
        log.info("Deleted context service: id={}, context={}, service='{}'",
                entity.getId(), entity.getContextUuid(), entity.getServiceName());
    }

    /**
     * Resolves a (contextUuid, serviceName) pair to a context_service UUID,
     * auto-creating the record if it does not exist.
     * 
     * <p>Used internally by AclService and ProviderService to transparently
     * manage context_service records during ACL/provider creation.</p>
     * 
     * @param contextUuid the context UUID
     * @param serviceName the service name
     * @param serviceType the service type to use if creating a new record
     * @return the UUID of the existing or newly created context_service
     * 
     * <p><strong>Requirements:</strong> 6.4, 6.5</p>
     */
    public UUID resolveOrCreate(UUID contextUuid, String serviceName, String serviceType) {
        log.debug("Resolving context service: context={}, service='{}', type='{}'",
                contextUuid, serviceName, serviceType);

        return contextServiceRepository.findByContextUuidAndServiceName(contextUuid, serviceName)
                .map(ContextService::getId)
                .orElseGet(() -> {
                    log.debug("Context service not found, creating: context={}, service='{}'",
                            contextUuid, serviceName);

                    ContextService entity = ContextService.builder()
                            .contextUuid(contextUuid)
                            .serviceName(serviceName)
                            .serviceType(serviceType)
                            .build();

                    ContextService saved = contextServiceRepository.save(entity);
                    log.info("Auto-created context service: id={}, context={}, service='{}'",
                            saved.getId(), saved.getContextUuid(), saved.getServiceName());
                    return saved.getId();
                });
    }

    private ContextServiceDTO mapToDTO(ContextService entity) {
        // Resolve human-readable context and zone identifiers for display
        String contextId = null;
        String zoneId = null;
        var context = contextRepository.findById(entity.getContextUuid()).orElse(null);
        if (context != null) {
            contextId = context.getContextId();
            var zone = zoneRepository.findById(context.getZoneUuid()).orElse(null);
            if (zone != null) {
                zoneId = zone.getZoneId();
            }
        }
        return new ContextServiceDTO(
                entity.getId(),
                entity.getContextUuid(),
                contextId,
                zoneId,
                entity.getServiceName(),
                entity.getServiceType(),
                entity.getCreatedAt()
        );
    }
}
