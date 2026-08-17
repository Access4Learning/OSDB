// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.util.ValidationUtils;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.ProviderRegistration;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.AvailableProviderOptionDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.exception.ContextNotFoundException;
import org.sifunity.broker.environment.exception.ContextServiceNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateProviderException;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.exception.InvalidProviderEnvironmentException;
import org.sifunity.broker.environment.exception.ProviderRegistrationNotFoundException;
import org.sifunity.broker.environment.exception.ZoneNotFoundException;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Service class for managing provider service registrations.
 * 
 * <p>This service handles the business logic for provider registry management including:
 * <ul>
 *   <li>Registering provider services with validation and duplicate checking</li>
 *   <li>Looking up providers by service name, zone, and context</li>
 *   <li>Listing all provider registrations for an environment</li>
 *   <li>Deleting provider registrations</li>
 * </ul>
 * 
 * <p>Internally, provider registrations reference context_services via contextServiceUuid.
 * The service layer resolves (serviceName, zoneId, contextId) → contextServiceUuid on create,
 * and maps contextServiceUuid → (serviceName, zoneId, contextId) on read, preserving
 * the external API contract.</p>
 * 
 * <p>All provider operations are logged for auditing and monitoring purposes.</p>
 * 
 * @see ProviderRegistration
 * @see ProviderRegistrationRepository
 * @see ProviderRegistrationRequest
 * @see ProviderRegistrationDTO
 */
@Service
@Transactional
public class ProviderService {
    
    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);
    
    private final ProviderRegistrationRepository providerRegistrationRepository;
    private final EnvironmentRepository environmentRepository;
    private final ContextServiceService contextServiceService;
    private final ContextServiceRepository contextServiceRepository;
    private final ContextRepository contextRepository;
    private final ZoneRepository zoneRepository;
    private final SolutionRepository solutionRepository;
    private final DomainRepository domainRepository;
    private final AclEntryRepository aclEntryRepository;
    
    public ProviderService(ProviderRegistrationRepository providerRegistrationRepository, 
                          EnvironmentRepository environmentRepository,
                          ContextServiceService contextServiceService,
                          ContextServiceRepository contextServiceRepository,
                          ContextRepository contextRepository,
                          ZoneRepository zoneRepository,
                          SolutionRepository solutionRepository,
                          DomainRepository domainRepository,
                          AclEntryRepository aclEntryRepository) {
        this.providerRegistrationRepository = providerRegistrationRepository;
        this.environmentRepository = environmentRepository;
        this.contextServiceService = contextServiceService;
        this.contextServiceRepository = contextServiceRepository;
        this.contextRepository = contextRepository;
        this.zoneRepository = zoneRepository;
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
        this.aclEntryRepository = aclEntryRepository;
    }
    
    /**
     * Registers a new provider service.
     * 
     * <p>This method performs the complete provider registration flow:
     * <ol>
     *   <li>Validates that the environment exists and is of type BROKERED</li>
     *   <li>Validates the endpoint URL format</li>
     *   <li>Validates the supported request types</li>
     *   <li>Validates that the context_service exists</li>
     *   <li>Checks for duplicate provider registration (environmentId, contextServiceUuid)</li>
     *   <li>Creates and saves the ProviderRegistration entity</li>
     *   <li>Returns the created provider registration as DTO with consumerName</li>
     * </ol>
     * 
     * @param request the provider registration request containing environmentId, contextServiceUuid,
     *                endpoint, serviceType, and supportedRequestTypes
     * @return ProviderRegistrationDTO representing the created provider registration
     * @throws EnvironmentNotFoundException if the specified environment does not exist
     * @throws InvalidProviderEnvironmentException if the environment is not of type BROKERED
     * @throws ContextServiceNotFoundException if the context_service does not exist or does not
     *         belong to the given domain+solution (404, indistinguishable to avoid leaking existence)
     * @throws DuplicateProviderException if a provider is already registered for the environment+context_service
     * 
     * <p><strong>Requirements:</strong> 2.3, 2.4</p>
     */
    public ProviderRegistrationDTO registerProvider(String domainId, String solutionId, ProviderRegistrationRequest request) {
        // D1: the body arrives wrapped under `provider`.
        ProviderRegistrationBody body = request.provider();

        // D1: endPoint and supportedRequestTypes are nested in the request as they are in the response,
        // so unwrap both once here and work with the flat values below.
        String endpoint = ProtocolDTO.locationOf(body.endPoint());
        List<SupportedRequestType> requestedTypes = body.supportedRequestTypes() == null
                ? null
                : body.supportedRequestTypes().supportedRequestType();

        log.debug("Registering provider for environment: {}, contextServiceUuid: {}, serviceName: {}, zoneId: {}, contextId: {}, endpoint: {}, serviceType: {}, supportedRequestTypes: {}",
                body.environmentId(), body.contextServiceUuid(), body.serviceName(), body.zoneId(),
                body.contextId(), endpoint, body.serviceType(), requestedTypes);

        // Validate environment exists and is type BROKERED (Requirements 1.1, 1.3)
        Environment environment = environmentRepository.findById(body.environmentId())
                .orElseThrow(() -> new EnvironmentNotFoundException(body.environmentId()));

        if (environment.getType() != EnvironmentType.BROKERED) {
            log.warn("Attempted to register provider with non-BROKERED environment: {} (type: {})",
                    body.environmentId(), environment.getType());
            throw new InvalidProviderEnvironmentException(body.environmentId(), environment.getType());
        }

        // Validate endpoint URL format (required for IMMEDIATE providers)
        boolean hasImmediate = requestedTypes != null && requestedTypes.stream()
                .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE);
        if (hasImmediate && (endpoint == null || endpoint.isBlank())) {
            throw new IllegalArgumentException("Endpoint URL is required for providers with IMMEDIATE operations");
        }
        if (endpoint != null && !endpoint.isBlank()) {
            validateEndpointUrl(endpoint);
        }

        // Validate supported request types (Requirements 2.2, 2.5, 7.1, 7.2)
        validateSupportedRequestTypes(requestedTypes);

        // D1: a conforming client identifies the service by (serviceName, zoneId, contextId); internal
        // callers may pass the resolved contextServiceUuid instead. Resolve whichever was supplied.
        UUID contextServiceUuid = resolveContextServiceUuid(domainId, solutionId, body);
        ContextService contextService = contextServiceRepository.findById(contextServiceUuid)
                .orElseThrow(() -> new ContextServiceNotFoundException(contextServiceUuid));

        // Tenant-ownership check: verify contextService belongs to the path's domain+solution.
        // This prevents a caller from registering a provider in another tenant's context.
        // Every failure mode below (broken chain OR cross-tenant mismatch) throws the same
        // ContextServiceNotFoundException (404) so the response is indistinguishable from a
        // genuine not-found — this avoids leaking the existence of another tenant's context service.
        Context context = contextRepository.findById(contextService.getContextUuid()).orElse(null);
        if (context == null) {
            throw new ContextServiceNotFoundException(contextServiceUuid);
        }
        Zone zone = zoneRepository.findById(context.getZoneUuid()).orElse(null);
        if (zone == null) {
            throw new ContextServiceNotFoundException(contextServiceUuid);
        }
        var solution = solutionRepository.findById(zone.getSolutionUuid()).orElse(null);
        if (solution == null || !solution.getSolutionId().equals(solutionId)) {
            throw new ContextServiceNotFoundException(contextServiceUuid);
        }
        var domain = domainRepository.findById(solution.getDomainUuid()).orElse(null);
        if (domain == null || !domain.getDomainId().equals(domainId)) {
            throw new ContextServiceNotFoundException(contextServiceUuid);
        }
        
        // Check for duplicate provider registration (only one provider per context service)
        boolean existsForContextService = providerRegistrationRepository
                .findByContextServiceUuid(contextServiceUuid).isPresent();
        
        if (existsForContextService) {
            log.warn("Attempted to register duplicate provider for contextServiceUuid: {} (provider already exists)",
                    contextServiceUuid);
            throw new DuplicateProviderException(
                    contextService.getServiceName(),
                    resolveZoneIdFromContextService(contextService),
                    resolveContextIdFromContextService(contextService)
            );
        }
        
        // Create and save ProviderRegistration entity with contextServiceUuid
        ProviderRegistration providerRegistration = new ProviderRegistration();
        providerRegistration.setEnvironment(environment);
        providerRegistration.setContextServiceUuid(contextServiceUuid);
        providerRegistration.setEndpoint(endpoint);
        providerRegistration.setServiceType(body.serviceType());
        providerRegistration.setSupportedRequestTypes(requestedTypes);
        
        ProviderRegistration savedProviderRegistration = providerRegistrationRepository.save(providerRegistration);
        
        log.info("Registered provider with ID: {} for environment: {}, contextServiceUuid: {}, endpoint: {}, serviceType: {}",
                savedProviderRegistration.getId(), body.environmentId(),
                contextServiceUuid, endpoint, body.serviceType());
        
        return mapToProviderRegistrationDTO(savedProviderRegistration);
    }
    
    /**
     * Looks up a provider registration by service name, zone, and context.
     * 
     * <p>This method resolves (serviceName, zoneId, contextId) → contextServiceUuid
     * and queries by contextServiceUuid for provider service discovery.</p>
     * 
     * @param serviceName the service name to search for (must not be null or blank)
     * @param zoneId the zone ID to search for (must not be null or blank)
     * @param contextId the context ID to search for (may be null)
     * @return Optional containing the provider registration DTO if found, empty otherwise
     * 
     * <p><strong>Requirements:</strong> 7.5</p>
     */
    @Timed(value = "sif.provider.registry.lookup",
           description = "Provider registry lookup latency",
           percentiles = {0.5, 0.95, 0.99})
    @Transactional(readOnly = true)
    public Optional<ProviderRegistrationDTO> lookupProvider(UUID solutionUuid, String serviceName, String zoneId, String contextId) {
        log.debug("Looking up provider for service: {}, zone: {}, context: {}, solution: {}", serviceName, zoneId, contextId, solutionUuid);
        
        // Validate input parameters
        ValidationUtils.validateServiceName(serviceName);
        ValidationUtils.validateZoneId(zoneId);
        
        // Resolve (solutionUuid, zoneId, contextId) → contextUuid
        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId).orElse(null);
        if (zone == null) {
            log.debug("No zone found for solutionUuid: {}, zoneId: {}", solutionUuid, zoneId);
            return Optional.empty();
        }
        
        Context context = contextRepository.findByZoneUuidAndContextId(zone.getId(), contextId).orElse(null);
        if (context == null) {
            log.debug("No context found for zoneId: {}, contextId: {}", zoneId, contextId);
            return Optional.empty();
        }
        
        // Resolve (contextUuid, serviceName) → contextServiceUuid
        Optional<ContextService> contextService = contextServiceRepository
                .findByContextUuidAndServiceName(context.getId(), serviceName);
        if (contextService.isEmpty()) {
            log.debug("No context service found for service: {}, context: {}", serviceName, context.getId());
            return Optional.empty();
        }
        
        // Query by contextServiceUuid
        Optional<ProviderRegistration> providerRegistration = providerRegistrationRepository
                .findByContextServiceUuid(contextService.get().getId());
        
        if (providerRegistration.isPresent()) {
            log.debug("Found provider registration: {} for service: {}, zone: {}, context: {}",
                    providerRegistration.get().getId(), serviceName, zoneId, contextId);
        } else {
            log.debug("No provider found for service: {}, zone: {}, context: {}", serviceName, zoneId, contextId);
        }
        
        return providerRegistration.map(this::mapToProviderRegistrationDTO);
    }

    /**
     * Looks up a provider registration by service name, zone, context, and optional serviceType.
     *
     * <p>When serviceType is provided, the lookup filters context_services by serviceType
     * in addition to serviceName and contextUuid. This ensures that XQUERYTEMPLATE providers
     * don't collide with OBJECT providers registered on the same serviceName.</p>
     *
     * <p>When serviceType is null, delegates to the existing 3-parameter method for
     * backward compatibility.</p>
     *
     * @param serviceName the service name to search for (required, must not be blank)
     * @param zoneId the zone ID to search within (required, must not be blank)
     * @param contextId the context ID to search within (optional, defaults to "DEFAULT")
     * @param serviceType the service type to filter by (optional, null means no filtering)
     * @return Optional containing the ProviderRegistrationDTO if found, empty otherwise
     *
     * <p><strong>Requirements:</strong> 3.1, 3.5, 7.2</p>
     */
    @Transactional(readOnly = true)
    public Optional<ProviderRegistrationDTO> lookupProvider(UUID solutionUuid, String serviceName, String zoneId, String contextId, String serviceType) {
        if (serviceType == null || serviceType.isBlank()) {
            return lookupProvider(solutionUuid, serviceName, zoneId, contextId);
        }

        log.debug("Looking up provider for service: {}, zone: {}, context: {}, serviceType: {}, solution: {}",
                serviceName, zoneId, contextId, serviceType, solutionUuid);

        // Validate input parameters
        ValidationUtils.validateServiceName(serviceName);
        ValidationUtils.validateZoneId(zoneId);

        // Resolve (solutionUuid, zoneId, contextId) → contextUuid
        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId).orElse(null);
        if (zone == null) {
            log.debug("No zone found for solutionUuid: {}, zoneId: {}", solutionUuid, zoneId);
            return Optional.empty();
        }

        Context context = contextRepository.findByZoneUuidAndContextId(zone.getId(), contextId).orElse(null);
        if (context == null) {
            log.debug("No context found for zoneId: {}, contextId: {}", zoneId, contextId);
            return Optional.empty();
        }

        // Resolve (contextUuid, serviceName, serviceType) → contextServiceUuid
        // First try exact match on serviceType
        Optional<ContextService> contextService = contextServiceRepository
                .findByContextUuidAndServiceNameAndServiceType(context.getId(), serviceName, serviceType);
        
        // If not found, fall back to matching by serviceName only — the ACL system creates
        // context_services with a default serviceType ("OBJECT"), so XQUERYTEMPLATE registrations
        // link to context_services that may not have the exact serviceType stored.
        if (contextService.isEmpty()) {
            contextService = contextServiceRepository
                    .findByContextUuidAndServiceName(context.getId(), serviceName);
        }
        
        if (contextService.isEmpty()) {
            log.debug("No context service found for service: {}, serviceType: {}, context: {}",
                    serviceName, serviceType, context.getId());
            return Optional.empty();
        }

        // Query by contextServiceUuid, then filter by serviceType on the provider_registration
        Optional<ProviderRegistration> providerRegistration = providerRegistrationRepository
                .findByContextServiceUuid(contextService.get().getId())
                .filter(pr -> serviceType.equalsIgnoreCase(pr.getServiceType().name()));

        if (providerRegistration.isPresent()) {
            log.debug("Found provider registration: {} for service: {}, serviceType: {}, zone: {}, context: {}",
                    providerRegistration.get().getId(), serviceName, serviceType, zoneId, contextId);
        } else {
            log.debug("No provider found for service: {}, serviceType: {}, zone: {}, context: {}",
                    serviceName, serviceType, zoneId, contextId);
        }

        return providerRegistration.map(this::mapToProviderRegistrationDTO);
    }

    /**
     * Lists all provider registrations for a specific environment.
     * 
     * <p>Returns all provider registrations associated with the specified environment.
     * Each contextServiceUuid is resolved back to (serviceName, zoneId, contextId)
     * for the external API contract.</p>
     * 
     * @param environmentId the environment ID to retrieve provider registrations for
     * @return List of ProviderRegistrationDTO objects for the environment (empty list if none found)
     * 
     * <p><strong>Requirements:</strong> 6.3, 6.6</p>
     */
    @Transactional(readOnly = true)
    public List<ProviderRegistrationDTO> listProviderRegistrations(UUID environmentId) {
        log.debug("Listing provider registrations for environment: {}", environmentId);
        
        List<ProviderRegistration> providerRegistrations = providerRegistrationRepository.findByEnvironmentId(environmentId);
        
        log.debug("Found {} provider registrations for environment: {}", providerRegistrations.size(), environmentId);
        
        return providerRegistrations.stream()
                .map(this::mapToProviderRegistrationDTO)
                .toList();
    }
    
    /**
     * Returns available provider registration options for an environment.
     *
     * <p>Computes the set difference between PROVIDE/APPROVED ACL entries and existing
     * provider registrations for the environment. Each remaining context_service is
     * resolved to zone/context/service/serviceType for the cascading dropdown UI.</p>
     *
     * @param environmentId the environment ID to compute available options for
     * @return List of AvailableProviderOptionDTO (empty list if no options available)
     *
     * <p><strong>Requirements:</strong> 4.1, 4.2, 4.3</p>
     */
    @Transactional(readOnly = true)
    public List<AvailableProviderOptionDTO> getAvailableOptions(UUID environmentId) {
        log.debug("Getting available provider options for environment: {}", environmentId);
        
        // Find all PROVIDE/APPROVED ACL entries for this environment
        List<AclEntry> provideAcls = aclEntryRepository.findByEnvironmentIdAndAccessTypeAndAccessLevel(
                environmentId, 
                AccessType.PROVIDE, 
                AccessLevel.APPROVED);
        
        if (provideAcls.isEmpty()) {
            log.debug("No PROVIDE/APPROVED ACL entries for environment: {}", environmentId);
            return List.of();
        }
        
        // Get contextServiceUuids from ACL entries
        Set<UUID> aclContextServiceUuids = provideAcls.stream()
                .map(AclEntry::getContextServiceUuid)
                .collect(Collectors.toSet());
        
        // Get contextServiceUuids from existing provider registrations
        List<ProviderRegistration> existingRegistrations = providerRegistrationRepository.findByEnvironmentId(environmentId);
        Set<UUID> registeredContextServiceUuids = existingRegistrations.stream()
                .map(ProviderRegistration::getContextServiceUuid)
                .collect(Collectors.toSet());
        
        // Compute set difference: ACL minus registered
        Set<UUID> availableContextServiceUuids = new HashSet<>(aclContextServiceUuids);
        availableContextServiceUuids.removeAll(registeredContextServiceUuids);
        
        if (availableContextServiceUuids.isEmpty()) {
            log.debug("All PROVIDE ACL entries already have registrations for environment: {}", environmentId);
            return List.of();
        }
        
        // Resolve each contextServiceUuid to zone/context/service/serviceType
        List<AvailableProviderOptionDTO> options = availableContextServiceUuids.stream()
                .map(csUuid -> {
                    ContextService contextService = contextServiceRepository.findById(csUuid).orElse(null);
                    if (contextService == null) {
                        return null;
                    }
                    
                    Context context = contextRepository.findById(contextService.getContextUuid()).orElse(null);
                    if (context == null) {
                        return null;
                    }
                    
                    Zone zone = zoneRepository.findById(context.getZoneUuid()).orElse(null);
                    if (zone == null) {
                        return null;
                    }
                    
                    return new AvailableProviderOptionDTO(
                            csUuid,
                            zone.getZoneId(),
                            context.getContextId(),
                            contextService.getServiceName(),
                            contextService.getServiceType()
                    );
                })
                .filter(option -> option != null)
                .sorted((a, b) -> {
                    int zoneCompare = a.zoneId().compareTo(b.zoneId());
                    if (zoneCompare != 0) return zoneCompare;
                    int contextCompare = a.contextId().compareTo(b.contextId());
                    if (contextCompare != 0) return contextCompare;
                    return a.serviceName().compareTo(b.serviceName());
                })
                .collect(Collectors.toList());
        
        log.debug("Found {} available provider options for environment: {}", options.size(), environmentId);
        
        return options;
    }
    
    /**
     * Updates a provider registration's endpoint and supported request types.
     * 
     * <p>Only the endpoint URL and supportedRequestTypes can be modified after creation.
     * The environment, contextService, and serviceType are immutable.</p>
     * 
     * @param id                    the ID of the provider registration to update
     * @param endpoint              the new endpoint URL (nullable for DELAYED-only providers)
     * @param supportedRequestTypes the new list of supported request types
     * @return ProviderRegistrationDTO representing the updated provider registration
     * @throws ProviderRegistrationNotFoundException if the provider registration does not exist
     * @throws IllegalArgumentException if validation fails
     */
    public ProviderRegistrationDTO updateProvider(String domainId, String solutionId, UUID id, String endpoint, List<SupportedRequestType> supportedRequestTypes) {
        log.debug("Updating provider registration: {}", id);
        
        ProviderRegistration providerRegistration = providerRegistrationRepository.findById(id)
                .orElseThrow(() -> new ProviderRegistrationNotFoundException(id));

        // Tenant-ownership check: verify the provider belongs to the path's domain+solution.
        // ProviderRegistration → contextServiceUuid → ContextService → contextUuid → Context → zoneUuid → Zone → solutionUuid → Solution → domainUuid → Domain
        verifyProviderBelongsToTenant(providerRegistration, domainId, solutionId);
        
        // Validate supported request types
        validateSupportedRequestTypes(supportedRequestTypes);
        
        // Validate endpoint: required if IMMEDIATE operations present
        boolean hasImmediate = supportedRequestTypes.stream()
                .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE);
        if (hasImmediate && (endpoint == null || endpoint.isBlank())) {
            throw new IllegalArgumentException("Endpoint URL is required for providers with IMMEDIATE operations");
        }
        if (endpoint != null && !endpoint.isBlank()) {
            validateEndpointUrl(endpoint);
        }
        
        // Update fields
        providerRegistration.setEndpoint(endpoint);
        providerRegistration.setSupportedRequestTypes(supportedRequestTypes);
        
        ProviderRegistration saved = providerRegistrationRepository.save(providerRegistration);
        
        log.info("Updated provider registration: {} endpoint={}, supportedRequestTypes={}", 
                id, endpoint, supportedRequestTypes);
        
        return mapToProviderRegistrationDTO(saved);
    }

    /**
     * Verifies a provider registration belongs to the specified domain and solution.
     * Walks the chain: provider → context_service → context → zone → solution → domain.
     * Throws ProviderRegistrationNotFoundException (404) if the chain doesn't resolve
     * under the given tenant — this prevents leaking existence of cross-tenant resources.
     */
    private void verifyProviderBelongsToTenant(ProviderRegistration provider, String domainId, String solutionId) {
        UUID contextServiceUuid = provider.getContextServiceUuid();
        var contextService = contextServiceRepository.findById(contextServiceUuid).orElse(null);
        if (contextService == null) {
            throw new ProviderRegistrationNotFoundException(provider.getId());
        }
        var context = contextRepository.findById(contextService.getContextUuid()).orElse(null);
        if (context == null) {
            throw new ProviderRegistrationNotFoundException(provider.getId());
        }
        var zone = zoneRepository.findById(context.getZoneUuid()).orElse(null);
        if (zone == null) {
            throw new ProviderRegistrationNotFoundException(provider.getId());
        }
        var solution = solutionRepository.findById(zone.getSolutionUuid()).orElse(null);
        if (solution == null) {
            throw new ProviderRegistrationNotFoundException(provider.getId());
        }
        var domain = domainRepository.findById(solution.getDomainUuid()).orElse(null);
        if (domain == null || !domain.getDomainId().equals(domainId)) {
            throw new ProviderRegistrationNotFoundException(provider.getId());
        }
        if (!solution.getSolutionId().equals(solutionId)) {
            throw new ProviderRegistrationNotFoundException(provider.getId());
        }
    }
    
    /**
     * Deletes a provider registration.
     * 
     * <p>Removes the specified provider registration from the system. This operation
     * cannot be undone and will make the service unavailable for routing.</p>
     * 
     * @param id the ID of the provider registration to delete
     * @throws ProviderRegistrationNotFoundException if the provider registration does not exist
     * 
     * <p><strong>Requirements:</strong> 5.8</p>
     */
    public void deleteProviderRegistration(String domainId, String solutionId, UUID id) {
        log.debug("Deleting provider registration: {}", id);
        
        ProviderRegistration providerRegistration = providerRegistrationRepository.findById(id)
                .orElseThrow(() -> new ProviderRegistrationNotFoundException(id));

        // Tenant-ownership check: verify the provider belongs to the path's domain+solution
        verifyProviderBelongsToTenant(providerRegistration, domainId, solutionId);
        
        log.info("Deleting provider registration: {} with contextServiceUuid: {}",
                id, providerRegistration.getContextServiceUuid());
        
        providerRegistrationRepository.delete(providerRegistration);
        
        log.info("Deleted provider registration: {}", id);
    }
    
    /**
     * Resolves (zoneId, contextId) → contextUuid.
     * 
     * <p>Finds the Zone by zoneId, then finds the Context by zoneUuid and contextId.</p>
     * 
     * @param zoneId the zone ID string
     * @param contextId the context ID string
     * @return the UUID of the resolved context
     * @throws ZoneNotFoundException if the zone does not exist
     * @throws ContextNotFoundException if the context does not exist in the zone
     */
    private UUID resolveContextUuid(UUID solutionUuid, String zoneId, String contextId) {
        // Find zone by solutionUuid and zoneId (tenant-isolated)
        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId)
                .orElseThrow(() -> new ZoneNotFoundException(zoneId));
        
        // Find context by zoneUuid and contextId
        Context context = contextRepository.findByZoneUuidAndContextId(zone.getId(), contextId)
                .orElseThrow(() -> new ContextNotFoundException(zone.getId()));
        
        return context.getId();
    }
    
    /**
     * Resolves which {@code context_service} row a registration request refers to (D1).
     *
     * <p>Accepts either identification form documented on {@link ProviderRegistrationBody}:</p>
     * <ul>
     *   <li>{@code contextServiceUuid} — used directly. Internal callers already hold it.</li>
     *   <li>the {@code (serviceName, zoneId, contextId)} triple — resolved
     *       {@code solution → zone → context → context_service}. This is the only form an external
     *       client can use, since the UUID is an OSDB-internal key.</li>
     * </ul>
     *
     * <p>An unresolvable triple is a <b>400</b>, not a 404: the client supplied values that do not name
     * anything in this solution, which is a bad request rather than a missing resource. That also keeps
     * it distinct from {@link ContextServiceNotFoundException}, which the caller uses for the
     * cross-tenant case and which deliberately reads as 404 so it cannot confirm another tenant's data.</p>
     *
     * @param domainId   the domain from the request path
     * @param solutionId the solution from the request path
     * @param body       the registration body
     * @return the resolved context_service UUID
     * @throws IllegalArgumentException when neither form is usable, or the triple names nothing
     */
    private UUID resolveContextServiceUuid(String domainId, String solutionId, ProviderRegistrationBody body) {
        if (body.contextServiceUuid() != null) {
            return body.contextServiceUuid();
        }

        // Bean Validation's @AssertTrue already rejects a body with neither form, but this method is
        // also reachable from internal callers that bypass validation.
        if (body.serviceName() == null || body.zoneId() == null || body.contextId() == null) {
            throw new IllegalArgumentException(
                    "Provide either contextServiceUuid, or all of serviceName, zoneId and contextId");
        }

        var domain = domainRepository.findByDomainId(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown domain: " + domainId));
        var solution = solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), solutionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown solution: " + solutionId));

        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), body.zoneId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No zone '" + body.zoneId() + "' in solution '" + solutionId + "'"));
        Context context = contextRepository.findByZoneUuidAndContextId(zone.getId(), body.contextId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No context '" + body.contextId() + "' in zone '" + body.zoneId() + "'"));
        ContextService contextService = contextServiceRepository
                .findByContextUuidAndServiceName(context.getId(), body.serviceName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No service '" + body.serviceName() + "' in zone '" + body.zoneId()
                        + "', context '" + body.contextId() + "'"));

        log.debug("Resolved (serviceName={}, zoneId={}, contextId={}) to contextServiceUuid={}",
                body.serviceName(), body.zoneId(), body.contextId(), contextService.getId());
        return contextService.getId();
    }

    /**
     * Resolves the zone ID string from a ContextService entity by traversing
     * ContextService → Context → Zone.
     *
     * @param contextService the context service to resolve zone for
     * @return the zone ID string, or "unknown" if resolution fails
     */
    private String resolveZoneIdFromContextService(ContextService contextService) {
        return contextRepository.findById(contextService.getContextUuid())
                .flatMap(context -> zoneRepository.findById(context.getZoneUuid()))
                .map(Zone::getZoneId)
                .orElse("unknown");
    }
    
    /**
     * Resolves the context ID string from a ContextService entity by traversing
     * ContextService → Context.
     * 
     * @param contextService the context service to resolve context for
     * @return the context ID string, or "unknown" if resolution fails
     */
    private String resolveContextIdFromContextService(ContextService contextService) {
        return contextRepository.findById(contextService.getContextUuid())
                .map(Context::getContextId)
                .orElse("unknown");
    }
    
    /**
     * Validates that an endpoint URL is properly formatted.
     * 
     * <p>The endpoint must be a valid URL with either HTTP or HTTPS protocol.
     * This validation ensures that the endpoint can be used for routing requests.</p>
     * 
     * @param endpoint the endpoint URL to validate
     * @throws IllegalArgumentException if the endpoint is not a valid URL
     */
    private void validateEndpointUrl(String endpoint) {
        try {
            URL url = new URL(endpoint);
            String protocol = url.getProtocol().toLowerCase();
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                throw new IllegalArgumentException("Endpoint must use HTTP or HTTPS protocol");
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid endpoint URL format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validates the structured supported request types.
     * 
     * <p>Validates that:
     * <ul>
     *   <li>The list is not empty (Req 2.2)</li>
     *   <li>No duplicate {@code requestType} values across entries (Req 7.1)</li>
     *   <li>Each entry has at least one operation (Req 2.5)</li>
     *   <li>No duplicate operations within a single entry (Req 7.2)</li>
     * </ul>
     * 
     * @param supportedRequestTypes the list of supported request types to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateSupportedRequestTypes(List<SupportedRequestType> supportedRequestTypes) {
        if (supportedRequestTypes == null || supportedRequestTypes.isEmpty()) {
            throw new IllegalArgumentException("At least one supported request type is required");
        }
        
        Set<RequestType> seenRequestTypes = new HashSet<>();
        for (SupportedRequestType entry : supportedRequestTypes) {
            // Check for duplicate requestType values across entries (Req 7.1)
            if (!seenRequestTypes.add(entry.requestType())) {
                throw new IllegalArgumentException(
                        "Duplicate request type: " + entry.requestType() + ". Each request type can appear at most once");
            }
            
            // D1 (2.13): operations is nested — an OperationsDTO carrying an `operation` array —
            // so unwrap one level before validating.
            List<Operation> entryOperations =
                    entry.operations() == null ? null : entry.operations().operation();

            // Check each entry has at least one operation (Req 2.5)
            if (entryOperations == null || entryOperations.isEmpty()) {
                throw new IllegalArgumentException("Each supported request type must have at least one operation");
            }

            // Check for duplicate operations within a single entry (Req 7.2)
            Set<Operation> seenOperations = new HashSet<>();
            for (Operation operation : entryOperations) {
                if (!seenOperations.add(operation)) {
                    throw new IllegalArgumentException(
                            "Duplicate operation: " + operation + " in request type " + entry.requestType());
                }
            }
        }
    }
    
    /**
     * Maps a ProviderRegistration entity to ProviderRegistrationDTO.
     * 
     * <p>Resolves contextServiceUuid → (serviceName, zoneId, contextId) by loading
     * the ContextService, Context, and Zone entities. Preserves the external API
     * contract by populating the DTO with original field names.</p>
     * 
     * @param providerRegistration the ProviderRegistration entity to map
     * @return ProviderRegistrationDTO containing the entity data with resolved fields
     * 
     * <p><strong>Requirements:</strong> 6.6</p>
     */
    private ProviderRegistrationDTO mapToProviderRegistrationDTO(ProviderRegistration providerRegistration) {
        // Lookup ContextService → get serviceName, contextUuid
        ContextService contextService = contextServiceRepository
                .findById(providerRegistration.getContextServiceUuid())
                .orElse(null);
        
        String serviceName = null;
        String zoneId = null;
        String contextId = null;
        
        if (contextService != null) {
            serviceName = contextService.getServiceName();
            
            // Lookup Context by contextUuid → get contextId, zoneUuid
            Context context = contextRepository.findById(contextService.getContextUuid()).orElse(null);
            if (context != null) {
                contextId = context.getContextId();
                
                // Lookup Zone by zoneUuid → get zoneId
                Zone zone = zoneRepository.findById(context.getZoneUuid()).orElse(null);
                if (zone != null) {
                    zoneId = zone.getZoneId();
                }
            }
        }
        
        return new ProviderRegistrationDTO(
                providerRegistration.getId(),
                providerRegistration.getEnvironment().getId(),
                serviceName,
                zoneId,
                contextId,
                providerRegistration.getEnvironment().getConsumerName(),
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(providerRegistration.getEndpoint()),
                providerRegistration.getServiceType(),
                SupportedRequestsDTO.of(providerRegistration.getSupportedRequestTypes()),
                providerRegistration.getRegisteredAt()
        );
    }
}
