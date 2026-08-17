// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import org.sifunity.broker.common.constant.SifHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filter that injects environment context headers for backend services.
 * 
 * <p>This filter runs seventh in the filter chain (Order 7) after the ProxyImpersonationFilter.
 * It injects environment context headers (X-Environment-Id and X-Environment-Name) into
 * the request so that backend services know which environment made the request without
 * re-authenticating.</p>
 * 
 * <p>Behavior:</p>
 * <ul>
 *   <li>Extracts environment ID and name from exchange attributes (set by AuthenticationFilter)</li>
 *   <li>Injects X-Environment-Id header with the environment UUID</li>
 *   <li>Injects X-Environment-Name header with the application name</li>
 *   <li>Injects X-Internal-Service-Token when fingerprint is "broker-services" (so downstream
 *       services can verify the impersonation was gateway-authenticated)</li>
 *   <li>Skips injection if no environment context (exempt paths like POST /environments, /actuator/health)</li>
 * </ul>
 * 
 * <p>Exchange Attributes Used:</p>
 * <ul>
 *   <li>environmentId - UUID of the authenticated environment</li>
 *   <li>environmentName - Application name of the environment</li>
 * </ul>
 * 
 * <p>Requirements: 4.1, 4.2, 4.6</p>
 * 
 * @see AuthenticationFilter
 * @see SifHeaders#X_ENVIRONMENT_ID
 * @see SifHeaders#X_ENVIRONMENT_NAME
 */
@Component
public class EnvironmentContextFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(EnvironmentContextFilter.class);

    private final String internalServiceToken;

    public EnvironmentContextFilter(
            @Value("${broker.internal-service-token:}") String internalServiceToken) {
        this.internalServiceToken = internalServiceToken;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Extract environment details from exchange attributes (set by AuthenticationFilter)
        UUID environmentId = exchange.getAttribute("environmentId");
        String environmentName = exchange.getAttribute("environmentName");
        
        if (environmentId == null) {
            // No environment context (exempt path) — still strip any client-supplied
            // internal headers to prevent non-admin callers from spoofing identity.
            // On exempt paths there is no authenticated context, so impersonation
            // headers must never pass through to backends.
            log.debug("No environment context found, stripping internal headers on exempt path");
            ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(SifHeaders.X_ENVIRONMENT_ID);
                    h.remove(SifHeaders.X_ENVIRONMENT_NAME);
                    h.remove(SifHeaders.X_ENVIRONMENT_FINGERPRINT);
                    h.remove(SifHeaders.X_SOLUTION_UUID);
                    h.remove("X-Internal-Service-Token");
                })
                .build();
            return chain.filter(exchange.mutate().request(strippedRequest).build());
        }
        
        log.debug("Injecting environment context headers: environmentId={}, environmentName={}",
            environmentId, environmentName);
        
        // Strip any client-supplied internal headers to prevent spoofing,
        // then inject the authenticated values from the gateway's auth filter.
        // X-Correlation-Id is intentionally NOT stripped — clients may provide
        // their own correlation ID for distributed tracing across systems.
        String fingerprint = exchange.getAttribute("fingerprint");
        UUID solutionUuid = exchange.getAttribute("solutionUuid");
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
            .headers(h -> {
                h.remove(SifHeaders.X_ENVIRONMENT_ID);
                h.remove(SifHeaders.X_ENVIRONMENT_NAME);
                h.remove(SifHeaders.X_ENVIRONMENT_FINGERPRINT);
                h.remove(SifHeaders.X_SOLUTION_UUID);
                h.remove("X-Internal-Service-Token");
            })
            .header(SifHeaders.X_ENVIRONMENT_ID, environmentId.toString())
            .header(SifHeaders.X_ENVIRONMENT_NAME, environmentName);
        
        if (fingerprint != null && !fingerprint.isBlank()) {
            requestBuilder.header(SifHeaders.X_ENVIRONMENT_FINGERPRINT, fingerprint);
            // Inject internal service token for broker-services impersonation so downstream
            // services can verify the fingerprint was set by a trusted gateway, not spoofed.
            if ("broker-services".equals(fingerprint)
                    && internalServiceToken != null && !internalServiceToken.isEmpty()) {
                requestBuilder.header("X-Internal-Service-Token", internalServiceToken);
            }
        }
        
        if (solutionUuid != null) {
            requestBuilder.header(SifHeaders.X_SOLUTION_UUID, solutionUuid.toString());
        }
        
        ServerHttpRequest mutatedRequest = requestBuilder.build();
        
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
    
    @Override
    public int getOrder() {
        return 7; // Seventh filter in the chain (after ProxyImpersonationFilter)
    }
}
