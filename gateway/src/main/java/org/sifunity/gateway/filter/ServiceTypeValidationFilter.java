// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.constant.SifServiceType;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.gateway.filter.ServiceTypePathMapping.PathCategory;
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
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Filter that validates the {@code serviceType} HTTP header against the URL path pattern.
 *
 * <p>Per SIF 3.3 §5.1, different URL paths correspond to specific service types.
 * This filter rejects requests where the serviceType header does not match the
 * valid set for the target path with HTTP 400 and an actionable error response.</p>
 *
 * <p>This filter runs at order 3, after AuthenticationFilter (order 2) and before
 * AuthorizationFilter (order 4). Unauthenticated requests are rejected first;
 * invalid serviceType requests don't waste ACL lookup resources.</p>
 *
 * <p>Behavior:</p>
 * <ul>
 *   <li>Exempt paths (actuator, swagger, admin, domains, solutions, environment registration) skip validation</li>
 *   <li>If path does not map to a known category → pass through</li>
 *   <li>If serviceType header absent or blank → pass through (SIF default applies)</li>
 *   <li>If header value is not a recognized SIF serviceType → reject with 400 (SVC_TYPE_002)</li>
 *   <li>If header value is a valid SIF type but not in path's valid set → reject with 400 (SVC_TYPE_001)</li>
 *   <li>Case-insensitive comparison via {@link SifServiceType#fromString(String)}</li>
 * </ul>
 *
 * <p>Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3,
 * 5.1, 5.2, 5.3, 5.4, 5.5, 12.1, 12.2, 12.3</p>
 *
 * @see ServiceTypePathMapping
 * @see SifServiceType
 */
@Component
public class ServiceTypeValidationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ServiceTypeValidationFilter.class);

    private static final String ALL_SIF_SERVICE_TYPES = Arrays.stream(SifServiceType.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

    private final ObjectMapper objectMapper;

    public ServiceTypeValidationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return 3; // After AuthenticationFilter (2), before AuthorizationFilter (4)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        // Skip exempt paths
        if (isExemptPath(path, method)) {
            return chain.filter(exchange);
        }

        // Parse path to extract resource path after /{domainId}/{solutionId}
        ParsedPath parsed = UrlPrefixParser.parse(path);
        String resourcePath = parsed.resourcePath();

        // Determine path category
        PathCategory category = ServiceTypePathMapping.categorize(resourcePath);
        if (category == null) {
            // Path not subject to serviceType validation
            return chain.filter(exchange);
        }

        // Extract serviceType header (use first value if multiple)
        String serviceTypeValue = request.getHeaders().getFirst(SifHeaders.SERVICE_TYPE);
        if (serviceTypeValue == null || serviceTypeValue.isBlank()) {
            // Absent/blank header → pass through (SIF default applies)
            return chain.filter(exchange);
        }

        // Case-insensitive lookup
        Optional<SifServiceType> resolvedType = SifServiceType.fromString(serviceTypeValue);

        if (resolvedType.isEmpty()) {
            // Not a recognized SIF serviceType at all → SVC_TYPE_002
            return rejectUnrecognizedServiceType(exchange, path, serviceTypeValue, category);
        }

        // Check if the resolved type is in the path's valid set
        Set<String> validSet = category.getValidServiceTypes();
        if (!validSet.contains(resolvedType.get().name())) {
            // Valid SIF type but not valid for this path → SVC_TYPE_001
            return rejectInvalidServiceTypeForPath(exchange, path, serviceTypeValue, category);
        }

        // Valid serviceType for this path → pass through
        return chain.filter(exchange);
    }

    /**
     * Checks if a path is exempt from serviceType validation.
     *
     * <p>Exempt paths:</p>
     * <ul>
     *   <li>{@code /actuator/**} — health checks, readiness probes</li>
     *   <li>{@code /swagger-ui/**}, {@code /v3/api-docs/**} — documentation</li>
     *   <li>{@code /admin-users/**}, {@code /admin-proxy/**} — admin auth</li>
     *   <li>Domain management paths ({@code /domains/**})</li>
     *   <li>Solution management paths ({@code /{domainId}/solutions/**})</li>
     *   <li>POST {@code /{d}/{s}/environments} — environment registration</li>
     * </ul>
     */
    private boolean isExemptPath(String path, HttpMethod method) {
        // Actuator paths
        if (path.startsWith("/actuator")) {
            return true;
        }

        // OpenAPI/Swagger paths
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) {
            return true;
        }

        // Admin user and proxy paths
        if (path.startsWith("/admin-users") || path.startsWith("/admin-proxy")) {
            return true;
        }

        ParsedPath parsed = UrlPrefixParser.parse(path);

        // Domain management paths (/domains/**)
        if (parsed.isDomainManagementPath()) {
            return true;
        }

        // Solution management paths (/{domainId}/solutions/**)
        if (parsed.isSolutionManagementPath()) {
            return true;
        }

        // POST /{d}/{s}/environments — environment registration
        if (parsed.isResourcePath() && method == HttpMethod.POST
                && parsed.resourcePath().equals("/environments")) {
            return true;
        }

        return false;
    }

    /**
     * Rejects a request where the serviceType header value is not a recognized SIF serviceType.
     * Error code: SVC_TYPE_002
     */
    private Mono<Void> rejectUnrecognizedServiceType(ServerWebExchange exchange, String path,
                                                      String invalidValue, PathCategory category) {
        UUID environmentId = exchange.getAttribute("environmentId");
        Set<String> validForPath = category.getValidServiceTypes();

        String description = String.format(
                "serviceType '%s' is not a recognized SIF service type. "
                        + "Valid SIF service types are: %s. "
                        + "For path '%s', valid values are: %s",
                invalidValue,
                ALL_SIF_SERVICE_TYPES,
                path,
                String.join(", ", validForPath));

        log.warn("serviceType validation failed [environmentId={}, path={}, value={}, expected={}]: "
                        + "unrecognized SIF service type",
                environmentId, path, invalidValue, validForPath);

        return writeErrorResponse(exchange, "SVC_TYPE_002",
                "Invalid serviceType header value", description);
    }

    /**
     * Rejects a request where the serviceType header is a valid SIF type but not valid for the path.
     * Error code: SVC_TYPE_001
     */
    private Mono<Void> rejectInvalidServiceTypeForPath(ServerWebExchange exchange, String path,
                                                        String invalidValue, PathCategory category) {
        UUID environmentId = exchange.getAttribute("environmentId");
        Set<String> validForPath = category.getValidServiceTypes();

        String description = String.format(
                "serviceType '%s' is not valid for path '%s'. "
                        + "Valid values are: %s",
                invalidValue, path, String.join(", ", validForPath));

        log.warn("serviceType validation failed [environmentId={}, path={}, value={}, expected={}]: "
                        + "serviceType not valid for path",
                environmentId, path, invalidValue, validForPath);

        return writeErrorResponse(exchange, "SVC_TYPE_001",
                "Invalid serviceType for path", description);
    }

    /**
     * Writes a SIF-compliant error response with HTTP 400 status.
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, String errorCode,
                                           String message, String description) {
        SifErrorResponse errorResponse = new SifErrorResponse(
                UUID.randomUUID().toString(), HttpStatus.BAD_REQUEST.value(), errorCode, message,
                description, "BROKER", Instant.now(), "serviceType", null);
        return GatewayErrorResponseUtils.writeErrorResponse(
                exchange, objectMapper, HttpStatus.BAD_REQUEST, errorResponse);
    }
}
