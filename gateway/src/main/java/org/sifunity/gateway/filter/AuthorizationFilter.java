// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.PermissionCheckRequest;
import org.sifunity.broker.common.exception.InsufficientPermissionsException;
import org.sifunity.broker.common.exception.InvalidRequestException;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.util.LoggingUtils;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.metrics.GatewayMetricsService;
import org.sifunity.gateway.util.GatewayErrorResponseUtils;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Filter that checks ACL permissions via the Environment Service.
 * 
 * <p>This filter runs fourth in the filter chain (Order 4) after the ServiceTypeValidationFilter.
 * It validates that the authenticated environment has permission to access the requested
 * service by calling the Environment Service's internal check-permission API.</p>
 * 
 * <p>Behavior:</p>
 * <ul>
 *   <li>Exempt paths (POST /environments, /actuator/health) skip authorization</li>
 *   <li>Extracts environment ID from exchange attributes (set by AuthenticationFilter)</li>
 *   <li>Extracts service name from headers or path</li>
 *   <li>Extracts zone and context from headers (uses defaults if missing)</li>
 *   <li>Determines access type based on HTTP method and path pattern</li>
 *   <li>Calls EnvironmentServiceClient.checkPermission</li>
 *   <li>Returns 403 Forbidden for authorization failures</li>
 * </ul>
 * 
 * <p>Access Type Determination Rules:</p>
 * <ul>
 *   <li>GET /requests/* → QUERY</li>
 *   <li>POST /responses/* → PROVIDE</li>
 *   <li>POST /events/* → PROVIDE</li>
 *   <li>/services/{name}/{jobId}/{phaseName}/... (3+ segments) → CREATE (any method)</li>
 *   <li>POST /services/{name} → CREATE</li>
 *   <li>GET /services/{name} or /services/{name}/{jobId} → QUERY</li>
 *   <li>DELETE /services/{name}/{jobId} → DELETE</li>
 *   <li>Default → QUERY</li>
 * </ul>
 * 
 * <p>Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10</p>
 * 
 * @see EnvironmentServiceClient
 * @see PermissionCheckRequest
 * @see AccessType
 */
@Component
public class AuthorizationFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(AuthorizationFilter.class);
    
    private final EnvironmentServiceClient environmentServiceClient;
    private final ObjectMapper objectMapper;
    private final GatewayMetricsService metricsService;
    
    /**
     * Constructs an AuthorizationFilter with the specified dependencies.
     * 
     * @param environmentServiceClient the client for calling Environment Service APIs
     * @param objectMapper the Jackson ObjectMapper for JSON serialization
     * @param metricsService the metrics service for recording authorization metrics
     */
    public AuthorizationFilter(
            EnvironmentServiceClient environmentServiceClient,
            ObjectMapper objectMapper,
            GatewayMetricsService metricsService) {
        this.environmentServiceClient = environmentServiceClient;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }
    
    @Timed(value = "sif.auth.acl.lookup",
           description = "ACL permission check latency",
           percentiles = {0.5, 0.95, 0.99})
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();
        
        // Skip authorization for exempt paths (already handled by AuthenticationFilter)
        if (isExemptPath(path, method)) {
            log.debug("Path {} is exempt from authorization", path);
            return chain.filter(exchange);
        }
        
        // Extract environment details from exchange attributes (set by AuthenticationFilter)
        UUID environmentId = exchange.getAttribute("environmentId");
        if (environmentId == null) {
            // Should not happen if AuthenticationFilter ran first
            log.error("Environment not authenticated - environmentId is null");
            return handleAuthorizationError(exchange, 
                new InsufficientPermissionsException("Environment not authenticated"),
                null, null);
        }
        
        try {
            // Extract service name, zone, context from request
            String serviceName = extractServiceName(exchange, request);
            String zoneId = extractZoneId(exchange, request);
            String contextId = extractContextId(exchange, request);
            
            // Determine access type based on method and ORIGINAL path (before RewritePath filters)
            String originalPath = getOriginalPath(exchange, request);
            AccessType accessType = determineAccessType(method, originalPath);
            
            log.debug("Checking permission for environment: {}, service: {}, zone: {}, context: {}, accessType: {}, originalPath: {}",
                environmentId, serviceName, zoneId, contextId, accessType, originalPath);
            
            // Call Environment Service to check permission
            PermissionCheckRequest permissionRequest = new PermissionCheckRequest(
                environmentId,
                serviceName,
                zoneId,
                contextId,
                accessType
            );
            
            return environmentServiceClient.checkPermission(permissionRequest)
                .onErrorResume(InsufficientPermissionsException.class, e -> {
                    // Record authorization failure metric
                    metricsService.recordAuthorizationFailure(environmentId, serviceName, accessType);
                    return Mono.error(e);
                })
                .onErrorResume(ServiceUnavailableException.class, e -> {
                    // Record authorization failure metric
                    metricsService.recordAuthorizationFailure(environmentId, serviceName, accessType);
                    return Mono.error(e);
                })
                .onErrorResume(InvalidTokenException.class, e -> {
                    // Record authorization failure metric
                    metricsService.recordAuthorizationFailure(environmentId, serviceName, accessType);
                    return Mono.error(e);
                })
                .onErrorResume(e -> {
                    // Handle other errors from checkPermission with generic error response
                    log.error("Unexpected error during permission check: {}", e.getMessage(), e);
                    metricsService.recordAuthorizationFailure(environmentId, serviceName, accessType);
                    return Mono.error(e);
                })
                .flatMap(response -> {
                    if (response.granted()) {
                        log.debug("Authorization successful for environment: {}, service: {}",
                            environmentId, serviceName);
                        
                        // Record authorization success metric
                        metricsService.recordAuthorizationSuccess(environmentId, serviceName, accessType);
                        
                        // Continue with filter chain - errors from chain.filter() will propagate to GlobalErrorWebExceptionHandler
                        return chain.filter(exchange);
                    } else {
                        log.warn("Authorization denied for environment: {}, service: {}, accessType: {}",
                            environmentId, serviceName, accessType);
                        
                        // Record authorization failure metric
                        metricsService.recordAuthorizationFailure(environmentId, serviceName, accessType);
                        
                        return handleAuthorizationError(exchange,
                            new InsufficientPermissionsException(
                                "Access denied for service: " + serviceName),
                            serviceName, accessType);
                    }
                })
                .onErrorResume(InsufficientPermissionsException.class, e -> {
                    return handleAuthorizationError(exchange, e, serviceName, accessType);
                })
                .onErrorResume(ServiceUnavailableException.class, e -> {
                    return handleAuthorizationError(exchange, e, serviceName, accessType);
                })
                .onErrorResume(InvalidTokenException.class, e -> {
                    return handleAuthorizationError(exchange, e, serviceName, accessType);
                })
                .onErrorResume(e -> {
                    // Only handle errors from checkPermission, not from chain.filter()
                    // Check if this is a routing error (connection refused, etc.)
                    if (e instanceof java.net.ConnectException ||
                        (e.getCause() != null && e.getCause() instanceof java.net.ConnectException) ||
                        (e.getMessage() != null && e.getMessage().contains("Connection refused"))) {
                        // Let routing errors propagate to GlobalErrorWebExceptionHandler
                        return Mono.error(e);
                    }
                    // Handle other errors from checkPermission with generic error response
                    return handleGenericError(exchange, e);
                });
                
        } catch (InvalidRequestException e) {
            log.warn("Invalid authorization request: {}", e.getMessage());
            return handleAuthorizationError(exchange, e, null, null);
        }
    }
    
    /**
     * Checks if a path is exempt from authorization.
     * 
     * <p>Exempt paths:</p>
     * <ul>
     *   <li>Domain management paths ({@code /domains/**}) — exempt from ACL authorization</li>
     *   <li>Solution management paths ({@code /{domainId}/solutions/**}) — exempt from ACL authorization</li>
     *   <li>{@code /{domainId}/{solutionId}/queues/**} — infrastructure, exempt from ACL authorization</li>
     *   <li>POST {@code /{domainId}/{solutionId}/environments} — environment registration, exempt</li>
     *   <li>{@code /actuator/health} — health check, exempt</li>
     * </ul>
     * 
     * @param path the request path
     * @param method the HTTP method
     * @return true if the path is exempt from authorization, false otherwise
     */
    private boolean isExemptPath(String path, HttpMethod method) {
        // OpenAPI/Swagger paths are exempt — documentation UI and API specs
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui") 
                || path.startsWith("/webjars/")) {
            return true;
        }

        // Admin user auth paths are exempt — login and session management use their own auth
        if (path.startsWith("/admin-users/login") || path.startsWith("/admin-users/sessions")
                || path.startsWith("/admin-proxy")) {
            return true;
        }

        ParsedPath parsed = UrlPrefixParser.parse(path);

        // Domain management paths (/domains/**, /actuator/**) are exempt from ACL authorization
        if (parsed.isDomainManagementPath()) {
            return true;
        }

        // Solution management paths (/{domainId}/solutions/**) are exempt from ACL authorization
        if (parsed.isSolutionManagementPath()) {
            return true;
        }

        // For resource paths, check the resource path after the prefix
        String resourcePath = parsed.resourcePath();

        // Environment management paths - require authentication but not ACL authorization
        if (resourcePath.startsWith("/environments")) {
            return true;
        }

        // Infrastructure paths - require authentication but not ACL authorization
        if (resourcePath.startsWith("/queues")) {
            return true;
        }

        // Zone/context paths - authorization handled by environment-service's own filter
        if (resourcePath.startsWith("/zones")) {
            return true;
        }

        // ACL management paths - require authentication but not ACL authorization
        if (resourcePath.startsWith("/acl-entries")) {
            return true;
        }

        // Admin user management paths - require authentication but not ACL authorization
        if (resourcePath.startsWith("/admin-users")) {
            return true;
        }

        // Provider paths - require authentication but not ACL authorization
        if (resourcePath.startsWith("/providers")) {
            return true;
        }

        // Subscription management paths - require authentication but not ACL authorization
        if (resourcePath.startsWith("/subscriptions")) {
            return true;
        }

        // Job management paths - require authentication but not ACL authorization
        if (resourcePath.startsWith("/jobs")) {
            return true;
        }

        // Tag management paths - require authentication but not ACL authorization
        if (resourcePath.startsWith("/tags")) {
            return true;
        }

        // Solution service catalog paths - require authentication but not ACL authorization
        if (resourcePath.startsWith("/solution-services")) {
            return true;
        }

        // Context service paths - require authentication but not ACL authorization
        if (resourcePath.startsWith("/context-services")) {
            return true;
        }

        // Admin tag definition paths - require authentication but not ACL authorization
        // Authorization is handled by environment-service's own filter
        if (path.startsWith("/admin/tag-definitions")) {
            return true;
        }

        // Alerts paths - any authenticated environment can create/query alerts
        // without a specific ACL entry (SIF 3.7 §7, Requirement 8.4)
        if (resourcePath.startsWith("/alerts")) {
            return true;
        }

        // Code Sets paths - any authenticated environment can query code sets
        // without a specific ACL entry (same pattern as alerts)
        if (resourcePath.startsWith("/codeSets")) {
            return true;
        }

        // Namespaces paths - any authenticated environment can query namespaces
        // without a specific ACL entry (same pattern as alerts and code sets)
        if (resourcePath.startsWith("/namespaces")) {
            return true;
        }

        // xQueryTemplate paths - authorization handled by environment-service's own controller
        // (XQueryTemplateController validates PROVIDE access internally)
        if (resourcePath.startsWith("/xquerys")) {
            return true;
        }

        return false;
    }
    
    /**
     * Extracts the service name from the request.
     * 
     * <p>Service name extraction depends on the resource path:</p>
     * <ul>
     *   <li>{@code /responses} — service name is NOT in the URL. Providers POST raw payloads with
     *       service info in the {@code relativeServicePath} header. The service name is the first
     *       path segment of that header value.</li>
     *   <li>{@code /events} — service name is NOT in the URL. Providers POST events with
     *       the object type in the {@code serviceName} header.</li>
     *   <li>All other resource paths (e.g., {@code /requests/StudentPersonals}) — service
     *       name is extracted from the second segment of the resource path.</li>
     * </ul>
     * 
     * <p>Note: We use the original path before any RewritePath filters have modified it,
     * then strip the domain/solution prefix using {@link UrlPrefixParser} to get the
     * resource path.</p>
     * 
     * @param exchange the server web exchange
     * @param request the server HTTP request
     * @return the service name
     * @throws InvalidRequestException if service name cannot be extracted
     */
    private String extractServiceName(ServerWebExchange exchange, ServerHttpRequest request) {
        // Get the ORIGINAL path (before RewritePath filter)
        @SuppressWarnings("unchecked")
        java.util.LinkedHashSet<java.net.URI> originalUris = exchange.getAttribute(
            org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        
        String path;
        if (originalUris != null && !originalUris.isEmpty()) {
            java.net.URI originalUri = originalUris.iterator().next();
            path = originalUri.getPath();
            log.debug("Using original path for service name extraction: {}", path);
        } else {
            path = request.getPath().value();
            log.debug("Using current path for service name extraction: {}", path);
        }
        
        // Parse the path to strip the domain/solution prefix and get the resource path
        ParsedPath parsed = UrlPrefixParser.parse(path);
        String resourcePath = parsed.resourcePath();
        
        // For /responses, the service name is NOT in the URL path. Providers POST raw payloads
        // with service info in the relativeServicePath header. Extract service name from that
        // header using the same logic as ResponseController.
        //
        // The public /responses/error endpoint was removed (not a SIF concept — errors are conveyed
        // as an Error Object in the response payload with the code in the HTTP status field — and it
        // had no callers), so it is no longer special-cased here. /internal/responses/error is
        // unaffected: the gateway blocks /internal paths outright.
        if (resourcePath.equals("/responses")) {
            String relativeServicePath = request.getHeaders().getFirst(SifHeaders.RELATIVE_SERVICE_PATH);
            String fromHeader = extractServiceNameFromRelativeServicePath(relativeServicePath);
            if (fromHeader != null) {
                log.debug("Extracted service name '{}' from relativeServicePath header for /responses", fromHeader);
                return fromHeader;
            }
            throw new InvalidRequestException("relativeServicePath header required for /responses");
        }
        
        // For /events, the service name is NOT in the URL path.
        // Providers POST events with the object type in the serviceName header.
        if (resourcePath.equals("/events")) {
            String serviceName = request.getHeaders().getFirst(SifHeaders.SERVICE_NAME);
            if (serviceName != null && !serviceName.isBlank()) {
                log.debug("Extracted service name '{}' from serviceName header for /events", serviceName);
                return serviceName;
            }
            throw new InvalidRequestException("serviceName header required for /events");
        }
        
        // For resource paths like /requests/StudentPersonals, etc.
        // Extract the service name from the second segment of the resource path
        String[] parts = resourcePath.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        
        // For infrastructure paths like /environments, /acl-entries (no service name segment)
        // Use the first segment of the resource path as the service name
        if (parts.length >= 2 && !parts[1].isBlank()) {
            return parts[1];
        }
        
        throw new InvalidRequestException("Service name not found in path");
    }
    
    /**
     * Extracts the service name from a relativeServicePath header per SIF 3.7.
     *
     * <p>The relativeServicePath can take several forms:</p>
     * <ul>
     *   <li>{@code /StudentPersonals} — leading slash</li>
     *   <li>{@code /StudentPersonals;contextId=DEFAULT;zoneId=zone1} — matrix params</li>
     *   <li>{@code /StudentPersonals?param=value} — query params</li>
     *   <li>{@code /StudentPersonals/uuid123} — sub-path segments</li>
     *   <li>{@code StudentPersonals} — no leading slash (graceful handling)</li>
     * </ul>
     *
     * <p>The service name is the first path segment, stripped of any leading slash,
     * truncated before any {@code ;}, {@code ?}, or {@code /}.</p>
     *
     * @param relativeServicePath the SIF relative service path header value
     * @return the extracted service name, or null if blank/missing
     */
    public static String extractServiceNameFromRelativeServicePath(String relativeServicePath) {
        if (relativeServicePath == null || relativeServicePath.isBlank()) {
            return null;
        }
        String p = relativeServicePath.trim();
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        int end = p.length();
        int semi = p.indexOf(';');
        int question = p.indexOf('?');
        int slash = p.indexOf('/');
        if (semi >= 0 && semi < end) end = semi;
        if (question >= 0 && question < end) end = question;
        if (slash >= 0 && slash < end) end = slash;
        String name = p.substring(0, end);
        return name.isBlank() ? null : name;
    }
    
    /**
     * Gets the original request path before any RewritePath filters.
     * 
     * <p>This method extracts the original path from the exchange attributes
     * to ensure we use the path as it was received by the Gateway, not after
     * any RewritePath filters have modified it. This is critical for correct
     * access type determination.</p>
     * 
     * @param exchange the server web exchange
     * @param request the server HTTP request
     * @return the original request path
     */
    private String getOriginalPath(ServerWebExchange exchange, ServerHttpRequest request) {
        // Get the original request URLs from exchange attributes (it's a LinkedHashSet<URI>)
        @SuppressWarnings("unchecked")
        java.util.LinkedHashSet<java.net.URI> originalUris = exchange.getAttribute(
            org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        
        if (originalUris != null && !originalUris.isEmpty()) {
            // Get the first (original) URI from the set
            java.net.URI originalUri = originalUris.iterator().next();
            String originalPath = originalUri.getPath();
            log.debug("Using original path for access type determination: {}", originalPath);
            return originalPath;
        } else {
            // Fallback to current path if original not available
            String currentPath = request.getPath().value();
            log.debug("Using current path for access type determination: {}", currentPath);
            return currentPath;
        }
    }
    
    /**
     * Extracts the zone ID from the request.
     * 
     * <p>Zone ID extraction is prefix-agnostic — it reads from headers and exchange
     * attributes, not from the URL path, so it works correctly regardless of whether
     * the path includes a {@code /{domainId}/{solutionId}} prefix.</p>
     * 
     * <p>Zone ID extraction priority:</p>
     * <ol>
     *   <li>zoneId header (if present)</li>
     *   <li>Default zone from environment (set by AuthenticationFilter)</li>
     * </ol>
     * 
     * @param exchange the server web exchange
     * @param request the server HTTP request
     * @return the zone ID
     */
    private String extractZoneId(ServerWebExchange exchange, ServerHttpRequest request) {
        String zoneId = request.getHeaders().getFirst(SifHeaders.ZONE_ID);
        if (zoneId == null || zoneId.isBlank()) {
            // Use default zone from environment
            zoneId = exchange.getAttribute("defaultZoneId");
        }
        return zoneId;
    }
    
    /**
     * Extracts the context ID from the request.
     * 
     * <p>Context ID extraction is prefix-agnostic — it reads from headers,
     * not from the URL path, so it works correctly regardless of whether
     * the path includes a {@code /{domainId}/{solutionId}} prefix.</p>
     * 
     * <p>Context ID extraction priority:</p>
     * <ol>
     *   <li>contextId header (if present)</li>
     *   <li>"DEFAULT" (hardcoded fallback)</li>
     * </ol>
     * 
     * @param exchange the server web exchange
     * @param request the server HTTP request
     * @return the context ID (never null)
     */
    private String extractContextId(ServerWebExchange exchange, ServerHttpRequest request) {
        String contextId = request.getHeaders().getFirst(SifHeaders.CONTEXT_ID);
        if (contextId == null || contextId.isBlank()) {
            contextId = "DEFAULT";
        }
        return contextId;
    }
    
    /**
     * Determines the access type based on HTTP method and path pattern.
     * 
     * <p>Uses {@link UrlPrefixParser} to strip the {@code /{domainId}/{solutionId}}
     * prefix before matching path patterns, so the same rules apply regardless of
     * whether the path is prefixed or not.</p>
     * 
     * <p>Access type rules (applied to the resource path after prefix):</p>
     * <ul>
     *   <li>GET /requests/* → QUERY</li>
     *   <li>POST /responses/* → PROVIDE</li>
     *   <li>POST /events/* → PROVIDE</li>
     *   <li>/services/{name}/{jobId}/{phaseName}/... (3+ segments) → CREATE (phase/state ops, any method)</li>
     *   <li>/services/{name} (1 segment) → POST→CREATE, GET→QUERY, DELETE→DELETE</li>
     *   <li>/services/{name}/{jobId} (2 segments) → GET→QUERY, DELETE→DELETE</li>
     *   <li>Default → QUERY</li>
     * </ul>
     * 
     * @param method the HTTP method
     * @param path the request path (may include domain/solution prefix)
     * @return the access type
     */
    private AccessType determineAccessType(HttpMethod method, String path) {
        // Strip the domain/solution prefix to get the resource path
        ParsedPath parsed = UrlPrefixParser.parse(path);
        String resourcePath = parsed.resourcePath();

        // GET /requests/* -> QUERY
        if (method == HttpMethod.GET && resourcePath.startsWith("/requests")) {
            return AccessType.QUERY;
        }
        
        // POST /responses/* -> PROVIDE
        if (method == HttpMethod.POST && resourcePath.startsWith("/responses")) {
            return AccessType.PROVIDE;
        }
        
        // POST /events/* -> PROVIDE
        if (method == HttpMethod.POST && resourcePath.startsWith("/events")) {
            return AccessType.PROVIDE;
        }
        
        // /services/* - determine access type based on path segment count
        if (resourcePath.startsWith("/services")) {
            // Split resource path: /services/{name}/{jobId}/{phaseName}/...
            // parts[0]="" parts[1]="services" parts[2]=name parts[3]=jobId parts[4]=phaseName ...
            String[] parts = resourcePath.split("/");
            // Count segments after /services/ (parts[2] onwards)
            int segmentsAfterServices = parts.length - 2;
            
            if (segmentsAfterServices >= 3) {
                // 3+ segments: phase/state operations → always CREATE regardless of HTTP method
                return AccessType.CREATE;
            }
            
            // 1-2 segments: job-level operations → method-based access type
            if (method == HttpMethod.POST) {
                return AccessType.CREATE;
            }
            if (method == HttpMethod.GET) {
                return AccessType.QUERY;
            }
            if (method == HttpMethod.DELETE) {
                return AccessType.DELETE;
            }
            if (method == HttpMethod.PUT) {
                return AccessType.UPDATE;
            }
            
            // Fallback for other methods
            return AccessType.QUERY;
        }
        
        // Default to QUERY for other paths
        return AccessType.QUERY;
    }
    
    /**
     * Handles authorization errors by creating a SIF-compliant error response.
     * 
     * <p>This method logs the error with correlation ID and environment context,
     * and returns an HTTP 403 Forbidden response with a SifErrorResponse body.</p>
     * 
     * @param exchange the server web exchange
     * @param error the error that occurred during authorization
     * @param serviceName the service name being accessed (may be null)
     * @param accessType the access type being requested (may be null)
     * @return a Mono that completes when the error response is written
     */
    private Mono<Void> handleAuthorizationError(ServerWebExchange exchange, Throwable error, 
                                                 String serviceName, AccessType accessType) {
        // Get correlation ID from request headers (set by CorrelationIdFilter)
        String correlationId = exchange.getRequest().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
        UUID environmentId = exchange.getAttribute("environmentId");
        
        // Log with all required context per Requirement 7.7
        log.error("Authorization failed [correlationId={}, environmentId={}, serviceName={}, accessType={}]: {}", 
            correlationId, environmentId, serviceName, accessType, error.getMessage());
        
        // Create SIF-compliant error response
        SifErrorResponse errorResponse;
        if (error instanceof SifBrokerException) {
            errorResponse = SifErrorResponse.fromException((SifBrokerException) error);
        } else {
            errorResponse = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.FORBIDDEN.value(),
                "AUTHZ_001",
                error.getMessage(),
                null,
                "BROKER",
                Instant.now(),
                null,
                null
            );
        }
        
        return GatewayErrorResponseUtils.writeErrorResponse(
                exchange, objectMapper, HttpStatus.FORBIDDEN, errorResponse);
    }
    
    /**
     * Handles generic errors (non-authorization-specific) by writing an error response
     * and completing the reactive chain.
     * 
     * <p>This method ensures that all errors result in onComplete() being called
     * (not onError()), which is the expected behavior for filter error handling.</p>
     * 
     * <p>Returns HTTP 403 FORBIDDEN for generic errors in authorization context,
     * as these errors occur during permission checking and should be treated as
     * authorization failures rather than internal server errors.</p>
     * 
     * @param exchange the current server web exchange
     * @param error the error that occurred
     * @return a Mono that completes after writing the error response
     */
    private Mono<Void> handleGenericError(ServerWebExchange exchange, Throwable error) {
        // Get correlation ID from request headers (set by CorrelationIdFilter)
        String correlationId = exchange.getRequest().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
        UUID environmentId = exchange.getAttribute("environmentId");
        
        // Log the error with context
        log.error("Generic error in authorization filter [correlationId={}, environmentId={}]: {}", 
            correlationId, environmentId, error.getMessage(), error);
        
        // Create generic error response
        SifErrorResponse errorResponse = new SifErrorResponse(
            UUID.randomUUID().toString(),
            HttpStatus.FORBIDDEN.value(),
            "GATEWAY_001",
            "An error occurred while processing the request",
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        return GatewayErrorResponseUtils.writeErrorResponse(
                exchange, objectMapper, HttpStatus.FORBIDDEN, errorResponse);
    }
    
    @Override
    public int getOrder() {
        return 4; // Fourth filter in the chain (after ServiceTypeValidationFilter at Order 3)
    }

}
