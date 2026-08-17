// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.environment.dto.AdminSessionContext;
import org.sifunity.broker.environment.dto.UserEnvironmentAccessRequest;
import org.sifunity.broker.environment.dto.UserLoginRequest;
import org.sifunity.broker.environment.dto.UserLoginResponse;
import org.sifunity.broker.environment.filter.AdminSessionFilter;
import org.sifunity.broker.environment.service.UserAuthenticationService;

/**
 * REST controller for admin user authentication and session management.
 *
 * <p>This controller handles:</p>
 * <ul>
 *   <li>{@code POST /admin-users/login} — authenticate with username/password (no auth required)</li>
 *   <li>{@code POST /admin-users/sessions/select-environment} — select environment (admin session required)</li>
 *   <li>{@code POST /admin-users/sessions/logout} — logout (admin session required)</li>
 *   <li>{@code GET /admin-users/sessions/current} — get current session info (admin session required)</li>
 * </ul>
 *
 * <p>The login endpoint requires no authentication. Session endpoints are protected
 * by the {@link AdminSessionFilter} which validates the {@code X-Admin-Session-Token}
 * header and sets the {@link AdminSessionContext} as a request attribute.</p>
 *
 * <p><strong>Requirements:</strong> 3.1, 3.2, 3.3, 4.1, 4.4, 5.4</p>
 *
 * @see UserAuthenticationService
 * @see AdminSessionFilter
 * @see AdminSessionContext
 */
@Tag(name = "Internal API", description = "Admin user login and session management")
@RestController
@RequestMapping("/admin-users")
public class AdminUserAuthController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserAuthController.class);

    private final UserAuthenticationService userAuthenticationService;

    public AdminUserAuthController(UserAuthenticationService userAuthenticationService) {
        this.userAuthenticationService = userAuthenticationService;
    }

    /**
     * Authenticates an admin user with username and password.
     *
     * <p>No authentication is required for this endpoint. On success, returns a session
     * token and the list of environments the user has access to.</p>
     *
     * @param request the login request containing username, password, domainUuid, solutionUuid
     * @return 200 OK with session token, expiry, and environment list
     */
    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        log.info("Admin user login attempt: username={}, domain={}, solution={}",
                request.username(), request.domainId(), request.solutionId());

        UserLoginResponse response = userAuthenticationService.login(request);

        log.info("Admin user login successful: username={}", request.username());

        return ResponseEntity.ok(response);
    }

    /**
     * Selects an environment for the current admin session.
     *
     * <p>Requires a valid admin session token in the {@code X-Admin-Session-Token} header.
     * The user must have access to the specified environment.</p>
     *
     * @param request     the request containing the environmentId to select
     * @param httpRequest the HTTP request (contains AdminSessionContext set by filter)
     * @return 204 No Content on success
     */
    @PostMapping("/sessions/select-environment")
    public ResponseEntity<Void> selectEnvironment(
            @Valid @RequestBody UserEnvironmentAccessRequest request,
            HttpServletRequest httpRequest) {

        String sessionToken = httpRequest.getHeader(AdminSessionFilter.ADMIN_SESSION_HEADER);
        AdminSessionContext session = getSessionContext(httpRequest);

        log.info("Environment selection: user={}, environment={}",
                session.username(), request.environmentId());

        userAuthenticationService.selectEnvironment(sessionToken, request.environmentId());

        log.info("Environment selected: user={}, environment={}",
                session.username(), request.environmentId());

        return ResponseEntity.noContent().build();
    }

    /**
     * Logs out the current admin session.
     *
     * <p>Requires a valid admin session token in the {@code X-Admin-Session-Token} header.
     * Invalidates the session token in Redis.</p>
     *
     * @param httpRequest the HTTP request (contains session token header)
     * @return 204 No Content on success
     */
    @PostMapping("/sessions/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        String sessionToken = httpRequest.getHeader(AdminSessionFilter.ADMIN_SESSION_HEADER);
        AdminSessionContext session = getSessionContext(httpRequest);

        log.info("Admin user logout: user={}", session.username());

        userAuthenticationService.logout(sessionToken);

        log.info("Admin user logged out: user={}", session.username());

        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the current admin session information.
     *
     * <p>Requires a valid admin session token in the {@code X-Admin-Session-Token} header.
     * Returns the session context including user identity and selected environment.</p>
     *
     * @param httpRequest the HTTP request (contains AdminSessionContext set by filter)
     * @return 200 OK with the current session context
     */
    @GetMapping("/sessions/current")
    public ResponseEntity<AdminSessionContext> getCurrentSession(HttpServletRequest httpRequest) {
        AdminSessionContext session = getSessionContext(httpRequest);

        log.debug("Current session requested: user={}", session.username());

        return ResponseEntity.ok(session);
    }

    /**
     * Extracts the AdminSessionContext from the request attributes.
     * The filter has already validated the session and set this attribute.
     */
    private AdminSessionContext getSessionContext(HttpServletRequest request) {
        return (AdminSessionContext) request.getAttribute(AdminSessionFilter.ADMIN_SESSION_CONTEXT_ATTRIBUTE);
    }
}
