// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.AnyGrantCheckRequest;
import org.sifunity.broker.environment.dto.PermissionCheckRequest;
import org.sifunity.broker.environment.dto.PermissionCheckResponse;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for determining authorization permissions based on environment type and resource ownership.
 * 
 * <p>This service implements the authorization rules for the SIF Unity Broker:</p>
 * <ul>
 *   <li>ADMIN environments have full permissions to all operations</li>
 *   <li>Regular environments (DIRECT, BROKERED) can only access their own data</li>
 *   <li>Only ADMIN can create environments, ACLs, or delete any resource</li>
 * </ul>
 * 
 * <p><strong>Requirements:</strong> 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11, 
 * 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8</p>
 */
@Service
public class AuthorizationService {
    
    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);
    
    private final AclEntryRepository aclEntryRepository;
    private final EnvironmentRepository environmentRepository;
    private final SolutionService solutionService;
    private final DomainService domainService;
    private final ContextRepository contextRepository;
    private final ContextServiceRepository contextServiceRepository;
    private final ZoneRepository zoneRepository;
    
    public AuthorizationService(AclEntryRepository aclEntryRepository, 
                               EnvironmentRepository environmentRepository,
                               SolutionService solutionService,
                               DomainService domainService,
                               ContextRepository contextRepository,
                               ContextServiceRepository contextServiceRepository,
                               ZoneRepository zoneRepository) {
        this.aclEntryRepository = aclEntryRepository;
        this.environmentRepository = environmentRepository;
        this.solutionService = solutionService;
        this.domainService = domainService;
        this.contextRepository = contextRepository;
        this.contextServiceRepository = contextServiceRepository;
        this.zoneRepository = zoneRepository;
    }
    
    /**
     * Checks if an environment has permission to access a service based on ACL entries.
     * 
     * <p>This method resolves the zoneId/contextId strings from the gateway's
     * PermissionCheckRequest to a context_uuid before querying the ACL entries.
     * If contextId is null, queries with contextUuid = null (wildcard).
     * If contextId is non-null but no matching context is found, permission is denied.</p>
     * 
     * @param request the permission check request containing environment ID, service name, 
     *                zone, context, and access type
     * @return a response indicating whether permission is granted
     */
    @Cacheable(value = "acl-permissions",
               key = "T(org.sifunity.broker.common.util.PermissionCacheKey).of("
                   + "#request.environmentId(), #request.zoneId(), #request.contextId(), "
                   + "#request.serviceName(), #request.accessType())")
    public PermissionCheckResponse checkPermission(PermissionCheckRequest request) {
        try {
            AclScope scope = resolveAclScope(
                    request.environmentId(), request.serviceName(), request.zoneId(), request.contextId());
            if (scope.decided()) {
                return new PermissionCheckResponse(scope.granted());
            }

            // Query ACL entry using resolved contextServiceUuid
            Optional<AclEntry> aclEntry = aclEntryRepository.findByEnvironmentIdAndContextServiceUuidAndAccessType(
                request.environmentId(),
                scope.contextServiceUuid(),
                request.accessType()
            );

            // Check if entry exists and is APPROVED
            boolean granted = aclEntry.isPresent() &&
                             aclEntry.get().getAccessLevel() == AccessLevel.APPROVED;

            return new PermissionCheckResponse(granted);
        } catch (Exception e) {
            // On any error, deny permission for security
            return new PermissionCheckResponse(false);
        }
    }

    /**
     * Checks whether an environment holds ANY approved ACL grant for a service in a
     * zone/context, regardless of access type.
     *
     * <p>Used by the response-destination entitlement check in {@code responses-connector}: a
     * delayed response is delivered to the queue of the consumer that issued the original
     * request, and the broker cannot know which access type that consumer used. A consumer that
     * issued a create/update/delete holds {@code CREATE}/{@code UPDATE}/{@code DELETE} and may
     * hold no {@code QUERY} grant at all, so requiring a specific type would reject legitimate
     * responses. The question this answers is the weaker but correct one: <em>is this
     * environment entitled to this object's data at all?</em></p>
     *
     * <p>Shares its resolution (environment lookup, admin short-circuit, self-access,
     * zone/context → {@code contextServiceUuid}) with {@link #checkPermission} via
     * {@link #resolveAclScope}, so the two cannot drift. Only the final ACL query differs:
     * any-approved-grant instead of a specific access type.</p>
     *
     * <p>Cached separately from {@code acl-permissions} because the key has no access-type
     * component and must not collide. Both caches are evicted together on every ACL mutation —
     * see the {@code @CacheEvict} annotations in {@code AclService}. That eviction is
     * load-bearing: ACL grants are revocable, unlike queue ownership, so a stale positive here
     * would keep admitting responses after a grant is withdrawn.</p>
     *
     * <p>Fails closed: any error returns not-granted.</p>
     *
     * <p><b>Positives only are cached</b> ({@code unless} below). A negative here can mean either a
     * genuine denial or a transient failure — the fail-closed {@code catch} also returns
     * not-granted — and caching that would turn a momentary environment-service or database blip
     * into a sticky 30-second outage for a provider that is in fact entitled. The cost is that
     * genuine denials re-query each time, which is the right way round: denial is the abnormal path,
     * and a cache miss must mean <em>fetch</em>, never <em>deny</em>.</p>
     *
     * @param request the entitlement request (access type, if present, is ignored)
     * @return a response indicating whether any approved grant exists
     */
    @Cacheable(value = "acl-any-grant",
               key = "'any:' + #request.environmentId() + ':' + #request.zoneId() + ':' "
                   + "+ #request.contextId() + ':' + #request.serviceName()",
               unless = "#result == null || !#result.granted()")
    public PermissionCheckResponse hasAnyGrant(AnyGrantCheckRequest request) {
        try {
            AclScope scope = resolveAclScope(
                    request.environmentId(), request.serviceName(), request.zoneId(), request.contextId());
            if (scope.decided()) {
                return new PermissionCheckResponse(scope.granted());
            }

            boolean granted = aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessLevel(
                    request.environmentId(),
                    scope.contextServiceUuid(),
                    AccessLevel.APPROVED);

            log.debug("Any-grant check for environment={}, service={}, zone={}, context={}: granted={}",
                    request.environmentId(), request.serviceName(), request.zoneId(),
                    request.contextId(), granted);

            return new PermissionCheckResponse(granted);
        } catch (Exception e) {
            // Fail closed, matching checkPermission.
            log.warn("Any-grant check failed for environment={}, service={}: {}",
                    request.environmentId(), request.serviceName(), e.getMessage());
            return new PermissionCheckResponse(false);
        }
    }

    /**
     * Outcome of resolving an ACL lookup down to a {@code contextServiceUuid}.
     *
     * <p>Either the decision was already reached without consulting ACL entries (environment
     * missing, admin, self-access, or an unresolvable zone/context/service), or resolution
     * succeeded and the caller should query ACL entries against
     * {@link #contextServiceUuid()}.</p>
     */
    private record AclScope(boolean decided, boolean granted, UUID contextServiceUuid) {
        static AclScope decided(boolean granted) {
            return new AclScope(true, granted, null);
        }

        static AclScope resolved(UUID contextServiceUuid) {
            return new AclScope(false, false, contextServiceUuid);
        }
    }

    /**
     * Shared resolution for both {@link #checkPermission} and {@link #hasAnyGrant}.
     *
     * <p>Extracted so the two checks cannot drift on the parts that must behave identically:
     * which environments short-circuit to granted (admin, self-access), and how a
     * {@code (zoneId, contextId, serviceName)} triple resolves to a {@code contextServiceUuid}.
     * Behaviour is unchanged from the original inline implementation in
     * {@code checkPermission}.</p>
     */
    private AclScope resolveAclScope(UUID environmentId, String serviceName,
                                     String zoneId, String contextId) {
        // Find the environment
        Environment environment = environmentRepository.findById(environmentId)
            .orElse(null);

        if (environment == null) {
            return AclScope.decided(false);
        }

        // Admin has permission to everything
        if (isAdmin(environment)) {
            return AclScope.decided(true);
        }

        // Allow environments to access their own environment record (self-access)
        // The gateway sends the environment UUID as the serviceName for /environments/{id} requests
        if (serviceName != null) {
            try {
                UUID targetId = UUID.fromString(serviceName);
                if (targetId.equals(environmentId)) {
                    log.debug("Self-access granted for environment: {}", environmentId);
                    return AclScope.decided(true);
                }
            } catch (IllegalArgumentException ignored) {
                // serviceName is not a UUID — not a self-access request, continue to ACL check
            }
        }

        // Resolve zoneId/contextId strings to context_uuid
        UUID contextUuid = null;
        if (contextId != null) {
            // contextId is non-null — resolve to context_uuid via environment's solution
            UUID solutionUuid = environment.getSolutionUuid();
            Optional<org.sifunity.broker.environment.domain.Zone> zone =
                zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId);
            if (zone.isEmpty()) {
                log.debug("Zone not found for zoneId: {}, denying permission", zoneId);
                return AclScope.decided(false);
            }

            Optional<org.sifunity.broker.environment.domain.Context> context =
                contextRepository.findByZoneUuidAndContextId(zone.get().getId(), contextId);
            if (context.isEmpty()) {
                log.debug("Context not found for zoneId: {}, contextId: {}, denying permission",
                    zoneId, contextId);
                return AclScope.decided(false);
            }

            contextUuid = context.get().getId();
        }
        // If contextId is null, contextUuid stays null (wildcard query)

        // Resolve (serviceName, contextUuid) → contextServiceUuid via ContextServiceRepository
        // If context_service doesn't exist, no permission is possible
        var contextServiceOpt = contextServiceRepository.findByContextUuidAndServiceName(
                contextUuid, serviceName);
        if (contextServiceOpt.isEmpty()) {
            log.debug("No context_service found for serviceName: {}, contextUuid: {}, denying permission",
                    serviceName, contextUuid);
            return AclScope.decided(false);
        }

        return AclScope.resolved(contextServiceOpt.get().getId());
    }
    
    /**
     * Checks if the requester can create a new environment.
     * 
     * <p>Only ADMIN environments can create new environments.</p>
     * 
     * @param requester the environment making the request
     * @return true if the requester can create environments, false otherwise
     */
    public boolean canCreateEnvironment(Environment requester) {
        if (requester == null) {
            return false;
        }
        return isAdmin(requester);
    }
    
    /**
     * Checks if the requester can read the target environment.
     * 
     * <p>ADMIN can read all environments. Regular environments can only read their own data.</p>
     * 
     * @param requester the environment making the request
     * @param target the environment to be read
     * @return true if the requester can read the target environment, false otherwise
     */
    public boolean canReadEnvironment(Environment requester, Environment target) {
        if (requester == null || target == null) {
            return false;
        }
        
        // Admin can read all environments
        if (isAdmin(requester)) {
            return true;
        }
        
        // Regular environments can only read their own data
        return requester.getId().equals(target.getId());
    }
    
    /**
     * Checks if the requester can update the target environment.
     * 
     * <p>ADMIN can update all environments. Regular environments can only update their own data.</p>
     * 
     * @param requester the environment making the request
     * @param target the environment to be updated
     * @return true if the requester can update the target environment, false otherwise
     */
    public boolean canUpdateEnvironment(Environment requester, Environment target) {
        if (requester == null || target == null) {
            return false;
        }
        
        // Admin can update all environments
        if (isAdmin(requester)) {
            return true;
        }
        
        // Regular environments can only update their own data
        return requester.getId().equals(target.getId());
    }
    
    /**
     * Checks if the requester can delete the target environment.
     * 
     * <p>Only ADMIN environments can delete any environment.</p>
     * 
     * @param requester the environment making the request
     * @param target the environment to be deleted
     * @return true if the requester can delete the target environment, false otherwise
     */
    public boolean canDeleteEnvironment(Environment requester, Environment target) {
        if (requester == null || target == null) {
            return false;
        }
        return isAdmin(requester);
    }
    
    /**
     * Checks if the requester can create a new ACL entry.
     * 
     * <p>Only ADMIN environments can create ACL entries.</p>
     * 
     * @param requester the environment making the request
     * @return true if the requester can create ACL entries, false otherwise
     */
    public boolean canCreateAcl(Environment requester) {
        if (requester == null) {
            log.warn("canCreateAcl: requester is null");
            return false;
        }
        boolean result = isAdmin(requester);
        log.debug("canCreateAcl: requester.id={}, requester.type={}, result={}", 
            requester.getId(), requester.getType(), result);
        return result;
    }
    
    /**
     * Checks if the requester can read the ACL entry.
     * 
     * <p>ADMIN can read all ACL entries. Regular environments can read ACL entries 
     * that reference them.</p>
     * 
     * @param requester the environment making the request
     * @param acl the ACL entry to be read
     * @return true if the requester can read the ACL entry, false otherwise
     */
    public boolean canReadAcl(Environment requester, AclEntry acl) {
        if (requester == null || acl == null) {
            return false;
        }
        
        // Admin can read all ACL entries
        if (isAdmin(requester)) {
            return true;
        }
        
        // Regular environments can read ACL entries that reference them
        return acl.getEnvironment() != null && 
               acl.getEnvironment().getId().equals(requester.getId());
    }
    
    /**
     * Checks if the requester can update the ACL entry.
     * 
     * <p>Only ADMIN environments can update ACL entries.</p>
     * 
     * @param requester the environment making the request
     * @param acl the ACL entry to be updated
     * @return true if the requester can update the ACL entry, false otherwise
     */
    public boolean canUpdateAcl(Environment requester, AclEntry acl) {
        if (requester == null || acl == null) {
            return false;
        }
        return isAdmin(requester);
    }
    
    /**
     * Checks if the requester can delete the ACL entry.
     * 
     * <p>Only admin environments can delete ACL entries.</p>
     * 
     * @param requester the environment making the request
     * @param acl the ACL entry to be deleted
     * @return true if the requester can delete the ACL entry, false otherwise
     */
    public boolean canDeleteAcl(Environment requester, AclEntry acl) {
        if (requester == null || acl == null) {
            return false;
        }
        return isAdmin(requester);
    }

    /**
     * Checks if the given environment is a global admin environment.
     *
     * <p>Resolves the environment's domain via its solutionId and checks
     * against the well-known global admin domain and solution constants.</p>
     *
     * <p>This method is public so that controllers (e.g. {@code AclController})
     * can determine the caller's admin status for request-level authorization
     * decisions.</p>
     *
     * @param env the environment to check
     * @return true if the environment is a global admin, false otherwise
     */
    public boolean isAdmin(Environment env) {
        if (env.getSolutionUuid() == null) {
            return false;
        }
        // Global admin check via cached service methods
        boolean globalAdmin = solutionService.findByIdCached(env.getSolutionUuid())
            .map(solution -> {
                String domainId = domainService.findByIdCached(solution.getDomainUuid())
                    .map(Domain::getDomainId)
                    .orElse(null);
                return GlobalAdminConstants.isGlobalAdmin(domainId, solution.getSolutionId());
            })
            .orElse(false);
        if (globalAdmin) return true;

        // Solution-level admin: broker-services fingerprint
        return "broker-services".equals(env.getFingerprint());
    }
}
