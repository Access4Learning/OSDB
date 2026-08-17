// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.environment.dto.UserEnvironmentAccessDTO;
import org.sifunity.broker.environment.dto.UserEnvironmentAccessRequest;
import org.sifunity.broker.environment.service.UserEnvironmentAccessService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing user-environment access associations.
 *
 * <p>This controller handles HTTP requests for granting, revoking, and listing
 * environment access for admin users:
 * <ul>
 *   <li>Grant access (POST /admin-users/{userId}/environments)</li>
 *   <li>Revoke access (DELETE /admin-users/{userId}/environments/{environmentId})</li>
 *   <li>List user's environments (GET /admin-users/{userId}/environments)</li>
 * </ul>
 *
 * <p>All endpoints are HMAC-authenticated via the existing AuthorizationFilter.
 * The caller's scope determines what they can manage (global admin → any scope,
 * broker-services → same domain+solution, regular → same environment only).</p>
 *
 * @see UserEnvironmentAccessService
 * @see UserEnvironmentAccessRequest
 * @see UserEnvironmentAccessDTO
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/admin-users/{userId}/environments")
public class UserEnvironmentAccessController {

    private static final Logger log = LoggerFactory.getLogger(UserEnvironmentAccessController.class);

    private final UserEnvironmentAccessService accessService;

    public UserEnvironmentAccessController(UserEnvironmentAccessService accessService) {
        this.accessService = accessService;
    }

    /**
     * Grants an admin user access to an environment.
     *
     * <p>Creates a user-environment association linking the specified admin user
     * to the specified environment. The environment must belong to the same
     * domain+solution as the admin user.</p>
     *
     * @param domainId   the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param userId     the admin user's UUID
     * @param request    the access request containing the environment ID
     * @return ResponseEntity containing the created association details
     *
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>201 Created - Access granted successfully</li>
     *   <li>400 Bad Request - Domain+solution mismatch or invalid request</li>
     *   <li>404 Not Found - Admin user or environment not found</li>
     *   <li>409 Conflict - User already has access to this environment</li>
     * </ul>
     *
     * <p><strong>Requirements:</strong> 2.2, 2.3, 2.4, 2.7, 2.8</p>
     */
    @PostMapping
    public ResponseEntity<UserEnvironmentAccessDTO> grantAccess(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID userId,
            @Valid @RequestBody UserEnvironmentAccessRequest request) {

        log.info("Granting environment access: userId={}, environmentId={}", userId, request.environmentId());

        UserEnvironmentAccessDTO access = accessService.grantAccess(userId, request);

        log.info("Environment access granted: userId={}, environmentId={}, associationId={}",
                userId, request.environmentId(), access.id());

        return ResponseEntity.status(HttpStatus.CREATED).body(access);
    }

    /**
     * Revokes an admin user's access to an environment.
     *
     * <p>Deletes the user-environment association for the specified user and
     * environment combination.</p>
     *
     * @param domainId      the domain ID from the URL path
     * @param solutionId    the solution ID from the URL path
     * @param userId        the admin user's UUID
     * @param environmentId the environment's UUID
     * @return ResponseEntity with no content
     *
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>204 No Content - Access revoked successfully</li>
     *   <li>404 Not Found - Association not found</li>
     * </ul>
     *
     * <p><strong>Requirements:</strong> 2.9</p>
     */
    @DeleteMapping("/{environmentId}")
    public ResponseEntity<Void> revokeAccess(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID userId,
            @PathVariable UUID environmentId) {

        log.info("Revoking environment access: userId={}, environmentId={}", userId, environmentId);

        accessService.revokeAccess(userId, environmentId);

        log.info("Environment access revoked: userId={}, environmentId={}", userId, environmentId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all environments an admin user has access to.
     *
     * @param domainId   the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param userId     the admin user's UUID
     * @return ResponseEntity containing the list of environment access associations
     *
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - List returned (may be empty)</li>
     * </ul>
     *
     * <p><strong>Requirements:</strong> 2.2</p>
     */
    @GetMapping
    public ResponseEntity<List<UserEnvironmentAccessDTO>> listUserEnvironments(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID userId) {

        log.debug("Listing environment access for user: userId={}", userId);

        List<UserEnvironmentAccessDTO> environments = accessService.listAccessForUser(userId);

        log.debug("Environment access listed: userId={}, count={}", userId, environments.size());

        return ResponseEntity.ok(environments);
    }
}
