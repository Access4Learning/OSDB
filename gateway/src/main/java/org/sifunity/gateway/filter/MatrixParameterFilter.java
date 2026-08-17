// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import org.sifunity.broker.common.util.MatrixParameterParser;
import org.sifunity.broker.common.util.MatrixParameterParser.ParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

/**
 * Extracts SIF 3.3 matrix parameters from the URL path and stores them as an exchange attribute.
 *
 * <p>SIF 3.3 clients convey {@code zoneId}, {@code contextId}, and {@code messageId} as URL
 * matrix parameters (e.g., {@code /StudentPersonals;zoneId=zone1;contextId=DEFAULT}).
 * This filter parses those parameters using {@link MatrixParameterParser}, stores the
 * extracted map as an exchange attribute, and rewrites the URI with a clean path
 * (matrix params stripped) so downstream routing works correctly.</p>
 *
 * <p>Runs at order {@code -100}, before authentication and routing.</p>
 */
@Component
public class MatrixParameterFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MatrixParameterFilter.class);

    /**
     * Exchange attribute key for the extracted matrix parameters map.
     */
    public static final String MATRIX_PARAMS_ATTR = "matrixParams";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI originalUri = exchange.getRequest().getURI();
        String rawPath = originalUri.getRawPath();

        // Quick check — no semicolons means no matrix params
        if (rawPath == null || !rawPath.contains(";")) {
            exchange.getAttributes().put(MATRIX_PARAMS_ATTR, Map.<String, String>of());
            return chain.filter(exchange);
        }

        // Parse matrix params from the raw path (parser handles query string preservation)
        ParseResult result = MatrixParameterParser.parse(rawPath);

        // Store extracted params as exchange attribute
        exchange.getAttributes().put(MATRIX_PARAMS_ATTR, result.params());

        if (result.params().isEmpty()) {
            // No recognized matrix params found — continue without URI rewrite
            return chain.filter(exchange);
        }

        // Rewrite URI with clean path (matrix params stripped)
        URI newUri = UriComponentsBuilder.fromUri(originalUri)
                .replacePath(result.cleanPath())
                .build(true)
                .toUri();

        log.debug("Extracted matrix params {} from URI: {} -> {}", result.params(), originalUri, newUri);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().uri(newUri).build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Run very early, before authentication and routing
        return -100;
    }
}
