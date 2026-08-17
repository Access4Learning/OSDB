// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.environment.config.BrokerInfrastructureConfig;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.AdminSessionContext;
import org.sifunity.broker.environment.filter.AdminSessionFilter;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Proxy controller for the admin console's "user mode".
 *
 * <p>Receives requests with an {@code X-Admin-Session-Token} header, validates the
 * admin session (via {@link AdminSessionFilter}), retrieves the selected environment's
 * HMAC credentials, signs the request using SIF HMAC-SHA256, and forwards it to the gateway.</p>
 *
 * <p>The {@link AdminSessionFilter} runs before this controller and validates the session
 * token, setting the {@link AdminSessionContext} as a request attribute. This controller
 * reads the context from the attribute — no re-validation needed.</p>
 *
 * <p>Request flow:</p>
 * <ol>
 *   <li>AdminSessionFilter validates {@code X-Admin-Session-Token} and sets context attribute</li>
 *   <li>Controller reads {@link AdminSessionContext} from request attribute</li>
 *   <li>Retrieves selected environment's credentials from database</li>
 *   <li>Computes HMAC-SHA256 signature using {@link SifAuthenticationUtils#computeHmac}</li>
 *   <li>Forwards request to gateway with SIF auth headers</li>
 *   <li>Returns gateway response (status, headers, body) to caller</li>
 * </ol>
 *
 * @see AdminSessionFilter
 * @see SifAuthenticationUtils
 * @see AdminSessionContext
 */
@Tag(name = "Internal API", description = "Admin console proxy endpoint")
@RestController
@RequestMapping("/admin-proxy")
public class AdminProxyController {

    private static final Logger log = LoggerFactory.getLogger(AdminProxyController.class);

    private final EnvironmentRepository environmentRepository;
    private final BrokerInfrastructureConfig infrastructureConfig;
    private final RestTemplate restTemplate;

    public AdminProxyController(
            EnvironmentRepository environmentRepository,
            BrokerInfrastructureConfig infrastructureConfig,
            RestTemplate restTemplate) {
        this.environmentRepository = environmentRepository;
        this.infrastructureConfig = infrastructureConfig;
        this.restTemplate = restTemplate;
    }

    /**
     * Proxies any request to the gateway on behalf of the authenticated admin user.
     *
     * <p>The admin session is already validated by {@link AdminSessionFilter} which sets
     * the {@link AdminSessionContext} as a request attribute. This method reads the context,
     * retrieves the environment's HMAC credentials, signs the request, and forwards it.</p>
     *
     * @param request the incoming HTTP request
     * @return the gateway's response forwarded back to the caller
     */
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        // 1. Read session context (set by AdminSessionFilter)
        AdminSessionContext session = (AdminSessionContext) request.getAttribute(
                AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE);

        if (session == null) {
            // Should not happen if filter is configured correctly, but defensive check
            log.error("AdminSessionContext not found in request attributes — filter misconfiguration");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody("ADMIN_PROXY_001", "Session validation failed"));
        }

        // 2. Check that an environment is selected
        UUID selectedEnvironmentId = session.selectedEnvironmentId();
        if (selectedEnvironmentId == null) {
            log.warn("Admin proxy request without selected environment, user={}", session.username());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody("ADMIN_PROXY_003", "No environment selected"));
        }

        // 3. Retrieve environment credentials
        Environment environment = environmentRepository.findById(selectedEnvironmentId).orElse(null);
        if (environment == null) {
            log.warn("Admin proxy: selected environment not found, id={}", selectedEnvironmentId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody("ADMIN_PROXY_004", "Selected environment not found"));
        }

        String envSessionToken = environment.getSessionToken();
        String envSharedSecret = environment.getSharedSecret();

        // 4. Compute HMAC-SHA256 signature
        String timestamp = Instant.now().toString();
        String hmacHash = SifAuthenticationUtils.computeHmac(envSessionToken, timestamp, envSharedSecret);
        String credentials = envSessionToken + ":" + hmacHash;
        String authHeaderValue = "SIF_HMACSHA256 " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        // 5. Build target URL: strip /admin-proxy prefix, preserve path and query string
        String requestUri = request.getRequestURI();
        String targetPath = requestUri.replaceFirst("^/admin-proxy", "");
        if (targetPath.isEmpty()) {
            targetPath = "/";
        }
        String queryString = request.getQueryString();
        String targetUrl = infrastructureConfig.gatewayBaseUrl() + targetPath;
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        // 6. Build outbound headers
        HttpHeaders outboundHeaders = new HttpHeaders();
        outboundHeaders.set(SifHeaders.AUTHORIZATION, authHeaderValue);
        outboundHeaders.set(SifHeaders.TIMESTAMP, timestamp);
        outboundHeaders.set(SifHeaders.X_RUN_AS_ENVIRONMENT, "broker-services");
        outboundHeaders.setContentType(MediaType.APPLICATION_JSON);

        // 7. Read request body for POST/PUT
        byte[] requestBody = null;
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            try {
                requestBody = request.getInputStream().readAllBytes();
            } catch (IOException e) {
                log.error("Failed to read request body for admin proxy", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorBody("ADMIN_PROXY_005", "Failed to read request body"));
            }
        }

        // 8. Forward request to gateway
        HttpEntity<byte[]> outboundEntity = new HttpEntity<>(requestBody, outboundHeaders);
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

        log.debug("Admin proxy forwarding: {} {} -> {} (user={}, env={})",
                method, requestUri, targetUrl, session.username(), selectedEnvironmentId);

        try {
            ResponseEntity<byte[]> gatewayResponse = restTemplate.exchange(
                    targetUrl, httpMethod, outboundEntity, byte[].class);

            // 9. Return gateway response to caller
            HttpHeaders responseHeaders = new HttpHeaders();
            if (gatewayResponse.getHeaders().getContentType() != null) {
                responseHeaders.setContentType(gatewayResponse.getHeaders().getContentType());
            }
            // Forward correlation ID if present
            String correlationId = gatewayResponse.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
            if (correlationId != null) {
                responseHeaders.set(SifHeaders.X_CORRELATION_ID, correlationId);
            }

            return new ResponseEntity<>(gatewayResponse.getBody(), responseHeaders,
                    gatewayResponse.getStatusCode());

        } catch (HttpClientErrorException e) {
            // 4xx from gateway — forward as-is
            log.debug("Admin proxy received {} from gateway for: {} {}",
                    e.getStatusCode(), method, targetPath);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(e.getResponseBodyAsByteArray(), responseHeaders,
                    e.getStatusCode());

        } catch (HttpServerErrorException e) {
            // 5xx from gateway — forward as-is
            log.warn("Admin proxy received {} from gateway for: {} {}",
                    e.getStatusCode(), method, targetPath);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(e.getResponseBodyAsByteArray(), responseHeaders,
                    e.getStatusCode());

        } catch (Exception e) {
            // Connection errors, timeouts, etc.
            log.error("Admin proxy gateway communication error for: {} {}", method, targetPath, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody("ADMIN_PROXY_006", "Gateway communication error"));
        }
    }

    /**
     * Builds a minimal JSON error response body.
     */
    private byte[] errorBody(String code, String message) {
        String json = String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"scope\":\"BROKER\",\"timestamp\":\"%s\"}",
                code, message, Instant.now().toString());
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
