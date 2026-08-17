// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.common.exception.InvalidParameterException;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for managing environment registration and lifecycle operations.
 *
 * <p>This service handles the core business logic for environment management including:
 * <ul>
 *   <li>Environment registration with credential generation</li>
 *   <li>Environment retrieval and listing (SIF 3.7 response format)</li>
 *   <li>Environment deletion with cascade cleanup</li>
 *   <li>Internal operations for authentication services</li>
 * </ul>
 *
 * @see Environment
 * @see EnvironmentRepository
 * @see EnvironmentRegistrationRequest
 * @see SifEnvironmentRegistrationResponse
 */
@Service
@Transactional
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);

    private final EnvironmentRepository environmentRepository;
    private final SolutionRepository solutionRepository;
    private final DomainRepository domainRepository;
    private final AclEntryRepository aclEntryRepository;
    private final SecretsBackend secretsBackend;
    private final EnvironmentResponseMapper mapper;
    private final CacheManager cacheManager;
    private final QueueServiceClient queueServiceClient;
    private final ZoneRepository zoneRepository;
    private final SecureRandom secureRandom;

    public EnvironmentService(EnvironmentRepository environmentRepository,
                              SolutionRepository solutionRepository,
                              DomainRepository domainRepository,
                              AclEntryRepository aclEntryRepository,
                              SecretsBackend secretsBackend,
                              EnvironmentResponseMapper mapper,
                              CacheManager cacheManager,
                              QueueServiceClient queueServiceClient,
                              ZoneRepository zoneRepository) {
        this.environmentRepository = environmentRepository;
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
        this.aclEntryRepository = aclEntryRepository;
        this.secretsBackend = secretsBackend;
        this.mapper = mapper;
        this.cacheManager = cacheManager;
        this.queueServiceClient = queueServiceClient;
        this.zoneRepository = zoneRepository;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Registers a new environment with the broker.
     *
     * <p>Validates uniqueness, generates credentials (sessionToken, sharedSecret),
     * persists applicationInfo fields, generates fingerprint, sets authenticationMethod
     * to SIF_HMACSHA256, and sets consumerName from applicationName.</p>
     *
     * @param request the environment registration request
     * @return SifEnvironmentRegistrationResponse containing the full SIF 3.7 environment + sharedSecret
     * @throws SolutionNotFoundException if solutionId is provided but does not exist
     */
    public SifEnvironmentRegistrationResponse registerEnvironment(String domainId, EnvironmentRegistrationRequest request) {
        // Resolve solution string ID to UUID, scoped to its owning domain
        UUID solutionUuid = null;
        if (request.environment().solutionId() != null && !request.environment().solutionId().isEmpty()) {
            solutionUuid = resolveSolutionUuid(domainId, request.environment().solutionId());
        } else {
            throw new IllegalArgumentException("solutionId is required for environment registration");
        }

        // Validate defaultZoneId against existing zones in the solution
        validateDefaultZoneId(solutionUuid, request.environment().defaultZoneId());

        // Generate credentials
        String sessionToken = UUID.randomUUID().toString();
        String sharedSecret = generateSharedSecret();
        String fingerprint = UUID.randomUUID().toString();

        // Create Environment entity
        Environment environment = new Environment();
        environment.setApplicationName(request.environment().consumerName());
        environment.setSessionToken(sessionToken);
        environment.setCreatedAt(Instant.now());
        environment.setDefaultZoneId(request.environment().defaultZoneId());
        environment.setType(request.environment().type());
        environment.setSolutionUuid(solutionUuid);
        environment.setSharedSecret("PLACEHOLDER"); // Satisfies NOT NULL; updated by secrets backend

        // SIF 3.7 fields
        environment.setFingerprint(fingerprint);
        environment.setAuthenticationMethod("SIF_HMACSHA256");
        environment.setConsumerName(request.environment().consumerName());

        // Persist applicationInfo fields from request to entity (flattened)
        if (request.environment().applicationInfo() != null) {
            ApplicationInfoDTO appInfo = request.environment().applicationInfo();
            environment.setAppinfoApplicationKey(appInfo.applicationKey());
            environment.setAppinfoSupportedInfraVersion(appInfo.supportedInfrastructureVersion());
            environment.setAppinfoDataModelNamespace(appInfo.dataModelNamespace());
            environment.setAppinfoTransport(appInfo.transport());

            if (appInfo.applicationProduct() != null) {
                ProductIdentityDTO appProduct = appInfo.applicationProduct();
                environment.setAppinfoAppVendorName(appProduct.vendorName());
                environment.setAppinfoAppProductName(appProduct.productName());
                environment.setAppinfoAppProductVersion(appProduct.productVersion());
                environment.setAppinfoAppIconUri(appProduct.iconURI());
            }

            if (appInfo.adapterProduct() != null) {
                ProductIdentityDTO adapterProduct = appInfo.adapterProduct();
                environment.setAppinfoAdapterVendorName(adapterProduct.vendorName());
                environment.setAppinfoAdapterProductName(adapterProduct.productName());
                environment.setAppinfoAdapterProductVersion(adapterProduct.productVersion());
                environment.setAppinfoAdapterIconUri(adapterProduct.iconURI());
            }
        }

        // Persist the environment
        Environment savedEnvironment = environmentRepository.save(environment);

        // Store the shared secret using the secrets backend
        secretsBackend.store(savedEnvironment.getId(), sharedSecret);

        // Return SIF 3.7 registration response via mapper
        return mapper.toRegistrationResponse(savedEnvironment, sharedSecret);
    }

    /**
     * Retrieves an environment by its ID as a full SIF 3.7 response.
     *
     * <p>Loads the entity with ACL entries, ensures fingerprint exists for
     * backward compatibility, and returns the full SIF 3.7 environment object.</p>
     *
     * @param id the unique identifier of the environment
     * @return SifEnvironmentResponse (without sharedSecret)
     * @throws EnvironmentNotFoundException if the environment does not exist
     */
    @Transactional
    public SifEnvironmentResponse getEnvironment(UUID id) {
        Environment environment = environmentRepository.findByIdWithAclEntries(id)
                .orElseThrow(() -> new EnvironmentNotFoundException(id));

        // Backward compat: generate fingerprint if missing
        ensureFingerprint(environment);

        return mapper.toEnvironmentResponse(environment);
    }

    /**
     * Lists environments with optional type filtering and pagination.
     * Returns full SIF 3.7 environment objects with provisionedZones.
     *
     * @param type optional environment type filter (null for all types)
     * @param pageable pagination information
     * @return Page of SifEnvironmentResponse objects
     */
    @Transactional(readOnly = true)
    public Page<SifEnvironmentResponse> listEnvironments(EnvironmentType type, Pageable pageable) {
        Page<Environment> environments;
        if (type != null) {
            environments = environmentRepository.findByType(type, pageable);
        } else {
            environments = environmentRepository.findAll(pageable);
        }

        // Batch-fetch ACL entries for all environments in the page
        List<UUID> environmentIds = environments.getContent().stream()
                .map(Environment::getId)
                .toList();

        Map<UUID, List<AclEntry>> aclEntriesByEnvId = Collections.emptyMap();
        if (!environmentIds.isEmpty()) {
            aclEntriesByEnvId = aclEntryRepository.findByEnvironmentIdIn(environmentIds).stream()
                    .collect(Collectors.groupingBy(acl -> acl.getEnvironment().getId()));
        }

        final Map<UUID, List<AclEntry>> aclMap = aclEntriesByEnvId;
        return environments.map(env ->
                mapper.toEnvironmentResponse(env, aclMap.getOrDefault(env.getId(), List.of()))
        );
    }

    /**
     * Lists environments filtered by solution ID.
     * Returns full SIF 3.7 environment objects.
     *
     * @param solutionId the solution ID to filter by
     * @return List of SifEnvironmentResponse objects
     */
    @Transactional(readOnly = true)
    public List<SifEnvironmentResponse> listEnvironmentsBySolution(String domainId, String solutionId) {
        UUID solutionUuid = resolveSolutionUuid(domainId, solutionId);
        List<Environment> environments = environmentRepository.findBySolutionUuid(solutionUuid);

        // Batch-fetch ACL entries
        List<UUID> environmentIds = environments.stream()
                .map(Environment::getId)
                .toList();

        Map<UUID, List<AclEntry>> aclEntriesByEnvId = Collections.emptyMap();
        if (!environmentIds.isEmpty()) {
            aclEntriesByEnvId = aclEntryRepository.findByEnvironmentIdIn(environmentIds).stream()
                    .collect(Collectors.groupingBy(acl -> acl.getEnvironment().getId()));
        }

        final Map<UUID, List<AclEntry>> aclMap = aclEntriesByEnvId;
        return environments.stream()
                .map(env -> mapper.toEnvironmentResponse(env, aclMap.getOrDefault(env.getId(), List.of())))
                .toList();
    }

    /**
     * Ensures the environment has a fingerprint. For backward compatibility,
     * generates and persists a fingerprint on first retrieval if missing.
     *
     * @param entity the environment entity to check
     */
    private void ensureFingerprint(Environment entity) {
        if (entity.getFingerprint() == null) {
            entity.setFingerprint(UUID.randomUUID().toString());
            entity.setAuthenticationMethod("SIF_HMACSHA256");
            environmentRepository.save(entity);
        }
    }

    /**
     * Updates mutable fields on an existing environment.
     * Only non-null fields in the request are applied.
     *
     * @param id      the environment ID
     * @param request the update request with fields to change
     * @return the updated environment response
     * @throws EnvironmentNotFoundException if the environment does not exist
     */
    @Transactional
    public SifEnvironmentResponse updateEnvironment(UUID id, EnvironmentUpdateRequest request) {
        Environment env = environmentRepository.findByIdWithAclEntries(id)
                .orElseThrow(() -> new EnvironmentNotFoundException(id));

        if (request.consumerName() != null) {
            env.setConsumerName(request.consumerName());
        }
        if (request.defaultZone() != null && request.defaultZone().id() != null) {
            validateDefaultZoneId(env.getSolutionUuid(), request.defaultZone().id());
            env.setDefaultZoneId(request.defaultZone().id());
        }
        if (request.fingerprint() != null && !request.fingerprint().equals(env.getFingerprint())) {
            throw new InvalidParameterException("fingerprint", "Fingerprint is immutable after registration");
        }
        if (request.instanceId() != null) {
            env.setInstanceId(request.instanceId());
        }
        if (request.userToken() != null) {
            env.setUserToken(request.userToken());
        }
        if (request.applicationInfo() != null) {
            ApplicationInfoDTO info = request.applicationInfo();
            if (info.applicationKey() != null) env.setAppinfoApplicationKey(info.applicationKey());
            if (info.supportedInfrastructureVersion() != null) env.setAppinfoSupportedInfraVersion(info.supportedInfrastructureVersion());
            if (info.dataModelNamespace() != null) env.setAppinfoDataModelNamespace(info.dataModelNamespace());
            if (info.transport() != null) env.setAppinfoTransport(info.transport());
            if (info.applicationProduct() != null) {
                if (info.applicationProduct().vendorName() != null) env.setAppinfoAppVendorName(info.applicationProduct().vendorName());
                if (info.applicationProduct().productName() != null) env.setAppinfoAppProductName(info.applicationProduct().productName());
                if (info.applicationProduct().productVersion() != null) env.setAppinfoAppProductVersion(info.applicationProduct().productVersion());
                if (info.applicationProduct().iconURI() != null) env.setAppinfoAppIconUri(info.applicationProduct().iconURI());
            }
            if (info.adapterProduct() != null) {
                if (info.adapterProduct().vendorName() != null) env.setAppinfoAdapterVendorName(info.adapterProduct().vendorName());
                if (info.adapterProduct().productName() != null) env.setAppinfoAdapterProductName(info.adapterProduct().productName());
                if (info.adapterProduct().productVersion() != null) env.setAppinfoAdapterProductVersion(info.adapterProduct().productVersion());
                if (info.adapterProduct().iconURI() != null) env.setAppinfoAdapterIconUri(info.adapterProduct().iconURI());
            }
        }

        environmentRepository.save(env);
        return mapper.toEnvironmentResponse(env);
    }

    /**
     * Deletes an environment and all associated data.
     * Programmatically evicts both auth-environment (by sessionToken) and
     * auth-shared-secret (by environmentId) caches before deletion.
     *
     * @param id the unique identifier of the environment to delete
     * @throws EnvironmentNotFoundException if the environment does not exist
     */
    public void deleteEnvironment(UUID id) {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new EnvironmentNotFoundException(id));

        // Best-effort queue cleanup before deletion
        try {
            String domainId = resolveDomainId(environment.getSolutionUuid());
            String solutionId = resolveSolutionId(environment.getSolutionUuid());
            queueServiceClient.deleteQueuesForEnvironment(environment.getId(), domainId, solutionId);
        } catch (Exception e) {
            log.warn("Queue cleanup failed for environment={}. Proceeding with deletion. Error: {}",
                    id, e.getMessage());
        }

        // Evict auth-environment cache by sessionToken
        var authEnvCache = cacheManager.getCache("auth-environment");
        if (authEnvCache != null) {
            authEnvCache.evict(environment.getSessionToken());
        }

        // Evict auth-shared-secret cache by environmentId
        var authSecretCache = cacheManager.getCache("auth-shared-secret");
        if (authSecretCache != null) {
            authSecretCache.evict(id);
        }

        secretsBackend.delete(id);
        environmentRepository.deleteById(id);
    }

    /**
     * Validates that an environment exists and belongs to the specified domain+solution.
     *
     * <p>Returns normally if the environment exists and its solutionUuid matches
     * the solution identified by the given domainId+solutionId path. Throws
     * {@link EnvironmentNotFoundException} if the environment does not exist or
     * does not belong to the specified domain+solution (to avoid leaking info).</p>
     *
     * @param envId      the environment UUID
     * @param domainId   the human-readable domain ID from the URL path
     * @param solutionId the human-readable solution ID from the URL path
     * @throws EnvironmentNotFoundException if the environment does not exist or does not belong to the domain+solution
     */
    @Transactional(readOnly = true)
    public void validateEnvironmentBelongsToDomainSolution(UUID envId, String domainId, String solutionId) {
        Environment environment = environmentRepository.findById(envId)
                .orElseThrow(() -> new EnvironmentNotFoundException(envId));

        // Resolve solution scoped to its domain from path params
        Domain domain = domainRepository.findByDomainId(domainId).orElse(null);
        if (domain == null) {
            throw new EnvironmentNotFoundException(envId);
        }
        Solution solution = solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), solutionId)
                .orElse(null);
        if (solution == null) {
            throw new EnvironmentNotFoundException(envId);
        }

        // Verify environment belongs to this solution
        if (!environment.getSolutionUuid().equals(solution.getId())) {
            throw new EnvironmentNotFoundException(envId);
        }
    }

    /**
     * Finds an environment by its session token.
     * Internal use only - not exposed through controllers.
     *
     * @param sessionToken the session token to search for
     * @return the Environment entity if found, null otherwise
     */
    @Cacheable(value = "auth-environment", key = "#sessionToken", unless = "#result == null")
    @Transactional(readOnly = true)
    public Environment findBySessionToken(String sessionToken) {
        return environmentRepository.findBySessionToken(sessionToken).orElse(null);
    }

    /**
     * Finds an environment by its UUID, with caching.
     * The key prefix 'uuid:' avoids collisions with {@link #findBySessionToken} entries
     * which are keyed by session token string in the same cache.
     *
     * @param id the environment UUID
     * @return the Environment entity if found, null otherwise
     */
    @Cacheable(value = "auth-environment", key = "'uuid:' + #id", unless = "#result == null")
    @Transactional(readOnly = true)
    public Environment findByUuidCached(UUID id) {
        return environmentRepository.findById(id).orElse(null);
    }

    @CacheEvict(value = {"auth-environment", "auth-shared-secret"}, allEntries = true)
    public CredentialRotationResponse rotateCredentials(UUID id) {
        return rotateCredentials(id, null);
    }

    /**
     * Rotates the credentials (session token and shared secret) for an environment.
     *
     * <p>If a request body is provided with specific values, those are used.
     * Otherwise, new credentials are auto-generated.</p>
     *
     * @param id the environment ID to rotate credentials for
     * @param request optional request with desired credential values
     * @return response containing the new session token and shared secret
     * @throws org.sifunity.broker.environment.exception.EnvironmentNotFoundException if environment not found
     */
    @CacheEvict(value = {"auth-environment", "auth-shared-secret"}, allEntries = true)
    public CredentialRotationResponse rotateCredentials(UUID id, CredentialRotationRequest request) {
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new org.sifunity.broker.environment.exception.EnvironmentNotFoundException(id));

        String oldSessionToken = environment.getSessionToken();

        // Use provided values or generate new ones
        String newSessionToken = (request != null && request.sessionToken() != null)
                ? request.sessionToken()
                : UUID.randomUUID().toString();
        String newSharedSecret = (request != null && request.sharedSecret() != null)
                ? request.sharedSecret()
                : generateSharedSecret();

        // Update session token in the entity
        environment.setSessionToken(newSessionToken);
        environmentRepository.save(environment);

        // Store the new shared secret (overwrites the old encrypted value)
        secretsBackend.store(environment.getId(), newSharedSecret);

        log.info("Credentials rotated for environment: id={}, oldSessionToken={}...{}, newSessionToken={}...{}",
                id,
                oldSessionToken.substring(0, Math.min(4, oldSessionToken.length())), "****",
                newSessionToken.substring(0, Math.min(4, newSessionToken.length())), "****");

        return new CredentialRotationResponse(id, newSessionToken, newSharedSecret, Instant.now());
    }

    /**
     * Retrieves the shared secret for an environment.
     * Internal use only - not exposed through controllers.
     *
     * @param environmentId the unique identifier of the environment
     * @return the shared secret for HMAC validation
     */
    @Cacheable(value = "auth-shared-secret", key = "#environmentId")
    @Transactional(readOnly = true)
    public String getSharedSecret(UUID environmentId) {
        return secretsBackend.retrieve(environmentId);
    }

    /**
     * Generates a cryptographically random shared secret (256 bits, Base64 encoded).
     */
    private String generateSharedSecret() {
        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }

    /**
     * Resolves the human-readable solutionId from a solution UUID.
     *
     * @param solutionUuid the UUID of the solution to look up
     * @return the human-readable solution ID string
     * @throws SolutionNotFoundException if no solution exists with the given UUID
     */
    /**
     * Resolves a solution UUID scoped to its owning domain. {@code solutionId} is unique
     * only within a domain, so the lookup is scoped by {@code domainId} to avoid
     * cross-tenant resolution.
     *
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @return the resolved solution UUID
     * @throws SolutionNotFoundException if the domain or solution does not exist
     */
    private UUID resolveSolutionUuid(String domainId, String solutionId) {
        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(Domain::getId)
                .orElseThrow(() -> new SolutionNotFoundException(solutionId));
        return solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .orElseThrow(() -> new SolutionNotFoundException(solutionId))
                .getId();
    }

    private String resolveSolutionId(UUID solutionUuid) {
        return solutionRepository.findById(solutionUuid)
                .map(Solution::getSolutionId)
                .orElseThrow(() -> new SolutionNotFoundException("UUID: " + solutionUuid));
    }

    /**
     * Resolves the human-readable domainId from a solution UUID
     * (solution → domainUuid → domain → domainId).
     *
     * @param solutionUuid the UUID of the solution whose domain to look up
     * @return the human-readable domain ID string
     * @throws SolutionNotFoundException if no solution exists with the given UUID
     * @throws IllegalStateException if no domain exists for the solution's domainUuid
     */
    private String resolveDomainId(UUID solutionUuid) {
        Solution solution = solutionRepository.findById(solutionUuid)
                .orElseThrow(() -> new SolutionNotFoundException("UUID: " + solutionUuid));
        return domainRepository.findById(solution.getDomainUuid())
                .map(Domain::getDomainId)
                .orElseThrow(() -> new IllegalStateException(
                        "Domain not found for domainUuid=" + solution.getDomainUuid()));
    }

    /**
     * Validates that the given defaultZoneId corresponds to an existing zone in the solution.
     *
     * <p>If defaultZoneId is null or blank, validation is skipped (the field is optional).
     * If non-blank and the zone does not exist in the solution, throws IllegalArgumentException.</p>
     *
     * @param solutionUuid the UUID of the solution to validate against
     * @param defaultZoneId the zone ID to validate
     * @throws IllegalArgumentException if defaultZoneId is non-blank and does not match any zone in the solution
     */
    private void validateDefaultZoneId(UUID solutionUuid, String defaultZoneId) {
        if (defaultZoneId != null && !defaultZoneId.isBlank()) {
            if (!zoneRepository.existsBySolutionUuidAndZoneId(solutionUuid, defaultZoneId)) {
                throw new IllegalArgumentException(
                        "defaultZoneId '" + defaultZoneId + "' does not match any zone in this solution");
            }
        }
    }
}
