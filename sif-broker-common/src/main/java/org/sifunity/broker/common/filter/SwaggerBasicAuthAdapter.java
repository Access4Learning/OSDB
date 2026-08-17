// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Development-only filter that converts Basic Auth credentials into SIF HMAC-SHA256 headers.
 *
 * <p>This enables Swagger UI's "Try it out" feature to work with the SIF authentication scheme.
 * Users enter their sessionToken as username and sharedSecret as password in Swagger's
 * Authorize dialog. This filter intercepts the Basic Auth header, computes the proper
 * SIF_HMACSHA256 signature, and injects the correct Authorization and timestamp headers
 * before the real auth filter runs.</p>
 *
 * <p>Only triggers when the request originates from Swagger UI (detected via Referer header).</p>
 *
 * <p>This filter is not a Spring component — it must be explicitly registered in the
 * security filter chain by each service's SecurityConfiguration (non-prod profiles only).</p>
 */
public class SwaggerBasicAuthAdapter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SwaggerBasicAuthAdapter.class);
    private static final String BASIC_PREFIX = "Basic ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BASIC_PREFIX) && isFromSwaggerUi(request)) {
            try {
                String base64Credentials = authHeader.substring(BASIC_PREFIX.length());
                String decoded = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
                int colonIdx = decoded.indexOf(':');

                if (colonIdx > 0) {
                    String sessionToken = decoded.substring(0, colonIdx);
                    String sharedSecret = decoded.substring(colonIdx + 1);
                    String timestamp = Instant.now().toString();

                    // Compute SIF HMAC-SHA256
                    String hmac = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

                    // Build SIF auth header: SIF_HMACSHA256 Base64(sessionToken:hmac)
                    String sifCredentials = Base64.getEncoder().encodeToString(
                            (sessionToken + ":" + hmac).getBytes(StandardCharsets.UTF_8));
                    String sifAuthHeader = "SIF_HMACSHA256 " + sifCredentials;

                    log.debug("Swagger Basic Auth adapter: converted credentials for sessionToken={}", sessionToken);

                    HttpServletRequest wrappedRequest = new SifAuthRequestWrapper(request, sifAuthHeader, timestamp);
                    filterChain.doFilter(wrappedRequest, response);
                    return;
                }
            } catch (Exception e) {
                log.warn("Failed to convert Basic Auth to SIF HMAC: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Checks if the request originates from Swagger UI by inspecting the Referer header.
     */
    private boolean isFromSwaggerUi(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        return referer != null && (referer.contains("/swagger-ui") || referer.contains("/webjars/swagger-ui"));
    }

    /**
     * Request wrapper that replaces the Authorization header with SIF HMAC-SHA256
     * and adds the timestamp header.
     */
    private static class SifAuthRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> customHeaders;

        SifAuthRequestWrapper(HttpServletRequest request, String sifAuthHeader, String timestamp) {
            super(request);
            this.customHeaders = new HashMap<>();
            this.customHeaders.put("authorization", sifAuthHeader);
            this.customHeaders.put("timestamp", timestamp);
        }

        @Override
        public String getHeader(String name) {
            // Case-insensitive lookup for our custom headers
            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return Collections.enumeration(List.of(entry.getValue()));
                }
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new java.util.ArrayList<>(Collections.list(super.getHeaderNames()));
            for (String key : customHeaders.keySet()) {
                if (names.stream().noneMatch(n -> n.equalsIgnoreCase(key))) {
                    names.add(key);
                }
            }
            return Collections.enumeration(names);
        }
    }
}
