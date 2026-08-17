// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sifunity.broker.common.constant.ErrorCodes;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.EnvironmentLookupResponse;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.util.GatewayErrorResponseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filter that implements admin proxy impersonation ("run-as") capability.
 *
 * <p>This filter runs sixth in the filter chain (Order 6) after MembershipValidationFilter
 * and before EnvironmentContextFilter. It allows the global admin environment to impersonate
 * a per-solution broker-services environment by overriding the {@code environmentId} and
 * {@code environmentName} exchange attributes.</p>
 *
 * <p>Behavior:</p>
 * <ul>
 *   <li>Always strips {@code X-Run-As-Environment} header from forwarded requests</li>
 *   <li>If header absent/blank → passes through unchanged</li>
 *   <li>If caller is not global admin → logs DEBUG, passes through unchanged</li>
 *   <li>Validates header value (UUID or "broker-services" literal, else 400 PROXY_005)</li>
 *   <li>Resolves target environment via EnvironmentServiceClient</li>
 *   <li>Validates target fingerprint = "broker-services" (else 403 PROXY_003)</li>
 *   <li>Validates target solutionUuid matches URL path (else 403 PROXY_004)</li>
 *   <li>Overrides environmentId and environmentName exchange attributes</li>
 *   <li>Logs INFO with admin UUID, target UUID, target name, request path</li>
 * </ul>
 *
 * <p>Exchange Attributes Read:</p>
 * <ul>
 *   <li>tokenDomainId - Domain ID from TokenValidationResponse</li>
 *   <li>tokenSolutionId - Solution ID from TokenValidationResponse</li>
 *   <li>environmentId - UUID of the authenticated environment (admin's own)</li>
 *   <li>solutionId - Solution ID extracted from the URL path</li>
 * </ul>
 *
 * <p>Exchange Attributes Overridden (on successful impersonation):</p>
 * <ul>
 *   <li>environmentId - overridden with target environment's UUID</li>
 *   <li>environmentName - overridden with target environment's applicationName</li>
 * </ul>
 *
 * <p>Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4,
 * 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 8.1, 8.2, 8.3,
 * 9.1, 9.2, 9.3, 9.4, 10.1, 10.2, 11.1, 11.2, 11.3</p>
 *
 * @see EnvironmentServiceClient
 * @see GlobalAdminConstants
 */
@Component
public class ProxyImpersonationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ProxyImpersonationFilter.class);

    private static final String BROKER_SERVICES_LITERAL = "broker-services";

    private final EnvironmentServiceClient environmentServiceClient;
    private final ObjectMapper objectMapper;

    public ProxyImpersonationFilter(EnvironmentServiceClient environmentServiceClient,
                                    ObjectMapper objectMapper) {
        this.environmentServiceClient = environmentServiceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            String runAsHeader = exchange.getRequest().getHeaders()
                    .getFirst(SifHeaders.X_RUN_AS_ENVIRONMENT);

            // Always strip the header from the forwarded request
            ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
                    .headers(h -> h.remove(SifHeaders.X_RUN_AS_ENVIRONMENT))
                    .build();
            ServerWebExchange strippedExchange = exchange.mutate()
                    .request(strippedRequest)
                    .build();

            // If header absent or blank, pass through
            if (runAsHeader == null || runAsHeader.isBlank()) {
                return chain.filter(strippedExchange);
            }

            // Check if caller is global admin
            String tokenDomainId = exchange.getAttribute("tokenDomainId");
            String tokenSolutionId = exchange.getAttribute("tokenSolutionId");
            if (!GlobalAdminConstants.isGlobalAdmin(tokenDomainId, tokenSolutionId)) {
                // Deliberately NOT an error response. Silently proceeding as the caller's own
                // identity avoids confirming to an unprivileged caller that this header carries
                // any privilege, and lets a client that sends it spuriously still function.
                // Logged at WARN rather than DEBUG because the request then behaves quite
                // differently from what the caller asked for, and DEBUG is not enabled in
                // production - an ignored run-as is otherwise invisible while being the most
                // likely explanation for "my impersonated request was rejected".
                log.warn("X-Run-As-Environment ignored: caller is not a global admin "
                                + "(tokenDomain={}, tokenSolution={}); proceeding as the caller's own "
                                + "identity. Impersonation requires a global admin AND a "
                                + "broker-services target. path: {}",
                        tokenDomainId, tokenSolutionId, exchange.getRequest().getPath().value());
                return chain.filter(strippedExchange);
            }

            // Validate header value: must be UUID or "broker-services" literal
            String headerValue = runAsHeader.trim();
            boolean isBrokerServicesLiteral = BROKER_SERVICES_LITERAL.equals(headerValue);
            UUID targetUuid = null;

            if (!isBrokerServicesLiteral) {
                try {
                    targetUuid = UUID.fromString(headerValue);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid X-Run-As-Environment header value '{}' from admin, path: {}",
                            headerValue, exchange.getRequest().getPath().value());
                    return writeErrorResponse(strippedExchange, HttpStatus.BAD_REQUEST,
                            ErrorCodes.PROXY_INVALID_HEADER,
                            "Invalid X-Run-As-Environment header value");
                }
            }

            // Resolve target environment
            return resolveAndApplyImpersonation(strippedExchange, chain, headerValue,
                    isBrokerServicesLiteral, targetUuid);

        } catch (Exception e) {
            log.error("Unexpected error in ProxyImpersonationFilter: {}", e.getMessage(), e);
            return writeErrorResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCodes.PROXY_SERVICE_UNAVAILABLE,
                    "Unable to resolve impersonation target");
        }
    }

    /**
     * Resolves the target environment and applies impersonation if all validations pass.
     */
    private Mono<Void> resolveAndApplyImpersonation(ServerWebExchange exchange,
                                                     GatewayFilterChain chain,
                                                     String headerValue,
                                                     boolean isBrokerServicesLiteral,
                                                     UUID targetUuid) {
        String path = exchange.getRequest().getPath().value();
        UUID adminEnvironmentId = exchange.getAttribute("environmentId");
        String domainIdFromPath = exchange.getAttribute("domainId");
        String solutionIdFromPath = exchange.getAttribute("solutionId");

        // Resolve target environment via client
        Mono<EnvironmentLookupResponse> lookupMono;
        if (isBrokerServicesLiteral) {
            if (solutionIdFromPath == null || solutionIdFromPath.isBlank()) {
                log.warn("Cannot resolve broker-services without solutionId in path: {}", path);
                return writeErrorResponse(exchange, HttpStatus.BAD_REQUEST,
                        ErrorCodes.PROXY_INVALID_HEADER,
                        "Invalid X-Run-As-Environment header value");
            }
            lookupMono = environmentServiceClient.lookupEnvironmentByFingerprint(
                    BROKER_SERVICES_LITERAL, domainIdFromPath, solutionIdFromPath);
        } else {
            lookupMono = environmentServiceClient.lookupEnvironmentById(targetUuid);
        }

        return lookupMono
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Target environment not found for header value '{}', admin: {}, path: {}",
                            headerValue, adminEnvironmentId, path);
                    return Mono.error(new TargetNotFoundException());
                }))
                .flatMap(target -> {
                    // Validate fingerprint = "broker-services"
                    if (!BROKER_SERVICES_LITERAL.equals(target.fingerprint())) {
                        log.warn("Impersonation rejected: target {} has fingerprint '{}' (not broker-services), "
                                        + "admin: {}, path: {}",
                                target.id(), target.fingerprint(), adminEnvironmentId, path);
                        return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                                ErrorCodes.PROXY_NOT_BROKER_SERVICES,
                                "Only broker-services environments can be impersonated");
                    }

                    // Validate solutionUuid matches URL path solution
                    // For broker-services literal lookups, the environment-service already
                    // scoped the lookup to the correct solution, so this is defense-in-depth.
                    // For UUID-based lookups, we must verify the target belongs to the right solution.
                    if (!isBrokerServicesLiteral && solutionIdFromPath != null) {
                        String targetSolution = target.solutionUuid() != null ? target.solutionUuid().toString() : null;
                        if (!solutionIdFromPath.equals(targetSolution)) {
                            log.warn("Impersonation rejected: target {} belongs to solution {}, "
                                            + "but URL path has solution {}, admin: {}, path: {}",
                                    target.id(), target.solutionUuid(), solutionIdFromPath,
                                    adminEnvironmentId, path);
                            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                                    ErrorCodes.PROXY_SOLUTION_MISMATCH,
                                    "Target environment does not belong to the requested solution");
                        }
                    }

                    // Override exchange attributes
                    exchange.getAttributes().put("environmentId", target.id());
                    exchange.getAttributes().put("environmentName", target.applicationName());
                    exchange.getAttributes().put("fingerprint", target.fingerprint());
                    if (target.solutionUuid() != null) {
                        exchange.getAttributes().put("solutionUuid", target.solutionUuid());
                    }

                    log.info("Proxy impersonation applied: admin={}, target={}, targetName={}, path={}",
                            adminEnvironmentId, target.id(), target.applicationName(), path);

                    return chain.filter(exchange);
                })
                .onErrorResume(TargetNotFoundException.class, e ->
                        writeErrorResponse(exchange, HttpStatus.UNPROCESSABLE_ENTITY,
                                ErrorCodes.PROXY_TARGET_NOT_FOUND,
                                "Target environment not found"))
                .onErrorResume(org.sifunity.broker.common.exception.ServiceUnavailableException.class, e -> {
                    log.error("Environment service unavailable during impersonation lookup: {}", e.getMessage());
                    return writeErrorResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                            ErrorCodes.PROXY_SERVICE_UNAVAILABLE,
                            "Unable to resolve impersonation target");
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected error during proxy impersonation: {}", e.getMessage(), e);
                    return writeErrorResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                            ErrorCodes.PROXY_SERVICE_UNAVAILABLE,
                            "Unable to resolve impersonation target");
                });
    }

    /**
     * Writes a SIF-compliant error response with correlation ID preservation.
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status,
                                          String errorCode, String message) {
        return GatewayErrorResponseUtils.writeErrorResponse(
                exchange, objectMapper, status, errorCode, message, null);
    }

    @Override
    public int getOrder() {
        return 6; // Sixth filter: after MembershipValidationFilter (5), before EnvironmentContextFilter (7)
    }

    /**
     * Internal marker exception for target-not-found scenario.
     * Used to signal switchIfEmpty in the reactive chain.
     */
    private static class TargetNotFoundException extends RuntimeException {
        TargetNotFoundException() {
            super("Target environment not found");
        }
    }
}
