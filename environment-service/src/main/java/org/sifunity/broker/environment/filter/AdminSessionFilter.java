// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.environment.dto.AdminSessionContext;
import org.sifunity.broker.environment.exception.InvalidAdminSessionException;
import org.sifunity.broker.environment.service.UserAuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

/**
 * Filter that validates admin session tokens for session-protected endpoints.
 *
 * <p>This filter intercepts requests to admin-session-protected endpoints,
 * reads the {@code X-Admin-Session-Token} header, validates the session via Redis,
 * and sets the {@link AdminSessionContext} as a request attribute on success.</p>
 *
 * <p>Session-protected endpoints:</p>
 * <ul>
 *   <li>{@code POST /admin-users/sessions/select-environment}</li>
 *   <li>{@code POST /admin-users/sessions/logout}</li>
 *   <li>{@code GET /admin-users/sessions/current}</li>
 *   <li>{@code GET/POST/PUT/DELETE /admin-proxy/**} — admin console proxy</li>
 * </ul>
 *
 * <p>Endpoints NOT requiring admin session (excluded from this filter):</p>
 * <ul>
 *   <li>{@code POST /admin-users/login} — no auth required</li>
 *   <li>{@code POST /admin-users} — HMAC-authenticated</li>
 *   <li>{@code GET /admin-users} — HMAC-authenticated</li>
 *   <li>{@code GET /admin-users/{id}} — HMAC-authenticated</li>
 *   <li>{@code DELETE /admin-users/{id}} — HMAC-authenticated</li>
 *   <li>{@code POST /admin-users/{userId}/environments} — HMAC-authenticated</li>
 *   <li>{@code DELETE /admin-users/{userId}/environments/{environmentId}} — HMAC-authenticated</li>
 *   <li>{@code GET /admin-users/{userId}/environments} — HMAC-authenticated</li>
 * </ul>
 *
 * <p><strong>Requirements:</strong> 3.1, 3.7, 7.4</p>
 *
 * @see UserAuthenticationService#validateSession(String)
 * @see AdminSessionContext
 */
public class AdminSessionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminSessionFilter.class);

    public static final String ADMIN_SESSION_HEADER = "X-Admin-Session-Token";
    public static final String ADMIN_SESSION_CONTEXT_ATTRIBUTE = "adminSessionContext";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static { OBJECT_MAPPER.findAndRegisterModules(); }

    /**
     * URL paths that require admin session authentication.
     * Only requests matching these path prefixes are intercepted by this filter.
     */
    private static final String SESSION_PROTECTED_PATH = "/admin-users/sessions/";

    private final UserAuthenticationService userAuthenticationService;

    public AdminSessionFilter(UserAuthenticationService userAuthenticationService) {
        this.userAuthenticationService = userAuthenticationService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Only filter requests to admin session endpoints and admin proxy endpoints
        // Session-protected: /admin-users/sessions/*
        if (path.startsWith(SESSION_PROTECTED_PATH)) {
            return false;
        }

        // Admin proxy endpoints also require session validation
        if (path.startsWith("/admin-proxy/") || path.equals("/admin-proxy")) {
            return false;
        }

        // All other paths are NOT session-protected — skip this filter
        return true;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        log.debug("AdminSessionFilter processing: {} {}", method, path);

        // Extract session token from header
        String sessionToken = request.getHeader(ADMIN_SESSION_HEADER);

        if (sessionToken == null || sessionToken.isBlank()) {
            log.warn("Missing {} header for: {} {}", ADMIN_SESSION_HEADER, method, path);
            writeSifError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "ADMIN_USER_006", "Session expired or invalid",
                    "No admin session token provided");
            return;
        }

        // Validate session via Redis
        try {
            AdminSessionContext sessionContext = userAuthenticationService.validateSession(sessionToken);

            // Store session context as request attribute for controllers
            request.setAttribute(ADMIN_SESSION_CONTEXT_ATTRIBUTE, sessionContext);

            log.debug("Admin session validated for user={}, proceeding with request: {} {}",
                    sessionContext.username(), method, path);

            // Continue filter chain
            filterChain.doFilter(request, response);

        } catch (InvalidAdminSessionException e) {
            log.warn("Invalid admin session token for: {} {}", method, path);
            writeSifError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "ADMIN_USER_006", "Session expired or invalid",
                    "Admin session token is expired or invalid");
        }
    }

    /**
     * Writes a SIF-compliant JSON error response.
     */
    private void writeSifError(HttpServletResponse response, int status, String code,
                               String message, String description) throws IOException {
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(), status, code, message,
                description, "BROKER", Instant.now(), null, null);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getWriter(), SifErrorResponseEnvelope.of(error));
    }
}
