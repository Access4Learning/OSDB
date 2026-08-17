// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.dto.EnvironmentLookupResponse;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.util.CredentialMasker;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.AnyGrantCheckRequest;
import org.sifunity.broker.environment.dto.PermissionCheckRequest;
import org.sifunity.broker.environment.dto.PermissionCheckResponse;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.dto.TokenValidationResponse;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.service.AuthenticationService;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * REST controller for internal authentication and authorization APIs.
 * 
 * <p>This controller provides internal APIs used exclusively by the API Gateway
 * for validating sessionTokens and checking ACL permissions. These endpoints
 * are NOT exposed through the Gateway and should only be accessible within
 * the trusted internal network.</p>
 * 
 * <p>The controller handles two primary operations:</p>
 * <ul>
 *   <li><strong>Token Validation:</strong> Validates sessionTokens and HMAC signatures
 *       for incoming requests to authenticate environments</li>
 *   <li><strong>Permission Checking:</strong> Checks ACL permissions to authorize
 *       authenticated environments for specific service operations</li>
 * </ul>
 * 
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li>These APIs must only be accessible from the API Gateway</li>
 *   <li>Network policies should restrict access to internal cluster traffic</li>
 *   <li>All operations are logged for security auditing</li>
 *   <li>No authentication is required as these are trusted internal calls</li>
 * </ul>
 * 
 * <p><strong>Error Handling:</strong> All validation errors and authentication
 * failures are handled by the global exception handler and returned as
 * SIF-compliant error responses.</p>
 * 
 * @see AuthenticationService
 * @see AuthorizationService
 * @see TokenValidationRequest
 * @see TokenValidationResponse
 * @see PermissionCheckRequest
 * @see PermissionCheckResponse
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/internal")
public class InternalAuthController {
    
    private static final Logger log = LoggerFactory.getLogger(InternalAuthController.class);
    
    private final AuthenticationService authenticationService;
    private final AuthorizationService authorizationService;
    private final ProviderService providerService;
    private final EnvironmentRepository environmentRepository;
    private final SolutionRepository solutionRepository;
    private final DomainRepository domainRepository;
    private final EnvironmentService environmentService;
    private final ZoneRepository zoneRepository;
    private final ContextRepository contextRepository;
    private final ContextServiceRepository contextServiceRepository;
    private final AclEntryRepository aclEntryRepository;
    
    /**
     * Constructor for dependency injection.
     * 
     * @param authenticationService service for token validation operations
     * @param authorizationService service for permission checking operations
     * @param providerService service for provider lookup operations
     * @param environmentRepository repository for environment lookups
     * @param solutionRepository repository for solution lookups
     * @param environmentService service for environment operations (shared secret retrieval)
     * @param zoneRepository repository for zone lookups
     * @param contextRepository repository for context lookups
     * @param contextServiceRepository repository for context_service lookups
     * @param aclEntryRepository repository for ACL entry lookups
     */
    public InternalAuthController(AuthenticationService authenticationService,
                                 AuthorizationService authorizationService,
                                 ProviderService providerService,
                                 EnvironmentRepository environmentRepository,
                                 SolutionRepository solutionRepository,
                                 EnvironmentService environmentService,
                                 ZoneRepository zoneRepository,
                                 ContextRepository contextRepository,
                                 ContextServiceRepository contextServiceRepository,
                                 AclEntryRepository aclEntryRepository,
                                 DomainRepository domainRepository) {
        this.authenticationService = authenticationService;
        this.authorizationService = authorizationService;
        this.providerService = providerService;
        this.environmentRepository = environmentRepository;
        this.solutionRepository = solutionRepository;
        this.environmentService = environmentService;
        this.zoneRepository = zoneRepository;
        this.contextRepository = contextRepository;
        this.contextServiceRepository = contextServiceRepository;
        this.aclEntryRepository = aclEntryRepository;
        this.domainRepository = domainRepository;
    }
    
    /**
     * Validates a sessionToken and HMAC signature for authentication.
     * 
     * <p>This endpoint is called by the API Gateway to authenticate incoming
     * requests. It validates the sessionToken exists, verifies the HMAC signature
     * using the stored sharedSecret, and checks timestamp freshness.</p>
     * 
     * <p><strong>Request Flow:</strong></p>
     * <ol>
     *   <li>Gateway extracts sessionToken, timestamp, and hash from Authorization header</li>
     *   <li>Gateway calls this endpoint with the extracted values</li>
     *   <li>Service validates the token and returns environment details</li>
     *   <li>Gateway uses the environment details for further processing</li>
     * </ol>
     * 
     * <p><strong>Success Response (200):</strong> Returns environment details including
     * ID, application name, type, and default zone/context for the authenticated environment.</p>
     * 
     * <p><strong>Error Responses:</strong></p>
     * <ul>
     *   <li><strong>400 Bad Request:</strong> Invalid request format or missing required fields</li>
     *   <li><strong>401 Unauthorized:</strong> SessionToken not found, HMAC validation failed,
     *       or timestamp outside tolerance window</li>
     *   <li><strong>500 Internal Server Error:</strong> Unexpected server error</li>
     * </ul>
     * 
     * @param request the token validation request containing sessionToken, timestamp, and hash
     * @return ResponseEntity containing environment details if validation succeeds
     * 
     * <p><strong>Requirements:</strong> 7.1, 8.1</p>
     */
    @PostMapping("/validate-token")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @Valid @RequestBody TokenValidationRequest request) {
        
        log.debug("Received token validation request for sessionToken: {}", CredentialMasker.mask(request.sessionToken()));
        
        // Delegate to AuthenticationService for validation
        TokenValidationResponse response = authenticationService.validateToken(request);
        
        log.debug("Token validation successful for sessionToken: {}, environment: {}", 
                CredentialMasker.mask(request.sessionToken()), response.environmentId());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Checks ACL permissions for an authenticated environment.
     * 
     * <p>This endpoint is called by the API Gateway after successful authentication
     * to check if the environment has permission to access a specific service
     * with the requested access type in the specified zone/context.</p>
     * 
     * <p><strong>Request Flow:</strong></p>
     * <ol>
     *   <li>Gateway authenticates the request using validate-token</li>
     *   <li>Gateway extracts service, zone, context, and access type from the request</li>
     *   <li>Gateway calls this endpoint with the environment ID and access parameters</li>
     *   <li>Service checks ACL and returns permission granted/denied</li>
     *   <li>Gateway allows or rejects the request based on the response</li>
     * </ol>
     * 
     * <p><strong>Success Response (200):</strong> Always returns 200 with a boolean
     * indicating whether permission is granted. A denied permission is not an error
     * condition but a valid authorization decision.</p>
     * 
     * <p><strong>Error Responses:</strong></p>
     * <ul>
     *   <li><strong>400 Bad Request:</strong> Invalid request format or missing required fields</li>
     *   <li><strong>500 Internal Server Error:</strong> Unexpected server error</li>
     * </ul>
     * 
     * <p><strong>Permission Logic:</strong> Permission is granted only if an ACL entry
     * exists with exact matches on all parameters (environment, service, zone, context,
     * access type) and the access level is APPROVED.</p>
     * 
     * @param request the permission check request containing environment ID and access parameters
     * @return ResponseEntity containing permission granted/denied result
     * 
     * <p><strong>Requirements:</strong> 8.1</p>
     */
    @PostMapping("/check-permission")
    public ResponseEntity<PermissionCheckResponse> checkPermission(
            @Valid @RequestBody PermissionCheckRequest request) {
        
        log.debug("Received permission check request for environment: {}, service: {}, zone: {}, access: {}",
                request.environmentId(), request.serviceName(), request.zoneId(), request.accessType());
        
        // Delegate to AuthorizationService for permission checking
        PermissionCheckResponse response = authorizationService.checkPermission(request);
        
        log.debug("Permission check completed for environment: {}, granted: {}",
                request.environmentId(), response.granted());

        return ResponseEntity.ok(response);
    }

    /**
     * Checks whether an environment holds ANY approved ACL grant for a service, regardless of
     * access type (internal API).
     *
     * <p>Distinct from {@code /check-permission}, which requires a specific {@code accessType}.
     * This endpoint answers the weaker question <em>"is this environment entitled to this
     * object's data at all?"</em></p>
     *
     * <p><strong>Why a separate endpoint rather than a nullable accessType:</strong>
     * {@code accessType} is {@code @NotNull} on {@code PermissionCheckRequest}, and relaxing it
     * would weaken validation for every caller of that far hotter path. This is additive, so no
     * existing caller changes.</p>
     *
     * <p><strong>Caller:</strong> {@code responses-connector}, to decide whether the environment
     * owning a caller-supplied destination queue is entitled to the object being delivered. The
     * specific access type cannot be known there — a delayed response goes to the queue of the
     * consumer that issued the original request, and that consumer may hold only {@code CREATE},
     * {@code UPDATE} or {@code DELETE} rather than {@code QUERY}. Requiring a specific type would
     * reject legitimate responses.</p>
     *
     * <p>Note the environment being checked is normally the <em>queue owner</em>, not the caller.
     * Fails closed: any resolution error yields {@code granted: false}.</p>
     *
     * @param request the entitlement check request (environment, service, zone, context)
     * @return ResponseEntity containing the granted/denied result
     */
    @PostMapping("/check-any-permission")
    public ResponseEntity<PermissionCheckResponse> checkAnyPermission(
            @Valid @RequestBody AnyGrantCheckRequest request) {

        log.debug("Received any-grant check for environment: {}, service: {}, zone: {}, context: {}",
                request.environmentId(), request.serviceName(), request.zoneId(), request.contextId());

        PermissionCheckResponse response = authorizationService.hasAnyGrant(request);

        log.debug("Any-grant check completed for environment: {}, granted: {}",
                request.environmentId(), response.granted());

        return ResponseEntity.ok(response);
    }

    /**
     * Looks up a provider registration by service name, zone, context, and optional serviceType (internal API).
     * 
     * <p>This endpoint is called by internal connector services (requests-connector,
     * responses-connector, etc.) to discover provider endpoints for service routing.
     * It provides the same functionality as the public /providers/lookup
     * endpoint but without requiring authentication, as it's only accessible within
     * the trusted internal network.</p>
     * 
     * <p>When serviceType is provided, the lookup filters by serviceType in addition
     * to serviceName/zone/context. This ensures XQUERYTEMPLATE providers don't collide
     * with OBJECT providers on the same serviceName.</p>
     * 
     * <p><strong>Request Flow:</strong></p>
     * <ol>
     *   <li>Connector service receives a request for a specific service</li>
     *   <li>Connector calls this endpoint to find the provider endpoint</li>
     *   <li>Service returns provider registration details if found</li>
     *   <li>Connector forwards the request to the provider endpoint</li>
     * </ol>
     * 
     * <p><strong>Success Response (200):</strong> Returns provider registration details
     * including the endpoint URL and supported message exchange patterns.</p>
     * 
     * <p><strong>Error Responses:</strong></p>
     * <ul>
     *   <li><strong>400 Bad Request:</strong> Invalid service name or zone ID format</li>
     *   <li><strong>404 Not Found:</strong> No provider found for the specified service/zone/context</li>
     *   <li><strong>500 Internal Server Error:</strong> Unexpected server error</li>
     * </ul>
     * 
     * <p><strong>Security Considerations:</strong> This endpoint must only be accessible
     * from internal connector services. Network policies should restrict access to
     * internal cluster traffic.</p>
     * 
     * @param serviceName the service name to search for (required)
     * @param zoneId the zone ID to search for (required)
     * @param contextId the context ID to search for (optional)
     * @param serviceType the service type to filter by (optional — when absent, returns any matching provider)
     * @return ResponseEntity containing the provider registration if found, 404 if not found
     * 
     * <p><strong>Requirements:</strong> 3.1, 3.5, 5.6, 7.2</p>
     */
    @GetMapping("/provider-lookup")
    public ResponseEntity<ProviderRegistrationDTO> lookupProvider(
            @RequestParam String serviceName,
            @RequestParam String zoneId,
            @RequestParam(required = false) String contextId,
            @RequestParam(required = false) String serviceType,
            @RequestParam String domainId,
            @RequestParam String solutionId) {

        log.debug("Internal provider lookup: serviceName={}, zoneId={}, contextId={}, serviceType={}, domainId={}, solutionId={}",
                serviceName, zoneId, contextId, serviceType, domainId, solutionId);

        UUID solutionUuid = resolveSolutionUuid(domainId, solutionId);
        if (solutionUuid == null) {
            log.debug("No solution found for domainId={}, solutionId={}", domainId, solutionId);
            return ResponseEntity.notFound().build();
        }
        
        Optional<ProviderRegistrationDTO> providerRegistration = providerService.lookupProvider(
                solutionUuid, serviceName, zoneId, contextId, serviceType);
        
        if (providerRegistration.isPresent()) {
            log.debug("Provider found: id={}, endpoint={}", 
                    providerRegistration.get().id(), ProtocolDTO.locationOf(providerRegistration.get().endPoint()));
            return ResponseEntity.ok(providerRegistration.get());
        } else {
            log.debug("No provider found for serviceName={}, zoneId={}, contextId={}, serviceType={}", 
                    serviceName, zoneId, contextId, serviceType);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Looks up an environment by fingerprint and solution UUID (internal API).
     *
     * <p>This endpoint is called by the API Gateway for proxy impersonation
     * target resolution. It resolves a fingerprint shorthand (e.g. "broker-services")
     * to a specific environment within a solution.</p>
     *
     * @param fingerprint the environment fingerprint to search for
     * @param domainId the human-readable domain ID that scopes the solution (solutionId is
     *                 unique only within a domain, so this is required for tenant isolation)
     * @param solutionId the human-readable solution ID (e.g. "production") to scope the search to
     * @return 200 with EnvironmentLookupResponse if found, 404 if not
     */
    @GetMapping("/environments/by-fingerprint")
    public ResponseEntity<EnvironmentLookupResponse> lookupEnvironmentByFingerprint(
            @RequestParam String fingerprint,
            @RequestParam String domainId,
            @RequestParam String solutionId) {

        log.debug("Internal environment lookup by fingerprint: fingerprint={}, domainId={}, solutionId={}",
                fingerprint, domainId, solutionId);

        // Resolve solutionId scoped by domainId (solutionId is unique only within a domain)
        UUID solutionUuid = resolveSolutionUuid(domainId, solutionId);
        if (solutionUuid == null) {
            log.debug("No solution found for domainId={}, solutionId={}", domainId, solutionId);
            return ResponseEntity.notFound().build();
        }
        
        Optional<Environment> environment = environmentRepository.findByFingerprintAndSolutionUuid(
                fingerprint, solutionUuid);
        
        if (environment.isPresent()) {
            Environment env = environment.get();
            EnvironmentLookupResponse response = new EnvironmentLookupResponse(
                    env.getId(), env.getApplicationName(), env.getFingerprint(), env.getSolutionUuid());
            log.debug("Environment found: id={}, applicationName={}", env.getId(), env.getApplicationName());
            return ResponseEntity.ok(response);
        } else {
            log.debug("No environment found for fingerprint={}, solutionId={}", fingerprint, solutionId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Looks up an environment by its UUID (internal API).
     *
     * <p>This endpoint is called by the API Gateway for proxy impersonation
     * target resolution when the admin specifies a UUID directly.</p>
     *
     * @param id the environment UUID
     * @return 200 with EnvironmentLookupResponse if found, 404 if not
     */
    @GetMapping("/environments/{id}")
    public ResponseEntity<EnvironmentLookupResponse> lookupEnvironmentById(
            @PathVariable UUID id) {
        
        log.debug("Internal environment lookup by id: id={}", id);
        
        Optional<Environment> environment = environmentRepository.findById(id);
        
        if (environment.isPresent()) {
            Environment env = environment.get();
            EnvironmentLookupResponse response = new EnvironmentLookupResponse(
                    env.getId(), env.getApplicationName(), env.getFingerprint(), env.getSolutionUuid());
            log.debug("Environment found: id={}, applicationName={}", env.getId(), env.getApplicationName());
            return ResponseEntity.ok(response);
        } else {
            log.debug("No environment found for id={}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Resolves a (domainId, solutionId) pair to the solution's UUID, scoped by domain.
     *
     * <p>solutionId is unique only within a domain, so both identifiers are required to
     * isolate tenants correctly. Resolving by solutionId alone can match another domain's
     * solution (wrong-tenant data) or throw when two domains share a solutionId.</p>
     *
     * @return the solution UUID, or null if either id is blank or no matching solution exists
     */
    private UUID resolveSolutionUuid(String domainId, String solutionId) {
        if (domainId == null || domainId.isBlank() || solutionId == null || solutionId.isBlank()) {
            return null;
        }
        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(domain -> domain.getId())
                .orElse(null);
        if (domainUuid == null) {
            return null;
        }
        return solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .map(solution -> solution.getId())
                .orElse(null);
    }

    /**
     * Retrieves the session token for an environment.
     *
     * <p>Internal endpoint for service-to-service calls. The requests-connector
     * uses this endpoint to retrieve the session token for HMAC signature
     * computation when signing outbound provider requests.</p>
     *
     * <p><strong>Security:</strong> This endpoint is internal-only and must NOT be
     * exposed through the API Gateway. Network policies should restrict access
     * to trusted internal services only.</p>
     *
     * <p><strong>Long-term direction:</strong> The preferred architecture is to replace
     * raw credential retrieval with a {@code POST /internal/sign} endpoint where callers
     * send the data to sign and environment-service performs the HMAC signing internally,
     * returning only the computed signature. This keeps secrets from ever leaving the
     * environment-service boundary. This is non-blocking for the current implementation
     * but should be the target architecture.</p>
     *
     * @param id the environment UUID
     * @return 200 with the plaintext session token, or 404 if not found/blank
     * @see <a href=".kiro/specs/service-to-service-trust-boundary/design.md">Design §2.4</a>
     */
    // TODO: Long-term, replace raw credential retrieval with a /internal/sign endpoint
    // that performs HMAC signing inside environment-service. See design doc:
    // .kiro/specs/service-to-service-trust-boundary/design.md §2.4
    @GetMapping("/environments/{id}/session-token")
    public ResponseEntity<String> getSessionToken(@PathVariable UUID id, HttpServletRequest request) {
        log.debug("Internal session-token retrieval: environmentId={}", id);

        Environment env = environmentRepository.findById(id).orElse(null);
        if (env == null || env.getSessionToken() == null || env.getSessionToken().isBlank()) {
            log.debug("No session token found for environmentId={}", id);
            return ResponseEntity.notFound().build();
        }
        log.info("AUDIT: credential retrieval - operation={}, targetEnvironmentId={}, caller={}",
                "session-token", id,
                request.getHeader("X-Caller-Service") != null ? request.getHeader("X-Caller-Service") : "internal-service");
        return ResponseEntity.ok(env.getSessionToken());
    }

    /**
     * Retrieves the shared secret for an environment.
     *
     * <p>Internal endpoint for service-to-service calls. Extensions that need
     * provider credentials (e.g., for Bearer token authentication) use this
     * endpoint to retrieve the decrypted shared secret.</p>
     *
     * <p><strong>Security:</strong> This endpoint is internal-only and must NOT be
     * exposed through the API Gateway. Network policies should restrict access
     * to trusted internal services only.</p>
     *
     * <p><strong>Long-term direction:</strong> The preferred architecture is to replace
     * raw credential retrieval with a {@code POST /internal/sign} endpoint where callers
     * send the data to sign and environment-service performs the HMAC signing internally,
     * returning only the computed signature. This keeps secrets from ever leaving the
     * environment-service boundary. This is non-blocking for the current implementation
     * but should be the target architecture.</p>
     *
     * @param id the environment UUID
     * @return 200 with the plaintext shared secret, or 404 if not found
     * @see <a href=".kiro/specs/service-to-service-trust-boundary/design.md">Design §2.4</a>
     */
    // TODO: Long-term, replace raw credential retrieval with a /internal/sign endpoint
    // that performs HMAC signing inside environment-service. See design doc:
    // .kiro/specs/service-to-service-trust-boundary/design.md §2.4
    @GetMapping("/environments/{id}/shared-secret")
    public ResponseEntity<String> getSharedSecret(@PathVariable UUID id, HttpServletRequest request) {
        log.debug("Internal shared-secret retrieval: environmentId={}", id);

        try {
            String secret = environmentService.getSharedSecret(id);
            if (secret == null || secret.isBlank()) {
                log.debug("No shared secret found for environmentId={}", id);
                return ResponseEntity.notFound().build();
            }
            log.info("AUDIT: credential retrieval - operation={}, targetEnvironmentId={}, caller={}",
                    "shared-secret", id,
                    request.getHeader("X-Caller-Service") != null ? request.getHeader("X-Caller-Service") : "internal-service");
            return ResponseEntity.ok(secret);
        } catch (Exception e) {
            log.error("Failed to retrieve shared secret for environmentId={}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Checks if a PROVIDE ACL entry exists for a given service/zone/context.
     *
     * <p>This endpoint is called by the services-connector DELAYED routing flow
     * as a fallback when no ProviderRegistration exists. Per SIF 3.7, DELAYED
     * requests only require a PROVIDE ACL to exist — the provider may not have
     * registered yet or may only receive messages via queue subscription.</p>
     *
     * <p><strong>Resolution Flow:</strong></p>
     * <ol>
     *   <li>Resolve zoneId → Zone entity</li>
     *   <li>Resolve contextId within zone → Context entity</li>
     *   <li>Resolve (contextUuid, serviceName) → ContextService entity</li>
     *   <li>Check if an ACL entry exists with accessType=PROVIDE, accessLevel=APPROVED for that contextServiceUuid</li>
     * </ol>
     *
     * <p><strong>Security:</strong> This endpoint is internal-only and must NOT be
     * exposed through the API Gateway. Network policies should restrict access
     * to trusted internal services only.</p>
     *
     * @param serviceName the service name to check (required)
     * @param zoneId the zone ID to check (required)
     * @param contextId the context ID to check (optional, defaults to "DEFAULT")
     * @return 200 with JSON {"exists": true/false}
     */
    @GetMapping("/provide-acl-check")
    public ResponseEntity<?> checkProvideAcl(
            @RequestParam String serviceName,
            @RequestParam String zoneId,
            @RequestParam(required = false, defaultValue = "DEFAULT") String contextId,
            @RequestParam String domainId,
            @RequestParam String solutionId) {

        log.debug("Internal PROVIDE ACL check: serviceName={}, zoneId={}, contextId={}, domainId={}, solutionId={}",
                serviceName, zoneId, contextId, domainId, solutionId);

        // Resolve (domainId, solutionId) → solutionUuid (solutionId is unique only within a domain)
        UUID solutionUuid = resolveSolutionUuid(domainId, solutionId);
        if (solutionUuid == null) {
            log.debug("No solution found for domainId={}, solutionId={}, returning exists=false", domainId, solutionId);
            return ResponseEntity.ok(Map.of("exists", false));
        }

        // Resolve zoneId → Zone entity (solution-scoped)
        Optional<Zone> zone = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId);
        if (zone.isEmpty()) {
            log.debug("No zone found for solutionUuid={}, zoneId={}, returning exists=false", solutionUuid, zoneId);
            return ResponseEntity.ok(Map.of("exists", false));
        }

        // Resolve contextId within zone → Context entity
        Optional<Context> context = contextRepository.findByZoneUuidAndContextId(
                zone.get().getId(), contextId);
        if (context.isEmpty()) {
            log.debug("No context found for zoneId={}, contextId={}, returning exists=false", zoneId, contextId);
            return ResponseEntity.ok(Map.of("exists", false));
        }

        // Resolve (contextUuid, serviceName) → ContextService entity
        Optional<ContextService> contextService = contextServiceRepository
                .findByContextUuidAndServiceName(context.get().getId(), serviceName);
        if (contextService.isEmpty()) {
            log.debug("No context_service found for serviceName={}, contextUuid={}, returning exists=false",
                    serviceName, context.get().getId());
            return ResponseEntity.ok(Map.of("exists", false));
        }

        // Check if PROVIDE ACL with APPROVED level exists for this context_service
        // Also find the actual entry to get the provider's environmentId for queue routing
        Optional<AclEntry> provideAcl = aclEntryRepository.findFirstByContextServiceUuidAndAccessTypeAndAccessLevel(
                contextService.get().getId(), AccessType.PROVIDE, AccessLevel.APPROVED);

        if (provideAcl.isPresent()) {
            UUID providerEnvironmentId = provideAcl.get().getEnvironment().getId();
            log.debug("PROVIDE ACL found: serviceName={}, zoneId={}, contextId={}, providerEnvironmentId={}",
                    serviceName, zoneId, contextId, providerEnvironmentId);

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("exists", true);
            result.put("providerEnvironmentId", providerEnvironmentId.toString());
            return ResponseEntity.ok(result);
        }

        log.debug("PROVIDE ACL check result: serviceName={}, zoneId={}, contextId={}, exists=false",
                serviceName, zoneId, contextId);

        return ResponseEntity.ok(Map.of("exists", false));
    }

    /**
     * Resolves a (domainId, solutionId, zoneId, contextId) tuple to the context's internal UUID.
     *
     * <p>This endpoint is called by connectors (events, requests, services) that need
     * a context UUID for subscription queries. The public zone/context listing endpoints
     * no longer expose internal UUIDs (SIF spec compliance), so connectors use this
     * internal endpoint instead.</p>
     *
     * @param domainId   the domain identifier string
     * @param solutionId the solution identifier string
     * @param zoneId     the zone identifier string
     * @param contextId  the context identifier string (defaults to "DEFAULT")
     * @return 200 with JSON {"contextUuid": "..."}, or 404 if not resolvable
     */
    @GetMapping("/contexts/resolve")
    public ResponseEntity<?> resolveContext(
            @RequestParam String domainId,
            @RequestParam String solutionId,
            @RequestParam String zoneId,
            @RequestParam(required = false, defaultValue = "DEFAULT") String contextId) {

        log.debug("Internal context resolve: domainId={}, solutionId={}, zoneId={}, contextId={}",
                domainId, solutionId, zoneId, contextId);

        UUID solutionUuid = resolveSolutionUuid(domainId, solutionId);
        if (solutionUuid == null) {
            log.debug("No solution found for domainId={}, solutionId={}", domainId, solutionId);
            return ResponseEntity.notFound().build();
        }

        Optional<Zone> zone = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId);
        if (zone.isEmpty()) {
            log.debug("No zone found for solutionUuid={}, zoneId={}", solutionUuid, zoneId);
            return ResponseEntity.notFound().build();
        }

        Optional<Context> context = contextRepository.findByZoneUuidAndContextId(zone.get().getId(), contextId);
        if (context.isEmpty()) {
            log.debug("No context found for zoneId={}, contextId={}", zoneId, contextId);
            return ResponseEntity.notFound().build();
        }

        log.debug("Resolved context: domainId={}, solutionId={}, zoneId={}, contextId={} → uuid={}",
                domainId, solutionId, zoneId, contextId, context.get().getId());

        return ResponseEntity.ok(Map.of("contextUuid", context.get().getId().toString()));
    }
}