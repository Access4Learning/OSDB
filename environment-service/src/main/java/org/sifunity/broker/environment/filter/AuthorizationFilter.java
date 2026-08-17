// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.util.ServletErrorResponseUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.UUID;

/**
 * Authorization filter that enforces role-based access control for all requests.
 * 
 * <p>This filter:
 * <ul>
 *   <li>Extracts credentials from the Authorization header</li>
 *   <li>Looks up the environment by sessionToken</li>
 *   <li>Checks permissions using AuthorizationService</li>
 *   <li>Returns 401 for missing/invalid credentials</li>
 *   <li>Returns 403 for insufficient permissions</li>
 *   <li>Stores the requester in request attribute for controllers</li>
 * </ul>
 * 
 * <p><strong>Requirements:</strong> 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7</p>
 */
@Slf4j
public class AuthorizationFilter extends OncePerRequestFilter {
    
    private final String internalServiceToken;
    
    private final AuthorizationService authorizationService;
    private final EnvironmentRepository environmentRepository;
    private final AclEntryRepository aclEntryRepository;
    private final org.sifunity.broker.environment.repository.SolutionRepository solutionRepository;
    private final org.sifunity.broker.environment.repository.DomainRepository domainRepository;
    
    public AuthorizationFilter(
            AuthorizationService authorizationService,
            EnvironmentRepository environmentRepository,
            AclEntryRepository aclEntryRepository,
            org.sifunity.broker.environment.repository.SolutionRepository solutionRepository,
            org.sifunity.broker.environment.repository.DomainRepository domainRepository,
            String internalServiceToken) {
        this.authorizationService = authorizationService;
        this.environmentRepository = environmentRepository;
        this.aclEntryRepository = aclEntryRepository;
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
        this.internalServiceToken = internalServiceToken;
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Skip filter for actuator endpoints
        if (path.startsWith("/actuator/")) {
            return true;
        }
        
        // Skip filter for OpenAPI/Swagger documentation endpoints
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui") 
                || path.startsWith("/webjars/")) {
            return true;
        }
        
        // Skip filter for zone/context listing (only exact connector patterns with valid internal token)
        if (isAllowedConnectorZoneRequest(request)) {
            return true;
        }
        
        // Skip filter for entity tag listing (used by extensions module for feature toggle queries)
        if (path.matches(".+/tags$") && "GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        
        // Skip filter for admin user login endpoint (no auth required)
        if (path.equals("/admin-users/login") && "POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        
        // Skip filter for admin session endpoints (handled by AdminSessionFilter)
        if (path.startsWith("/admin-users/sessions/")) {
            return true;
        }
        
        // Skip filter for admin proxy endpoints (handled by AdminSessionFilter)
        if (path.startsWith("/admin-proxy/")) {
            return true;
        }
        
        return false;
    }
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Internal endpoint token verification (constant-time comparison)
        if (path.startsWith("/internal/")) {
            String presentedToken = request.getHeader("X-Internal-Service-Token");
            if (presentedToken == null || !MessageDigest.isEqual(
                    internalServiceToken.getBytes(StandardCharsets.UTF_8),
                    presentedToken.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Rejected internal request: path={}, tokenPresent={}", path, presentedToken != null);
                writeSifError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_010",
                        "Internal authentication required",
                        "Valid X-Internal-Service-Token header is required for internal endpoints");
                return;
            }
            log.debug("Internal token verified for path={}", path);
            filterChain.doFilter(request, response);
            return;
        }
        
        log.debug("Authorization filter processing: {} {}", method, path);
        
        // Extract session token from Authorization header
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || authHeader.trim().isEmpty()) {
            log.warn("Missing Authorization header for: {} {}", method, path);
            writeSifError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_001",
                    "Authentication required", "No Authorization header provided");
            return;
        }
        
        // Extract session token (format: "SIF_HMACSHA256 <sessionToken>:<signature>")
        String sessionToken = extractSessionToken(authHeader);
        
        if (sessionToken == null) {
            log.warn("Invalid Authorization header format for: {} {}", method, path);
            writeSifError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_002",
                    "Invalid credentials", "Authorization header format is invalid");
            return;
        }
        
        // Find environment by session token
        Environment requester = environmentRepository
            .findBySessionToken(sessionToken)
            .orElse(null);
        
        if (requester == null) {
            log.warn("Invalid session token for: {} {}", method, path);
            writeSifError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_003",
                    "Invalid credentials", "Session token not recognized");
            return;
        }
        
        log.info("Request from environment: id={}, type={}, applicationName={}", 
            requester.getId(), requester.getType(), requester.getApplicationName());
        
        // Check authorization based on request method and path
        boolean authorized = checkAuthorization(requester, method, path, request);
        
        if (!authorized) {
            log.warn("Authorization denied for environment {} ({}) to {} {}",
                requester.getId(), requester.getType(), method, path);
            writeSifError(response, HttpServletResponse.SC_FORBIDDEN, "AUTHZ_001",
                    "Insufficient permissions",
                    "Environment " + requester.getId() + " is not authorized for this operation. " +
                    "Only the environment owner or a global admin can access this resource.");
            return;
        }
        
        log.debug("Authorization granted for environment {} to {} {}", 
            requester.getId(), method, path);
        
        // Store requester in request attribute for controllers
        request.setAttribute("requester", requester);
        
        // Set Spring Security authentication context
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            requester,
            null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + requester.getType().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        log.debug("Spring Security authentication set for environment {}", requester.getId());
        
        // Continue filter chain
        filterChain.doFilter(request, response);
    }
    
    /**
     * Extracts the session token from the Authorization header.
     * 
     * <p>Expected format: "SIF_HMACSHA256 <sessionToken>:<signature>"</p>
     * 
     * @param authHeader the Authorization header value
     * @return the session token, or null if invalid format
     */
    private String extractSessionToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("SIF_HMACSHA256 ")) {
            return null;
        }
        
        String base64Credentials = authHeader.substring("SIF_HMACSHA256 ".length());
        
        // Decode from Base64 (format: Base64(sessionToken:hash))
        String credentials;
        try {
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Credentials);
            credentials = new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        
        int colonIndex = credentials.indexOf(':');
        
        if (colonIndex == -1) {
            return null;
        }
        
        return credentials.substring(0, colonIndex);
    }
    
    /**
     * Writes a SIF-compliant JSON error response.
     */
    private void writeSifError(HttpServletResponse response, int status, String code,
                                String message, String description) throws IOException {
        ServletErrorResponseUtils.writeErrorResponse(response, status, code, message, description);
    }
    
    /**
     * Checks if the requester is authorized for the given operation.
     * 
     * @param requester the environment making the request
     * @param method the HTTP method
     * @param path the request path
     * @param request the HTTP request
     * @return true if authorized, false otherwise
     */
    private boolean checkAuthorization(
            Environment requester,
            String method,
            String path,
            HttpServletRequest request) {
        
        // Extract the resource path after the domain/solution prefix
        // Paths like /{domainId}/{solutionId}/environments/... or /{domainId}/solutions/...
        String resourcePath = extractResourcePath(path);

        // Extract the domain/solution scope from the URL path so admin checks can confirm a
        // solution admin (broker-services) is operating within its OWN domain+solution.
        // segments = ["", domainId, solutionId, ...]; segments[2] is "solutions" for
        // solution-management paths, which carry no per-solution scope.
        String[] pathSegments = path.split("/");
        String urlDomainId = pathSegments.length > 1 ? pathSegments[1] : null;
        String urlSolutionId = (pathSegments.length > 2 && !"solutions".equals(pathSegments[2]))
                ? pathSegments[2] : null;
        
        // Environment operations
        if (resourcePath.startsWith("/environments")) {
            return checkEnvironmentAuthorization(requester, method, resourcePath, urlDomainId, urlSolutionId);
        }
        
        // ACL operations
        if (resourcePath.startsWith("/acl-entries")) {
            return checkAclAuthorization(requester, method, resourcePath);
        }
        
        // Internal operations - allow all authenticated requests
        if (path.startsWith("/internal")) {
            return true;
        }
        
        // Domain and solution management - global admin only
        if (path.startsWith("/domains") || resourcePath.startsWith("/solutions")) {
            return isGlobalAdmin(requester);
        }
        
        // Tag definition management - global admin only
        if (path.startsWith("/admin/tag-definitions")) {
            return isGlobalAdmin(requester);
        }
        
        // Zone and context management - admin only
        if (resourcePath.startsWith("/zones")) {
            return isAdmin(requester, urlDomainId, urlSolutionId);
        }
        
        // Default: allow (for health checks, etc.)
        return true;
    }
    
    /**
     * Checks if the requester is a global admin environment.
     */
    private boolean isAdmin(Environment requester, String urlDomainId, String urlSolutionId) {
        return isGlobalAdmin(requester) || isSolutionAdmin(requester, urlDomainId, urlSolutionId);
    }

    /**
     * Checks if the requester is a global admin (GLOBAL_DOMAIN/GLOBAL_SOLUTION).
     */
    private boolean isGlobalAdmin(Environment requester) {
        if (requester.getSolutionUuid() == null) return false;
        return solutionRepository.findById(requester.getSolutionUuid())
            .map(solution -> {
                String domainId = domainRepository.findById(solution.getDomainUuid())
                    .map(org.sifunity.broker.environment.domain.Domain::getDomainId)
                    .orElse(null);
                return org.sifunity.broker.common.constant.GlobalAdminConstants.isGlobalAdmin(domainId, solution.getSolutionId());
            })
            .orElse(false);
    }

    /**
     * Checks if the requester is a solution-level admin for the domain+solution in the URL path.
     *
     * <p>A solution admin is identified by the {@code broker-services} fingerprint AND must be
     * scoped to the same domain+solution as the resource being accessed. This second check makes
     * the environment-service independently enforce solution isolation, rather than relying solely
     * on the gateway's MembershipValidationFilter — a broker-services credential provisioned for
     * solution A cannot act as admin on solution B.</p>
     */
    private boolean isSolutionAdmin(Environment requester, String urlDomainId, String urlSolutionId) {
        // Must carry the broker-services fingerprint...
        if (!"broker-services".equals(requester.getFingerprint())) {
            return false;
        }
        // ...AND resolve to the same domain+solution as the URL path.
        if (requester.getSolutionUuid() == null || urlDomainId == null || urlSolutionId == null) {
            return false;
        }
        return solutionRepository.findById(requester.getSolutionUuid())
            .map(solution -> {
                if (!urlSolutionId.equals(solution.getSolutionId())) {
                    return false;
                }
                String domainId = domainRepository.findById(solution.getDomainUuid())
                    .map(org.sifunity.broker.environment.domain.Domain::getDomainId)
                    .orElse(null);
                return urlDomainId.equals(domainId);
            })
            .orElse(false);
    }
    
    /**
     * Extracts the resource path from a full URL path that may include domain/solution prefix.
     * 
     * <p>Handles three URL tiers:
     * <ul>
     *   <li>{@code /domains/**} → returns full path</li>
     *   <li>{@code /{domainId}/solutions/**} → returns /solutions/...</li>
     *   <li>{@code /{domainId}/{solutionId}/resource/**} → returns /resource/...</li>
     * </ul>
     * 
     * @param path the full request path
     * @return the resource path after any domain/solution prefix
     */
    private String extractResourcePath(String path) {
        if (path.startsWith("/domains") || path.startsWith("/internal") || path.startsWith("/actuator")) {
            return path;
        }
        
        // Split path into segments: ["", domainId, solutionIdOrSolutions, ...]
        String[] segments = path.split("/");
        if (segments.length < 3) {
            return path;
        }
        
        // Check if this is a /{domainId}/solutions/... path
        if ("solutions".equals(segments[2])) {
            // Return /solutions/...
            return path.substring(segments[0].length() + segments[1].length() + 1);
        }
        
        // Otherwise it's /{domainId}/{solutionId}/resource/...
        if (segments.length >= 4) {
            int prefixLength = segments[0].length() + segments[1].length() + segments[2].length() + 2;
            return path.substring(prefixLength);
        }
        
        return path;
    }
    
    /**
     * Checks authorization for environment operations.
     * 
     * @param requester the environment making the request
     * @param method the HTTP method
     * @param path the request path
     * @return true if authorized, false otherwise
     */
    private boolean checkEnvironmentAuthorization(
            Environment requester,
            String method,
            String path,
            String urlDomainId,
            String urlSolutionId) {
        
        log.info("checkEnvironmentAuthorization: method={}, path={}, requester.type={}", 
            method, path, requester.getType());
        
        // SIF 3.7 singular-subpath create: POST /environments/environment.
        // This is a create, not an operation on a resource with id "environment".
        if (method.equals("POST") && path.equals("/environments/environment")) {
            boolean result = authorizationService.canCreateEnvironment(requester);
            log.info("checkEnvironmentAuthorization POST (singular create): canCreateEnvironment returned {}", result);
            return result;
        }
        
        String targetId = extractIdFromPath(path, "/environments/");
        
        // POST without a target ID = create new environment (bare collection; controller returns 405,
        // but authorization is still evaluated as a create for consistency).
        if (method.equals("POST") && targetId == null) {
            boolean result = authorizationService.canCreateEnvironment(requester);
            log.info("checkEnvironmentAuthorization POST (create): canCreateEnvironment returned {}", result);
            return result;
        }
        
        if (targetId == null) {
            if (method.equals("GET")) {
                // List operation: admin only — normal environments must use /environments/{id}
                boolean adminCheck = isAdmin(requester, urlDomainId, urlSolutionId);
                log.info("checkEnvironmentAuthorization GET list: isAdmin={}", adminCheck);
                return adminCheck;
            }
            log.info("checkEnvironmentAuthorization {}: no targetId, denying", method);
            return false;
        }
        
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(targetId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid environment ID format: {}", targetId);
            return false;
        }
        
        Environment target = environmentRepository.findById(targetUuid).orElse(null);
        if (target == null) {
            log.info("checkEnvironmentAuthorization: target {} not found, denying", targetId);
            return false;
        }
        
        boolean result = switch (method) {
            case "GET" -> authorizationService.canReadEnvironment(requester, target);
            case "PUT", "PATCH" -> authorizationService.canUpdateEnvironment(requester, target);
            case "POST" -> authorizationService.canUpdateEnvironment(requester, target);
            case "DELETE" -> authorizationService.canDeleteEnvironment(requester, target);
            default -> false;
        };
        
        log.info("checkEnvironmentAuthorization {}: targetId={}, result={}", method, targetId, result);
        return result;
    }
    
    /**
     * Checks authorization for ACL operations.
     * 
     * @param requester the environment making the request
     * @param method the HTTP method
     * @param path the request path
     * @return true if authorized, false otherwise
     */
    private boolean checkAclAuthorization(
            Environment requester,
            String method,
            String path) {
        
        log.info("checkAclAuthorization: method={}, path={}, requester.type={}", 
            method, path, requester.getType());
        
        if (method.equals("POST")) {
            boolean result = authorizationService.canCreateAcl(requester);
            log.info("checkAclAuthorization POST: canCreateAcl returned {}", result);
            return result;
        }
        
        String targetId = extractIdFromPath(path, "/acl-entries/");
        
        if (targetId == null) {
            if (method.equals("GET")) {
                return true;
            }
            return false;
        }
        
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(targetId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ACL entry ID format: {}", targetId);
            return false;
        }
        
        AclEntry acl = aclEntryRepository.findById(targetUuid).orElse(null);
        if (acl == null) {
            return false;
        }
        
        return switch (method) {
            case "GET" -> authorizationService.canReadAcl(requester, acl);
            case "PUT", "PATCH" -> authorizationService.canUpdateAcl(requester, acl);
            case "DELETE" -> authorizationService.canDeleteAcl(requester, acl);
            default -> false;
        };
    }
    
    /**
     * Extracts the resource ID from the path.
     * 
     * <p>Example: "/environments/123" with prefix "/environments/" returns "123"</p>
     * 
     * @param path the request path
     * @param prefix the path prefix to remove
     * @return the resource ID, or null if not found
     */
    private String extractIdFromPath(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return null;
        }
        
        String remainder = path.substring(prefix.length());
        
        // Handle paths like "/environments/123" or "/environments/123/"
        int slashIndex = remainder.indexOf('/');
        if (slashIndex != -1) {
            remainder = remainder.substring(0, slashIndex);
        }
        
        // Handle query parameters
        int questionIndex = remainder.indexOf('?');
        if (questionIndex != -1) {
            remainder = remainder.substring(0, questionIndex);
        }
        
        return remainder.isEmpty() ? null : remainder;
    }
    
    /**
     * Checks if the request is an allowed connector zone request that can bypass HMAC auth.
     * 
     * <p>Only GET requests to exact connector patterns with a valid internal service token
     * are allowed to bypass authentication:
     * <ul>
     *   <li>GET /{domainId}/{solutionId}/zones</li>
     *   <li>GET /{domainId}/{solutionId}/zones/{zoneId}/contexts</li>
     * </ul>
     *
     * @param request the HTTP request
     * @return true if this is a valid connector zone request, false otherwise
     */
    private boolean isAllowedConnectorZoneRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String[] segments = path.split("/");
        // Pattern 1: GET /{domainId}/{solutionId}/zones (4 segments: ["", domain, solution, "zones"])
        boolean isZoneListing = segments.length == 4 && "zones".equals(segments[3]);
        // Pattern 2: GET /{domainId}/{solutionId}/zones/{zoneId}/contexts (6 segments)
        boolean isContextListing = segments.length == 6 
            && "zones".equals(segments[3]) && "contexts".equals(segments[5]);
        if (!isZoneListing && !isContextListing) {
            return false;
        }
        String token = request.getHeader("X-Internal-Service-Token");
        return internalServiceToken != null && !internalServiceToken.isEmpty() && internalServiceToken.equals(token);
    }
}
