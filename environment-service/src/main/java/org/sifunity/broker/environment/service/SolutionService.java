// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.ProviderRegistration;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateSolutionException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EntityTagRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.sifunity.broker.common.dto.OperationsDTO;

/**
 * Service class for managing solutions within the SIF Unity Broker.
 */
@Service
@Transactional(readOnly = true)
public class SolutionService {
    
    private static final Logger log = LoggerFactory.getLogger(SolutionService.class);
    
    private final SolutionRepository solutionRepository;
    private final DomainRepository domainRepository;
    private final ZoneRepository zoneRepository;
    private final ContextRepository contextRepository;
    private final ContextServiceRepository contextServiceRepository;
    private final EntityTagRepository entityTagRepository;
    private final ProviderRegistrationRepository providerRegistrationRepository;
    private final EnvironmentRepository environmentRepository;
    private final ContextServiceService contextServiceService;
    private final CacheManager cacheManager;
    private final CascadeCleanupClient cascadeCleanupClient;
    
    public SolutionService(SolutionRepository solutionRepository, DomainRepository domainRepository,
                           ZoneRepository zoneRepository,
                           ContextRepository contextRepository,
                           ContextServiceRepository contextServiceRepository,
                           EntityTagRepository entityTagRepository,
                           ProviderRegistrationRepository providerRegistrationRepository,
                           EnvironmentRepository environmentRepository,
                           ContextServiceService contextServiceService,
                           CacheManager cacheManager,
                           CascadeCleanupClient cascadeCleanupClient) {
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
        this.zoneRepository = zoneRepository;
        this.contextRepository = contextRepository;
        this.contextServiceRepository = contextServiceRepository;
        this.entityTagRepository = entityTagRepository;
        this.providerRegistrationRepository = providerRegistrationRepository;
        this.environmentRepository = environmentRepository;
        this.contextServiceService = contextServiceService;
        this.cacheManager = cacheManager;
        this.cascadeCleanupClient = cascadeCleanupClient;
    }
    
    /**
     * Creates a new solution with the specified domain ID, solution ID, and description.
     * 
     * @param request the solution creation request
     * @return SolutionDTO containing the created solution information
     * @throws DomainNotFoundException if the parent domain does not exist
     * @throws DuplicateSolutionException if a solution with the same ID already exists in the domain
     */
    @Transactional
    public SolutionDTO createSolution(SolutionCreateRequest request) {
        log.debug("Creating solution with ID: {} in domain: {}", 
            request.solutionId(), request.domainId());
        
        // Look up parent domain by string ID, resolve to UUID
        Domain domain = domainRepository.findByDomainId(request.domainId())
            .orElseThrow(() -> {
                log.warn("Attempted to create solution in non-existent domain: {}", request.domainId());
                return new DomainNotFoundException(request.domainId());
            });
        
        // Validate solution uniqueness within domain using UUID
        if (solutionRepository.existsByDomainUuidAndSolutionId(domain.getId(), request.solutionId())) {
            log.warn("Attempted to create duplicate solution with ID: {} in domain: {}", 
                request.solutionId(), request.domainId());
            throw new DuplicateSolutionException(request.domainId(), request.solutionId());
        }
        
        Solution solution = new Solution();
        solution.setDomainUuid(domain.getId());
        solution.setSolutionId(request.solutionId());
        solution.setDescription(request.description());
        
        Solution savedSolution = solutionRepository.save(solution);
        log.info("Successfully created solution with ID: {} in domain: {} (UUID: {})", 
            savedSolution.getSolutionId(), request.domainId(), savedSolution.getId());
        
        // Bootstrap the environment-global zone with DEFAULT context per SIF spec
        bootstrapEnvironmentGlobalZone(savedSolution.getDomainUuid(), savedSolution.getSolutionId());
        
        return mapToSolutionDTO(savedSolution, request.domainId());
    }
    
    /**
     * Creates the environment-global zone and DEFAULT context for a new solution.
     * Also creates the BROKER_SERVICES environment and registers the alerts provider.
     * Per SIF spec, every solution must have an environment-global zone for utility services.
     */
    private void bootstrapEnvironmentGlobalZone(UUID domainUuid, String solutionId) {
        Solution solution = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
            .orElseThrow(() -> new SolutionNotFoundException(solutionId));
        
        if (!zoneRepository.existsBySolutionUuidAndZoneId(solution.getId(), "environment-global")) {
            var zone = new org.sifunity.broker.environment.domain.Zone();
            zone.setSolutionUuid(solution.getId());
            zone.setZoneId("environment-global");
            zone.setDescription("Global zone for utility services (auto-created per SIF spec)");
            var savedZone = zoneRepository.save(zone);
            log.info("Created environment-global zone for solution: {}", solutionId);
            
            // Create DEFAULT context in the environment-global zone
            var context = new org.sifunity.broker.environment.domain.Context();
            context.setZoneUuid(savedZone.getId());
            context.setContextId("DEFAULT");
            context.setDescription("Default context (auto-created per SIF spec)");
            contextRepository.save(context);
            log.info("Created DEFAULT context in environment-global zone for solution: {}", solutionId);
        }
        
        // Create BROKER_SERVICES environment if not already present
        String brokerAppName = "BROKER_SERVICES_" + solution.getId();
        var existingBrokerEnv = environmentRepository.findByApplicationNameAndSolutionUuid(
                brokerAppName, solution.getId());
        Environment brokerEnv;
        if (existingBrokerEnv.isEmpty()) {
            byte[] secretBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(secretBytes);
            brokerEnv = Environment.builder()
                    .applicationName(brokerAppName)
                    .consumerName("Internal Broker Services")
                    .type(EnvironmentType.BROKERED)
                    .solutionUuid(solution.getId())
                    .sessionToken(java.util.UUID.randomUUID().toString())
                    .sharedSecret(java.util.Base64.getEncoder().encodeToString(secretBytes))
                    .fingerprint("broker-services")
                    .build();
            brokerEnv = environmentRepository.save(brokerEnv);
            log.info("Created BROKER_SERVICES environment for solution: {}", solutionId);
        } else {
            brokerEnv = existingBrokerEnv.get();
        }
        
        // Register alerts provider in environment-global zone if not already registered
        UUID alertsContextServiceUuid = contextServiceService.resolveOrCreate(
                contextRepository.findByZoneUuidAndContextId(
                        zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global")
                                .orElseThrow().getId(), "DEFAULT")
                        .orElseThrow().getId(),
                "alerts", "UTILITY");
        if (providerRegistrationRepository.findByContextServiceUuid(alertsContextServiceUuid).isEmpty()) {
            ProviderRegistration alertsProvider = ProviderRegistration.builder()
                    .environment(brokerEnv)
                    .contextServiceUuid(alertsContextServiceUuid)
                    .endpoint("internal://broker/alerts")
                    .serviceType(ServiceType.UTILITY)
                    .supportedRequestTypes(List.of(
                            new SupportedRequestType(RequestType.IMMEDIATE,
                                    OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))
                    ))
                    .build();
            providerRegistrationRepository.save(alertsProvider);
            log.info("Registered alerts provider in environment-global zone for solution: {}", solutionId);
        }
        
        // Register codeSets provider in environment-global zone if not already registered
        UUID codeSetsContextServiceUuid = contextServiceService.resolveOrCreate(
                contextRepository.findByZoneUuidAndContextId(
                        zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global")
                                .orElseThrow().getId(), "DEFAULT")
                        .orElseThrow().getId(),
                "codeSets", "UTILITY");
        if (providerRegistrationRepository.findByContextServiceUuid(codeSetsContextServiceUuid).isEmpty()) {
            ProviderRegistration codeSetsProvider = ProviderRegistration.builder()
                    .environment(brokerEnv)
                    .contextServiceUuid(codeSetsContextServiceUuid)
                    .endpoint("internal://broker/codeSets")
                    .serviceType(ServiceType.UTILITY)
                    .supportedRequestTypes(List.of(
                            new SupportedRequestType(RequestType.IMMEDIATE,
                                    OperationsDTO.of(Set.of(Operation.QUERY, Operation.CREATE, Operation.UPDATE, Operation.DELETE)))
                    ))
                    .build();
            providerRegistrationRepository.save(codeSetsProvider);
            log.info("Registered codeSets provider in environment-global zone for solution: {}", solutionId);
        }
        
        // Register namespaces provider in environment-global zone if not already registered
        UUID namespacesContextServiceUuid = contextServiceService.resolveOrCreate(
                contextRepository.findByZoneUuidAndContextId(
                        zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global")
                                .orElseThrow().getId(), "DEFAULT")
                        .orElseThrow().getId(),
                "namespaces", "UTILITY");
        if (providerRegistrationRepository.findByContextServiceUuid(namespacesContextServiceUuid).isEmpty()) {
            ProviderRegistration namespacesProvider = ProviderRegistration.builder()
                    .environment(brokerEnv)
                    .contextServiceUuid(namespacesContextServiceUuid)
                    .endpoint("internal://broker/namespaces")
                    .serviceType(ServiceType.UTILITY)
                    .supportedRequestTypes(List.of(
                            new SupportedRequestType(RequestType.IMMEDIATE,
                                    OperationsDTO.of(Set.of(Operation.QUERY, Operation.CREATE, Operation.UPDATE, Operation.DELETE)))
                    ))
                    .build();
            providerRegistrationRepository.save(namespacesProvider);
            log.info("Registered namespaces provider in environment-global zone for solution: {}", solutionId);
        }
    }
    
    /**
     * Retrieves a solution by its UUID, with caching for the auth token validation hot path.
     * Used by AuthenticationService to avoid repeated database lookups during token validation.
     *
     * @param id the solution UUID
     * @return Optional containing the Solution entity if found, empty otherwise
     */
    @Cacheable(value = "auth-solution", key = "#id", unless = "#result == null")
    public Optional<Solution> findByIdCached(UUID id) {
        return solutionRepository.findById(id);
    }

    /**
     * Retrieves a solution by its solution ID.
     * 
     * @param solutionId the solution ID to retrieve
     * @return SolutionDTO containing the solution information
     * @throws SolutionNotFoundException if the solution does not exist
     */
    public SolutionDTO getSolution(String domainId, String solutionId) {
        log.debug("Retrieving solution with ID: {}", solutionId);

        Solution solution = resolveSolution(domainId, solutionId);

        return mapToSolutionDTO(solution, domainId);
    }

    /**
     * Resolves a solution scoped to its owning domain. {@code solutionId} is unique only
     * within a domain, so the lookup is scoped by {@code domainId} to avoid cross-tenant
     * resolution.
     */
    private Solution resolveSolution(String domainId, String solutionId) {
        UUID domainUuid = domainRepository.findByDomainId(domainId)
            .map(Domain::getId)
            .orElseThrow(() -> new SolutionNotFoundException(solutionId));
        return solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
            .orElseThrow(() -> new SolutionNotFoundException(solutionId));
    }
    
    /**
     * Retrieves all solutions in the system.
     * Batch-fetches domains to resolve domainUuid → domainId efficiently.
     * 
     * @return List of SolutionDTO objects representing all solutions
     */
    public List<SolutionDTO> listSolutions() {
        log.debug("Retrieving all solutions");
        
        List<Solution> solutions = solutionRepository.findAll();
        log.debug("Found {} solutions", solutions.size());
        
        // Batch-fetch all domains to build UUID → domainId map for efficient resolution
        Map<UUID, String> domainUuidToIdMap = domainRepository.findAll().stream()
            .collect(Collectors.toMap(Domain::getId, Domain::getDomainId));
        
        return solutions.stream()
            .map(solution -> {
                String domainId = domainUuidToIdMap.get(solution.getDomainUuid());
                return mapToSolutionDTO(solution, domainId);
            })
            .toList();
    }
    
    /**
     * Retrieves all solutions within a specific domain.
     * 
     * @param domainId the domain ID to filter by
     * @return List of SolutionDTO objects in the domain
     * @throws DomainNotFoundException if the domain does not exist
     */
    public List<SolutionDTO> listSolutionsByDomain(String domainId) {
        log.debug("Retrieving solutions for domain: {}", domainId);
        
        Domain domain = domainRepository.findByDomainId(domainId)
            .orElseThrow(() -> new DomainNotFoundException(domainId));
        
        List<Solution> solutions = solutionRepository.findByDomainUuid(domain.getId());
        log.debug("Found {} solutions in domain: {}", solutions.size(), domainId);
        
        return solutions.stream()
            .map(solution -> mapToSolutionDTO(solution, domainId))
            .toList();
    }
    
    /**
     * Updates a solution's description.
     * 
     * @param solutionId the solution ID to update
     * @param request the update request containing new description
     * @return SolutionDTO containing the updated solution information
     * @throws SolutionNotFoundException if the solution does not exist
     */
    @Transactional
    public SolutionDTO updateSolution(String domainId, String solutionId, org.sifunity.broker.environment.dto.SolutionUpdateRequest request) {
        log.debug("Updating solution with ID: {}", solutionId);

        Solution solution = resolveSolution(domainId, solutionId);

        solution.setDescription(request.description());
        Solution updatedSolution = solutionRepository.save(solution);

        log.info("Successfully updated solution with ID: {}", solutionId);

        return mapToSolutionDTO(updatedSolution, domainId);
    }
    
    /**
     * Deletes a solution by its solution ID.
     * Database ON DELETE CASCADE handles deletion of zones, contexts, and environments.
     * Entity tags are deleted at the application level (not FK-cascaded).
     * Programmatically evicts the auth-solution cache by solutionUuid before deletion.
     * 
     * @param solutionId the solution ID to delete
     * @throws SolutionNotFoundException if the solution does not exist
     */
    @Transactional
    public void deleteSolution(String domainId, String solutionId) {
        log.debug("Deleting solution with ID: {}", solutionId);

        Solution solution = resolveSolution(domainId, solutionId);
        
        // Evict auth-solution cache by solutionUuid
        var authSolutionCache = cacheManager.getCache("auth-solution");
        if (authSolutionCache != null) {
            authSolutionCache.evict(solution.getId());
        }
        
        // Delete entity tags at application level (not covered by DB FK cascade)
        entityTagRepository.findBySolutionId(solution.getId()).forEach(entityTagRepository::delete);
        
        // DB ON DELETE CASCADE handles zones, contexts, and environments
        solutionRepository.delete(solution);
        log.info("Successfully deleted solution with ID: {}", solutionId);

        // Trigger async cascade cleanup (fire-and-forget)
        try {
            cascadeCleanupClient.triggerSolutionCleanup(domainId, solutionId);
        } catch (Exception e) {
            log.warn("Failed to trigger cascade cleanup for solution={}: {}", solutionId, e.getMessage());
        }
    }
    
    /**
     * Checks if a solution with the specified solution ID exists.
     * 
     * @param solutionId the solution ID to check
     * @return true if the solution exists, false otherwise
     */
    public boolean solutionExists(String domainId, String solutionId) {
        return domainRepository.findByDomainId(domainId)
            .map(domain -> solutionRepository.existsByDomainUuidAndSolutionId(domain.getId(), solutionId))
            .orElse(false);
    }
    
    /**
     * Maps a Solution entity to a SolutionDTO, resolving domainUuid back to domainId string.
     * Used when the domainId is not already known (e.g., getSolution, updateSolution).
     */
    private SolutionDTO mapToSolutionDTO(Solution solution) {
        String domainId = domainRepository.findById(solution.getDomainUuid())
            .map(Domain::getDomainId)
            .orElse(null);
        return mapToSolutionDTO(solution, domainId);
    }
    
    /**
     * Maps a Solution entity to a SolutionDTO using a pre-resolved domainId string.
     * Used when the caller already has the domainId (e.g., listSolutionsByDomain, createSolution).
     */
    private SolutionDTO mapToSolutionDTO(Solution solution, String domainId) {
        return new SolutionDTO(
            solution.getId(),
            domainId,
            solution.getSolutionId(),
            solution.getDescription(),
            solution.getCreatedAt()
        );
    }
}
