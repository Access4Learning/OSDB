// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.dto.DomainUpdateRequest;
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateDomainException;
import org.sifunity.broker.environment.repository.DomainRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for managing domains within the SIF Unity Broker.
 */
@Service
@Transactional(readOnly = true)
public class DomainService {
    
    private static final Logger log = LoggerFactory.getLogger(DomainService.class);
    
    private final DomainRepository domainRepository;
    private final CacheManager cacheManager;
    private final CascadeCleanupClient cascadeCleanupClient;
    
    public DomainService(DomainRepository domainRepository, CacheManager cacheManager,
                         CascadeCleanupClient cascadeCleanupClient) {
        this.domainRepository = domainRepository;
        this.cacheManager = cacheManager;
        this.cascadeCleanupClient = cascadeCleanupClient;
    }
    
    /**
     * Retrieves a domain by its UUID, with caching for the auth token validation hot path.
     * Used by AuthenticationService to avoid repeated database lookups during token validation.
     *
     * @param id the domain UUID
     * @return Optional containing the Domain entity if found, empty otherwise
     */
    @Cacheable(value = "auth-domain", key = "#id", unless = "#result == null")
    public Optional<Domain> findByIdCached(UUID id) {
        return domainRepository.findById(id);
    }
    
    /**
     * Creates a new domain with the specified domain ID and description.
     * 
     * @param request the domain creation request
     * @return DomainDTO containing the created domain information
     * @throws DuplicateDomainException if a domain with the same domain ID already exists
     */
    @Transactional
    public DomainDTO createDomain(DomainCreateRequest request) {
        log.debug("Creating domain with ID: {}", request.domainId());
        
        if (domainRepository.existsByDomainId(request.domainId())) {
            log.warn("Attempted to create duplicate domain with ID: {}", request.domainId());
            throw new DuplicateDomainException(request.domainId());
        }
        
        Domain domain = new Domain();
        domain.setDomainId(request.domainId());
        domain.setDescription(request.description());
        
        Domain savedDomain = domainRepository.save(domain);
        log.info("Successfully created domain with ID: {} (UUID: {})", 
            savedDomain.getDomainId(), savedDomain.getId());
        
        return mapToDomainDTO(savedDomain);
    }
    
    /**
     * Retrieves a domain by its domain ID.
     * 
     * @param domainId the domain ID to retrieve
     * @return DomainDTO containing the domain information
     * @throws DomainNotFoundException if the domain does not exist
     */
    public DomainDTO getDomain(String domainId) {
        log.debug("Retrieving domain with ID: {}", domainId);
        
        Domain domain = domainRepository.findByDomainId(domainId)
            .orElseThrow(() -> new DomainNotFoundException(domainId));
        
        return mapToDomainDTO(domain);
    }
    
    /**
     * Retrieves all domains in the system.
     * 
     * @return List of DomainDTO objects representing all domains
     */
    public List<DomainDTO> listDomains() {
        log.debug("Retrieving all domains");
        
        List<Domain> domains = domainRepository.findAll();
        log.debug("Found {} domains", domains.size());
        
        return domains.stream()
            .map(this::mapToDomainDTO)
            .toList();
    }
    
    /**
     * Updates a domain's description.
     * 
     * @param domainId the domain ID to update
     * @param request the update request containing new description
     * @return DomainDTO containing the updated domain information
     * @throws DomainNotFoundException if the domain does not exist
     */
    @Transactional
    public DomainDTO updateDomain(String domainId, DomainUpdateRequest request) {
        log.debug("Updating domain with ID: {}", domainId);
        
        Domain domain = domainRepository.findByDomainId(domainId)
            .orElseThrow(() -> new DomainNotFoundException(domainId));
        
        domain.setDescription(request.description());
        Domain updatedDomain = domainRepository.save(domain);
        
        log.info("Successfully updated domain with ID: {}", domainId);
        
        return mapToDomainDTO(updatedDomain);
    }
    
    /**
     * Deletes a domain by its domain ID.
     * Database ON DELETE CASCADE handles deletion of all child solutions, zones, contexts, and environments.
     * Programmatically evicts the auth-domain cache by domainUuid before deletion.
     * 
     * @param domainId the domain ID to delete
     * @throws DomainNotFoundException if the domain does not exist
     */
    @Transactional
    public void deleteDomain(String domainId) {
        log.debug("Deleting domain with ID: {}", domainId);
        
        Domain domain = domainRepository.findByDomainId(domainId)
            .orElseThrow(() -> new DomainNotFoundException(domainId));
        
        // Evict auth-domain cache by domainUuid
        var authDomainCache = cacheManager.getCache("auth-domain");
        if (authDomainCache != null) {
            authDomainCache.evict(domain.getId());
        }
        
        domainRepository.delete(domain);
        log.info("Successfully deleted domain with ID: {}", domainId);

        // Trigger async cascade cleanup (fire-and-forget)
        try {
            cascadeCleanupClient.triggerDomainCleanup(domainId);
        } catch (Exception e) {
            log.warn("Failed to trigger cascade cleanup for domain={}: {}", domainId, e.getMessage());
        }
    }
    
    /**
     * Checks if a domain with the specified domain ID exists.
     * 
     * @param domainId the domain ID to check
     * @return true if the domain exists, false otherwise
     */
    public boolean domainExists(String domainId) {
        return domainRepository.existsByDomainId(domainId);
    }
    
    private DomainDTO mapToDomainDTO(Domain domain) {
        return new DomainDTO(
            domain.getId(),
            domain.getDomainId(),
            domain.getDescription(),
            domain.getCreatedAt()
        );
    }
}
