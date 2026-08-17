// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.gateway.util.GatewayErrorResponseUtils;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filter that validates domain/solution membership for authenticated environments.
 *
 * <p>This filter runs fifth in the filter chain (Order 5) after the AuthorizationFilter.
 * It verifies that the authenticated environment belongs to the domain and solution
 * specified in the URL path prefix.</p>
 *
 * <p>Validation rules:</p>
 * <ul>
 *   <li>Global admin environments (GLOBAL_DOMAIN/GLOBAL_SOLUTION) skip membership validation entirely</li>
 *   <li>Domain management paths ({@code /domains/**}) skip validation</li>
 *   <li>Solution management paths ({@code /{domainId}/solutions/**}) validate domain membership only</li>
 *   <li>All other prefixed paths validate both domain and solution membership</li>
 * </ul>
 *
 * <p>Exchange Attributes Read:</p>
 * <ul>
 *   <li>environmentType - Type of environment (set by AuthenticationFilter)</li>
 *   <li>domainId - Domain ID from the URL path (set by AuthenticationFilter)</li>
 *   <li>solutionId - Solution ID from the URL path (set by AuthenticationFilter)</li>
 *   <li>tokenDomainId - Domain ID from TokenValidationResponse (set by AuthenticationFilter)</li>
 *   <li>tokenSolutionId - Solution ID from TokenValidationResponse (set by AuthenticationFilter)</li>
 * </ul>
 *
 * <p>Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8</p>
 */
@Component
public class MembershipValidationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MembershipValidationFilter.class);

    private final ObjectMapper objectMapper;

    public MembershipValidationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        ParsedPath parsed = UrlPrefixParser.parse(path);

        // Domain management paths (/domains/**, /actuator/**) — skip validation
        if (parsed.isDomainManagementPath()) {
            log.debug("Path {} is domain-management tier, skipping membership validation", path);
            return chain.filter(exchange);
        }

        // If no environmentType attribute, the request was not authenticated (exempt path) — skip
        Object environmentType = exchange.getAttribute("environmentType");
        if (environmentType == null) {
            log.debug("No environmentType attribute, skipping membership validation for path {}", path);
            return chain.filter(exchange);
        }

        // Global admin environments bypass membership validation
        String tokenDomainId = exchange.getAttribute("tokenDomainId");
        String tokenSolutionId = exchange.getAttribute("tokenSolutionId");
        if (GlobalAdminConstants.isGlobalAdmin(tokenDomainId, tokenSolutionId)) {
            log.debug("Global admin environment, skipping membership validation for path {}", path);
            return chain.filter(exchange);
        }

        // Solution management paths — validate domain membership only
        if (parsed.isSolutionManagementPath()) {
            Mono<Void> domainError = checkDomainMembership(exchange, parsed.domainId());
            if (domainError != null) {
                return domainError;
            }
            return chain.filter(exchange);
        }

        // Full resource paths — validate both domain and solution membership
        if (parsed.isResourcePath()) {
            Mono<Void> domainError = checkDomainMembership(exchange, parsed.domainId());
            if (domainError != null) {
                return domainError;
            }
            Mono<Void> solutionError = checkSolutionMembership(exchange, parsed.solutionId());
            if (solutionError != null) {
                return solutionError;
            }
            return chain.filter(exchange);
        }

        // Fallback — no prefix to validate
        return chain.filter(exchange);
    }

    /**
     * Checks domain membership. Returns a 403 error Mono if validation fails, or null if valid.
     */
    private Mono<Void> checkDomainMembership(ServerWebExchange exchange, String urlDomainId) {
        String tokenDomainId = exchange.getAttribute("tokenDomainId");

        if (tokenDomainId == null || !tokenDomainId.equals(urlDomainId)) {
            log.warn("Domain membership mismatch: URL domainId='{}', token domainId='{}'",
                    urlDomainId, tokenDomainId);
            return writeForbiddenResponse(exchange, "MEMBERSHIP_001",
                    "Environment does not belong to domain '" + urlDomainId + "'");
        }

        return null;
    }

    /**
     * Checks solution membership. Returns a 403 error Mono if validation fails, or null if valid.
     */
    private Mono<Void> checkSolutionMembership(ServerWebExchange exchange, String urlSolutionId) {
        String tokenSolutionId = exchange.getAttribute("tokenSolutionId");

        if (tokenSolutionId == null || !tokenSolutionId.equals(urlSolutionId)) {
            log.warn("Solution membership mismatch: URL solutionId='{}', token solutionId='{}'",
                    urlSolutionId, tokenSolutionId);
            return writeForbiddenResponse(exchange, "MEMBERSHIP_002",
                    "Environment does not belong to solution '" + urlSolutionId + "'");
        }

        return null;
    }

    private Mono<Void> writeForbiddenResponse(ServerWebExchange exchange,
                                               String errorCode,
                                               String message) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);

        log.error("Membership validation failed [correlationId={}, errorCode={}]: {}",
                correlationId, errorCode, message);

        return GatewayErrorResponseUtils.writeErrorResponse(
                exchange, objectMapper, HttpStatus.FORBIDDEN, errorCode, message, null);
    }

    @Override
    public int getOrder() {
        return 5; // Fifth filter in the chain (after AuthorizationFilter at Order 4)
    }
}
