// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.exception.MalformedAuthHeaderException;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.util.CredentialMasker;
import org.sifunity.broker.common.util.LoggingUtils;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.util.GatewayErrorResponseUtils;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.metrics.GatewayMetricsService;
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
 * Filter that validates authentication credentials via the Environment Service.
 * 
 * <p>This filter runs second in the filter chain (Order 2) after the CorrelationIdFilter.
 * It validates sessionToken and HMAC signatures by calling the Environment Service's
 * internal validate-token API.</p>
 * 
 * <p>Behavior:</p>
 * <ul>
 *   <li>Exempt paths (POST /environments, /actuator/health) skip authentication</li>
 *   <li>Extracts Authorization header from the request</li>
 *   <li>Parses header using SifAuthenticationUtils.parseAuthHeader</li>
 *   <li>Calls EnvironmentServiceClient.validateToken</li>
 *   <li>Stores environment details in exchange attributes for downstream filters</li>
 *   <li>Returns 401 Unauthorized for authentication failures</li>
 * </ul>
 * 
 * <p>Exchange Attributes Set:</p>
 * <ul>
 *   <li>environmentId - UUID of the authenticated environment</li>
 *   <li>environmentName - Application name of the environment</li>
 *   <li>environmentType - Type of environment (DIRECT, BROKERED, ADMIN)</li>
 *   <li>defaultZoneId - Default zone ID for the environment</li>
 *   <li>tokenDomainId - Domain ID from the TokenValidationResponse (the environment's actual domain)</li>
 *   <li>tokenSolutionId - Solution ID from the TokenValidationResponse (the environment's actual solution)</li>
 *   <li>domainId - Domain ID extracted from the URL path</li>
 *   <li>solutionId - Solution ID extracted from the URL path</li>
 * </ul>
 * 
 * <p>Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4</p>
 * 
 * @see EnvironmentServiceClient
 * @see SifAuthenticationUtils
 * @see TokenValidationRequest
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);
    
    private final EnvironmentServiceClient environmentServiceClient;
    private final ObjectMapper objectMapper;
    private final GatewayMetricsService metricsService;
    
    /**
     * Constructs an AuthenticationFilter with the specified dependencies.
     * 
     * @param environmentServiceClient the client for calling Environment Service APIs
     * @param objectMapper the Jackson ObjectMapper for JSON serialization
     * @param metricsService the metrics service for recording authentication metrics
     */
    public AuthenticationFilter(
            EnvironmentServiceClient environmentServiceClient,
            ObjectMapper objectMapper,
            GatewayMetricsService metricsService) {
        this.environmentServiceClient = environmentServiceClient;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }
    
    @Timed(value = "sif.auth.hmac.verification",
           description = "HMAC authentication verification latency",
           percentiles = {0.5, 0.95, 0.99})
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();
        
        // Block internal API paths immediately with 403 FORBIDDEN
        // This provides defense-in-depth alongside InternalApiBlockingFilter
        if (path.startsWith("/internal")) {
            log.warn("Blocked request to internal API path: {}", path);
            return GatewayErrorResponseUtils.writeErrorResponse(
                    exchange, objectMapper, HttpStatus.FORBIDDEN,
                    "ACCESS_001", "Access denied",
                    "Internal API endpoints are not accessible externally");
        }
        
        // Check if path is exempt from authentication
        if (isExemptPath(path, method)) {
            log.debug("Path {} is exempt from authentication", path);
            return chain.filter(exchange);
        }
        
        // Convert Basic Auth from Swagger UI to SIF HMAC-SHA256 (non-prod only)
        ServerWebExchange currentExchange = exchange;
        ServerHttpRequest currentRequest = request;
        String rawAuthHeader = request.getHeaders().getFirst(SifHeaders.AUTHORIZATION);
        if (rawAuthHeader != null && rawAuthHeader.startsWith("Basic ") && isFromSwaggerUi(request)) {
            try {
                String decoded = new String(
                    java.util.Base64.getDecoder().decode(rawAuthHeader.substring(6)),
                    java.nio.charset.StandardCharsets.UTF_8);
                int colonIdx = decoded.indexOf(':');
                if (colonIdx > 0) {
                    String sessionToken = decoded.substring(0, colonIdx);
                    String sharedSecret = decoded.substring(colonIdx + 1);
                    String swaggerTimestamp = Instant.now().toString();
                    String hmac = SifAuthenticationUtils.computeHmac(sessionToken, swaggerTimestamp, sharedSecret);
                    String sifCredentials = java.util.Base64.getEncoder().encodeToString(
                        (sessionToken + ":" + hmac).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    rawAuthHeader = "SIF_HMACSHA256 " + sifCredentials;
                    
                    // Mutate request with proper SIF headers
                    currentRequest = request.mutate()
                        .headers(h -> {
                            h.remove(SifHeaders.AUTHORIZATION);
                            h.remove(SifHeaders.TIMESTAMP);
                        })
                        .header(SifHeaders.AUTHORIZATION, rawAuthHeader)
                        .header(SifHeaders.TIMESTAMP, swaggerTimestamp)
                        .build();
                    currentExchange = exchange.mutate().request(currentRequest).build();
                    log.debug("Gateway Swagger adapter: converted Basic Auth for sessionToken={}", CredentialMasker.mask(sessionToken));
                }
            } catch (Exception e) {
                log.warn("Failed to convert Basic Auth to SIF HMAC in gateway: {}", e.getMessage());
            }
        }
        
        // Use the (possibly mutated) exchange and request for the rest of the filter
        final ServerWebExchange finalExchange = currentExchange;
        final ServerHttpRequest finalRequest = currentExchange.getRequest();
        
        // Extract Authorization header
        String authHeader = finalRequest.getHeaders().getFirst(SifHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            log.warn("Missing Authorization header for path: {}", path);
            return handleAuthenticationError(finalExchange, 
                new InvalidTokenException("Missing Authorization header"));
        }
        
        // Extract timestamp header
        String timestamp = finalRequest.getHeaders().getFirst(SifHeaders.TIMESTAMP);
        if (timestamp == null || timestamp.isBlank()) {
            log.warn("Missing timestamp header for path: {}", path);
            return handleAuthenticationError(finalExchange, 
                new InvalidTokenException("Missing timestamp header"));
        }
        
        try {
            // Parse Authorization header
            SifAuthenticationUtils.AuthHeader parsedHeader = 
                SifAuthenticationUtils.parseAuthHeader(authHeader);
            
            log.debug("Parsed Authorization header for sessionToken: {}", 
                CredentialMasker.mask(parsedHeader.sessionToken()));
            
            // Call Environment Service to validate token
            TokenValidationRequest validationRequest = new TokenValidationRequest(
                parsedHeader.sessionToken(),
                timestamp,
                parsedHeader.hash()
            );
            
            return environmentServiceClient.validateToken(validationRequest)
                .onErrorResume(e -> {
                    // Check if this is a timeout error - convert to ServiceUnavailableException
                    if (e instanceof IllegalStateException && 
                        e.getMessage() != null && 
                        e.getMessage().contains("Timeout on blocking read")) {
                        return Mono.error(new org.sifunity.broker.common.exception.ServiceUnavailableException(
                            "Gateway", "Request timeout"));
                    }
                    // Let InvalidTokenException and ServiceUnavailableException pass through
                    if (e instanceof InvalidTokenException || 
                        e instanceof org.sifunity.broker.common.exception.ServiceUnavailableException) {
                        return Mono.error(e);
                    }
                    // Handle all other errors from validateToken (connection errors, etc.)
                    log.error("Unexpected error during token validation: {}", e.getMessage(), e);
                    // Convert to InvalidTokenException for consistent error handling
                    return Mono.error(new InvalidTokenException("Authentication failed"));
                })
                .flatMap(response -> {
                    log.debug("Authentication successful for environment: {} ({})",
                        response.environmentId(), response.applicationName());
                    
                    // Record authentication success metric
                    metricsService.recordAuthenticationSuccess(response.environmentId());
                    
                    // Store environment details in exchange attributes
                    finalExchange.getAttributes().put("environmentId", response.environmentId());
                    finalExchange.getAttributes().put("environmentName", response.applicationName());
                    finalExchange.getAttributes().put("environmentType", response.type());
                    // Only put nullable fields if not null (ConcurrentHashMap doesn't allow null values)
                    if (response.defaultZoneId() != null) {
                        finalExchange.getAttributes().put("defaultZoneId", response.defaultZoneId());
                    }
                    
                    // Store token's domain/solution membership from TokenValidationResponse
                    // These represent the domain/solution the environment actually belongs to
                    if (response.domainId() != null) {
                        finalExchange.getAttributes().put("tokenDomainId", response.domainId());
                    }
                    if (response.solutionId() != null) {
                        finalExchange.getAttributes().put("tokenSolutionId", response.solutionId());
                    }
                    if (response.solutionUuid() != null) {
                        finalExchange.getAttributes().put("solutionUuid", response.solutionUuid());
                    }
                    
                    // Extract domainId and solutionId from URL path and store as exchange attributes
                    UrlPrefixParser.ParsedPath parsed = UrlPrefixParser.parse(path);
                    if (parsed.domainId() != null) {
                        finalExchange.getAttributes().put("domainId", parsed.domainId());
                    }
                    if (parsed.solutionId() != null) {
                        finalExchange.getAttributes().put("solutionId", parsed.solutionId());
                    }
                    
                    // Stamp authenticated fingerprint: strip client-supplied value, inject trusted value
                    String fingerprint = response.fingerprint();
                    boolean hasFingerprint = fingerprint != null && !fingerprint.isBlank();
                    
                    // Store fingerprint in exchange attributes for downstream filters
                    if (hasFingerprint) {
                        finalExchange.getAttributes().put("fingerprint", fingerprint);
                    }
                    
                    // Always strip client-supplied fingerprint header; conditionally inject authenticated value
                    ServerHttpRequest.Builder requestBuilder = finalExchange.getRequest().mutate()
                        .headers(h -> h.remove("fingerprint"));
                    if (hasFingerprint) {
                        requestBuilder.header("fingerprint", fingerprint);
                    }
                    ServerHttpRequest mutatedRequest = requestBuilder.build();
                    
                    // Continue with the filter chain - errors from chain.filter() will propagate to GlobalErrorWebExceptionHandler
                    return chain.filter(finalExchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(InvalidTokenException.class, e -> {
                    // Only handle InvalidTokenException (authentication errors)
                    return handleAuthenticationError(finalExchange, e);
                })
                .onErrorResume(org.sifunity.broker.common.exception.ServiceUnavailableException.class, e -> {
                    // Handle ServiceUnavailableException (service errors)
                    return handleAuthenticationError(finalExchange, e);
                });
                
        } catch (MalformedAuthHeaderException e) {
            log.warn("Malformed Authorization header for path: {}", path);
            return handleAuthenticationError(finalExchange, e);
        }
    }
    
    /**
     * Checks if a path is exempt from authentication.
     * 
     * <p>Exempt paths:</p>
     * <ul>
     *   <li>Actuator paths ({@code /actuator/**}) — health checks, readiness probes</li>
     * </ul>
     *
     * <p>All other paths require authentication, including environment registration,
     * domain management, and solution management. Only the global admin environment
     * (bootstrapped on startup) can perform administrative operations.</p>
     * 
     * @param path the request path
     * @param method the HTTP method
     * @return true if the path is exempt from authentication, false otherwise
     */
    private boolean isExemptPath(String path, HttpMethod method) {
        // Actuator paths (/actuator/**) are exempt — health checks, readiness probes
        if (path.startsWith("/actuator")) {
            return true;
        }

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

        // POST /environments is exempt (environment registration)
        // Supports both bare path and domain-scoped path (/{domainId}/{solutionId}/environments)
        UrlPrefixParser.ParsedPath parsed = UrlPrefixParser.parse(path);
        String resourcePath = parsed.resourcePath();
        if (method == HttpMethod.POST && resourcePath.equals("/environments")) {
            return true;
        }

        return false;
    }
    
    /**
     * Checks if the request originates from Swagger UI by inspecting the Referer header.
     * Used to scope the Basic Auth → SIF HMAC conversion to Swagger-only requests.
     */
    private boolean isFromSwaggerUi(ServerHttpRequest request) {
        String referer = request.getHeaders().getFirst("Referer");
        return referer != null && (referer.contains("/swagger-ui") || referer.contains("/webjars/swagger-ui"));
    }
    
    /**
     * Handles authentication errors by creating a SIF-compliant error response.
     * 
     * <p>This method logs the error with correlation ID context and returns
     * an HTTP 401 Unauthorized response for authentication failures or
     * 503 Service Unavailable for service unavailability errors.</p>
     * 
     * @param exchange the server web exchange
     * @param error the error that occurred during authentication
     * @return a Mono that completes when the error response is written
     */
    private Mono<Void> handleAuthenticationError(ServerWebExchange exchange, Throwable error) {
        String correlationId = LoggingUtils.getCorrelationId();
        // Fallback to request headers if LoggingUtils doesn't have it
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = exchange.getRequest().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
        }
        log.error("Authentication failed [correlationId={}]: {}", correlationId, error.getMessage());
        
        // Create SIF-compliant error response
        SifErrorResponse errorResponse;
        String errorCode;
        HttpStatus httpStatus;
        
        if (error instanceof SifBrokerException) {
            errorResponse = SifErrorResponse.fromException((SifBrokerException) error);
            errorCode = ((SifBrokerException) error).getErrorCode();
            // Return 503 for ServiceUnavailableException, 401 for other auth errors
            if (error instanceof org.sifunity.broker.common.exception.ServiceUnavailableException) {
                httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            } else {
                httpStatus = HttpStatus.UNAUTHORIZED;
            }
        } else {
            errorCode = "AUTH_001";
            errorResponse = new SifErrorResponse(
                UUID.randomUUID().toString(),
                HttpStatus.UNAUTHORIZED.value(),
                errorCode,
                error.getMessage(),
                null,
                "BROKER",
                Instant.now(),
                null,
                null
            );
            httpStatus = HttpStatus.UNAUTHORIZED;
        }
        
        // Record authentication failure metric
        metricsService.recordAuthenticationFailure(errorCode);
        
        // Set MDC correlation ID on response before calling utility
        if (correlationId != null && !correlationId.isBlank()) {
            exchange.getResponse().getHeaders().set(SifHeaders.X_CORRELATION_ID, correlationId);
        }
        
        return GatewayErrorResponseUtils.writeErrorResponse(
                exchange, objectMapper, httpStatus, errorResponse);
    }
    
    /**
     * Writes a SIF-compliant error response with the specified status, code, and message.
     * Preserves the correlation ID from the original request in the response headers.
     *
     * @param exchange the server web exchange
     * @param status the HTTP status to return
     * @param errorCode the SIF error code
     * @param message the error message
     * @return a Mono that completes when the error response is written
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status,
                                          String errorCode, String message) {
        return GatewayErrorResponseUtils.writeErrorResponse(
                exchange, objectMapper, status, errorCode, message, null);
    }
    
    @Override
    public int getOrder() {
        return 2; // Second filter in the chain (after CorrelationIdFilter)
    }
}
