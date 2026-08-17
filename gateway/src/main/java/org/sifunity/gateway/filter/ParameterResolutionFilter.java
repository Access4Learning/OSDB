// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.exception.ParameterConflictException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.util.ParameterResolver;
import org.sifunity.broker.common.util.ParameterResolver.ResolvedParams;
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

import java.time.Instant;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves SIF parameters from matrix params and HTTP headers, applies defaults,
 * and sets canonical headers for downstream services.
 *
 * <p>This filter runs at order 8 (after {@link EnvironmentContextFilter} at order 7).
 * It merges values from URL matrix parameters (SIF 3.3 legacy) and HTTP headers
 * (SIF 3.7 modern), detects conflicts, injects defaults for missing zone/context,
 * and forwards resolved values as canonical headers.</p>
 *
 * <p>Resolution steps:</p>
 * <ol>
 *   <li>Retrieve matrix params from exchange attribute (set by {@link MatrixParameterFilter})</li>
 *   <li>Read zoneId, contextId, deleteMessageId from request headers</li>
 *   <li>Merge via {@link ParameterResolver#resolve} — conflict → HTTP 400</li>
 *   <li>Apply defaults: zoneId from environment's defaultZoneId, contextId → "DEFAULT"</li>
 *   <li>Set canonical headers on mutated request for downstream</li>
 * </ol>
 *
 * <p>Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3, 4.4</p>
 *
 * @see MatrixParameterFilter
 * @see ParameterResolver
 * @see EnvironmentContextFilter
 */
@Component
public class ParameterResolutionFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ParameterResolutionFilter.class);

    private static final String DEFAULT_CONTEXT_ID = "DEFAULT";

    /**
     * Path segments that require mandatory zoneId resolution (SIF data connectors).
     * Infrastructure endpoints (queues, subscriptions, environments, etc.) do NOT
     * require zone/context — they manage resources, not route SIF messages.
     */
    private static final java.util.Set<String> ZONE_REQUIRED_SEGMENTS = java.util.Set.of(
            "events", "requests", "responses", "services"
    );

    private final ObjectMapper objectMapper;

    public ParameterResolutionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Determines whether the given request path requires mandatory zoneId resolution.
     * Only SIF connector paths (/events, /requests, /responses, /services) require zone.
     * Infrastructure paths (/queues, /subscriptions, /environments, etc.) do not.
     */
    private boolean isZoneRequired(String path) {
        // Path format: /{domainId}/{solutionId}/{resource} or /{resource}
        String[] segments = path.split("/");
        // Find the resource segment (last meaningful segment before any ID)
        // For /{domainId}/{solutionId}/{resource}: segment[3]
        // For /{resource}: segment[1]
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            if (ZONE_REQUIRED_SEGMENTS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip if no environment context (exempt paths like health checks)
        if (exchange.getAttribute("environmentId") == null) {
            return chain.filter(exchange);
        }

        // 1. Get matrix params from exchange attribute (set by MatrixParameterFilter)
        Map<String, String> matrixParams = exchange.getAttribute(MatrixParameterFilter.MATRIX_PARAMS_ATTR);
        if (matrixParams == null) {
            matrixParams = Map.of();
        }

        // 2. Get relevant headers from request
        Map<String, String> headers = new HashMap<>();
        String headerZoneId = exchange.getRequest().getHeaders().getFirst(SifHeaders.ZONE_ID);
        String headerContextId = exchange.getRequest().getHeaders().getFirst(SifHeaders.CONTEXT_ID);
        String headerDeleteMessageId = exchange.getRequest().getHeaders().getFirst("deleteMessageId");

        if (headerZoneId != null && !headerZoneId.isEmpty()) {
            headers.put("zoneId", headerZoneId);
        }
        if (headerContextId != null && !headerContextId.isEmpty()) {
            headers.put("contextId", headerContextId);
        }
        if (headerDeleteMessageId != null && !headerDeleteMessageId.isEmpty()) {
            headers.put("deleteMessageId", headerDeleteMessageId);
        }

        // 3. Resolve (conflict detection)
        ResolvedParams resolved;
        try {
            resolved = ParameterResolver.resolve(matrixParams, headers);
        } catch (ParameterConflictException e) {
            log.warn("Parameter conflict detected: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.BAD_REQUEST,
                    "PARAM_CONFLICT", e.getMessage());
        }

        // 4. Apply defaults
        String zoneId = resolved.zoneId();
        String contextId = resolved.contextId();
        String deleteMessageId = resolved.deleteMessageId();

        // Default zoneId from environment's configured defaultZoneId
        if (zoneId == null || zoneId.isEmpty()) {
            zoneId = exchange.getAttribute("defaultZoneId");
        }

        // If still no zoneId, only reject for connector paths that require it
        if (zoneId == null || zoneId.isEmpty()) {
            String path = exchange.getRequest().getURI().getPath();
            if (isZoneRequired(path)) {
                String environmentName = exchange.getAttribute("environmentName");
                String msg = "zoneId is required but no value or default was found for environment '"
                        + (environmentName != null ? environmentName : "unknown") + "'";
                log.warn(msg);
                return writeErrorResponse(exchange, HttpStatus.BAD_REQUEST,
                        "MISSING_ZONE_ID", msg);
            } else {
                // Infrastructure endpoint — pass through without zone/context headers
                log.debug("No zoneId resolved for infrastructure path {}, passing through", path);
                return chain.filter(exchange);
            }
        }

        // Default contextId to "DEFAULT"
        if (contextId == null || contextId.isEmpty()) {
            contextId = DEFAULT_CONTEXT_ID;
        }

        // 5. Set canonical headers on mutated request for downstream
        String finalZoneId = zoneId;
        String finalContextId = contextId;
        String finalDeleteMessageId = deleteMessageId;

        // Retrieve the environment's configured defaultZoneId for downstream injection
        String defaultZoneId = exchange.getAttribute("defaultZoneId");

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .headers(h -> {
                    // Replace any existing zoneId/contextId headers with resolved values
                    h.remove(SifHeaders.ZONE_ID);
                    h.remove(SifHeaders.CONTEXT_ID);
                    h.set(SifHeaders.ZONE_ID, finalZoneId);
                    h.set(SifHeaders.CONTEXT_ID, finalContextId);

                    // Set deleteMessageId if resolved
                    if (finalDeleteMessageId != null && !finalDeleteMessageId.isEmpty()) {
                        h.remove("deleteMessageId");
                        h.set("deleteMessageId", finalDeleteMessageId);
                    }

                    // Inject X-Default-Zone-Id for connector-level fallback
                    if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                        h.set("X-Default-Zone-Id", defaultZoneId);
                    }
                });

        ServerHttpRequest mutatedRequest = requestBuilder.build();

        log.debug("Resolved parameters: zoneId={}, contextId={}, deleteMessageId={}",
                finalZoneId, finalContextId, finalDeleteMessageId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return 8; // After EnvironmentContextFilter at order 7
    }

    /**
     * Writes a JSON error response and completes the exchange.
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status,
                                          String errorCode, String message) {
        SifErrorResponse errorResponse = new SifErrorResponse(
                UUID.randomUUID().toString(), status.value(), errorCode, message,
                null, "GATEWAY", Instant.now(), null, null);
        return GatewayErrorResponseUtils.writeErrorResponse(
                exchange, objectMapper, status, errorResponse);
    }
}
